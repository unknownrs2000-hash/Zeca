"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  LEGACY_FINANCIAL_CALLABLES,
  assertFinancialCallableAvailable,
  assertMinimumAppVersion,
} = require("./financial-mode");

test("all identified balance-changing callables have a Mongo path or explicit room guard", () => {
  assert.equal(LEGACY_FINANCIAL_CALLABLES.size, 0);
});

test("Mongo mode permits migrated handlers without affecting legacy mode", () => {
  for (const name of [
    "transferByPixKey",
    "adminAdjustBalance",
    "playGame",
    "placeSportsBet",
    "settleMySportsBets",
    "startMines",
    "revealMinesCell",
    "cashOutMines",
    "startCrash",
    "cashOutCrash",
    "startBlackjack",
    "blackjackAction",
    "buyCosmetic",
    "completeWorkShift",
    "claimWeeklyEventReward",
  ]) {
    assert.doesNotThrow(() => assertFinancialCallableAvailable(name, "mongo"));
  }
});

test("Mongo mode rejects missing or old app versions and accepts the required version", () => {
  assert.throws(
    () => assertMinimumAppVersion({ mode: "mongo", minimum: 2, client: 1 }),
    (error) => error.reason === "app-version-unsupported",
  );
  assert.throws(
    () => assertMinimumAppVersion({ mode: "mongo", minimum: 2, client: undefined }),
    (error) => error.reason === "app-version-unsupported",
  );
  assert.throws(
    () => assertMinimumAppVersion({ mode: "mongo", minimum: undefined, client: 2 }),
    (error) => error.reason === "app-version-policy-unconfigured",
  );
  assert.doesNotThrow(() => assertMinimumAppVersion({ mode: "mongo", minimum: 2, client: 2 }));
});