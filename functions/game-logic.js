"use strict";

const { randomInt } = require("node:crypto");

const SLOT_SYMBOLS = ["7", "BAR", "STAR", "DIAMOND", "CHERRY", "LEMON", "BELL", "CLOVER"];
const RED_NUMBERS = new Set([1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]);
const SUITS = ["♠", "♥", "♦", "♣"];
const RANKS = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"];

function validateWager(amountCents, maxWagerCents) {
  return Number.isSafeInteger(amountCents)
    && amountCents >= 100
    && amountCents <= maxWagerCents;
}

function spinSlots(wagerCents, secureRandomInt = randomInt) {
  const reels = Array.from({ length: 3 }, () => SLOT_SYMBOLS[secureRandomInt(SLOT_SYMBOLS.length)]);
  const counts = reels.reduce((map, symbol) => map.set(symbol, (map.get(symbol) || 0) + 1), new Map());
  const matching = Math.max(...counts.values());
  const multiplier = matching === 3 ? 20 : matching === 2 ? 2 : 0;
  return { reels, payoutCents: wagerCents * multiplier, multiplier };
}

function rouletteResult(betType, selection, number, wagerCents) {
  if (!Number.isInteger(number) || number < 0 || number > 36) {
    throw new RangeError("Roulette number must be between 0 and 36.");
  }
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER)) {
    throw new RangeError("Invalid roulette wager.");
  }

  let won = false;
  let multiplier = 0;
  if (betType === "color" && ["red", "black"].includes(selection)) {
    const color = number === 0 ? "green" : RED_NUMBERS.has(number) ? "red" : "black";
    won = color === selection;
    multiplier = 2;
  } else if (betType === "parity" && ["even", "odd"].includes(selection)) {
    won = number !== 0 && (number % 2 === 0 ? "even" : "odd") === selection;
    multiplier = 2;
  } else if (betType === "range" && ["low", "high"].includes(selection)) {
    won = number !== 0 && (selection === "low" ? number <= 18 : number >= 19);
    multiplier = 2;
  } else if (betType === "dozen" && [1, 2, 3].includes(Number(selection))) {
    const dozen = Number(selection);
    won = number !== 0 && Math.ceil(number / 12) === dozen;
    multiplier = 3;
  } else if (betType === "number" && Number.isInteger(Number(selection)) && Number(selection) >= 0 && Number(selection) <= 36) {
    won = number === Number(selection);
    multiplier = 36;
  } else {
    throw new RangeError("Invalid roulette bet.");
  }

  const color = number === 0 ? "green" : RED_NUMBERS.has(number) ? "red" : "black";
  return { number, color, won, payoutCents: won ? wagerCents * multiplier : 0, multiplier };
}

function createShuffledDeck(secureRandomInt = randomInt) {
  const deck = SUITS.flatMap((suit) => RANKS.map((rank) => ({ rank, suit })));
  for (let index = deck.length - 1; index > 0; index -= 1) {
    const swapIndex = secureRandomInt(index + 1);
    [deck[index], deck[swapIndex]] = [deck[swapIndex], deck[index]];
  }
  return deck;
}

function blackjackHandValue(cards) {
  let total = 0;
  let aces = 0;
  for (const card of cards) {
    if (card.rank === "A") {
      total += 11;
      aces += 1;
    } else {
      total += ["J", "Q", "K"].includes(card.rank) ? 10 : Number(card.rank);
    }
  }
  while (total > 21 && aces > 0) {
    total -= 10;
    aces -= 1;
  }
  return { total, soft: aces > 0 };
}

function isBlackjack(cards) {
  return cards.length === 2 && blackjackHandValue(cards).total === 21;
}

function settleBlackjack(playerCards, dealerCards, wagerCents, playerBusted = false) {
  const player = blackjackHandValue(playerCards).total;
  const dealer = blackjackHandValue(dealerCards).total;
  const playerNatural = isBlackjack(playerCards);
  const dealerNatural = isBlackjack(dealerCards);
  if (playerBusted || player > 21) return { outcome: "bust", payoutCents: 0 };
  if (playerNatural && dealerNatural) return { outcome: "push", payoutCents: wagerCents };
  if (playerNatural) return { outcome: "blackjack", payoutCents: Math.floor(wagerCents * 2.5) };
  if (dealerNatural) return { outcome: "dealer_blackjack", payoutCents: 0 };
  if (dealer > 21 || player > dealer) return { outcome: "win", payoutCents: wagerCents * 2 };
  if (player === dealer) return { outcome: "push", payoutCents: wagerCents };
  return { outcome: "lose", payoutCents: 0 };
}

function crashPointBasisPoints(secureRandomInt = randomInt) {
  const random = secureRandomInt(1_000_000);
  return Math.max(100, Math.min(1_000_000, Math.floor(99_000_000 / (1_000_000 - random))));
}

function crashMultiplierBasisPoints(elapsedMs) {
  if (!Number.isFinite(elapsedMs) || elapsedMs < 0) return 100;
  return Math.min(1_000_000, Math.floor(100 * Math.exp(elapsedMs / 5_000)));
}

module.exports = {
  SLOT_SYMBOLS,
  blackjackHandValue,
  crashMultiplierBasisPoints,
  crashPointBasisPoints,
  createShuffledDeck,
  isBlackjack,
  rouletteResult,
  settleBlackjack,
  spinSlots,
  validateWager,
};