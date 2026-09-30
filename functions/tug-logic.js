"use strict";

const PULL_COOLDOWN_MS = 120;
const WINNING_PULL_MARGIN = 8;

function applyTugPull(room, uid, nowMs) {
  if (!room || room.status !== "active") throw new Error("room-not-active");
  const side = uid === room.creatorUid ? "creator" : uid === room.opponentUid ? "opponent" : null;
  if (!side) throw new Error("not-a-player");
  const lastPullAtMs = room.lastPullAtMs?.[uid] || 0;
  if (nowMs - lastPullAtMs < PULL_COOLDOWN_MS) throw new Error("pull-too-fast");

  const creatorPulls = (room.creatorPulls || 0) + (side === "creator" ? 1 : 0);
  const opponentPulls = (room.opponentPulls || 0) + (side === "opponent" ? 1 : 0);
  const lead = creatorPulls - opponentPulls;
  const winnerUid = lead >= WINNING_PULL_MARGIN
    ? room.creatorUid
    : lead <= -WINNING_PULL_MARGIN
      ? room.opponentUid
      : "";
  return {
    creatorPulls,
    opponentPulls,
    winnerUid,
    status: winnerUid ? "settled" : "active",
    lastPullAtMs: { ...(room.lastPullAtMs || {}), [uid]: nowMs },
  };
}

module.exports = { PULL_COOLDOWN_MS, WINNING_PULL_MARGIN, applyTugPull };