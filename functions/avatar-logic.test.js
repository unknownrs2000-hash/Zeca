"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { equipAvatarItem, unequipAvatarSlot } = require("./avatar-logic");

test("equipping an avatar item replaces the item in that slot", () => {
  assert.deepEqual(
    equipAvatarItem(["avatar_hair_wave", "avatar_top_hoodie"], ["avatar_hair_curls", "avatar_top_hoodie"], "avatar_hair_curls"),
    ["avatar_top_hoodie", "avatar_hair_curls"],
  );
});

test("avatar items cannot be equipped unless owned", () => {
  assert.throws(() => equipAvatarItem([], [], "avatar_crown_neon"), /avatar-item-not-owned/);
  assert.throws(() => equipAvatarItem([], ["unknown"], "unknown"), /unknown-avatar-item/);
});

test("an avatar slot can be cleared without affecting other clothing", () => {
  assert.deepEqual(
    unequipAvatarSlot(["avatar_hair_wave", "avatar_top_jacket", "avatar_glasses_round"], "accessory"),
    ["avatar_hair_wave", "avatar_top_jacket"],
  );
});
