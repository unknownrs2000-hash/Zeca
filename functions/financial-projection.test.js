"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { createFirestoreProjector, increment, SERVER_TIMESTAMP } = require("./financial-projection");

const FieldValue = {
  serverTimestamp: () => "SERVER_TIMESTAMP",
  delete: () => "DELETE_FIELD",
  increment: (amount) => `INCREMENT(${amount})`,
};

function makeFirestore({ failCommits = 0 } = {}) {
  const committed = [];
  const receipts = new Set();
  let remainingFailures = failCommits;
  const database = {
    doc: (path) => ({ path }),
    async runTransaction(callback) {
      const writes = [];
      const transaction = {
        get: async (document) => ({ exists: receipts.has(document.path) }),
        set: (document, data, options) => writes.push({ operation: "set", path: document.path, data, options }),
        delete: (document) => writes.push({ operation: "delete", path: document.path }),
      };
      await callback(transaction);
      if (remainingFailures > 0) {
        remainingFailures -= 1;
        throw new Error("Firestore unavailable");
      }
      if (writes.length > 0) {
        for (const write of writes) {
          if (write.path.startsWith("financialProjectionReceipts/")) receipts.add(write.path);
        }
        committed.push(writes);
      }
    },
  };
  return { committed, database };
}

test("projector removes legacy balance fields instead of writing Mongo balances to Firestore", async () => {
  const { committed, database } = makeFirestore();
  const projector = createFirestoreProjector({ database, FieldValue });
  await projector.apply({
    operationId: "op-1",
    balances: { player: 900 },
    projection: {
      writes: [
        { path: "users/player", data: { balanceCents: 100, gamesPlayed: 4 } },
        { path: "leaderboard/player", data: { balanceCents: 100, level: 2 } },
        { path: "users/player/transactions/op-1", data: { createdAt: SERVER_TIMESTAMP } },
      ],
    },
  });
  const writes = committed[0];
  assert.equal(writes.find((write) => write.path === "users/player").data.balanceCents, "DELETE_FIELD");
  assert.equal(writes.find((write) => write.path === "leaderboard/player").data.balanceCents, "DELETE_FIELD");
  assert.equal(writes.find((write) => write.path.endsWith("transactions/op-1")).data.createdAt, "SERVER_TIMESTAMP");
  assert.equal(committed.length, 1);
});

test("projector merges repeated paths into one write", async () => {
  const { committed, database } = makeFirestore();
  const projector = createFirestoreProjector({ database, FieldValue });
  await projector.apply({
    operationId: "op-2",
    balances: { player: 300 },
    projection: { writes: [{ path: "users/player", data: { displayName: "Jogador" } }] },
  });
  assert.equal(committed[0].filter((write) => write.path === "users/player").length, 1);
  assert.deepEqual(committed[0].find((write) => write.path === "users/player").data, {
    displayName: "Jogador",
    balanceCents: "DELETE_FIELD",
  });
});

test("applying the same operation twice writes once, so increments are not doubled", async () => {
  const { committed, database } = makeFirestore();
  const projector = createFirestoreProjector({ database, FieldValue });
  const operation = {
    operationId: "game-9:settlement",
    balances: {},
    projection: { writes: [{ path: "users/player/minigameStats/all", data: { played: increment(1) } }] },
  };
  await projector.apply(operation);
  await projector.apply(operation);
  assert.equal(committed.length, 1);
  assert.equal(committed[0].find((write) => write.path.endsWith("minigameStats/all")).data.played, "INCREMENT(1)");
});

test("a failed commit leaves no receipt, so the retry applies the projection", async () => {
  const { committed, database } = makeFirestore({ failCommits: 1 });
  const projector = createFirestoreProjector({ database, FieldValue });
  const operation = {
    operationId: "game-10:settlement",
    balances: {},
    projection: { writes: [{ path: "users/player", data: { gamesPlayed: increment(1) } }] },
  };
  await assert.rejects(projector.apply(operation), /Firestore unavailable/);
  assert.equal(committed.length, 0);
  await projector.apply(operation);
  assert.equal(committed.length, 1);
});

test("projector rejects an operation without an ID", async () => {
  const { database } = makeFirestore();
  const projector = createFirestoreProjector({ database, FieldValue });
  await assert.rejects(projector.apply({ balances: {}, projection: {} }), TypeError);
});