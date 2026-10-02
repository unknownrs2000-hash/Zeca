"use strict";

const PULL_COOLDOWN_MS = 120;
const WINNING_PULL_MARGIN = 8;
const MAX_PULLS_PER_BATCH = 8;
const TEAM_RACE_TARGET = 24;
const TEAM_RELAY_TARGET = 12;
const TEAM_BLITZ_TARGET = 36;

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
  if (allowedPulls < 1) throw new Error("pull-too-fast");

  const teamAPulls = room.teamAPulls ?? room.creatorPulls ?? 0;
  const teamBPulls = room.teamBPulls ?? room.opponentPulls ?? 0;
  const lead = teamAPulls - teamBPulls;
  const gameId = room.gameId || "tug";
  const lastPlayerByTeam = { ...(room.lastPlayerByTeam || {}) };
  if (gameId === "teamRelay" && lastPlayerByTeam[player.team] === uid) {
    throw new Error("relay-turn");
  }
  const pullsToWin = gameId === "teamRace"
    ? TEAM_RACE_TARGET - (player.team === "A" ? teamAPulls : teamBPulls)
    : gameId === "teamRelay"
      ? TEAM_RELAY_TARGET - (player.team === "A" ? teamAPulls : teamBPulls)
      : gameId === "teamBlitz"
        ? TEAM_BLITZ_TARGET - (player.team === "A" ? teamAPulls : teamBPulls)
        : player.team === "A" ? WINNING_PULL_MARGIN - lead : WINNING_PULL_MARGIN + lead;
  const appliedPulls = Math.min(pullCount, allowedPulls, pullsToWin);
  const scoringPulls = gameId === "teamBlitz" ? appliedPulls * 2 : appliedPulls;
  const nextTeamAPulls = teamAPulls + (player.team === "A" ? scoringPulls : 0);
  const nextTeamBPulls = teamBPulls + (player.team === "B" ? scoringPulls : 0);
  const nextLead = nextTeamAPulls - nextTeamBPulls;
  const winnerTeam = gameId === "teamRace" || gameId === "teamRelay" || gameId === "teamBlitz"
    ? nextTeamAPulls >= (gameId === "teamRace" ? TEAM_RACE_TARGET : gameId === "teamRelay" ? TEAM_RELAY_TARGET : TEAM_BLITZ_TARGET)
      ? "A"
      : nextTeamBPulls >= (gameId === "teamRace" ? TEAM_RACE_TARGET : gameId === "teamRelay" ? TEAM_RELAY_TARGET : TEAM_BLITZ_TARGET)
        ? "B"
        : ""
    : nextLead >= WINNING_PULL_MARGIN ? "A" : nextLead <= -WINNING_PULL_MARGIN ? "B" : "";
  const winner = winnerTeam ? playersInRoom(room).find((member) => member.team === winnerTeam) : null;
  if (appliedPulls > 0 && gameId === "teamRelay") lastPlayerByTeam[player.team] = uid;
  return {
    teamAPulls: nextTeamAPulls,
    teamBPulls: nextTeamBPulls,
    creatorPulls: nextTeamAPulls,
    opponentPulls: nextTeamBPulls,
    winnerTeam,
    winnerUid: winner?.uid || "",
    acceptedPulls: appliedPulls,
    lastPlayerByTeam,
    status: winnerTeam ? "settled" : "active",
    lastPullAtMs: { ...(room.lastPullAtMs || {}), [uid]: nowMs },
  };
}

module.exports = { MAX_PULLS_PER_BATCH, PULL_COOLDOWN_MS, WINNING_PULL_MARGIN, applyTugPull };