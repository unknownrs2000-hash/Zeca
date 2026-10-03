"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  INFLATION_EPOCH_MS,
  INFLATION_MONTH_MS,
  monthlyInflationMultiplier,
  priceAfterInflation,
} = require("./economy-logic");

test("store inflation compounds by 2% each completed 30-day period", () => {
  assert.equal(monthlyInflationMultiplier(INFLATION_EPOCH_MS), 1);
  assert.equal(monthlyInflationMultiplier(INFLATION_EPOCH_MS + INFLATION_MONTH_MS), 1.02);
  assert.equal(monthlyInflationMultiplier(INFLATION_EPOCH_MS + 2 * INFLATION_MONTH_MS), 1.02 ** 2);
  assert.equal(priceAfterInflation(1_000, INFLATION_EPOCH_MS + INFLATION_MONTH_MS), 1_020);
});

test("inflation rejects invalid base prices and guards integer overflow", () => {
  assert.throws(() => priceAfterInflation(-1), TypeError);
  assert.throws(() => priceAfterInflation(1.5), TypeError);
  assert.throws(() => priceAfterInflation(Number.MAX_SAFE_INTEGER, INFLATION_EPOCH_MS + 12 * INFLATION_MONTH_MS), RangeError);
});
