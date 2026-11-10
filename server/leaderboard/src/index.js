// Public leaderboard for the app's games. One row per (game, player) holding that player's best score.
//   POST /v1/score  {game, player, name, score}  -> {ok, rank}
//   POST /v1/name   {player, name}               -> {ok}   renames the player on every board
//   GET  /v1/top?game=stack_game&player=<id>     -> {top: [{name, score, you}], you: {rank, score} | null}
// Player ids are random per install and never returned, so nobody can post as someone else from the list.
import { checkName, checkScore, GAMES, TOP, validPlayer } from "./rules.js";

const MIN_GAP_MS = 2000; // one write per player every 2 s is plenty for a human

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (request.method === "POST" && url.pathname === "/v1/score") return await postScore(await body(request), env);
      if (request.method === "POST" && url.pathname === "/v1/name") return await postName(await body(request), env);
      if (request.method === "GET" && url.pathname === "/v1/top") return await getTop(url, env);
      return json({ error: "not found" }, 404);
    } catch (e) {
      return json({ error: "server error" }, 500);
    }
  },
};

async function body(request) {
  if (Number(request.headers.get("content-length") ?? 0) > 1024) return null;
  try { return await request.json(); } catch { return null; }
}

async function postScore(input, env) {
  const entry = checkScore(input);
  if (entry.error) return json(entry, 400);
  const now = Date.now();
  const last = await env.DB.prepare("SELECT MAX(submitted_at) AS at FROM scores WHERE player = ?").bind(entry.player).first();
  if (last?.at && now - last.at < MIN_GAP_MS) return json({ error: "too fast" }, 429);
  // Keep the best score; the name always follows the latest submission.
  await env.DB.prepare(
    `INSERT INTO scores (game, player, name, score, at, submitted_at) VALUES (?1, ?2, ?3, ?4, ?5, ?5)
     ON CONFLICT (game, player) DO UPDATE SET
       name = excluded.name,
       at = CASE WHEN excluded.score > scores.score THEN excluded.at ELSE scores.at END,
       submitted_at = excluded.submitted_at,
       score = MAX(scores.score, excluded.score)`
  ).bind(entry.game, entry.player, entry.name, entry.score, now).run();
  return json({ ok: true, rank: await rankOf(env, entry.game, entry.player) });
}

async function postName(input, env) {
  const entry = checkName(input);
  if (entry.error) return json(entry, 400);
  await env.DB.prepare("UPDATE scores SET name = ? WHERE player = ?").bind(entry.name, entry.player).run();
  return json({ ok: true });
}

async function getTop(url, env) {
  const game = url.searchParams.get("game");
  if (!GAMES.includes(game)) return json({ error: "unknown game" }, 400);
  const player = url.searchParams.get("player");
  const rows = await env.DB.prepare(
    "SELECT player, name, score FROM scores WHERE game = ? ORDER BY score DESC, at ASC LIMIT ?"
  ).bind(game, TOP).all();
  const top = rows.results.map((r) => ({ name: r.name, score: r.score, you: r.player === player }));
  let you = null;
  if (validPlayer(player)) {
    const mine = await env.DB.prepare("SELECT score FROM scores WHERE game = ? AND player = ?").bind(game, player).first();
    if (mine) you = { rank: await rankOf(env, game, player), score: mine.score };
  }
  return json({ top, you }, 200, "public, max-age=15");
}

/** 1 + players with a better score, or an equal one set earlier. */
async function rankOf(env, game, player) {
  const row = await env.DB.prepare(
    `SELECT 1 + COUNT(*) AS rank FROM scores o, scores m
     WHERE m.game = ?1 AND m.player = ?2 AND o.game = ?1
       AND (o.score > m.score OR (o.score = m.score AND o.at < m.at))`
  ).bind(game, player).first();
  return row?.rank ?? null;
}

function json(data, status = 200, cache = "no-store") {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": cache },
  });
}
