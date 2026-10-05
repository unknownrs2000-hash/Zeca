'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { LIMITS } = require('./cards-logic');
const { createChargeService } = require('./charge-service');
const { createPaymentService } = require('./payment-service');

const NOW = 1000000;

function fakeCardsStore() {
  const charges = new Map();
  return {
    async getCharge(id) { return charges.has(id) ? structuredClone(charges.get(id)) : null; },
    async insertCharge(charge) {
      if (charges.has(charge._id)) return { inserted: false, charge: structuredClone(charges.get(charge._id)) };
      charges.set(charge._id, structuredClone(charge));
      return { inserted: true, charge };
    },
    async countPendingCharges(merchantUid, nowMs) {
      return [...charges.values()]
        .filter((c) => c.merchantUid === merchantUid && c.status === 'pending' && c.expiresAtMs > nowMs).length;
    },
    async markPaid(id, patch) { Object.assign(charges.get(id), { status: 'paid', ...patch }); },
  };
}

// Imita só o contrato do financial-store: idempotência por ID, saldo mínimo e lançamentos atômicos.
function fakeStore(balances) {
  const operations = new Map();
  return {
    balances,
    async getOperation(id) { return operations.get(id) ?? null; },
    async resumeOperation(id) { return operations.get(id) ?? null; },
    async withActionLock(key, callback) { return callback({ key, token: 'token' }); },
    async applyOperation(input) {
      const hash = JSON.stringify([input.entries, input.result.requestFingerprint]);
      const existing = operations.get(input.operationId);
      if (existing) {
        if (existing.hash !== hash) {
          const error = new Error('conflict');
          error.name = 'IdempotencyConflictError';
          throw error;
        }
        return existing;
      }
      for (const requirement of input.minimumBalances) {
        if ((balances[requirement.uid] ?? 0) < requirement.minimumBalanceCents) {
          const error = new Error('Saldo insuficiente.');
          error.name = 'InsufficientBalanceError';
          throw error;
        }
      }
      for (const { uid, deltaCents } of input.entries) balances[uid] = (balances[uid] || 0) + deltaCents;
      const operation = {
        _id: input.operationId,
        status: 'projected',
        hash,
        result: input.result,
        balances: { ...balances },
        committedAtMs: NOW,
      };
      operations.set(input.operationId, operation);
      return operation;
    },
  };
}

function setup(balances = { cliente: 5000, outro: 5000, loja: 0 }) {
  const clock = { now: NOW };
  const cardsStore = fakeCardsStore();
  const store = fakeStore({ ...balances });
  const loadProfile = async (uid) => ({ displayName: uid, username: uid });
  return {
    clock,
    cardsStore,
    store,
    charges: createChargeService({ cardsStore, loadProfile, clock: () => clock.now }),
    payments: createPaymentService({ store, cardsStore, clock: () => clock.now }),
  };
}

const request = (extra = {}) => ({ merchantUid: 'loja', chargeId: 'c1', amountCents: 500, description: 'Café', ...extra });
const hasCode = (code) => (error) => error.code === code;

test('createCharge é idempotente e recusa reutilizar o ID com outro valor', async () => {
  const { charges } = setup();
  const first = await charges.createCharge(request());
  const again = await charges.createCharge(request());
  assert.equal(first.chargeId, again.chargeId);
  assert.equal(first.merchantName, 'loja');
  await assert.rejects(charges.createCharge(request({ amountCents: 600 })), hasCode('already-exists'));
});

test('um recebedor não pode ter cobranças abertas demais', async () => {
  const { charges } = setup();
  for (let i = 0; i < LIMITS.maxPendingChargesPerMerchant; i += 1) {
    await charges.createCharge(request({ chargeId: `c${i}` }));
  }
  await assert.rejects(charges.createCharge(request({ chargeId: 'extra' })), hasCode('resource-exhausted'));
});

test('payCharge move o dinheiro uma vez só, mesmo repetindo a chamada', async () => {
  const { charges, payments, store, cardsStore } = setup();
  await charges.createCharge(request());
  const first = await payments.payCharge({ payerUid: 'cliente', chargeId: 'c1' });
  const second = await payments.payCharge({ payerUid: 'cliente', chargeId: 'c1' });
  assert.equal(store.balances.cliente, 4500);
  assert.equal(store.balances.loja, 500);
  assert.equal(first.status, 'paid');
  assert.equal(second.balanceCents, 4500);
  assert.equal((await cardsStore.getCharge('c1')).status, 'paid');
});

test('outra pessoa não consegue pagar uma cobrança já paga', async () => {
  const { charges, payments, store } = setup();
  await charges.createCharge(request());
  await payments.payCharge({ payerUid: 'cliente', chargeId: 'c1' });
  await assert.rejects(payments.payCharge({ payerUid: 'outro', chargeId: 'c1' }), hasCode('failed-precondition'));
  assert.equal(store.balances.outro, 5000);
});

test('cobrança expirada não pode ser paga', async () => {
  const { charges, payments, store, clock } = setup();
  await charges.createCharge(request());
  clock.now = NOW + LIMITS.chargeTtlMs;
  await assert.rejects(payments.payCharge({ payerUid: 'cliente', chargeId: 'c1' }), hasCode('failed-precondition'));
  assert.equal(store.balances.cliente, 5000);
});

test('o recebedor não pode pagar a própria cobrança', async () => {
  const { charges, payments } = setup();
  await charges.createCharge(request());
  await assert.rejects(payments.payCharge({ payerUid: 'loja', chargeId: 'c1' }), hasCode('invalid-argument'));
});

test('saldo insuficiente não move dinheiro e a cobrança continua pendente', async () => {
  const { charges, payments, store, cardsStore } = setup({ cliente: 100, loja: 0 });
  await charges.createCharge(request());
  await assert.rejects(
    payments.payCharge({ payerUid: 'cliente', chargeId: 'c1' }),
    (error) => error.name === 'InsufficientBalanceError',
  );
  assert.equal(store.balances.cliente, 100);
  assert.equal((await cardsStore.getCharge('c1')).status, 'pending');
});

test('pagamento simultâneo de outra pessoa vira erro claro', async () => {
  const { charges, payments, store } = setup();
  await charges.createCharge(request());
  store.applyOperation = async () => {
    const error = new Error('conflict');
    error.name = 'IdempotencyConflictError';
    throw error;
  };
  await assert.rejects(payments.payCharge({ payerUid: 'cliente', chargeId: 'c1' }), hasCode('already-exists'));
});

test('getCharge mostra o status real e recusa cobrança inexistente', async () => {
  const { charges, clock } = setup();
  await charges.createCharge(request());
  clock.now = NOW + LIMITS.chargeTtlMs;
  assert.equal((await charges.getCharge({ chargeId: 'c1' })).status, 'expired');
  await assert.rejects(charges.getCharge({ chargeId: 'nao-existe' }), hasCode('not-found'));
});