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

module.exports = { initializeBalance };