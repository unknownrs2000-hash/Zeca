"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { doc, getDoc, runTransaction, serverTimestamp, setDoc, updateDoc } = require("firebase/firestore");

const PROJECT_ID = "demo-zeca-store-rules";
const RULES = fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8");
const PRODUCTS = {
  frame_aurora: { name: "Moldura Aurora", priceCents: 1_299 },
  title_lucky: { name: "Título: Sorte Grande", priceCents: 799 },
  frame_neon: { name: "Moldura Neon", priceCents: 1_999 },
  avatar_hair_wave: { name: "Cabelo Ondulado", priceCents: 999 },
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
      username: uid,
      profileSetupComplete: true,
      balanceCents,
      balanceInitialized: true,
      level: 1,
      avatarUrl: "",
      avatarAsProfilePhoto: false,
      equippedAvatarItems: [],
      pixKey: "",
      pixKeyType: "",
      pixKeyHash: "",
      gamesPlayed: 0,
      wins: 0,
      inventory: [],
    });
    await database.doc(`leaderboard/${uid}`).set({
      displayName: uid,
      username: uid,
      balanceCents,
      level: 1,
      avatarUrl: "",
      avatarAsProfilePhoto: false,
      equippedAvatarItems: [],
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
      senderUsername: senderUid,
      senderAvatarUrl: "",
      senderAvatarItems: [],
      senderAvatarAsProfilePhoto: false,
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

test("allows a new account to create an incomplete profile before choosing its username", async () => {
  const uid = "new-profile-user";
  const database = environment.authenticatedContext(uid).firestore();
  await assertSucceeds(runTransaction(database, async (transaction) => {
    transaction.set(doc(database, "users", uid), {
      uid,
      displayName: "Jogador Novo",
      username: "",
      profileSetupComplete: false,
      email: "novo@example.com",
      balanceCents: 50_000,
      balanceInitialized: true,
      level: 1,
      avatarUrl: "",
      avatarAsProfilePhoto: false,
      equippedAvatarItems: [],
      pixKey: "",
      pixKeyType: "",
      pixKeyHash: "",
      gamesPlayed: 0,
      wins: 0,
      inventory: [],
      createdAt: serverTimestamp(),
    });
    transaction.set(doc(database, "leaderboard", uid), {
      displayName: "Jogador Novo",
      username: "",
      balanceCents: 50_000,
      level: 1,
      avatarUrl: "",
      avatarAsProfilePhoto: false,
      equippedAvatarItems: [],
    });
  }));
});

test("denies direct client writes to username reservations", async () => {
  const database = environment.authenticatedContext("username-attacker").firestore();
  await assertFails(setDoc(doc(database, "usernames", "already_taken"), { uid: "username-attacker" }));
});

test("group members can read their group and its messages but outsiders cannot", async () => {
  const chatId = "group-chat-test-00000000000000000000";
  await environment.withSecurityRulesDisabled(async (context) => {
    const database = context.firestore();
    await database.doc(`chats/${chatId}`).set({
      type: "group",
      name: "Teste",
      participantUids: ["group-member-a", "group-member-b"],
      streakDays: 0,
    });
    await database.doc(`chats/${chatId}/messages/message-1`).set({ text: "Olá" });
  });

  const member = environment.authenticatedContext("group-member-a").firestore();
  const outsider = environment.authenticatedContext("group-outsider").firestore();
  await assertSucceeds(getDoc(doc(member, "chats", chatId)));
  await assertSucceeds(getDoc(doc(member, "chats", chatId, "messages", "message-1")));
  await assertFails(getDoc(doc(outsider, "chats", chatId)));
  await assertFails(getDoc(doc(outsider, "chats", chatId, "messages", "message-1")));
  await assertSucceeds(updateDoc(doc(member, "chats", chatId, "messages", "message-1"), {
    deletedFor: ["group-member-a"],
  }));
  await assertFails(updateDoc(doc(outsider, "chats", chatId, "messages", "message-1"), {
    deletedFor: ["group-outsider"],
  }));
});

test("group members cannot edit streak or last-message metadata directly", async () => {
  const chatId = "group-chat-locked-000000000000000000";
  await environment.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc(`chats/${chatId}`).set({
      type: "group",
      participantUids: ["group-owner", "group-member"],
      streakDays: 3,
      lastMessage: "Antes",
      lastMessageAt: new Date(),
      lastMessageId: "old-message",
      lastMessageSenderUid: "group-owner",
    });
  });
  const member = environment.authenticatedContext("group-member").firestore();
  await assertFails(updateDoc(doc(member, "chats", chatId), {
    streakDays: 99,
    lastMessage: "Falso",
  }));
});

test("allows purchase of a priced avatar cosmetic", async () => {
  const uid = "avatar-buyer";
  await seedPlayer(uid);
  await assertSucceeds(purchase(uid, "avatar_hair_wave", "purchase-avatar-0001"));
  const database = environment.authenticatedContext(uid).firestore();
  const profile = await getDoc(doc(database, "users", uid));
  assert.equal(profile.data().balanceCents, 49_001);
  assert.deepEqual(profile.data().inventory, ["avatar_hair_wave"]);
});

test("allows legacy profiles to receive only safe default avatar fields once", async () => {
  const uid = "legacy-avatar-profile";
  await environment.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc(`users/${uid}`).set({
      uid,
      displayName: "Jogador Legado",
      email: "legado@example.com",
      balanceCents: 10_000,
      balanceInitialized: true,
      level: 1,
      avatarUrl: "",
      username: "legado",
      profileSetupComplete: true,
      pixKey: "",
      pixKeyType: "",
      pixKeyHash: "",
      gamesPlayed: 2,
      wins: 1,
      inventory: [],
    });
  });
  const database = environment.authenticatedContext(uid).firestore();
  const userRef = doc(database, "users", uid);
  await assertSucceeds(updateDoc(userRef, { avatarAsProfilePhoto: false, equippedAvatarItems: [] }));
  await assertFails(updateDoc(userRef, { equippedAvatarItems: ["avatar_crown_neon"] }));
  await assertFails(updateDoc(userRef, { avatarAsProfilePhoto: true }));
});

test("allows legacy balance initialization and avatar defaults in one atomic update", async () => {
  const uid = "legacy-balance-avatar-profile";
  await environment.withSecurityRulesDisabled(async (context) => {
    await context.firestore().doc(`users/${uid}`).set({
      uid,
      displayName: "Jogador Antigo",
      email: "antigo@example.com",
      balanceCents: 0,
      balanceInitialized: false,
      level: 1,
      avatarUrl: "",
      username: "antigo",
      profileSetupComplete: true,
      pixKey: "",
      pixKeyType: "",
      pixKeyHash: "",
      gamesPlayed: 0,
      wins: 0,
      inventory: [],
    });
  });
  const database = environment.authenticatedContext(uid).firestore();
  await assertSucceeds(updateDoc(doc(database, "users", uid), {
    balanceCents: 50_000,
    balanceInitialized: true,
    avatarAsProfilePhoto: false,
    equippedAvatarItems: [],
  }));
  await assertFails(updateDoc(doc(database, "users", uid), {
    balanceCents: 50_000,
    balanceInitialized: true,
    avatarAsProfilePhoto: false,
    equippedAvatarItems: ["avatar_crown_neon"],
  }));
});