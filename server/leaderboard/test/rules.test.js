import { test } from "node:test";
import assert from "node:assert/strict";
import { checkScore, checkName, cleanName, isBlocked } from "../src/rules.js";

const player = "0123456789abcdef0123456789abcdef";

test("a normal score passes and its name is cleaned", () => {
  assert.deepEqual(checkScore({ game: "stack_game", player, name: "  Ya:ser; ", score: 12 }),
    { game: "stack_game", player, name: "Yaser", score: 12 });
});

test("bad input is refused", () => {
  assert.ok(checkScore({ game: "chess", player, name: "a", score: 1 }).error);
  assert.ok(checkScore({ game: "stack_game", player: "x", name: "a", score: 1 }).error);
  assert.ok(checkScore({ game: "stack_game", player, name: "a", score: -1 }).error);
  assert.ok(checkScore({ game: "stack_game", player, name: "a", score: 1.5 }).error);
  assert.ok(checkScore({ game: "stack_game", player, name: "   ", score: 1 }).error);
  assert.ok(checkScore(null).error);
  assert.ok(checkName({ player: "nope", name: "Sam" }).error);
});

test("offensive names are blocked, ordinary ones are not", () => {
  for (const bad of ["fuck you", "F.U.C.K", "sh1t", "big dick", "زب", "يا شرموط", "قحبة"]) assert.ok(isBlocked(bad), bad);
  for (const ok of ["Grape", "Dickens", "زبيدة", "اكسترا", "Mighty Rocket 96", "الصقر السريع 42", "Yaser"]) assert.ok(!isBlocked(ok), ok);
});

test("names are capped at 20 characters", () => {
  assert.equal(cleanName("x".repeat(40)).length, 20);
});
