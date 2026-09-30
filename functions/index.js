"use strict";

const { createHash, randomInt, randomUUID } = require("node:crypto");
const { getAuth } = require("firebase-admin/auth");
const { cert, initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("./callable");
const {
  blackjackHandValue,
  crashMultiplierBasisPoints,
  crashPointBasisPoints,
  createShuffledDeck,
  isBlackjack,
  rouletteResult,
  settleBlackjack,
  spinSlots,
  validateWager,
} = require("./game-logic");
const { initializeBalance } = require("./profile-logic");

initializeApp({
  credential: cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)),
});

const database = getFirestore();
const INITIAL_BALANCE_CENTS = 50_000;
const MAX_TRANSFER_CENTS = 1_000_000;
const GAME_COOLDOWN_MS = 250;
const CHAT_COOLDOWN_MS = 300;
const COSMETICS = {
  frame_aurora: { name: "Moldura Aurora", priceCents: 1_299 },
  title_lucky: { name: "Título: Sorte Grande", priceCents: 799 },
  frame_neon: { name: "Moldura Neon", priceCents: 1_999 },
};

function authenticatedUid(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Entre na sua conta para continuar.");
  }
  return request.auth.uid;
}

function normalizePixKey(key) {
  if (typeof key !== "string") {
    throw new HttpsError("invalid-argument", "Informe uma chave Pix.");
  }
  const normalized = key.trim().toLowerCase();
  const isEmail = /^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$/i.test(normalized);
  const isRandom = /^[a-f0-9]{32}$/.test(normalized)
    || /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(normalized);
  if (!isEmail && !isRandom) {
    throw new HttpsError("invalid-argument", "Use um e-mail ou uma chave aleatória válida.");
  }
  return { normalized, type: isEmail ? "email" : "random" };
}

function pixKeyHash(key) {
  return createHash("sha256").update(key).digest("hex");
}

function safeName(value, fallback) {
  const name = typeof value === "string" ? value.trim().replace(/\s+/g, " ") : "";
  if (name.length < 2 || name.length > 24) {
    return fallback;
  }
  return name;
}

function enforceGameCooldown(profile, nowMs) {
  const lastActionAtMs = profile.lastGameActionAtMs;
  if (Number.isSafeInteger(lastActionAtMs) && nowMs - lastActionAtMs < GAME_COOLDOWN_MS) {
    throw new HttpsError("resource-exhausted", "Aguarde um instante antes da próxima ação.");
  }
}

exports.ensurePlayerProfile = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const authUser = await getAuth().getUser(uid);
  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  const fallbackName = authUser.email?.split("@")[0] || "Jogador";
  const displayName = safeName(authUser.displayName, fallbackName);

  await database.runTransaction(async (transaction) => {
    const [userSnapshot, rankSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(rankRef),
    ]);
    const existing = userSnapshot.data() || {};
    const { balanceCents } = initializeBalance(existing, INITIAL_BALANCE_CENTS);

    const profile = {
      ...existing,
      uid,
      displayName: safeName(existing.displayName, displayName),
      email: authUser.email || existing.email || "",
      balanceCents,
      balanceInitialized: true,
      level: Number.isSafeInteger(existing.level) && existing.level > 0 ? existing.level : 1,
      avatarUrl: existing.avatarUrl || authUser.photoURL || "",
      pixKey: existing.pixKey || "",
      pixKeyType: existing.pixKeyType || "",
      pixKeyHash: existing.pixKeyHash || "",
      gamesPlayed: Number.isSafeInteger(existing.gamesPlayed) ? existing.gamesPlayed : 0,
      wins: Number.isSafeInteger(existing.wins) ? existing.wins : 0,
      inventory: Array.isArray(existing.inventory) ? existing.inventory : [],
      createdAt: existing.createdAt || FieldValue.serverTimestamp(),
    };
    const publicProfile = {
      displayName: profile.displayName,
      balanceCents: profile.balanceCents,
      level: profile.level,
      avatarUrl: profile.avatarUrl,
    };

    if (userSnapshot.exists) transaction.set(userRef, profile);
    else transaction.create(userRef, profile);
    if (rankSnapshot.exists) transaction.set(rankRef, publicProfile);
    else transaction.create(rankRef, publicProfile);
  });

  return { ok: true };
});

exports.updatePlayerProfile = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const displayName = safeName(request.data?.displayName, "");
  if (!displayName) {
    throw new HttpsError("invalid-argument", "O apelido deve ter entre 2 e 24 caracteres.");
  }

  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  await database.runTransaction(async (transaction) => {
    const [userSnapshot, rankSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(rankRef),
    ]);
    if (!userSnapshot.exists || !rankSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil ainda não foi criado.");
    }
    transaction.update(userRef, { displayName });
    transaction.update(rankRef, { displayName });
  });
  return { ok: true, displayName };
});

exports.registerPixKey = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const { normalized, type } = normalizePixKey(request.data?.key);
  const requestedType = String(request.data?.type || "").toLowerCase();
  if ((type === "email" && !["email", "e-mail"].includes(requestedType))
      || (type === "random" && !["random", "aleatória", "aleatoria"].includes(requestedType))) {
    throw new HttpsError("invalid-argument", "Tipo de chave incompatível.");
  }

  if (type === "email") {
    const authUser = await getAuth().getUser(uid);
    const verifiedEmail = String(authUser.email || "").toLowerCase();
    if (!authUser.emailVerified || normalized !== verifiedEmail) {
      throw new HttpsError("failed-precondition", "Confirme seu e-mail e use o mesmo endereço da conta.");
    }
  }

  const userRef = database.collection("users").doc(uid);
  const newHash = pixKeyHash(normalized);
  const newKeyRef = database.collection("pixKeys").doc(newHash);
  await database.runTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
    if (!userSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil ainda não foi criado.");
    }
    const profile = userSnapshot.data();
    const oldHash = profile.pixKeyHash || "";
    const oldKeyRef = oldHash ? database.collection("pixKeys").doc(oldHash) : null;
    const newKeySnapshot = await transaction.get(newKeyRef);
    const oldKeySnapshot = oldKeyRef && oldHash !== newHash
      ? await transaction.get(oldKeyRef)
      : null;

    if (newKeySnapshot.exists && newKeySnapshot.data().uid !== uid) {
      throw new HttpsError("already-exists", "Esta chave já está vinculada a outra conta.");
    }
    if (oldKeySnapshot?.exists && oldKeySnapshot.data().uid === uid) {
      transaction.delete(oldKeyRef);
    }
    transaction.set(newKeyRef, { uid, createdAt: FieldValue.serverTimestamp() });
    transaction.update(userRef, {
      pixKey: normalized,
      pixKeyType: type,
      pixKeyHash: newHash,
    });
  });

  return { ok: true };
});

exports.lookupPixKey = onCall(async (request) => {
  authenticatedUid(request);
  const { normalized } = normalizePixKey(request.data?.key);
  const keySnapshot = await database.collection("pixKeys").doc(pixKeyHash(normalized)).get();
  if (!keySnapshot.exists) {
    throw new HttpsError("not-found", "Nenhuma conta encontrada para essa chave.");
  }
  const uid = keySnapshot.data().uid;
  const profileSnapshot = await database.collection("leaderboard").doc(uid).get();
  if (!profileSnapshot.exists) {
    throw new HttpsError("not-found", "Perfil do destinatário não encontrado.");
  }
  const profile = profileSnapshot.data();
  return {
    uid,
    displayName: profile.displayName || "Jogador",
    level: profile.level || 1,
    avatarUrl: profile.avatarUrl || "",
  };
});

exports.transferByPixKey = onCall(async (request) => {
  const senderUid = authenticatedUid(request);
  const { normalized } = normalizePixKey(request.data?.key);
  const amountCents = request.data?.amountCents;
  const requestId = request.data?.requestId;
  if (!Number.isSafeInteger(amountCents) || amountCents < 1 || amountCents > MAX_TRANSFER_CENTS) {
    throw new HttpsError("invalid-argument", "Valor da transferência inválido.");
  }
  if (typeof requestId !== "string" || !/^[a-f0-9-]{36}$/i.test(requestId)) {
    throw new HttpsError("invalid-argument", "Identificador da transferência inválido.");
  }

  const transferRef = database.collection("transfers").doc(requestId);
  const keyRef = database.collection("pixKeys").doc(pixKeyHash(normalized));
  const senderRef = database.collection("users").doc(senderUid);
  let response;

  await database.runTransaction(async (transaction) => {
    const previousTransfer = await transaction.get(transferRef);
    if (previousTransfer.exists) {
      const prior = previousTransfer.data();
      if (prior.senderUid !== senderUid) {
        throw new HttpsError("already-exists", "Identificador já utilizado.");
      }
      response = {
        transferId: requestId,
        recipientName: prior.recipientName,
        balanceCents: prior.senderBalanceAfter,
      };
      return;
    }

    const keySnapshot = await transaction.get(keyRef);
    if (!keySnapshot.exists) {
      throw new HttpsError("not-found", "Nenhuma conta encontrada para essa chave.");
    }
    const recipientUid = keySnapshot.data().uid;
    if (recipientUid === senderUid) {
      throw new HttpsError("invalid-argument", "Escolha a chave de outro usuário.");
    }

    const recipientRef = database.collection("users").doc(recipientUid);
    const senderRankRef = database.collection("leaderboard").doc(senderUid);
    const recipientRankRef = database.collection("leaderboard").doc(recipientUid);
    const senderHistoryRef = senderRef.collection("transactions").doc(requestId);
    const recipientHistoryRef = recipientRef.collection("transactions").doc(requestId);
    const [senderSnapshot, recipientSnapshot, senderRankSnapshot, recipientRankSnapshot] = await Promise.all([
      transaction.get(senderRef),
      transaction.get(recipientRef),
      transaction.get(senderRankRef),
      transaction.get(recipientRankRef),
    ]);

    if (!senderSnapshot.exists || !recipientSnapshot.exists
        || !senderRankSnapshot.exists || !recipientRankSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Uma das contas ainda não está pronta.");
    }

    const sender = senderSnapshot.data();
    const recipient = recipientSnapshot.data();
    const senderBalance = sender.balanceCents || 0;
    const recipientBalance = recipient.balanceCents || 0;
    if (senderBalance < amountCents) {
      throw new HttpsError("failed-precondition", "Saldo insuficiente.");
    }

    const senderAfter = senderBalance - amountCents;
    const recipientAfter = recipientBalance + amountCents;
    const senderName = sender.displayName || "Jogador";
    const recipientName = recipient.displayName || "Jogador";
    const now = FieldValue.serverTimestamp();
    transaction.update(senderRef, { balanceCents: senderAfter });
    transaction.update(recipientRef, { balanceCents: recipientAfter });
    transaction.update(senderRankRef, { balanceCents: senderAfter });
    transaction.update(recipientRankRef, { balanceCents: recipientAfter });
    transaction.create(transferRef, {
      senderUid,
      recipientUid,
      senderName,
      recipientName,
      amountCents,
      senderBalanceAfter: senderAfter,
      createdAt: now,
    });
    transaction.create(senderHistoryRef, {
      description: `Para ${recipientName}`,
      deltaCents: -amountCents,
      counterpartyUid: recipientUid,
      createdAt: now,
    });
    transaction.create(recipientHistoryRef, {
      description: `De ${senderName}`,
      deltaCents: amountCents,
      counterpartyUid: senderUid,
      createdAt: now,
    });
    response = { transferId: requestId, recipientName, balanceCents: senderAfter };
  });

  return response;
});

exports.playGame = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const game = request.data?.game;
  const amountCents = request.data?.amountCents;
  const requestId = request.data?.requestId;
  if (!validateWager(amountCents, MAX_TRANSFER_CENTS)) {
    throw new HttpsError("invalid-argument", "Valor da aposta inválido.");
  }
  if (!new Set(["slots", "roulette"]).has(game)
      || typeof requestId !== "string"
      || !/^[a-f0-9-]{36}$/i.test(requestId)) {
    throw new HttpsError("invalid-argument", "Jogo ou identificador inválido.");
  }

  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  const requestRef = userRef.collection("gameRequests").doc(requestId);
  const transactionRef = userRef.collection("transactions").doc(requestId);
  let response;

  await database.runTransaction(async (transaction) => {
    const previousRequest = await transaction.get(requestRef);
    if (previousRequest.exists) {
      response = previousRequest.data().response;
      return;
    }
    const [userSnapshot, rankSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(rankRef),
    ]);
    if (!userSnapshot.exists || !rankSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil ainda não foi criado.");
    }
    const profile = userSnapshot.data();
    const actionAtMs = Date.now();
    enforceGameCooldown(profile, actionAtMs);
    const balance = profile.balanceCents || 0;
    if (balance < amountCents) {
      throw new HttpsError("failed-precondition", "Saldo insuficiente.");
    }

    let result;
    let returnedCents;
    if (game === "slots") {
      result = spinSlots(amountCents);
      returnedCents = result.payoutCents;
    } else {
      try {
        result = rouletteResult(
          request.data?.betType,
          request.data?.selection,
          randomInt(37),
          amountCents,
        );
        returnedCents = result.payoutCents;
      } catch {
        throw new HttpsError("invalid-argument", "Aposta de roleta inválida.");
      }
    }

    const deltaCents = returnedCents - amountCents;
    const balanceAfter = balance + deltaCents;
    if (!Number.isSafeInteger(balanceAfter) || balanceAfter < 0) {
      throw new HttpsError("failed-precondition", "Saldo resultante inválido.");
    }
    const gamesPlayed = (profile.gamesPlayed || 0) + 1;
    const wins = (profile.wins || 0) + (returnedCents > amountCents ? 1 : 0);
    const description = game === "slots"
      ? `Slots · ${result.reels.join(" ")}`
      : `Roleta · ${result.number} ${result.color}`;
    transaction.update(userRef, {
      balanceCents: balanceAfter,
      gamesPlayed,
      wins,
      lastGameActionAtMs: actionAtMs,
    });
    transaction.update(rankRef, { balanceCents: balanceAfter });
    transaction.create(transactionRef, {
      description,
      deltaCents,
      createdAt: FieldValue.serverTimestamp(),
    });
    response = {
      result: game === "slots" ? result.reels : { number: result.number, color: result.color },
      payoutMultiplier: result.multiplier,
      deltaCents,
      balanceCents: balanceAfter,
      gamesPlayed,
      wins,
    };
    transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
  });

  return response;
});

exports.startCrash = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const amountCents = request.data?.amountCents;
  const requestId = request.data?.requestId;
  if (!validateWager(amountCents, MAX_TRANSFER_CENTS)
      || typeof requestId !== "string"
      || !/^[a-f0-9-]{36}$/i.test(requestId)) {
    throw new HttpsError("invalid-argument", "Aposta ou identificador inválido.");
  }

  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  const gameRef = userRef.collection("games").doc("crash");
  const requestRef = userRef.collection("gameRequests").doc(requestId);
  const historyRef = userRef.collection("transactions").doc(`${requestId}_crash_bet`);
  const gameId = randomUUID();
  const startedAtMs = Date.now();
  const crashAtBps = crashPointBasisPoints();
  let response;

  await database.runTransaction(async (transaction) => {
    const previousRequest = await transaction.get(requestRef);
    if (previousRequest.exists) {
      response = previousRequest.data().response;
      return;
    }
    const [gameSnapshot, userSnapshot, rankSnapshot] = await Promise.all([
      transaction.get(gameRef),
      transaction.get(userRef),
      transaction.get(rankRef),
    ]);
    if (gameSnapshot.exists && gameSnapshot.data().status === "active") {
      const active = gameSnapshot.data();
      response = {
        gameId: active.gameId,
        amountCents: active.amountCents,
        startedAtMs: active.startedAtMs,
        status: "active",
        resumed: true,
      };
      transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
      return;
    }
    if (!userSnapshot.exists || !rankSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil ainda não foi criado.");
    }
    const profile = userSnapshot.data();
    enforceGameCooldown(profile, startedAtMs);
    const balance = profile.balanceCents || 0;
    if (balance < amountCents) {
      throw new HttpsError("failed-precondition", "Saldo insuficiente.");
    }
    const balanceAfter = balance - amountCents;
    transaction.update(userRef, { balanceCents: balanceAfter, lastGameActionAtMs: startedAtMs });
    transaction.update(rankRef, { balanceCents: balanceAfter });
    transaction.set(gameRef, {
      gameId,
      status: "active",
      amountCents,
      crashAtBps,
      startedAtMs,
      createdAt: FieldValue.serverTimestamp(),
    });
    transaction.create(historyRef, {
      description: "Crash · aposta",
      deltaCents: -amountCents,
      createdAt: FieldValue.serverTimestamp(),
    });
    response = { gameId, amountCents, startedAtMs, status: "active", resumed: false };
    transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
  });

  return response;
});

exports.cashOutCrash = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const gameId = request.data?.gameId;
  const requestId = request.data?.requestId;
  if (typeof gameId !== "string" || !/^[a-f0-9-]{36}$/i.test(gameId)
      || typeof requestId !== "string" || !/^[a-f0-9-]{36}$/i.test(requestId)) {
    throw new HttpsError("invalid-argument", "Identificador da partida inválido.");
  }

  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  const gameRef = userRef.collection("games").doc("crash");
  const requestRef = userRef.collection("gameRequests").doc(requestId);
  const historyRef = userRef.collection("transactions").doc(`${requestId}_crash_payout`);
  const nowMs = Date.now();
  let response;

  await database.runTransaction(async (transaction) => {
    const previousRequest = await transaction.get(requestRef);
    if (previousRequest.exists) {
      response = previousRequest.data().response;
      return;
    }
    const [gameSnapshot, userSnapshot, rankSnapshot] = await Promise.all([
      transaction.get(gameRef),
      transaction.get(userRef),
      transaction.get(rankRef),
    ]);
    if (!gameSnapshot.exists || gameSnapshot.data().gameId !== gameId
        || gameSnapshot.data().status !== "active") {
      throw new HttpsError("failed-precondition", "Esta partida não está ativa.");
    }
    if (!userSnapshot.exists || !rankSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil ainda não foi criado.");
    }

    const game = gameSnapshot.data();
    const profile = userSnapshot.data();
    enforceGameCooldown(profile, nowMs);
    const elapsedMs = Math.max(0, nowMs - game.startedAtMs);
    const multiplierBps = crashMultiplierBasisPoints(elapsedMs);
    const crashed = multiplierBps >= game.crashAtBps;
    if (!crashed && multiplierBps <= 100) {
      throw new HttpsError("failed-precondition", "Aguarde o multiplicador passar de 1,00x.");
    }

    const payoutCents = crashed ? 0 : Math.floor(game.amountCents * multiplierBps / 100);
    const profitCents = payoutCents - game.amountCents;
    const balanceAfter = (profile.balanceCents || 0) + payoutCents;
    if (!Number.isSafeInteger(balanceAfter)) {
      throw new HttpsError("failed-precondition", "Saldo resultante inválido.");
    }
    const gamesPlayed = (profile.gamesPlayed || 0) + 1;
    const wins = (profile.wins || 0) + (profitCents > 0 ? 1 : 0);
    transaction.update(userRef, { balanceCents: balanceAfter, gamesPlayed, wins, lastGameActionAtMs: nowMs });
    transaction.update(rankRef, { balanceCents: balanceAfter });
    transaction.update(gameRef, {
      status: "settled",
      crashed,
      settledAt: FieldValue.serverTimestamp(),
      settledMultiplierBps: multiplierBps,
      payoutCents,
    });
    if (payoutCents > 0) {
      transaction.create(historyRef, {
        description: crashed ? "Crash · encerrado" : "Crash · retirada",
        deltaCents: payoutCents,
        createdAt: FieldValue.serverTimestamp(),
      });
    }
    response = {
      crashed,
      multiplierBps,
      payoutCents,
      profitCents,
      balanceCents: balanceAfter,
      gamesPlayed,
      wins,
    };
    transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
  });

  return response;
});

function publicBlackjackState(game) {
  const active = game.status === "active";
  return {
    gameId: game.gameId,
    status: game.status,
    playerCards: game.playerCards,
    dealerCards: active ? game.dealerCards.slice(0, 1) : game.dealerCards,
    dealerHoleHidden: active,
    wagerCents: game.wagerCents,
    outcome: game.outcome || "",
    payoutCents: game.payoutCents || 0,
    profitCents: game.profitCents || 0,
    balanceCents: game.balanceCents,
  };
}

exports.startBlackjack = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const amountCents = request.data?.amountCents;
  const requestId = request.data?.requestId;
  if (!validateWager(amountCents, MAX_TRANSFER_CENTS)
      || typeof requestId !== "string"
      || !/^[a-f0-9-]{36}$/i.test(requestId)) {
    throw new HttpsError("invalid-argument", "Aposta ou identificador inválido.");
  }

  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  const gameRef = userRef.collection("games").doc("blackjack");
  const requestRef = userRef.collection("gameRequests").doc(requestId);
  const betHistoryRef = userRef.collection("transactions").doc(`${requestId}_blackjack_bet`);
  const payoutHistoryRef = userRef.collection("transactions").doc(`${requestId}_blackjack_payout`);
  const gameId = randomUUID();
  const deck = createShuffledDeck();
  const playerCards = deck.slice(0, 2);
  const dealerCards = deck.slice(2, 4);
  const nextCardIndex = 4;
  let response;

  await database.runTransaction(async (transaction) => {
    const previousRequest = await transaction.get(requestRef);
    if (previousRequest.exists) {
      response = previousRequest.data().response;
      return;
    }
    const [gameSnapshot, userSnapshot, rankSnapshot] = await Promise.all([
      transaction.get(gameRef),
      transaction.get(userRef),
      transaction.get(rankRef),
    ]);
    if (gameSnapshot.exists && gameSnapshot.data().status === "active") {
      response = publicBlackjackState(gameSnapshot.data());
      response.resumed = true;
      transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
      return;
    }
    if (!userSnapshot.exists || !rankSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil ainda não foi criado.");
    }
    const profile = userSnapshot.data();
    const startedAtMs = Date.now();
    enforceGameCooldown(profile, startedAtMs);
    const balance = profile.balanceCents || 0;
    if (balance < amountCents) {
      throw new HttpsError("failed-precondition", "Saldo insuficiente.");
    }

    const initialGame = {
      gameId,
      status: "active",
      playerCards,
      dealerCards,
      deck,
      nextCardIndex,
      wagerCents: amountCents,
      startedAt: FieldValue.serverTimestamp(),
    };
    let balanceAfter = balance - amountCents;
    let gamesPlayed = profile.gamesPlayed || 0;
    let wins = profile.wins || 0;
    const hasNatural = isBlackjack(playerCards) || isBlackjack(dealerCards);
    if (hasNatural) {
      const settlement = settleBlackjack(playerCards, dealerCards, amountCents);
      balanceAfter += settlement.payoutCents;
      gamesPlayed += 1;
      if (settlement.payoutCents > amountCents) wins += 1;
      initialGame.status = "settled";
      initialGame.outcome = settlement.outcome;
      initialGame.payoutCents = settlement.payoutCents;
      initialGame.profitCents = settlement.payoutCents - amountCents;
      initialGame.balanceCents = balanceAfter;
    }
    if (!Number.isSafeInteger(balanceAfter) || balanceAfter < 0) {
      throw new HttpsError("failed-precondition", "Saldo resultante inválido.");
    }

    transaction.update(userRef, { balanceCents: balanceAfter, gamesPlayed, wins, lastGameActionAtMs: startedAtMs });
    transaction.update(rankRef, { balanceCents: balanceAfter });
    transaction.set(gameRef, initialGame);
    transaction.create(betHistoryRef, {
      description: "Blackjack · aposta",
      deltaCents: -amountCents,
      createdAt: FieldValue.serverTimestamp(),
    });
    if (initialGame.status === "settled" && settlementPayout(initialGame) > 0) {
      transaction.create(payoutHistoryRef, {
        description: `Blackjack · ${initialGame.outcome}`,
        deltaCents: initialGame.payoutCents,
        createdAt: FieldValue.serverTimestamp(),
      });
    }
    response = publicBlackjackState(initialGame);
    transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
  });

  return response;
});

function settlementPayout(game) {
  return Number.isSafeInteger(game.payoutCents) ? game.payoutCents : 0;
}

exports.blackjackAction = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const gameId = request.data?.gameId;
  const requestId = request.data?.requestId;
  const action = request.data?.action;
  if (typeof gameId !== "string" || !/^[a-f0-9-]{36}$/i.test(gameId)
      || typeof requestId !== "string" || !/^[a-f0-9-]{36}$/i.test(requestId)
      || !["hit", "stand", "double"].includes(action)) {
    throw new HttpsError("invalid-argument", "Ação ou identificador inválido.");
  }

  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  const gameRef = userRef.collection("games").doc("blackjack");
  const requestRef = userRef.collection("gameRequests").doc(requestId);
  const extraBetRef = userRef.collection("transactions").doc(`${requestId}_blackjack_double`);
  const payoutRef = userRef.collection("transactions").doc(`${requestId}_blackjack_payout`);
  let response;

  await database.runTransaction(async (transaction) => {
    const previousRequest = await transaction.get(requestRef);
    if (previousRequest.exists) {
      response = previousRequest.data().response;
      return;
    }
    const [gameSnapshot, userSnapshot, rankSnapshot] = await Promise.all([
      transaction.get(gameRef),
      transaction.get(userRef),
      transaction.get(rankRef),
    ]);
    if (!gameSnapshot.exists || gameSnapshot.data().gameId !== gameId) {
      throw new HttpsError("failed-precondition", "Esta mão não está ativa.");
    }
    if (gameSnapshot.data().status === "settled") {
      response = publicBlackjackState(gameSnapshot.data());
      transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
      return;
    }
    if (gameSnapshot.data().status !== "active") {
      throw new HttpsError("failed-precondition", "Esta mão não está ativa.");
    }
    if (!userSnapshot.exists || !rankSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil ainda não foi criado.");
    }

    const game = gameSnapshot.data();
    const profile = userSnapshot.data();
    const actionAtMs = Date.now();
    enforceGameCooldown(profile, actionAtMs);
    const playerCards = [...game.playerCards];
    const dealerCards = [...game.dealerCards];
    let nextCardIndex = game.nextCardIndex;
    let wagerCents = game.wagerCents;
    let extraWagerCents = 0;
    if (action === "double") {
      if (playerCards.length !== 2) {
        throw new HttpsError("failed-precondition", "Dobrar só é permitido nas duas primeiras cartas.");
      }
      if ((profile.balanceCents || 0) < wagerCents) {
        throw new HttpsError("failed-precondition", "Saldo insuficiente para dobrar.");
      }
      extraWagerCents = wagerCents;
      wagerCents *= 2;
    }

    let finished = action === "stand" || action === "double";
    if (action === "hit" || action === "double") {
      if (nextCardIndex >= game.deck.length) {
        throw new HttpsError("internal", "Não há cartas disponíveis nesta mão.");
      }
      playerCards.push(game.deck[nextCardIndex]);
      nextCardIndex += 1;
      if (blackjackHandValue(playerCards).total >= 21) finished = true;
    }

    if (!finished) {
      const updatedGame = { ...game, playerCards, nextCardIndex };
      transaction.update(gameRef, { playerCards, nextCardIndex });
      response = publicBlackjackState(updatedGame);
      transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
      return;
    }

    while (blackjackHandValue(dealerCards).total < 17) {
      if (nextCardIndex >= game.deck.length) {
        throw new HttpsError("internal", "Não há cartas disponíveis para o dealer.");
      }
      dealerCards.push(game.deck[nextCardIndex]);
      nextCardIndex += 1;
    }
    const playerBusted = blackjackHandValue(playerCards).total > 21;
    const settlement = settleBlackjack(playerCards, dealerCards, wagerCents, playerBusted);
    const balanceBeforePayout = (profile.balanceCents || 0) - extraWagerCents;
    const balanceAfter = balanceBeforePayout + settlement.payoutCents;
    if (!Number.isSafeInteger(balanceAfter) || balanceAfter < 0) {
      throw new HttpsError("failed-precondition", "Saldo resultante inválido.");
    }
    const gamesPlayed = (profile.gamesPlayed || 0) + 1;
    const wins = (profile.wins || 0) + (settlement.payoutCents > wagerCents ? 1 : 0);
    const finalGame = {
      ...game,
      status: "settled",
      playerCards,
      dealerCards,
      nextCardIndex,
      wagerCents,
      outcome: settlement.outcome,
      payoutCents: settlement.payoutCents,
      profitCents: settlement.payoutCents - wagerCents,
      balanceCents: balanceAfter,
    };

    transaction.update(userRef, { balanceCents: balanceAfter, gamesPlayed, wins, lastGameActionAtMs: actionAtMs });
    transaction.update(rankRef, { balanceCents: balanceAfter });
    transaction.update(gameRef, finalGame);
    if (extraWagerCents > 0) {
      transaction.create(extraBetRef, {
        description: "Blackjack · dobrar aposta",
        deltaCents: -extraWagerCents,
        createdAt: FieldValue.serverTimestamp(),
      });
    }
    if (settlement.payoutCents > 0) {
      transaction.create(payoutRef, {
        description: `Blackjack · ${settlement.outcome}`,
        deltaCents: settlement.payoutCents,
        createdAt: FieldValue.serverTimestamp(),
      });
    }
    response = publicBlackjackState(finalGame);
    transaction.create(requestRef, { response, createdAt: FieldValue.serverTimestamp() });
  });

  return response;
});

exports.buyCosmetic = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const itemId = request.data?.itemId;
  const product = COSMETICS[itemId];
  if (!product) {
    throw new HttpsError("invalid-argument", "Item não encontrado.");
  }

  const userRef = database.collection("users").doc(uid);
  const rankRef = database.collection("leaderboard").doc(uid);
  const transactionRef = userRef.collection("transactions").doc();
  let response;
  await database.runTransaction(async (transaction) => {
    const [userSnapshot, rankSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(rankRef),
    ]);
    if (!userSnapshot.exists || !rankSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil ainda não foi criado.");
    }
    const profile = userSnapshot.data();
    const inventory = Array.isArray(profile.inventory) ? profile.inventory : [];
    if (inventory.includes(itemId)) {
      throw new HttpsError("already-exists", "Você já tem este item.");
    }
    const balance = profile.balanceCents || 0;
    if (balance < product.priceCents) {
      throw new HttpsError("failed-precondition", "Saldo insuficiente.");
    }
    const balanceAfter = balance - product.priceCents;
    transaction.update(userRef, {
      balanceCents: balanceAfter,
      inventory: [...inventory, itemId],
    });
    transaction.update(rankRef, { balanceCents: balanceAfter });
    transaction.create(transactionRef, {
      description: `Loja · ${product.name}`,
      deltaCents: -product.priceCents,
      createdAt: FieldValue.serverTimestamp(),
    });
    response = { itemId, balanceCents: balanceAfter };
  });
  return response;
});

exports.sendChatMessage = onCall(async (request) => {
  const uid = authenticatedUid(request);
  const text = typeof request.data?.text === "string" ? request.data.text.trim() : "";
  const recipientUid = request.data?.recipientUid || null;
  const requestId = request.data?.requestId;
  if (text.length < 1 || text.length > 500) {
    throw new HttpsError("invalid-argument", "A mensagem deve ter entre 1 e 500 caracteres.");
  }
  if (typeof requestId !== "string" || !/^[a-f0-9-]{36}$/i.test(requestId)) {
    throw new HttpsError("invalid-argument", "Identificador da mensagem inválido.");
  }
  if (recipientUid !== null && (typeof recipientUid !== "string" || recipientUid === uid)) {
    throw new HttpsError("invalid-argument", "Destinatário inválido.");
  }

  const senderRef = database.collection("users").doc(uid);
  const recipientRef = recipientUid ? database.collection("users").doc(recipientUid) : null;
  const participants = recipientUid ? [uid, recipientUid].sort() : [];
  const chatId = recipientUid ? participants.join("_") : "global";
  const chatRef = database.collection("chats").doc(chatId);
  const messageRef = chatRef.collection("messages").doc(requestId);
  const sentAtMs = Date.now();
  let response;

  await database.runTransaction(async (transaction) => {
    const reads = [transaction.get(messageRef), transaction.get(senderRef)];
    if (recipientRef) reads.push(transaction.get(recipientRef), transaction.get(chatRef));
    const snapshots = await Promise.all(reads);
    const [existingMessage, senderSnapshot] = snapshots;
    if (existingMessage.exists) {
      const previous = existingMessage.data();
      if (previous.senderUid !== uid || previous.text !== text) {
        throw new HttpsError("already-exists", "Identificador da mensagem já utilizado.");
      }
      response = { chatId, messageId: requestId };
      return;
    }
    if (!senderSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Perfil do remetente não encontrado.");
    }
    const sender = senderSnapshot.data();
    if (Number.isSafeInteger(sender.lastChatAtMs)
        && sentAtMs - sender.lastChatAtMs < CHAT_COOLDOWN_MS) {
      throw new HttpsError("resource-exhausted", "Aguarde um instante antes de enviar outra mensagem.");
    }
    const senderName = safeName(sender.displayName, "Jogador");
    let chatSnapshot = null;
    if (recipientRef) {
      const recipientSnapshot = snapshots[2];
      chatSnapshot = snapshots[3];
      if (!recipientSnapshot.exists) {
        throw new HttpsError("not-found", "Destinatário não encontrado.");
      }
      if (chatSnapshot.exists) {
        const savedParticipants = chatSnapshot.data().participantUids;
        if (!Array.isArray(savedParticipants)
            || savedParticipants.length !== 2
            || savedParticipants[0] !== participants[0]
            || savedParticipants[1] !== participants[1]) {
          throw new HttpsError("permission-denied", "Conversa inválida.");
        }
      }
    }

    const now = FieldValue.serverTimestamp();
    if (recipientRef) {
      const chatData = {
        participantUids: participants,
        lastMessage: text,
        lastMessageAt: now,
      };
      if (chatSnapshot.exists) transaction.update(chatRef, chatData);
      else transaction.create(chatRef, { ...chatData, createdAt: now });
    }
    transaction.update(senderRef, { lastChatAtMs: sentAtMs });
    transaction.create(messageRef, {
      senderUid: uid,
      senderName,
      text,
      createdAt: now,
    });
    response = { chatId, messageId: requestId };
  });

  return response;
});