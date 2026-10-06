'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const logic = require('./cards-logic');

const NOW = 1000000;

function novaCobranca(extra = {}) {
  return logic.buildCharge({ id: 'c1', merchantUid: 'loja', amountCents: 500, nowMs: NOW, ...extra });
}

test('buildCharge cria cobrança pendente com validade curta', () => {
  const charge = novaCobranca({ description: '  Café  ' });
  assert.equal(charge.status, 'pending');
  assert.equal(charge.description, 'Café');
  assert.equal(charge.expiresAtMs, NOW + logic.LIMITS.chargeTtlMs);
});

test('buildCharge recusa valores fora dos limites ou não inteiros', () => {
  for (const amountCents of [0, 99, 1.5, 1000001, '500']) {
    assert.throws(
      () => novaCobranca({ amountCents }),
      (error) => error instanceof RangeError && error.code === 'INVALID_AMOUNT',
    );
  }
});

test('checkChargePayable aceita só cobrança pendente, válida e de outra pessoa', () => {
  const charge = novaCobranca();
  assert.deepEqual(logic.checkChargePayable(charge, 'cliente', NOW + 1000), { ok: true });
  assert.equal(logic.checkChargePayable(charge, 'cliente', charge.expiresAtMs).reason, 'charge-expired');
  assert.equal(logic.checkChargePayable(charge, 'loja', NOW).reason, 'self-payment');
  assert.equal(logic.checkChargePayable({ ...charge, status: 'paid' }, 'cliente', NOW).reason, 'charge-not-pending');
  assert.equal(logic.checkChargePayable(null, 'cliente', NOW).reason, 'charge-not-found');
});

test('chargeOperationId é estável para a mesma cobrança', () => {
  assert.equal(logic.chargeOperationId('c1'), logic.chargeOperationId('c1'));
  assert.notEqual(logic.chargeOperationId('c1'), logic.chargeOperationId('c2'));
});

test('splitFee não cobra taxa por padrão e calcula taxa em pontos-base', () => {
  assert.deepEqual(logic.splitFee(1000), { feeCents: 0, netCents: 1000 });
  assert.deepEqual(logic.splitFee(1000, 250), { feeCents: 25, netCents: 975 });
  for (const feeBps of [-1, 10001, 1.5]) {
    assert.throws(
      () => logic.splitFee(1000, feeBps),
      (error) => error instanceof RangeError && error.code === 'INVALID_FEE',
    );
  }
});

test('PIN: hash confere o certo e recusa o errado ou mal formatado', () => {
  const record = logic.hashPin('1234');
  assert.equal(logic.verifyPin('1234', record), true);
  assert.equal(logic.verifyPin('4321', record), false);
  assert.equal(logic.verifyPin('abcd', record), false);
  assert.throws(() => logic.hashPin('12'), (error) => error.code === 'INVALID_PIN');
});

test('cinco erros de PIN bloqueiam o cartão pelo tempo definido', () => {
  let card = { status: 'active' };
  for (let i = 1; i <= 4; i += 1) {
    card = { ...card, ...logic.registerPinFailure(card, NOW) };
    assert.equal(card.failedPinAttempts, i);
  }
  card = { ...card, ...logic.registerPinFailure(card, NOW) };
  assert.equal(card.failedPinAttempts, 0);
  assert.equal(card.lockedUntilMs, NOW + logic.LIMITS.pinLockMs);
  assert.equal(logic.checkCardUsable(card, NOW + 1).reason, 'pin-locked');
  assert.deepEqual(logic.checkCardUsable(card, card.lockedUntilMs), { ok: true });
});

test('checkCardUsable recusa cartão inexistente, bloqueado ou cancelado', () => {
  assert.equal(logic.checkCardUsable(null, NOW).reason, 'card-not-found');
  assert.equal(logic.checkCardUsable({ status: 'blocked' }, NOW).reason, 'card-blocked');
  assert.equal(logic.checkCardUsable({ status: 'cancelled' }, NOW).reason, 'card-cancelled');
  assert.deepEqual(logic.checkCardUsable({ status: 'active' }, NOW), { ok: true });
});

test('token de pagamento: guarda só o hash, expira rápido e nunca se repete', () => {
  const first = logic.newPaymentToken(NOW);
  const second = logic.newPaymentToken(NOW);
  assert.notEqual(first.token, first.tokenHash);
  assert.equal(logic.hashToken(first.token), first.tokenHash);
  assert.equal(first.expiresAtMs, NOW + logic.LIMITS.tokenTtlMs);
  assert.notEqual(first.token, second.token);
});

test('checkCardUsable recusa cartão vencido só a partir do mês seguinte à validade', () => {
  const card = { status: 'active', expiryMonth: 10, expiryYear: 2031 };
  const lastMomentOfOctober = Date.UTC(2031, 9, 31, 23, 59, 59, 999);
  const firstMomentOfNovember = Date.UTC(2031, 10, 1);
  assert.deepEqual(logic.checkCardUsable(card, lastMomentOfOctober), { ok: true });
  assert.equal(logic.checkCardUsable(card, firstMomentOfNovember).reason, 'card-expired');
  assert.equal(logic.checkCardUsable({ ...card, status: 'cancelled' }, firstMomentOfNovember).reason, 'card-cancelled');
  assert.equal(logic.checkCardUsable({ ...card, status: 'blocked' }, firstMomentOfNovember).reason, 'card-blocked');
  assert.deepEqual(logic.checkCardUsable({ status: 'active' }, firstMomentOfNovember), { ok: true });
});