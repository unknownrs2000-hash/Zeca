"use strict";

const { createHash } = require("node:crypto");

const SERVER_TIMESTAMP = Object.freeze({ $serverTimestamp: true });
const SERVER_DELETE = Object.freeze({ $serverDelete: true });
const RECEIPT_COLLECTION = "financialProjectionReceipts";
const MAX_TRANSACTION_WRITES = 500;

function increment(amount) {
  if (!Number.isSafeInteger(amount)) throw new TypeError("Projection increment must be a safe integer.");
  return Object.freeze({ $increment: amount });
}

function resolveProjectionValue(value, FieldValue, balances = {}) {
  if (Array.isArray(value)) return value.map((item) => resolveProjectionValue(item, FieldValue, balances));
  if (!value || typeof value !== "object") return value;
  if (value.$serverTimestamp === true && Object.keys(value).length === 1) {
    return FieldValue.serverTimestamp();
  }
  if (value.$serverDelete === true && Object.keys(value).length === 1) {
    return FieldValue.delete();
  }
  if (Number.isSafeInteger(value.$increment) && Object.keys(value).length === 1) {
    if (typeof FieldValue.increment !== "function") throw new TypeError("FieldValue.increment is unavailable.");
    return FieldValue.increment(value.$increment);
  }
  if (typeof value.$walletBalance === "string" && Object.keys(value).length === 1) {
    const balance = balances[value.$walletBalance];
    if (!Number.isSafeInteger(balance)) throw new TypeError("Wallet balance is missing from the projection.");
    return balance;
  }
  return Object.fromEntries(
    Object.entries(value).map(([key, nested]) => [key, resolveProjectionValue(nested, FieldValue, balances)]),
  );
}

function mergeProjectionWrite(writes, path, data, operation = "set") {
  if (operation === "delete") {
    writes.set(path, { path, operation, data: null });
    return;
  }
  const previous = writes.get(path);
  writes.set(path, {
    path,
    operation: "set",
    data: { ...(previous?.data || {}), ...data },
  });
}

function receiptPath(operationId) {
  return `${RECEIPT_COLLECTION}/${createHash("sha256").update(operationId).digest("hex")}`;
}

function buildWrites(operation) {
  const writes = new Map();
  for (const write of operation.projection?.writes || []) {
    if (typeof write?.path !== "string" || !write.path.trim()
        || !["set", "update", "delete"].includes(write.operation || "set")) {
      throw new TypeError("Invalid Firestore projection write.");
    }
    mergeProjectionWrite(writes, write.path, write.data || {}, write.operation || "set");
  }
  for (const entry of operation.ledger || []) {
    const { uid, id, ...data } = entry;
    if (data.createdAtMs == null) data.createdAtMs = operation.committedAtMs;
    if (data.createdAt == null) data.createdAt = SERVER_TIMESTAMP;
    mergeProjectionWrite(writes, `users/${uid}/transactions/${id}`, data);
  }
  for (const uid of Object.keys(operation.balances || {})) {
    mergeProjectionWrite(writes, `users/${uid}`, { balanceCents: SERVER_DELETE });
    mergeProjectionWrite(writes, `leaderboard/${uid}`, { balanceCents: SERVER_DELETE });
  }
  if (writes.size + 1 > MAX_TRANSACTION_WRITES) {
    throw new RangeError("A financial projection cannot exceed 500 Firestore writes.");
  }
  return writes;
}

function createFirestoreProjector({ database, FieldValue }) {
  if (!database || typeof database.doc !== "function" || typeof database.runTransaction !== "function"
      || typeof FieldValue?.serverTimestamp !== "function") {
    throw new TypeError("Firestore projector dependencies are incomplete.");
  }

  return {
    async apply(operation) {
      if (typeof operation?.operationId !== "string" || !operation.operationId.trim()) {
        throw new TypeError("A projection requires the financial operation ID.");
      }
      const writes = buildWrites(operation);
      const receiptRef = database.doc(receiptPath(operation.operationId));
      await database.runTransaction(async (transaction) => {
        const receipt = await transaction.get(receiptRef);
        if (receipt.exists) return;
        for (const write of writes.values()) {
          const document = database.doc(write.path);
          if (write.operation === "delete") {
            transaction.delete(document);
          } else {
            transaction.set(
              document,
              resolveProjectionValue(write.data, FieldValue, operation.balances),
              { merge: true },
            );
          }
        }
        transaction.set(receiptRef, {
          operationId: operation.operationId,
          operationType: operation.operationType || "",
          projectedAt: FieldValue.serverTimestamp(),
        });
      });
    },

    validate(operation) {
      const balances = Object.fromEntries((operation.uids || []).map((uid) => [uid, 0]));
      const writes = buildWrites({ ...operation, balances, committedAtMs: 0 });
      const stub = { serverTimestamp: () => null, delete: () => null, increment: () => null };
      for (const write of writes.values()) {
        const segments = write.path.split("/");
        if (segments.length % 2 !== 0 || segments.some((segment) => !segment)) {
          throw new TypeError(`Invalid Firestore path: ${write.path}`);
        }
        resolveProjectionValue(write.data, stub, balances);
      }
    },
  };
}

module.exports = {
  SERVER_TIMESTAMP,
  SERVER_DELETE,
  createFirestoreProjector,
  increment,
  resolveProjectionValue,
};