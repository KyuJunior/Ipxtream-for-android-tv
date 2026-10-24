// IPXtream brand asset generator.
// Builds every SVG (text outlined to paths), the PNG exports, the launcher icon set,
// the TV banner, and the theme token files from one palette definition.
//
// Usage (from any folder that has the two npm packages installed):
//   npm i harfbuzzjs@0.4.7 @resvg/resvg-js@2.6.2
//   node build-brand.js <fontsDir> <outDir>
// When <outDir> is the repo's الهوية البصرية folder, icon/android-res/** is also copied into
// app/src/main/res — re-run this instead of editing those drawables by hand.
// fontsDir must contain Lexend.ttf and ReadexPro.ttf (variable fonts from Google Fonts, OFL).

const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

const FONTS = process.argv[2];
const OUT = process.argv[3];
if (!FONTS || !OUT) { console.error('usage: node build-brand.js <fontsDir> <outDir>'); process.exit(1); }
// Brand name drawn in the wordmarks (BRAND_WORD=Xtream for the Google Play build, docs/play-store-plan.md D2).
// The letter X is drawn in Ember.
const WORD = process.env.BRAND_WORD || 'IPXtream';
const WORD_AR = process.env.BRAND_WORD_AR || 'آي بي إكستريم';

// ─── Palette ──────────────────────────────────────────────────────────────────

const P = {
  ember:   { 50: '#FFF1EA', 100: '#FFDCCB', 200: '#FFBB9B', 300: '#FF9A6B', 400: '#FF8048', 500: '#FF6A2B', 600: '#E8521A', 700: '#B83D12', 800: '#86300F', 900: '#4D1C0B' },
  orchid:  { 50: '#F6F0FF', 100: '#EADCFF', 200: '#D6BCFF', 300: '#C39BFF', 400: '#B488FF', 500: '#A574FF', 600: '#8A55F0', 700: '#6E3BD0', 800: '#4F2A99', 900: '#2C1A52' },
  halo:    { 100: '#FFF4EA', 200: '#FFE6D1', 300: '#FFD2AE' },
  night:   { 50: '#F8F4F6', 100: '#EEE8EF', 200: '#D9D0DC', 300: '#B8ACBE', 400: '#8F8296', 500: '#6A5C74', 600: '#4B3D56', 700: '#362A40', 800: '#271C2F', 850: '#1D1424', 900: '#150E1B', 950: '#0D0811' },
  paper:   { 0: '#FFFFFF', 50: '#FBF7F4', 100: '#F4EDE9', 200: '#EAE1DD', 300: '#DCD0CD' },
  jade:    { 400: '#34D69C', 700: '#0E7A55' },
  saffron: { 400: '#FFC23D', 700: '#8F5B00' },
  rose:    { 400: '#FF5470', 700: '#C21E3D' },
};

const FAMILIES = [
  { key: 'ember',   en: 'Ember',   ar: 'جمرة',    role: 'Primary',   roleAr: 'الأساسي',  hero: 500, note: 'Brand colour: primary buttons, progress, the X. Dark text on top (Night 950), never white.' },
  { key: 'orchid',  en: 'Orchid',  ar: 'أوركيد',  role: 'Secondary', roleAr: 'الثانوي',  hero: 500, note: 'Secondary actions, selected chips, series/collection tags.' },
  { key: 'halo',    en: 'Halo',    ar: 'هالة',    role: 'Accent / focus', roleAr: 'التمييز / التركيز', hero: 200, note: 'Warm light used for the focus platter and the play triangle. Replaces pure white.' },
  { key: 'night',   en: 'Night',   ar: 'ليل',     role: 'Neutrals (dark)', roleAr: 'المحايدات (داكن)', hero: 950, note: 'Plum-tinted neutrals for the dark theme: canvas, surfaces, borders, text.' },
  { key: 'paper',   en: 'Paper',   ar: 'ورق',     role: 'Neutrals (light)', roleAr: 'المحايدات (فاتح)', hero: 50, note: 'Warm paper neutrals for the light theme.' },
  { key: 'jade',    en: 'Jade',    ar: 'يَشْم',   role: 'Success',   roleAr: 'نجاح',     hero: 400, note: '400 on dark, 700 on light.' },
  { key: 'saffron', en: 'Saffron', ar: 'زعفران',  role: 'Warning',   roleAr: 'تحذير',    hero: 400, note: '400 on dark, 700 on light. Ratings stars use it too.' },
  { key: 'rose',    en: 'Rose',    ar: 'ورد',     role: 'Error / Live', roleAr: 'خطأ / بث مباشر', hero: 400, note: '400 on dark, 700 on light. Also the LIVE dot.' },
];

// Semantic theme roles. Values are palette refs ("night.950") or hex with alpha ("#RRGGBBAA").
const THEMES = {
  dark: {
    background: 'night.950', surface: 'night.900', card: 'night.850', surfaceRaised: 'night.800',
    outline: 'night.700', outlineStrong: 'night.600',
    textPrimary: 'night.50', textSecondary: 'night.300', textMuted: 'night.400', textDisabled: 'night.500',
    primary: 'ember.500', onPrimary: 'night.950', primaryContainer: '#4D1C0B', onPrimaryContainer: 'ember.200', primaryText: 'ember.400',
    secondary: 'orchid.400', onSecondary: 'night.950', secondaryContainer: 'orchid.900', onSecondaryContainer: 'orchid.100',
    focus: 'halo.200', onFocus: 'night.950', focusRing: 'halo.200',
    success: 'jade.400', warning: 'saffron.400', error: 'rose.400', live: 'rose.400', onStatus: 'night.950',
    progress: 'ember.500', progressTrack: '#FFE6D140', scrim: '#0D0811CC', glass: '#FFE6D11F', hairline: '#FFE6D11A',
  },
  light: {
    background: 'paper.50', surface: 'paper.0', card: 'paper.100', surfaceRaised: 'paper.200',
    outline: 'paper.300', outlineStrong: 'night.300',
    textPrimary: 'night.900', textSecondary: 'night.600', textMuted: 'night.500', textDisabled: 'night.300',
    primary: 'ember.600', onPrimary: 'night.950', primaryContainer: 'ember.100', onPrimaryContainer: 'ember.800', primaryText: 'ember.700',
    secondary: 'orchid.700', onSecondary: 'paper.0', secondaryContainer: 'orchid.100', onSecondaryContainer: 'orchid.800',
    focus: 'night.900', onFocus: 'halo.100', focusRing: 'night.900',
    success: 'jade.700', warning: 'saffron.700', error: 'rose.700', live: 'rose.700', onStatus: 'paper.0',
    progress: 'ember.600', progressTrack: '#150E1B26', scrim: '#150E1B99', glass: '#150E1B0F', hairline: '#150E1B1A',
  },
};

const ROLE_DOCS = {
  background: 'Screen canvas', surface: 'Panels, side menu', card: 'Resting cards and posters', surfaceRaised: 'Dialogs, raised panels',
  outline: 'Dividers, resting outlines', outlineStrong: 'Input borders, strong dividers',
  textPrimary: 'Titles and body text', textSecondary: 'Metadata, descriptions', textMuted: 'Timestamps, hints', textDisabled: 'Disabled labels',
  primary: 'Primary buttons, brand highlights', onPrimary: 'Text/icons on primary', primaryContainer: 'Tinted primary background', onPrimaryContainer: 'Text on primaryContainer', primaryText: 'Primary-coloured text and links on the canvas',
  secondary: 'Secondary actions, selected chips', onSecondary: 'Text on secondary', secondaryContainer: 'Tinted secondary background', onSecondaryContainer: 'Text on secondaryContainer',
  focus: 'Focused button / chip platter', onFocus: 'Text on the focus platter', focusRing: 'Focus outline on posters',
  success: 'Success, "New" badges', warning: 'Warnings, rating stars', error: 'Errors, destructive actions', live: 'LIVE indicator', onStatus: 'Text on filled status badges',
  progress: 'Watched progress, scrubber', progressTrack: 'Track behind progress', scrim: 'Overlay behind dialogs / over video', glass: 'Translucent chip/button fill', hairline: 'Hairline separators',
};

function resolve(ref) {
  if (ref.startsWith('#')) return ref.toUpperCase();
  const [fam, step] = ref.split('.');
  return P[fam][step];
}

// ─── Contrast (WCAG 2.x) ──────────────────────────────────────────────────────

function rgb(hex) { const h = hex.replace('#', ''); return [0, 2, 4].map(i => parseInt(h.substr(i, 2), 16)); }
function alphaOf(hex) { const h = hex.replace('#', ''); return h.length === 8 ? parseInt(h.substr(6, 2), 16) / 255 : 1; }
function over(fg, bg) { const a = alphaOf(fg), f = rgb(fg), b = rgb(bg); return '#' + f.map((c, i) => Math.round(c * a + b[i] * (1 - a)).toString(16).padStart(2, '0')).join(''); }
function lum(hex) { return rgb(hex).map(c => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }).reduce((s, c, i) => s + c * [0.2126, 0.7152, 0.0722][i], 0); }
function contrast(a, b) { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); }
function grade(r) { return r >= 7 ? 'AAA' : r >= 4.5 ? 'AA' : r >= 3 ? 'AA Large' : 'Fail'; }

const CONTRAST_PAIRS = {
  dark: [
    ['textPrimary', 'background'], ['textSecondary', 'background'], ['textMuted', 'background'], ['textPrimary', 'card'], ['textSecondary', 'card'],
    ['onPrimary', 'primary'], ['primaryText', 'background'], ['primary', 'background'], ['onSecondary', 'secondary'], ['onFocus', 'focus'], ['focus', 'background'],
    ['onPrimaryContainer', 'primaryContainer'], ['onSecondaryContainer', 'secondaryContainer'],
    ['success', 'background'], ['warning', 'background'], ['error', 'background'], ['onStatus', 'error'],
  ],
  light: [
    ['textPrimary', 'background'], ['textSecondary', 'background'], ['textMuted', 'background'], ['textPrimary', 'card'], ['textSecondary', 'card'],
    ['onPrimary', 'primary'], ['primaryText', 'background'], ['primary', 'background'], ['onSecondary', 'secondary'], ['onFocus', 'focus'], ['focus', 'background'],
    ['onPrimaryContainer', 'primaryContainer'], ['onSecondaryContainer', 'secondaryContainer'],
    ['success', 'background'], ['warning', 'background'], ['error', 'background'], ['onStatus', 'error'],
  ],
};

function contrastRows(theme) {
  const t = THEMES[theme];
  return CONTRAST_PAIRS[theme].map(([fg, bg]) => {
    const bgHex = over(resolve(t[bg]), resolve(t.background));
    const fgHex = over(resolve(t[fg]), bgHex);
    const r = contrast(fgHex, bgHex);
    return { fg, bg, fgHex: resolve(t[fg]), bgHex: resolve(t[bg]), ratio: r, grade: grade(r) };
  });
}

// ─── Mark geometry ────────────────────────────────────────────────────────────
// A wide slab X (arms at ±36.5°) whose left counter holds a play triangle pointing at the
// crossing. Unit space: X spans x ∈ [-50, 50], y ∈ [-35, 35]; y grows downwards.

const A = 50, B = 37, M = B / A;          // half width, half height, arm slope
const BAR = 16.5;                          // arm thickness (perpendicular)
const K = Math.sqrt(1 + M * M);
const HV = (BAR / 2) * K;                  // vertical half-thickness of an arm
const PX = (B - HV) / M;                   // where an arm edge meets the top/bottom edge
const QX = HV / M;                         // left/right counter tip

const xPoly = [
  [-A, -B], [-PX, -B], [0, -HV], [PX, -B], [A, -B], [A, -B + HV], [QX, 0], [A, B - HV],
  [A, B], [PX, B], [0, HV], [-PX, B], [-A, B], [-A, B - HV], [-QX, 0], [-A, -B + HV],
];

const GAP = 3.4;     // gap between triangle and arms
const TR = 2.4;      // triangle corner radius
const XR = 1.1;      // X corner softening radius
function triPoly() {
  const d = GAP + TR;                      // offset to the stroke centre line
  const c = HV + d * K;                    // offset diagonals: y = ±(M x + c)
  const left = -A + TR;                    // triangle flush with the arms' ends
  const tipX = -c / M;
  return [[left, M * left + c], [tipX, 0], [left, -(M * left + c)]];
}
// Shrinks the X by the stroke radius so fill + round-joined stroke lands on the exact outline.
function insetX() {
  const sx = (A - XR) / A, sy = (B - XR) / B;
  return xPoly.map(([x, y]) => [x * sx, y * sy]);
}

function polyD(pts, tx, ty, s) {
  return pts.map(([x, y], i) => `${i ? 'L' : 'M'}${(tx + x * s).toFixed(2)} ${(ty + y * s).toFixed(2)}`).join(' ') + ' Z';
}

// Returns SVG elements for the mark centred at (cx, cy), `w` = width of the X.
function markSvg(cx, cy, w, xColor, triColor) {
  const s = w / (2 * A);
  return `<path d="${polyD(insetX(), cx, cy, s)}" fill="${xColor}" stroke="${xColor}" stroke-width="${(2 * XR * s).toFixed(2)}" stroke-linejoin="round"/>` +
    `<path d="${polyD(triPoly(), cx, cy, s)}" fill="${triColor}" stroke="${triColor}" stroke-width="${(2 * TR * s).toFixed(2)}" stroke-linejoin="round"/>`;
}
const MARK_H_RATIO = B / A;   // mark height / width

// ─── Text to outlines (HarfBuzz) ──────────────────────────────────────────────

let hb;
const fonts = {};
function loadFont(name, file) {
  const blob = hb.createBlob(fs.readFileSync(path.join(FONTS, file)));
  const face = hb.createFace(blob, 0);
  fonts[name] = { face, upem: face.upem };
}
function shapeText(fontName, text, wght, opts = {}) {
  const f = fonts[fontName];
  const font = hb.createFont(f.face);
  font.setVariations({ wght });
  const buf = hb.createBuffer();
  buf.addText(text);
  buf.guessSegmentProperties();
  if (opts.direction) buf.setDirection(opts.direction);
  hb.shape(font, buf);
  const glyphs = buf.json();
  let x = 0;
  const out = [];
  for (const g of glyphs) {
    out.push({ d: font.glyphToPath(g.g), x: x + g.dx, y: g.dy, cl: g.cl });
    x += g.ax + (opts.tracking || 0) * f.upem;
  }
  buf.destroy(); font.destroy();
  return { glyphs: out, advance: x - (opts.tracking || 0) * f.upem, upem: f.upem };
}
// Renders shaped text with its baseline at (x, y) and font size `size`; colorFor(cluster) picks the fill.
function textSvg(shaped, x, y, size, colorFor) {
  const k = size / shaped.upem;
  return shaped.glyphs.filter(g => g.d).map(g =>
    `<path transform="translate(${(x + g.x * k).toFixed(2)} ${(y - g.y * k).toFixed(2)}) scale(${k.toFixed(5)} ${(-k).toFixed(5)})" d="${g.d}" fill="${colorFor(g.cl)}"/>`
  ).join('');
}

// Cap height / x-height in font units (from the glyph extents of H and x).
function capHeight(fontName, wght, ch = 'H') {
  const f = fonts[fontName];
  const font = hb.createFont(f.face); font.setVariations({ wght });
  const buf = hb.createBuffer(); buf.addText(ch); buf.guessSegmentProperties(); hb.shape(font, buf);
  const gid = buf.json()[0].g;
  const d = font.glyphToPath(gid);
  const ys = [...d.matchAll(/-?\d+(\.\d+)?/g)].map(m => +m[0]).filter((_, i) => i % 2 === 1);
  buf.destroy(); font.destroy();
  return Math.max(...ys);
}

// ─── Output helpers ───────────────────────────────────────────────────────────

const written = [];
function write(rel, content) {
  const p = path.join(OUT, rel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, content);
  written.push(rel);
}
function svgDoc(w, h, body, { bg = null, title = 'IPXtream' } = {}) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${title}">` +
    `<title>${title}</title>` + (bg ? `<rect width="${w}" height="${h}" fill="${bg}"/>` : '') + body + `</svg>\n`;
}
function png(svgRel, pngRel, width, opts = {}) {
  const svg = fs.readFileSync(path.join(OUT, svgRel), 'utf8');
  const r = new Resvg(svg, { fitTo: { mode: 'width', value: width }, background: opts.background });
  const data = r.render().asPng();
  const p = path.join(OUT, pngRel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, data);
  written.push(pngRel);
}

// ─── Colour variants for logos ────────────────────────────────────────────────

const VARIANTS = {
  dark:  { x: P.ember[500], tri: P.halo[200], text: P.night[50], textX: P.ember[500], sub: P.night[300], bg: P.night[950] },   // for dark backgrounds
  light: { x: P.ember[500], tri: P.night[900], text: P.night[900], textX: P.ember[600], sub: P.night[600], bg: P.paper[50] },  // for light backgrounds
  white: { x: '#FFFFFF', tri: '#FFFFFF', text: '#FFFFFF', textX: '#FFFFFF', sub: '#FFFFFF', bg: P.night[950] },
  black: { x: '#000000', tri: '#000000', text: '#000000', textX: '#000000', sub: '#000000', bg: P.paper[0] },
};

// ─── Build ────────────────────────────────────────────────────────────────────

async function main() {
  hb = await require('harfbuzzjs');
  loadFont('lexend', 'Lexend.ttf');
  loadFont('readex', 'ReadexPro.ttf');

  const LATIN_W = 640, ARABIC_W = 700;
  const wm = shapeText('lexend', WORD, LATIN_W, { tracking: -0.02 });
  const cap = capHeight('lexend', LATIN_W);
  const ar = shapeText('readex', WORD_AR, ARABIC_W, { direction: 'rtl' });
  const arCap = capHeight('readex', ARABIC_W, 'ك');       // tallest joining letter, used to size the Arabic line
  const xCluster = WORD.indexOf('X');                      // the Ember "X" (cluster = letter index for Latin)

  // Wordmark sized so cap height = 100 px.
  function wordmark(x, baseline, capPx, v) {
    const size = capPx / cap * wm.upem;
    return { svg: textSvg(wm, x, baseline, size, cl => cl === xCluster ? v.textX : v.text), width: wm.advance * size / wm.upem };
  }
  function arabicWord(xRight, baseline, capPx, color) {
    const size = capPx / arCap * ar.upem;
    const w = ar.advance * size / ar.upem;
    return { svg: textSvg(ar, xRight - w, baseline, size, () => color), width: w };
  }

  const PAD = 60;   // export padding (≥ the clear-space rule of 0.5 × mark height at this size)
  for (const [vk, v] of Object.entries(VARIANTS)) {
    const sfx = vk === 'dark' ? '' : `-${vk === 'light' ? 'light' : 'mono-' + vk}`;
    const title = WORD;

    // Mark only.
    {
      const w = 400, h = w * MARK_H_RATIO;
      write(`logo/svg/mark${sfx}.svg`, svgDoc(w + 2 * PAD, h + 2 * PAD, markSvg(PAD + w / 2, PAD + h / 2, w, v.x, v.tri), { title }));
    }
    // Wordmark only.
    {
      const capPx = 100, wmk = wordmark(PAD, PAD + capPx, capPx, v);
      write(`logo/svg/wordmark${sfx}.svg`, svgDoc(Math.ceil(wmk.width + 2 * PAD), capPx + 2 * PAD + 26, wmk.svg, { title }));
    }
    // Horizontal lockup: mark height = 1.25 × cap height, gap = 0.55 × mark width.
    {
      const capPx = 100, markH = capPx * 1.3, markW = markH / MARK_H_RATIO, gap = markW * 0.34;
      const wmk = wordmark(PAD + markW + gap, 0, capPx, v);
      const h = Math.max(markH, capPx) + 2 * PAD;
      const baseline = PAD + markH / 2 + capPx / 2;
      const body = markSvg(PAD + markW / 2, PAD + markH / 2, markW, v.x, v.tri) + wordmark(PAD + markW + gap, baseline, capPx, v).svg;
      write(`logo/svg/logo-horizontal${sfx}.svg`, svgDoc(Math.ceil(PAD + markW + gap + wmk.width + PAD), Math.ceil(h), body, { title }));
    }
    // Stacked lockup: mark above, wordmark centred under it.
    {
      const capPx = 100, markW = 300, markH = markW * MARK_H_RATIO, gap = 70;
      const wmk = wordmark(0, 0, capPx, v);
      const W = Math.ceil(Math.max(markW, wmk.width) + 2 * PAD);
      const body = markSvg(W / 2, PAD + markH / 2, markW, v.x, v.tri) + wordmark((W - wmk.width) / 2, PAD + markH + gap + capPx, capPx, v).svg;
      write(`logo/svg/logo-stacked${sfx}.svg`, svgDoc(W, Math.ceil(PAD + markH + gap + capPx + 26 + PAD), body, { title }));
    }
    // Arabic horizontal lockup (RTL: mark on the right).
    {
      const capPx = 92, markH = 130, markW = markH / MARK_H_RATIO, gap = markW * 0.3;
      const tmp = arabicWord(0, 0, capPx, v.text);
      const W = Math.ceil(PAD + tmp.width + gap + markW + PAD);
      const H = Math.ceil(markH + 2 * PAD + 30);
      const baseline = PAD + markH / 2 + capPx / 2;
      const body = markSvg(W - PAD - markW / 2, PAD + markH / 2, markW, v.x, v.tri) + arabicWord(W - PAD - markW - gap, baseline, capPx, v.text).svg;
      write(`logo/svg/logo-arabic-horizontal${sfx}.svg`, svgDoc(W, H, body, { title: 'آي بي إكستريم' }));
    }
    // Bilingual stacked lockup: mark, Arabic name, Latin wordmark.
    {
      const markW = 300, markH = markW * MARK_H_RATIO, arCapPx = 84, latCap = 54;
      const a = arabicWord(0, 0, arCapPx, v.text), l = wordmark(0, 0, latCap, v);
      const W = Math.ceil(Math.max(markW, a.width, l.width) + 2 * PAD);
      const yAr = PAD + markH + 70 + arCapPx;
      const yLat = yAr + 96 + latCap;
      const body = markSvg(W / 2, PAD + markH / 2, markW, v.x, v.tri) +
        arabicWord((W + a.width) / 2, yAr, arCapPx, v.text).svg + wordmark((W - l.width) / 2, yLat, latCap, v).svg;
      write(`logo/svg/logo-bilingual-stacked${sfx}.svg`, svgDoc(W, Math.ceil(yLat + 20 + PAD), body, { title: 'IPXtream · آي بي إكستريم' }));
    }
  }

  // PNG exports of every logo SVG (2 sizes), transparent background.
  for (const f of fs.readdirSync(path.join(OUT, 'logo/svg'))) {
    const base = f.replace(/\.svg$/, '');
    png(`logo/svg/${f}`, `logo/png/${base}.png`, 1200);
    png(`logo/svg/${f}`, `logo/png/${base}@small.png`, 400);
  }

  // ─── App icon ───────────────────────────────────────────────────────────────
  const bgGrad = (id, w, h) =>
    `<defs><linearGradient id="${id}" x1="0" y1="0" x2="${w}" y2="${h}" gradientUnits="userSpaceOnUse">` +
    `<stop offset="0" stop-color="#2E1838"/><stop offset="1" stop-color="${P.night[950]}"/></linearGradient>` +
    `<radialGradient id="${id}g" cx="${w * 0.36}" cy="${h * 0.5}" r="${w * 0.5}" gradientUnits="userSpaceOnUse">` +
    `<stop offset="0" stop-color="${P.ember[500]}" stop-opacity="0.22"/><stop offset="1" stop-color="${P.ember[500]}" stop-opacity="0"/></radialGradient></defs>`;
  const ICON_MARK = 0.56;  // mark width / icon width for full-bleed icons

  // Master (full-bleed square, 1024): stores, launchers that mask it themselves.
  write('icon/icon-master.svg', svgDoc(1024, 1024,
    bgGrad('bg', 1024, 1024) + `<rect width="1024" height="1024" fill="url(#bg)"/><rect width="1024" height="1024" fill="url(#bgg)"/>` +
    markSvg(512, 512, 1024 * ICON_MARK, P.ember[500], P.halo[200]), { title: 'IPXtream app icon' }));
  // Legacy shapes (pre-Android 8 launchers): rounded square and circle, with the standard 1/48 padding.
  const legacy = (shape) => {
    const S = 192, pad = 4, inner = S - 2 * pad;
    const clip = shape === 'round'
      ? `<clipPath id="c"><circle cx="${S / 2}" cy="${S / 2}" r="${inner / 2}"/></clipPath>`
      : `<clipPath id="c"><rect x="${pad}" y="${pad}" width="${inner}" height="${inner}" rx="${inner * 0.18}"/></clipPath>`;
    return svgDoc(S, S, `<defs>${clip}</defs>` + bgGrad('bg', S, S) +
      `<g clip-path="url(#c)"><rect width="${S}" height="${S}" fill="url(#bg)"/><rect width="${S}" height="${S}" fill="url(#bgg)"/></g>` +
      markSvg(S / 2, S / 2, inner * (shape === 'round' ? 0.6 : 0.62), P.ember[500], P.halo[200]), { title: 'IPXtream app icon' });
  };
  write('icon/icon-legacy-square.svg', legacy('square'));
  write('icon/icon-legacy-round.svg', legacy('round'));

  // Adaptive layers (108 × 108 dp; the mark stays inside the 66 dp safe circle).
  // At 52 dp wide the X's outer corners, including the rounded stroke, reach ≈ 32.1 dp from the
  // centre: inside the 33 dp safe radius, so no launcher mask clips the tips (54 dp reached ≈ 33.4).
  const AD = 108, adMarkW = 52;
  write('icon/adaptive/ic_launcher_background.svg', svgDoc(AD, AD,
    bgGrad('bg', AD, AD) + `<rect width="${AD}" height="${AD}" fill="url(#bg)"/><rect width="${AD}" height="${AD}" fill="url(#bgg)"/>`, { title: 'Adaptive icon background' }));
  write('icon/adaptive/ic_launcher_foreground.svg', svgDoc(AD, AD, markSvg(AD / 2, AD / 2, adMarkW, P.ember[500], P.halo[200]), { title: 'Adaptive icon foreground' }));
  write('icon/adaptive/ic_launcher_monochrome.svg', svgDoc(AD, AD, markSvg(AD / 2, AD / 2, adMarkW, '#000000', '#000000'), { title: 'Themed icon (Android 13+)' }));
  // Preview of the adaptive icon under the common masks.
  {
    const fg = markSvg(0, 0, adMarkW, P.ember[500], P.halo[200]);
    const cell = (x, clip) => `<g transform="translate(${x} 0)"><defs><clipPath id="m${x}">${clip}</clipPath></defs><g clip-path="url(#m${x})">` +
      `<rect width="108" height="108" fill="url(#bg)"/><rect width="108" height="108" fill="url(#bgg)"/><g transform="translate(54 54)">${fg}</g></g></g>`;
    write('icon/adaptive/preview-masks.svg', svgDoc(108 * 4 + 36, 108, bgGrad('bg', 108, 108) +
      cell(0, '<circle cx="54" cy="54" r="54"/>') + cell(120, '<rect width="108" height="108" rx="24"/>') +
      cell(240, '<path d="M54 0 C96 0 108 12 108 54 C108 96 96 108 54 108 C12 108 0 96 0 54 C0 12 12 0 54 0Z"/>') +
      cell(360, '<rect x="0" y="0" width="108" height="108" rx="54" ry="40"/>'), { title: 'Adaptive icon mask preview' }));
  }

  // Android vector drawables (ready to drop into res/).
  const s108 = adMarkW / (2 * A);
  const vecPath = (pts, color, r) => `    <path\n        android:fillColor="${color}"\n        android:strokeColor="${color}"\n        android:strokeWidth="${(2 * r * s108).toFixed(2)}"\n        android:strokeLineJoin="round"\n        android:pathData="${polyD(pts, 54, 54, s108)}" />\n`;
  const vecHead = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n';
  write('icon/android-res/drawable/ic_launcher_foreground.xml', vecHead +
    '    <!-- IPXtream mark: Ember X with a Halo play triangle in its left counter. Fits the 66dp safe circle. -->\n' +
    vecPath(insetX(), P.ember[500], XR) + vecPath(triPoly(), P.halo[200], TR) + '</vector>\n');
  write('icon/android-res/drawable/ic_launcher_monochrome.xml', vecHead +
    '    <!-- Themed-icon layer (Android 13+): the launcher tints it, so one flat colour. -->\n' +
    vecPath(insetX(), '#FF000000', XR) + vecPath(triPoly(), '#FF000000', TR) + '</vector>\n');
  write('icon/android-res/drawable/ic_launcher_background.xml', '<?xml version="1.0" encoding="utf-8"?>\n' +
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:aapt="http://schemas.android.com/aapt"\n' +
    '    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n' +
    '    <!-- Night plum gradient with a soft Ember glow behind the mark. -->\n' +
    `    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n` +
    `            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108">\n` +
    `                <item android:offset="0" android:color="#FF2E1838" />\n                <item android:offset="1" android:color="#FF${P.night[950].slice(1)}" />\n` +
    `            </gradient>\n        </aapt:attr>\n    </path>\n` +
    `    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n` +
    `            <gradient android:type="radial" android:centerX="38.88" android:centerY="54" android:gradientRadius="54">\n` +
    `                <item android:offset="0" android:color="#38${P.ember[500].slice(1)}" />\n                <item android:offset="1" android:color="#00${P.ember[500].slice(1)}" />\n` +
    `            </gradient>\n        </aapt:attr>\n    </path>\n</vector>\n`);
  const adaptiveXml = '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n' +
    '    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n' +
    '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n';
  write('icon/android-res/mipmap-anydpi-v26/ic_launcher.xml', adaptiveXml);
  write('icon/android-res/mipmap-anydpi-v26/ic_launcher_round.xml', adaptiveXml);

  // Legacy PNG sizes.
  const DENS = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
  for (const [d, px] of Object.entries(DENS)) {
    png('icon/icon-legacy-square.svg', `icon/android-res/mipmap-${d}/ic_launcher.png`, px);
    png('icon/icon-legacy-round.svg', `icon/android-res/mipmap-${d}/ic_launcher_round.png`, px);
    png('icon/adaptive/ic_launcher_foreground.svg', `icon/adaptive/png/ic_launcher_foreground-${d}.png`, px * 108 / 48);
    png('icon/adaptive/ic_launcher_background.svg', `icon/adaptive/png/ic_launcher_background-${d}.png`, px * 108 / 48);
  }
  png('icon/icon-master.svg', 'icon/icon-master-1024.png', 1024);
  png('icon/icon-master.svg', 'icon/play-store-512.png', 512);
  png('icon/adaptive/preview-masks.svg', 'icon/adaptive/preview-masks.png', 936);

  // ─── TV banner (320 × 180, android:banner) ──────────────────────────────────
  {
    const W = 320, H = 180;
    const capPx = 27, markW = 64, markH = markW * MARK_H_RATIO, gap = 14;
    const wmk = wordmark(0, 0, capPx, VARIANTS.dark);
    const total = markW + gap + wmk.width;
    const x0 = (W - total) / 2, cy = H / 2 - 2;
    const body = bgGrad('bg', W, H) + `<rect width="${W}" height="${H}" fill="url(#bg)"/><rect width="${W}" height="${H}" fill="url(#bgg)"/>` +
      markSvg(x0 + markW / 2, cy, markW, P.ember[500], P.halo[200]) +
      wordmark(x0 + markW + gap, cy + capPx / 2, capPx, VARIANTS.dark).svg;
    write('icon/tv-banner/banner.svg', svgDoc(W, H, body, { title: `${WORD} TV banner` }));
    png('icon/tv-banner/banner.svg', 'icon/android-res/drawable-xhdpi/banner.png', 320);
    png('icon/tv-banner/banner.svg', 'icon/tv-banner/banner@2x.png', 640);
    png('icon/tv-banner/banner.svg', 'icon/tv-banner/banner-1280x720.png', 1280);
  }

  // ─── In-app vector drawables (mark + wordmark for the side menu and login) ──
  {
    const vec = (w, h, body) => '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n' +
      `    android:width="${(w / h * 24).toFixed(2)}dp"\n    android:height="24dp"\n    android:viewportWidth="${w.toFixed(2)}"\n    android:viewportHeight="${h.toFixed(2)}">\n${body}</vector>\n`;
    const vp = (d, color, stroke) => `    <path\n        android:fillColor="${color}"\n` +
      (stroke ? `        android:strokeColor="${color}"\n        android:strokeWidth="${stroke.toFixed(2)}"\n        android:strokeLineJoin="round"\n` : '') +
      `        android:pathData="${d}" />\n`;
    write('icon/android-res/drawable/ic_brand_mark.xml', vec(2 * A, 2 * B,
      '    <!-- IPXtream mark: Ember X + Halo play triangle. Generated by الهوية البصرية/_source/build-brand.js. -->\n' +
      vp(polyD(insetX(), A, B, 1), P.ember[500], 2 * XR) + vp(polyD(triPoly(), A, B, 1), P.halo[200], 2 * TR)));
    const capPx = 100, size = capPx / cap * wm.upem, k = size / wm.upem;
    let body = `    <!-- "${WORD}" wordmark in Lexend SemiBold, outlined; the X is Ember. -->\n`;
    for (const g of wm.glyphs.filter(g => g.d)) {
      const color = g.cl === xCluster ? P.ember[500] : P.night[50];
      body += `    <group\n        android:translateX="${(g.x * k).toFixed(2)}"\n        android:translateY="${(capPx - g.y * k).toFixed(2)}"\n` +
        `        android:scaleX="${k.toFixed(5)}"\n        android:scaleY="${(-k).toFixed(5)}">\n` +
        vp(g.d, color).replace(/^ {4}/gm, '        ') + '    </group>\n';
    }
    write('icon/android-res/drawable/ic_brand_wordmark.xml', vec(wm.advance * k, capPx, body));
  }

  // ─── Theme files ────────────────────────────────────────────────────────────
  writeTokens();
  writeKotlin();
  writeColorsXml();
  writePaletteMd();

  fs.mkdirSync(path.join(OUT, '_source'), { recursive: true });
  fs.writeFileSync(path.join(OUT, '_source/contrast.json'), JSON.stringify({ dark: contrastRows('dark'), light: contrastRows('light') }, null, 1));

  // ─── Copy the Android resources into the app, so the app never drifts from the brand files ──
  const appRes = path.join(OUT, '..', 'app', 'src', 'main', 'res');
  if (fs.existsSync(appRes)) {
    // Plain recursive copy: fs.cpSync crashes Node on Windows for this non-ASCII folder.
    const copyDir = (src, dst) => {
      fs.mkdirSync(dst, { recursive: true });
      for (const e of fs.readdirSync(src, { withFileTypes: true })) {
        const s = path.join(src, e.name), d = path.join(dst, e.name);
        if (e.isDirectory()) copyDir(s, d); else fs.copyFileSync(s, d);
      }
    };
    copyDir(path.join(OUT, 'icon', 'android-res'), appRes);
    console.log(`copied icon/android-res → ${appRes}`);
  }
  console.log(`${written.length} files written`);
}

function camel(s) { return s[0].toUpperCase() + s.slice(1); }
function argb(hex) { const h = hex.replace('#', '').toUpperCase(); return h.length === 8 ? h.slice(6) + h.slice(0, 6) : 'FF' + h; }

function writeTokens() {
  const color = {};
  for (const fam of FAMILIES) {
    color[fam.key] = { $description: `${fam.en} (${fam.ar}) — ${fam.role}` };
    for (const [step, hex] of Object.entries(P[fam.key])) color[fam.key][step] = { $type: 'color', $value: hex };
  }
  const theme = {};
  for (const [name, roles] of Object.entries(THEMES)) {
    theme[name] = {};
    for (const [role, ref] of Object.entries(roles)) {
      theme[name][role] = { $type: 'color', $value: ref.startsWith('#') ? ref.toUpperCase() : `{color.${ref}}`, $description: ROLE_DOCS[role] };
    }
  }
  const tokens = {
    $schema: 'https://design-tokens.github.io/community-group/format/',
    $description: 'IPXtream brand tokens (W3C Design Tokens format). Dark is the primary theme on TV.',
    color, theme,
    font: {
      latin: { $type: 'fontFamily', $value: ['Lexend', 'sans-serif'], $description: 'Latin UI + wordmark. OFL. https://fonts.google.com/specimen/Lexend' },
      arabic: { $type: 'fontFamily', $value: ['Readex Pro', 'sans-serif'], $description: 'Arabic UI + Arabic wordmark. OFL. https://fonts.google.com/specimen/Readex+Pro' },
    },
    type: {
      display:  { $type: 'dimension', $value: '48sp', weight: 600 },
      headline: { $type: 'dimension', $value: '32sp', weight: 600 },
      title:    { $type: 'dimension', $value: '24sp', weight: 500 },
      body:     { $type: 'dimension', $value: '18sp', weight: 400 },
      label:    { $type: 'dimension', $value: '16sp', weight: 500 },
      caption:  { $type: 'dimension', $value: '14sp', weight: 400, $description: 'Smallest size allowed on TV.' },
    },
    space: { xs: '4dp', sm: '8dp', md: '16dp', lg: '24dp', xl: '32dp', xxl: '48dp', gutter: '48dp', overscanX: '48dp', overscanY: '27dp' },
    radius: { sm: '8dp', md: '14dp', lg: '20dp', pill: '999dp' },
  };
  write('themes/tokens.json', JSON.stringify(tokens, null, 2) + '\n');
}

function writeKotlin() {
  let k = `package com.ipxtream.tv.ui.theme\n\n` +
    `// IPXtream brand colours — generated from الهوية البصرية/_source/build-brand.js.\n` +
    `// Not wired into the app yet: replacing the current Color.kt is a separate decision.\n\n` +
    `import androidx.compose.runtime.Immutable\nimport androidx.compose.runtime.staticCompositionLocalOf\nimport androidx.compose.ui.graphics.Color\n\n` +
    `// ─── Raw palette ──────────────────────────────────────────────────────────────\n\n` +
    `object IpxPalette {\n`;
  for (const fam of FAMILIES) {
    k += `    // ${fam.en} (${fam.ar}) — ${fam.role}. ${fam.note}\n`;
    for (const [step, hex] of Object.entries(P[fam.key])) k += `    val ${fam.en}${step} = Color(0x${argb(hex)})\n`;
    k += '\n';
  }
  k = k.trimEnd() + '\n}\n\n';
  k += `// ─── Theme roles ──────────────────────────────────────────────────────────────\n\n@Immutable\ndata class IpxColors(\n`;
  for (const role of Object.keys(THEMES.dark)) k += `    /** ${ROLE_DOCS[role]}. */\n    val ${role}: Color,\n`;
  k += `    val isDark: Boolean,\n)\n\n`;
  const ktRef = (ref) => {
    if (ref.startsWith('#')) return `Color(0x${argb(ref)})`;
    const [fam, step] = ref.split('.');
    return `IpxPalette.${camel(fam)}${step}`;
  };
  for (const [name, roles] of Object.entries(THEMES)) {
    k += `/** ${name === 'dark' ? 'Primary theme on TV.' : 'Light theme (settings preview, phone companion, daytime use).'} */\nval Ipx${camel(name)}Colors = IpxColors(\n`;
    for (const [role, ref] of Object.entries(roles)) k += `    ${role} = ${ktRef(ref)},\n`;
    k += `    isDark = ${name === 'dark'},\n)\n\n`;
  }
  k += `/** Read with LocalIpxColors.current; provide IpxLightColors at the theme root for the light theme. */\nval LocalIpxColors = staticCompositionLocalOf { IpxDarkColors }\n`;
  write('themes/Color.kt', k);
}

function writeColorsXml() {
  let x = `<?xml version="1.0" encoding="utf-8"?>\n<!-- IPXtream brand colours — generated from الهوية البصرية/_source/build-brand.js. -->\n<resources>\n\n    <!-- Raw palette -->\n`;
  for (const fam of FAMILIES) {
    x += `    <!-- ${fam.en} (${fam.ar}) — ${fam.role} -->\n`;
    for (const [step, hex] of Object.entries(P[fam.key])) x += `    <color name="${fam.key}_${step}">#FF${hex.slice(1)}</color>\n`;
  }
  for (const [name, roles] of Object.entries(THEMES)) {
    x += `\n    <!-- ${camel(name)} theme roles -->\n`;
    for (const [role, ref] of Object.entries(roles)) {
      const snake = role.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
      const val = ref.startsWith('#') ? `#${argb(ref)}` : `@color/${ref.replace('.', '_')}`;
      x += `    <color name="ipx_${name}_${snake}">${val}</color>\n`;
    }
  }
  x += `\n    <!-- Launcher / window -->\n    <color name="ic_launcher_background">#FF${P.night[950].slice(1)}</color>\n</resources>\n`;
  write('themes/colors.xml', x);
}

function writePaletteMd() {
  let m = `# لوحة الألوان · Colour palette\n\n` +
    `Generated from \`_source/build-brand.js\`. Contrast ratios are WCAG 2.x, computed against the theme background (alpha colours composited first).\n\n`;
  for (const fam of FAMILIES) {
    m += `## ${fam.ar} · ${fam.en} — ${fam.roleAr} · ${fam.role}\n\n${fam.note}\n\n| Token | Hex | vs Night 950 | vs Paper 50 |\n|---|---|---|---|\n`;
    for (const [step, hex] of Object.entries(P[fam.key])) {
      m += `| \`${fam.key}.${step}\`${+step === fam.hero ? ' ★' : ''} | \`${hex}\` | ${contrast(hex, P.night[950]).toFixed(2)} | ${contrast(hex, P.paper[50]).toFixed(2)} |\n`;
    }
    m += '\n';
  }
  for (const t of ['dark', 'light']) {
    m += `## ${t === 'dark' ? 'الثيم الداكن · Dark theme (primary on TV)' : 'الثيم الفاتح · Light theme'}\n\n| Role | Value | Use |\n|---|---|---|\n`;
    for (const [role, ref] of Object.entries(THEMES[t])) m += `| \`${role}\` | \`${ref}\` (${resolve(ref)}) | ${ROLE_DOCS[role]} |\n`;
    m += `\n**Contrast checks**\n\n| Foreground | Background | Ratio | Grade |\n|---|---|---|---|\n`;
    for (const r of contrastRows(t)) m += `| ${r.fg} \`${r.fgHex}\` | ${r.bg} \`${r.bgHex}\` | ${r.ratio.toFixed(2)}:1 | ${r.grade} |\n`;
    m += '\n';
  }
  m += `## ملاحظات المشاهدة على التلفاز · TV viewing notes\n\n` +
    `- Viewers sit about 3 m away. Body text must reach 4.5:1 and anything smaller than 18 sp should reach 7:1 — that is why body text uses \`night.50\` / \`night.300\` on the dark canvas, never \`night.400\` or darker.\n` +
    `- The canvas is \`night.950\` (#0D0811), not #000000: pure black next to bright posters causes blooming/halo on local-dimming LCDs and black crush on cheap panels, and a faint plum tint keeps the brand present.\n` +
    `- Text is \`night.50\` (#F8F4F6), not #FFFFFF, to avoid glare and "vibrating" edges at distance. The brightest element on screen is the Halo focus platter, so focus is always the first thing the eye finds.\n` +
    `- Ember is a highly saturated orange. TVs in "Vivid"/"Store" mode push saturation further, so Ember is for buttons, progress and small highlights — never a full-screen fill or long body text.\n` +
    `- Text on Ember is always dark (\`night.950\`, ${contrast(P.night[950], P.ember[500]).toFixed(1)}:1). White on Ember is only ${contrast('#FFFFFF', P.ember[500]).toFixed(1)}:1 and fails.\n` +
    `- Ember (hue ≈ 18°) and Rose (hue ≈ 350°) are both warm. Never rely on colour alone for errors: pair Rose with an icon or the word, and keep the LIVE dot paired with the "LIVE"/"مباشر" label.\n` +
    `- Focus must never rely on colour alone: it is a Halo platter or ring **plus** a 1.06–1.1× scale.\n` +
    `- Keep all text and focusable items inside the overscan-safe area: 48 dp left/right, 27 dp top/bottom at 960×540 dp.\n`;
  write('palette/palette.md', m);
}

main().catch(e => { console.error(e); process.exit(1); });
