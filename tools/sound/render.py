#!/usr/bin/env python3
"""Renders the game's sound banks: one 16-bit mono WAV for every `Cue`, for every skin.

    python3 tools/sound/render.py            # every bank
    python3 tools/sound/render.py buzzers    # one

The samples are synthesized, so there is no licence to carry and a sound is a few numbers to change:
edit a recipe below, render, and commit the files it writes to
`app/designsystem/src/commonMain/composeResources/files/sound/<bank>/`. Pure Python (no numpy); a render is
deterministic, so rendering twice writes the same bytes. The cue names are read from `Cue.kt`: a cue with no
recipe in a bank, or a recipe for no cue, stops the render.

Every sample is levelled alike (peak and loudness), faded in and out a few milliseconds so none clicks, and
left to the skin's `level` and `trim` to sit in the mix.
"""

import math
import random
import re
import struct
import sys
import wave
import zlib
from pathlib import Path

SR = 22_050
ROOT = Path(__file__).resolve().parents[2]
CUE_KT = ROOT / "app/designsystem/src/commonMain/kotlin/io/ntole/kvizic/design/sound/Cue.kt"
OUT = ROOT / "app/designsystem/src/commonMain/composeResources/files/sound"

# What every sample is levelled to: the loudest it may peak, and how loud it is to be on average, when its
# shape lets it (a click cannot be both: its peak wins).
PEAK = 0.89
RMS = 0.17
TWO_PI = 2 * math.pi


# --- Building blocks -------------------------------------------------------------------------------


def n(seconds):
    return max(1, int(round(seconds * SR)))


def freqs(f0, f1, dur, curve="exp"):
    """A pitch gliding from f0 to f1 Hz over dur seconds, as one frequency a sample."""
    count = n(dur)
    out = []
    for i in range(count):
        x = i / max(1, count - 1)
        if curve == "exp":
            out.append(f0 * (f1 / f0) ** x)
        else:
            out.append(f0 + (f1 - f0) * x)
    return out


def vibrato(f, dur, rate=6.0, depth=0.012):
    """A steady pitch f with a vibrato of depth (a share) at rate Hz."""
    return [f * (1 + depth * math.sin(TWO_PI * rate * i / SR)) for i in range(n(dur))]


def osc(shape, f, dur):
    """An oscillator: shape 'sine', 'tri', 'saw' or 'square'; f a pitch in Hz or one a sample."""
    count = n(dur)
    pitch = f if isinstance(f, list) else [f] * count
    phase = 0.0
    out = []
    for i in range(count):
        phase = (phase + pitch[min(i, len(pitch) - 1)] / SR) % 1.0
        if shape == "sine":
            out.append(math.sin(TWO_PI * phase))
        elif shape == "tri":
            out.append(4 * abs(phase - 0.5) - 1)
        elif shape == "saw":
            out.append(2 * phase - 1)
        else:
            out.append(1.0 if phase < 0.5 else -1.0)
    return out


def noise(dur, rng):
    return [rng.uniform(-1, 1) for _ in range(n(dur))]


def decay(dur, tau, attack=0.002):
    """An envelope: up in attack seconds, then down exponentially with time constant tau."""
    out = []
    for i in range(n(dur)):
        t = i / SR
        out.append((1 - math.exp(-t / attack)) * math.exp(-t / tau))
    return out


def swell(dur, rise, fall):
    """An envelope rising linearly for rise seconds, level, then falling linearly over the last fall seconds."""
    count = n(dur)
    a, r = n(rise), n(fall)
    out = []
    for i in range(count):
        v = 1.0
        if i < a:
            v = i / a
        if i > count - r:
            v = min(v, (count - i) / r)
        out.append(v)
    return out


def mul(a, b):
    return [x * y for x, y in zip(a, b)]


def gain(a, g):
    return [x * g for x in a]


def mix(parts):
    """Lays samples at offsets: [(offset_seconds, samples, gain), ...] summed into one."""
    length = max(n(off) + len(s) for off, s, _ in parts)
    out = [0.0] * length
    for off, s, g in parts:
        start = n(off) if off > 0 else 0
        for i, v in enumerate(s):
            out[start + i] += v * g
    return out


def biquad(x, kind, fc, q=0.707):
    """A second-order filter (RBJ's): 'lp', 'hp' or 'bp' at fc Hz."""
    fc = min(fc, SR * 0.45)
    w0 = TWO_PI * fc / SR
    alpha = math.sin(w0) / (2 * q)
    cosw = math.cos(w0)
    if kind == "lp":
        b0, b1, b2 = (1 - cosw) / 2, 1 - cosw, (1 - cosw) / 2
    elif kind == "hp":
        b0, b1, b2 = (1 + cosw) / 2, -(1 + cosw), (1 + cosw) / 2
    else:
        b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * cosw, 1 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    x1 = x2 = y1 = y2 = 0.0
    out = []
    for v in x:
        y = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, v, y1, y
        out.append(y)
    return out


def sweep_filter(x, kind, f0, f1, q=1.0, block=64):
    """A filter whose cutoff glides from f0 to f1 over the sound, set anew every block of samples."""
    out = []
    for start in range(0, len(x), block):
        frac = start / max(1, len(x) - 1)
        fc = f0 * (f1 / f0) ** frac
        out.extend(biquad(x[start:start + block], kind, fc, q))
    return out


# --- Voices ----------------------------------------------------------------------------------------


def partials(f, dur, parts):
    """Sines at multiples of f: parts is [(ratio, amplitude, decay_seconds), ...]."""
    out = [0.0] * n(dur)
    for ratio, amp, tau in parts:
        wave_ = osc("sine", f * ratio, dur)
        env = decay(dur, tau, 0.001)
        for i in range(len(out)):
            out[i] += wave_[i] * env[i] * amp
    return out


def bell(f, dur, tau=0.35, ratio=3.5, index=2.5):
    """A struck bell: two-operator FM whose brightness dies away faster than its loudness."""
    out = []
    phase_c = phase_m = 0.0
    for i in range(n(dur)):
        t = i / SR
        phase_c += f / SR
        phase_m += f * ratio / SR
        idx = index * math.exp(-t / (tau * 0.4))
        out.append(math.sin(TWO_PI * phase_c + idx * math.sin(TWO_PI * phase_m)) * math.exp(-t / tau))
    return mul(out, swell(dur, 0.001, 0.01))


def glock(f, dur=0.6, tau=0.25):
    return partials(f, dur, [(1, 1.0, tau), (2.76, 0.45, tau * 0.5), (5.4, 0.22, tau * 0.3), (8.9, 0.1, tau * 0.2)])


def marimba(f, dur=0.5, tau=0.16):
    return partials(f, dur, [(1, 1.0, tau), (3.9, 0.32, tau * 0.25), (9.2, 0.1, tau * 0.1)])


def block(f, dur=0.18, tau=0.035):
    """A wooden block: a short pitched knock with an inharmonic overtone."""
    return partials(f, dur, [(1, 1.0, tau), (2.45, 0.55, tau * 0.6), (4.1, 0.25, tau * 0.4)])


def thunk(f0, f1, dur, tau, rng):
    """A low thud: a pitch dropping fast, with a bit of noise on its front."""
    body = mul(osc("sine", freqs(f0, f1, dur), dur), decay(dur, tau, 0.001))
    click = mul(biquad(noise(0.012, rng), "bp", 1800, 0.8), decay(0.012, 0.004, 0.0005))
    return mix([(0, body, 1.0), (0, click, 0.35)])


def blip(shape, f0, f1, dur, tau, lowpass=None):
    s = mul(osc(shape, freqs(f0, f1, dur), dur), decay(dur, tau, 0.002))
    return biquad(s, "lp", lowpass) if lowpass else s


def brass(f, dur, rng, vib=0.008, bright=3200):
    """A brass stab: two detuned saws, filtered, with a slow-ish attack and a vibrato."""
    a = osc("saw", vibrato(f, dur, 5.5, vib), dur)
    b = osc("saw", vibrato(f * 1.006, dur, 5.2, vib), dur)
    s = biquad([(x + y) * 0.5 for x, y in zip(a, b)], "lp", bright, 0.9)
    return mul(s, swell(dur, 0.025, min(0.12, dur * 0.4)))


def tick(f, dur, rng, bright=3200):
    """A pencil's or a clock's tick: a spark of noise and a short pitched click."""
    spark = mul(biquad(noise(dur, rng), "bp", bright, 1.2), decay(dur, dur * 0.25, 0.0005))
    tone = mul(osc("sine", f, dur), decay(dur, dur * 0.3, 0.0005))
    return mix([(0, spark, 0.8), (0, tone, 0.5)])


def paper(dur, rng, lo=1500, hi=5000, rise=0.3, fall=None):
    """Paper moving: noise through a filter that glides, with a soft swell."""
    s = sweep_filter(noise(dur, rng), "bp", lo, hi, 0.9)
    return mul(s, swell(dur, dur * rise, dur * (fall if fall is not None else 0.6)))


def whoosh(dur, rng, lo, hi, rise=0.5):
    s = sweep_filter(noise(dur, rng), "lp", lo, hi, 0.8)
    return mul(s, swell(dur, dur * rise, dur * (1 - rise)))


def claps(count, spacing, rng, lo=1100, q=1.1, tail=0.06):
    parts = []
    for i in range(count):
        # A clap is a few hands a hair apart: a flam of three bursts.
        for flam in range(3):
            burst = mul(biquad(noise(tail, rng), "bp", lo + rng.uniform(-200, 200), q), decay(tail, 0.014, 0.0004))
            parts.append((i * spacing + flam * 0.009 + rng.uniform(0, 0.006), burst, 0.8))
    return mix(parts)


def crackle(dur, rng, density=90):
    out = [0.0] * n(dur)
    for _ in range(int(dur * density)):
        at = rng.randrange(len(out) - 40)
        amp = rng.uniform(0.3, 1.0)
        for j in range(rng.randrange(6, 30)):
            out[at + j] += rng.uniform(-1, 1) * amp * math.exp(-j / 6)
    return biquad(out, "hp", 1200)


def seq(notes, voice, gap):
    """A run of notes, one every gap seconds: voice(freq) makes each."""
    parts = [(i * gap, voice(f), 1.0) for i, f in enumerate(notes)]
    return mix(parts)


# Pitches, in Hz.
C4, D4, E4, F4, G4, A4, B4 = 261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88
C5, D5, E5, F5, G5, A5, B5 = 523.25, 587.33, 659.25, 698.46, 783.99, 880.00, 987.77
C6, D6, E6, G6 = 1046.50, 1174.66, 1318.51, 1567.98
C7 = 2093.00


# --- The game show: buzzers, bells and bright stabs -------------------------------------------------


def buzzers(rng):
    r = rng
    c = {}
    # The chrome.
    c["tap_primary"] = lambda: mix([(0, thunk(210, 85, 0.16, 0.05, r), 1.0), (0, blip("square", 440, 330, 0.06, 0.02, 2500), 0.35)])
    c["tap"] = lambda: mix([(0, blip("square", 700, 640, 0.07, 0.022, 3000), 0.8), (0, tick(1500, 0.01, r), 0.6)])
    c["tap_soft"] = lambda: mix([(0, blip("sine", 1250, 1150, 0.06, 0.014), 1.0), (0, tick(2400, 0.008, r), 0.3)])
    c["back"] = lambda: mix([(0, blip("square", 520, 500, 0.06, 0.02, 2200), 0.8), (0.055, blip("square", 390, 370, 0.07, 0.025, 2200), 0.8)])
    c["toggle_on"] = lambda: mix([(0, blip("square", 600, 600, 0.05, 0.016, 2600), 0.7), (0.05, blip("square", 900, 900, 0.07, 0.022, 2600), 0.8)])
    c["toggle_off"] = lambda: mix([(0, blip("square", 900, 900, 0.05, 0.016, 2600), 0.7), (0.05, blip("square", 600, 600, 0.07, 0.022, 2600), 0.8)])
    c["dialog_open"] = lambda: mul(blip("tri", 280, 760, 0.14, 0.09), swell(0.14, 0.01, 0.05))
    c["dialog_close"] = lambda: mul(blip("tri", 760, 280, 0.12, 0.07), swell(0.12, 0.01, 0.05))
    c["key"] = lambda: mix([(0, blip("sine", 980, 940, 0.05, 0.011), 1.0), (0, blip("sine", 1960, 1900, 0.04, 0.008), 0.3), (0, tick(3000, 0.006, r), 0.2)])
    c["key_delete"] = lambda: mix([(0, blip("sine", 640, 420, 0.07, 0.02), 1.0), (0, tick(1400, 0.008, r), 0.25)])
    c["error"] = lambda: mix([(0, mul(biquad(osc("square", 150, 0.1), "lp", 1400), swell(0.1, 0.004, 0.02)), 1.0), (0.13, mul(biquad(osc("square", 142, 0.16), "lp", 1400), swell(0.16, 0.004, 0.05)), 1.0)])
    c["copied"] = lambda: mix([(0, bell(C6 * 1.0, 0.18, 0.06), 0.9), (0.06, bell(E6 * 1.0, 0.25, 0.08), 0.9)])
    # The lobby.
    c["welcome"] = lambda: seq([C5, E5, G5, C6], lambda f: bell(f, 0.55, 0.2, 2.0, 1.6), 0.075)
    c["joined"] = lambda: mix([(0, bell(G5, 0.3, 0.1, 2.0, 1.4), 0.9), (0.07, bell(C6, 0.4, 0.14, 2.0, 1.4), 0.9)])
    c["left"] = lambda: mix([(0, bell(C6, 0.3, 0.1, 2.0, 1.4), 0.8), (0.07, bell(G5, 0.4, 0.14, 2.0, 1.4), 0.8)])
    c["host"] = lambda: mix([(0, brass(G4, 0.2, r), 0.8), (0.11, brass(C5, 0.2, r), 0.8), (0.22, brass(E5, 0.2, r), 0.8), (0.33, brass(G5, 0.5, r), 1.0), (0.33, bell(C7, 0.5, 0.2), 0.25)])
    c["kicked"] = lambda: mix([(0, biquad(mul(osc("saw", vibrato(220, 0.34, 6, 0.02), 0.34), swell(0.34, 0.02, 0.1)), "lp", 1100), 1.0), (0.36, biquad(mul(osc("saw", freqs(185, 120, 0.6), 0.6), swell(0.6, 0.02, 0.3)), "lp", 900), 1.0)])
    c["vote"] = lambda: mix([(0, block(420, 0.1, 0.022), 0.9), (0.08, block(330, 0.12, 0.026), 0.9)])
    c["notice"] = lambda: mix([(0, bell(A5, 0.35, 0.14, 2.0, 1.2), 0.8), (0.12, bell(D6, 0.5, 0.2, 2.0, 1.2), 0.8)])
    c["link_lost"] = lambda: mix([(0, blip("square", 620, 600, 0.09, 0.03, 1800), 0.8), (0.1, blip("square", 470, 450, 0.09, 0.03, 1800), 0.8), (0.2, blip("square", 330, 300, 0.16, 0.06, 1800), 0.8)])
    c["link_back"] = lambda: mix([(0, blip("square", 330, 340, 0.09, 0.03, 1800), 0.8), (0.1, blip("square", 470, 480, 0.09, 0.03, 1800), 0.8), (0.2, blip("square", 700, 710, 0.18, 0.07, 1800), 0.8)])
    # The reactions.
    c["react_bravo"] = lambda: mix([(0, brass(C5, 0.3, r), 0.6), (0, brass(E5, 0.3, r), 0.6), (0, brass(G5, 0.3, r), 0.6), (0.02, bell(C7, 0.4, 0.15), 0.25)])
    c["react_clap"] = lambda: claps(5, 0.085, r)
    c["react_fire"] = lambda: mix([(0, whoosh(0.5, r, 500, 4200, 0.35), 0.9), (0.12, crackle(0.35, r), 0.25)])
    c["react_wow"] = lambda: mul(osc("sine", freqs(380, 1250, 0.2, "lin") + freqs(1250, 880, 0.22, "lin"), 0.42), swell(0.42, 0.03, 0.12))
    c["react_laugh"] = lambda: mix([(i * 0.105, mul(biquad(osc("saw", 290 - i * 14, 0.075), "bp", 900, 1.4), swell(0.075, 0.01, 0.03)), 1.1) for i in range(4)])
    c["react_oops"] = lambda: biquad(mul(osc("saw", freqs(330, 215, 0.5, "lin"), 0.5), swell(0.5, 0.03, 0.15)), "lp", 1300)
    c["nudge"] = lambda: mix([(0, bell(C7 * 0.75, 0.7, 0.32, 3.5, 2.0), 1.0), (0.2, bell(C7 * 0.75, 0.8, 0.36, 3.5, 2.0), 0.9)])
    # A game.
    c["count"] = lambda: mix([(0, blip("sine", 880, 860, 0.1, 0.03), 1.0), (0, tick(2600, 0.008, r), 0.3)])
    c["go"] = lambda: mix([(0, brass(C5, 0.35, r), 0.5), (0, brass(E5, 0.35, r), 0.5), (0, brass(G5, 0.35, r), 0.5), (0, bell(C6, 0.5, 0.2), 0.5), (0.01, bell(G6, 0.5, 0.2), 0.3)])
    c["question"] = lambda: mix([(0, paper(0.16, r, 500, 3600, 0.5, 0.5), 0.7), (0.1, bell(E6, 0.4, 0.15, 2.0, 1.0), 0.6)])
    c["tile"] = lambda: mix([(0, blip("sine", 540, 880, 0.05, 0.02), 1.0), (0, tick(2000, 0.006, r), 0.2)])
    c["lock"] = lambda: mix([(0, thunk(140, 60, 0.2, 0.06, r), 1.0), (0, blip("square", 230, 200, 0.05, 0.015, 1800), 0.4), (0, tick(1200, 0.01, r), 0.5)])
    c["tick"] = lambda: mix([(0, tick(1500, 0.014, r, 3600), 0.9), (0, blip("sine", 1200, 1100, 0.03, 0.007), 0.5)])
    c["time_up"] = lambda: mix([(0, mul(biquad([x + y for x, y in zip(osc("square", 118, 0.62), osc("square", 124, 0.62))], "lp", 1800), swell(0.62, 0.005, 0.22)), 0.6)])
    c["right"] = lambda: mix([(0, bell(E6, 0.5, 0.18, 2.0, 1.4), 0.9), (0.09, bell(G6, 0.7, 0.26, 2.0, 1.4), 1.0), (0.09, bell(C7, 0.5, 0.18), 0.25)])
    c["wrong"] = lambda: mix([(0, mul(biquad(osc("square", freqs(150, 92, 0.28, "lin"), 0.28), "lp", 900), swell(0.28, 0.005, 0.12)), 1.0)])
    c["bonus"] = lambda: seq([G6, C7, E6 * 2], lambda f: bell(f, 0.3, 0.1, 2.0, 1.0), 0.055)
    c["rank_up"] = lambda: mul(blip("tri", 420, 980, 0.2, 0.2), swell(0.2, 0.01, 0.06))
    c["rank_down"] = lambda: mul(blip("tri", 720, 300, 0.22, 0.2), swell(0.22, 0.01, 0.08))
    c["last"] = lambda: mix([(0, thunk(110, 55, 0.3, 0.1, r), 1.0), (0.17, thunk(110, 55, 0.3, 0.1, r), 1.0), (0.17, brass(G4, 0.4, r), 0.5), (0.17, brass(D5, 0.4, r), 0.4)])
    # A game's end.
    c["win"] = lambda: mix(
        [(0, brass(G4, 0.15, r), 0.8), (0.14, brass(C5, 0.15, r), 0.8), (0.28, brass(E5, 0.15, r), 0.8), (0.42, brass(G5, 0.4, r), 0.9),
         (0.84, brass(E5, 0.13, r), 0.8), (0.97, brass(G5, 0.75, r), 1.0), (0.97, brass(C5, 0.75, r), 0.55), (0.97, bell(C7, 0.9, 0.35), 0.3),
         (0.42, bell(G6, 0.6, 0.2), 0.25), (1.0, whoosh(0.6, r, 3000, 6000, 0.2), 0.15)]
    )
    c["podium"] = lambda: mix([(0, brass(C5, 0.15, r), 0.8), (0.14, brass(E5, 0.15, r), 0.8), (0.28, brass(G5, 0.55, r), 0.9), (0.28, bell(C7, 0.6, 0.25), 0.25)])
    c["end"] = lambda: seq([G5, E5, C5], lambda f: bell(f, 0.7, 0.28, 2.0, 1.1), 0.16)
    c["best"] = lambda: mix([(i * 0.045, bell(f, 0.5, 0.18, 2.0, 1.2), 0.8) for i, f in enumerate([C5, E5, G5, C6, E6, G6])] + [(0.27, bell(C7, 0.9, 0.4), 0.4), (0.2, whoosh(0.5, r, 2500, 7000, 0.2), 0.12)])
    return c


# --- The exercise book: pencil, wood, paper and soft mallets ---------------------------------------


def notebook(rng):
    r = rng
    c = {}
    c["tap_primary"] = lambda: mix([(0, block(330, 0.16, 0.04), 1.0), (0, paper(0.05, r, 600, 2400, 0.1, 0.8), 0.35)])
    c["tap"] = lambda: mix([(0, block(520, 0.12, 0.03), 0.9), (0, tick(2200, 0.006, r), 0.3)])
    c["tap_soft"] = lambda: tick(1900, 0.014, r, 4200)
    c["back"] = lambda: paper(0.12, r, 3600, 900, 0.1, 0.8)
    c["toggle_on"] = lambda: mix([(0, tick(1500, 0.012, r, 3800), 0.9), (0.06, tick(2100, 0.012, r, 4400), 1.0)])
    c["toggle_off"] = lambda: mix([(0, tick(2100, 0.012, r, 4400), 0.9), (0.06, tick(1500, 0.012, r, 3800), 1.0)])
    c["dialog_open"] = lambda: mix([(0, paper(0.14, r, 900, 3200, 0.5, 0.5), 0.8), (0.08, block(440, 0.1, 0.025), 0.5)])
    c["dialog_close"] = lambda: mix([(0, paper(0.12, r, 3200, 900, 0.2, 0.7), 0.8), (0, block(330, 0.1, 0.025), 0.4)])
    c["key"] = lambda: mix([(0, tick(2300, 0.012, r, 4600), 0.9), (0, block(900, 0.06, 0.012), 0.35)])
    c["key_delete"] = lambda: mul(biquad(noise(0.075, r), "bp", 1700, 1.0), swell(0.075, 0.01, 0.04))
    c["error"] = lambda: mix([(0, block(190, 0.14, 0.04), 1.0), (0.13, block(170, 0.2, 0.05), 1.0)])
    c["copied"] = lambda: mix([(0, tick(2400, 0.012, r, 4600), 0.8), (0.055, tick(3000, 0.012, r, 5200), 0.9)])
    c["welcome"] = lambda: seq([C5, E5, G5, C6], lambda f: marimba(f, 0.5, 0.18), 0.08)
    c["joined"] = lambda: mix([(0, marimba(G5, 0.3, 0.1), 0.9), (0.08, marimba(C6, 0.4, 0.14), 0.9)])
    c["left"] = lambda: mix([(0, marimba(C6, 0.3, 0.1), 0.8), (0.08, marimba(G5, 0.4, 0.14), 0.8)])
    c["host"] = lambda: mix([(i * 0.09, marimba(f, 0.5, 0.2), 0.9) for i, f in enumerate([G4, C5, E5, G5])] + [(0.27, glock(C7, 0.7, 0.3), 0.3)])
    c["kicked"] = lambda: seq([A4 / 2, G4 / 2, E4 / 2], lambda f: marimba(f, 0.6, 0.24), 0.2)
    c["vote"] = lambda: mix([(0, tick(1800, 0.012, r, 3800), 0.9), (0.07, tick(1500, 0.012, r, 3400), 0.9)])
    c["notice"] = lambda: mix([(0, glock(A5, 0.5, 0.2), 0.8), (0.12, glock(D6, 0.6, 0.24), 0.8)])
    c["link_lost"] = lambda: seq([A4, F4, D4], lambda f: marimba(f, 0.3, 0.1), 0.11)
    c["link_back"] = lambda: seq([D4, F4, A4], lambda f: marimba(f, 0.4, 0.14), 0.11)
    c["react_bravo"] = lambda: mix([(i * 0.06, marimba(f, 0.4, 0.16), 0.8) for i, f in enumerate([C5, E5, G5, C6])] + [(0.15, glock(C7, 0.5, 0.2), 0.25)])
    c["react_clap"] = lambda: claps(5, 0.09, r, 900, 0.9)
    c["react_fire"] = lambda: mix([(0, paper(0.45, r, 700, 4500, 0.4, 0.5), 0.9), (0.1, crackle(0.35, r, 70), 0.3)])
    c["react_wow"] = lambda: mul(osc("sine", freqs(380, 1250, 0.2, "lin") + freqs(1250, 880, 0.22, "lin"), 0.42), swell(0.42, 0.03, 0.12))
    c["react_laugh"] = lambda: mix([(i * 0.1, marimba(f, 0.18, 0.07), 1.0) for i, f in enumerate([E5, E5, D5, E5])])
    c["react_oops"] = lambda: mix([(0, marimba(E4, 0.4, 0.15), 1.0), (0.17, marimba(D4 * 0.94, 0.5, 0.2), 1.0)])
    c["nudge"] = lambda: mix([(0, glock(C7 * 0.75, 0.9, 0.4), 1.0), (0.22, glock(C7 * 0.75, 1.0, 0.45), 0.9)])
    c["count"] = lambda: mix([(0, block(740, 0.1, 0.025), 1.0), (0, tick(2400, 0.006, r), 0.25)])
    c["go"] = lambda: mix([(0, marimba(C5, 0.5, 0.22), 0.8), (0, marimba(E5, 0.5, 0.22), 0.7), (0, marimba(G5, 0.5, 0.22), 0.7), (0, glock(C6, 0.6, 0.3), 0.4)])
    c["question"] = lambda: mix([(0, paper(0.18, r, 700, 3600, 0.4, 0.5), 0.8), (0.12, glock(E6, 0.4, 0.18), 0.5)])
    c["tile"] = lambda: mix([(0, block(1100, 0.06, 0.012), 0.8), (0, tick(2600, 0.006, r), 0.2)])
    c["lock"] = lambda: mix([(0, block(250, 0.16, 0.04), 1.0), (0, paper(0.06, r, 700, 2400, 0.05, 0.9), 0.5), (0, tick(1500, 0.01, r), 0.4)])
    c["tick"] = lambda: mix([(0, block(1500, 0.05, 0.008), 0.7), (0, tick(3400, 0.01, r, 4800), 0.7)])
    c["time_up"] = lambda: mix([(0, bell(1760, 0.5, 0.2, 5.0, 1.2), 0.9), (0.17, bell(1760, 0.6, 0.24, 5.0, 1.2), 0.9)])
    c["right"] = lambda: mix([(0, glock(E6, 0.5, 0.2), 0.9), (0.1, glock(G6, 0.7, 0.28), 1.0)])
    c["wrong"] = lambda: mix([(0, block(160, 0.2, 0.05), 1.0), (0, biquad(noise(0.1, r), "bp", 1300, 1.0), 0.15)])
    c["bonus"] = lambda: seq([G6, C7, E6 * 2], lambda f: glock(f, 0.35, 0.14), 0.06)
    c["rank_up"] = lambda: seq([C5, D5, E5, G5, C6], lambda f: marimba(f, 0.18, 0.07), 0.035)
    c["rank_down"] = lambda: seq([C6, G5, E5, D5, C5], lambda f: marimba(f, 0.18, 0.07), 0.035)
    c["last"] = lambda: mix([(0, block(150, 0.25, 0.08), 1.0), (0.17, block(150, 0.3, 0.09), 1.0), (0.17, marimba(G4, 0.45, 0.2), 0.5)])
    c["win"] = lambda: mix(
        [(0, marimba(G4, 0.2, 0.1), 0.9), (0.14, marimba(C5, 0.2, 0.1), 0.9), (0.28, marimba(E5, 0.2, 0.1), 0.9), (0.42, marimba(G5, 0.5, 0.2), 1.0),
         (0.84, marimba(E5, 0.16, 0.1), 0.9), (0.97, marimba(G5, 0.9, 0.35), 1.0), (0.97, marimba(C5, 0.9, 0.35), 0.6), (0.97, glock(C7, 0.9, 0.4), 0.3),
         (0.42, glock(G6, 0.6, 0.25), 0.25)]
    )
    c["podium"] = lambda: mix([(0, marimba(C5, 0.2, 0.1), 0.9), (0.14, marimba(E5, 0.2, 0.1), 0.9), (0.28, marimba(G5, 0.6, 0.24), 1.0), (0.28, glock(C7, 0.6, 0.25), 0.25)])
    c["end"] = lambda: seq([G5, E5, C5], lambda f: marimba(f, 0.7, 0.28), 0.17)
    c["best"] = lambda: mix([(i * 0.05, marimba(f, 0.45, 0.18), 0.8) for i, f in enumerate([C5, E5, G5, C6, E6, G6])] + [(0.3, glock(C7, 0.9, 0.45), 0.4)])
    return c


BANKS = {"buzzers": buzzers, "notebook": notebook}


# --- Finishing and writing -------------------------------------------------------------------------


def finish(samples):
    """Levels a sample (peak and loudness), fades its ends, and returns 16-bit PCM."""
    samples = list(samples)
    # Nothing under a hertz or two: the filters' drift would shift the middle.
    mean = sum(samples) / len(samples)
    samples = [s - mean for s in samples]
    peak = max(abs(s) for s in samples) or 1.0
    rms = math.sqrt(sum(s * s for s in samples) / len(samples)) or 1.0
    scale = min(PEAK / peak, RMS / rms)
    fade_in, fade_out = n(0.002), n(0.008)
    out = []
    for i, s in enumerate(samples):
        v = s * scale
        if i < fade_in:
            v *= i / fade_in
        if i >= len(samples) - fade_out:
            v *= (len(samples) - 1 - i) / fade_out
        out.append(max(-32768, min(32767, int(round(v * 32767)))))
    return out


def write_wav(path, pcm):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(struct.pack("<%dh" % len(pcm), *pcm))


def cues():
    """The cue files' names, read from Cue.kt's enum: one entry a line, up to the `;` that ends them."""
    names = []
    for line in CUE_KT.read_text().split("enum class Cue(", 1)[1].splitlines():
        if line.strip() == ";":
            break
        m = re.match(r"^    ([A-Z][A-Z_]*)(?:\([^)]*\))?,?$", line)
        if m:
            names.append(m.group(1).lower())
    return names


def render(bank):
    recipes = BANKS[bank](None)  # for the names only
    wanted = cues()
    missing = [c for c in wanted if c not in recipes]
    extra = [c for c in recipes if c not in wanted]
    if missing or extra:
        sys.exit("%s: no recipe for %s; recipe for no cue: %s" % (bank, missing, extra))
    total = 0
    for name in wanted:
        # The same seed every render: the noise is the same noise, so a render is byte for byte repeatable.
        rng = random.Random(zlib.crc32(("%s/%s" % (bank, name)).encode()))
        pcm = finish(BANKS[bank](rng)[name]())
        path = OUT / bank / ("%s.wav" % name)
        write_wav(path, pcm)
        total += path.stat().st_size
        print("  %-12s %5.0f ms %6.1f kB" % (name, 1000 * len(pcm) / SR, path.stat().st_size / 1000))
    print("%s: %d samples, %.0f kB" % (bank, len(wanted), total / 1000))


if __name__ == "__main__":
    for bank in sys.argv[1:] or BANKS:
        if bank not in BANKS:
            sys.exit("no bank %s; the banks are %s" % (bank, sorted(BANKS)))
        print(bank)
        render(bank)
