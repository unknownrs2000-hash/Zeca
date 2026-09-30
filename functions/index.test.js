"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  adminAdjustBalance,
  adminDeleteUser,
  adminListUsers,
  adminUpdateGameSettings,
  adminUpdateUserInventory,
  createTugRoom,
  joinTugRoom,
  listFootballMatches,
  listTugRooms,
  manageTugRoom,
  placeSportsBet,
  pullTugRope,
  settleMySportsBets,
  sendChatMessage,
  signChatAudioUpload,
  startMines,
} = require("./index");

test("admin callables reject unauthenticated and non-admin requests", async () => {
  await assert.rejects(
    adminListUsers({ auth: null, data: {} }),
    (error) => error.code === "unauthenticated",
  );
  const regularUser = { auth: { uid: "player", token: {} }, data: {} };
  await assert.rejects(adminListUsers(regularUser), (error) => error.code === "permission-denied");
  await assert.rejects(adminAdjustBalance(regularUser), (error) => error.code === "permission-denied");
  await assert.rejects(adminUpdateGameSettings(regularUser), (error) => error.code === "permission-denied");
  await assert.rejects(adminUpdateUserInventory(regularUser), (error) => error.code === "permission-denied");
  await assert.rejects(adminDeleteUser(regularUser), (error) => error.code === "permission-denied");
});

test("admin inventory changes reject unknown items before accessing Firestore", async () => {
  await assert.rejects(
    adminUpdateUserInventory({
      auth: { uid: "admin", token: { admin: true } },
      data: {
        uid: "player",
        action: "add",
        itemId: "unknown_item",
        requestId: "123e4567-e89b-42d3-a456-426614174000",
        reason: "Ajuste de inventário solicitado",
      },
    }),
    (error) => error.code === "invalid-argument",
  );
});

test("Mines rejects invalid mine counts before starting a transaction", async () => {
  await assert.rejects(
    startMines({
      auth: { uid: "player", token: {} },
      data: { amountCents: 100, mineCount: 25, requestId: "123e4567-e89b-42d3-a456-426614174000" },
    }),
    (error) => error.code === "invalid-argument",
  );
});

test("audio upload signing and chat sending require authentication", async () => {
  await assert.rejects(
    signChatAudioUpload({ auth: null, data: {} }),
    (error) => error.code === "unauthenticated",
  );
  await assert.rejects(
    sendChatMessage({ auth: null, data: {} }),
    (error) => error.code === "unauthenticated",
  );
});

test("chat audio rejects untrusted URLs before accessing Firestore", async () => {
  await assert.rejects(
    sendChatMessage({
      auth: { uid: "player", token: {} },
      data: {
        text: "",
        audioUrl: "https://audio.example.net/voice.m4a",
        audioPublicId: "123e4567-e89b-42d3-a456-426614174000",
        audioDurationMs: 2_000,
        requestId: "123e4567-e89b-42d3-a456-426614174000",
      },
    }),
    (error) => error.code === "invalid-argument",
  );
});

test("new multiplayer and sports callables reject unauthenticated requests", async () => {
  const unauthenticated = { auth: null, data: {} };
  for (const callable of [
    createTugRoom,
    joinTugRoom,
    listFootballMatches,
    listTugRooms,
    manageTugRoom,
    placeSportsBet,
    pullTugRope,
    settleMySportsBets,
  ]) {
    await assert.rejects(callable(unauthenticated), (error) => error.code === "unauthenticated");
  }
});

test("tug rooms and sports bets reject invalid stakes and duplicate or unknown selections", async () => {
  const uid = "player";
  const requestId = "123e4567-e89b-42d3-a456-426614174000";
  await assert.rejects(
    createTugRoom({ auth: { uid, token: {} }, data: { stakeCents: 0, requestId, invitedUids: [], password: "" } }),
    (error) => error.code === "invalid-argument",
  );
  await assert.rejects(
    placeSportsBet({ auth: { uid, token: {} }, data: { fixtureId: 42, selection: "unknown", amountCents: 100, requestId } }),
    (error) => error.code === "invalid-argument",
  );
  await assert.rejects(
    placeSportsBet({
      auth: { uid, token: {} },
      data: {
        legs: [
          { fixtureId: 42, marketId: "match_winner", selectionId: "home" },
          { fixtureId: 42, marketId: "match_winner", selectionId: "draw" },
        ],
        amountCents: 100,
        requestId,
      },
    }),
    (error) => error.code === "invalid-argument",
  );
  await assert.rejects(
    listFootballMatches({ auth: { uid, token: {} }, data: { date: "not-a-date" } }),
    (error) => error.code === "invalid-argument",
  );
});