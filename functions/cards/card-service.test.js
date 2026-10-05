'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { LIMITS, nextCardStatus } = require('./cards-logic');
const { createCardService } = require('./card-service');
const { createChargeService } = require('./charge-service');
const { createPaymentService } = require('./payment-service');

const NOW = 1000000;

function fakeCardsStore() {
  const cards = new Map();
  const charges = new Map();
  return {
    async getCharge(id) { return charges.has(id) ? structuredClone(charges.get(id)) : null; },
    async insertCharge(charge) {
      if (charges.has(charge._id)) return { inserted: false, charge: structuredClone(charges.get(charge._id)) };
      charges.set(charge._id, structuredClone(charge));
      return { inserted: true, charge };
    },
    async countPendingCharges() { return 0; },
    async markPaid(id, patch) { Object.assign(charges.get(id), { status: 'paid', ...patch }); },
    async listCardPayments(uid, cardId, limit) {
      return [...charges.values()]
        .filter((c) => c.paidByUid === uid && c.paidWithCardId === cardId)
        .sort((a, b) => b.paidAtMs - a.paidAtMs)
        .slice(0, limit);
    },

    async getCard(id) { return cards.has(id) ? structuredClone(cards.get(id)) : null; },
    async listCards(ownerUid) { return [...cards.values()].filter((c) => c.ownerUid === ownerUid).map((c) => structuredClone(c)); },
    async countOpenCards(ownerUid) {
      return [...cards.values()].filter((c) => c.ownerUid === ownerUid && c.status !== 'cancelled').length;
    },
    async countRecentCards(ownerUid, sinceMs) {
      return [...cards.values()].filter((c) => c.ownerUid === ownerUid && c.createdAtMs > sinceMs).length;
    },
    async insertCard(card) {
      if (cards.has(card._id)) return { inserted: false, card: structuredClone(cards.get(card._id)) };
      cards.set(card._id, structuredClone(card));
      return { inserted: true, card };
    },
    async setCardStatus(id, status) { cards.get(id).status = status; },
    async setPinRecord(id, pin) { Object.assign(cards.get(id), { pin, failedPinAttempts: 0, lockedUntilMs: 0 }); },
    async clearPinFailures(id) { Object.assign(cards.get(id), { failedPinAttempts: 0, lockedUntilMs: 0 }); },
    async registerPinFailure(id, nowMs, maxAttempts, lockMs) {
      const card = cards.get(id);
      card.failedPinAttempts = (card.failedPinAttempts || 0) + 1;
      if (card.failedPinAttempts >= maxAttempts) {
        card.failedPinAttempts = 0;
        card.lockedUntilMs = nowMs + lockMs;
      }
    },
  };
}

// Imita só o necessário do financial-store para estes testes.
function fakeFinancial(balances) {
  const operations = new Map();
  return {
    balances,
    async getOperation(id) { return operations.get(id) ?? null; },
    async resumeOperation(id) { return operations.get(id); },
    async withActionLock(key, callback) { return callback({ key }); },
    async applyOperation(input) {
      for (const { uid, deltaCents } of input.entries) balances[uid] = (balances[uid] || 0) + deltaCents;
      const operation = {
        _id: input.operationId,
        status: 'projected',
        result: input.result,
        balances: { ...balances },
        committedAtMs: NOW,
      };
      operations.set(input.operationId, operation);
      return operation;
    },
  };
}

function setup(balances = { cliente: 5000, loja: 0 }) {
  const clock = { now: NOW };
  const cardsStore = fakeCardsStore();
  const store = fakeFinancial({ ...balances });
  return {
    clock,
    cardsStore,
    store,
    cards: createCardService({ cardsStore, clock: () => clock.now }),
    charges: createChargeService({
      cardsStore,
      loadProfile: async (uid) => ({ displayName: uid, username: uid }),
      clock: () => clock.now,
    }),
    payments: createPaymentService({ store, cardsStore, clock: () => clock.now }),
  };
}

const hasCode = (code) => (error) => error.code === code;
const newCard = (extra = {}) => ({ ownerUid: 'cliente', cardId: 'k1', pin: '1234', label: 'Meu cartão', ...extra });
const chargeRequest = (extra = {}) => ({ merchantUid: 'loja', chargeId: 'c1', amountCents: 500, description: 'Café', ...extra });
const auth = (cards, pin, extra = {}) => cards.authorizePayment({ ownerUid: 'cliente', cardId: 'k1', pin, ...extra });

test('createCard é idempotente e nunca expõe o PIN', async () => {
  const { cards } = setup();
  const first = await cards.createCard(newCard());
  const again = await cards.createCard(newCard());
  assert.equal(first.cardId, again.cardId);
  assert.equal(first.status, 'active');
  assert.equal(first.label, 'Meu cartão');
  assert.match(first.last4, /^\d{4}$/);
  assert.equal(first.pin, undefined);
  assert.equal(JSON.stringify(first).includes('hash'), false);
  assert.equal((await cards.listCards({ ownerUid: 'cliente' })).length, 1);
});

test('cada pessoa tem no máximo 3 cartões abertos e cancelar libera a vaga', async () => {
  const { cards } = setup();
  for (let i = 0; i < LIMITS.maxCardsPerUser; i += 1) await cards.createCard(newCard({ cardId: `k${i}` }));
  await assert.rejects(cards.createCard(newCard({ cardId: 'extra' })), hasCode('resource-exhausted'));
  await cards.setStatus({ ownerUid: 'cliente', cardId: 'k0', status: 'cancelled' });
  const created = await cards.createCard(newCard({ cardId: 'extra' }));
  assert.equal(created.cardId, 'extra');
});

test('PIN fora do formato de 4 dígitos é recusado', async () => {
  const { cards } = setup();
  for (const pin of ['12', 'abcd', '12345', undefined]) {
    await assert.rejects(cards.createCard(newCard({ pin })), hasCode('invalid-argument'));
  }
});

test('outra pessoa não vê, bloqueia nem reutiliza o cartão', async () => {
  const { cards } = setup();
  await cards.createCard(newCard());
  await assert.rejects(cards.setStatus({ ownerUid: 'outro', cardId: 'k1', status: 'blocked' }), hasCode('not-found'));
  await assert.rejects(cards.createCard(newCard({ ownerUid: 'outro' })), hasCode('already-exists'));
  assert.deepEqual(await cards.listCards({ ownerUid: 'outro' }), []);
});

test('bloquear e desbloquear é livre, cancelar é definitivo', async () => {
  const { cards } = setup();
  await cards.createCard(newCard());
  const set = (status) => cards.setStatus({ ownerUid: 'cliente', cardId: 'k1', status });
  assert.equal((await set('blocked')).status, 'blocked');
  assert.equal((await set('active')).status, 'active');
  assert.equal((await set('cancelled')).status, 'cancelled');
  await assert.rejects(set('active'), hasCode('failed-precondition'));
  await assert.rejects(set('foo'), hasCode('invalid-argument'));
});

test('nextCardStatus só aceita ativar, bloquear e cancelar', () => {
  assert.deepEqual(nextCardStatus('active', 'blocked'), { ok: true });
  assert.deepEqual(nextCardStatus('blocked', 'active'), { ok: true });
  assert.deepEqual(nextCardStatus('active', 'cancelled'), { ok: true });
  assert.equal(nextCardStatus('cancelled', 'active').reason, 'card-cancelled');
  assert.equal(nextCardStatus('active', 'foo').reason, 'invalid-status');
});

test('cinco erros de PIN travam o cartão até o tempo passar', async () => {
  const { cards, clock } = setup();
  await cards.createCard(newCard());
  for (let i = 0; i < LIMITS.maxPinAttempts; i += 1) {
    await assert.rejects(auth(cards, '0000'), hasCode('permission-denied'));
  }
  await assert.rejects(auth(cards, '1234'), hasCode('resource-exhausted'));
  clock.now = NOW + LIMITS.pinLockMs;
  assert.equal(await auth(cards, '1234'), 'k1');
});

test('changePin exige o PIN atual e o PIN antigo deixa de valer', async () => {
  const { cards } = setup();
  await cards.createCard(newCard());
  const change = (currentPin, newPin) => cards.changePin({ ownerUid: 'cliente', cardId: 'k1', currentPin, newPin });
  await assert.rejects(change('9999', '4321'), hasCode('permission-denied'));
  await assert.rejects(change('1234', 'abc'), hasCode('invalid-argument'));
  await change('1234', '4321');
  await assert.rejects(auth(cards, '1234'), hasCode('permission-denied'));
  assert.equal(await auth(cards, '4321'), 'k1');
});

test('pagar com cartão exige o PIN, move o dinheiro uma vez e aparece no histórico', async () => {
  const { cards, charges, payments, store, cardsStore } = setup();
  await cards.createCard(newCard());
  await charges.createCharge(chargeRequest());
  const pay = (pin) => payments.payCharge({
    payerUid: 'cliente',
    chargeId: 'c1',
    cardId: 'k1',
    authorize: () => auth(cards, pin),
  });

  await assert.rejects(pay('0000'), hasCode('permission-denied'));
  assert.equal(store.balances.cliente, 5000);
  assert.equal((await cardsStore.getCharge('c1')).status, 'pending');

  const receipt = await pay('1234');
  assert.equal(receipt.status, 'paid');
  assert.equal(store.balances.cliente, 4500);
  assert.equal(store.balances.loja, 500);

  await pay('1234');
  assert.equal(store.balances.cliente, 4500);

  const history = await cards.history({ ownerUid: 'cliente', cardId: 'k1' });
  assert.equal(history.length, 1);
  assert.equal(history[0].chargeId, 'c1');
  assert.equal(history[0].amountCents, 500);
});

test('cartão bloqueado não paga e o saldo fica intacto', async () => {
  const { cards, charges, payments, store } = setup();
  await cards.createCard(newCard());
  await cards.setStatus({ ownerUid: 'cliente', cardId: 'k1', status: 'blocked' });
  await charges.createCharge(chargeRequest());
  await assert.rejects(
    payments.payCharge({
      payerUid: 'cliente',
      chargeId: 'c1',
      cardId: 'k1',
      authorize: () => auth(cards, '1234'),
    }),
    hasCode('failed-precondition'),
  );
  assert.equal(store.balances.cliente, 5000);
});

function luhnValid(number) {
  let sum = 0;
  for (let i = number.length - 1, doubled = false; i >= 0; i -= 1, doubled = !doubled) {
    let digit = Number(number[i]);
    if (doubled) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
  }
  return sum % 10 === 0;
}

test('número completo e CVV só saem com o PIN certo e o cartão parece de verdade', async () => {
  const { cards } = setup();
  await cards.createCard(newCard({ holderName: 'Ana Souza' }));

  const listed = (await cards.listCards({ ownerUid: 'cliente' }))[0];
  assert.equal(listed.number, undefined);
  assert.equal(JSON.stringify(listed).includes('cvv'), false);
  assert.equal(listed.holderName, 'ANA SOUZA');
  assert.equal(listed.expiry, '01/75');

  const details = (pin) => cards.details({ ownerUid: 'cliente', cardId: 'k1', pin });
  await assert.rejects(details('0000'), hasCode('permission-denied'));
  const full = await details('1234');
  assert.match(full.number, /^9\d{15}$/);
  assert.equal(luhnValid(full.number), true);
  assert.match(full.cvv, /^\d{3}$/);
  assert.equal(full.number.slice(-4), listed.last4);
  await assert.rejects(
    cards.details({ ownerUid: 'outro', cardId: 'k1', pin: '1234' }),
    hasCode('not-found'),
  );
});

test('há um limite de cartões criados por dia, mesmo cancelando, e ele libera depois da janela', async () => {
  const { cards, clock } = setup();
  const create = (cardId) => cards.createCard(newCard({ cardId }));
  const cancel = (cardId) => cards.setStatus({ ownerUid: 'cliente', cardId, status: 'cancelled' });

  for (let i = 0; i < LIMITS.maxCardsCreatedPerDay; i += 1) {
    await create(`k${i}`);
    await cancel(`k${i}`);
  }
  await assert.rejects(create('extra'), hasCode('resource-exhausted'));

  clock.now = NOW + LIMITS.cardCreationWindowMs;
  assert.equal((await create('extra')).cardId, 'extra');
});