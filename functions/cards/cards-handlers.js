'use strict';

const { getFirestore } = require('firebase-admin/firestore');
const { HttpsError, onCall } = require('../callable');
const { getFinancialServices } = require('../financial-runtime');
const { createCardService } = require('./card-service');
const { createCardsStore } = require('./cards-store');
const { createChargeService } = require('./charge-service');
const { createCreditService } = require('./credit-service');
const { createCreditStore } = require('./credit-store');
const { createPaymentService } = require('./payment-service');

const ID_PATTERN = /^[a-f0-9-]{36}$/i;
let servicesPromise = null;

function authenticatedUid(request) {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Entre na sua conta para continuar.');
  return request.auth.uid;
}

function assertMongoMode() {
  if (process.env.ZECA_FINANCIAL_MODE !== 'mongo') {
    throw new HttpsError('failed-precondition', 'A maquininha está indisponível no momento.');
  }
}

async function loadProfile(uid) {
  const snapshot = await getFirestore().collection('users').doc(uid).get();
  if (!snapshot.exists) return null;
  return {
    displayName: snapshot.get('displayName') || 'Jogador',
    username: snapshot.get('username') || '',
  };
}

function getServices() {
  if (!servicesPromise) {
    servicesPromise = (async () => {
      const { store, connection } = await getFinancialServices();
      const cardsStore = createCardsStore({ database: connection.database });
      await cardsStore.ensureIndexes();
      const creditStore = createCreditStore({ database: connection.database });
      await creditStore.ensureIndexes();
      const credit = createCreditService({ store, creditStore });
      return {
        credit,
        cards: createCardService({ cardsStore }),
        charges: createChargeService({ cardsStore, loadProfile }),
        payments: createPaymentService({ store, cardsStore, creditService: credit }),
      };
    })().catch((error) => {
      servicesPromise = null;
      throw error;
    });
  }
  return servicesPromise;
}

function requireId(value, message) {
  if (typeof value !== 'string' || !ID_PATTERN.test(value)) throw new HttpsError('invalid-argument', message);
  return value;
}

exports.createCharge = onCall(async (request) => {
  const merchantUid = authenticatedUid(request);
  assertMongoMode();
  const chargeId = requireId(request.data?.requestId, 'Identificador da cobrança inválido.');
  const { charges } = await getServices();
  return charges.createCharge({
    merchantUid,
    chargeId,
    amountCents: request.data?.amountCents,
    description: request.data?.description,
  });
});

exports.getCharge = onCall(async (request) => {
  authenticatedUid(request);
  assertMongoMode();
  const chargeId = requireId(request.data?.chargeId, 'Cobrança inválida.');
  const { charges } = await getServices();
  return charges.getCharge({ chargeId });
});

exports.payCharge = onCall(async (request) => {
  const payerUid = authenticatedUid(request);
  assertMongoMode();
  const chargeId = requireId(request.data?.chargeId, 'Cobrança inválida.');
  const { payments, cards } = await getServices();

  const rawCardId = request.data?.cardId;
  if (rawCardId === undefined || rawCardId === null || rawCardId === '') {
    return payments.payCharge({ payerUid, chargeId });
  }
  const cardId = requireId(rawCardId, 'Cartão inválido.');
  const pin = request.data?.pin;
  return payments.payCharge({
    payerUid,
    chargeId,
    cardId,
    authorize: () => cards.authorizePayment({ ownerUid: payerUid, cardId, pin }),
  });
});

exports.createCard = onCall(async (request) => {
  const ownerUid = authenticatedUid(request);
  assertMongoMode();
  const cardId = requireId(request.data?.requestId, 'Identificador do cartão inválido.');
  const type = request.data?.type ?? 'debit';
  if (!['debit', 'credit'].includes(type)) throw new HttpsError('invalid-argument', 'Tipo de cartão inválido.');
  const { cards, credit } = await getServices();
  if (type === 'credit') await credit.requireAccount({ uid: ownerUid });
  const profile = await loadProfile(ownerUid);
  return cards.createCard({
    ownerUid,
    cardId,
    pin: request.data?.pin,
    label: request.data?.label,
    holderName: profile?.displayName,
    type,
  });
});

exports.listCards = onCall(async (request) => {
  const ownerUid = authenticatedUid(request);
  assertMongoMode();
  const { cards } = await getServices();
  return { cards: await cards.listCards({ ownerUid }) };
});

exports.setCardStatus = onCall(async (request) => {
  const ownerUid = authenticatedUid(request);
  assertMongoMode();
  const cardId = requireId(request.data?.cardId, 'Cartão inválido.');
  const { cards } = await getServices();
  return cards.setStatus({ ownerUid, cardId, status: request.data?.status });
});

exports.changeCardPin = onCall(async (request) => {
  const ownerUid = authenticatedUid(request);
  assertMongoMode();
  const cardId = requireId(request.data?.cardId, 'Cartão inválido.');
  const { cards } = await getServices();
  return cards.changePin({
    ownerUid,
    cardId,
    currentPin: request.data?.currentPin,
    newPin: request.data?.newPin,
  });
});

exports.getCardHistory = onCall(async (request) => {
  const ownerUid = authenticatedUid(request);
  assertMongoMode();
  const cardId = requireId(request.data?.cardId, 'Cartão inválido.');
  const { cards } = await getServices();
  return { payments: await cards.history({ ownerUid, cardId }) };
});

exports.getCardDetails = onCall(async (request) => {
  const ownerUid = authenticatedUid(request);
  assertMongoMode();
  const cardId = requireId(request.data?.cardId, 'Cartão inválido.');
  const { cards } = await getServices();
  return cards.details({ ownerUid, cardId, pin: request.data?.pin });
});

exports.contractCredit = onCall(async (request) => {
  const uid = authenticatedUid(request);
  assertMongoMode();
  const { credit } = await getServices();
  return credit.contractCredit({ uid });
});

exports.getCreditAccount = onCall(async (request) => {
  const uid = authenticatedUid(request);
  assertMongoMode();
  const { credit } = await getServices();
  return credit.getCreditAccount({ uid });
});

exports.getInvoice = onCall(async (request) => {
  const uid = authenticatedUid(request);
  assertMongoMode();
  const { credit } = await getServices();
  return credit.getInvoice({ uid, cycle: request.data?.cycle });
});