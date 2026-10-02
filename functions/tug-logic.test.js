"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { applyTugPull } = require("./tug-logic");

test("tug pulls are restricted to active room members and rate limited", () => {
  const room = {
    status: "active",
    creatorUid: "creator",
    opponentUid: "opponent",
    creatorPulls: 0,
    opponentPulls: 0,
  };
  assert.throws(() => applyTugPull(room, "outsider", 1_000), /not-a-player/);
  const first = applyTugPull(room, "creator", 1_000);
  assert.equal(first.creatorPulls, 1);
  assert.throws(() => applyTugPull({ ...room, ...first }, "creator", 1_100), /pull-too-fast/);
});

test("tug match ends when one side gains an eight-pull lead", () => {
  let room = {
    status: "active",
    creatorUid: "creator",
    opponentUid: "opponent",
    creatorPulls: 0,
    opponentPulls: 0,
    lastPullAtMs: {},
  };
  for (let pull = 0; pull < 8; pull += 1) {
    room = { ...room, ...applyTugPull(room, "creator", 1_000 + pull * 121) };
  }
  assert.equal(room.status, "settled");
  assert.equal(room.winnerUid, "creator");
});

test("2v2 teammates contribute to the same team pull count", () => {
  let room = {
    status: "active",
    mode: "2v2",
    players: [
      { uid: "a1", team: "A" },
      { uid: "a2", team: "A" },
      { uid: "b1", team: "B" },
      { uid: "b2", team: "B" },
    ],
    teamAPulls: 0,
    teamBPulls: 0,
    startedAtMs: 1_000,
    lastPullAtMs: {},
  };
  room = { ...room, ...applyTugPull(room, "a1", 1_120) };
  room = { ...room, ...applyTugPull(room, "a2", 1_120) };
  assert.equal(room.teamAPulls, 2);
  room = { ...room, ...applyTugPull(room, "b1", 1_120) };
  assert.equal(room.teamBPulls, 1);
});

test("batched taps are partially accepted at the rate limit and settle at the winning margin", () => {
  const room = {
    status: "active",
    creatorUid: "creator",
    opponentUid: "opponent",
    creatorPulls: 0,
    opponentPulls: 0,
    startedAtMs: 1_000,
    lastPullAtMs: {},
  };
  let current = { ...room, ...applyTugPull(room, "creator", 1_120, 2) };
  assert.equal(current.acceptedPulls, 1);
  let nextPullAt = 1_360;
  while (current.status === "active") {
    current = {
      ...current,
      ...applyTugPull(current, "creator", nextPullAt, 2),
    };
    nextPullAt += 240;
  }
  assert.equal(current.creatorPulls, 8);
  assert.equal(current.status, "settled");
});

test("team race and blitz use their own score targets", () => {
  const players = [
    { uid: "a1", team: "A" },
    { uid: "a2", team: "A" },
    { uid: "b1", team: "B" },
    { uid: "b2", team: "B" },
  ];
  let race = {
    status: "active",
    mode: "2v2",
    gameId: "teamRace",
    players,
    teamAPulls: 0,
    teamBPulls: 0,
    startedAtMs: 1_000,
    lastPullAtMs: {},
  };
  for (let pull = 0; pull < 24; pull += 1) {
    race = { ...race, ...applyTugPull(race, "a1", 1_120 + pull * 121) };
  }
  assert.equal(race.status, "settled");
  assert.equal(race.winnerTeam, "A");
  assert.equal(race.teamAPulls, 24);

  let blitz = { ...race, status: "active", gameId: "teamBlitz", teamAPulls: 0, teamBPulls: 0, lastPullAtMs: {} };
  for (let pull = 0; pull < 18; pull += 1) {
    blitz = { ...blitz, ...applyTugPull(blitz, "a1", 1_120 + pull * 121) };
  }
  assert.equal(blitz.status, "settled");
  assert.equal(blitz.teamAPulls, 36);
});

test("relay requires teammates to alternate before each side scores", () => {
  let room = {
    status: "active",
    mode: "2v2",
    gameId: "teamRelay",
    players: [
      { uid: "a1", team: "A" },
      { uid: "a2", team: "A" },
      { uid: "b1", team: "B" },
      { uid: "b2", team: "B" },
    ],
    teamAPulls: 0,
    teamBPulls: 0,
    startedAtMs: 1_000,
    lastPullAtMs: {},
  };
  room = { ...room, ...applyTugPull(room, "a1", 1_120) };
  assert.throws(() => applyTugPull(room, "a1", 1_241), /relay-turn/);
  room = { ...room, ...applyTugPull(room, "a2", 1_241) };
  assert.equal(room.teamAPulls, 2);
  assert.equal(room.lastPlayerByTeam.A, "a2");
});