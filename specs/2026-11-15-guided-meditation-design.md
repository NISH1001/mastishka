# Guided meditation (Android) — design

**Date:** 2026-11-15
**Status:** built on `feature/guided-meditation` (v0.6.0); tried on a Pixel 9

## Intent

Sit with a teacher's recorded guidance playing inside Mastishka instead of only the silent
timer. First use: S. N. Goenka's English Anapana and Vipassana recordings; other teachers
later. Requested for two people's daily practice, and it must be low-friction for anyone else
who installs the app.

## Goals

- A "Guided" mode next to today's silent sit, on the same setup screen.
- Recordings are one tap away: no folders to set up, no files to manage for the built-in ones.
- A guided sit ends up in history, Health Connect and Drive sync like any other sit.
- Silent sits behave exactly as they do today.

## Non-goals

- No streaming: a sit never depends on the network once it has begun.
- No compression or re-encoding of recordings.
- No playlists, seeking or playback speed. No macOS or web changes.

## Anti-goals

- **Audio in the APK or the repo.** The recordings are copyright Vipassana Research Institute
  and their notice forbids copying and redistributing them; this repo is public. The app
  ships names and links only.
- **Bypassing the old-student login.** Recordings dhamma.org serves only after login (the
  1-hour group sittings) must never be fetched by a hard-coded link. Each person gets them
  through their own login.
- Guidance audio cutting out when the screen turns off.

## Approaches considered

1. **Bundle (compressed) audio in the APK.** What was first asked for. Ruled out by the
   recordings' terms and the public repo. Would need written permission from dhamma.org.
2. **User's own files in a folder they pick.** Built first, then dropped: choosing a folder
   is too much friction for other people.
3. **Built-in catalog, downloaded on demand from the publisher, plus "add your own"
   (chosen).** The app lists recordings by name; choosing one downloads it once, straight
   from dhamma.org's public link, into app storage. Anything else is added from the user's
   own files. No audio is shipped, the app's size is unchanged, playback is offline.
   Known cost: only what the publisher serves publicly can be built in (Anapana, not
   Vipassana), and linking to a public file is more defensible than rehosting it but is not
   explicitly permitted by the notice.
4. **Stream on first play while downloading.** Considered and declined: a network hiccup
   would cut the guidance mid-sit, and the files are small (about 10 MB and 25 MB).

## Design

**Setup screen.** A two-segment switch "Silent | Guided" under the greeting, remembered
between launches. The Practice card is unchanged. In Silent, the "Sit duration" card shows
as today. In Guided it is replaced by a "Guided meditation" card:

- One row per recording, single choice. Built-in recordings first, then the user's own.
- A recording not yet on the phone shows its length, size and a download icon. Choosing it
  starts the download; the icon becomes a progress ring with the percentage. Once on the
  phone the row shows its length.
- "1-hour Vipassana group sitting (old students)" opens dhamma.org's old-student login in
  the browser. The app cannot fetch these itself (see anti-goals).
- "Add your own recording" picks an audio file; the app keeps its own copy and selects it.
- Long-press a recording that is on the phone to remove it (a built-in one can be
  downloaded again).

No explanatory text in the card: the rows speak for themselves.

**Begin button.** Disabled in Guided until the chosen recording is on the phone. While that
recording downloads, the button shows the progress ring and percentage in place of the play
icon. It does not start by itself when the download ends.

**Common settings stay on the same screen, below Begin sit** (no separate settings page).
The gong card's slider is "Volume" and applies to both the gong and the recording.

**Sit screen.** Appears immediately, as today: remaining time and End sit, which works at any
moment. A guided sit adds the recording's name and a Pause/Resume button.

**Behaviour.**
- The recording's length is the planned duration.
- No gong in Guided. When the recording ends the timer counts overtime silently.
- Audio continues with the screen off and follows headphones when connected.
- Pause stops audio and clock; paused time does not count toward the sit's total.
- A phone call, another player taking over, or headphones being unplugged pauses the sit.
  It is resumed by hand, never automatically.
- If the recording cannot be played, the sit carries on as a silent one (with the gong) and
  the sit screen says so.

**Volume (decided by the user: the slider is the control, nothing plays at max).**
- The slider follows a perceptual (logarithmic) curve, 40 dB wide, so low percentages are
  genuinely soft. The old linear scaling was the reported "too loud even at low %" bug.
  "Test gong" uses the same curve as the real gong.
- Gong: as before, the alarm stream is pinned while the gong rings and restored afterwards,
  so the phone's alarm volume does not change what the slider means.
- Recording: plays as media. The slider scales it on top of the phone's media volume, which
  the app never changes; the side buttons work during the sit.
- Tapping Begin in Guided warns first if the recording would be inaudible: slider at 0%,
  media volume at zero, or Do Not Disturb in total-silence mode. Other Do Not Disturb modes
  normally allow media; detection beyond total silence is not attempted.

**History.** The saved sit keeps the practice chip as its type and stores the recording's
title, shown on its history row as "Guided · <title>". Post-sit calmness, notes and Metta are
unchanged.

## Implementation notes

- Catalog: `GUIDE_CATALOG` in `data/Guided.kt` (id, title, public https link, approximate
  length, size). Adding a teacher or recording is adding an entry.
- Storage: `filesDir/guided/`. A catalog recording is `<id>.mp3`; a user-added one is
  `user-<name>`. Downloads go to a `.part` file and are renamed only when complete.
- Playback lives in `TimerService` with the platform `MediaPlayer`; no new library. The sit
  clock is `SitClock`, which counts only time spent running.
- The app gained the `INTERNET` permission, used only for these downloads.
- `Session.guide` (Room migration 4 → 5) and a `guide` field in the schema-1 sit record;
  readers ignore unknown fields, so no schema bump (see `ecosystem-protocol.md`).
- `-PidSuffix=.dev` builds a test copy that installs beside the real app with its own data.

## Not yet done

- "Add your own recording" has not been exercised with a real file on a phone.
- Getting an old-students-only recording into the app is still a manual path (browser or
  the Dhamma.org app, then add). An in-app dhamma.org page that saves the chosen recording
  straight into the list, using the person's own login, was proposed and is undecided.
- The Metta people list is not part of Drive sync (sits are); a reinstall restores it only
  from a manual export or a database copy.
