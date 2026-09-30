"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  adminAdjustBalance,
  adminDeleteUser,
  adminListUsers,
  adminUpdateGameSettings,
  adminUpdateUserInventory,
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