# لوحة الألوان · Colour palette

Generated from `_source/build-brand.js`. Contrast ratios are WCAG 2.x, computed against the theme background (alpha colours composited first).

## جمرة · Ember — الأساسي · Primary

Brand colour: primary buttons, progress, the X. Dark text on top (Night 950), never white.

| Token | Hex | vs Night 950 | vs Paper 50 |
|---|---|---|---|
| `ember.50` | `#FFF1EA` | 17.95 | 1.04 |
| `ember.100` | `#FFDCCB` | 15.43 | 1.21 |
| `ember.200` | `#FFBB9B` | 12.11 | 1.54 |
| `ember.300` | `#FF9A6B` | 9.52 | 1.95 |
| `ember.400` | `#FF8048` | 7.96 | 2.34 |
| `ember.500` ★ | `#FF6A2B` | 6.93 | 2.68 |
| `ember.600` | `#E8521A` | 5.33 | 3.49 |
| `ember.700` | `#B83D12` | 3.50 | 5.31 |
| `ember.800` | `#86300F` | 2.31 | 8.07 |
| `ember.900` | `#4D1C0B` | 1.40 | 13.26 |

## أوركيد · Orchid — الثانوي · Secondary

Secondary actions, selected chips, series/collection tags.

| Token | Hex | vs Night 950 | vs Paper 50 |
|---|---|---|---|
| `orchid.50` | `#F6F0FF` | 17.76 | 1.05 |
| `orchid.100` | `#EADCFF` | 15.26 | 1.22 |
| `orchid.200` | `#D6BCFF` | 11.79 | 1.58 |
| `orchid.300` | `#C39BFF` | 8.92 | 2.09 |
| `orchid.400` | `#B488FF` | 7.46 | 2.49 |
| `orchid.500` ★ | `#A574FF` | 6.17 | 3.01 |
| `orchid.600` | `#8A55F0` | 4.38 | 4.25 |
| `orchid.700` | `#6E3BD0` | 3.02 | 6.16 |
| `orchid.800` | `#4F2A99` | 2.00 | 9.28 |
| `orchid.900` | `#2C1A52` | 1.30 | 14.32 |

## هالة · Halo — التمييز / التركيز · Accent / focus

Warm light used for the focus platter and the play triangle. Replaces pure white.

| Token | Hex | vs Night 950 | vs Paper 50 |
|---|---|---|---|
| `halo.100` | `#FFF4EA` | 18.28 | 1.02 |
| `halo.200` ★ | `#FFE6D1` | 16.50 | 1.13 |
| `halo.300` | `#FFD2AE` | 14.23 | 1.31 |

## ليل · Night — المحايدات (داكن) · Neutrals (dark)

Plum-tinted neutrals for the dark theme: canvas, surfaces, borders, text.

| Token | Hex | vs Night 950 | vs Paper 50 |
|---|---|---|---|
| `night.50` | `#F8F4F6` | 18.17 | 1.02 |
| `night.100` | `#EEE8EF` | 16.44 | 1.13 |
| `night.200` | `#D9D0DC` | 13.21 | 1.41 |
| `night.300` | `#B8ACBE` | 9.14 | 2.04 |
| `night.400` | `#8F8296` | 5.47 | 3.40 |
| `night.500` | `#6A5C74` | 3.20 | 5.80 |
| `night.600` | `#4B3D56` | 1.98 | 9.38 |
| `night.700` | `#362A40` | 1.47 | 12.62 |
| `night.800` | `#271C2F` | 1.22 | 15.24 |
| `night.850` | `#1D1424` | 1.11 | 16.74 |
| `night.900` | `#150E1B` | 1.05 | 17.75 |
| `night.950` ★ | `#0D0811` | 1.00 | 18.60 |

## ورق · Paper — المحايدات (فاتح) · Neutrals (light)

Warm paper neutrals for the light theme.

| Token | Hex | vs Night 950 | vs Paper 50 |
|---|---|---|---|
| `paper.0` | `#FFFFFF` | 19.81 | 1.07 |
| `paper.50` ★ | `#FBF7F4` | 18.60 | 1.00 |
| `paper.100` | `#F4EDE9` | 17.11 | 1.09 |
| `paper.200` | `#EAE1DD` | 15.39 | 1.21 |
| `paper.300` | `#DCD0CD` | 13.16 | 1.41 |

## يَشْم · Jade — نجاح · Success

400 on dark, 700 on light.

| Token | Hex | vs Night 950 | vs Paper 50 |
|---|---|---|---|
| `jade.400` ★ | `#34D69C` | 10.61 | 1.75 |
| `jade.700` | `#0E7A55` | 3.71 | 5.01 |

## زعفران · Saffron — تحذير · Warning

400 on dark, 700 on light. Ratings stars use it too.

| Token | Hex | vs Night 950 | vs Paper 50 |
|---|---|---|---|
| `saffron.400` ★ | `#FFC23D` | 12.30 | 1.51 |
| `saffron.700` | `#8F5B00` | 3.46 | 5.38 |

## ورد · Rose — خطأ / بث مباشر · Error / Live

400 on dark, 700 on light. Also the LIVE dot.

| Token | Hex | vs Night 950 | vs Paper 50 |
|---|---|---|---|
| `rose.400` ★ | `#FF5470` | 6.37 | 2.92 |
| `rose.700` | `#C21E3D` | 3.35 | 5.56 |

## الثيم الداكن · Dark theme (primary on TV)

| Role | Value | Use |
|---|---|---|
| `background` | `night.950` (#0D0811) | Screen canvas |
| `surface` | `night.900` (#150E1B) | Panels, side menu |
| `card` | `night.850` (#1D1424) | Resting cards and posters |
| `surfaceRaised` | `night.800` (#271C2F) | Dialogs, raised panels |
| `outline` | `night.700` (#362A40) | Dividers, resting outlines |
| `outlineStrong` | `night.600` (#4B3D56) | Input borders, strong dividers |
| `textPrimary` | `night.50` (#F8F4F6) | Titles and body text |
| `textSecondary` | `night.300` (#B8ACBE) | Metadata, descriptions |
| `textMuted` | `night.400` (#8F8296) | Timestamps, hints |
| `textDisabled` | `night.500` (#6A5C74) | Disabled labels |
| `primary` | `ember.500` (#FF6A2B) | Primary buttons, brand highlights |
| `onPrimary` | `night.950` (#0D0811) | Text/icons on primary |
| `primaryContainer` | `#4D1C0B` (#4D1C0B) | Tinted primary background |
| `onPrimaryContainer` | `ember.200` (#FFBB9B) | Text on primaryContainer |
| `primaryText` | `ember.400` (#FF8048) | Primary-coloured text and links on the canvas |
| `secondary` | `orchid.400` (#B488FF) | Secondary actions, selected chips |
| `onSecondary` | `night.950` (#0D0811) | Text on secondary |
| `secondaryContainer` | `orchid.900` (#2C1A52) | Tinted secondary background |
| `onSecondaryContainer` | `orchid.100` (#EADCFF) | Text on secondaryContainer |
| `focus` | `halo.200` (#FFE6D1) | Focused button / chip platter |
| `onFocus` | `night.950` (#0D0811) | Text on the focus platter |
| `focusRing` | `halo.200` (#FFE6D1) | Focus outline on posters |
| `success` | `jade.400` (#34D69C) | Success, "New" badges |
| `warning` | `saffron.400` (#FFC23D) | Warnings, rating stars |
| `error` | `rose.400` (#FF5470) | Errors, destructive actions |
| `live` | `rose.400` (#FF5470) | LIVE indicator |
| `onStatus` | `night.950` (#0D0811) | Text on filled status badges |
| `progress` | `ember.500` (#FF6A2B) | Watched progress, scrubber |
| `progressTrack` | `#FFE6D140` (#FFE6D140) | Track behind progress |
| `scrim` | `#0D0811CC` (#0D0811CC) | Overlay behind dialogs / over video |
| `glass` | `#FFE6D11F` (#FFE6D11F) | Translucent chip/button fill |
| `hairline` | `#FFE6D11A` (#FFE6D11A) | Hairline separators |

**Contrast checks**

| Foreground | Background | Ratio | Grade |
|---|---|---|---|
| textPrimary `#F8F4F6` | background `#0D0811` | 18.17:1 | AAA |
| textSecondary `#B8ACBE` | background `#0D0811` | 9.14:1 | AAA |
| textMuted `#8F8296` | background `#0D0811` | 5.47:1 | AA |
| textPrimary `#F8F4F6` | card `#1D1424` | 16.35:1 | AAA |
| textSecondary `#B8ACBE` | card `#1D1424` | 8.22:1 | AAA |
| onPrimary `#0D0811` | primary `#FF6A2B` | 6.93:1 | AA |
| primaryText `#FF8048` | background `#0D0811` | 7.96:1 | AAA |
| primary `#FF6A2B` | background `#0D0811` | 6.93:1 | AA |
| onSecondary `#0D0811` | secondary `#B488FF` | 7.46:1 | AAA |
| onFocus `#0D0811` | focus `#FFE6D1` | 16.50:1 | AAA |
| focus `#FFE6D1` | background `#0D0811` | 16.50:1 | AAA |
| onPrimaryContainer `#FFBB9B` | primaryContainer `#4D1C0B` | 8.63:1 | AAA |
| onSecondaryContainer `#EADCFF` | secondaryContainer `#2C1A52` | 11.75:1 | AAA |
| success `#34D69C` | background `#0D0811` | 10.61:1 | AAA |
| warning `#FFC23D` | background `#0D0811` | 12.30:1 | AAA |
| error `#FF5470` | background `#0D0811` | 6.37:1 | AA |
| onStatus `#0D0811` | error `#FF5470` | 6.37:1 | AA |

## الثيم الفاتح · Light theme

| Role | Value | Use |
|---|---|---|
| `background` | `paper.50` (#FBF7F4) | Screen canvas |
| `surface` | `paper.0` (#FFFFFF) | Panels, side menu |
| `card` | `paper.100` (#F4EDE9) | Resting cards and posters |
| `surfaceRaised` | `paper.200` (#EAE1DD) | Dialogs, raised panels |
| `outline` | `paper.300` (#DCD0CD) | Dividers, resting outlines |
| `outlineStrong` | `night.300` (#B8ACBE) | Input borders, strong dividers |
| `textPrimary` | `night.900` (#150E1B) | Titles and body text |
| `textSecondary` | `night.600` (#4B3D56) | Metadata, descriptions |
| `textMuted` | `night.500` (#6A5C74) | Timestamps, hints |
| `textDisabled` | `night.300` (#B8ACBE) | Disabled labels |
| `primary` | `ember.600` (#E8521A) | Primary buttons, brand highlights |
| `onPrimary` | `night.950` (#0D0811) | Text/icons on primary |
| `primaryContainer` | `ember.100` (#FFDCCB) | Tinted primary background |
| `onPrimaryContainer` | `ember.800` (#86300F) | Text on primaryContainer |
| `primaryText` | `ember.700` (#B83D12) | Primary-coloured text and links on the canvas |
| `secondary` | `orchid.700` (#6E3BD0) | Secondary actions, selected chips |
| `onSecondary` | `paper.0` (#FFFFFF) | Text on secondary |
| `secondaryContainer` | `orchid.100` (#EADCFF) | Tinted secondary background |
| `onSecondaryContainer` | `orchid.800` (#4F2A99) | Text on secondaryContainer |
| `focus` | `night.900` (#150E1B) | Focused button / chip platter |
| `onFocus` | `halo.100` (#FFF4EA) | Text on the focus platter |
| `focusRing` | `night.900` (#150E1B) | Focus outline on posters |
| `success` | `jade.700` (#0E7A55) | Success, "New" badges |
| `warning` | `saffron.700` (#8F5B00) | Warnings, rating stars |
| `error` | `rose.700` (#C21E3D) | Errors, destructive actions |
| `live` | `rose.700` (#C21E3D) | LIVE indicator |
| `onStatus` | `paper.0` (#FFFFFF) | Text on filled status badges |
| `progress` | `ember.600` (#E8521A) | Watched progress, scrubber |
| `progressTrack` | `#150E1B26` (#150E1B26) | Track behind progress |
| `scrim` | `#150E1B99` (#150E1B99) | Overlay behind dialogs / over video |
| `glass` | `#150E1B0F` (#150E1B0F) | Translucent chip/button fill |
| `hairline` | `#150E1B1A` (#150E1B1A) | Hairline separators |

**Contrast checks**

| Foreground | Background | Ratio | Grade |
|---|---|---|---|
| textPrimary `#150E1B` | background `#FBF7F4` | 17.75:1 | AAA |
| textSecondary `#4B3D56` | background `#FBF7F4` | 9.38:1 | AAA |
| textMuted `#6A5C74` | background `#FBF7F4` | 5.80:1 | AA |
| textPrimary `#150E1B` | card `#F4EDE9` | 16.33:1 | AAA |
| textSecondary `#4B3D56` | card `#F4EDE9` | 8.63:1 | AAA |
| onPrimary `#0D0811` | primary `#E8521A` | 5.33:1 | AA |
| primaryText `#B83D12` | background `#FBF7F4` | 5.31:1 | AA |
| primary `#E8521A` | background `#FBF7F4` | 3.49:1 | AA Large |
| onSecondary `#FFFFFF` | secondary `#6E3BD0` | 6.56:1 | AA |
| onFocus `#FFF4EA` | focus `#150E1B` | 17.45:1 | AAA |
| focus `#150E1B` | background `#FBF7F4` | 17.75:1 | AAA |
| onPrimaryContainer `#86300F` | primaryContainer `#FFDCCB` | 6.69:1 | AA |
| onSecondaryContainer `#4F2A99` | secondaryContainer `#EADCFF` | 7.62:1 | AAA |
| success `#0E7A55` | background `#FBF7F4` | 5.01:1 | AA |
| warning `#8F5B00` | background `#FBF7F4` | 5.38:1 | AA |
| error `#C21E3D` | background `#FBF7F4` | 5.56:1 | AA |
| onStatus `#FFFFFF` | error `#C21E3D` | 5.92:1 | AA |

## ملاحظات المشاهدة على التلفاز · TV viewing notes

- Viewers sit about 3 m away. Body text must reach 4.5:1 and anything smaller than 18 sp should reach 7:1 — that is why body text uses `night.50` / `night.300` on the dark canvas, never `night.400` or darker.
- The canvas is `night.950` (#0D0811), not #000000: pure black next to bright posters causes blooming/halo on local-dimming LCDs and black crush on cheap panels, and a faint plum tint keeps the brand present.
- Text is `night.50` (#F8F4F6), not #FFFFFF, to avoid glare and "vibrating" edges at distance. The brightest element on screen is the Halo focus platter, so focus is always the first thing the eye finds.
- Ember is a highly saturated orange. TVs in "Vivid"/"Store" mode push saturation further, so Ember is for buttons, progress and small highlights — never a full-screen fill or long body text.
- Text on Ember is always dark (`night.950`, 6.9:1). White on Ember is only 2.9:1 and fails.
- Ember (hue ≈ 18°) and Rose (hue ≈ 350°) are both warm. Never rely on colour alone for errors: pair Rose with an icon or the word, and keep the LIVE dot paired with the "LIVE"/"مباشر" label.
- Focus must never rely on colour alone: it is a Halo platter or ring **plus** a 1.06–1.1× scale.
- Keep all text and focusable items inside the overscan-safe area: 48 dp left/right, 27 dp top/bottom at 960×540 dp.
