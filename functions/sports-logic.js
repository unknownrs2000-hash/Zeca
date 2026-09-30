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

function parseMatchOdds(payload, fixtureId, homeTeam, awayTeam) {
  const fixtureOdds = (payload?.response || []).find((entry) => Number(entry?.fixture?.id) === fixtureId);
  const markets = new Map();
  for (const bookmaker of fixtureOdds?.bookmakers || []) {
    for (const bet of bookmaker?.bets || []) {
      const betName = String(bet.name || "").toLowerCase();
      const betId = Number(bet.id);
      const marketId = betId === 1 || ["match winner", "1x2", "fulltime result"].includes(betName)
        ? "match_winner"
        : betId === 5 || betName.includes("over/under") || betName.includes("goals over")
          ? "total_goals"
          : betId === 8 || betName.includes("both teams score")
            ? "both_teams_score"
            : "";
      if (!marketId || markets.has(marketId)) continue;

      const options = [];
      for (const value of bet.values || []) {
        const label = String(value.value || "").trim();
        const odds = Number(value.odd);
        if (!Number.isFinite(odds) || odds <= 1) continue;
        let selectionId = "";
        let selectionName = label;
        let line = null;
        if (marketId === "match_winner") {
          const normalized = label.toLowerCase();
          if (["home", homeTeam.toLowerCase()].includes(normalized)) selectionId = "home";
          else if (["draw", "tie", "x"].includes(normalized)) selectionId = "draw";
          else if (["away", awayTeam.toLowerCase()].includes(normalized)) selectionId = "away";
          selectionName = selectionId === "home" ? homeTeam : selectionId === "away" ? awayTeam : "Empate";
        } else if (marketId === "total_goals") {
          const total = label.match(/^(over|under)\s+(\d+(?:\.\d+)?)$/i);
          if (!total) continue;
          line = Number(total[2]);
          if (!Number.isFinite(line) || !Number.isInteger(line * 2)) continue;
          const side = total[1].toLowerCase();
          selectionId = `${side}_${String(line).replace(".", "_")}`;
          selectionName = `${side === "over" ? "Mais de" : "Menos de"} ${line} gols`;
        } else {
          const normalized = label.toLowerCase();
          if (["yes", "sim"].includes(normalized)) selectionId = "yes";
          else if (["no", "não", "nao"].includes(normalized)) selectionId = "no";
          selectionName = selectionId === "yes" ? "Ambas marcam · Sim" : "Ambas marcam · Não";
        }
        if (selectionId) {
          options.push({
            marketId,
            marketName: marketId === "match_winner" ? "Resultado 1X2"
              : marketId === "total_goals" ? "Total de gols" : "Ambas marcam",
            selectionId,
            selectionName,
            line,
            oddsBps: Math.round(odds * 10_000),
            bookmaker: String(bookmaker.name || ""),
          });
        }
      }
      if (options.length > 0) markets.set(marketId, options);
    }
  }
  return [...markets.values()].flat();
}

function parseWinnerOdds(payload, fixtureId, homeTeam, awayTeam) {
  const options = parseMatchOdds(payload, fixtureId, homeTeam, awayTeam);
  const home = options.find((option) => option.marketId === "match_winner" && option.selectionId === "home");
  const away = options.find((option) => option.marketId === "match_winner" && option.selectionId === "away");
  return home && away ? { homeOddsBps: home.oddsBps, awayOddsBps: away.oddsBps } : null;
}

function fixtureWinner(fixture) {
  if (!fixture || !["FT", "AET", "PEN"].includes(fixture.status)
      || !Number.isInteger(fixture.homeGoals) || !Number.isInteger(fixture.awayGoals)) return null;
  if (fixture.homeGoals === fixture.awayGoals) return "draw";
  return fixture.homeGoals > fixture.awayGoals ? "home" : "away";
}

function sportsPayoutCents(wagerCents, oddsBps) {
  if (!Number.isSafeInteger(wagerCents) || wagerCents < 100
      || !Number.isSafeInteger(oddsBps) || oddsBps <= 10_000 || oddsBps > 10_000_000) {
    throw new RangeError("Invalid sports wager.");
  }
  return Number(BigInt(wagerCents) * BigInt(oddsBps) / 10_000n);
}

function combinedOddsBps(legs) {
  if (!Array.isArray(legs) || legs.length < 1 || legs.length > 10) throw new RangeError("Invalid accumulator.");
  let odds = 10_000n;
  for (const leg of legs) {
    if (!Number.isSafeInteger(leg.oddsBps) || leg.oddsBps <= 10_000 || leg.oddsBps > 1_000_000) {
      throw new RangeError("Invalid sports odds.");
    }
    odds = odds * BigInt(leg.oddsBps) / 10_000n;
    if (odds > 10_000_000n) throw new RangeError("Accumulator odds exceed the limit.");
  }
  return Number(odds);
}

function settleSportsSelection(fixture, leg) {
  const result = fixtureWinner(fixture);
  if (!result) return null;
  if (leg.marketId === "match_winner") return result === leg.selectionId ? "won" : "lost";
  if (leg.marketId === "total_goals") {
    const line = Number(leg.line);
    if (!Number.isFinite(line) || !Number.isInteger(line * 2)) throw new RangeError("Invalid goal line.");
    const total = fixture.homeGoals + fixture.awayGoals;
    if (total === line) return "void";
    return (leg.selectionId.startsWith("over_") ? total > line : total < line) ? "won" : "lost";
  }
  if (leg.marketId === "both_teams_score") {
    const bothScored = fixture.homeGoals > 0 && fixture.awayGoals > 0;
    return (leg.selectionId === "yes") === bothScored ? "won" : "lost";
  }
  throw new RangeError("Unsupported sports market.");
}

module.exports = {
  combinedOddsBps,
  fixtureWinner,
  parseFixtures,
  parseMatchOdds,
  parseWinnerOdds,
  settleSportsSelection,
  sportsPayoutCents,
};