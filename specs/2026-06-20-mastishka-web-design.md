# Mastishka Web — design

**Date:** 2026-06-20
**Status:** approved (brainstorm), pending implementation plan

## Overview

A standalone, minimal web version of Mastishka: a single self-contained page where
someone lands, picks a practice and a duration, and sits with a breathing green orb while
a gong rings without interrupting them. Nothing is stored — a purely transient sit. It is
the lowest-friction way to *try* Mastishka (just a URL, no install) and a contemplative
companion to the Android app, in the spirit of [anicca](https://nishparadox.com/anicca).

The Android app remains the full version (history, Health Connect, heart rate, metta).
The web version deliberately strips all of that away.

## Goals

- One calming page: land → choose → sit → done.
- Preserve Mastishka's **signature**: at zero the gong rings *without interrupting*, and
  the timer keeps counting **overtime** so you know how long you actually sat.
- A distinctive **liquid-gel breathing sphere** as the centerpiece — Mastishka's own art
  identity, not a copy of anicca.
- Works everywhere, especially on a phone, with no install and no build step.
- Shareable as a URL.

## Non-goals (explicitly out of scope)

- No history / saved sits, no localStorage, no accounts — fully transient.
- No Health Connect, no heart rate, no metta/people, no notes, no export/import.
- No backend, no analytics, no tracking.
- No frameworks, no bundler, no dependencies.

## Hosting & layout

- Single file: **`docs/index.html`** (HTML + CSS + JS all inline; the gong is synthesized
  in-browser, so there are no binary assets).
- **GitHub Pages**: Settings → Pages → Source `main` branch, `/docs` folder (one-time).
- Live at `https://nish1001.github.io/mastishka/`.
- `docs/screenshots/` (existing README images) is untouched and coexists; publishing
  `/docs` does not affect the README's image links on the repo page.

## Tech approach

- Vanilla HTML/CSS/JS in one file. Dark-only, app-green palette. Mobile-first responsive.
- Orb rendered with the **Canvas 2D API** (no WebGL dependency), using the gooey-metaball
  technique validated in brainstorming (see "Orb" below).
- Gong **synthesized at runtime via the Web Audio API**, ported from
  `scripts/generate_gong.py`. This is chosen over shipping the `.ogg`: OGG Vorbis fails on
  iPhone Safari, and synthesizing keeps the bell character identical, works on every
  browser, and keeps the repo to one file.
- **Screen Wake Lock API** keeps the screen awake during a sit, with graceful fallback when
  unavailable.

## Palette (from the app theme)

- Background: deep green-black radial `#17281f` → `#0a140f` (echoes app `#12211D`).
- Orb gel gradient stops: `#CEF2E4` (light mint highlight) → `#8FD3C2` (TealLight) →
  `#5E8C7F` (Teal) → `#26483E` (deep).
- Glow / shimmer accent: `rgba(143,211,194, …)` (`#8FD3C2`).
- Text (timer, labels): `#E9F4EF` at varying alpha.

## The flow — one page, three states

### 1. Setup
- Small title `मस्तिष्क · Mastishka`.
- Practice-type chips: **Vipassana / Anapana / Focused Breathing / Concentration**
  (default unselected → "Meditation"). Cosmetic only: it becomes the faded label under the
  timer. No logic attached (nothing is saved).
- Duration: preset chips **10 / 20 / 30 / 45 / 60 min** plus a compact **− / +** stepper for
  any custom length.
- Gong volume control (slider + mute), default level comparable to the app.
- A large **Begin sit** button. Tapping it is also the user gesture that unlocks/creates the
  AudioContext, so the gong can ring later.
- A small **GitHub icon** in a corner linking to `https://github.com/NISH1001/mastishka`.

### 2. Sit
- Full-screen: the breathing gel orb with drifting/returning bubbles, the **shimmer timer**
  counting down in the center, the faded practice label below it, and one unobtrusive
  **End** affordance.
- At **zero**: the gong rings softly and — *without interrupting* — the center switches from
  remaining time to a quiet **`+overtime`** that counts up while the orb keeps breathing.
- The person taps **End** whenever they like → Complete.

### 3. Complete
- A gentle line: "You sat for `MM:SS` · Mettā to you 🙏" (total time = planned + overtime).
- A **Sit again** button returns to Setup. Nothing is stored.

## The orb (validated in brainstorming)

A liquid-gel sphere rendered with Canvas 2D, app-green palette, on the dark green-black
background.

- **Breathing**: ~10 s cycle (`0.5 − 0.5·cos`), inhale expands and brightens, exhale
  contracts. Expansion is pronounced (radius ≈ 0.80→1.15 of base) — visibly swelling.
- **Surface**: a slightly wobbling outline (sum of sines) gives an organic, living edge;
  radial gel gradient with a top-left gloss highlight and a bottom inner shadow for a 3D
  read; a soft outer glow that pulses with the breath.
- **Bubbles**: small blobs emit from the surface and **fully detach** (the gooey bridge
  snaps as they pull away). Two kinds:
  - *returners* — arc out and fall back into the orb, merging at the surface;
  - *floaters* — get a tangential kick so they orbit/drift around the field, then slowly
    spiral inward to the center.
  A distance-based restoring pull keeps them bounded (they roam to mid-field, never fly
  off). ~55% floaters; pool capped (~7–10); gentle organic jitter.
- **Gooey rendering** (the metaball look): draw the main blob + bubbles in white on an
  offscreen canvas with a blur; harden the alpha by accumulating several draws onto a
  second offscreen → a merged silhouette with stringy bridges; fill a third offscreen with
  the gel gradient + gloss + inner shadow and mask it to the silhouette
  (`destination-in`); composite to screen with the outer glow.

## The timer — "liquid shimmer"

- Centered `MM:SS`, light weight, over a subtle dark radial scrim so it stays legible
  against the bright orb core.
- **Shimmer**: a slow band of light sweeps horizontally through the digits (animated
  gradient fill), so they look lit from within the same fluid — quietly alive even when the
  number isn't changing. A soft mint glow behind it.
- During overtime the center reads `+MM:SS`; a small label switches from a quiet
  "remaining" feel to "overtime" (kept minimal, not shouty).

## Gong (Web Audio port of `generate_gong.py`)

- Fill an `AudioBuffer` (44.1 kHz mono) using the same additive-synthesis `sample()` math:
  10 inharmonic partials `(ratio, amp, decay)`, per-partial exponential decay, a tiny
  per-partial detune for the struck-bell shimmer, and a ~25 ms high-frequency strike
  transient at onset; normalize to peak, apply a 0.5 s tail fade.
- Default to the **medium** variant (fundamental 261 Hz, 6.5 s, decay_scale 1.0). (Small =
  392 Hz/5.0 s/1.45, Large = 165 Hz/8.0 s/0.72 are available if a picker is ever wanted —
  not in v1.)
- Buffer is rendered once (on Begin, after the AudioContext is unlocked by the tap).
- Volume applied via a `GainNode`; mute supported.

## Behavior details & edge cases

- **Audio unlock**: browsers block audio until a user gesture — the Begin tap creates/
  resumes the AudioContext and renders the gong buffer, guaranteeing playback at zero.
- **Wake Lock**: request on entering Sit, release on Complete / leaving Sit; ignore failure.
- **Tab backgrounded**: rAF throttles when hidden; the sit clock is computed from
  timestamps (not frame counts), so elapsed/remaining stay correct on return. The gong is
  scheduled by wall-clock time, so it still fires (or fires immediately on return if the
  zero crossing was missed while hidden).
- **Reduced motion**: respect `prefers-reduced-motion` — keep a gentle breath but damp the
  bubble activity.
- **Responsive**: orb is sized from the smaller viewport dimension and centered; controls
  stack comfortably on a phone.

## Success criteria

- Open the URL on desktop and phone; pick a practice + duration; Begin.
- Orb breathes, bubbles detach/return/float and gravitate to center; timer shimmers and
  counts down.
- At zero the gong rings and the timer flips to counting overtime, orb unchanged.
- End → "You sat for …" → Sit again returns to Setup.
- No network calls, no storage written.
