'use strict';

const CHARGES_COLLECTION = 'cardCharges';
const CARDS_COLLECTION = 'cards';

// Guarda só as cobranças. Saldo nunca é alterado aqui: só o applyOperation mexe em carteira.
function createCardsStore({ database }) {
  const charges = database.collection(CHARGES_COLLECTION);
  const cards = database.collection(CARDS_COLLECTION);

  return {
    async ensureIndexes() {
      await charges.createIndex({ merchantUid: 1, status: 1, expiresAtMs: 1 });
      await charges.createIndex({ paidByUid: 1, paidWithCardId: 1, paidAtMs: -1 });
      await cards.createIndex({ ownerUid: 1, createdAtMs: 1 });
    },

    getCharge: (chargeId) => charges.findOne({ _id: chargeId }),

    // Se o ID já existe (corrida entre duas chamadas iguais), devolve o documento salvo.
    async insertCharge(charge) {
      try {
        await charges.insertOne(charge);
        return { inserted: true, charge };
      } catch (error) {
        if (error?.code === 11000) {
          return { inserted: false, charge: await charges.findOne({ _id: charge._id }) };
        }
        throw error;
      }
    },

    countPendingCharges: (merchantUid, nowMs) => charges.countDocuments({
      merchantUid,
      status: 'pending',
      expiresAtMs: { $gt: nowMs },
    }),

    getCard: (cardId) => cards.findOne({ _id: cardId }),

    listCards: (ownerUid) => cards.find({ ownerUid }).sort({ createdAtMs: 1 }).toArray(),

    countOpenCards: (ownerUid) => cards.countDocuments({ ownerUid, status: { $ne: 'cancelled' } }),

    // Conta cartões criados depois de sinceMs, inclusive os cancelados (usa o índice ownerUid + createdAtMs).
    countRecentCards: (ownerUid, sinceMs) => cards.countDocuments({ ownerUid, createdAtMs: { $gt: sinceMs } }),

    // Se o ID já existe (corrida entre duas chamadas iguais), devolve o documento salvo.
    async insertCard(card) {
      try {
        await cards.insertOne(card);
        return { inserted: true, card };
      } catch (error) {
        if (error?.code === 11000) {
          return { inserted: false, card: await cards.findOne({ _id: card._id }) };
        }
        throw error;
      }
    },

    async setCardStatus(cardId, status) {
      await cards.updateOne({ _id: cardId }, { $set: { status } });
    },

    async setPinRecord(cardId, pin) {
      await cards.updateOne({ _id: cardId }, { $set: { pin, failedPinAttempts: 0, lockedUntilMs: 0 } });
    },

    async clearPinFailures(cardId) {
      await cards.updateOne({ _id: cardId }, { $set: { failedPinAttempts: 0, lockedUntilMs: 0 } });
    },

    // Contagem atômica ($inc): tentativas simultâneas de PIN não passam sem ser contadas.
    async registerPinFailure(cardId, nowMs, maxAttempts, lockMs) {
      await cards.updateOne({ _id: cardId }, { $inc: { failedPinAttempts: 1 } });
      await cards.updateOne(
        { _id: cardId, failedPinAttempts: { $gte: maxAttempts } },
        { $set: { failedPinAttempts: 0, lockedUntilMs: nowMs + lockMs } },
      );
    },

    listCardPayments: (payerUid, cardId, limit) => charges
      .find({ paidByUid: payerUid, paidWithCardId: cardId })
      .sort({ paidAtMs: -1 })
      .limit(limit)
      .toArray(),

    async markPaid(chargeId, patch) {
      await charges.updateOne(
        { _id: chargeId, status: { $in: ['pending', 'paid'] } },
        { $set: { status: 'paid', ...patch } },
      );
    },
  };
}

module.exports = { CARDS_COLLECTION, CHARGES_COLLECTION, createCardsStore };