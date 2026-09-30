"use strict";

function initializeBalance(profile = {}, initialBalanceCents = 50_000) {
  let balanceCents = Number.isSafeInteger(profile.balanceCents) && profile.balanceCents >= 0
    ? profile.balanceCents
    : initialBalanceCents;
  const hasGameHistory = (profile.gamesPlayed || 0) > 0 || (profile.wins || 0) > 0;
  if (profile.balanceInitialized !== true && balanceCents === 0 && !hasGameHistory) {
    balanceCents = initialBalanceCents;
  }
  return { balanceCents, balanceInitialized: true };
}

function normalizeUsername(username) {
  if (typeof username !== "string") return null;
  const normalized = username.trim().toLowerCase();
  return /^[a-z0-9_]{3,20}$/.test(normalized) ? normalized : null;
}

function levelProgress(gamesPlayed) {
  const safeGamesPlayed = Number.isSafeInteger(gamesPlayed) && gamesPlayed >= 0 ? gamesPlayed : 0;
  const gamesTowardNextLevel = safeGamesPlayed % 10;
  const rewardCents = safeGamesPlayed > 0 && gamesTowardNextLevel === 0 ? 1_000 : 0;
  return {
    level: Math.floor(safeGamesPlayed / 10) + 1,
    gamesTowardNextLevel,
    gamesToNextLevel: 10 - gamesTowardNextLevel,
    rewardCents,
  };
}

module.exports = { initializeBalance, levelProgress, normalizeUsername };