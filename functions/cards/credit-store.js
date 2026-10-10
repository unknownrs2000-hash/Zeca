'use strict';

const ACCOUNTS_COLLECTION = 'creditAccounts';
const PURCHASES_COLLECTION = 'creditPurchases';
const INVOICES_COLLECTION = 'creditInvoices';

const invoiceId = (uid, cycle) => `${uid}:${cycle}`;

// Guarda só limite, compras e faturas. Saldo nunca é alterado aqui: só o applyOperation mexe em carteira.
function createCreditStore({ database }) {
  const accounts = database.collection(ACCOUNTS_COLLECTION);
  const purchases = database.collection(PURCHASES_COLLECTION);
  const invoices = database.collection(INVOICES_COLLECTION);

  return {
    async ensureIndexes() {
      await purchases.createIndex({ uid: 1, state: 1, createdAtMs: 1 });
      await invoices.createIndex({ uid: 1, cycle: 1 }, { unique: true });
    },

    getAccount: (uid) => accounts.findOne({ _id: uid }),

    // Se o ID já existe (corrida entre duas chamadas iguais), devolve o documento salvo.
    async insertAccount(account) {
      try {
        await accounts.insertOne(account);
        return { inserted: true, account };
      } catch (error) {
        if (error?.code === 11000) {
          return { inserted: false, account: await accounts.findOne({ _id: account._id }) };
        }
        throw error;
      }
    },

    // Reserva atômica: só casa se a conta está ativa e usedCents + valor <= limitCents.
    async reserveLimit(uid, amountCents) {
      const result = await accounts.updateOne(
        {
          _id: uid,
          status: 'active',
          $expr: { $lte: [{ $add: ['$usedCents', amountCents] }, '$limitCents'] },
        },
        { $inc: { usedCents: amountCents } },
      );
      return result.modifiedCount === 1;
    },

    // Nunca deixa usedCents negativo.
    async releaseLimit(uid, amountCents) {
      const result = await accounts.updateOne(
        { _id: uid, usedCents: { $gte: amountCents } },
        { $inc: { usedCents: -amountCents } },
      );
      return result.modifiedCount === 1;
    },

    async setAccountStatus(uid, status) {
      await accounts.updateOne({ _id: uid }, { $set: { status } });
    },

    getPurchase: (purchaseId) => purchases.findOne({ _id: purchaseId }),

    // Se o ID já existe (corrida entre duas chamadas iguais), devolve o documento salvo.
    async insertPurchase(purchase) {
      try {
        await purchases.insertOne(purchase);
        return { inserted: true, purchase };
      } catch (error) {
        if (error?.code === 11000) {
          return { inserted: false, purchase: await purchases.findOne({ _id: purchase._id }) };
        }
        throw error;
      }
    },

    // Transição condicional: só muda se a compra ainda está no estado "from".
    async transitionPurchase(purchaseId, from, to, patch = {}) {
      const result = await purchases.updateOne(
        { _id: purchaseId, state: from },
        { $set: { state: to, ...patch } },
      );
      return result.modifiedCount === 1;
    },

    // Reservas paradas há mais de ttlMs (isReservationStale usa >=, então <= aqui).
    listStaleReservations: (uid, nowMs, ttlMs, limit = 20) => purchases
      .find({ uid, state: 'reserved', createdAtMs: { $lte: nowMs - ttlMs } })
      .sort({ createdAtMs: 1 })
      .limit(limit)
      .toArray(),

    getInvoice: (uid, cycle) => invoices.findOne({ _id: invoiceId(uid, cycle) }),

    listInvoices: (uid, limit = 12) => invoices
      .find({ uid })
      .sort({ cycle: -1 })
      .limit(limit)
      .toArray(),

    // Idempotente: purchaseIds impede somar a mesma compra duas vezes.
    // Se o filtro não casa, o upsert tenta inserir o mesmo _id e cai no 11000 (já anexada).
    async addPurchaseToInvoice(uid, cycle, purchaseId, amountCents, nowMs) {
      try {
        await invoices.updateOne(
          { _id: invoiceId(uid, cycle), purchaseIds: { $ne: purchaseId } },
          {
            $inc: { totalCents: amountCents },
            $push: { purchaseIds: purchaseId },
            $setOnInsert: { uid, cycle, paidCents: 0, createdAtMs: nowMs },
          },
          { upsert: true },
        );
        return { attached: true };
      } catch (error) {
        if (error?.code === 11000) return { attached: false };
        throw error;
      }
    },
  };
}

module.exports = {
  ACCOUNTS_COLLECTION,
  INVOICES_COLLECTION,
  PURCHASES_COLLECTION,
  createCreditStore,
};