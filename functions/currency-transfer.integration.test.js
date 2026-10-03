"use strict";

const assert = require("node:assert/strict");
const { createHash, randomUUID } = require("node:crypto");
const test = require("node:test");
const { getFirestore } = require("firebase-admin/firestore");
const { transferByPixKey } = require("./index");

const database = getFirestore();
const today = new Date().toISOString().slice(0, 10);
const id = randomUUID();
const senderUid = `transfer-sender-${id}`;
const recipientUid = `transfer-recipient-${id}`;
const poorSenderUid = `transfer-poor-sender-${id}`;
const poorRecipientUid = `transfer-poor-recipient-${id}`;
const pixKey = `recipient-${id}@example.com`;
const insufficientPixKey = `poor-recipient-${id}@example.com`;
const pixKeyHash = (key) => createHash("sha256").update(key.toLowerCase()).digest("hex");
const refsToDelete = [];

async function seedAccount(uid, currencyCode, balanceCents) {
  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  refsToDelete.push(userRef, rankRef);
  await Promise.all([
    userRef.set({ uid, displayName: uid, balanceCents, currencyCode, countryCode: "US" }),
    rankRef.set({ uid, displayName: uid, balanceCents }),
  ]);
}

async function seedPixKey(key, uid) {
  const keyRef = database.collection("pixKeys").doc(pixKeyHash(key));
  refsToDelete.push(keyRef);
  await keyRef.set({ uid });
}

test.before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Run this suite using the Firestore emulator.");
  const rates = database.collection("systemExchangeRates");
  await Promise.all([
    rates.doc("BRL_USD").set({ rate: 0.2, rateDate: today, cacheDay: today }),
    rates.doc("BRL_EUR").set({ rate: 0.18, rateDate: today, cacheDay: today }),
  ]);
  refsToDelete.push(rates.doc("BRL_USD"), rates.doc("BRL_EUR"));

  await Promise.all([
    seedAccount(senderUid, "USD", 100_000),
    seedAccount(recipientUid, "EUR", 0),
    seedAccount(poorSenderUid, "USD", 50_499),
    seedAccount(poorRecipientUid, "EUR", 0),
    seedPixKey(pixKey, recipientUid),
    seedPixKey(insufficientPixKey, poorRecipientUid),
  ]);
});

test.after(async () => {
  await Promise.all(refsToDelete.map((ref) => ref.delete()));
  const transferDocs = await database.collection("transfers")
    .where("senderUid", "in", [senderUid, poorSenderUid])
    .get();
  await Promise.all(transferDocs.docs.map((doc) => doc.ref.delete()));
  await Promise.all([
    database.collection("systemFinancials").doc("currencyTransferFees").delete(),
    database.recursiveDelete(database.collection("users").doc(senderUid).collection("transactions")),
    database.recursiveDelete(database.collection("users").doc(recipientUid).collection("transactions")),
    database.recursiveDelete(database.collection("users").doc(poorSenderUid).collection("transactions")),
    database.recursiveDelete(database.collection("users").doc(poorRecipientUid).collection("transactions")),
  ]);
});

test("cross-currency settlement is atomic, auditable, and idempotent", async () => {
  const requestId = randomUUID();
  const request = {
    auth: { uid: senderUid, token: {} },
    data: { key: pixKey, amountCents: 10_000, requestId },
  };

  const result = await transferByPixKey(request);
  assert.equal(result.balanceCents, 49_500);
  assert.equal(result.amountCentsInSenderCurrency, 10_000);
  assert.equal(result.amountCents, 50_000);
  assert.equal(result.recipientAmountCents, 9_000);
  assert.equal(result.feeCents, 500);
  assert.equal(result.feeCentsInSenderCurrency, 100);
  assert.equal(result.senderDebitCents, 50_500);
  assert.equal(result.exchangeRate, 0.9);
  assert.equal(result.senderCurrencyCode, "USD");
  assert.equal(result.recipientCurrencyCode, "EUR");

  const repeatedResult = await transferByPixKey(request);
  assert.deepEqual(repeatedResult, result);

  const [sender, recipient, senderHistory, recipientHistory, feeTotals] = await Promise.all([
    database.collection("users").doc(senderUid).get(),
    database.collection("users").doc(recipientUid).get(),
    database.collection("users").doc(senderUid).collection("transactions").doc(requestId).get(),
    database.collection("users").doc(recipientUid).collection("transactions").doc(requestId).get(),
    database.collection("systemFinancials").doc("currencyTransferFees").get(),
  ]);
  assert.equal(sender.get("balanceCents"), 49_500);
  assert.equal(recipient.get("balanceCents"), 50_000);
  assert.equal(senderHistory.get("deltaCents"), -50_500);
  assert.equal(senderHistory.get("feeCentsInSenderCurrency"), 100);
  assert.equal(senderHistory.get("description"), `Para ${recipientUid}`);
  assert.equal(recipientHistory.get("deltaCents"), 50_000);
  assert.equal(recipientHistory.get("recipientAmountCents"), 9_000);
  assert.equal(feeTotals.get("totalFeeCents"), 500);
  assert.equal(feeTotals.get("transferCount"), 1);
});

test("insufficient funds including the currency fee leave both wallets unchanged", async () => {
  const requestId = randomUUID();
  await assert.rejects(
    transferByPixKey({
      auth: { uid: poorSenderUid, token: {} },
      data: { key: insufficientPixKey, amountCents: 10_000, requestId },
    }),
    (error) => error.code === "failed-precondition",
  );

  const [sender, recipient, transfer] = await Promise.all([
    database.collection("users").doc(poorSenderUid).get(),
    database.collection("users").doc(poorRecipientUid).get(),
    database.collection("transfers").doc(requestId).get(),
  ]);
  assert.equal(sender.get("balanceCents"), 50_499);
  assert.equal(recipient.get("balanceCents"), 0);
  assert.equal(transfer.exists, false);
});
