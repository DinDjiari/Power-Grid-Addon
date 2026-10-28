#!/usr/bin/env python3
"""
Erzeugt die Sounds von pghv (assets/pghv/sounds/*.ogg).

Quellen:
  * Klicks/Schalter: echte Aufnahmen mechanischer Schalter aus "UI Audio" von Kenney (CC0, siehe
    kenney/LICENSE-Kenney-UI-Audio.txt), hier nachbearbeitet: tiefer gestimmt, Gehäuseresonanz
    (Unterputzdose, Kasten), mehrere Aufnahmen übereinandergelegt.
  * Lüfter-Laufgeräusch und Sicherungsknall: physikalisch modelliert (Luftrauschen + Blattfrequenz +
    Motorbrummen 100 Hz; Lichtbogen-Knall + Funken), da keine freie Aufnahme erreichbar war.
Aufruf: python3 tools/sounds/make_sounds.py   (braucht numpy, scipy, ffmpeg mit libvorbis)
"""
import os
import subprocess
import tempfile

import numpy as np
import scipy.io.wavfile as wav
from scipy import signal

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "kenney")
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "pghv", "sounds")


# ---------------------------------------------------------------- helpers
def load(name):
    sr, x = wav.read(os.path.join(SRC, name + ".wav"))
    x = x.astype(np.float64)
    if x.ndim > 1:
        x = x.mean(axis=1)
    x /= np.max(np.abs(x)) + 1e-9
    assert sr == SR
    # trim leading silence (keep 2 ms before onset)
    on = np.argmax(np.abs(x) > 0.02)
    x = x[max(0, on - int(0.002 * SR)):]
    # trim tail below -55 dB
    env = np.abs(x)
    last = np.where(env > 10 ** (-55 / 20))[0]
    return x[: last[-1] + 1] if len(last) else x


def pitch(x, factor):
    """factor < 1 = tiefer und länger (wie langsamere Wiedergabe)."""
    up, down = int(round(1000 / factor)), 1000
    g = np.gcd(up, down)
    return signal.resample_poly(x, up // g, down // g)


def body(x, freq, decay, amount):
    """Gehäuseresonanz: Faltung mit einer kurz ausklingenden Schwingung."""
    t = np.arange(int(decay * 6 * SR)) / SR
    ir = np.sin(2 * np.pi * freq * t) * np.exp(-t / decay)
    ir /= np.sum(np.abs(ir))
    y = signal.fftconvolve(x, ir)[: len(x) + len(t)]
    return np.pad(x, (0, len(y) - len(x))) + amount * y / (np.max(np.abs(y)) + 1e-9)


def room(x, length=0.12, level=0.08, seed=1):
    """Kleiner Raum/Kasten: kurzer, dichter Nachhall."""
    rng = np.random.default_rng(seed)
    t = np.arange(int(length * SR)) / SR
    ir = rng.standard_normal(len(t)) * np.exp(-t / (length / 5))
    b, a = signal.butter(2, 4000 / (SR / 2), "low")
    ir = signal.lfilter(b, a, ir)
    ir /= np.sum(np.abs(ir)) / 8
    y = signal.fftconvolve(x, ir)
    return np.pad(x, (0, len(y) - len(x))) + level * y / (np.max(np.abs(y)) + 1e-9)


def lowpass(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), "low")
    return signal.lfilter(b, a, x)


def highpass(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), "high")
    return signal.lfilter(b, a, x)


def mix(*parts):
    """parts: (signal, delay_s, gain)"""
    n = max(int(d * SR) + len(s) for s, d, g in parts)
    y = np.zeros(n)
    for s, d, g in parts:
        o = int(d * SR)
        y[o:o + len(s)] += g * s
    return y


def finish(x, peak_db=-3.0, fade=0.01, hp=40):
    x = highpass(x, hp)
    n = int(fade * SR)
    if len(x) > n:
        x[-n:] *= np.linspace(1, 0, n)
    x = x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)
    return x


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        w = os.path.join(tmp, name + ".wav")
        wav.write(w, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", w, "-ac", "1", "-ar", str(SR),
                        "-c:a", "libvorbis", "-q:a", "5", os.path.join(OUT, name + ".ogg")], check=True)
    print(f"{name:24s} {len(x) / SR:5.2f} s")


# ---------------------------------------------------------------- recorded switches
def rocker(src, p, seed):
    # EU-Wippschalter in der Unterputzdose: tiefer, Kunststoff-Wippe + Hohlraum der Dose
    x = pitch(load(src), p)
    x = body(x, 900, 0.006, 0.35)
    x = body(x, 230, 0.010, 0.25)
    x = lowpass(x, 9000)
    return finish(room(x, 0.08, 0.05, seed), -4, hp=110)


def push(src, p, seed):
    x = pitch(load(src), p)
    x = body(x, 1100, 0.005, 0.3)
    x = lowpass(x, 8000)
    return finish(room(x, 0.06, 0.04, seed), -6, hp=130)


def breaker(src, p, seed, extra=None):
    # Leitungsschutzschalter/FI auf der Hutschiene: harter Schnappmechanismus im Metallkasten
    x = pitch(load(src), p)
    if extra is not None:
        x = mix((x, 0, 1.0), (pitch(load(extra), p * 0.9), 0.004, 0.6))
    x = body(x, 650, 0.008, 0.4)
    x = body(x, 160, 0.015, 0.3)
    return finish(room(x, 0.15, 0.10, seed), -3, hp=80)


def main_switch(src, seed):
    # großer roter Hauptschalter im Hausanschlusskasten: schwere Mechanik
    x = pitch(load(src), 0.6)
    t = np.arange(int(0.12 * SR)) / SR
    thump = np.sin(2 * np.pi * (95 - 200 * t) * t) * np.exp(-t / 0.03)
    x = mix((x, 0, 1.0), (thump, 0.012, 0.5))
    x = body(x, 420, 0.012, 0.4)
    return finish(room(x, 0.2, 0.12, seed), -2)


# ---------------------------------------------------------------- modelled
def fuse_blow(seed):
    rng = np.random.default_rng(seed)
    n = int(0.45 * SR)
    t = np.arange(n) / SR
    # Lichtbogen-Knall
    burst = rng.standard_normal(n) * np.exp(-t / 0.018)
    burst = signal.lfilter(*signal.butter(2, [600 / (SR / 2), 7000 / (SR / 2)], "band"), burst)
    # Druckstoß im Sicherungsgehäuse
    thump = np.sin(2 * np.pi * (110 - 150 * t) * t) * np.exp(-t / 0.035)
    # Funken: kurze Knackser, dichter am Anfang
    sparks = np.zeros(n)
    for _ in range(14):
        o = int(rng.exponential(0.06) * SR) + int(0.008 * SR)
        if o >= n - 300:
            continue
        L = rng.integers(40, 260)
        sparks[o:o + L] += rng.standard_normal(L) * np.exp(-np.arange(L) / (L / 4)) * rng.uniform(0.2, 0.7)
    sparks = highpass(sparks, 2500)
    snap = load("switch29")
    x = mix((burst, 0, 1.0), (thump, 0, 0.8), (sparks, 0, 0.5), (snap, 0, 0.35))
    return finish(room(x, 0.18, 0.12, seed), -1)


def fan_loop(seconds=2.0, seed=7):
    """Nahtlose Schleife: Rauschen mit Zufallsphasen direkt im Spektrum (zyklisch), alle Töne auf dem
    Frequenzraster 1/seconds. Bei 100 %: 4 Blätter x 25 U/s -> Blattfrequenz 100 Hz, Motor 100 Hz."""
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    f = np.fft.rfftfreq(n, 1 / SR)
    fs = np.maximum(f, 1)
    # Luftrauschen: Breitband, Schwerpunkt ~ 500 Hz, nach oben mit ~ 1/f abfallend
    mag = 1 / np.sqrt(fs) * np.exp(-((np.log(fs / 500)) ** 2) / 2.2)
    mag += 0.25 / np.sqrt(fs) * np.exp(-((np.log(fs / 3000)) ** 2) / 0.8)  # Zischen an den Blattkanten
    mag[f < 30] = 0
    spec = mag * np.exp(1j * rng.uniform(0, 2 * np.pi, len(f)))
    air = np.fft.irfft(spec, n)
    air /= np.max(np.abs(air))
    t = np.arange(n) / SR
    bpf = 100.0
    # Blattdurchgänge modulieren das Rauschen (Wummern)
    chop = 1 + 0.18 * np.sin(2 * np.pi * bpf * t) + 0.06 * np.sin(2 * np.pi * 2 * bpf * t + 1.0)
    air *= chop
    tones = (0.10 * np.sin(2 * np.pi * bpf * t)
             + 0.05 * np.sin(2 * np.pi * 2 * bpf * t + 0.4)
             + 0.025 * np.sin(2 * np.pi * 3 * bpf * t + 1.3)
             + 0.03 * np.sin(2 * np.pi * 300 * t + 0.7)        # Motor (Nutenfrequenz)
             + 0.006 * np.sin(2 * np.pi * 2400 * t))            # Lager
    x = air + tones
    # langsame Schwankung (Turbulenz), periodisch über die Schleife
    x *= 1 + 0.05 * np.sin(2 * np.pi * t / seconds) + 0.03 * np.sin(2 * np.pi * 3 * t / seconds + 2)
    return x / np.max(np.abs(x)) * 10 ** (-4 / 20)


def main():
    write("wall_switch_on1", rocker("switch20", 0.88, 1))
    write("wall_switch_on2", rocker("switch23", 0.86, 2))
    write("wall_switch_off1", rocker("switch21", 0.80, 3))
    write("wall_switch_off2", rocker("switch25", 0.78, 4))
    write("wall_button_press", push("switch13", 0.80, 5))
    write("wall_button_release", push("switch12", 0.85, 6))
    write("breaker_on1", breaker("switch16", 0.75, 7))
    write("breaker_on2", breaker("switch22", 0.75, 8))
    write("breaker_off1", breaker("switch18", 0.70, 9))
    write("breaker_off2", breaker("switch31", 0.70, 10))
    write("breaker_trip", breaker("switch18", 0.62, 11, extra="switch16"))
    write("module_insert", breaker("switch3", 0.80, 12))
    write("fuse_insert", breaker("switch7", 0.72, 13))
    write("main_switch_on", main_switch("switch33", 14))
    write("main_switch_off", main_switch("switch34", 15))
    write("fuse_blow1", fuse_blow(16))
    write("fuse_blow2", fuse_blow(17))
    write("cooling_fan_loop", fan_loop())


if __name__ == "__main__":
    main()
