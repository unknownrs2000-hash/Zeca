"use strict";

const assert = require("node:assert/strict");
const { createHash, randomInt, randomUUID } = require("node:crypto");
const test = require("node:test");
const { getFirestore } = require("firebase-admin/firestore");
const {
  _completeWhatsAppLink,
  lookupPixKey,
  transferByPixKey,
  unlinkWhatsAppAccount,
} = require("./index");

const database = getFirestore();
const id = randomUUID();
const recipientUid = `phone-pix-recipient-${id}`;
const senderUid = `phone-pix-sender-${id}`;
const secondUid = `phone-pix-second-${id}`;
const phoneNumber = `+1555${String(Date.now()).slice(-7)}${randomInt(100, 1_000)}`;
const jid = `${phoneNumber.slice(1)}@s.whatsapp.net`;
const linkCode = "A1B2C3D4E5F6";
const secondLinkCode = "F6E5D4C3B2A1";
const transferId = randomUUID();
const hash = (value) => createHash("sha256").update(value).digest("hex");
const ownerRef = database.collection("whatsappPhoneOwners").doc(hash(jid));
const linkRef = database.collection("whatsappAccountLinks").doc(hash(jid));
const phonePixKeyRef = database.collection("pixKeys").doc(hash(phoneNumber));
const request = (uid, data = {}) => ({ auth: { uid, token: {} }, data });

async function seedUser(uid, balanceCents = 0) {
  await Promise.all([
    database.collection("users").doc(uid).set({
      uid,
      displayName: uid,
      username: uid,
      profileSetupComplete: true,
      balanceCents,
      level: 3,
      countryCode: "US",
      currencyCode: "BRL",
      avatarUrl: "",
      avatarAsProfilePhoto: false,
      equippedAvatarItems: [],
      equippedFrame: "",
      equippedTitle: "",
    }),
    database.collection("leaderboard").doc(uid).set({
      uid,
      displayName: uid,
      username: uid,
      level: 3,
      balanceCents,
      avatarUrl: "",
      avatarAsProfilePhoto: false,
      equippedAvatarItems: [],
      equippedFrame: "",
    }),
  ]);
}

async function seedLinkCode(uid, code) {
  await database.collection("whatsappLinkCodes").doc(uid).set({
    codeHash: hash(code),
    expiresAtMs: Date.now() + 600_000,
  });
}

test.before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Run this suite using the Firestore emulator.");
  await Promise.all([
    seedUser(recipientUid),
    seedUser(senderUid, 25_000),
    seedUser(secondUid),
    seedLinkCode(recipientUid, linkCode),
  ]);
});

test.after(async () => {
  const transfer = database.collection("transfers").doc(transferId);
  const histories = await Promise.all([
    database.collection("users").doc(senderUid).collection("transactions").get(),
    database.collection("users").doc(recipientUid).collection("transactions").get(),
  ]);
  await Promise.all([
    ...histories.flatMap((snapshot) => snapshot.docs.map((document) => document.ref.delete())),
    database.collection("whatsappLinkCodes").doc(recipientUid).delete(),
    database.collection("whatsappLinkCodes").doc(secondUid).delete(),
    database.collection("pixKeys").doc(hash(phoneNumber)).delete(),
    ownerRef.delete(),
    linkRef.delete(),
    transfer.delete(),
    database.collection("leaderboard").doc(senderUid).delete(),
    database.collection("leaderboard").doc(recipientUid).delete(),
    database.collection("leaderboard").doc(secondUid).delete(),
    database.recursiveDelete(database.collection("users").doc(senderUid)),
    database.recursiveDelete(database.collection("users").doc(recipientUid)),
    database.recursiveDelete(database.collection("users").doc(secondUid)),
  ]);
});

test("WhatsApp-linked phone is unique, discoverable, transferable, and removed on unlink", async () => {
  const linked = await _completeWhatsAppLink({
    data: { code: linkCode, jid },
  });
  assert.deepEqual(linked, { linked: true });

  const [recipient, owner, key, link] = await Promise.all([
    database.collection("users").doc(recipientUid).get(),
    ownerRef.get(),
    phonePixKeyRef.get(),
    linkRef.get(),
  ]);
  assert.equal(recipient.get("phoneNumber"), phoneNumber);
  assert.equal(recipient.get("phoneVerifiedAtMs") > 0, true);
  assert.equal(recipient.get("whatsappPhonePixKeyHash"), hash(phoneNumber));
  assert.equal(owner.get("uid"), recipientUid);
  assert.equal(key.get("uid"), recipientUid);
  assert.equal(key.get("type"), "phone");
  assert.equal(link.get("uid"), recipientUid);

  const details = await lookupPixKey(request(senderUid, { key: phoneNumber }));
  assert.equal(details.uid, recipientUid);
  assert.equal(details.displayName, recipientUid);
  assert.equal(details.currencyCode, "BRL");

  const transfer = await transferByPixKey(request(senderUid, {
    key: phoneNumber,
    amountCents: 2_500,
    requestId: transferId,
  }));
  assert.equal(transfer.recipientName, recipientUid);
  assert.equal(transfer.amountCents, 2_500);
  assert.equal(transfer.feeCents, 0);
  assert.equal(
    (await database.collection("users").doc(recipientUid).get()).get("balanceCents"),
    2_500,
  );

  await seedLinkCode(secondUid, secondLinkCode);
  await assert.rejects(
    _completeWhatsAppLink({
      data: { code: secondLinkCode, jid },
    }),
    (error) => error.code === "already-exists",
  );

  const unlinked = await unlinkWhatsAppAccount(request(recipientUid));
  assert.deepEqual(unlinked, { unlinked: true });
  const [unlinkedProfile, removedKey] = await Promise.all([
    database.collection("users").doc(recipientUid).get(),
    phonePixKeyRef.get(),
  ]);
  assert.equal(unlinkedProfile.get("phoneNumber"), undefined);
  assert.equal(unlinkedProfile.get("phoneVerifiedAtMs"), undefined);
  assert.equal(removedKey.exists, false);
  await assert.rejects(
    lookupPixKey(request(senderUid, { key: phoneNumber })),
    (error) => error.code === "failed-precondition",
  );

  const afterUnlinkCode = "123456ABCDEF";
  await seedLinkCode(secondUid, afterUnlinkCode);
  await assert.rejects(
    _completeWhatsAppLink({
      data: { code: afterUnlinkCode, jid },
    }),
    (error) => error.code === "already-exists",
  );
});
