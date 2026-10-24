# Google Play build: plan for owner approval

Status: **Phases 1–3 built (2026-10-24). Owner answered D1–D7 (§9); open: the app name and the final package check
(before Phase 4).** Written 2026-10-24 after the local council review
(`.claude/council-cache/local-council-1792789843.md`). Owner goal: publish on Google Play with a separate Play
build that has no self-updater, no beIN mapping, its own signing key, targetSdk 36, an AAB and a real privacy policy.
The GitHub build and every TV that already runs it stay exactly as they are.

Policy statements below come from what the authors knew up to mid-2026, not from today's Play pages. Each one marked
**verify** must be checked in the Play Console or the policy centre before acting on it.

---

## 1. What exists today (checked in the code on 2026-10-24)

| Item | Where | Why it matters for Play |
|---|---|---|
| Self-updater: checks GitHub releases, downloads `app-release.apk`, installs it | `data/api/UpdateManager.kt`; called from `DashboardViewModel` (`checkForUpdates`, `installApk`), banner and dialog in `DashboardScreen`, card in `SettingsScreen` (About) | Play forbids apps updating themselves outside Play (Device and Network Abuse policy) |
| `REQUEST_INSTALL_PACKAGES` permission | `AndroidManifest.xml` | Restricted permission; self-update is not an allowed use |
| FileProvider (used only to hand the APK to the installer) | `AndroidManifest.xml`, `res/xml/file_paths.xml` | Not needed without the updater; its paths are wider than needed |
| beIN guide scraper (undocumented beIN Azure endpoint) | `data/match/BeinGuide.kt` (`BeinGuideSource`), used in `DashboardViewModel.buildMatchListings` | Points users to a specific paid sports channel; intellectual-property and trademark risk |
| Rights table: competition → broadcaster families (beIN, SSC, …) | `assets/match_rights.json`, `ChannelMatcher.kt` (`MatchRights`), `DashboardViewModel` | Same: steers users to rights-holders' channels inside an unlicensed list |
| "On beIN SPORTS 3 (beIN guide)" row hint | `res/values*/strings.xml` → `matches_channel_guide` | Brand name in the UI |
| Signing: every build uses `debugCI` (`app/debug.keystore`, public); a second config reads `ipxtream_keystore.jks` from env vars | `app/build.gradle.kts` | Play needs a private upload key. **Both keystores are tracked in a public repo** (`KyuJunior/Ipxtream-for-android-tv`), so neither can be the Play upload key |
| `compileSdk = 35`, `targetSdk = 35`, AGP 8.5.2 | `app/build.gradle.kts`, `gradle/libs.versions.toml` | Play's yearly rule very likely requires target 36 for new apps by now (**verify** in Console → Policy → Target API) |
| APK only (`assembleRelease`) | `.github/workflows/release.yml` | Play requires an AAB for new apps |
| Privacy text says the app sends nothing anywhere but the provider and GitHub | `strings.xml` → `legal_privacy_body` | Wrong: the app also calls ESPN (fixtures, crests) and beIN. Play also needs a hosted URL, not in-app text |
| Global cleartext HTTP | `res/xml/network_security_config.xml` | Allowed (providers are HTTP), but must be declared in Data safety ("not encrypted in transit") |
| Credentials inside stream URLs (`/live/<user>/<pass>/…`) are not masked in logs or the crash log | `ApiClient.redactCredentials` (masks only `password=`), `CrashLog` | Password can be shown in Settings → About and kept in saved downloads |
| `profileable shell="true"`, `largeHeap="true"` | `AndroidManifest.xml` | Fine for GitHub; remove `profileable` from the Play build |
| Home's live highlights pick "Alwan" channels | `DashboardViewModel` (~line 1018) | Owner-specific; meaningless for other users |
| Back key handled through `KEYCODE_BACK` in `dispatchKeyEvent` and Compose `Key.Back` | `DashboardActivity` (~line 242), `PlayerPanel`, `SideNavBar`, detail screens | Android 16 (target 36) turns on predictive back, which can stop `KEYCODE_BACK` reaching the app (**verify**, see §5) |

---

## 2. Owner decisions needed before any code

| # | Decision | Options | Recommendation |
|---|---|---|---|
| D1 | **Package name of the Play app** (permanent once published) | (a) same `com.ipxtream.tv`: GitHub installs can't update to it (different signer); users must uninstall first and lose their saved login. (b) a new one, e.g. `com.ipxtream.tv.play` or a new brand's name: both can sit side by side | **(b)**. No conflict on any TV, the GitHub build keeps its users, and testers can run both |
| D2 | **App name on Play** | Keep "IPXtream TV", or a neutral name | Consider a neutral name. "Xtream" is tied to a product known from piracy takedowns, which a reviewer or a trademark complaint can pick up. The name is not hard to change later, unlike the package |
| D3 | **What Matches does in the Play build** | (a) remove the section; (b) fixtures and scores only, user picks a channel by hand (remembered), no suggestions; (c) fixtures + suggestions by team names only (event channels), no broadcaster families, no beIN guide, no auto-play | **(b)**. It keeps the useful part (what's on, when, live score) and points users at nothing. (c) still ranks channels for a paid match and is the riskier middle ground |
| D4 | **Demo access for Play's reviewers** | Reviewers must be able to sign in and play something (Play Console → App content → App access). (a) a built-in "Demo" sign-in that plays a few legal free streams (public-domain films, free-to-air/FAST HLS channels whose terms allow it); (b) give reviewers a real provider login | **(a)**. A real provider login shows the reviewer an unlicensed catalogue. Needs a small list of legal stream URLs, chosen and checked by the owner |
| D5 | **Support e-mail and developer name** | Shown publicly on the listing and in the privacy policy | Owner to choose (a dedicated address is better than a personal one) |
| D6 | **Play Console account** | Personal or organisation | Owner task. A personal account created after Nov 2023 must run a **closed test with at least 12 testers for 14 days** before production (**verify**: the rule may have changed) |
| D7 | **Release rhythm** | GitHub keeps releasing on every push; Play gets builds only when the owner triggers one | Recommended. Play review takes hours to days, so every-push releases don't fit there |

---

## 3. Build design: two product flavors

One codebase, one `store` flavor dimension:

| | `github` flavor | `play` flavor |
|---|---|---|
| applicationId | `com.ipxtream.tv` (unchanged) | per D1 |
| Signing | `debugCI` (unchanged, owner decision 2026-10-22) | Play upload key (§4); Play App Signing re-signs for users |
| Output | APK `app-github-release.apk`, published as `app-release.apk` + `app-debug.apk` alias as today | AAB (`bundlePlayRelease`) |
| Self-updater, `REQUEST_INSTALL_PACKAGES`, FileProvider | yes | **no code, no permission** |
| beIN guide, `match_rights.json`, suggestions, auto-play | yes | per D3 |
| `profileable` | yes | no |
| Privacy/terms text | current (corrected, §6) | Play version (§6) |
| Demo sign-in | no | yes (D4) |

How the split is done (**built in Phase 1, 2026-10-24**), so that the Play build contains none of the removed code
(reviewers can decompile an AAB):

1. **Source sets plus two BuildConfig flags.** Flavor-only code lives in `app/src/github/` and `app/src/play/`; each
   flavor has a class with the same name and public shape, so `main` compiles against either:
   - `data/api/UpdateManager.kt`: the real updater in `src/github`; in `src/play` a stand-in whose methods return
     null/false and touch no network. `BuildConfig.SELF_UPDATE` (false in play) skips the 30 s startup check and makes
     the update spots say "updates come from Google Play" (Settings → About, My Library card, header status hidden);
     "check for updates" there opens the app's Play Store page (`DashboardViewModel.openStorePage`).
   - `data/match/MatchChannelFinder.kt`: in `src/github` it holds today's logic (beIN guide, rights table,
     `ChannelMatcher`, sports-category index), moved out of `DashboardViewModel` unchanged. `BeinGuide.kt`,
     `ChannelMatcher.kt` (with `MatchRights`) and `assets/match_rights.json` are github-only.
     In `src/play` it is provider-neutral (D3): it suggests the user's live channels whose names hold **both** team
     names (`TeamNames`, any provider's event channels such as "Al Hilal vs Al Nassr"), plus the user's remembered pick.
     No broadcaster families, no sports-category ranking, no region preference, no third-party guide.
     `BuildConfig.MATCH_AUTO_PLAY` (false in play): OK plays only a channel the user picked before; otherwise it opens
     the picker, where "Found in your channels" is on top. Long-press always opens the picker. CH+/CH− during a
     match steps through the match's channels (the remembered pick and the found ones), as in the GitHub build.
   - Later: the provider's own EPG programmes naming both teams can feed the same play finder.
2. **Manifest per flavor.** `REQUEST_INSTALL_PACKAGES`, the FileProvider (`res/xml/file_paths.xml`) and `profileable`
   are in `src/github/AndroidManifest.xml` only; the main manifest has none of them.
3. **Strings.** Shared update strings stay in `main`. `matches_channel_guide` (names beIN) is in
   `src/github/res/values*/`. `src/play/res/values*/` overrides a few labels ("Found in your channels", "About").
   `StringResourcesTest` checks English/Arabic keys and placeholders in main and in both flavors.
4. **Unit tests.** `src/testGithub/`: `MatchesTest`, `UpdateVersionTest`. `src/testPlay/PlayBuildTest`: flags off, no
   install permission or FileProvider in the main/play manifests, `BeinGuide`/`ChannelMatcher`/`MatchRights` absent
   from the classpath, no rights table in main assets, team-name matching.
5. **CI.** `release.yml` runs `testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug lintPlayDebug
   assembleGithubRelease` and publishes `app-github-release.apk` as `app-release.apk` and `app-debug.apk` (same names
   and signing key as before, so installed apps keep updating). The Play AAB pipeline is Phase 4.
6. **AGENTS.md "Current state"** has a "Build flavors" bullet.

Until Phase 4, a local `assemblePlayRelease` is signed with `debugCI` like the GitHub build; it is for testing only.

Shared files touched (post a lock in AGENTS.md first): `app/build.gradle.kts`, `AndroidManifest.xml`,
`DashboardViewModel.kt`, `DashboardScreen.kt`, `DashboardActivity.kt`, `SettingsScreen.kt`, `data/match/**`,
`release.yml`.

---

## 4. Signing, AAB and CI

**Upload key (owner does this, on their own PC; never in the repo):**

1. `keytool -genkeypair -v -keystore ipxtream-play-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. Keep two backups off this PC (e.g. a password manager + USB drive). Losing it is recoverable through Play support,
   but slow.
3. Add GitHub repository secrets: `PLAY_UPLOAD_KEYSTORE_B64` (base64 of the .jks), `PLAY_UPLOAD_STORE_PASSWORD`,
   `PLAY_UPLOAD_KEY_ALIAS`, `PLAY_UPLOAD_KEY_PASSWORD`. Names go in `SECRETS.local.md`, values never in tracked files.
4. In Play Console, enrol in **Play App Signing** with a Google-generated app signing key (the default).

**Gradle:** a `playUpload` signing config that reads those four values from env vars (decoded keystore written to
the runner's temp dir). The `play` flavor uses it for release; if the secrets are missing, `bundlePlayRelease` fails
with a clear message (Play builds must never fall back to `debugCI`). The `github` flavor keeps `debugCI` exactly as
today; the unused `release` config reading `ipxtream_keystore.jks` can be removed.

**CI:**

- `release.yml` (GitHub channel): `assembleRelease` → `assembleGithubRelease`; the APK path becomes
  `app/build/outputs/apk/github/release/app-github-release.apk`. The cert-pin step and the published asset names
  (`app-release.apk`, `app-debug.apk`) stay the same, so installed apps keep updating. Lint/tests run for both
  flavors (`testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug lintPlayDebug`); the lint baseline may need
  one regeneration for the new variant names only (no new errors hidden; review the diff).
- New `play.yml`, **manual trigger only** (`workflow_dispatch`): tests + lint, `bundlePlayRelease`, a check that the
  merged Play manifest has no `REQUEST_INSTALL_PACKAGES`, then upload the AAB as a workflow artifact. Phase 2 of this
  file: upload straight to the Play **internal** track with a Play service account key (secret
  `PLAY_SERVICE_ACCOUNT_JSON`) via Gradle Play Publisher or a well-known upload action. Never auto-promote to
  production.
- Version: both flavors read `version.properties`. Play only needs `versionCode` to go up between Play uploads,
  which the GitHub bumps guarantee.

---

## 5. Target API 36 (built in Phase 2, 2026-10-24)

1. AGP 8.5.2 → **8.13.2**, Gradle 8.9 → **8.13**; Kotlin 2.0.21 and the Compose BOM unchanged.
2. `compileSdk = 36`, `targetSdk = 36` for both flavors.
3. Behaviour changes, tested on the Android 16 (API 36) TV emulator:
   - **Back key.** Confirmed: with target 36 and no opt-out, the remote's Back no longer reached the app's
     `Key.Back` handlers (Back on the side menu closed the app instead of returning to the content). The manifest now
     sets `android:enableOnBackInvokedCallback="false"` on `<application>`, which restores the old delivery. Checked
     after the change: side menu → content, details → Home, playback → Home, Matches picker closes, Live TV split
     player closes, Settings stays in Settings. Follow-up (not urgent): move the Back handling in `DashboardActivity`,
     `PlayerPanel`, `SideNavBar` and the detail screens to `OnBackPressedDispatcher`/`BackHandler`, then drop the
     opt-out before Android removes it.
   - **Orientation / resizability:** the app is landscape-only on TV; nothing looked stretched on the 1080p emulator.
   - Edge-to-edge was already forced since target 35; no change seen.
4. Lint: no new errors on either flavor (the baseline only shows entries that no longer occur).

## 6. Privacy policy, Data safety, content rating, listing

**Privacy policy (hosted):** a page in the repo, e.g. `docs/privacy/index.html` (English + Arabic), served by GitHub
Pages (the repo is public). Its URL goes into the Play Console and the in-app About → Privacy screen. It must say,
accurately for the Play build:

- What is stored, only on the device: provider login (encrypted), favourites, watch history, settings, cached
  catalogues, crash log (on device only).
- What leaves the device and to whom: the provider the user enters (all catalogue and stream traffic, often
  unencrypted HTTP; the user's IP and login are visible to that provider); ESPN's public site API and image CDN
  (fixtures and crests: IP address only); image hosts named by the provider's catalogue (posters, logos); Google Play
  (installs, updates). No analytics, ads or tracking SDKs; nothing sold or shared.
- No developer account and no data held by the developer, so nothing to delete server-side; uninstalling or
  "Clear data" removes everything.
- Contact (D5), effective date.

The GitHub flavor's in-app text is corrected the same way, plus GitHub (update checks) and beIN's guide.

**Data safety form (Play build), expected answers** (re-check against the final code):

- Data collected: **none** sent to the developer. Data shared: none.
- Data encrypted in transit: **No** (provider traffic is often HTTP).
- Users can request deletion: not applicable (no account with the developer).
- Credentials entered by the user go only to their own provider: disclose as described in Google's guidance for
  "data sent to a server the user chooses" (**verify** the current wording).

**Content rating (IARC questionnaire):** the app plays whatever the user's provider sends, which can include adult
content → answer as a media player with unrestricted user-chosen content; expect an 18+/Mature rating. Target
audience: adults only, not designed for children. A parental PIN (later feature) helps, but isn't required for this.

**Store listing:**

- Text in English and Arabic: "A player for your own IPTV service. No channels, films or subscriptions are included or
  sold." No mention of beIN, football leagues, "22,000 channels", "4K films" or any provider.
- Screenshots and TV banner (1280×720), feature graphic (1024×500), icon (512×512): from the brand folder and from a
  **mock catalogue** (the local mock Xtream server used in earlier testing, with made-up names and plain artwork). No
  real channel logos, posters or sports branding.
- Category: Video Players & Editors. Form factor: **Android TV** opted in (Release → Advanced settings → Form factors),
  which adds Google's TV quality review: leanback launcher, banner, no touch required, full D-pad navigation
  (all present today).

**Demo sign-in (D4):** a "Try the demo" button on the login screen of the Play build only, loading a small built-in
catalogue of legal streams (owner-approved list); the reviewer instructions in App access say to press it.

---

## 7. Hardening that ships in both flavors (built in Phase 3, 2026-10-24)

1. `ApiClient.redactCredentials` masks `username=` / `password=` query parameters, the `<user>/<pass>` segments of
   `/live|movie|series|timeshift/` paths, and the type-less live form `http://host/<user>/<pass>/<id>.m3u8`. Used by
   network logging (debug only), `CrashLog` (on write and again on read, so older crash files are masked too),
   player error texts, download error texts and the login screen's unexpected-error text
   (`RedactCredentialsTest`).
2. Downloads: checked that `DownloadRepository` is in memory only; the credentialed URL never reaches disk (it lives
   in the running service and its start intent). Error texts are masked. No further change needed.
3. Login: after a successful sign-in of a **new** HTTP account whose provider sends `server_info.https_port`, the
   same login is tried over `https://<host>:<https_port>/` for up to 4 s; if it works, the account is saved with the
   HTTPS address. Existing accounts are never moved, because favourites, history, 4K results, match picks and caches
   are keyed on the server address. The login form shows a short note under the server address whenever it is not
   `https://`. Fixed third-party hosts (GitHub, ESPN, the beIN guide CDN) refuse cleartext in
   `network_security_config.xml`; provider hosts stay allowed.
   Checked on the emulator: a new account on a test server advertising a dead HTTPS port signed in over HTTP after
   the timeout. Not checked: a provider with working HTTPS (no such test server here).
4. Home's live row (`HomeLive.highlights`): the user's favourite live channels, then recently watched ones, else the
   provider's first 15 (was: channels named "Alwan").

---

## 8. Phases and order

| Phase | Work | Who | Done when |
|---|---|---|---|
| 0 | Decisions D1–D7; Play Console account and identity check; upload key + secrets; support e-mail; list of legal demo streams | Owner | Answers recorded in §9 of this file |
| 1 | **Done 2026-10-24.** Flavors and source-set split (§3); `release.yml` moved to the github flavor | Claude | GitHub APK behaves exactly as v2.0.x on the owner's TV (updater, Matches with beIN guide); a self-update from the previous release installs over it; Play debug build runs with no updater UI and per-D3 Matches; the Play flavor contains no `UpdateManager`/`BeinGuide` classes |
| 2 | **Done 2026-10-24** (owner TV not checked). Target/compile 36 + AGP upgrade (§5) | Claude | All Back paths and playback checked on the emulator and the owner's TV; tests + lint pass |
| 3 | **Done 2026-10-24.** Hardening (§7) | Claude | Redaction tests pass; crash log shows no password for a forced playback error |
| 4 | Play signing, `play.yml`, AAB artifact (§4) | Claude + owner (secrets) | A signed AAB from CI; `bundletool` shows the expected package, target 36, no install permission |
| 5 | Privacy page, corrected in-app texts, demo sign-in, listing text and assets (§6) | Claude drafts, owner approves | Privacy URL live; listing assets in `الهوية البصرية/store/` (or similar) |
| 6 | Internal test track → closed test (12+ testers, 14 days if required) → production review | Owner in Play Console, Claude fixes findings | App live on Play |

Phases 1–3 are independent of the owner's Play Console work, so they can start as soon as D1 and D3 are answered.

---

## 9. Owner answers

*(to be filled in)*

| # | Answer | Date |
|---|---|---|
| D1 | `com.xtream.kyu` (owner). Note: it contains "xtream", which D2 warns about, and it is permanent after the first upload; confirm before Phase 4 | 2026-10-24 |
| D2 | OK: use a neutral app name on Play; name still to choose | 2026-10-24 |
| D3 | Provider-neutral suggestions: search the user's own channel names (and the provider's EPG later) for both team names; no beIN guide, no rights table, no auto-play; the user confirms with OK; picks remembered (see §3) | 2026-10-24 |
| D4 | OK: built-in demo with legal streams; stream list to be chosen and checked | 2026-10-24 |
| D5 | contact@dryaser.me | 2026-10-24 |
| D6 | Owner has an active Play Console account; check in the Console whether the 12-tester / 14-day closed test applies to it | 2026-10-24 |
| D7 | OK: GitHub keeps releasing on every push; Play builds only when the owner starts one | 2026-10-24 |
