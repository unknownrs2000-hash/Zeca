"use strict";

const { createHash, randomUUID } = require("node:crypto");

class InsufficientBalanceError extends Error {
  constructor(uid) {
    super("Saldo insuficiente.");
    this.name = "InsufficientBalanceError";
    this.code = "failed-precondition";
    this.uid = uid;
  }
}

class WalletNotFoundError extends Error {
  constructor(uid) {
    super("Carteira financeira não encontrada. A conta precisa ser migrada ou inicializada.");
    this.name = "WalletNotFoundError";
    this.code = "failed-precondition";
    this.uid = uid;
  }
}

class ActionPendingError extends Error {
  constructor(key, operationId = "") {
    super(operationId
      ? "A partida está sendo atualizada. Aguarde a sincronização financeira antes de tentar novamente."
      : "Já existe uma ação em andamento nesta partida. Aguarde e tente novamente.");
    this.name = "ActionPendingError";
    this.code = "unavailable";
    this.reason = operationId ? "projection-pending" : "action-in-progress";
    this.key = key;
    this.operationId = operationId;
  }
}

class IdempotencyConflictError extends Error {
  constructor(operationId) {
    super("Identificador financeiro já utilizado para outra operação.");
    this.name = "IdempotencyConflictError";
    this.code = "already-exists";
    this.operationId = operationId;
  }
}

class ProjectionPendingError extends Error {
  constructor(operationId, cause) {
    super("Operação confirmada; a atualização da conta está pendente. Tente novamente em instantes.", { cause });
    this.name = "ProjectionPendingError";
    this.code = "unavailable";
    this.reason = "projection-pending";
    this.operationId = operationId;
  }
}

class CommitUncertainError extends Error {
  constructor(operationId, cause) {
    super("Não foi possível confirmar o resultado financeiro. Repita a solicitação com o mesmo identificador.", { cause });
    this.name = "CommitUncertainError";
    this.code = "unavailable";
    this.reason = "commit-uncertain";
    this.operationId = operationId;
  }
}

function stableValue(value) {
  if (Array.isArray(value)) return value.map(stableValue);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.keys(value).sort().map((key) => [key, stableValue(value[key])]),
  );
}

function normalizeEntries(entries) {
  if (!Array.isArray(entries)) throw new TypeError("A operação financeira precisa de uma lista de lançamentos.");
  const deltas = new Map();
  for (const entry of entries) {
    if (typeof entry?.uid !== "string" || !entry.uid.trim()
        || !Number.isSafeInteger(entry.deltaCents)) {
      throw new TypeError("Lançamento financeiro inválido.");
    }
    deltas.set(entry.uid, (deltas.get(entry.uid) || 0) + entry.deltaCents);
  }
  return [...deltas]
    .map(([uid, deltaCents]) => ({ uid, deltaCents }))
    .filter((entry) => entry.deltaCents !== 0)
    .sort((left, right) => left.uid.localeCompare(right.uid));
}

function normalizeLedger(ledger) {
  if (!Array.isArray(ledger)) throw new TypeError("Ledger entries must be an array.");
  return ledger.map((entry) => {
    if (typeof entry?.uid !== "string" || !entry.uid.trim()
        || typeof entry.id !== "string" || !entry.id.trim()
        || !Number.isSafeInteger(entry.deltaCents)
        || typeof entry.description !== "string") {
      throw new TypeError("Financial ledger entry is invalid.");
    }
    return { ...entry };
  });
}

function normalizeMinimumBalances(minimumBalances) {
  if (!Array.isArray(minimumBalances)) throw new TypeError("Minimum balances must be an array.");
  const requirements = new Map();
  for (const requirement of minimumBalances) {
    if (typeof requirement?.uid !== "string" || !requirement.uid.trim()
        || !Number.isSafeInteger(requirement.minimumBalanceCents) || requirement.minimumBalanceCents < 0) {
      throw new TypeError("Minimum balance requirement is invalid.");
    }
    requirements.set(
      requirement.uid,
      Math.max(requirements.get(requirement.uid) || 0, requirement.minimumBalanceCents),
    );
  }
  return [...requirements]
    .map(([uid, minimumBalanceCents]) => ({ uid, minimumBalanceCents }))
    .sort((left, right) => left.uid.localeCompare(right.uid));
}

function operationHash(operationType, entries, ledger, minimumBalances, lockKeys, projection, result) {
  const canonical = JSON.stringify(stableValue({
    operationType,
    entries,
    ledger,
    minimumBalances,
    lockKeys,
    projection,
    result,
  }));
  return createHash("sha256").update(canonical).digest("hex");
}

function hasUnknownCommitResult(error) {
  return error?.hasErrorLabel?.("UnknownTransactionCommitResult") === true
    || error?.codeName === "UnknownTransactionCommitResult"
    || error?.code === "UnknownTransactionCommitResult";
}

function createFinancialStore({ repository, projector, initialBalanceCents = 50_000, clock = Date.now }) {
  if (!repository || typeof repository.withTransaction !== "function"
      || typeof repository.getOperation !== "function"
      || typeof repository.listTransactions !== "function"
      || typeof repository.acquireActionLock !== "function"
      || typeof projector?.apply !== "function") {
    throw new TypeError("Financial store dependencies are incomplete.");
  }
  if (!Number.isSafeInteger(initialBalanceCents) || initialBalanceCents < 0) {
    throw new TypeError("Initial wallet balance must be a non-negative safe integer.");
  }

  const projectionTasks = new Map();
  const actionLockLeaseMs = 120_000;

  async function project(operation) {
    if (operation.status === "projected") return operation;
    const activeTask = projectionTasks.get(operation._id);
    if (activeTask) return activeTask;
    const task = (async () => {
      try {
        await projector.apply({
          operationId: operation._id,
          operationType: operation.operationType,
          entries: operation.entries,
          ledger: operation.ledger || [],
          balances: operation.balances,
          projection: operation.projection,
          result: operation.result,
          committedAtMs: operation.committedAtMs,
        });
        await repository.markProjected(operation._id, clock());
        return { ...operation, status: "projected" };
      } catch (error) {
        throw new ProjectionPendingError(operation._id, error);
      }
    })();
    projectionTasks.set(operation._id, task);
    try {
      return await task;
    } finally {
      if (projectionTasks.get(operation._id) === task) projectionTasks.delete(operation._id);
    }
  }

  async function applyOperation(input) {
    const operationId = input?.operationId || input?.requestId;
    const operationType = input?.operationType;
    const entries = normalizeEntries(input?.entries);
    const ledger = normalizeLedger(input?.ledger ?? []);
    const minimumBalances = normalizeMinimumBalances(input?.minimumBalances ?? []);
    const lockKeys = input?.lockLease ? [input.lockLease.key] : [];
    const projection = input?.projection ?? {};
    const result = input?.result ?? {};
    if (typeof operationId !== "string" || !operationId.trim()
        || typeof operationType !== "string" || !operationType.trim()) {
      throw new TypeError("Operation ID and operation type are required.");
    }
    const payloadHash = operationHash(operationType, entries, ledger, minimumBalances, lockKeys, projection, result);
    projector.validate?.({
      projection,
      ledger,
      uids: [...entries.map((entry) => entry.uid), ...minimumBalances.map((requirement) => requirement.uid)],
    });
    let operation;
    try {
      operation = await repository.withTransaction(async (transaction) => {
        const existing = await transaction.getOperation(operationId);
        if (existing) {
          if (existing.payloadHash !== payloadHash) throw new IdempotencyConflictError(operationId);
          return existing;
        }

        if (input?.lockLease) {
          const lock = await transaction.getActionLock(input.lockLease.key);
          if (!lock || lock.token !== input.lockLease.token) {
            throw new ActionPendingError(input.lockLease.key, lock?.operationId || "");
          }
          await transaction.promoteActionLock(input.lockLease.key, input.lockLease.token, operationId);
        }

        const balanceBefore = new Map();
        for (const uid of new Set([
          ...entries.map((entry) => entry.uid),
          ...minimumBalances.map((requirement) => requirement.uid),
        ])) {
          const wallet = await transaction.getWallet(uid);
          if (!wallet) throw new WalletNotFoundError(uid);
          if (!Number.isSafeInteger(wallet.balanceCents) || wallet.balanceCents < 0) {
            throw new Error(`Invalid wallet balance for ${uid}.`);
          }
          balanceBefore.set(uid, wallet);
        }
        for (const { uid, minimumBalanceCents } of minimumBalances) {
          if (balanceBefore.get(uid).balanceCents < minimumBalanceCents) {
            throw new InsufficientBalanceError(uid);
          }
        }

        const balances = {};
        for (const { uid } of minimumBalances) balances[uid] = balanceBefore.get(uid).balanceCents;
        for (const { uid, deltaCents } of entries) {
          const wallet = balanceBefore.get(uid);
          const balanceAfter = wallet.balanceCents + deltaCents;
          if (!Number.isSafeInteger(balanceAfter) || balanceAfter < 0) {
            throw new InsufficientBalanceError(uid);
          }
          await transaction.setWallet(uid, {
            balanceCents: balanceAfter,
            createdAtMs: wallet?.createdAtMs ?? clock(),
            updatedAtMs: clock(),
          });
          balances[uid] = balanceAfter;
        }

        const committedAtMs = clock();
        const storedLedger = ledger.map((entry) => ({
          ...entry,
          createdAtMs: Number.isSafeInteger(entry.createdAtMs) ? entry.createdAtMs : committedAtMs,
        }));
        const created = {
          _id: operationId,
          operationType,
          payloadHash,
          entries,
          ledger: storedLedger,
          lockKeys,
          balances,
          projection,
          result,
          status: "pending_projection",
          committedAtMs,
        };
        for (const entry of storedLedger) await transaction.insertLedger(entry, operationId);
        await transaction.insertOperation(created);
        return created;
      });
    } catch (error) {
      if (error instanceof InsufficientBalanceError || error instanceof IdempotencyConflictError
          || error instanceof WalletNotFoundError || error instanceof ActionPendingError) throw error;
      const duplicateKey = error?.code === 11000;
      if (!duplicateKey && !hasUnknownCommitResult(error)) throw error;
      try {
        operation = await repository.getOperation(operationId);
      } catch (readError) {
        throw new CommitUncertainError(operationId, readError);
      }
      if (!operation) {
        if (duplicateKey) throw error;
        throw new CommitUncertainError(operationId, error);
      }
      if (operation.payloadHash !== payloadHash) throw new IdempotencyConflictError(operationId);
    }
    if (input?.lockLease && operation.status !== "projected") input.lockLease.promoted = true;
    return project(operation);
  }

  async function withActionLock(key, callback) {
    if (typeof key !== "string" || !key.trim() || typeof callback !== "function") {
      throw new TypeError("Action lock key and callback are required.");
    }
    const token = randomUUID();
    const acquired = await repository.acquireActionLock(key, token, clock(), actionLockLeaseMs);
    if (!acquired) {
      const lock = await repository.getActionLock(key);
      throw new ActionPendingError(key, lock?.operationId || "");
    }
    const lease = { key, token, promoted: false };
    try {
      return await callback(lease);
    } finally {
      if (!lease.promoted) await repository.releaseActionLock(key, token);
    }
  }

  async function ensureWallet(uid) {
    if (typeof uid !== "string" || !uid.trim()) throw new TypeError("Wallet UID is required.");
    return repository.withTransaction(async (transaction) => {
      const existing = await transaction.getWallet(uid);
      if (existing) return existing;
      const wallet = {
        balanceCents: initialBalanceCents,
        createdAtMs: clock(),
        updatedAtMs: clock(),
      };
      await transaction.setWallet(uid, wallet);
      return wallet;
    });
  }

  async function getBalance(uid) {
    const wallet = await repository.getWallet(uid);
    return wallet?.balanceCents ?? null;
  }

  async function getOperation(operationId) {
    return repository.getOperation(operationId);
  }

  async function resumeOperation(operationId) {
    const operation = await repository.getOperation(operationId);
    return operation ? project(operation) : null;
  }

  async function listTransactions(uid, limit = 50) {
    return repository.listTransactions(uid, limit);
  }

  async function reconcilePending(limit = 100) {
    const operations = await repository.listPending(limit);
    const summary = { scanned: operations.length, projected: 0, failed: 0 };
    for (const operation of operations) {
      try {
        await project(operation);
        summary.projected += 1;
      } catch {
        summary.failed += 1;
      }
    }
    return summary;
  }

  return {
    applyOperation,
    archiveWallet: (uid) => repository.archiveWallet(uid, clock()),
    ensureWallet,
    getBalance,
    getOperation,
    listTransactions,
    reconcilePending,
    resumeOperation,
    withActionLock,
  };
}

module.exports = {
  ActionPendingError,
  CommitUncertainError,
  IdempotencyConflictError,
  InsufficientBalanceError,
  ProjectionPendingError,
  WalletNotFoundError,
  createFinancialStore,
  hasUnknownCommitResult,
  normalizeEntries,
};