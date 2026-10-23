# Matches section: plan for owner approval

Status: **plan approved; Release 1 built (2026-10-23), Releases 2–3 not started.** The owner's decisions (§7) are final and override anything
earlier in this doc that disagrees with them.

Goal (owner): most people use IPTV to watch football. Add a **Matches** section, fed by a free API,
that shows scheduled matches and which channel shows each one. Pressing a match that is live opens
the channel showing it.

---

## 1. Fixture APIs: what is free and usable from a TV app

Tested live on 2026-10-23 from this machine (not just the docs), except where marked *docs*.

| API | Key? | Free limits | Major leagues | Saudi Pro League | Other Arab leagues | Crests | Live status | TV broadcasters |
|---|---|---|---|---|---|---|---|---|
| **ESPN public JSON** (`site.api.espn.com/apis/site/v2/sports/soccer/{league}/scoreboard?dates=YYYYMMDD`) | **None** | No published limit; responses are CDN-cached (`max-age=5`) | PL, La Liga, Serie A, Bundesliga, Ligue 1, UCL, UEL, UECL, cups, World Cup + qualifiers | **Yes** (`ksa.1`, `ksa.kings.cup`) | AFC Champions League, AFC Cup, CAF Champions League/Confed. **No** Egypt, UAE, Qatar, Morocco, Algeria, Tunisia leagues | Yes (logo URL per team) | Yes: `pre` / `in` / `post` + clock and score | Only **US** networks (Paramount+, ESPN+, USA Net). Useless for Arab viewers |
| **TheSportsDB** v1 (`/api/v1/json/123/...`) | Public test key `123` | 30 req/min *docs*; the free key **truncates results** (`eventsday` returned 3 matches for a whole day, `eventstv` returned 1 row) | Yes | Yes (league 4668) | Many, incl. Egypt | Yes | Basic (`strStatus`) | `eventstv` has per-country channels, but only for the premium key (~US$9/month) |
| **football-data.org** v4 | Free personal key (anonymous also works: 50 req/day seen in headers) | 10 req/min with key *docs* | 12 competitions: PL, PD, SA, BL1, FL1, CL, DED, PPL, ELC, BSA, WC, EC | **No** on free tier | No | Yes | Yes, slightly delayed | No |
| **API-Football** (api-sports.io) | Free key, required | 100 req/day, 10/min *docs*; free plan is reported to cover only recent/older seasons, not all, so it needs checking before relying on it | All | Yes | Yes (Egypt, UAE, Qatar, Morocco...) | Yes | Yes | No |
| **OpenLigaDB** | None | Generous | Bundesliga and other German leagues only | No | No | Yes | Yes | No |

**Findings**

- **No free API lists Arab TV channels (beIN, SSC, AD Sports, Alkass, Shahid) per match.** ESPN's
  broadcasters are for the US. TheSportsDB has the data, but behind its premium key. So mapping a
  match to a channel has to be done in the app (§2), whatever the source.
- **ESPN is the best free default:** no key, no account, one request per league per day, crests,
  live state and score, and it covers the Saudi league and the Asian/African club competitions. The
  risk is that the API is undocumented: ESPN could change or block it without notice.
- The gap is the domestic leagues of Egypt, UAE, Qatar and the Maghreb. The only free source that has
  them is API-Football, which needs a key and allows 100 requests a day.

**Recommendation:** ship with **ESPN** behind a `FixtureSource` interface. Optionally (Release 3) add
**API-Football as a second source that the user switches on by entering their own free key** in
Settings, to cover the extra Arab leagues.

**Keys and the repo (AGENTS.md → Secrets).** The repository and the APK are public (releases are
signed with the public debugCI key), so any key compiled into the app is effectively published. So:
- ESPN and TheSportsDB `123` need no secret.
- For API-Football or football-data.org, **don't bundle a key.** The user pastes their own free key into
  Settings → Matches. It is stored encrypted, like the login (same `EncryptedSharedPreferences` path as
  `CredentialStore`), and is never logged. `ApiClient`'s redaction is extended to the `x-apisports-key`
  header and the `X-Auth-Token` header.
- If the owner ever wants a shared key, it goes behind a small proxy they host, not into the APK.
  Developer test keys live only in `SECRETS.local.md` → `FOOTBALL_API_KEY`.

---

## 2. Matching a match to the user's own IPTV channels

Provider channel names are free text: `beIN SPORTS 1 HD`, `AR| BEIN 1 FHD`, `|AR| beIN Sports 1 ᴴᴰ`,
`SSC 1 HD`, `AD SPORTS PREMIUM 1`, `ALKASS ONE`, `MBC ACTION`. Since no free API gives Arab
broadcasters (§1), the app uses three layers, cheapest first.

### 2a. Rights table → broadcaster family (bundled, no network)
A small JSON file in `assets/` that maps **competition → broadcaster families for the MENA region**,
e.g. `eng.1 → [beIN]`, `uefa.champions → [beIN]`, `ksa.1 → [SSC, Thmanyah]`, `esp.1 → [beIN]`,
`afc.champions → [SSC, beIN]`. Rights change by season, so the file has a `season` field and the owner
can update it in a normal release (it is data, not code). Its accuracy is the owner's call; the app
says "suggested", never "official".

### 2b. Name-matching heuristic over the live channel list
`ContentRepository.getLiveStreams()` is already cached in memory and on disk, so this costs no extra
network. A pure function `ChannelMatcher` (JVM unit-tested, like `PosterGridAnchorTest`):
1. **Normalise:** lowercase; strip prefixes such as `AR|`, `|AR|`, `[AR]`, `AR:`, `ar-`; strip quality
   tags (`HD`, `FHD`, `UHD`, `4K`, `SD`, `HEVC`, `ᴴᴰ`, `ᶠᴴᴰ`, `+1`, `backup`); fold Unicode small-caps and
   superscripts; Arabic → Latin aliases (`بي ان`/`بين` → `bein`); collapse punctuation; spell numbers as
   digits (`one` → `1`, `premium 1` stays).
2. **Family and number:** recognise families (`bein sports`, `bein`, `ssc`, `ad sports`, `alkass`,
   `shahid`, `thmanyah`, `mbc action`, `sky sports`...) and the channel number (`bein 1`, `ssc 2`).
3. **Score** each live channel for a match: family in the rights table (+), category name containing
   `sport`/`رياض`/`bein` (+), an Arabic category (`AR`, `عربي`) when the app language is Arabic (+), a higher
   quality tag (small +, to break ties), a `backup`/`test`/`24/7` tag (−).
4. Return the **top 3–5 candidates**, not one guess. The exact channel number (beIN 1 vs beIN 3) is not
   in the API; only layer 2c or 2d can know it.

### 2b′. beIN's own MENA guide (built 2026-10-23)
The owner's provider has no EPG for any beIN channel, so the exact channel (beIN SPORTS 1 vs 3) comes from
beIN's public guide: one JSON file per channel per day at
`storagebeincom-b4dvftgkaebcayar.z01.azurefd.net/epg/<yyyy-MM-dd>/<code>.json` (the files behind
bein.com/tv-guide; no key). `BeinGuideSource` reads beIN SPORTS 1–9, EN 1–2 and 4K, keeps live broadcasts
whose title names both teams near kick-off, and `ChannelMatcher` puts the provider's copies of that channel first
(never TR/FR/AU/US beIN). Undocumented like ESPN: if beIN changes it, matching falls back to the guesses.

### 2c. EPG cross-check (Release 3, optional)
Xtream panels expose `get_short_epg&stream_id=…` (now/next per channel). For the 3–5 candidates only,
read now/next around kick-off and look for both team names in the programme title. A hit moves that
channel to the top and marks it as **confirmed by guide**. This is bounded (≤5 calls per opened
match, never a sweep), because the app doesn't fetch EPG today and many panels have poor EPG.

### 2d. User override, remembered
On the match screen the user can pick **Choose channel…**, which lists the candidates with a search
over all live channels. The choice is saved per account in a new `MatchChannelStore` (same shape as
`LibraryStore`: per-account prefs, Gson, corrupt-copy backup), keyed at two levels:
- **this match** (`eventId → streamId`), and
- **"always use for this competition / broadcaster"** (`competition → streamId`,
  `family+number → streamId`).
Saved choices beat every heuristic. After the user picks once, the next PL match goes straight to
their channel.

---

## 3. UI

### Placement
- New `ContentSection.MATCHES` (icon `Icons.Rounded.SportsSoccer`), **second in the side menu,
  right after Home** (it is the main reason people open the app). Add it to every exhaustive
  `when (section)`, and add a `showMatchesSection` visibility toggle in `AppSettingsStore` next to the
  existing ones (`SideNavBar.visibleSections`).
- Home gets a **"Live now & today"** shelf (wrapped in `FocusMemoryItem`, like the other shelves) that
  appears only when there are live or upcoming matches in the user's followed competitions.

### Matches screen
- **Top row:** day chips `Yesterday · Today · Tomorrow · +2…+6` (the `CategoryRow` chip pattern), plus a
  **Competitions** chip that opens a filter sheet (followed competitions, saved in settings; default
  set: Champions League, Premier League, La Liga).
- **List**, grouped by competition (header: competition crest + name), sorted by kick-off. In "Today",
  live matches are pinned on top.
- **Match row (focusable card, `premiumFocus`, own scale 1f, `Border.None`):**
  home crest + name · centre block · away name + crest; a broadcaster hint under it
  (`beIN SPORTS 1 · your channel` once matched).
  - Upcoming: kick-off **in the TV's time zone** (`java.time`, API times are UTC) with `HH:mm`, plus
    "in 25 min" when < 1 h.
  - Live: Ember `LIVE` pill + minute (`67'`) + score; light pulse only when `!LocalReducedEffects`.
  - Finished: `FT` + score, dimmed (`TextSecondary`).
- Crests via the existing Coil image loading, cached; letter-avatar fallback.
- **Arabic / RTL:** all labels in `strings.xml` + `values-ar` (`StringResourcesTest` enforces it).
  Team and competition names come from the provider, so they go through `bidi()`. ESPN returns English
  names even with `lang=ar` (tested), so Release 2 adds an `assets/team_names_ar.json` for the followed
  leagues' clubs. The score stays LTR (`2 - 1`, home on the visual start in both directions, so the row
  mirrors with the team order kept). Digits are Western (`ar-u-nu-latn`, existing rule).

### Focus / D-Pad (per AGENTS.md)
- The list is a `LazyColumn` with `focusProperties { canFocus = false }`. Rows are wrapped in
  `FocusMemoryItem`, so menu → content and closing the player return to the last focused match, never a
  requester on a fixed lazy item.
- Day chips → Down lands on the first *live* match, or the first match. Jumps toward the menu use
  `focusProperties { start = … }`, and key handlers use `directionalKeys()`.
- A row is one focus target (no inner buttons), so Left/Right never get trapped inside a row. The
  actions live in the detail sheet.
- Held D-Pad repeats keep the existing 110 ms rate limit.

### Pressing a match
| State | OK does | Long-press / Menu |
|---|---|---|
| **Live** | Plays the best channel straight away (same `onStreamSelected` → `PlayerViewModel.loadStream` path as Live TV): the remembered channel, else the guide-confirmed one, else the top suggestion. The channel banner shows the match name and "CH+/CH− for other channels". | Opens the **match sheet**: the candidates + "Choose channel…"; picking one plays it and offers "Remember for <competition>" |
| **Live**, no candidate found at all | Opens the match sheet on "Choose channel…" | — |
| **Upcoming** | Opens the match sheet: kick-off, candidates (you can preview a channel now), **Remind me** | — |
| **Finished** | Match sheet with the score (no play) | — |

Live playback detail: the dashboard's channel up/down uses `liveChannelList` (the current section's
streams). In Matches that list would be empty, so a playback started from a match passes **its
candidate channels** as the up/down list: CH+ moves to the next candidate (beIN 1 → beIN 2), which is
what a viewer hunting for the right feed wants.

**Reminders (Release 2), banner only (owner decision):** "Remind me" saves the match locally. While the
app is open, a banner appears 5 min before kick-off ("Real Madrid – Barcelona starts in 5 min · Watch"),
reusing the update banner's non-modal pattern. No system notification or alarm.

---

## 4. Data layer

New files, all under `com.ipxtream.tv.data` (so the existing R8 rule
`-keep class com.ipxtream.tv.data.** { *; }` already covers Gson; **no new keep rule needed** if this
holds):
- `data/model/match/` — `Match`, `Team`, `Competition`, `MatchState (UPCOMING|LIVE|FINISHED|POSTPONED)`,
  plus the raw ESPN DTOs (`EspnScoreboard`, `EspnEvent`...). The UI only sees `Match`.
- `data/api/EspnSoccerService.kt` — Retrofit, its own `OkHttpClient` with a different base URL from the
  Xtream portal, and a timeout. No credentials, so nothing to redact. Debug-only request-line logging,
  same as `ApiClient`.
- `data/repository/FixtureSource.kt` (interface) + `EspnFixtureSource`, later `ApiFootballFixtureSource`.
- `data/repository/MatchRepository.kt` — merges the followed competitions for a day and dedups.
- `data/local/MatchChannelStore.kt` — overrides (§2d), per account.
- `data/repository/ChannelMatcher.kt` — pure, unit-tested.
- `assets/match_rights.json`, `assets/team_names_ar.json`.

**Requests:** one per *followed competition per day*. Today with the 3 default competitions = 3 small requests
(~5–40 KB each). The week view loads days only when their chip is focused.

**Caching** (via `CacheManager`, streamed JSON + temp-file rename, which already exists):
| Data | Fresh for |
|---|---|
| Past days | never re-fetched (final) |
| Today, while any match is live | 60 s, and only while the Matches screen or Home shelf is visible (no polling in the background or during playback) |
| Today, nothing live | 10 min |
| Future days | 3 h |
| Crests | Coil disk cache |

Refreshing pauses while video plays (same rule as the resolution scanner). Back-off on HTTP 429/5xx
(2×, max 10 min).

**Offline:** show the cached day with a "Last updated 14:05" note. Live minutes freeze, with no fake
clock. A day never fetched shows an empty state with Retry. Channel matching works offline because the
live list is cached too.

**Time:** all API times are UTC ISO-8601 → `Instant` → `ZonedDateTime` in `ZoneId.systemDefault()`. Day
chips use the local date, so a 00:30 local match appears under the correct day. The fetch window is
±1 UTC day around the local day.

---

## 5. Release slicing

1. **R1 — Schedule + play (MVP).** ESPN source, Matches section (today/tomorrow/±days), followed
   competitions, live/upcoming/finished rows, crests, local times, Arabic strings, rights table +
   `ChannelMatcher`, match sheet with candidates, "Choose channel…", remember per match/competition,
   OK on a remembered live match plays it, CH+/CH− across candidates. Unit tests: matcher normalisation
   on ~50 real-world channel names, ESPN parsing from saved fixtures, time-zone day bucketing.
2. **R2 — Polish.** Home "Live now" shelf, reminders (in-app banner), Arabic team names, competition
   picker, visibility toggle.
3. **R3 — Accuracy.** EPG cross-check for candidates and rights-table refresh from a JSON in this repo's GitHub
   releases (same channel as app updates), so it can change without an app release.

Each slice goes through `testDebugUnitTest lintDebug assembleRelease` and is checked on the emulator's
preview build. R1 is also checked on the owner's TV (performance: the list must stay inside the Home
frame budget measured there).

---

## 6. Risks

| Risk | Mitigation |
|---|---|
| ESPN's undocumented API changes or blocks clients | `FixtureSource` interface; parse defensively (every field nullable); on failure show cache + "source unavailable"; an API-Football source with a user key can be added later |
| Wrong channel suggested (heuristics) | OK plays the top guess (owner decision), so CH+/CH− steps through the other candidates and long-press opens the picker; the choice is remembered |
| Rights table goes stale each season | Season field; owner-editable data; R3 remote refresh |
| Arab domestic leagues missing on ESPN | Documented gap; R3 user-key source |
| Legal: showing ESPN data / crests | Personal, non-commercial use of a public endpoint, but its terms don't grant redistribution. Keep the attribution "Data: ESPN", don't re-host crests, and drop the source if asked |
| Battery/CPU on weak TVs | No background polling, 60 s refresh only while visible, `LocalReducedEffects` turns off the live pulse |
| Gson + R8 empties saved data | All models under `com.ipxtream.tv.data`; a unit test round-trips `MatchChannelStore` JSON |
| Time-zone / DST bugs | `java.time` only; tests with Riyadh (UTC+3, no DST) and a DST zone |

---

## 7. Owner decisions (2026-10-23)

1. **Source:** "use the best free one" → **ESPN** (no key). The API-Football user-key source in R3 is
   dropped unless the owner asks for the missing Arab leagues later.
2. **Default competitions:** **Champions League, Premier League, La Liga** (`uefa.champions`, `eng.1`,
   `esp.1`). Others (Saudi Pro League etc.) stay addable in the R2 competition picker.
3. **Rights table:** yes. And **pressing a live match goes straight to the suggested channel**, even when
   it is only a heuristic guess (§3 table updated). CH+/CH− steps through the other candidates if the
   guess is wrong; long-press opens the sheet to pick and remember.
4. **Menu position:** second item, right after Home.
5. **Remembered channel:** plays straight away.
6. **Reminders:** in-app banner only.
