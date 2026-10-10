'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const credit = require('./credit-logic');

const NOW = Date.UTC(2031, 9, 15);
const invoice = (extra = {}) => ({ cycle: '2031-10', totalCents: 10000, paidCents: 0, ...extra });
const DUE = Date.UTC(2031, 10, 11);

test('buildCreditAccount cria conta ativa com o limite inicial e nada usado', () => {
  const account = credit.buildCreditAccount({ uid: 'u1', nowMs: NOW });
  assert.equal(account.status, 'active');
  assert.equal(account.limitCents, credit.CREDIT_LIMITS.initialLimitCents);
  assert.equal(account.usedCents, 0);
  assert.equal(credit.availableCredit(account), 20000);
});

test('limites inválidos e dono ausente são recusados', () => {
  for (const limitCents of [-1, 1.5, credit.CREDIT_LIMITS.maxLimitCents + 1, '100']) {
    assert.throws(
      () => credit.buildCreditAccount({ uid: 'u1', nowMs: NOW, limitCents }),
      (error) => error instanceof RangeError && error.code === 'INVALID_LIMIT',
    );
  }
  assert.throws(
    () => credit.buildCreditAccount({ uid: '', nowMs: NOW }),
    (error) => error.code === 'INVALID_CREDIT_ACCOUNT',
  );
});

test('checkCanReserve respeita status, valor e limite disponível', () => {
  const account = { ...credit.buildCreditAccount({ uid: 'u1', nowMs: NOW }), usedCents: 15000 };
  assert.deepEqual(credit.checkCanReserve(account, 5000), { ok: true });
  assert.equal(credit.checkCanReserve(account, 5001).reason, 'limit-exceeded');
  assert.equal(credit.checkCanReserve(account, 99).reason, 'invalid-amount');
  assert.equal(credit.checkCanReserve(account, 1.5).reason, 'invalid-amount');
  assert.equal(credit.checkCanReserve({ ...account, status: 'suspended' }, 500).reason, 'credit-suspended');
  assert.equal(credit.checkCanReserve(null, 500).reason, 'credit-not-found');
});

test('availableCredit nunca fica negativo', () => {
  assert.equal(credit.availableCredit({ limitCents: 1000, usedCents: 5000 }), 0);
  assert.equal(credit.availableCredit(null), 0);
});

test('cycleKey usa o mês em UTC e invoiceDates fecha no dia 1 e vence no fim do dia 10', () => {
  assert.equal(credit.cycleKey(NOW), '2031-10');
  assert.equal(credit.cycleKey(Date.UTC(2031, 11, 31, 23, 59)), '2031-12');
  assert.deepEqual(credit.invoiceDates('2031-10'), {
    closesAtMs: Date.UTC(2031, 10, 1),
    dueAtMs: DUE,
  });
});

test('o ciclo de dezembro fecha em janeiro do ano seguinte', () => {
  assert.deepEqual(credit.invoiceDates('2031-12'), {
    closesAtMs: Date.UTC(2032, 0, 1),
    dueAtMs: Date.UTC(2032, 0, 11),
  });
});

test('ciclo inválido é recusado', () => {
  for (const cycle of ['2031-13', '2031-00', '31-10', 'abc', undefined]) {
    assert.throws(() => credit.invoiceDates(cycle), (error) => error.code === 'INVALID_CYCLE');
  }
});

test('invoiceStatusAt percorre aberta, fechada, vencida e paga', () => {
  const closes = Date.UTC(2031, 10, 1);
  assert.equal(credit.invoiceStatusAt(invoice(), closes - 1), 'open');
  assert.equal(credit.invoiceStatusAt(invoice(), closes), 'closed');
  assert.equal(credit.invoiceStatusAt(invoice(), DUE - 1), 'closed');
  assert.equal(credit.invoiceStatusAt(invoice(), DUE), 'overdue');
  assert.equal(credit.invoiceStatusAt(invoice({ paidCents: 10000 }), DUE + 1000), 'paid');
  assert.equal(credit.invoiceStatusAt(invoice({ totalCents: 0 }), closes), 'paid');
});

test('overdueCharges não cobra nada antes do vencimento', () => {
  assert.deepEqual(credit.overdueCharges(invoice(), DUE - 1), {
    outstandingCents: 10000,
    feeCents: 0,
    interestCents: 0,
    totalDueCents: 10000,
  });
});

test('overdueCharges cobra multa no vencimento e juros por dia de atraso', () => {
  const noDia = credit.overdueCharges(invoice(), DUE);
  assert.equal(noDia.feeCents, 200);
  assert.equal(noDia.interestCents, 0);
  assert.equal(noDia.totalDueCents, 10200);

  const depoisDeTresDias = credit.overdueCharges(invoice(), DUE + 3 * credit.DAY_MS);
  assert.equal(depoisDeTresDias.feeCents, 200);
  assert.equal(depoisDeTresDias.interestCents, 99);
  assert.equal(depoisDeTresDias.totalDueCents, 10299);
});

test('overdueCharges calcula sobre o que falta pagar, não sobre o total', () => {
  const parcial = credit.overdueCharges(invoice({ paidCents: 4000 }), DUE);
  assert.equal(parcial.outstandingCents, 6000);
  assert.equal(parcial.feeCents, 120);
  assert.equal(credit.overdueCharges(invoice({ paidCents: 10000 }), DUE + credit.DAY_MS).totalDueCents, 0);
});

test('minimumPayment é 15%, com piso de R$ 10 e teto no que falta', () => {
  assert.equal(credit.minimumPayment(invoice({ totalCents: 10000 })), 1500);
  assert.equal(credit.minimumPayment(invoice({ totalCents: 3000 })), 1000);
  assert.equal(credit.minimumPayment(invoice({ totalCents: 500 })), 500);
  assert.equal(credit.minimumPayment(invoice({ totalCents: 0 })), 0);
  assert.equal(credit.minimumPayment(invoice({ totalCents: 10000, paidCents: 10000 })), 0);
});

test('checkInvoicePayment recusa valor inválido, acima do devido ou sem dívida', () => {
  assert.deepEqual(credit.checkInvoicePayment(5000, 5000), { ok: true });
  assert.deepEqual(credit.checkInvoicePayment(5000, 1), { ok: true });
  assert.equal(credit.checkInvoicePayment(5000, 5001).reason, 'amount-above-due');
  assert.equal(credit.checkInvoicePayment(5000, 0).reason, 'invalid-amount');
  assert.equal(credit.checkInvoicePayment(5000, 1.5).reason, 'invalid-amount');
  assert.equal(credit.checkInvoicePayment(0, 100).reason, 'nothing-to-pay');
});

test('shouldSuspend só dispara com fatura vencida e não paga', () => {
  const faturas = [invoice(), invoice({ cycle: '2031-09', paidCents: 10000 })];
  assert.equal(credit.shouldSuspend(faturas, DUE - 1), false);
  assert.equal(credit.shouldSuspend(faturas, DUE), true);
  assert.equal(credit.shouldSuspend([], DUE), false);
});

test('IDs de operação são estáveis e a compra no crédito usa o mesmo ID do débito', () => {
  assert.equal(credit.creditPurchaseOperationId('c1'), 'card-charge:c1');
  assert.equal(credit.creditPurchaseOperationId('c1'), credit.creditPurchaseOperationId('c1'));
  assert.notEqual(credit.creditPurchaseOperationId('c1'), credit.creditPurchaseOperationId('c2'));
  assert.equal(credit.invoicePaymentOperationId('u1', '2031-10', 'r1'), 'credit-invoice-payment:u1:2031-10:r1');
  assert.notEqual(
    credit.invoicePaymentOperationId('u1', '2031-10', 'r1'),
    credit.invoicePaymentOperationId('u1', '2031-10', 'r2'),
  );
});

test('isReservationStale só vale para reservas antigas e ainda não liquidadas', () => {
  const reserva = { state: 'reserved', createdAtMs: NOW };
  assert.equal(credit.isReservationStale(reserva, NOW + credit.CREDIT_LIMITS.reservationTtlMs - 1), false);
  assert.equal(credit.isReservationStale(reserva, NOW + credit.CREDIT_LIMITS.reservationTtlMs), true);
  assert.equal(credit.isReservationStale({ ...reserva, state: 'settled' }, NOW + 10 * 60 * 1000), false);
  assert.equal(credit.isReservationStale(null, NOW), false);
});

test('Pix Crédito: sub-limite de 30%, taxa de 3% e idade mínima da conta', () => {
  const conta = { limitCents: 20000, usedCents: 0, pixUsedCents: 0, createdAtMs: NOW };
  assert.equal(credit.pixCreditAvailable(conta), 6000);
  assert.equal(credit.pixCreditAvailable({ ...conta, pixUsedCents: 5000 }), 1000);
  assert.equal(credit.pixCreditAvailable({ ...conta, usedCents: 19500 }), 500);
  assert.equal(credit.pixCreditAvailable({ ...conta, pixUsedCents: 9999 }), 0);
  assert.equal(credit.pixCreditFee(1000), 30);
  assert.equal(credit.pixCreditFee(33), 0);
  assert.equal(credit.pixCreditAccountOldEnough(conta, NOW + credit.CREDIT_LIMITS.pixCreditMinAccountAgeMs - 1), false);
  assert.equal(credit.pixCreditAccountOldEnough(conta, NOW + credit.CREDIT_LIMITS.pixCreditMinAccountAgeMs), true);
});