"""Synthesises the IPXtream start-up intro sound (app/src/main/res/raw/intro_sfx.wav).

Every cue is timed to the animation in ui/intro/BrandIntro.kt (t = 0 when the sequence starts):
  0.00 s  whoosh, panned left → right   (top-left → bottom-right arm sweeps in)
  0.12 s  whoosh, panned right → left   (the other arm, from the right)
  0.50 s  soft low thud                 (arms finish crossing)
  0.50 s  rising "pop" + 0.70 s tick    (play triangle springs in and settles)
  0.78 s  warm bell chord with a tail   (wordmark rises in), fading out with the picture by ~2.4 s

Usage: python build-intro-sfx.py <out.wav>     (needs numpy)
"""
import sys
import wave

import numpy as np

SR = 44100
LENGTH = 2.6
rng = np.random.default_rng(7)
N = int(SR * LENGTH)
t = np.arange(N) / SR
left = np.zeros(N)
right = np.zeros(N)


def place(sig, start, pan_from=0.0, pan_to=None, gain=1.0):
    """Mixes a mono signal in at `start` seconds; pan -1 = left, +1 = right (equal-power)."""
    i = int(start * SR)
    sig = sig[: N - i] * gain
    pan = np.linspace(pan_from, pan_from if pan_to is None else pan_to, len(sig))
    a = (pan + 1) * np.pi / 4
    left[i : i + len(sig)] += sig * np.cos(a)
    right[i : i + len(sig)] += sig * np.sin(a)


def svf_bandpass(x, f0, f1, q=1.6):
    """State-variable band-pass whose centre frequency glides exponentially from f0 to f1."""
    f = f0 * (f1 / f0) ** np.linspace(0, 1, len(x))
    g = 2 * np.sin(np.pi * f / SR)
    low = band = 0.0
    out = np.empty_like(x)
    for n in range(len(x)):
        high = x[n] - low - band / q
        band += g[n] * high
        low += g[n] * band
        out[n] = band
    return out


def env(length, attack, decay_power=2.0):
    n = int(length * SR)
    a = int(attack * SR)
    e = np.ones(n)
    e[:a] = np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    e[a:] = (1 - np.linspace(0, 1, n - a)) ** decay_power
    return e


def whoosh(length, f0, f1):
    """Noise through a narrow band-pass gliding upwards, twice for a steeper slope."""
    noise = rng.standard_normal(int(length * SR))
    swept = svf_bandpass(svf_bandpass(noise, f0, f1, q=5.0), f0, f1, q=5.0)
    return swept / np.max(np.abs(swept)) * env(length, 0.12, 1.6)


def thud():
    n = int(0.35 * SR)
    tt = np.arange(n) / SR
    freq = 46 + 34 * np.exp(-tt * 18)
    body = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-tt * 11)
    click = svf_bandpass(rng.standard_normal(n), 900, 300, q=0.9) * np.exp(-tt * 70)
    return body + 0.25 * click


def pop(f0, f1, length, decay):
    n = int(length * SR)
    tt = np.arange(n) / SR
    freq = f0 * (f1 / f0) ** np.clip(tt / 0.045, 0, 1)
    ph = 2 * np.pi * np.cumsum(freq) / SR
    tone = np.sin(ph) + 0.3 * np.sin(2 * ph)
    e = np.exp(-tt * decay) * np.clip(tt / 0.003, 0, 1)
    return tone * e


def bell(freq, length, detune=0.0):
    n = int(length * SR)
    tt = np.arange(n) / SR
    f = freq * (1 + detune)
    s = np.sin(2 * np.pi * f * tt) + 0.22 * np.sin(2 * np.pi * 2.01 * f * tt) * np.exp(-tt * 3)
    return s * np.exp(-tt * 2.1) * np.clip(tt / 0.012, 0, 1)


def reverb(x, mix=0.28):
    """Small Schroeder-style tail: four feedback combs in parallel."""
    out = np.zeros_like(x)
    for d_ms, fb in ((29.7, 0.77), (37.1, 0.74), (41.1, 0.72), (43.7, 0.70)):
        d = int(d_ms / 1000 * SR)
        y = x.copy()
        for k in range(d, len(y)):
            y[k] += fb * y[k - d]
        out += y
    return x * (1 - mix) + out / 4 * mix


# Arms.
place(whoosh(0.50, 380, 2600), 0.00, -0.8, 0.8, gain=0.55)
place(whoosh(0.50, 450, 3000), 0.12, 0.8, -0.8, gain=0.50)
# Crossing + triangle.
place(thud(), 0.50, gain=0.65)
place(pop(620, 1480, 0.20, 26), 0.50, -0.25, gain=0.30)
place(pop(1650, 1900, 0.06, 60), 0.70, -0.25, gain=0.10)
# Wordmark chord (A add9: A4 E5 B5 C#6), notes slightly staggered, with a tail.
chord = np.zeros(int(1.8 * SR))
for k, (fq, dt) in enumerate(((440.0, 0.0), (659.25, 0.035), (987.77, 0.07), (1108.73, 0.10))):
    b = bell(fq, 1.8 - dt, detune=(k - 1.5) * 0.0015)
    chord[int(dt * SR) : int(dt * SR) + len(b)] += b * (0.32 if k else 0.4)
place(reverb(chord), 0.78, gain=0.42)

mix = np.stack([left, right], axis=1)
mix = np.stack([reverb(mix[:, 0], 0.12), reverb(mix[:, 1], 0.12)], axis=1)
fade = np.ones(N)
fs, fe = int(2.15 * SR), N
fade[fs:fe] = np.cos(np.linspace(0, np.pi / 2, fe - fs)) ** 2
mix *= fade[:, None]
mix *= 0.7 / np.max(np.abs(mix))          # peak -3 dBFS
pcm = (mix * 32767).astype("<i2")

with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print(f"wrote {sys.argv[1]}: {LENGTH} s, {len(pcm.tobytes()) // 1024} KB")
