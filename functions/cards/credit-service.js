'use strict';

const { HttpsError } = require('../callable');
const { SERVER_TIMESTAMP } = require('../financial-projection');
const { splitFee } = require('./cards-logic');
const credit = require('./credit-logic');

const RESERVE_ERRORS = Object.freeze({
  'credit-not-found': ['failed-precondition', 'Contrate o crédito antes de usar este cartão.'],
  'credit-suspended': ['failed-precondition', 'Seu crédito está suspenso por fatura em atraso.'],
  'invalid-amount': ['invalid-argument', 'Valor inválido para o crédito.'],
  'limit-exceeded': ['failed-precondition', 'Limite de crédito insuficiente.'],
});

function reserveError(reason) {
  const [code, message] = RESERVE_ERRORS[reason];
  return new HttpsError(code, message);
}

function publicAccount(account) {
  return {
    contracted: true,
    status: account.status,
    limitCents: account.limitCents,
    usedCents: account.usedCents,
    availableCents: credit.availableCredit(account),
  };
}

function publicInvoice(invoice, nowMs) {
  const { closesAtMs, dueAtMs } = credit.invoiceDates(invoice.cycle);
  return {
    cycle: invoice.cycle,
    status: credit.invoiceStatusAt(invoice, nowMs),
    totalCents: invoice.totalCents || 0,
    paidCents: invoice.paidCents || 0,
    minimumPaymentCents: credit.minimumPayment(invoice),
    ...credit.overdueCharges(invoice, nowMs),
    closesAtMs,
    dueAtMs,
  };
}

function createCreditService({ store, creditStore, clock = Date.now }) {
  // Anexa à fatura (idempotente) e só depois marca como liquidada: se cair no meio, a recuperação repete.
  async function settlePurchase(purchase) {
    await creditStore.addPurchaseToInvoice(
      purchase.uid, purchase.cycle, purchase._id, purchase.amountCents, clock(),
    );
    await creditStore.transitionPurchase(purchase._id, 'reserved', 'settled', { settledAtMs: clock() });
  }

  // A transição condicional vem primeiro: só quem a vence libera o limite (nunca libera duas vezes).
  async function abandonPurchase(purchase) {
    const moved = await creditStore.transitionPurchase(purchase._id, 'reserved', 'released', {
      releasedAtMs: clock(),
    });
    if (moved) await creditStore.releaseLimit(purchase.uid, purchase.amountCents);
  }

  async function recoverReservations(uid) {
    const nowMs = clock();
    const stale = await creditStore.listStaleReservations(uid, nowMs, credit.CREDIT_LIMITS.reservationTtlMs);
    for (const purchase of stale) {
      if (!credit.isReservationStale(purchase, nowMs)) continue;
      const operation = await store.getOperation(purchase._id);
      if (operation) {
        if (operation.status !== 'projected') await store.resumeOperation(operation._id);
        await settlePurchase(purchase);
      } else {
        await abandonPurchase(purchase);
      }
    }
  }

  async function contractCredit({ uid }) {
    const existing = await creditStore.getAccount(uid);
    if (existing) return publicAccount(existing);
    const result = await creditStore.insertAccount(credit.buildCreditAccount({ uid, nowMs: clock() }));
    return publicAccount(result.account);
  }

  async function requireAccount({ uid }) {
    const account = await creditStore.getAccount(uid);
    if (!account) throw reserveError('credit-not-found');
    return account;
  }

  async function getCreditAccount({ uid }) {
    await recoverReservations(uid);
    const account = await creditStore.getAccount(uid);
    return account ? publicAccount(account) : { contracted: false };
  }

  async function getInvoice({ uid, cycle }) {
    const nowMs = clock();
    const wanted = cycle || credit.cycleKey(nowMs);
    try {
      credit.invoiceDates(wanted);
    } catch {
      throw new HttpsError('invalid-argument', 'Ciclo de fatura inválido.');
    }
    await recoverReservations(uid);
    const invoice = await creditStore.getInvoice(uid, wanted)
      || { uid, cycle: wanted, totalCents: 0, paidCents: 0 };
    return publicInvoice(invoice, nowMs);
  }

  // Chamado pelo payment-service dentro do lock do pagador. Devolve a operação gravada.
  async function chargeOnCredit({ payerUid, charge, lockLease }) {
    const nowMs = clock();
    const purchaseId = credit.creditPurchaseOperationId(charge._id);
    await recoverReservations(payerUid);

    const existing = await creditStore.getPurchase(purchaseId);
    if (!existing || existing.state === 'released') {
      const check = credit.checkCanReserve(await creditStore.getAccount(payerUid), charge.amountCents);
      if (!check.ok) throw reserveError(check.reason);
      if (!await creditStore.reserveLimit(payerUid, charge.amountCents)) throw reserveError('limit-exceeded');

      const claimed = existing
        ? await creditStore.transitionPurchase(purchaseId, 'released', 'reserved', { createdAtMs: nowMs })
        : (await creditStore.insertPurchase({
          _id: purchaseId,
          uid: payerUid,
          chargeId: charge._id,
          merchantUid: charge.merchantUid,
          amountCents: charge.amountCents,
          description: charge.description,
          cycle: credit.cycleKey(nowMs),
          state: 'reserved',
          createdAtMs: nowMs,
        })).inserted;
      // Perdeu a corrida para outra chamada igual: devolve a reserva extra.
      if (!claimed) await creditStore.releaseLimit(payerUid, charge.amountCents);
    }
    // Se já havia reserva "reserved" (queda antes do applyOperation), ela é reaproveitada.

    const { feeCents, netCents } = splitFee(charge.amountCents);
    const label = charge.description || 'cobrança';
    const response = {
      chargeId: charge._id,
      status: 'paid',
      method: 'credit',
      amountCents: charge.amountCents,
      feeCents,
      merchantUid: charge.merchantUid,
      description: charge.description,
    };

    try {
      return await store.applyOperation({
        operationId: purchaseId,
        operationType: 'card_charge_payment',
        // Só o recebedor entra em entries: o saldo do pagador não muda.
        entries: [{ uid: charge.merchantUid, deltaCents: netCents }],
        ledger: [
          {
            uid: payerUid,
            id: `charge_${charge._id}`,
            description: `Crédito · ${label}`,
            deltaCents: 0,
            type: 'credit_charge_payment',
            chargeId: charge._id,
            counterpartyUid: charge.merchantUid,
            createdAt: SERVER_TIMESTAMP,
          },
          {
            uid: charge.merchantUid,
            id: `charge_${charge._id}`,
            description: `Maquininha · recebimento · ${label}`,
            deltaCents: netCents,
            type: 'card_charge_received',
            chargeId: charge._id,
            counterpartyUid: payerUid,
            feeCents,
            createdAt: SERVER_TIMESTAMP,
          },
        ],
        lockLease,
        projection: { writes: [] },
        result: {
          response,
          requestFingerprint: {
            payerUid, merchantUid: charge.merchantUid, amountCents: charge.amountCents, method: 'credit',
          },
        },
      });
    } catch (error) {
      // Outra pessoa pagou a mesma cobrança: nada foi aplicado por nós, então devolve o limite.
      if (error?.name === 'IdempotencyConflictError') {
        await abandonPurchase({ _id: purchaseId, uid: payerUid, amountCents: charge.amountCents });
        throw new HttpsError('already-exists', 'Esta cobrança já foi paga por outra pessoa.');
      }
      // Qualquer outro erro: resultado incerto. Não libera; a recuperação decide pelo getOperation.
      throw error;
    }
  }

  // Depois da operação gravada (primeira vez ou repetição): fatura + limite disponível.
  async function settleCharge({ payerUid, operation }) {
    const purchase = await creditStore.getPurchase(credit.creditPurchaseOperationId(operation.result.response.chargeId));
    if (purchase?.state === 'reserved') await settlePurchase(purchase);
    const account = await creditStore.getAccount(payerUid);
    return {
      availableCreditCents: credit.availableCredit(account),
      invoiceCycle: purchase?.cycle || '',
    };
  }

  return {
    contractCredit,
    requireAccount,
    getCreditAccount,
    getInvoice,
    chargeOnCredit,
    settleCharge,
    recoverReservations,
  };
}

module.exports = { createCreditService };