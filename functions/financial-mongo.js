"use strict";

const { MongoClient } = require("mongodb");

const FINANCIAL_DATABASE_NAME = "zeca_financial";
const PENDING_PROJECTION_BATCH_SIZE = 100;

function createMongoFinancialRepository({ client, database }) {
  const wallets = database.collection("wallets");
  const operations = database.collection("financialOperations");
  const ledger = database.collection("financialTransactions");
  const locks = database.collection("financialActionLocks");

  return {
    async withTransaction(callback) {
      const session = client.startSession();
      try {
        let result;
        await session.withTransaction(async () => {
          const transaction = {
            getWallet: (uid) => wallets.findOne({ _id: uid }, { session }),
            setWallet: (uid, wallet) => wallets.updateOne(
              { _id: uid },
              { $set: wallet },
              { upsert: true, session },
            ),
            getOperation: (operationId) => operations.findOne({ _id: operationId }, { session }),
            insertOperation: (operation) => operations.insertOne(operation, { session }),
            getActionLock: (key) => locks.findOne({ _id: key }, { session }),
            promoteActionLock: async (key, token, operationId) => {
              const result = await locks.updateOne(
                { _id: key, token },
                { $set: { operationId }, $unset: { token: "", expiresAtMs: "" } },
                { session },
              );
              if (result.matchedCount !== 1) throw new Error("Action lock ownership changed.");
            },
            insertLedger: (entry, operationId) => ledger.insertOne({
              ...entry,
              _id: `${entry.uid}:${entry.id}`,
              operationId,
            }, { session }),
          };
          result = await callback(transaction);
        }, {
          readPreference: "primary",
          readConcern: { level: "snapshot" },
          writeConcern: { w: "majority" },
        });
        return result;
      } finally {
        await session.endSession();
      }
    },

    getWallet: (uid) => wallets.findOne({ _id: uid }),
    getOperation: (operationId) => operations.findOne({ _id: operationId }),
    listTransactions: async (uid, limit = 50) => {
      const safeLimit = Number.isInteger(limit) ? Math.max(1, Math.min(limit, 100)) : 50;
      return ledger.find({ uid })
        .sort({ createdAtMs: -1, _id: -1 })
        .limit(safeLimit)
        .toArray();
    },
    listTopWallets: async (limit = 50) => {
      const safeLimit = Number.isInteger(limit) ? Math.max(1, Math.min(limit, 100)) : 50;
      return wallets.find({ deletedAtMs: { $exists: false } })
        .sort({ balanceCents: -1, _id: 1 })
        .limit(safeLimit)
        .toArray();
    },
    archiveWallet: async (uid, atMs) => {
      await wallets.updateOne({ _id: uid }, { $set: { deletedAtMs: atMs } });
    },
    markProjected: async (operationId, projectedAtMs) => {
      const session = client.startSession();
      try {
        await session.withTransaction(async () => {
          await operations.updateOne(
            { _id: operationId, status: "pending_projection" },
            { $set: { status: "projected", projectedAtMs } },
            { session },
          );
          await locks.deleteMany({ operationId }, { session });
        }, {
          readPreference: "primary",
          readConcern: { level: "snapshot" },
          writeConcern: { w: "majority" },
        });
      } finally {
        await session.endSession();
      }
    },
    async acquireActionLock(key, token, nowMs, leaseMs) {
      try {
        await locks.findOneAndUpdate(
          { _id: key, expiresAtMs: { $lte: nowMs } },
          { $set: { token, expiresAtMs: nowMs + leaseMs } },
          { upsert: true, returnDocument: "after" },
        );
        return true;
      } catch (error) {
        if (error?.code === 11000) return false;
        throw error;
      }
    },
    getActionLock: (key) => locks.findOne({ _id: key }),
    releaseActionLock: async (key, token) => {
      await locks.deleteOne({ _id: key, token, operationId: { $exists: false } });
    },
    async listPending(limit = PENDING_PROJECTION_BATCH_SIZE) {
      const safeLimit = Number.isInteger(limit) ? Math.max(1, Math.min(limit, 500)) : PENDING_PROJECTION_BATCH_SIZE;
      return operations.find({ status: "pending_projection" })
        .sort({ committedAtMs: 1, _id: 1 })
        .limit(safeLimit)
        .toArray();
    },
  };
}

async function connectFinancialMongo({ env = process.env, MongoClientClass = MongoClient, ensureIndexes = true } = {}) {
  const connectionString = env.ZECA_FINANCIAL_MONGODB_URI;
  if (typeof connectionString !== "string" || !connectionString.trim()) {
    const error = new Error("Configure ZECA_FINANCIAL_MONGODB_URI no ambiente do backend Zeca.");
    error.code = "unavailable";
    throw error;
  }
  const client = new MongoClientClass(connectionString, {
    appName: "zeca-financial-api",
    retryWrites: true,
    serverSelectionTimeoutMS: 10_000,
  });
  try {
    await client.connect();
    const database = client.db(FINANCIAL_DATABASE_NAME);
    await database.command({ ping: 1 });
    if (ensureIndexes) {
      await Promise.all([
        database.collection("wallets").createIndex({ balanceCents: -1, _id: 1 }),
        database.collection("financialTransactions").createIndex({ uid: 1, createdAtMs: -1 }),
        database.collection("financialOperations").createIndex({ status: 1, committedAtMs: 1 }),
      ]);
    }
    const repository = createMongoFinancialRepository({ client, database });
    return {
      client,
      database,
      repository,
      async close() {
        await client.close();
      },
    };
  } catch (error) {
    await client.close().catch(() => {});
    throw error;
  }
}

module.exports = {
  FINANCIAL_DATABASE_NAME,
  createMongoFinancialRepository,
  connectFinancialMongo,
};