"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { initializeBalance, levelProgress, normalizeUsername } = require("./profile-logic");

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

test("usernames are unique-key compatible and case-insensitive", () => {
  assert.equal(normalizeUsername("  Zeca_Player9 "), "zeca_player9");
  assert.equal(normalizeUsername("ab"), null);
  assert.equal(normalizeUsername("nome com espaço"), null);
  assert.equal(normalizeUsername("nome!"), null);
});

test("level reward is granted once at each ten settled games", () => {
  assert.deepEqual(levelProgress(0), {
    level: 1,
    gamesTowardNextLevel: 0,
    gamesToNextLevel: 10,
    rewardCents: 0,
  });
  assert.deepEqual(levelProgress(9), {
    level: 1,
    gamesTowardNextLevel: 9,
    gamesToNextLevel: 1,
    rewardCents: 0,
  });
  assert.deepEqual(levelProgress(10), {
    level: 2,
    gamesTowardNextLevel: 0,
    gamesToNextLevel: 10,
    rewardCents: 1_000,
  });
});