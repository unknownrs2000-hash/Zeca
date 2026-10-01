"use strict";

const PULL_COOLDOWN_MS = 120;
const WINNING_PULL_MARGIN = 8;
const MAX_PULLS_PER_BATCH = 8;

function playersInRoom(room) {
  if (Array.isArray(room?.players)) return room.players;
  return [
    { uid: room?.creatorUid, team: "A" },
    ...(room?.opponentUid ? [{ uid: room.opponentUid, team: "B" }] : []),
  ];
}

function applyTugPull(room, uid, nowMs, pullCount = 1) {
  if (!room || room.status !== "active") throw new Error("room-not-active");
  const player = playersInRoom(room).find((member) => member.uid === uid);
  if (!player || !["A", "B"].includes(player.team)) throw new Error("not-a-player");
  if (!Number.isInteger(pullCount) || pullCount < 1 || pullCount > MAX_PULLS_PER_BATCH) {
    throw new Error("invalid-pull-count");
  }
  const lastPullAtMs = room.lastPullAtMs?.[uid] ?? room.startedAtMs ?? nowMs - PULL_COOLDOWN_MS;
  const allowedPulls = Math.floor((nowMs - lastPullAtMs) / PULL_COOLDOWN_MS);
  if (allowedPulls < 1 || pullCount > allowedPulls) throw new Error("pull-too-fast");

  const teamAPulls = room.teamAPulls ?? room.creatorPulls ?? 0;
  const teamBPulls = room.teamBPulls ?? room.opponentPulls ?? 0;
  const lead = teamAPulls - teamBPulls;
  const pullsToWin = player.team === "A" ? WINNING_PULL_MARGIN - lead : WINNING_PULL_MARGIN + lead;
  const appliedPulls = Math.min(pullCount, pullsToWin);
  const nextTeamAPulls = teamAPulls + (player.team === "A" ? appliedPulls : 0);
  const nextTeamBPulls = teamBPulls + (player.team === "B" ? appliedPulls : 0);
  const nextLead = nextTeamAPulls - nextTeamBPulls;
  const winnerTeam = nextLead >= WINNING_PULL_MARGIN ? "A" : nextLead <= -WINNING_PULL_MARGIN ? "B" : "";
  const winner = winnerTeam ? playersInRoom(room).find((member) => member.team === winnerTeam) : null;
  return {
    teamAPulls: nextTeamAPulls,
    teamBPulls: nextTeamBPulls,
    creatorPulls: nextTeamAPulls,
    opponentPulls: nextTeamBPulls,
    winnerTeam,
    winnerUid: winner?.uid || "",
    acceptedPulls: appliedPulls,
    status: winnerTeam ? "settled" : "active",
    lastPullAtMs: { ...(room.lastPullAtMs || {}), [uid]: nowMs },
  };
}

module.exports = { MAX_PULLS_PER_BATCH, PULL_COOLDOWN_MS, WINNING_PULL_MARGIN, applyTugPull };