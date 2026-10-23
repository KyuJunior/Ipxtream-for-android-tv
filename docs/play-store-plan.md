# Google Play build: plan for owner approval

Status: **plan only, nothing built. Owner answered D1–D7 on 2026-10-24 (§9); open: the app name, the final package
check, and whether Play gets provider-neutral match suggestions (D3).** Written 2026-10-24 after the local council review
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

How the split is done, so that the Play build contains none of the removed code (reviewers can decompile an AAB):

1. **Source sets, not just `if (BuildConfig…)`.** Move flavor-only code into `app/src/github/` and give `app/src/play/`
   a stand-in with the same shape:
   - `UpdateManager.kt` → `src/github/java/.../data/api/`. `main` gets a small interface (e.g. `AppUpdater` with
     `check()`, `download()`, `install()`, `isSupported`) used by `DashboardViewModel`; `src/play` provides
     `NoUpdater` (`isSupported = false`). The Software Update card, the banner and the 30 s startup check hide when
     `isSupported` is false.
   - `BeinGuide.kt`, `ChannelMatcher.kt` (suggestions, sports-category index, rights table) → `src/github`. `main`
     keeps fixtures, `MatchRepository`, `MatchChannelStore` and the picker; a `MatchSuggestions` interface with a
     github implementation (today's behaviour) and a play implementation that returns only the user's remembered pick
     (D3 answered: (b)). In the Play build:
     - a match row shows the remembered channel, or "Choose a channel" when there is none;
     - OK on any match (live or not) opens the picker when nothing is remembered, and plays the remembered channel
       when there is one; long-press always opens the picker;
     - the picker lists all live channels with search, no "Suggested" group; "use for all <competition> matches" stays;
     - CH+/CH− during a match steps through normal live channels, not match suggestions;
     - the beIN-guide hint and string are absent.
   - `assets/match_rights.json` → `src/github/assets/`.
2. **Manifest per flavor.** `REQUEST_INSTALL_PACKAGES` and the FileProvider move to `src/github/AndroidManifest.xml`
   (merged into main); `profileable` too. `src/play/AndroidManifest.xml` adds nothing, and the merged Play manifest is
   checked in CI (§4) to contain no `REQUEST_INSTALL_PACKAGES`.
3. **Strings.** `matches_channel_guide` and the update strings stay in `main` only if both flavors use them; otherwise
   move them to `src/github/res/values*/` (English + Arabic, so `StringResourcesTest` keeps passing; extend the test
   to read flavor folders).
4. **Unit tests.** `MatchesTest` tests beIN logic → `src/testGithub/`. Add a Play test that fails if the play
   flavor's classpath contains `UpdateManager` or `BeinGuide` (simple reflection check), so a later edit can't bring
   them back by accident.
5. **AGENTS.md "Current state"** gets a bullet: flavor-only code lives in `src/github` / `src/play`; new features go in
   `main`; anything that downloads/installs code or names a rights-holder goes in `github` only.

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

## 5. Target API 36

1. Upgrade AGP to a version that supports `compileSdk 36` (8.9 or later; **verify** the exact minimum in the AGP
   release notes) and the Gradle wrapper to match; check Kotlin/Compose plugin compatibility.
2. `compileSdk = 36`, `targetSdk = 36`, both flavors (the GitHub build benefits too, and one target keeps testing simple).
3. Behaviour changes to test on the emulator and the owner's TV:
   - **Predictive back.** With target 36 the system may stop dispatching `KEYCODE_BACK` to `dispatchKeyEvent`/`onKeyDown`
     and stop calling `onBackPressed` (**verify** in the Android 16 behaviour-changes page). Back is handled in
     `DashboardActivity.dispatchKeyEvent` and in Compose `Key.Back` handlers (player, side menu, details). Either
     move those to `OnBackPressedDispatcher`/`BackHandler`, or set `android:enableOnBackInvokedCallback="false"` as a
     stop-gap if still allowed. Test every Back path: player → list, details → grid, Matches picker, settings panes,
     side menu, Live split view, update dialog (github).
   - **Orientation / resizability** declarations are ignored on large screens for target 36 (**verify** whether TVs are
     included). The app is landscape anyway; check nothing stretches.
   - Edge-to-edge is already forced since target 35; no change expected on TV.
4. Fix new lint errors, don't baseline them.

---

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

## 7. Hardening that ships in both flavors

These are fixes, not features, and they come before the first Play upload:

1. `ApiClient.redactCredentials` masks `/(live|movie|series|timeshift)/<user>/<pass>/` path segments and
   `username=` as well as `password=`; `CrashLog`, error messages and download records use it. Unit-tested in
   `app/src/test`.
2. Saved downloads stop storing the full credentialed URL; rebuild it from `CredentialStore` when needed.
3. Login: try `https` first when the provider's auth response offers `https_port`; show a one-line "this provider
   uses an unencrypted connection" note when it doesn't. Cleartext stays allowed for provider hosts; fixed hosts
   (GitHub, ESPN, beIN) get `cleartextTrafficPermitted="false"` entries.
4. Home's "Alwan" highlight becomes generic (e.g. favourites or most-watched live channels), in both flavors.

---

## 8. Phases and order

| Phase | Work | Who | Done when |
|---|---|---|---|
| 0 | Decisions D1–D7; Play Console account and identity check; upload key + secrets; support e-mail; list of legal demo streams | Owner | Answers recorded in §9 of this file |
| 1 | Flavors and source-set split (§3); `release.yml` moved to the github flavor | Claude | GitHub APK behaves exactly as v2.0.x on the owner's TV (updater, Matches with beIN guide); a self-update from the previous release installs over it; Play debug build runs with no updater UI and per-D3 Matches; the Play flavor contains no `UpdateManager`/`BeinGuide` classes |
| 2 | Target/compile 36 + AGP upgrade (§5) | Claude | All Back paths and playback checked on the emulator and the owner's TV; tests + lint pass |
| 3 | Hardening (§7) | Claude | Redaction tests pass; crash log shows no password for a forced playback error |
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
| D3 | (b) for now: fixtures and scores only; the user picks a channel by hand and the pick is remembered; no beIN guide, no rights table, no auto-play. Owner asked whether suggestions can work for any provider; open (provider-neutral suggestions from the user's own channel names / provider EPG, see chat 2026-10-24) | 2026-10-24 |
| D4 | OK: built-in demo with legal streams; stream list to be chosen and checked | 2026-10-24 |
| D5 | contact@dryaser.me | 2026-10-24 |
| D6 | Owner has an active Play Console account; check in the Console whether the 12-tester / 14-day closed test applies to it | 2026-10-24 |
| D7 | OK: GitHub keeps releasing on every push; Play builds only when the owner starts one | 2026-10-24 |
