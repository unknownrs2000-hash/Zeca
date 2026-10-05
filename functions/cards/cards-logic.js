'use strict';

const crypto = require('node:crypto');

const CARD_TYPES = Object.freeze({ DEBIT: 'debit', CREDIT: 'credit' });
const CARD_STATUS = Object.freeze({ ACTIVE: 'active', BLOCKED: 'blocked', CANCELLED: 'cancelled' });
const CHARGE_STATUS = Object.freeze({
  PENDING: 'pending',
  PAID: 'paid',
  EXPIRED: 'expired',
  CANCELLED: 'cancelled',
});

// Todas as decisões de negócio ficam aqui. Valores em centavos da moeda BASE (BRL).
const LIMITS = Object.freeze({
  chargeTtlMs: 2 * 60 * 1000,
  tokenTtlMs: 60 * 1000,
  minChargeCents: 100,
  maxChargeCents: 1000000,
  maxDescriptionLength: 80,
  maxPendingChargesPerMerchant: 5,
  maxCardsPerUser: 3,
  maxCardLabelLength: 24,
  maxPinAttempts: 5,
  pinLockMs: 15 * 60 * 1000,
  feeBps: 0,
});

function cardsError(message, code, Tipo = Error) {
  const erro = new Tipo(message);
  erro.code = code;
  return erro;
}

function validateChargeAmount(amountCents) {
  if (!Number.isSafeInteger(amountCents)
      || amountCents < LIMITS.minChargeCents
      || amountCents > LIMITS.maxChargeCents) {
    throw cardsError('Valor da cobrança fora dos limites.', 'INVALID_AMOUNT', RangeError);
  }
  return amountCents;
}

function buildCharge({ id, merchantUid, amountCents, description = '', nowMs }) {
  if (typeof id !== 'string' || !id.trim()) {
    throw cardsError('A cobrança precisa de um ID.', 'INVALID_CHARGE');
  }
  if (typeof merchantUid !== 'string' || !merchantUid.trim()) {
    throw cardsError('A cobrança precisa de um recebedor.', 'INVALID_CHARGE');
  }
  validateChargeAmount(amountCents);
  return {
    _id: id,
    merchantUid,
    amountCents,
    description: String(description || '').trim().slice(0, LIMITS.maxDescriptionLength),
    status: CHARGE_STATUS.PENDING,
    createdAtMs: nowMs,
    expiresAtMs: nowMs + LIMITS.chargeTtlMs,
  };
}

function effectiveChargeStatus(charge, nowMs) {
  if (charge.status === CHARGE_STATUS.PENDING && nowMs >= charge.expiresAtMs) {
    return CHARGE_STATUS.EXPIRED;
  }
  return charge.status;
}

function checkChargePayable(charge, payerUid, nowMs) {
  if (!charge) return { ok: false, reason: 'charge-not-found' };
  if (charge.merchantUid === payerUid) return { ok: false, reason: 'self-payment' };
  const status = effectiveChargeStatus(charge, nowMs);
  if (status === CHARGE_STATUS.EXPIRED) return { ok: false, reason: 'charge-expired' };
  if (status !== CHARGE_STATUS.PENDING) return { ok: false, reason: 'charge-not-pending' };
  return { ok: true };
}

// ID fixo por cobrança: pagar duas vezes a mesma cobrança nunca debita duas vezes.
function chargeOperationId(chargeId) {
  return `card-charge:${chargeId}`;
}

function splitFee(amountCents, feeBps = LIMITS.feeBps) {
  if (!Number.isInteger(feeBps) || feeBps < 0 || feeBps > 10000) {
    throw cardsError('Taxa inválida.', 'INVALID_FEE', RangeError);
  }
  const feeCents = Math.floor((amountCents * feeBps) / 10000);
  return { feeCents, netCents: amountCents - feeCents };
}

function isValidPinFormat(pin) {
  return typeof pin === 'string' && /^\d{4}$/.test(pin);
}

function hashPin(pin, salt = crypto.randomBytes(16).toString('hex')) {
  if (!isValidPinFormat(pin)) {
    throw cardsError('O PIN deve ter 4 dígitos.', 'INVALID_PIN');
  }
  return { salt, hash: crypto.scryptSync(pin, salt, 32).toString('hex') };
}

function verifyPin(pin, record) {
  if (!isValidPinFormat(pin) || !record?.salt || !record?.hash) return false;
  const expected = Buffer.from(record.hash, 'hex');
  const actual = crypto.scryptSync(pin, record.salt, 32);
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

function checkCardUsable(card, nowMs) {
  if (!card) return { ok: false, reason: 'card-not-found' };
  if (card.status === CARD_STATUS.CANCELLED) return { ok: false, reason: 'card-cancelled' };
  if (card.status !== CARD_STATUS.ACTIVE) return { ok: false, reason: 'card-blocked' };
  if ((card.lockedUntilMs || 0) > nowMs) return { ok: false, reason: 'pin-locked' };
  return { ok: true };
}

// Devolve só o trecho a gravar no cartão (patch), sem alterar o objeto recebido.
function registerPinFailure(card, nowMs) {
  const failed = (card.failedPinAttempts || 0) + 1;
  if (failed >= LIMITS.maxPinAttempts) {
    return { failedPinAttempts: 0, lockedUntilMs: nowMs + LIMITS.pinLockMs };
  }
  return { failedPinAttempts: failed, lockedUntilMs: card.lockedUntilMs || 0 };
}

function registerPinSuccess() {
  return { failedPinAttempts: 0, lockedUntilMs: 0 };
}

function hashToken(token) {
  return crypto.createHash('sha256').update(String(token)).digest('hex');
}

// O token em texto só existe na resposta ao app. No banco fica apenas o hash.
function newPaymentToken(nowMs) {
  const token = crypto.randomBytes(24).toString('base64url');
  return { token, tokenHash: hashToken(token), expiresAtMs: nowMs + LIMITS.tokenTtlMs };
}

function buildCard({ id, ownerUid, pin, label = '', nowMs }) {
  if (typeof id !== 'string' || !id.trim()) {
    throw cardsError('O cartão precisa de um ID.', 'INVALID_CARD');
  }
  if (typeof ownerUid !== 'string' || !ownerUid.trim()) {
    throw cardsError('O cartão precisa de um dono.', 'INVALID_CARD');
  }
  const { salt, hash } = hashPin(pin);
  return {
    _id: id,
    ownerUid,
    type: CARD_TYPES.DEBIT,
    status: CARD_STATUS.ACTIVE,
    label: String(label || '').trim().slice(0, LIMITS.maxCardLabelLength) || 'Cartão Zeca',
    // Número fictício só para identificar o cartão na tela. Não vale para nada fora do Zeca.
    last4: crypto.randomInt(0, 10000).toString().padStart(4, '0'),
    pin: { salt, hash },
    failedPinAttempts: 0,
    lockedUntilMs: 0,
    createdAtMs: nowMs,
  };
}

// Ativo <-> bloqueado é livre. Cancelado é definitivo.
function nextCardStatus(current, wanted) {
  if (!Object.values(CARD_STATUS).includes(wanted)) return { ok: false, reason: 'invalid-status' };
  if (current === wanted) return { ok: true };
  if (current === CARD_STATUS.CANCELLED) return { ok: false, reason: 'card-cancelled' };
  return { ok: true };
}

// O que o app pode ver. Nunca inclui o PIN (nem o hash).
function toPublicCard(card, nowMs) {
  return {
    cardId: card._id,
    label: card.label,
    last4: card.last4,
    type: card.type,
    status: card.status,
    locked: (card.lockedUntilMs || 0) > nowMs,
    lockedUntilMs: card.lockedUntilMs || 0,
    createdAtMs: card.createdAtMs,
  };
}

module.exports = {
  buildCard,
  nextCardStatus,
  toPublicCard,
  CARD_STATUS,
  CARD_TYPES,
  CHARGE_STATUS,
  LIMITS,
  buildCharge,
  chargeOperationId,
  checkCardUsable,
  checkChargePayable,
  effectiveChargeStatus,
  hashPin,
  hashToken,
  isValidPinFormat,
  newPaymentToken,
  registerPinFailure,
  registerPinSuccess,
  splitFee,
  validateChargeAmount,
  verifyPin,
};