"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  blackjackHandValue,
  coinFlipResult,
  crashMultiplierBasisPoints,
  crashPointBasisPoints,
  createShuffledDeck,
  createMinefield,
  diceGuessResult,
  footballShotResult,
  higherLowerResult,
  luckyDoorsResult,
  luckyNumberResult,
  rouletteResult,
  cardPairResult,
  colorWheelResult,
  diceSumResult,
  rangePickResult,
  doubleCoinResult,
  tripleDiceResult,
  luckySuitResult,
  safeVaultResult,
  resolveRockPaperScissors,
  resolveOnlineDuel,
  rockPaperScissorsResult,
  settleBlackjack,
  minesCashoutPayout,
  parityDiceResult,
  scratchCardResult,
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

test("coin flip, exact dice and parity settle the server-selected outcomes", () => {
  assert.equal(coinFlipResult("heads", 1_000, () => 0).payoutCents, 1_900);
  assert.equal(coinFlipResult("heads", 1_000, () => 1).payoutCents, 0);
  assert.equal(diceGuessResult("6", 1_000, () => 5).payoutCents, 5_500);
  assert.equal(diceGuessResult("2", 1_000, () => 5).payoutCents, 0);
  assert.equal(parityDiceResult("odd", 1_000, () => 0).payoutCents, 1_900);
  assert.equal(parityDiceResult("even", 1_000, () => 0).payoutCents, 0);
});

test("scratch cards pay only their predefined winning outcomes", () => {
  assert.equal(scratchCardResult(1_000, () => 0).payoutCents, 20_000);
  assert.equal(scratchCardResult(1_000, () => 5).payoutCents, 4_000);
  assert.equal(scratchCardResult(1_000, () => 100).payoutCents, 2_000);
  assert.equal(scratchCardResult(1_000, () => 320).payoutCents, 0);
  assert.throws(() => scratchCardResult(0), RangeError);
});

test("mines cash-out uses the 5x5 safe-pick probability at 98% RTP", () => {
  assert.deepEqual(minesCashoutPayout(400, 1, 1), {
    payoutCents: 408,
    multiplierBps: 10_208,
  });
  assert.ok(minesCashoutPayout(400, 5, 1).payoutCents > 400);
  assert.ok(minesCashoutPayout(400, 5, 2).payoutCents > minesCashoutPayout(400, 5, 1).payoutCents);
  assert.deepEqual(minesCashoutPayout(1_000_000, 1, 24), {
    payoutCents: 24_500_000,
    multiplierBps: 245_000,
  });
  assert.throws(() => minesCashoutPayout(400, 0, 1), RangeError);
  assert.throws(() => minesCashoutPayout(400, 24, 2), RangeError);
});

test("mines field contains the configured number of unique cells", () => {
  const mines = createMinefield(8, () => 0);
  assert.equal(mines.length, 8);
  assert.equal(new Set(mines).size, 8);
  assert.ok(mines.every((cell) => cell >= 0 && cell < 25));
  assert.throws(() => createMinefield(25), RangeError);
});

test("football shots score past the server-selected goalkeeper and pay 1.40x", () => {
  const goal = footballShotResult("left", 1_000, () => 1);
  assert.equal(goal.goalkeeper, "center");
  assert.equal(goal.won, true);
  assert.equal(goal.payoutCents, 1_400);

  const saved = footballShotResult("right", 1_000, () => 2);
  assert.equal(saved.won, false);
  assert.equal(saved.payoutCents, 0);
  assert.throws(() => footballShotResult("top", 1_000, () => 0), RangeError);
});

test("rock-paper-scissors settles wins, draws, and invalid selections", () => {
  assert.equal(rockPaperScissorsResult("paper", 1_000, () => 0).payoutCents, 1_850);
  assert.equal(rockPaperScissorsResult("rock", 1_000, () => 0).payoutCents, 1_000);
  assert.equal(rockPaperScissorsResult("scissors", 1_000, () => 0).payoutCents, 0);
  assert.throws(() => rockPaperScissorsResult("lizard", 1_000, () => 0), RangeError);
});

test("online rock-paper-scissors resolves server-submitted choices deterministically", () => {
  assert.deepEqual(resolveRockPaperScissors("rock", "scissors"), { winnerChoice: "rock", outcome: "win" });
  assert.deepEqual(resolveRockPaperScissors("paper", "rock"), { winnerChoice: "paper", outcome: "win" });
  assert.deepEqual(resolveRockPaperScissors("scissors", "paper"), { winnerChoice: "scissors", outcome: "win" });
  assert.deepEqual(resolveRockPaperScissors("rock", "rock"), { winnerChoice: "", outcome: "draw" });
  assert.throws(() => resolveRockPaperScissors("lizard", "paper"), RangeError);
});

test("online duel variants resolve coin, parity, and card choices on the server", () => {
  assert.equal(resolveOnlineDuel("duelParity", "even", "odd", () => 1).winnerIndex, 0);
  assert.equal(resolveOnlineDuel("duelCoin", "heads", "tails", () => 1).winnerIndex, 1);
  assert.equal(resolveOnlineDuel("duelCards", "6", "4").winnerIndex, 0);
  assert.equal(resolveOnlineDuel("duelCards", "3", "3").winnerIndex, -1);
  assert.throws(() => resolveOnlineDuel("duelCards", "7", "3"), RangeError);
});

test("higher-lower cards push on ties and pay only correct predictions", () => {
  const higherSequence = [4, 8];
  assert.equal(higherLowerResult("higher", 1_000, () => higherSequence.shift()).payoutCents, 1_900);
  assert.equal(higherLowerResult("lower", 1_000, () => 4).payoutCents, 1_000);
  const lowerSequence = [8, 2];
  assert.equal(higherLowerResult("higher", 1_000, () => lowerSequence.shift()).payoutCents, 0);
  assert.throws(() => higherLowerResult("same", 1_000, () => 0), RangeError);
});

test("lucky number and doors enforce choices and fixed server payouts", () => {
  assert.equal(luckyNumberResult("5", 1_000, () => 5).payoutCents, 9_500);
  assert.equal(luckyNumberResult("4", 1_000, () => 5).payoutCents, 0);
  assert.equal(luckyDoorsResult("3", 1_000, () => 2).payoutCents, 3_800);
  assert.equal(luckyDoorsResult("1", 1_000, () => 2).payoutCents, 0);
  assert.throws(() => luckyDoorsResult("5", 1_000, () => 0), RangeError);
});

test("dice sums refund ties and settle high or low picks", () => {
  assert.equal(diceSumResult("high", 1_000, (max) => max - 1).payoutCents, 1_880);
  const tieSequence = [2, 3];
  assert.equal(diceSumResult("low", 1_000, () => tieSequence.shift()).payoutCents, 1_000);
  assert.equal(diceSumResult("high", 1_000, () => 2).payoutCents, 0);
  assert.throws(() => diceSumResult("middle", 1_000, () => 0), RangeError);
});

test("card pairs support match and different predictions", () => {
  assert.equal(cardPairResult("match", 1_000, () => 0).payoutCents, 12_350);
  const differentSequence = [0, 1];
  assert.equal(cardPairResult("different", 1_000, () => differentSequence.shift()).payoutCents, 1_020);
  const nonMatchingSequence = [0, 1];
  assert.equal(cardPairResult("match", 1_000, () => nonMatchingSequence.shift()).payoutCents, 0);
  assert.throws(() => cardPairResult("similar", 1_000, () => 0), RangeError);
});

test("color wheel uses weighted server outcomes and fixed payouts", () => {
  assert.equal(colorWheelResult("gold", 1_000, () => 99).payoutCents, 9_500);
  assert.equal(colorWheelResult("red", 1_000, () => 0).payoutCents, 2_110);
  assert.equal(colorWheelResult("black", 1_000, () => 45).payoutCents, 2_110);
  assert.throws(() => colorWheelResult("blue", 1_000, () => 0), RangeError);
});

test("range picks pay according to the selected range probability", () => {
  assert.equal(rangePickResult("low", 1_000, () => 0).payoutCents, 2_380);
  assert.equal(rangePickResult("middle", 1_000, () => 4).payoutCents, 4_750);
  assert.equal(rangePickResult("high", 1_000, () => 9).payoutCents, 2_380);
  assert.equal(rangePickResult("high", 1_000, () => 0).payoutCents, 0);
  assert.throws(() => rangePickResult("other", 1_000, () => 0), RangeError);
});

test("new solo mini-games validate selections and calculate fixed payouts", () => {
  assert.equal(doubleCoinResult("same", 1_000, () => 0).payoutCents, 1_900);
  assert.equal(doubleCoinResult("different", 1_000, () => 0).payoutCents, 0);
  assert.throws(() => doubleCoinResult("heads", 1_000, () => 0), RangeError);

  assert.equal(tripleDiceResult("low", 1_000, () => 0).payoutCents, 5_980);
  assert.equal(tripleDiceResult("middle", 1_000, () => 2).payoutCents, 1_430);
  assert.equal(tripleDiceResult("high", 1_000, () => 5).payoutCents, 5_980);
  assert.throws(() => tripleDiceResult("seven", 1_000, () => 0), RangeError);

  assert.equal(luckySuitResult("spades", 1_000, () => 0).payoutCents, 3_800);
  assert.equal(luckySuitResult("clubs", 1_000, () => 0).payoutCents, 0);
  assert.throws(() => luckySuitResult("joker", 1_000, () => 0), RangeError);

  assert.equal(safeVaultResult("3", 1_000, () => 2).payoutCents, 4_750);
  assert.equal(safeVaultResult("2", 1_000, () => 2).payoutCents, 0);
  assert.throws(() => safeVaultResult("0", 1_000, () => 0), RangeError);
});