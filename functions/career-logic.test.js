"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  WORK_JOBS,
  WORK_TIERS,
  canPromote,
  emptyCareer,
  getWorkJob,
} = require("./career-logic");

test("shared career catalog mirrors all five bot tiers and 35 job choices", () => {
  assert.equal(WORK_JOBS.length, 35);
  assert.deepEqual(WORK_TIERS.map((tier) => tier.level), [1, 10, 25, 45, 70]);
  assert.equal(getWorkJob("entregador_pizza").salaryMinCents, 400);
  assert.equal(getWorkJob("magnata_bilionario").salaryMaxCents, 45_000);
  assert.equal(getWorkJob("not_a_job"), null);
});

test("career promotions require both the level and completed shift threshold", () => {
  const firstTierJob = getWorkJob("entregador_pizza");
  const career = { ...emptyCareer(), shiftsInTier: 12 };
  assert.equal(canPromote(career, firstTierJob, 9), false);
  assert.equal(canPromote(career, firstTierJob, 10), true);
  assert.equal(canPromote({ ...career, shiftsInTier: 11 }, firstTierJob, 10), false);
});
