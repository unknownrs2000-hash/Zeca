"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  CommitUncertainError,
  IdempotencyConflictError,
  InsufficientBalanceError,
  ProjectionPendingError,
  WalletNotFoundError,
  ActionPendingError,
  createFinancialStore,
} = require("./financial-store");

function clone(value) {
  return value == null ? value : structuredClone(value);
}

function unknownCommitError() {
  const error = new Error("Unknown transaction commit result");
  error.hasErrorLabel = (label) => label === "UnknownTransactionCommitResult";
  return error;
}

class MemoryFinancialRepository {
  constructor() {
    this.wallets = new Map();
    this.operations = new Map();
    this.ledger = new Map();
    this.locks = new Map();
    this.queue = Promise.resolve();
    this.uncertainAfterCommit = false;
    this.uncertainWithoutCommit = false;
    this.duplicateKeyOnce = false;
    this.failNextOperationRead = false;
  }

  async withTransaction(callback) {
    let unlock;
    const previous = this.queue;
    this.queue = new Promise((resolve) => { unlock = resolve; });
    await previous;
    const wallets = new Map([...this.wallets].map(([key, value]) => [key, clone(value)]));
    const operations = new Map([...this.operations].map(([key, value]) => [key, clone(value)]));
    const ledger = new Map([...this.ledger].map(([key, value]) => [key, clone(value)]));
    const locks = new Map([...this.locks].map(([key, value]) => [key, clone(value)]));
    const transaction = {
      getWallet: async (uid) => clone(wallets.get(uid)),
      setWallet: async (uid, wallet) => wallets.set(uid, clone(wallet)),
      getOperation: async (id) => clone(operations.get(id)),
      insertOperation: async (operation) => operations.set(operation._id, clone(operation)),
      insertLedger: async (entry, operationId) => ledger.set(`${entry.uid}:${entry.id}`, { ...clone(entry), operationId }),
      getActionLock: async (key) => clone(locks.get(key)),
      promoteActionLock: async (key, token, operationId) => {
        const lock = locks.get(key);
        if (!lock || lock.token !== token) throw new Error("Action lock ownership changed.");
        locks.set(key, { operationId });
      },
    };
    try {
      if (this.duplicateKeyOnce) {
        this.duplicateKeyOnce = false;
        const error = new Error("E11000 duplicate key error");
        error.code = 11000;
        throw error;
      }
      const result = await callback(transaction);
      if (this.uncertainWithoutCommit) {
        this.uncertainWithoutCommit = false;
        throw unknownCommitError();
      }
      this.wallets = wallets;
      this.operations = operations;
      this.ledger = ledger;
      this.locks = locks;
      if (this.uncertainAfterCommit) {
        this.uncertainAfterCommit = false;
        throw unknownCommitError();
      }
      return clone(result);
    } finally {
      unlock();
    }
  }

  async getOperation(id) {
    if (this.failNextOperationRead) {
      this.failNextOperationRead = false;
      throw new Error("Temporary read failure");
    }
    return clone(this.operations.get(id));
  }

  async getWallet(uid) {
    return clone(this.wallets.get(uid));
  }

  async acquireActionLock(key, token, nowMs, leaseMs) {
    const existing = this.locks.get(key);
    if (existing && (existing.operationId || existing.expiresAtMs > nowMs)) return false;
    this.locks.set(key, { token, expiresAtMs: nowMs + leaseMs });
    return true;
  }

  async getActionLock(key) {
    return clone(this.locks.get(key));
  }

  async releaseActionLock(key, token) {
    const existing = this.locks.get(key);
    if (existing?.token === token && !existing.operationId) this.locks.delete(key);
  }

  async listTransactions(uid, limit) {
    return [...this.ledger.values()].filter((entry) => entry.uid === uid).slice(0, limit).map(clone);
  }

  async markProjected(id, atMs) {
    const operation = this.operations.get(id);
    this.operations.set(id, { ...operation, status: "projected", projectedAtMs: atMs });
    for (const [key, lock] of this.locks) {
      if (lock.operationId === id) this.locks.delete(key);
    }
  }

  async listPending(limit) {
    return [...this.operations.values()]
      .filter((operation) => operation.status === "pending_projection")
      .slice(0, limit)
      .map(clone);
  }
}

function setup(options = {}) {
  const repository = new MemoryFinancialRepository();
  repository.wallets.set("player", {
    balanceCents: options.initialBalanceCents ?? 500,
    createdAtMs: 1,
    updatedAtMs: 1,
  });
  const projected = [];
  let failProjection = options.failProjection || 0;
  const store = createFinancialStore({
    repository,
    initialBalanceCents: options.initialBalanceCents ?? 500,
    clock: () => 1234,
    projector: {
      async apply(operation) {
        projected.push(clone(operation));
        if (failProjection > 0) {
          failProjection -= 1;
          throw new Error("Firestore projection unavailable");
        }
      },
    },
  });
  return { repository, projected, store };
}

function debit(operationId, deltaCents = -100) {
  return {
    operationId,
    operationType: "test_debit",
    entries: [{ uid: "player", deltaCents }],
    projection: { paths: [] },
    result: { accepted: true },
  };
}

test("repeating a request ID applies its debit and projection once", async () => {
  const { repository, projected, store } = setup();
  await store.applyOperation(debit("request-1"));
  await store.applyOperation(debit("request-1"));
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal(projected.length, 1);
});

test("retry after commit and projection failure completes without another debit", async () => {
  const { repository, projected, store } = setup({ failProjection: 1 });
  await assert.rejects(store.applyOperation(debit("request-2")), ProjectionPendingError);
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal((await repository.getOperation("request-2")).status, "pending_projection");
  await store.applyOperation(debit("request-2"));
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal(projected.length, 2);
});

test("insufficient balance does not create a wallet operation", async () => {
  const { repository, projected, store } = setup({ initialBalanceCents: 50 });
  await assert.rejects(store.applyOperation(debit("request-3")), InsufficientBalanceError);
  assert.equal((await repository.getWallet("player")).balanceCents, 50);
  assert.equal(await repository.getOperation("request-3"), undefined);
  assert.equal(projected.length, 0);
});

test("concurrent calls for the same match settlement debit only once", async () => {
  const { repository, projected, store } = setup();
  const first = debit("match-77:settlement");
  const second = { ...debit("match-77:settlement"), requestId: "different-http-request" };
  await Promise.all([store.applyOperation(first), store.applyOperation(second)]);
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal(projected.length, 1);
});

test("unknown commit with unavailable lookup does not project and same-ID retry resolves", async () => {
  const { repository, projected, store } = setup();
  repository.uncertainAfterCommit = true;
  repository.failNextOperationRead = true;
  await assert.rejects(store.applyOperation(debit("request-5")), CommitUncertainError);
  assert.equal(projected.length, 0);
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  await store.applyOperation(debit("request-5"));
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal(projected.length, 1);
});

test("new wallet receives the initial balance once under concurrent account creation", async () => {
  const repository = new MemoryFinancialRepository();
  repository.wallets.delete("player");
  const store = createFinancialStore({
    repository,
    projector: { async apply() {} },
    initialBalanceCents: 500,
    clock: () => 1234,
  });
  await Promise.all([store.ensureWallet("new-user"), store.ensureWallet("new-user")]);
  assert.equal((await repository.getWallet("new-user")).balanceCents, 500);
});

test("a missing wallet cannot receive an implicit starting balance", async () => {
  const repository = new MemoryFinancialRepository();
  repository.wallets.delete("player");
  const store = createFinancialStore({
    repository,
    projector: { async apply() {} },
    initialBalanceCents: 500,
  });
  await assert.rejects(store.applyOperation(debit("request-missing-wallet", 100)), WalletNotFoundError);
  assert.equal(await repository.getWallet("player"), undefined);
});

test("pending projection holds the action lock until the reconciler completes it", async () => {
  const { repository, store } = setup({ failProjection: 1 });
  const action = (lease) => store.applyOperation({ ...debit("room-9:settlement"), lockLease: lease });
  await assert.rejects(store.withActionLock("game:room-9", action), ProjectionPendingError);
  await assert.rejects(
    store.withActionLock("game:room-9", async () => {}),
    ActionPendingError,
  );
  const reconciliation = await store.reconcilePending();
  assert.deepEqual(reconciliation, { scanned: 1, projected: 1, failed: 0 });
  assert.equal(await repository.getActionLock("game:room-9"), undefined);
  let actionResumed = false;
  await store.withActionLock("game:room-9", async () => { actionResumed = true; });
  assert.equal(actionResumed, true);
});

test("a positive net payout still requires the full wager balance before play", async () => {
  const { repository, store } = setup({ initialBalanceCents: 50 });
  await assert.rejects(store.applyOperation({
    ...debit("winning-wager", 100),
    entries: [{ uid: "player", deltaCents: 25 }],
    minimumBalances: [{ uid: "player", minimumBalanceCents: 100 }],
  }), InsufficientBalanceError);
  assert.equal((await repository.getWallet("player")).balanceCents, 50);
  assert.equal(await repository.getOperation("winning-wager"), undefined);
});

test("zero-net game operations can still persist an idempotent projection", async () => {
  const { repository, projected, store } = setup();
  const operation = {
    operationId: "tied-game",
    operationType: "game_settlement",
    entries: [],
    minimumBalances: [{ uid: "player", minimumBalanceCents: 100 }],
    projection: { writes: [{ path: "users/player/games/history", data: { outcome: "tied" } }] },
    result: { outcome: "tied" },
  };
  await store.applyOperation(operation);
  await store.applyOperation(operation);
  assert.equal((await repository.getWallet("player")).balanceCents, 500);
  assert.equal(projected.length, 1);
});

test("resume by stored ID completes a pending projection without recalculating its payload", async () => {
  const { repository, projected, store } = setup({ failProjection: 1 });
  await assert.rejects(store.applyOperation(debit("resume-1")), ProjectionPendingError);
  const resumed = await store.resumeOperation("resume-1");
  assert.equal(resumed.status, "projected");
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal(projected.length, 2);
});

test("unknown commit that did land resolves by lookup and projects once", async () => {
  const { repository, projected, store } = setup();
  repository.uncertainAfterCommit = true;
  const result = await store.applyOperation(debit("uncertain-landed"));
  assert.equal(result.status, "projected");
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal(projected.length, 1);
});

test("unknown commit that did not land reports uncertainty, and the same-ID retry debits once", async () => {
  const { repository, projected, store } = setup();
  repository.uncertainWithoutCommit = true;
  await assert.rejects(store.applyOperation(debit("uncertain-lost")), CommitUncertainError);
  assert.equal((await repository.getWallet("player")).balanceCents, 500);
  assert.equal(projected.length, 0);
  await store.applyOperation(debit("uncertain-lost"));
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal(projected.length, 1);
});

test("a duplicate-key race on the operation insert resolves to the stored operation", async () => {
  const { repository, projected, store } = setup();
  await store.applyOperation(debit("race-1"));
  repository.duplicateKeyOnce = true;
  const result = await store.applyOperation(debit("race-1"));
  assert.equal(result.status, "projected");
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  assert.equal(projected.length, 1);
});

test("reusing an ID with a different payload is rejected without moving money", async () => {
  const { repository, store } = setup();
  await store.applyOperation(debit("reused-id", -100));
  await assert.rejects(store.applyOperation(debit("reused-id", -200)), IdempotencyConflictError);
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
});

test("two different requests cannot both spend the same balance", async () => {
  const { repository, store } = setup({ initialBalanceCents: 150 });
  const outcomes = await Promise.allSettled([
    store.applyOperation(debit("bet-a")),
    store.applyOperation(debit("bet-b")),
  ]);
  assert.equal(outcomes.filter((outcome) => outcome.status === "fulfilled").length, 1);
  const rejected = outcomes.find((outcome) => outcome.status === "rejected");
  assert.ok(rejected.reason instanceof InsufficientBalanceError);
  assert.equal((await repository.getWallet("player")).balanceCents, 50);
});

test("a second action on a game in progress is refused with a clear reason", async () => {
  const { repository, store } = setup();
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  const first = store.withActionLock("game:7", async (lease) => {
    await gate;
    return store.applyOperation({ ...debit("game-7:move-1"), lockLease: lease });
  });
  await assert.rejects(
    store.withActionLock("game:7", async () => {}),
    (error) => error instanceof ActionPendingError && error.reason === "action-in-progress",
  );
  release();
  await first;
  assert.equal((await repository.getWallet("player")).balanceCents, 400);
  await store.withActionLock("game:7", async () => {});
});

test("resuming a pending operation by ID releases its action lock", async () => {
  const { repository, store } = setup({ failProjection: 1 });
  const action = (lease) => store.applyOperation({ ...debit("room-10:settlement"), lockLease: lease });
  await assert.rejects(store.withActionLock("game:room-10", action), ProjectionPendingError);
  assert.ok((await repository.getActionLock("game:room-10")).operationId);
  await store.resumeOperation("room-10:settlement");
  assert.equal(await repository.getActionLock("game:room-10"), undefined);
});

test("a projection that can never be applied is rejected before any money moves", async () => {
  const repository = new MemoryFinancialRepository();
  repository.wallets.set("player", { balanceCents: 500, createdAtMs: 1, updatedAtMs: 1 });
  const store = createFinancialStore({
    repository,
    initialBalanceCents: 500,
    clock: () => 1234,
    projector: {
      async apply() {},
      validate(operation) {
        if (operation.projection.writes.some((write) => write.path.split("/").length % 2 !== 0)) {
          throw new TypeError("bad path");
        }
      },
    },
  });
  await assert.rejects(
    store.applyOperation({
      ...debit("poison-1"),
      projection: { writes: [{ path: "users/player/odd", data: {} }] },
    }),
    TypeError,
  );
  assert.equal((await repository.getWallet("player")).balanceCents, 500);
  assert.equal(await repository.getOperation("poison-1"), undefined);
});