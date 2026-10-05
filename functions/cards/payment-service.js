'use strict';

const { HttpsError } = require('../callable');
const { SERVER_TIMESTAMP } = require('../financial-projection');
const { chargeOperationId, checkChargePayable, splitFee } = require('./cards-logic');

const PAYABLE_ERRORS = Object.freeze({
  'charge-not-found': ['not-found', 'Cobrança não encontrada.'],
  'self-payment': ['invalid-argument', 'Você não pode pagar a própria cobrança.'],
  'charge-expired': ['failed-precondition', 'Esta cobrança expirou. Peça uma nova.'],
  'charge-not-pending': ['failed-precondition', 'Esta cobrança já foi paga ou encerrada.'],
});

function createPaymentService({ store, cardsStore, clock = Date.now }) {
  // Conclui a cobrança a partir de uma operação já gravada (primeira vez ou repetição).
  async function finish(charge, operation, payerUid, cardId) {
    const fingerprint = operation.result?.requestFingerprint || {};
    if (fingerprint.payerUid !== payerUid) {
      throw new HttpsError('failed-precondition', 'Esta cobrança já foi paga por outra pessoa.');
    }
    const settled = operation.status === 'projected'
      ? operation
      : await store.resumeOperation(operation._id);
    await cardsStore.markPaid(charge._id, {
      paidByUid: payerUid,
      paidAtMs: settled.committedAtMs || clock(),
      operationId: operation._id,
      ...(cardId ? { paidWithCardId: cardId } : {}),
    });
    return { ...operation.result.response, balanceCents: settled.balances[payerUid] };
  }

  async function payCharge({ payerUid, chargeId, cardId, authorize }) {
    const charge = await cardsStore.getCharge(chargeId);
    if (!charge) throw new HttpsError('not-found', 'Cobrança não encontrada.');

    const operationId = chargeOperationId(chargeId);
    const prior = await store.getOperation(operationId);
    if (prior) return finish(charge, prior, payerUid, cardId);

    const check = checkChargePayable(charge, payerUid, clock());
    if (!check.ok) {
      const [code, message] = PAYABLE_ERRORS[check.reason];
      throw new HttpsError(code, message);
    }

    return store.withActionLock(`financial-action:${payerUid}`, async (lockLease) => {
      const concurrent = await store.getOperation(operationId);
      if (concurrent) return finish(charge, concurrent, payerUid, cardId);

      // Autorização do cartão (PIN) dentro do lock: tentativas do mesmo pagador entram em fila.
      if (authorize) await authorize();

      const { feeCents, netCents } = splitFee(charge.amountCents);
      const label = charge.description || 'cobrança';
      const response = {
        chargeId,
        status: 'paid',
        amountCents: charge.amountCents,
        feeCents,
        merchantUid: charge.merchantUid,
        description: charge.description,
      };

      let operation;
      try {
        operation = await store.applyOperation({
          operationId,
          operationType: 'card_charge_payment',
          entries: [
            { uid: payerUid, deltaCents: -charge.amountCents },
            { uid: charge.merchantUid, deltaCents: netCents },
          ],
          minimumBalances: [{ uid: payerUid, minimumBalanceCents: charge.amountCents }],
          ledger: [
            {
              uid: payerUid,
              id: `charge_${chargeId}`,
              description: `Maquininha · pagamento · ${label}`,
              deltaCents: -charge.amountCents,
              type: 'card_charge_payment',
              chargeId,
              counterpartyUid: charge.merchantUid,
              createdAt: SERVER_TIMESTAMP,
            },
            {
              uid: charge.merchantUid,
              id: `charge_${chargeId}`,
              description: `Maquininha · recebimento · ${label}`,
              deltaCents: netCents,
              type: 'card_charge_received',
              chargeId,
              counterpartyUid: payerUid,
              feeCents,
              createdAt: SERVER_TIMESTAMP,
            },
          ],
          lockLease,
          projection: { writes: [] },
          result: {
            response,
            requestFingerprint: { payerUid, merchantUid: charge.merchantUid, amountCents: charge.amountCents },
          },
        });
      } catch (error) {
        // Outra pessoa pagou a mesma cobrança no mesmo instante.
        if (error?.name === 'IdempotencyConflictError') {
          throw new HttpsError('already-exists', 'Esta cobrança já foi paga por outra pessoa.');
        }
        // Erro comum do financial-store: vira mensagem clara em vez de "Erro interno.".
        if (error?.name === 'InsufficientBalanceError') {
          throw new HttpsError('failed-precondition', 'Saldo insuficiente para pagar esta cobrança.');
        }
        throw error;
      }
      return finish(charge, operation, payerUid, cardId);
    });
  }

  return { payCharge };
}

module.exports = { createPaymentService };