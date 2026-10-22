# الهوية البصرية · IPXtream brand identity

افتح `brand-guide.html` لرؤية الدليل الكامل (عربي أولًا مع الإنجليزية).
Open `brand-guide.html` for the full guide (Arabic first, with English).

**الفكرة · Concept:** حرف X عريض يحتضن في فراغه الأيسر مثلث تشغيل يشير إلى نقطة التقاطع. لون «جمرة» البرتقالي على «ليل» بنفسجي عميق، و«هالة» كريمية للتركيز بدل الأبيض.
A wide slab X holding a play triangle in its left counter. Ember orange on deep plum Night, with warm Halo cream for focus instead of white. Deliberately unrelated to the old cyan diamond icon and the neutral black/grey + blue/amber palette.

| | |
|---|---|
| Primary · جمرة Ember | `#FF6A2B` (text on it: `#0D0811`) |
| Secondary · أوركيد Orchid | `#A574FF` |
| Accent / focus · هالة Halo | `#FFE6D1` |
| Dark canvas · ليل Night 950 | `#0D0811` |
| Light canvas · ورق Paper 50 | `#FBF7F4` |
| Success · Warning · Error | `#34D69C` · `#FFC23D` · `#FF5470` (dark) / `#0E7A55` · `#8F5B00` · `#C21E3D` (light) |

Fonts: **Lexend** (Latin) + **Readex Pro** (Arabic, Lexend's Arabic companion). Both SIL OFL 1.1, on Google Fonts.

## Files

- `logo/svg`, `logo/png` — mark, wordmark, horizontal, stacked, Arabic, bilingual; each as dark-bg, `-light`, `-mono-white`, `-mono-black`. Text is outlined to paths.
- `icon/icon-master.svg` (+ 1024 / 512 PNG), `icon/adaptive/` (foreground, background, monochrome layers + mask preview), `icon/tv-banner/` (320×180 `android:banner`).
- `icon/android-res/` — mirrors `app/src/main/res/`: `mipmap-*/ic_launcher(.png|_round.png)`, `mipmap-anydpi-v26/*.xml`, `drawable/ic_launcher_{foreground,background,monochrome}.xml`, `drawable-xhdpi/banner.png`.
- `palette/palette.md` — every colour, theme role and WCAG contrast ratio, plus TV viewing notes.
- `themes/Color.kt` (Compose: `IpxPalette`, `IpxColors`, `IpxDarkColors`, `IpxLightColors`, `LocalIpxColors`), `themes/colors.xml`, `themes/tokens.json` (W3C design tokens).
- `_source/build-brand.js` — regenerates everything from one palette + geometry definition.

Nothing here is wired into the app yet. To apply it later: copy `icon/android-res/*` into `app/src/main/res/`, add `android:banner="@drawable/banner"` to the `<application>` in the manifest, and replace `ui/theme/Color.kt` usages with the new roles.

## Rebuild

```bash
npm i harfbuzzjs@0.4.7 @resvg/resvg-js@2.6.2
```

Put `Lexend.ttf` (`ofl/lexend/Lexend[wght].ttf`) and `ReadexPro.ttf` (`ofl/readexpro/ReadexPro[HEXP,wght].ttf`) from github.com/google/fonts in a folder, then:

```bash
node build-brand.js <fontsDir> "<path to الهوية البصرية>"
```
