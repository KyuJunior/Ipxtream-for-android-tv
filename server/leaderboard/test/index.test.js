import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import worker from "../src/index.js";

const player = "0123456789abcdef0123456789abcdef";
const other = "abcdef0123456789abcdef0123456789";
const schema = readFileSync(new URL("../schema.sql", import.meta.url), "utf8");

function database(t, sql = schema) {
  const db = new DatabaseSync(":memory:");
  t.after(() => db.close());
  db.exec(sql);
  const env = { DB: { prepare(sql) { return { bind(...values) {
    const statement = db.prepare(sql);
    const args = /\?\d/.test(sql)
      ? [Object.fromEntries(values.map((value, i) => [i + 1, value]))] : values;
    return {
      async first() { return statement.get(...args); },
      async all() { return { results: statement.all(...args) }; },
      async run() { statement.run(...args); },
    };
  } }; } } };
  return { db, env };
}

function submit(env, id, score, name = "Sam", game = "stack_game") {
  return worker.fetch(new Request("https://leaderboard.test/v1/score", {
    method: "POST", body: JSON.stringify({ game, player: id, name, score }),
  }), env);
}

test("equal and lower submissions preserve tie order and still update the name", async (t) => {
  const { db, env } = database(t);
  const clock = t.mock.method(Date, "now", () => 10_000);
  assert.equal((await submit(env, player, 10)).status, 200);
  clock.mock.mockImplementation(() => 13_000);
  assert.equal((await submit(env, other, 10, "Alex")).status, 200);
  for (const [now, score] of [[16_000, 10], [19_000, 5]]) {
    clock.mock.mockImplementation(() => now);
    const response = await submit(env, player, score, "Taylor");
    assert.deepEqual(await response.json(), { ok: true, rank: 1 });
    const row = db.prepare("SELECT * FROM scores WHERE player = ?").get(player);
    assert.equal(row.score, 10);
    assert.equal(row.at, 10_000);
    assert.equal(row.submitted_at, now);
    assert.equal(row.name, "Taylor");
  }
  const response = await worker.fetch(new Request(
    `https://leaderboard.test/v1/top?game=stack_game&player=${player}`), env);
  const board = await response.json();
  assert.deepEqual(board.top.map((row) => row.name), ["Taylor", "Alex"]);
  assert.deepEqual(board.you, { rank: 1, score: 10 });
});

test("an improved score gets a new tie timestamp", async (t) => {
  const { db, env } = database(t);
  const clock = t.mock.method(Date, "now", () => 10_000);
  await submit(env, player, 5);
  clock.mock.mockImplementation(() => 13_000);
  await submit(env, other, 10);
  clock.mock.mockImplementation(() => 16_000);
  const response = await submit(env, player, 10);
  assert.deepEqual(await response.json(), { ok: true, rank: 2 });
  assert.equal(db.prepare("SELECT at FROM scores WHERE player = ?").get(player).at, 16_000);
});

test("non-improving submissions still rate-limit the player across games", async (t) => {
  const { env } = database(t);
  const clock = t.mock.method(Date, "now", () => 10_000);
  await submit(env, player, 10);
  clock.mock.mockImplementation(() => 13_000);
  assert.equal((await submit(env, player, 5)).status, 200);
  clock.mock.mockImplementation(() => 14_999);
  assert.equal((await submit(env, player, 10)).status, 429);
  assert.equal((await submit(env, player, 10, "Sam", "penalty_game")).status, 429);
  clock.mock.mockImplementation(() => 15_000);
  assert.equal((await submit(env, player, 10, "Sam", "penalty_game")).status, 200);
});

test("migration preserves scores and seeds rate limiting from the old timestamp", async (t) => {
  const oldSchema = schema.replace(/  submitted_at INTEGER NOT NULL,\r?\n/, "");
  const { db, env } = database(t, oldSchema);
  db.prepare("INSERT INTO scores VALUES (?, ?, ?, ?, ?)").run("stack_game", player, "Sam", 10, 10_000);
  db.exec(readFileSync(new URL("../migrations/0001_score_submission_time.sql", import.meta.url), "utf8"));
  assert.equal(db.prepare("SELECT submitted_at FROM scores").get().submitted_at, 10_000);
  const clock = t.mock.method(Date, "now", () => 11_999);
  assert.equal((await submit(env, player, 10)).status, 429);
  clock.mock.mockImplementation(() => 12_000);
  assert.deepEqual(await (await submit(env, player, 10)).json(), { ok: true, rank: 1 });
  assert.equal(db.prepare("SELECT at FROM scores").get().at, 10_000);
});
