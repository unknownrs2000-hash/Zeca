"use strict";

function parseFixtures(payload) {
  if (!Array.isArray(payload?.response)) return [];
  return payload.response.map((entry) => {
    const fixture = entry?.fixture || {};
    const teams = entry?.teams || {};
    const league = entry?.league || {};
    const fixtureId = Number(fixture.id);
    const kickoffMs = Date.parse(fixture.date);
    if (!Number.isSafeInteger(fixtureId) || !Number.isFinite(kickoffMs)
        || typeof teams.home?.name !== "string" || typeof teams.away?.name !== "string") return null;
    return {
      fixtureId,
      kickoffMs,
      status: typeof fixture.status?.short === "string" ? fixture.status.short : "",
      homeTeam: teams.home.name,
      awayTeam: teams.away.name,
      league: typeof league.name === "string" ? league.name : "Futebol",
      homeGoals: Number.isInteger(entry.goals?.home) ? entry.goals.home : null,
      awayGoals: Number.isInteger(entry.goals?.away) ? entry.goals.away : null,
    };
  }).filter(Boolean);
}

function parseWinnerOdds(payload, fixtureId, homeTeam, awayTeam) {
  const fixtureOdds = (payload?.response || []).find((entry) => Number(entry?.fixture?.id) === fixtureId);
  for (const bookmaker of fixtureOdds?.bookmakers || []) {
    for (const bet of bookmaker?.bets || []) {
      const betName = String(bet.name || "").toLowerCase();
      if (!["match winner", "1x2", "fulltime result"].includes(betName) && Number(bet.id) !== 1) continue;
      const values = bet.values || [];
      const home = values.find((value) => ["home", homeTeam.toLowerCase()].includes(String(value.value).toLowerCase()));
      const away = values.find((value) => ["away", awayTeam.toLowerCase()].includes(String(value.value).toLowerCase()));
      const homeOdds = Number(home?.odd);
      const awayOdds = Number(away?.odd);
      if (Number.isFinite(homeOdds) && homeOdds > 1 && Number.isFinite(awayOdds) && awayOdds > 1) {
        return { homeOddsBps: Math.round(homeOdds * 10_000), awayOddsBps: Math.round(awayOdds * 10_000) };
      }
    }
  }
  return null;
}

function fixtureWinner(fixture) {
  if (!fixture || !["FT", "AET", "PEN"].includes(fixture.status)
      || !Number.isInteger(fixture.homeGoals) || !Number.isInteger(fixture.awayGoals)) return null;
  if (fixture.homeGoals === fixture.awayGoals) return "draw";
  return fixture.homeGoals > fixture.awayGoals ? "home" : "away";
}

function sportsPayoutCents(wagerCents, oddsBps) {
  if (!Number.isSafeInteger(wagerCents) || wagerCents < 100
      || !Number.isSafeInteger(oddsBps) || oddsBps <= 10_000 || oddsBps > 1_000_000) {
    throw new RangeError("Invalid sports wager.");
  }
  return Number(BigInt(wagerCents) * BigInt(oddsBps) / 10_000n);
}

module.exports = { fixtureWinner, parseFixtures, parseWinnerOdds, sportsPayoutCents };