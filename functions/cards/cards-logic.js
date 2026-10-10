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
  maxCardsCreatedPerDay: 5,
  cardCreationWindowMs: 24 * 60 * 60 * 1000,
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

// O cartão vale até o fim do mês de validade (UTC). Sem validade gravada, não vence.
function isCardExpired(card, nowMs) {
  if (!card.expiryMonth || !card.expiryYear) return false;
  return nowMs >= Date.UTC(card.expiryYear, card.expiryMonth, 1);
}

function checkCardUsable(card, nowMs) {
  if (!card) return { ok: false, reason: 'card-not-found' };
  if (card.status === CARD_STATUS.CANCELLED) return { ok: false, reason: 'card-cancelled' };
  if (card.status !== CARD_STATUS.ACTIVE) return { ok: false, reason: 'card-blocked' };
  if (isCardExpired(card, nowMs)) return { ok: false, reason: 'card-expired' };
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

const CARD_BIN_PREFIX = '9';
const CARD_VALIDITY_YEARS = 5;
const CARD_HOLDER_MAX_LENGTH = 26;

// Dígito verificador de Luhn: o número fictício parece um cartão de verdade.
function luhnCheckDigit(partial) {
  let sum = 0;
  for (let i = partial.length - 1, doubled = true; i >= 0; i -= 1, doubled = !doubled) {
    let digit = Number(partial[i]);
    if (doubled) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
  }
  return (10 - (sum % 10)) % 10;
}

// Começa com 9 (prefixo que nenhuma bandeira real usa): nunca coincide com um cartão real.
function generateCardNumber() {
  let body = CARD_BIN_PREFIX;
  while (body.length < 15) body += crypto.randomInt(0, 10);
  return body + luhnCheckDigit(body);
}

function generateCvv() {
  return crypto.randomInt(0, 1000).toString().padStart(3, '0');
}

function cardExpiry(nowMs) {
  const date = new Date(nowMs);
  return { expiryMonth: date.getUTCMonth() + 1, expiryYear: date.getUTCFullYear() + CARD_VALIDITY_YEARS };
}

function formatExpiry(month, year) {
  if (!month || !year) return '';
  return `${String(month).padStart(2, '0')}/${String(year % 100).padStart(2, '0')}`;
}

function buildCard({ id, ownerUid, pin, label = '', holderName = '', type = CARD_TYPES.DEBIT, nowMs }) {
  if (!Object.values(CARD_TYPES).includes(type)) {
    throw cardsError('Tipo de cartão inválido.', 'INVALID_CARD');
  }
  if (typeof id !== 'string' || !id.trim()) {
    throw cardsError('O cartão precisa de um ID.', 'INVALID_CARD');
  }
  if (typeof ownerUid !== 'string' || !ownerUid.trim()) {
    throw cardsError('O cartão precisa de um dono.', 'INVALID_CARD');
  }
  const { salt, hash } = hashPin(pin);
  const number = generateCardNumber();
  const { expiryMonth, expiryYear } = cardExpiry(nowMs);
  return {
    _id: id,
    ownerUid,
    type,
    status: CARD_STATUS.ACTIVE,
    label: String(label || '').trim().slice(0, LIMITS.maxCardLabelLength) || 'Cartão Zeca',
    // Dados fictícios: não valem para nada fora do Zeca.
    number,
    last4: number.slice(-4),
    cvv: generateCvv(),
    expiryMonth,
    expiryYear,
    holderName: String(holderName || '').trim().toUpperCase().slice(0, CARD_HOLDER_MAX_LENGTH) || 'JOGADOR ZECA',
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

// O que o app pode ver na lista. Nunca inclui o PIN, o número completo nem o CVV.
function toPublicCard(card, nowMs) {
  return {
    cardId: card._id,
    label: card.label,
    last4: card.last4,
    holderName: card.holderName || '',
    expiry: formatExpiry(card.expiryMonth, card.expiryYear),
    type: card.type,
    status: card.status,
    locked: (card.lockedUntilMs || 0) > nowMs,
    lockedUntilMs: card.lockedUntilMs || 0,
    createdAtMs: card.createdAtMs,
  };
}

// Só sai depois de o PIN conferir (ver card-service.details).
function toCardDetails(card, nowMs) {
  return { ...toPublicCard(card, nowMs), number: card.number, cvv: card.cvv };
}

module.exports = {
  buildCard,
  nextCardStatus,
  toCardDetails,
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