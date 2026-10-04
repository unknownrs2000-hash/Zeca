"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { closeOpenRooms, planOpenRooms } = require("./prepare-tug-cutover");

const OPEN = ["waiting", "ready", "active"];

function makeDatabase(initial) {
  const rooms = new Map(Object.entries(initial).map(([id, room]) => [id, structuredClone(room)]));
  const refFor = (id) => ({
    path: `tugRooms/${id}`,
    async set(patch) { rooms.set(id, { ...rooms.get(id), ...patch }); },
  });
  const snapshotOf = (limit) => {
    const entries = [...rooms].filter(([, room]) => OPEN.includes(room.status)).slice(0, limit);
    return {
      empty: entries.length === 0,
      docs: entries.map(([id, room]) => ({ id, data: () => structuredClone(room), ref: refFor(id) })),
    };
  };
  const query = {
    where: () => query,
    limit: (limit) => ({ get: async () => snapshotOf(limit) }),
    get: async () => snapshotOf(Infinity),
  };
  return {
    rooms,
    collection: () => ({ where: () => query }),
    applyProjection: (id, patch) => rooms.set(id, { ...rooms.get(id), ...patch }),
  };
}

function makeStore(database) {
  const operations = new Map();
  return {
    operations,
    withActionLock: async (key, callback) => callback({ key }),
    async applyOperation(input) {
      if (operations.has(input.operationId)) return operations.get(input.operationId);
      operations.set(input.operationId, input);
      for (const write of input.projection.writes) {
        database.applyProjection(write.path.split("/")[1], write.data);
      }
      return input;
    },
  };
}

const seed = () => ({
  r1: { roomId: "r1", status: "ready", stakeCents: 300, players: [{ uid: "a" }, { uid: "b" }] },
  r2: { roomId: "r2", status: "waiting", stakeCents: 100, players: [{ uid: "c" }] },
  r3: { roomId: "r3", status: "completed", stakeCents: 500, players: [{ uid: "d" }] },
  r4: { roomId: "r4", status: "waiting", stakeCents: 100, players: [] },
});

test("dry-run plan reports the refunds and changes nothing", async () => {
  const database = makeDatabase(seed());
  const plan = await planOpenRooms({ database });
  assert.equal(plan.rooms, 3);
  assert.equal(plan.playersToRefund, 3);
  assert.equal(plan.refundTotalCents, 700);
  assert.equal(database.rooms.get("r1").status, "ready");
});

test("execution refunds each stake once and a second run does nothing", async () => {
  const database = makeDatabase(seed());
  const store = makeStore(database);
  const first = await closeOpenRooms({ database, store });
  assert.deepEqual(first, { roomsClosed: 3, playersRefunded: 3 });
  assert.equal(store.operations.size, 2);
  assert.deepEqual(
    store.operations.get("tug-cutover-refund:r1").entries,
    [{ uid: "a", deltaCents: 300 }, { uid: "b", deltaCents: 300 }],
  );
  for (const id of ["r1", "r2", "r4"]) assert.equal(database.rooms.get(id).status, "cancelled");
  assert.equal(database.rooms.get("r3").status, "completed");
  const second = await closeOpenRooms({ database, store });
  assert.deepEqual(second, { roomsClosed: 0, playersRefunded: 0 });
  assert.equal(store.operations.size, 2);
});

test("an invalid stake aborts before any refund", async () => {
  const database = makeDatabase({
    bad: { roomId: "bad", status: "waiting", stakeCents: -1, players: [{ uid: "a" }] },
  });
  const store = makeStore(database);
  await assert.rejects(closeOpenRooms({ database, store }), (error) => error.code === "failed-precondition");
  assert.equal(store.operations.size, 0);
  assert.equal(database.rooms.get("bad").status, "waiting");
});

test("duplicate players in a room are refunded once", async () => {
  const database = makeDatabase({
    dup: { roomId: "dup", status: "active", stakeCents: 100, players: [{ uid: "a" }, { uid: "a" }, { uid: "b" }] },
  });
  const store = makeStore(database);
  await closeOpenRooms({ database, store });
  assert.equal(store.operations.get("tug-cutover-refund:dup").entries.length, 2);
});