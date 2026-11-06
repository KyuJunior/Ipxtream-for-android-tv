// Pure rules shared by the Worker and its tests: what a valid score and a valid name look like.

export const GAMES = [
  "stack_game", "penalty_game", "drift_game", "sling_game", "nova_game",
  "slicer_game", "wreck_game", "bouncer_game", "synergy_game",
];

export const NAME_MAX = 20;
// shortcut: one generous cap for every game, since scores come from the client and can't be proven; per-game caps if cheating shows up.
export const SCORE_MAX = 100_000_000;
export const TOP = 50;

// Offensive words. Keep both lists in step with GameNameFilter.kt in the app.
// Matched as whole words: short ones hide inside ordinary names ("grape", "زبيدة").
export const BLOCKED_WORDS = [
  "dick", "rape", "slut", "cunt", "whore", "penis", "vagina", "nazi", "hitler", "bastard",
  "كس", "طيز", "زب", "نيك", "خول", "عرص", "لوطي", "عاهر", "زبال",
];
// Matched anywhere, even glued to other letters: no ordinary name contains them.
export const BLOCKED_ANYWHERE = [
  "fuck", "shit", "bitch", "nigger", "nigga", "faggot", "porn", "asshole", "pussy",
  "شرموط", "قحبه", "منيوك", "متناك", "انيك", "كسمك", "كسختك",
];

/** Lower case, Arabic letter variants folded, common digit swaps undone; keeps letters and spaces. */
export function normalise(text) {
  return String(text)
    .toLowerCase()
    .replace(/[ً-ْـ]/g, "")      // tashkeel and tatweel
    .replace(/[أإآٱ]/g, "ا")
    .replace(/ة/g, "ه")
    .replace(/ى/g, "ي")
    .replace(/0/g, "o").replace(/1/g, "i").replace(/3/g, "e").replace(/4/g, "a")
    .replace(/5/g, "s").replace(/7/g, "t").replace(/@/g, "a").replace(/\$/g, "s")
    .replace(/[^\p{L}]+/gu, " ")
    .trim();
}

const WORDS = new Set(BLOCKED_WORDS.map(normalise));
const ANYWHERE = BLOCKED_ANYWHERE.map((w) => normalise(w).replace(/ /g, ""));

export function isBlocked(name) {
  const flat = normalise(name);
  if (flat.split(" ").some((word) => WORDS.has(word))) return true;
  const glued = flat.replace(/ /g, "");
  return ANYWHERE.some((word) => glued.includes(word));
}

/** Same cleaning as the app: no ':' or ';', no control characters, trimmed, at most NAME_MAX. */
export function cleanName(raw) {
  return [...String(raw ?? "")]
    .filter((c) => c !== ":" && c !== ";" && !/\p{Cc}/u.test(c))
    .join("")
    .trim()
    .slice(0, NAME_MAX);
}

export function validPlayer(id) {
  return typeof id === "string" && /^[0-9a-f]{32}$/.test(id);
}

/** Returns the cleaned score entry, or an error message for a 400 reply. */
export function checkScore(body) {
  if (!body || typeof body !== "object") return { error: "bad body" };
  const { game, player, score } = body;
  if (!GAMES.includes(game)) return { error: "unknown game" };
  if (!validPlayer(player)) return { error: "bad player" };
  if (!Number.isInteger(score) || score < 0 || score > SCORE_MAX) return { error: "bad score" };
  const name = cleanName(body.name);
  if (!name) return { error: "empty name" };
  if (isBlocked(name)) return { error: "name not allowed" };
  return { game, player, score, name };
}

export function checkName(body) {
  if (!body || !validPlayer(body.player)) return { error: "bad player" };
  const name = cleanName(body.name);
  if (!name) return { error: "empty name" };
  if (isBlocked(name)) return { error: "name not allowed" };
  return { player: body.player, name };
}
