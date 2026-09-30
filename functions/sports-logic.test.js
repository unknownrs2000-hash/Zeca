"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { fixtureWinner, parseFixtures, parseWinnerOdds, sportsPayoutCents } = require("./sports-logic");

test("parses fixture identity, teams, status, and kickoff", () => {
  const fixtures = parseFixtures({
    response: [{
      fixture: { id: 123, date: "2026-10-01T19:00:00+00:00", status: { short: "NS" } },
      teams: { home: { name: "Time A" }, away: { name: "Time B" } },
      league: { name: "Liga Exemplo" },
      goals: { home: null, away: null },
    }],
  });
  assert.equal(fixtures[0].fixtureId, 123);
  assert.equal(fixtures[0].homeTeam, "Time A");
  assert.equal(fixtures[0].status, "NS");
});

test("extracts only home and away winner odds", () => {
  const odds = parseWinnerOdds({
    response: [{
      fixture: { id: 123 },
      bookmakers: [{ bets: [{
        id: 1,
        name: "Match Winner",
        values: [{ value: "Home", odd: "1.75" }, { value: "Draw", odd: "3.2" }, { value: "Away", odd: "4.5" }],
      }] }],
    }],
  }, 123, "Time A", "Time B");
  assert.deepEqual(odds, { homeOddsBps: 17_500, awayOddsBps: 45_000 });
});

test("settles only final match results; draws are not a win", () => {
  assert.equal(fixtureWinner({ status: "NS" }), null);
  assert.equal(fixtureWinner({ status: "FT", homeGoals: 2, awayGoals: 1 }), "home");
  assert.equal(fixtureWinner({ status: "AET", homeGoals: 1, awayGoals: 2 }), "away");
  assert.equal(fixtureWinner({ status: "FT", homeGoals: 0, awayGoals: 0 }), "draw");
});

test("calculates moneyline payout in integer cents", () => {
  assert.equal(sportsPayoutCents(1_000, 17_500), 1_750);
  assert.throws(() => sportsPayoutCents(1_000, 10_000), RangeError);
});