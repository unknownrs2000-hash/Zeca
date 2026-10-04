"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { closeFinancialRuntime, initializeFinancialRuntime } = require("./financial-runtime");

const database = { doc: () => ({}), runTransaction: async () => {} };

function makeRepository(listPending = async () => []) {
  return {
    withTransaction: async () => {},
    getOperation: async () => undefined,
    listTransactions: async () => [],
    acquireActionLock: async () => true,
    listPending,
  };
}

test("a failed first initialization is retried instead of being cached", async () => {
  let attempts = 0;
  const connect = async () => {
    attempts += 1;
    if (attempts === 1) throw new Error("Mongo unavailable");
    return { repository: makeRepository(), close: async () => {} };
  };
  await assert.rejects(initializeFinancialRuntime({ connect, database }), /Mongo unavailable/);
  const runtime = await initializeFinancialRuntime({ connect, database });
  assert.equal(attempts, 2);
  assert.equal(typeof runtime.store.applyOperation, "function");
  await closeFinancialRuntime();
});

test("a failure after connecting closes that connection and allows a retry", async () => {
  let closed = 0;
  let listCalls = 0;
  const connect = async () => ({
    repository: makeRepository(async () => {
      listCalls += 1;
      if (listCalls === 1) throw new Error("listPending failed");
      return [];
    }),
    close: async () => { closed += 1; },
  });
  await assert.rejects(initializeFinancialRuntime({ connect, database }), /listPending failed/);
  assert.equal(closed, 1);
  await initializeFinancialRuntime({ connect, database });
  await closeFinancialRuntime();
  assert.equal(closed, 2);
});