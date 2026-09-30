"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { initializeBalance } = require("./profile-logic");

test("new profiles receive R$500 and migration grants it once to empty legacy profiles", () => {
  assert.deepEqual(initializeBalance(), { balanceCents: 50_000, balanceInitialized: true });
  assert.deepEqual(initializeBalance({ balanceCents: 0, gamesPlayed: 0, wins: 0 }), {
    balanceCents: 50_000,
    balanceInitialized: true,
  });
});

test("existing balances and previously used zero balances are not topped up", () => {
  assert.deepEqual(initializeBalance({ balanceCents: 17_500, gamesPlayed: 0 }), {
    balanceCents: 17_500,
    balanceInitialized: true,
  });
  assert.deepEqual(initializeBalance({ balanceCents: 0, gamesPlayed: 8, wins: 2 }), {
    balanceCents: 0,
    balanceInitialized: true,
  });
  assert.deepEqual(initializeBalance({ balanceCents: 0, balanceInitialized: true }), {
    balanceCents: 0,
    balanceInitialized: true,
  });
});