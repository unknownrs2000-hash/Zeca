'use strict';

const DAY_MS = 24 * 60 * 60 * 1000;

const CREDIT_STATUS = Object.freeze({ ACTIVE: 'active', SUSPENDED: 'suspended' });
const INVOICE_STATUS = Object.freeze({
  OPEN: 'open',
  CLOSED: 'closed',
  PAID: 'paid',
  OVERDUE: 'overdue',
});

// Todas as decisões de negócio do crédito ficam aqui. Valores em centavos da moeda BASE (BRL).
const CREDIT_LIMITS = Object.freeze({
  initialLimitCents: 20000,
  maxLimitCents: 100000,
  minPurchaseCents: 100,
  dueDay: 10,
  lateFeeBps: 200,
  lateInterestBpsPerDay: 33,
  minimumPaymentBps: 1500,
  minimumPaymentFloorCents: 1000,
  reservationTtlMs: 5 * 60 * 1000,
  // Usados só na Fase 5 (Pix Crédito).
  pixCreditSubLimitBps: 3000,
  pixCreditFeeBps: 300,
  pixCreditDailyCapCents: 5000,
  pixCreditMinAccountAgeMs: 7 * DAY_MS,
});

function creditError(message, code, Tipo = Error) {
  const erro = new Tipo(message);
  erro.code = code;
  return erro;
}

function validateLimit(limitCents) {
  if (!Number.isSafeInteger(limitCents) || limitCents < 0 || limitCents > CREDIT_LIMITS.maxLimitCents) {
    throw creditError('Limite de crédito fora dos limites.', 'INVALID_LIMIT', RangeError);
  }
  return limitCents;
}

function buildCreditAccount({ uid, nowMs, limitCents = CREDIT_LIMITS.initialLimitCents }) {
  if (typeof uid !== 'string' || !uid.trim()) {
    throw creditError('A conta de crédito precisa de um dono.', 'INVALID_CREDIT_ACCOUNT');
  }
  validateLimit(limitCents);
  return {
    _id: uid,
    uid,
    status: CREDIT_STATUS.ACTIVE,
    limitCents,
    usedCents: 0,
    createdAtMs: nowMs,
  };
}

function availableCredit(account) {
  if (!account) return 0;
  return Math.max(0, (account.limitCents || 0) - (account.usedCents || 0));
}

function checkCanReserve(account, amountCents) {
  if (!account) return { ok: false, reason: 'credit-not-found' };
  if (account.status !== CREDIT_STATUS.ACTIVE) return { ok: false, reason: 'credit-suspended' };
  if (!Number.isSafeInteger(amountCents) || amountCents < CREDIT_LIMITS.minPurchaseCents) {
    return { ok: false, reason: 'invalid-amount' };
  }
  if (amountCents > availableCredit(account)) return { ok: false, reason: 'limit-exceeded' };
  return { ok: true };
}

function parseCycle(cycle) {
  const match = /^(\d{4})-(\d{2})$/.exec(String(cycle));
  const month = match ? Number(match[2]) : 0;
  if (!match || month < 1 || month > 12) {
    throw creditError('Ciclo de fatura inválido.', 'INVALID_CYCLE');
  }
  return { year: Number(match[1]), monthIndex: month - 1 };
}

// A compra entra na fatura do mês em que foi feita (UTC).
function cycleKey(nowMs) {
  const date = new Date(nowMs);
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

// Fecha no dia 1 do mês seguinte e vence no fim do dia de vencimento.
function invoiceDates(cycle) {
  const { year, monthIndex } = parseCycle(cycle);
  return {
    closesAtMs: Date.UTC(year, monthIndex + 1, 1),
    dueAtMs: Date.UTC(year, monthIndex + 1, CREDIT_LIMITS.dueDay + 1),
  };
}

function outstandingCents(invoice) {
  return Math.max(0, (invoice.totalCents || 0) - (invoice.paidCents || 0));
}

function invoiceStatusAt(invoice, nowMs) {
  const { closesAtMs, dueAtMs } = invoiceDates(invoice.cycle);
  if (nowMs < closesAtMs) return INVOICE_STATUS.OPEN;
  if (outstandingCents(invoice) === 0) return INVOICE_STATUS.PAID;
  if (nowMs >= dueAtMs) return INVOICE_STATUS.OVERDUE;
  return INVOICE_STATUS.CLOSED;
}

// Multa única e juros por dia, sempre sobre o que ainda falta pagar (sem juros sobre juros).
function overdueCharges(invoice, nowMs) {
  const { dueAtMs } = invoiceDates(invoice.cycle);
  const outstanding = outstandingCents(invoice);
  if (outstanding === 0 || nowMs < dueAtMs) {
    return { outstandingCents: outstanding, feeCents: 0, interestCents: 0, totalDueCents: outstanding };
  }
  const days = Math.floor((nowMs - dueAtMs) / DAY_MS);
  const feeCents = Math.floor((outstanding * CREDIT_LIMITS.lateFeeBps) / 10000);
  const interestCents = Math.floor((outstanding * CREDIT_LIMITS.lateInterestBpsPerDay * days) / 10000);
  return {
    outstandingCents: outstanding,
    feeCents,
    interestCents,
    totalDueCents: outstanding + feeCents + interestCents,
  };
}

function minimumPayment(invoice) {
  const outstanding = outstandingCents(invoice);
  if (outstanding === 0) return 0;
  const proportional = Math.floor((outstanding * CREDIT_LIMITS.minimumPaymentBps) / 10000);
  return Math.min(outstanding, Math.max(proportional, CREDIT_LIMITS.minimumPaymentFloorCents));
}

function checkInvoicePayment(totalDueCents, amountCents) {
  if (!Number.isSafeInteger(amountCents) || amountCents < 1) return { ok: false, reason: 'invalid-amount' };
  if (totalDueCents === 0) return { ok: false, reason: 'nothing-to-pay' };
  if (amountCents > totalDueCents) return { ok: false, reason: 'amount-above-due' };
  return { ok: true };
}

function shouldSuspend(invoices, nowMs) {
  return invoices.some((invoice) => invoiceStatusAt(invoice, nowMs) === INVOICE_STATUS.OVERDUE);
}

// Mesmo ID da compra no débito: pagar a mesma cobrança duas vezes nunca cobra duas vezes.
function creditPurchaseOperationId(chargeId) {
  return `card-charge:${chargeId}`;
}

function invoicePaymentOperationId(uid, cycle, requestId) {
  return `credit-invoice-payment:${uid}:${cycle}:${requestId}`;
}

function isReservationStale(purchase, nowMs) {
  return purchase?.state === 'reserved'
    && nowMs - (purchase.createdAtMs || 0) >= CREDIT_LIMITS.reservationTtlMs;
}

// Pix Crédito (Fase 5): sub-limite, taxa e idade mínima da conta. O teto diário fica no serviço.
function pixCreditAvailable(account) {
  const subLimit = Math.floor(((account?.limitCents || 0) * CREDIT_LIMITS.pixCreditSubLimitBps) / 10000);
  const used = account?.pixUsedCents || 0;
  return Math.min(availableCredit(account), Math.max(0, subLimit - used));
}

function pixCreditFee(amountCents) {
  return Math.floor((amountCents * CREDIT_LIMITS.pixCreditFeeBps) / 10000);
}

function pixCreditAccountOldEnough(account, nowMs) {
  return nowMs - (account?.createdAtMs || nowMs) >= CREDIT_LIMITS.pixCreditMinAccountAgeMs;
}

module.exports = {
  CREDIT_LIMITS,
  CREDIT_STATUS,
  DAY_MS,
  INVOICE_STATUS,
  availableCredit,
  buildCreditAccount,
  checkCanReserve,
  checkInvoicePayment,
  creditPurchaseOperationId,
  cycleKey,
  invoiceDates,
  invoicePaymentOperationId,
  invoiceStatusAt,
  isReservationStale,
  minimumPayment,
  outstandingCents,
  overdueCharges,
  pixCreditAccountOldEnough,
  pixCreditAvailable,
  pixCreditFee,
  shouldSuspend,
  validateLimit,
};