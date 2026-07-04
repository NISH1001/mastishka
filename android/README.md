# Mastishka — Android

The full Mastishka app: native Kotlin + Jetpack Compose, Room (history), DataStore
(settings), Health Connect (mindfulness sessions + heart rate), a foreground service +
wake lock so the gong fires reliably with the screen off, and automatic multi-device
Drive sync (one folder grant; sits travel as per-device jsonl — see
[`../specs/ecosystem-protocol.md`](../specs/ecosystem-protocol.md)).

`applicationId`: `nishparadox.mastishka` · `minSdk` 26 · `compileSdk` 36

- **Sit timer** — gong rings at zero without interrupting; overtime keeps counting.
- **Gong volume/sound** (Small / Medium / Large) with a test button.
- **Practice type** — Vipassana / Anapana / … (defaults to "Meditation").
- **Post-sit** — calmness check + notes (voice-to-text friendly); Metta Bhavana people
  for Vipassana sits.
- **Health Connect** — sits sync as meditation sessions; heart rate pulled back per sit
  (BPM graph with avg/min/max) when a band records to Health Connect.
- **Drive sync — one folder grant, then automatic (v0.4.1)** — History → ⋮ → *Set up
  Drive sync…* → grant the `Drive/nishparadox/mastishka` folder once, ever. The app
  finds/creates `sits/` inside, writes this phone's `sits/<device>.jsonl` after every
  change, and **auto-discovers every other device's sit file** (the Mac's today, any
  future device the day it appears) on each History open. No linking, no re-sync
  buttons; duplicates are impossible (sits are identified by start time).
- **Multi-device history** — device filter chips (**All** · per device) appear when
  history spans devices; foreign sits carry a small device tag, flow into Health
  Connect automatically, and the heart-rate refresh enriches them by time-correlation —
  a Mac sit gets your band's real BPM.
- **History** — every sit on-device (total/overtime, calmness, practice, people, notes),
  with multi-select delete. *Import data…* reads both `.json` backups and `.jsonl` sit
  files. One quiet filter row — date range (presets + custom picker) · device — applies
  to both tabs.
- **Insights** — a second History tab of hand-rolled charts (pure Compose Canvas, no
  chart library): stat tiles (sits · time sat · day streak · avg bpm), practice-time
  bars split planned/overtime (granularity follows the range), per-sit heart-rate trend
  with min–max band, **within-a-sit overlay** (every sit's heartbeat curve + the typical
  sit, with a "settles ≈N min" marker for where your heart typically finds its floor),
  calmness histogram, **calm × heart** dot plot (each rated sit's bpm grouped by rating,
  medians vs your baseline — plus a per-sit mind–body agreement score), and a calendar
  heatmap of minutes per day.

## Install (sideload)

Grab `mastishka.apk` from the [latest `v*` release](https://github.com/NISH1001/mastishka/releases),
copy to the phone (Drive/email/USB), tap it, allow the source. Upgrades install in place —
history is preserved (same signing key).

Or over adb:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
"$ANDROID_HOME/platform-tools/adb" install -r mastishka.apk
```

> First gong: grant the notification permission when prompted (it powers the ongoing-timer
> notification). The sit still runs without it.

## Build from source

```sh
export JAVA_HOME="$HOME/.jdks/jdk-17.0.19+10/Contents/Home"   # JDK 17
export ANDROID_HOME="$HOME/Library/Android/sdk"

# from this android/ directory:
./gradlew test                 # unit tests (backup + jsonl round-trips)
./gradlew :app:assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk

# Regenerate gongs only if you change ../scripts/generate_gong.py (needs ffmpeg)
python3 ../scripts/generate_gong.py
```

## Run on the Pixel 9 emulator

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
"$ANDROID_HOME/emulator/emulator" -avd pixel9 &
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell am start -n nishparadox.mastishka/.MainActivity
```

## Code map

```
app/src/main/java/nishparadox/mastishka/
  MainActivity.kt              # screen flow + notification permission
  MeditationViewModel.kt       # settings, DB, timer state, Drive sync, import/export
  service/TimerService.kt      # foreground clock, gong, wake lock, notification
  data/AppData.kt              # Room entities/DAOs/DB + DataStore settings
  data/Backup.kt               # schema-1 session JSON: backup envelope + sit-lines jsonl
  ui/Screens.kt                # Setup / Sit / Metta / History composables
  ui/Theme.kt                  # Compose Material3 theme
app/src/main/res/raw/gong_{small,medium,large}.ogg   # synthesized gongs
```

Releases: bump `version.properties`, PR to main, tag `v<version>`, attach the APK.
