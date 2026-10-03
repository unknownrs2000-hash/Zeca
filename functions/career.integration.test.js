"use strict";

const assert = require("node:assert/strict");
const { createHash, randomInt, randomUUID } = require("node:crypto");
const test = require("node:test");
const { getFirestore } = require("firebase-admin/firestore");
const {
  applyWorkJob,
  completeWorkShift,
  getAccountCurrency,
  getLeaderboardCurrencyInfo,
  getWorkCareer,
  setAccountCountry,
  startWorkShift,
  _operateWhatsAppCareer,
} = require("./index");

const database = getFirestore();
const today = new Date().toISOString().slice(0, 10);
const uid = `career-player-${randomUUID()}`;
const jid = `1555${Date.now()}${randomInt(1000, 10_000)}@s.whatsapp.net`;
const whatsappLinkRef = database.collection("whatsappAccountLinks")
  .doc(createHash("sha256").update(jid).digest("hex"));
const request = (data = {}) => ({ auth: { uid, token: {} }, data });
const playerRef = database.collection("users").doc(uid);
const rankRef = database.collection("leaderboard").doc(uid);

test.before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Run this suite using the Firestore emulator.");
  await playerRef.set({
    uid,
    displayName: "Career Player",
    profileSetupComplete: true,
    balanceCents: 10_000,
    level: 1,
    countryCode: "",
    currencyCode: "",
    whatsappLink: { jid },
    career: { legacyImported: true },
  });
  await whatsappLinkRef.set({ uid });
  await rankRef.set({ uid, displayName: "Career Player", balanceCents: 10_000 });
  const rates = database.collection("systemExchangeRates");
  await Promise.all([
    rates.doc("BRL_USD").set({ rate: 0.2, rateDate: today, cacheDay: today }),
    rates.doc("BRL_EUR").set({ rate: 0.18, rateDate: today, cacheDay: today }),
  ]);
});

test.after(async () => {
  const histories = await playerRef.collection("transactions").get();
  const workSessions = await playerRef.collection("workSessions").get();
  await Promise.all([
    ...histories.docs.map((doc) => doc.ref.delete()),
    ...workSessions.docs.map((doc) => doc.ref.delete()),
    playerRef.delete(),
    rankRef.delete(),
    whatsappLinkRef.delete(),
    database.collection("systemExchangeRates").doc("BRL_USD").delete(),
    database.collection("systemExchangeRates").doc("BRL_EUR").delete(),
  ]);
});

test("account country and currency are locked after the initial choice", async () => {
  const firstChoice = await setAccountCountry(request({ countryCode: "US", currencyCode: "USD" }));
  assert.equal(firstChoice.currencyCode, "USD");

  const currency = await getAccountCurrency(request());
  assert.equal(currency.currencyCode, "USD");
  const rank = await rankRef.get();
  assert.equal(rank.get("countryCode"), "US");
  assert.equal(rank.get("currencyCode"), "USD");
  assert.equal(rank.get("currencyRate"), 0.2);

  await assert.rejects(
    setAccountCountry(request({ countryCode: "US", currencyCode: "EUR" })),
    (error) => error.code === "failed-precondition",
  );
  const unchanged = await playerRef.get();
  assert.equal(unchanged.get("countryCode"), "US");
  assert.equal(unchanged.get("currencyCode"), "USD");
});

test("leaderboard currency hydration uses each player's saved currency", async () => {
  await rankRef.set({ uid, displayName: "Career Player", balanceCents: 10_000 });

  const result = await getLeaderboardCurrencyInfo(request({ uids: [uid] }));

  assert.deepEqual(result.players, [{
    uid,
    countryCode: "US",
    currencyCode: "USD",
    currencyRate: 0.2,
    currencyRateDate: today,
  }]);
  const rank = await rankRef.get();
  assert.equal(rank.get("currencyCode"), "USD");
  assert.equal(rank.get("currencyRate"), 0.2);
});

test("app career shift pays the shared wallet once and enforces the cooldown", async () => {
  const status = await getWorkCareer(request());
  assert.equal(status.jobs.length, 35);
  assert.equal(status.currentJob, null);

  const hired = await applyWorkJob(request({ jobSlug: "entregador_pizza" }));
  assert.equal(hired.job.tier, 1);
  const sharedStatus = await _operateWhatsAppCareer({ data: { action: "status", jid } });
  assert.equal(sharedStatus.linked, true);
  assert.equal(sharedStatus.currentJob.slug, "entregador_pizza");

  const sessionId = randomUUID();
  const challenge = await startWorkShift(request({ requestId: sessionId }));
  assert.equal(challenge.sessionId, sessionId);
  assert.equal(challenge.options.length, 4);

  const sharedChallenge = await _operateWhatsAppCareer({
    data: { action: "status", jid },
  });
  assert.equal(sharedChallenge.activeSession.sessionId, sessionId);

  const sessionSnapshot = await playerRef.collection("workSessions").doc(sessionId).get();
  const answerIndex = sessionSnapshot.get("answerIndex");
  const result = await _operateWhatsAppCareer({
    data: { action: "complete", jid, sessionId, answerIndex },
  });
  assert.equal(result.correct, true);
  assert.ok(result.salaryCents >= 400);
  assert.equal(result.balanceCents, 10_000 + result.salaryCents);
  assert.equal(result.shiftsInTier, 1);

  const duplicate = await _operateWhatsAppCareer({
    data: { action: "complete", jid, sessionId, answerIndex },
  });
  assert.deepEqual(duplicate, result);
  const [player, rank, history] = await Promise.all([
    playerRef.get(),
    rankRef.get(),
    playerRef.collection("transactions").doc(`work_${sessionId}`).get(),
  ]);
  assert.equal(player.get("balanceCents"), result.balanceCents);
  assert.equal(rank.get("balanceCents"), result.balanceCents);
  assert.equal(history.get("deltaCents"), result.salaryCents);

  await assert.rejects(
    startWorkShift(request({ requestId: randomUUID() })),
    (error) => error.code === "resource-exhausted",
  );
});
