'use strict';

const CHARGES_COLLECTION = 'cardCharges';

// Guarda só as cobranças. Saldo nunca é alterado aqui: só o applyOperation mexe em carteira.
function createCardsStore({ database }) {
  const charges = database.collection(CHARGES_COLLECTION);

  return {
    async ensureIndexes() {
      await charges.createIndex({ merchantUid: 1, status: 1, expiresAtMs: 1 });
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

    async markPaid(chargeId, patch) {
      await charges.updateOne(
        { _id: chargeId, status: { $in: ['pending', 'paid'] } },
        { $set: { status: 'paid', ...patch } },
      );
    },
  };
}

module.exports = { CHARGES_COLLECTION, createCardsStore };