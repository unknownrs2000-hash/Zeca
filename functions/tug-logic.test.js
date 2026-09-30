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