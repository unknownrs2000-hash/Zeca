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

function coinFlipResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["heads", "tails"].includes(selection)) {
    throw new RangeError("Invalid coin-flip wager.");
  }
  const outcome = secureRandomInt(2) === 0 ? "heads" : "tails";
  const won = outcome === selection;
  return {
    displayText: `${outcome === "heads" ? "Cara" : "Coroa"} · ${won ? "Acertou" : "Errou"}`,
    outcome,
    won,
    payoutCents: won ? Math.floor(wagerCents * 190 / 100) : 0,
    multiplier: won ? 190 : 0,
  };
}

function diceGuessResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER)
      || !/^[1-6]$/.test(String(selection))) {
    throw new RangeError("Invalid dice wager.");
  }
  const roll = secureRandomInt(6) + 1;
  const won = roll === Number(selection);
  return {
    displayText: `Saiu ${roll} · ${won ? "Acertou" : "Errou"}`,
    roll,
    won,
    payoutCents: won ? Math.floor(wagerCents * 550 / 100) : 0,
    multiplier: won ? 550 : 0,
  };
}

function parityDiceResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["even", "odd"].includes(selection)) {
    throw new RangeError("Invalid parity wager.");
  }
  const roll = secureRandomInt(6) + 1;
  const outcome = roll % 2 === 0 ? "even" : "odd";
  const won = outcome === selection;
  return {
    displayText: `Dado ${roll} · ${won ? "Acertou" : "Errou"}`,
    roll,
    outcome,
    won,
    payoutCents: won ? Math.floor(wagerCents * 190 / 100) : 0,
    multiplier: won ? 190 : 0,
  };
}

function minesCashoutPayout(wagerCents, mineCount, safePicks, rtpBps = 9_800) {
  const boardSize = 25;
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER)
      || !Number.isInteger(mineCount) || mineCount < 1 || mineCount >= boardSize
      || !Number.isInteger(safePicks) || safePicks < 1 || safePicks > boardSize - mineCount
      || !Number.isInteger(rtpBps) || rtpBps < 1 || rtpBps > 10_000) {
    throw new RangeError("Invalid mines cash-out.");
  }

  let allOutcomes = 1n;
  let safeOutcomes = 1n;
  for (let pick = 0; pick < safePicks; pick += 1) {
    allOutcomes *= BigInt(boardSize - pick);
    safeOutcomes *= BigInt(boardSize - mineCount - pick);
  }
  return {
    payoutCents: Number(BigInt(wagerCents) * BigInt(rtpBps) * allOutcomes / (10_000n * safeOutcomes)),
    multiplierBps: Number(BigInt(rtpBps) * allOutcomes / safeOutcomes),
  };
}

function createMinefield(mineCount, secureRandomInt = randomInt) {
  if (!Number.isInteger(mineCount) || mineCount < 1 || mineCount >= 25) {
    throw new RangeError("Invalid mine count.");
  }
  const cells = Array.from({ length: 25 }, (_value, index) => index);
  for (let index = cells.length - 1; index > 0; index -= 1) {
    const swapIndex = secureRandomInt(index + 1);
    if (!Number.isInteger(swapIndex) || swapIndex < 0 || swapIndex > index) {
      throw new RangeError("Invalid random value.");
    }
    [cells[index], cells[swapIndex]] = [cells[swapIndex], cells[index]];
  }
  return cells.slice(0, mineCount).sort((left, right) => left - right);
}

function scratchCardResult(wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER)) {
    throw new RangeError("Invalid scratch-card wager.");
  }
  const draw = secureRandomInt(1_000);
  const prize = draw < 5
    ? { displayText: "Três estrelas · prêmio máximo", multiplier: 2_000 }
    : draw < 100
      ? { displayText: "Três sinos · prêmio", multiplier: 400 }
      : draw < 320
        ? { displayText: "Três cerejas · prêmio", multiplier: 200 }
        : { displayText: "Não premiada", multiplier: 0 };
  return {
    ...prize,
    payoutCents: Math.floor(wagerCents * prize.multiplier / 100),
  };
}

function footballShotResult(selection, wagerCents, secureRandomInt = randomInt) {
  const corners = ["left", "center", "right"];
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !corners.includes(selection)) {
    throw new RangeError("Invalid football wager.");
  }
  const goalkeeper = corners[secureRandomInt(corners.length)];
  const won = selection !== goalkeeper;
  const labels = { left: "Esquerda", center: "Centro", right: "Direita" };
  return {
    displayText: `Chute ${labels[selection]} · goleiro ${labels[goalkeeper]} · ${won ? "GOL" : "DEFENDEU"}`,
    shot: selection,
    goalkeeper,
    won,
    payoutCents: won ? Math.floor(wagerCents * 140 / 100) : 0,
    multiplier: won ? 140 : 0,
  };
}

function rockPaperScissorsResult(selection, wagerCents, secureRandomInt = randomInt) {
  const choices = ["rock", "paper", "scissors"];
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !choices.includes(selection)) {
    throw new RangeError("Invalid rock-paper-scissors wager.");
  }
  const opponent = choices[secureRandomInt(choices.length)];
  const tied = selection === opponent;
  const won = !tied && (
    (selection === "rock" && opponent === "scissors")
    || (selection === "paper" && opponent === "rock")
    || (selection === "scissors" && opponent === "paper")
  );
  const multiplier = won ? 185 : tied ? 100 : 0;
  const labels = { rock: "Pedra", paper: "Papel", scissors: "Tesoura" };
  return {
    displayText: `${labels[selection]} × ${labels[opponent]} · ${won ? "Vitória" : tied ? "Empate" : "Derrota"}`,
    opponent,
    won,
    payoutCents: won ? Math.floor(wagerCents * multiplier / 100) : tied ? wagerCents : 0,
    multiplier,
  };
}

function resolveRockPaperScissors(firstChoice, secondChoice) {
  const choices = ["rock", "paper", "scissors"];
  if (!choices.includes(firstChoice) || !choices.includes(secondChoice)) {
    throw new RangeError("Invalid rock-paper-scissors choice.");
  }
  if (firstChoice === secondChoice) return { winnerChoice: "", outcome: "draw" };
  const firstWins = (firstChoice === "rock" && secondChoice === "scissors")
    || (firstChoice === "paper" && secondChoice === "rock")
    || (firstChoice === "scissors" && secondChoice === "paper");
  return { winnerChoice: firstWins ? firstChoice : secondChoice, outcome: "win" };
}

function resolveOnlineDuel(gameId, firstChoice, secondChoice, secureRandomInt = randomInt) {
  if (gameId === "rps") {
    const result = resolveRockPaperScissors(firstChoice, secondChoice);
    return { winnerIndex: result.outcome === "draw" ? -1 : result.winnerChoice === firstChoice ? 0 : 1, result };
  }
  if (gameId === "duelParity") {
    const choices = ["even", "odd"];
    if (!choices.includes(firstChoice) || !choices.includes(secondChoice)) throw new RangeError("Invalid parity choice.");
    const roll = secureRandomInt(6) + 1;
    const outcome = roll % 2 === 0 ? "even" : "odd";
    const firstCorrect = firstChoice === outcome;
    const secondCorrect = secondChoice === outcome;
    const winnerIndex = firstCorrect === secondCorrect ? -1 : firstCorrect ? 0 : 1;
    return {
      winnerIndex,
      result: { displayText: `Saiu ${roll} (${outcome === "even" ? "par" : "ímpar"})` },
    };
  }
  if (gameId === "duelCoin") {
    const choices = ["heads", "tails"];
    if (!choices.includes(firstChoice) || !choices.includes(secondChoice)) throw new RangeError("Invalid coin choice.");
    const outcome = secureRandomInt(2) === 0 ? "heads" : "tails";
    const firstCorrect = firstChoice === outcome;
    const secondCorrect = secondChoice === outcome;
    const winnerIndex = firstCorrect === secondCorrect ? -1 : firstCorrect ? 0 : 1;
    return {
      winnerIndex,
      result: { displayText: `A moeda deu ${outcome === "heads" ? "cara" : "coroa"}` },
    };
  }
  if (gameId === "duelCards") {
    if (!/^[1-6]$/.test(String(firstChoice))
        || !/^[1-6]$/.test(String(secondChoice))) {
      throw new RangeError("Invalid card choice.");
    }
    const firstCard = Number(firstChoice);
    const secondCard = Number(secondChoice);
    const winnerIndex = firstCard === secondCard ? -1 : firstCard > secondCard ? 0 : 1;
    return {
      winnerIndex,
      result: { displayText: `Carta ${firstCard} contra carta ${secondCard}` },
    };
  }
  throw new RangeError("Invalid duel game.");
}

function higherLowerResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["higher", "lower"].includes(selection)) {
    throw new RangeError("Invalid higher-lower wager.");
  }
  const first = secureRandomInt(13) + 1;
  const second = secureRandomInt(13) + 1;
  const tied = first === second;
  const won = !tied && (selection === "higher" ? second > first : second < first);
  const multiplier = won ? 190 : tied ? 100 : 0;
  return {
    displayText: `Carta ${first} → ${second} · ${won ? "Acertou" : tied ? "Empate" : "Errou"}`,
    first,
    second,
    won,
    payoutCents: won ? Math.floor(wagerCents * multiplier / 100) : tied ? wagerCents : 0,
    multiplier,
  };
}

function luckyNumberResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !/^[0-9]$/.test(String(selection))) {
    throw new RangeError("Invalid lucky-number wager.");
  }
  const number = secureRandomInt(10);
  const won = number === Number(selection);
  return {
    displayText: `Saiu ${number} · ${won ? "Número certo" : "Não foi desta vez"}`,
    number,
    won,
    payoutCents: won ? Math.floor(wagerCents * 950 / 100) : 0,
    multiplier: won ? 950 : 0,
  };
}

function luckyDoorsResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !/^[1-4]$/.test(String(selection))) {
    throw new RangeError("Invalid lucky-doors wager.");
  }
  const winningDoor = secureRandomInt(4) + 1;
  const won = winningDoor === Number(selection);
  return {
    displayText: `A porta premiada era ${winningDoor} · ${won ? "Encontrou o prêmio" : "Porta vazia"}`,
    winningDoor,
    won,
    payoutCents: won ? Math.floor(wagerCents * 380 / 100) : 0,
    multiplier: won ? 380 : 0,
  };
}

function diceSumResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["low", "high"].includes(selection)) {
    throw new RangeError("Invalid dice-sum wager.");
  }
  const first = secureRandomInt(6) + 1;
  const second = secureRandomInt(6) + 1;
  const sum = first + second;
  const tied = sum === 7;
  const won = !tied && (selection === "low" ? sum < 7 : sum > 7);
  const multiplier = won ? 188 : tied ? 100 : 0;
  return {
    displayText: `Dados ${first} + ${second} = ${sum} · ${won ? "Acertou" : tied ? "Empate" : "Errou"}`,
    first,
    second,
    sum,
    won,
    payoutCents: won ? Math.floor(wagerCents * multiplier / 100) : tied ? wagerCents : 0,
    multiplier,
  };
}

function cardPairResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["match", "different"].includes(selection)) {
    throw new RangeError("Invalid card-pair wager.");
  }
  const first = secureRandomInt(13) + 1;
  const second = secureRandomInt(13) + 1;
  const matched = first === second;
  const won = selection === "match" ? matched : !matched;
  const multiplier = selection === "match" ? 1_235 : 102;
  return {
    displayText: `Cartas ${first} e ${second} · ${won ? "Previsão certa" : "Previsão errada"}`,
    first,
    second,
    won,
    payoutCents: won ? Math.floor(wagerCents * multiplier / 100) : 0,
    multiplier: won ? multiplier : 0,
  };
}

function colorWheelResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["red", "black", "gold"].includes(selection)) {
    throw new RangeError("Invalid color-wheel wager.");
  }
  const draw = secureRandomInt(100);
  const color = draw < 45 ? "red" : draw < 90 ? "black" : "gold";
  const won = color === selection;
  const multiplier = color === "gold" ? 950 : 211;
  const labels = { red: "Vermelho", black: "Preto", gold: "Dourado" };
  return {
    displayText: `A roda parou em ${labels[color]} · ${won ? "Acertou" : "Errou"}`,
    color,
    won,
    payoutCents: won ? Math.floor(wagerCents * multiplier / 100) : 0,
    multiplier: won ? multiplier : 0,
  };
}

function rangePickResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["low", "middle", "high"].includes(selection)) {
    throw new RangeError("Invalid range-pick wager.");
  }
  const number = secureRandomInt(10);
  const range = number <= 3 ? "low" : number <= 5 ? "middle" : "high";
  const won = range === selection;
  const multiplier = range === "middle" ? 475 : 238;
  const labels = { low: "Baixa", middle: "Central", high: "Alta" };
  return {
    displayText: `Saiu ${number} · Faixa ${labels[range]} · ${won ? "Acertou" : "Errou"}`,
    number,
    range,
    won,
    payoutCents: won ? Math.floor(wagerCents * multiplier / 100) : 0,
    multiplier: won ? multiplier : 0,
  };
}

function doubleCoinResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["same", "different"].includes(selection)) {
    throw new RangeError("Invalid double-coin wager.");
  }
  const first = secureRandomInt(2);
  const second = secureRandomInt(2);
  const outcome = first === second ? "same" : "different";
  const won = selection === outcome;
  return {
    displayText: `${first === 0 ? "Cara" : "Coroa"} + ${second === 0 ? "Cara" : "Coroa"} · ${won ? "Acertou" : "Errou"}`,
    outcome,
    won,
    payoutCents: won ? Math.floor(wagerCents * 190 / 100) : 0,
    multiplier: won ? 190 : 0,
  };
}

function tripleDiceResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !["low", "middle", "high"].includes(selection)) {
    throw new RangeError("Invalid triple-dice wager.");
  }
  const dice = Array.from({ length: 3 }, () => secureRandomInt(6) + 1);
  const sum = dice.reduce((total, value) => total + value, 0);
  const outcome = sum <= 7 ? "low" : sum <= 13 ? "middle" : "high";
  const multiplier = outcome === "middle" ? 143 : 598;
  const won = selection === outcome;
  return {
    displayText: `Dados ${dice.join(" + ")} = ${sum} · ${won ? "Acertou" : "Errou"}`,
    dice,
    sum,
    outcome,
    won,
    payoutCents: won ? Math.floor(wagerCents * multiplier / 100) : 0,
    multiplier: won ? multiplier : 0,
  };
}

function luckySuitResult(selection, wagerCents, secureRandomInt = randomInt) {
  const suits = ["spades", "hearts", "diamonds", "clubs"];
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER) || !suits.includes(selection)) {
    throw new RangeError("Invalid lucky-suit wager.");
  }
  const outcome = suits[secureRandomInt(suits.length)];
  const won = selection === outcome;
  const labels = { spades: "Espadas", hearts: "Copas", diamonds: "Ouros", clubs: "Paus" };
  return {
    displayText: `Naipe ${labels[outcome]} · ${won ? "Acertou" : "Errou"}`,
    outcome,
    won,
    payoutCents: won ? Math.floor(wagerCents * 380 / 100) : 0,
    multiplier: won ? 380 : 0,
  };
}

function safeVaultResult(selection, wagerCents, secureRandomInt = randomInt) {
  if (!validateWager(wagerCents, Number.MAX_SAFE_INTEGER)
      || !/^[1-5]$/.test(String(selection))) {
    throw new RangeError("Invalid safe-vault wager.");
  }
  const outcome = secureRandomInt(5) + 1;
  const won = Number(selection) === outcome;
  return {
    displayText: `Cofre ${outcome} · ${won ? "Encontrou o prêmio" : "Vazio"}`,
    outcome,
    won,
    payoutCents: won ? Math.floor(wagerCents * 475 / 100) : 0,
    multiplier: won ? 475 : 0,
  };
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
  coinFlipResult,
  createMinefield,
  crashMultiplierBasisPoints,
  crashPointBasisPoints,
  createShuffledDeck,
  diceGuessResult,
  footballShotResult,
  higherLowerResult,
  isBlackjack,
  luckyDoorsResult,
  luckyNumberResult,
  minesCashoutPayout,
  parityDiceResult,
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
  rouletteResult,
  scratchCardResult,
  settleBlackjack,
  spinSlots,
  validateWager,
};