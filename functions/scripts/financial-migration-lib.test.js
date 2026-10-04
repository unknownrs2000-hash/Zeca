"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { buildBackup, migrateBackupToMongo, verifyMongoAgainstBackup } = require("./financial-migration-lib");

function fakeCollection() {
  const docs = new Map();
  return {
    docs,
    async findOne({ _id }) { return docs.has(_id) ? structuredClone(docs.get(_id)) : null; },
    async insertOne(doc) {
      if (docs.has(doc._id)) throw new Error("duplicate key");
      docs.set(doc._id, structuredClone(doc));
    },
    async updateOne(filter, update) {
      const current = docs.get(filter._id);
      if (!current || (filter.sourceHash && current.sourceHash !== filter.sourceHash)) return { matchedCount: 0 };
      Object.assign(current, update.$set);
      return { matchedCount: 1 };
    },
    async countDocuments(filter) {
      const [[key, value]] = Object.entries(filter);
      return [...docs.values()].filter((doc) => doc[key] === value).length;
    },
  };
}

function fakeMongo() {
  const collections = new Map();
  return {
    collection(name) {
      if (!collections.has(name)) collections.set(name, fakeCollection());
      return collections.get(name);
    },
  };
}

function fakeFirestore(uids) {
  const updates = [];
  const refs = uids.map((id) => ({ id, ref: { path: `users/${id}` } }));
  return {
    updates,
    collection(name) {
      if (name === "leaderboard") return { doc: () => ({ get: async () => ({ exists: false }) }) };
      return {
        orderBy: () => ({
          limit: () => {
            let after = false;
            return {
              startAfter() { after = true; return this; },
              async get() { return after ? { empty: true, docs: [] } : { empty: refs.length === 0, docs: refs }; },
            };
          },
        }),
      };
    },
    batch: () => ({
      update(ref, data) { updates.push({ path: ref.path, data }); },
      async commit() {},
    }),
  };
}

function sampleBackup(extra = {}, transactions) {
  return buildBackup({
    users: [
      {
        uid: "a",
        balanceCents: 700,
        createdAt: 1,
        inventory: [],
        transactions: transactions || [
          { id: "t1", data: { description: "Bônus", deltaCents: 500, type: "bonus", createdAtMs: 10 } },
          { id: "t2", data: { description: "Aposta", deltaCents: 200, type: "game", createdAtMs: 20 } },
        ],
      },
      { uid: "b", balanceCents: 300, createdAt: 2, inventory: [], transactions: [] },
    ],
    ...extra,
  });
}

test("preview validates the backup and writes nothing", async () => {
  const database = fakeMongo();
  const firestore = fakeFirestore(["a", "b"]);
  const result = await migrateBackupToMongo({ database, firestore, backup: sampleBackup(), commit: false });
  assert.equal(result.state, "preview");
  assert.equal(database.collection("wallets").docs.size, 0);
  assert.equal(firestore.updates.length, 0);
});

test("commit imports wallets and ledger, verifies, and a second commit changes nothing", async () => {
  const database = fakeMongo();
  const firestore = fakeFirestore(["a", "b"]);
  const backup = sampleBackup();
  assert.equal((await migrateBackupToMongo({ database, firestore, backup, commit: true })).state, "completed");
  assert.equal(database.collection("wallets").docs.size, 2);
  assert.equal(database.collection("financialTransactions").docs.size, 2);
  assert.equal(firestore.updates.length, 2);
  const report = await verifyMongoAgainstBackup({ database, backup });
  assert.deepEqual(report.balances.map((entry) => entry.actualCents), [700, 300]);
  const again = await migrateBackupToMongo({ database, firestore, backup, commit: true });
  assert.equal(again.state, "already-completed");
  assert.equal(database.collection("wallets").docs.size, 2);
  assert.equal(database.collection("financialTransactions").docs.size, 2);
});

test("verification detects a wallet that diverges from the backup", async () => {
  const database = fakeMongo();
  const firestore = fakeFirestore(["a", "b"]);
  const backup = sampleBackup();
  await migrateBackupToMongo({ database, firestore, backup, commit: true });
  database.collection("wallets").docs.get("a").balanceCents = 1;
  await assert.rejects(verifyMongoAgainstBackup({ database, backup }), /Saldo diverge/);
});

test("open sports bets block the migration", async () => {
  const backup = sampleBackup({ sportsBets: [{ id: "s1", data: { status: "open" } }] });
  await assert.rejects(
    migrateBackupToMongo({ database: fakeMongo(), firestore: fakeFirestore([]), backup, commit: false }),
    /apostas abertas/,
  );
});

test("an invalid ledger entry fails in the preview, before any write", async () => {
  const database = fakeMongo();
  const backup = sampleBackup({}, [{ id: "bad", data: { description: "x", deltaCents: "10" } }]);
  await assert.rejects(
    migrateBackupToMongo({ database, firestore: fakeFirestore([]), backup, commit: false }),
    /Lançamento inválido/,
  );
  assert.equal(database.collection("wallets").docs.size, 0);
});

test("an existing wallet with another balance aborts the retry", async () => {
  const database = fakeMongo();
  const backup = sampleBackup();
  database.collection("wallets").docs.set("a", {
    _id: "a",
    balanceCents: 1,
    sourceMigrationId: backup.migrationId,
  });
  await assert.rejects(
    migrateBackupToMongo({ database, firestore: fakeFirestore(["a", "b"]), backup, commit: true }),
    /diverge da origem/,
  );
});