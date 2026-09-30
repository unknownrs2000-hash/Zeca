"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  blackjackHandValue,
  crashMultiplierBasisPoints,
  crashPointBasisPoints,
  createShuffledDeck,
  rouletteResult,
  settleBlackjack,
  spinSlots,
  validateWager,
} = require("./game-logic");

test("wagers reject fractions, zero, negatives, and values over limit", () => {
  assert.equal(validateWager(1_000, 100_000), true);
  assert.equal(validateWager(0, 100_000), false);
  assert.equal(validateWager(-100, 100_000), false);
  assert.equal(validateWager(100.5, 100_000), false);
  assert.equal(validateWager(100_001, 100_000), false);
});

test("slots settle triples, pairs, and misses with server payout table", () => {
  const sequence = [0, 0, 0];
  const triple = spinSlots(1_000, () => sequence.shift());
  assert.equal(triple.multiplier, 20);
  assert.equal(triple.payoutCents, 20_000);

  const pairSequence = [1, 1, 2];
  const pair = spinSlots(1_000, () => pairSequence.shift());
  assert.equal(pair.multiplier, 2);
  assert.equal(pair.payoutCents, 2_000);

  const missSequence = [0, 1, 2];
  assert.equal(spinSlots(1_000, () => missSequence.shift()).payoutCents, 0);
});

test("roulette handles zero and all supported bet families", () => {
  assert.equal(rouletteResult("color", "red", 0, 1_000).payoutCents, 0);
  assert.equal(rouletteResult("parity", "even", 2, 1_000).payoutCents, 2_000);
  assert.equal(rouletteResult("range", "high", 19, 1_000).payoutCents, 2_000);
  assert.equal(rouletteResult("dozen", 3, 36, 1_000).payoutCents, 3_000);
  assert.equal(rouletteResult("number", 0, 0, 1_000).payoutCents, 36_000);
  assert.throws(() => rouletteResult("number", 37, 1, 1_000), RangeError);
});

test("blackjack counts aces correctly and settles naturals and pushes", () => {
  assert.deepEqual(blackjackHandValue([{ rank: "A" }, { rank: "9" }]), { total: 20, soft: true });
  assert.deepEqual(blackjackHandValue([{ rank: "A" }, { rank: "9" }, { rank: "5" }]), { total: 15, soft: false });
  assert.deepEqual(settleBlackjack([{ rank: "A" }, { rank: "K" }], [{ rank: "10" }, { rank: "7" }], 1_000), {
    outcome: "blackjack",
    payoutCents: 2_500,
  });
  assert.deepEqual(settleBlackjack([{ rank: "10" }, { rank: "8" }], [{ rank: "10" }, { rank: "8" }], 1_000), {
    outcome: "push",
    payoutCents: 1_000,
  });
});

test("blackjack deck has 52 unique cards", () => {
  const deck = createShuffledDeck((max) => max - 1);
  assert.equal(deck.length, 52);
  assert.equal(new Set(deck.map((card) => `${card.rank}${card.suit}`)).size, 52);
});

test("crash curve starts at 1.00x, grows, and stays capped", () => {
  assert.equal(crashMultiplierBasisPoints(0), 100);
  assert.ok(crashMultiplierBasisPoints(5_000) > 100);
  assert.equal(crashMultiplierBasisPoints(100_000), 1_000_000);
  assert.equal(crashPointBasisPoints(() => 0), 100);
  assert.equal(crashPointBasisPoints(() => 999_999), 1_000_000);
});