# Google Play listing (Play version, `com.xtream.kyu`)

Draft for the Play Console, written 2026-10-24 (Phase 5 of `docs/play-store-plan.md`). App name **Xtream TV**
(owner decision D2, 2026-10-24); the TV banner and feature graphic use the "Xtream" wordmark.

Rules this text follows: no channel, film, sports league or broadcaster names; no promises of content; nothing that
suggests where to get a subscription.

---

## Store listing → Main store listing

**App name** (30 characters max): Xtream TV

**Short description** (80 max)

- English: `A fast, simple player for your own IPTV service, made for Android TV.` (69 characters; the first draft was 81, over the limit)
- Arabic: `مشغّل سريع وبسيط لخدمة IPTV التي لديك بالفعل، مصمّم لأجهزة Android TV.`

**Full description** (4000 max)

English:

```
Xtream TV is a video player for Android TV. Sign in with the Xtream Codes details from the IPTV service you already
subscribe to, and watch its live channels, films and series with a remote-friendly interface.

The app does not include, sell or recommend any channels, films or subscriptions. You need your own service and the
right to watch what it offers.

• Live TV, films and series in one place, with fast navigation built for the remote
• Continue Watching and favourites, kept on your TV
• Football fixtures and live scores, with the channel you choose for each match remembered
• Search across your service
• English and Arabic, with a full right-to-left layout
• Smooth mode for less powerful TVs
• Several accounts on one TV
• No ads, no tracking, no account with us

Try it first: "Try the demo" on the sign-in screen plays a few openly licensed films and test channels, no account
needed.

Privacy: everything you enter stays on your TV. Many IPTV services use unencrypted HTTP; the app tells you when that
is the case and uses HTTPS when your service offers it.
```

Arabic:

```
Xtream TV مشغّل فيديو لأجهزة Android TV. سجّل الدخول ببيانات Xtream Codes من خدمة IPTV المشترك فيها،
وشاهد قنواتها المباشرة وأفلامها ومسلسلاتها بواجهة مصمّمة لجهاز التحكم.

لا يتضمن التطبيق أي قنوات أو أفلام أو اشتراكات، ولا يبيعها ولا يرشّحها. تحتاج إلى خدمتك الخاصة وإلى حق مشاهدة ما تقدّمه.

• القنوات المباشرة والأفلام والمسلسلات في مكان واحد مع تنقّل سريع بجهاز التحكم
• متابعة المشاهدة والمفضلة، محفوظة على تلفازك
• مواعيد المباريات والنتائج المباشرة، مع حفظ القناة التي تختارها لكل مباراة
• البحث في خدمتك
• العربية والإنجليزية مع واجهة كاملة من اليمين إلى اليسار
• وضع سلس للأجهزة الأقل قوة
• عدة حسابات على تلفاز واحد
• بلا إعلانات ولا تتبّع ولا حساب لدينا

جرّبه أولًا: زر "جرّب العرض التوضيحي" في شاشة تسجيل الدخول يعرض أفلامًا مفتوحة الترخيص وقنوات تجريبية دون حساب.

الخصوصية: كل ما تدخله يبقى على تلفازك. كثير من خدمات IPTV تستخدم HTTP غير المشفّر؛ يوضّح التطبيق ذلك ويستخدم HTTPS عندما توفّره خدمتك.
```

**Graphics** (files in `الهوية البصرية/store/`):

| Asset | File | Size |
|---|---|---|
| App icon | `icon-512.png` | 512 × 512 |
| Feature graphic | `feature-graphic-1024x500.png` | 1024 × 500 |
| Android TV banner | `tv-banner-1280x720.png` | 1280 × 720 |
| Android TV screenshots | `screenshots/01-home.png`, `02-movies.png`, `03-details.png` | 1920 × 1080 |

Screenshots come from a local test server with made-up titles and generated artwork (no real channels, films, logos
or sports branding). They show the test account's name and a local address in the top-right corner; retake them
after the rename if that should go.

**Category:** Video Players & Editors · **Tags:** video player, Android TV
**Contact e-mail:** contact@dryaser.me · **Website:** (optional) the GitHub repository
**Privacy policy URL:** `https://kyujunior.github.io/Ipxtream-for-android-tv/privacy/` (live since 2026-10-24)

---

## App content

**Privacy policy:** the URL above.

**Ads:** No, the app contains no ads.

**App access:** "All or some functionality is restricted" → add instructions:

```
No account is needed for review. On the sign-in screen, move down to "Try the demo (no account needed)" and press OK.
The demo plays openly licensed films (Big Buck Bunny, Tears of Steel, Blender Foundation, CC BY 3.0) and public
test streams. A real IPTV service is the user's own and is entered on the same screen.
```

**Content rating (IARC questionnaire):** category "Entertainment / media player". The app plays whatever the user's
own service provides, which can include mature content, so answer the user-content questions accordingly; expect a
Mature / 18+ rating. No violence, gambling or purchases in the app itself.

**Target audience:** 18 and over. Not designed for children.

**News app:** No. **COVID-19 apps:** No. **Government app:** No. **Financial features:** None.

**Data safety** (re-check against the code before submitting):

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **No** data collected by or shared with the developer |
| Is all user data encrypted in transit? | **No** (IPTV services often use plain HTTP) |
| Can users request deletion? | Not applicable: the developer holds no user data; uninstalling or clearing app data deletes everything |
| Account creation | None with the developer; users enter their own IPTV service login, which stays on the device and goes only to that service |

**Advertising ID:** not used.

---

## Release

- Track: **Internal testing** first, then **Closed testing** (12+ testers for 14 days if the Console asks for it).
- Bundle: the `play-bundle` artifact from the "Play Store bundle" workflow (`docs/play-store-plan.md` §4).
- Countries: owner's choice. Release notes: "First release."
- Form factor: **Android TV** (Release → Advanced settings → Form factors → add Android TV, accept the TV
  guidelines). TV review checks the banner, D-pad navigation and that no touch screen is required.
