"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { createFinancialStore } = require("./financial-store");
const { connectFinancialMongo } = require("./financial-mongo");
const { createFirestoreProjector } = require("./financial-projection");

const RECONCILIATION_INTERVAL_MS = 30_000;
let runtimePromise;
let reconciliationTimer;
let testStore;

async function initializeFinancialRuntime({
  env = process.env,
  database = getFirestore(),
  connect = connectFinancialMongo,
} = {}) {
  if (testStore) return { store: testStore, connection: null };
  if (runtimePromise) return runtimePromise;
  const attempt = (async () => {
    const connection = await connect({ env });
    try {
      const store = createFinancialStore({
        repository: connection.repository,
        projector: createFirestoreProjector({ database, FieldValue }),
      });
      const firstPass = await store.reconcilePending();
      if (firstPass.failed > 0) {
        console.error(`Reconciliação financeira inicial: ${firstPass.failed} projeções continuam pendentes.`);
      }
      reconciliationTimer = setInterval(() => {
        store.reconcilePending().then((summary) => {
          if (summary.failed > 0) {
            console.error(`Reconciliação financeira: ${summary.failed} projeções continuam pendentes.`);
          }
        }).catch(() => {
          console.error("Falha ao consultar projeções financeiras pendentes.");
        });
      }, RECONCILIATION_INTERVAL_MS);
      reconciliationTimer.unref?.();
      return { store, connection };
    } catch (error) {
      await connection.close().catch(() => {});
      throw error;
    }
  })();
  runtimePromise = attempt;
  attempt.catch(() => {
    if (runtimePromise === attempt) runtimePromise = undefined;
  });
  return attempt;
}

async function getFinancialStore() {
  if (testStore) return testStore;
  const runtime = await initializeFinancialRuntime();
  return runtime.store;
}

async function getFinancialServices() {
  if (testStore) return { store: testStore, connection: { repository: testStore.repository } };
  return initializeFinancialRuntime();
}

function setFinancialStoreForTests(store) {
  testStore = store;
}

async function closeFinancialRuntime() {
  if (reconciliationTimer) clearInterval(reconciliationTimer);
  reconciliationTimer = undefined;
  if (!runtimePromise) return;
  const runtime = await runtimePromise.catch(() => null);
  runtimePromise = undefined;
  await runtime?.connection.close();
}

module.exports = {
  RECONCILIATION_INTERVAL_MS,
  closeFinancialRuntime,
  getFinancialStore,
  getFinancialServices,
  initializeFinancialRuntime,
  setFinancialStoreForTests,
};