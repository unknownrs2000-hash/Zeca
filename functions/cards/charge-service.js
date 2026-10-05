'use strict';

const { HttpsError } = require('../callable');
const { LIMITS, buildCharge, effectiveChargeStatus } = require('./cards-logic');

const INVALID_CODES = new Set(['INVALID_AMOUNT', 'INVALID_CHARGE']);

function toPublicCharge(charge, merchant, nowMs) {
  return {
    chargeId: charge._id,
    merchantUid: charge.merchantUid,
    merchantName: merchant?.displayName || 'Jogador',
    merchantUsername: merchant?.username || '',
    amountCents: charge.amountCents,
    description: charge.description,
    status: effectiveChargeStatus(charge, nowMs),
    expiresAtMs: charge.expiresAtMs,
  };
}

function createChargeService({ cardsStore, loadProfile, clock = Date.now }) {
  async function view(charge) {
    return toPublicCharge(charge, await loadProfile(charge.merchantUid), clock());
  }

  function assertSameRequest(existing, wanted) {
    if (existing.merchantUid !== wanted.merchantUid
        || existing.amountCents !== wanted.amountCents
        || existing.description !== wanted.description) {
      throw new HttpsError('already-exists', 'Identificador de cobrança já utilizado.');
    }
  }

  async function createCharge({ merchantUid, chargeId, amountCents, description }) {
    const nowMs = clock();
    let charge;
    try {
      charge = buildCharge({ id: chargeId, merchantUid, amountCents, description, nowMs });
    } catch (error) {
      if (INVALID_CODES.has(error?.code)) throw new HttpsError('invalid-argument', error.message);
      throw error;
    }

    const existing = await cardsStore.getCharge(chargeId);
    if (existing) {
      assertSameRequest(existing, charge);
      return view(existing);
    }

    const pending = await cardsStore.countPendingCharges(merchantUid, nowMs);
    if (pending >= LIMITS.maxPendingChargesPerMerchant) {
      throw new HttpsError('resource-exhausted', 'Você já tem cobranças abertas demais. Aguarde elas expirarem.');
    }

    const result = await cardsStore.insertCharge(charge);
    if (!result.inserted) assertSameRequest(result.charge, charge);
    return view(result.charge);
  }

  async function getCharge({ chargeId }) {
    const charge = await cardsStore.getCharge(chargeId);
    if (!charge) throw new HttpsError('not-found', 'Cobrança não encontrada.');
    return view(charge);
  }

  return { createCharge, getCharge };
}

module.exports = { createChargeService, toPublicCharge };