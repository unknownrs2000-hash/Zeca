'use strict';

const { HttpsError } = require('../callable');
const {
  LIMITS,
  buildCard,
  checkCardUsable,
  hashPin,
  isValidPinFormat,
  nextCardStatus,
  toCardDetails,
  toPublicCard,
  verifyPin,
} = require('./cards-logic');

const USABLE_ERRORS = Object.freeze({
  'card-not-found': ['not-found', 'Cartão não encontrado.'],
  'card-cancelled': ['failed-precondition', 'Este cartão foi cancelado.'],
  'card-blocked': ['failed-precondition', 'Este cartão está bloqueado.'],
  'card-expired': ['failed-precondition', 'Este cartão venceu.'],
  'pin-locked': ['resource-exhausted', 'Cartão travado por erros de PIN. Tente de novo mais tarde.'],
});

const STATUS_ERRORS = Object.freeze({
  'invalid-status': ['invalid-argument', 'Status inválido.'],
  'card-cancelled': ['failed-precondition', 'Este cartão foi cancelado.'],
});

const PIN_FORMAT_MESSAGE = 'O PIN deve ter 4 dígitos.';
const HISTORY_LIMIT = 20;

function createCardService({ cardsStore, clock = Date.now }) {
  async function ownedCard(ownerUid, cardId) {
    const card = await cardsStore.getCard(cardId);
    // Mesmo erro para "não existe" e "é de outra pessoa": não revela cartões alheios.
    if (!card || card.ownerUid !== ownerUid) throw new HttpsError('not-found', 'Cartão não encontrado.');
    return card;
  }

  function assertUsable(card) {
    const check = checkCardUsable(card, clock());
    if (!check.ok) {
      const [code, message] = USABLE_ERRORS[check.reason];
      throw new HttpsError(code, message);
    }
  }

  async function checkPin(card, pin) {
    assertUsable(card);
    if (verifyPin(pin, card.pin)) {
      await cardsStore.clearPinFailures(card._id);
      return;
    }
    await cardsStore.registerPinFailure(card._id, clock(), LIMITS.maxPinAttempts, LIMITS.pinLockMs);
    throw new HttpsError('permission-denied', 'PIN incorreto.');
  }

  async function createCard({ ownerUid, cardId, pin, label, holderName, type }) {
    if (!isValidPinFormat(pin)) throw new HttpsError('invalid-argument', PIN_FORMAT_MESSAGE);

    const existing = await cardsStore.getCard(cardId);
    if (existing) {
      if (existing.ownerUid !== ownerUid) throw new HttpsError('already-exists', 'Identificador de cartão já utilizado.');
      return toPublicCard(existing, clock());
    }

    if (await cardsStore.countOpenCards(ownerUid) >= LIMITS.maxCardsPerUser) {
      throw new HttpsError('resource-exhausted', 'Você já tem o máximo de cartões. Cancele um para criar outro.');
    }

    const sinceMs = clock() - LIMITS.cardCreationWindowMs;
    if (await cardsStore.countRecentCards(ownerUid, sinceMs) >= LIMITS.maxCardsCreatedPerDay) {
      throw new HttpsError('resource-exhausted', 'Você criou cartões demais hoje. Tente de novo amanhã.');
    }

    const nowMs = clock();
    const result = await cardsStore.insertCard(buildCard({ id: cardId, ownerUid, pin, label, holderName, type, nowMs }));
    if (!result.inserted && result.card.ownerUid !== ownerUid) {
      throw new HttpsError('already-exists', 'Identificador de cartão já utilizado.');
    }
    return toPublicCard(result.card, nowMs);
  }

  async function listCards({ ownerUid }) {
    const nowMs = clock();
    return (await cardsStore.listCards(ownerUid)).map((card) => toPublicCard(card, nowMs));
  }

  async function setStatus({ ownerUid, cardId, status }) {
    const card = await ownedCard(ownerUid, cardId);
    const next = nextCardStatus(card.status, status);
    if (!next.ok) {
      const [code, message] = STATUS_ERRORS[next.reason];
      throw new HttpsError(code, message);
    }
    if (card.status !== status) await cardsStore.setCardStatus(card._id, status);
    return toPublicCard({ ...card, status }, clock());
  }

  async function changePin({ ownerUid, cardId, currentPin, newPin }) {
    // Formato do PIN novo primeiro: erro de digitação aqui não pode gastar tentativas do PIN atual.
    if (!isValidPinFormat(newPin)) throw new HttpsError('invalid-argument', PIN_FORMAT_MESSAGE);
    const card = await ownedCard(ownerUid, cardId);
    await checkPin(card, currentPin);
    const { salt, hash } = hashPin(newPin);
    await cardsStore.setPinRecord(card._id, { salt, hash });
    return { ok: true };
  }

  // Chamado pelo payment-service dentro do lock do pagador. Devolve o cartão (ID e tipo) se o PIN confere.
  async function authorizePayment({ ownerUid, cardId, pin }) {
    const card = await ownedCard(ownerUid, cardId);
    await checkPin(card, pin);
    return { cardId: card._id, type: card.type };
  }

  async function history({ ownerUid, cardId }) {
    const card = await ownedCard(ownerUid, cardId);
    const payments = await cardsStore.listCardPayments(ownerUid, card._id, HISTORY_LIMIT);
    return payments.map((charge) => ({
      chargeId: charge._id,
      amountCents: charge.amountCents,
      description: charge.description,
      paidAtMs: charge.paidAtMs,
    }));
  }

  // Número completo e CVV só com o PIN certo (erro de PIN conta tentativa e trava o cartão).
  async function details({ ownerUid, cardId, pin }) {
    const card = await ownedCard(ownerUid, cardId);
    await checkPin(card, pin);
    if (!card.number || !card.cvv) {
      throw new HttpsError('failed-precondition', 'Este cartão não tem dados completos. Cancele-o e crie um novo.');
    }
    return toCardDetails(card, clock());
  }

  return { createCard, listCards, setStatus, changePin, authorizePayment, history, details };
}

module.exports = { createCardService };