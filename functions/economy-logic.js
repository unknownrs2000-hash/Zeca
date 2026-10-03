"use strict";

const INFLATION_RATE_BPS_PER_MONTH = 200;
const INFLATION_MONTH_MS = 30 * 24 * 60 * 60 * 1_000;
const INFLATION_EPOCH_MS = Date.UTC(2026, 9, 2);

function monthlyInflationMultiplier(nowMs = Date.now()) {
  const months = Math.max(0, Math.floor((nowMs - INFLATION_EPOCH_MS) / INFLATION_MONTH_MS));
  return (1 + INFLATION_RATE_BPS_PER_MONTH / 10_000) ** months;
}

function priceAfterInflation(basePriceCents, nowMs = Date.now()) {
  if (!Number.isSafeInteger(basePriceCents) || basePriceCents < 0) {
    throw new TypeError("Base price must be a non-negative integer number of cents.");
  }
  const adjusted = Math.round(basePriceCents * monthlyInflationMultiplier(nowMs));
  if (!Number.isSafeInteger(adjusted)) {
    throw new RangeError("Inflated price exceeds the supported amount.");
  }
  return adjusted;
}

module.exports = {
  INFLATION_EPOCH_MS,
  INFLATION_MONTH_MS,
  INFLATION_RATE_BPS_PER_MONTH,
  monthlyInflationMultiplier,
  priceAfterInflation,
};
