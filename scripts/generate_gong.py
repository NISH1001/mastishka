#!/usr/bin/env python3
"""Synthesize meditation gong/bell tones into app/src/main/res/raw/.

Pure standard-library additive synthesis (no numpy): a set of inharmonic partials with
per-partial exponential decay produces a bell-like timbre. Higher partials decay faster (as
real bells do), leaving a warm sustained hum. A short strike transient is layered at the onset.

Three sizes are produced, differing in fundamental pitch, ring length and decay:
  - gong_small.ogg   bright, higher-pitched, shorter ring
  - gong_medium.ogg  balanced "centered" ring  (the app default)
  - gong_large.ogg   deep temple gong, long ring

Output is OGG Vorbis (a free codec, ~10x smaller than WAV). Requires ffmpeg on PATH.

Run:  python3 scripts/generate_gong.py
"""
import math
import os
import struct
import subprocess
import tempfile
import wave

SAMPLE_RATE = 44100

# Vorbis quality for the final res/raw clips. q6 is near-transparent (the decaying bell
# tail is where lossy codecs warble) while keeping each clip ~40-55 KB vs ~0.5 MB as WAV.
OGG_QUALITY = "6"

# (frequency ratio, relative amplitude, base decay rate 1/s). Inharmonic, bell-like.
PARTIALS = [
    (0.50, 0.30, 0.6),
    (1.00, 1.00, 0.7),
    (1.19, 0.55, 1.1),
    (1.71, 0.45, 1.6),
    (2.00, 0.40, 1.8),
    (2.74, 0.30, 2.6),
    (3.00, 0.22, 2.9),
    (3.76, 0.18, 3.6),
    (4.07, 0.14, 4.2),
    (5.12, 0.10, 5.5),
]

# name -> (fundamental Hz, duration s, decay_scale). Higher decay_scale = shorter ring.
VARIANTS = {
    "gong_small": (392.0, 5.0, 1.45),
    "gong_medium": (261.0, 6.5, 1.0),
    "gong_large": (165.0, 8.0, 0.72),
}


def sample(t: float, fundamental: float, decay_scale: float) -> float:
    value = 0.0
    for ratio, amp, decay in PARTIALS:
        freq = fundamental * ratio
        # tiny per-partial detune gives the shimmering "beat" of a struck bell
        detune = 1.0 + 0.0008 * math.sin(2 * math.pi * 0.7 * t * ratio)
        env = math.exp(-decay * decay_scale * t)
        value += amp * env * math.sin(2 * math.pi * freq * detune * t)
    # brief noisy-ish strike transient (first ~25 ms) using stacked high sines
    if t < 0.025:
        strike = math.exp(-90 * t) * (
            math.sin(2 * math.pi * 2600 * t) + 0.6 * math.sin(2 * math.pi * 5300 * t)
        )
        value += 0.5 * strike
    return value


def render(out_path: str, fundamental: float, duration: float, decay_scale: float) -> None:
    n = int(SAMPLE_RATE * duration)
    raw = [sample(i / SAMPLE_RATE, fundamental, decay_scale) for i in range(n)]
    peak = max(abs(v) for v in raw) or 1.0

    frames = bytearray()
    for i, v in enumerate(raw):
        t = i / SAMPLE_RATE
        fade = 1.0 if t < duration - 0.5 else max(0.0, (duration - t) / 0.5)
        s = (v / peak) * 0.92 * fade
        frames += struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767))

    # Synthesize to a temporary WAV, then transcode to OGG Vorbis with ffmpeg. Vorbis is a
    # fully free codec (no MP3/AAC patent concerns) and is what ships in res/raw.
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    try:
        with wave.open(wav_path, "w") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SAMPLE_RATE)
            w.writeframes(bytes(frames))
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", wav_path,
             "-c:a", "libvorbis", "-q:a", OGG_QUALITY, out_path],
            check=True,
        )
    finally:
        os.remove(wav_path)

    size_kb = os.path.getsize(out_path) / 1024
    print(f"Wrote {os.path.basename(out_path)} ({size_kb:.0f} KB, {duration:.1f}s, {fundamental:.0f} Hz)")


def main() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.normpath(os.path.join(here, "..", "app", "src", "main", "res", "raw"))
    os.makedirs(out_dir, exist_ok=True)

    # Remove the old single-gong file if present (replaced by sized variants).
    legacy = os.path.join(out_dir, "gong.wav")
    if os.path.exists(legacy):
        os.remove(legacy)
        print("Removed legacy gong.wav")

    for name, (fundamental, duration, decay_scale) in VARIANTS.items():
        render(os.path.join(out_dir, f"{name}.ogg"), fundamental, duration, decay_scale)


if __name__ == "__main__":
    main()
