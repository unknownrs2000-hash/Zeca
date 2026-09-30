"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { doc, getDoc, runTransaction, serverTimestamp, updateDoc } = require("firebase/firestore");

const PROJECT_ID = "demo-zeca-store-rules";
const RULES = fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8");
const PRODUCTS = {
  frame_aurora: { name: "Moldura Aurora", priceCents: 1_299 },
  title_lucky: { name: "Título: Sorte Grande", priceCents: 799 },
  frame_neon: { name: "Moldura Neon", priceCents: 1_999 },
};

let environment;

test.before(async () => {
  environment = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules: RULES,
    },
  });
});

test.after(async () => {
  await environment?.cleanup();
});

async function seedPlayer(uid, balanceCents = 50_000) {
  await environment.withSecurityRulesDisabled(async (context) => {
    const database = context.firestore();
    await database.doc(`users/${uid}`).set({
      uid,
      displayName: uid,
      email: `${uid}@example.com`,
      balanceCents,
      balanceInitialized: true,
      level: 1,
      avatarUrl: "",
      pixKey: "",
      pixKeyType: "",
      pixKeyHash: "",
      gamesPlayed: 0,
      wins: 0,
      inventory: [],
    });
    await database.doc(`leaderboard/${uid}`).set({
      displayName: uid,
      balanceCents,
      level: 1,
      avatarUrl: "",
    });
  });
}

async function purchase(uid, itemId, purchaseId = "purchase-test-0001", amountOverride) {
  const product = PRODUCTS[itemId];
  const price = amountOverride ?? product?.priceCents ?? 0;
  const database = environment.authenticatedContext(uid).firestore();
  const userRef = doc(database, "users", uid);
  const rankRef = doc(database, "leaderboard", uid);
  const purchaseRef = doc(database, "users", uid, "purchases", purchaseId);
  const transactionRef = doc(database, "users", uid, "transactions", purchaseId);

  return runTransaction(database, async (transaction) => {
    const [profile, rank, receipt] = await Promise.all([
      transaction.get(userRef),
      transaction.get(rankRef),
      transaction.get(purchaseRef),
    ]);
    if (receipt.exists()) throw new Error("duplicate purchase");
    const balanceAfter = profile.data().balanceCents - price;
    const inventory = [...profile.data().inventory, itemId];
    transaction.update(userRef, {
      balanceCents: balanceAfter,
      inventory,
      lastPurchaseId: purchaseId,
    });
    transaction.update(rankRef, { balanceCents: balanceAfter });
    transaction.set(purchaseRef, {
      uid,
      itemId,
      amountCents: price,
      createdAt: serverTimestamp(),
    });
    transaction.set(transactionRef, {
      description: `Loja · ${product?.name || "Item inválido"}`,
      deltaCents: -price,
      itemId,
      createdAt: serverTimestamp(),
    });
    return balanceAfter;
  });
}

async function writePrivateMessage(senderUid, recipientUid, messageId, senderUidMetadata = senderUid) {
  const database = environment.authenticatedContext(senderUid).firestore();
  const participantUids = [senderUid, recipientUid].sort();
  const chatId = participantUids.join("_");
  const chatRef = doc(database, "chats", chatId);
  const messageRef = doc(database, "chats", chatId, "messages", messageId);

  return runTransaction(database, async (transaction) => {
    transaction.set(chatRef, {
      participantUids,
      lastMessage: "Olá",
      lastMessageAt: serverTimestamp(),
      lastMessageId: messageId,
      lastMessageSenderUid: senderUidMetadata,
      createdAt: serverTimestamp(),
    });
    transaction.set(messageRef, {
      senderUid,
      senderName: senderUid,
      text: "Olá",
      createdAt: serverTimestamp(),
    });
  });
}

test("allows purchase with fixed catalog price and atomically debits/inserts receipt", async () => {
  const uid = "player-valid";
  await seedPlayer(uid);
  const balanceAfter = await assertSucceeds(purchase(uid, "frame_aurora"));
  assert.equal(balanceAfter, 48_701);

  const database = environment.authenticatedContext(uid).firestore();
  const profile = await getDoc(doc(database, "users", uid));
  assert.equal(profile.data().balanceCents, 48_701);
  assert.deepEqual(profile.data().inventory, ["frame_aurora"]);
  assert.equal(profile.data().lastPurchaseId, "purchase-test-0001");
});

test("denies arbitrary balance changes", async () => {
  const uid = "player-balance-attack";
  await seedPlayer(uid);
  const database = environment.authenticatedContext(uid).firestore();
  await assertFails(updateDoc(doc(database, "users", uid), { balanceCents: 99_999_999 }));
});

test("denies altered cosmetic prices and unknown catalog items", async () => {
  const uid = "player-price-attack";
  await seedPlayer(uid);
  await assertFails(purchase(uid, "frame_aurora", "purchase-bad-price-01", 1));
  await assertFails(purchase(uid, "admin_item", "purchase-unknown-item", 1));
});

test("denies duplicate cosmetic ownership", async () => {
  const uid = "player-duplicate";
  await seedPlayer(uid);
  await assertSucceeds(purchase(uid, "title_lucky", "purchase-title-valid"));
  await assertFails(purchase(uid, "title_lucky", "purchase-title-again"));
});

test("allows a private message with the authenticated sender metadata", async () => {
  await seedPlayer("chat-sender");
  await seedPlayer("chat-recipient");
  await assertSucceeds(writePrivateMessage(
    "chat-sender",
    "chat-recipient",
    "12345678-1234-1234-1234-123456789012",
  ));
});

test("denies private-chat metadata that forges the last sender", async () => {
  await seedPlayer("chat-real-sender");
  await seedPlayer("chat-other-user");
  await seedPlayer("chat-forged-sender");
  await assertFails(writePrivateMessage(
    "chat-real-sender",
    "chat-other-user",
    "87654321-4321-4321-4321-210987654321",
    "chat-forged-sender",
  ));
});