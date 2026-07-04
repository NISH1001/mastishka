# nishparadox ecosystem protocol

**Version 1 — 2026-07-03**

The contract that lets nishparadox apps interchange data and trigger each other
without a server, a daemon, or any Google/API code. Two mechanisms only:

1. **Commands** travel between apps on the same device via URL schemes.
2. **Data** travels between devices as plain files in a folder that Google Drive syncs.

An app "joins the ecosystem" by implementing the relevant parts of this document.
There is no other integration surface.

## Participants

| App | Platform | Id | Role |
|---|---|---|---|
| Mastishka | Android | `nishparadox.mastishka` | practice: sits (timer, gong, history) |
| Mastishka | macOS (planned) | `dev.nishparadox.mastishka` | practice: sits |
| Mastishka Web | browser | nishparadox.com/mastishka | transient sit, stores nothing; fallback target |
| Vishrama | macOS | `dev.nishparadox.vishrama` | rhythm: work-break reminders |
| Expensalizer | Android + Python | `com.nishparadox.expensalizer` | expenses; **legacy-path participant** (see below) |

Future products join by implementing this doc.

## 1. URL verbs (same-device commands)

### `mastishka://sit`

Ask Mastishka (macOS) to begin a sit.

```
mastishka://sit?practice=<slug>&minutes=<int>&autostart=0|1&source=<app>
```

- `practice`: lowercase slug — `vipassana`, `anapana`, `metta`, `meditation`.
  Unknown slug → receiver's default practice.
- `minutes`: positive integer; missing/invalid → receiver's default duration.
- `autostart=1`: skip setup, start the countdown immediately.
- `source`: sender's short name (e.g. `vishrama`). Presence of a known source
  is what arms the completion callback.
- `prompt`: percent-encoded free text, the sender's own words shown on the sit
  screen (e.g. vishrama's break prompt "Anapana — observe your breath").
  Blank → treated as absent. Display-only; never stored in the sit record.
- A command received **while a sit is running is ignored** — nothing may
  interrupt a live sit.

### `vishrama://sitCompleted`

Optional callback, fired by Mastishka when a sit that arrived with
`source=vishrama` is saved.

```
vishrama://sitCompleted?startedAt=<epoch-ms>&totalMillis=<int>
```

Vishrama uses it to mark the break *completed* rather than skipped, and logs its
own event to its own log. Failure to open is silently ignored by the sender.

### Fallback rule

Before opening a `mastishka://` URL, a sender MUST check a handler exists
(macOS: `NSWorkspace.urlForApplication(toOpen:)`); if none, open
`https://nishparadox.com/mastishka` instead.

## 2. Canonical sit record (schema 1)

One sit = one JSON object. This is exactly the `sessions[]` element of the
Mastishka Android backup format (`android/app/src/main/java/nishparadox/mastishka/data/Backup.kt`),
which is the reference implementation.

```json
{
  "startedAt": 1751500000000,
  "plannedMillis": 600000,
  "totalMillis": 694000,
  "calmness": 4,
  "people": ["ma", "buwa"],
  "notes": "",
  "type": "Anapana",
  "hrAvg": 0, "hrMin": 0, "hrMax": 0,
  "hrSeries": ""
}
```

| Field | Type | Meaning |
|---|---|---|
| `startedAt` | epoch ms | **identity key** — two records with equal `startedAt` are the same sit |
| `plannedMillis` | int | intended duration |
| `totalMillis` | int | actual duration incl. overtime |
| `calmness` | int | 1–5; `0` = unset |
| `people` | string array | metta recipients (may be empty) |
| `notes` | string | free text |
| `type` | string | practice name, default `"Meditation"` |
| `hrAvg`/`hrMin`/`hrMax` | int | heart rate summary; `0` = absent |
| `hrSeries` | string | comma-separated bpm samples; `""` = absent |

Rules: writers emit **all** fields (defaults allowed); readers tolerate missing
fields (apply the defaults above) and **ignore unknown fields**. A reader may
reject only a record/envelope whose declared schema is *newer* than it knows.

## 3. Files & folders (cross-device data)

### Shared root

```
My Drive/nishparadox/
├── mastishka/
│   ├── sits/
│   │   ├── pixel-9-1a2b3c.jsonl ← written ONLY by the Pixel
│   │   ├── mac-m3max.jsonl      ← written ONLY by that Mac
│   │   └── mac-air.jsonl
│   └── backups/
│       └── mastishka-2026-07-03.json   ← dated full-backup snapshots (envelope)
└── vishrama/
    ├── events/YYYY-MM.jsonl
    └── settings.json
```

- The root name `nishparadox/` is the **recommended default**, not a hard rule.
  Apps with years of pre-existing data (expensalizer: its own Drive folder of
  yearly expense sheets, managed via the Sheets API) stay where they are and
  simply document their location. Each app's data root is user-configurable.
- One subfolder per product. **No app ever writes inside another app's folder.**
  Reading another app's files is allowed (e.g. a future stats app).
- Mark `nishparadox/` "available offline" in Drive clients so reads never
  block on the network.

### Sit files: one writer per device, append-only

- Path: `mastishka/sits/<device>.jsonl` — one canonical sit record (§2) per line.
- `<device>` is a stable id generated on first launch and persisted in that
  device's settings, then never recomputed. Seed it from something readable +
  something unique — Android: `Build.MODEL` slug + first 6 hex chars of
  `ANDROID_ID` (e.g. `pixel-9-1a2b3c`); macOS: hostname slug. True hardware ids
  are unavailable to Android apps (privacy); this doesn't matter — an id change
  merely starts a new file, and read-time union keeps history correct.
- **Single-writer rule:** a device appends only to its own file, never edits
  another's. This is what makes Drive's whole-file, last-writer-wins sync
  conflict-free by construction: no file ever has two writers.
- If the owning device must revise a past sit (edited notes), it rewrites its
  *own* file — still single-writer.

### Consolidation (read-time union)

There is no merge step and no combined file. Every reader, on every open:

1. read every `mastishka/sits/*.jsonl`;
2. parse line by line, **skipping corrupt/torn lines** (never fatal);
3. dedupe by `startedAt` (first seen wins);
4. sort by `startedAt` → that is the history.

Reference implementation of the dedupe: `MeditationViewModel.importData`.
Scale check: 20 years of twice-daily sits ≈ 15k lines ≈ 4 MB — milliseconds to
parse; caching is unnecessary.

### Backups

Full-snapshot envelope files (`{app, schema, versionName, exportedAt,
sessions[], people[]}` — see `Backup.kt`) written occasionally to
`mastishka/backups/`, dated filenames. Snapshots are single-writer by nature.
The `people` list travels **only** in backups, not in sit files (it is
device-local preference data).

## 4. Transport

The apps never talk to Google. **No OAuth clients, no Drive/Sheets API, no
tokens** (expensalizer is the deliberate exception, predating this doc). Files
are moved by Google's own clients:

- **macOS:** Google Drive for desktop mounts
  `~/Library/CloudStorage/GoogleDrive-<account>/My Drive/`. Apps resolve their
  data root as: user override → glob `GoogleDrive-*/My Drive/nishparadox/<app>` →
  local Application Support fallback. Beware dataless "online-only" placeholder
  files: reads must fail soft.
- **Android:** the Drive app exposes no filesystem folder, so apps use the SAF
  document picker: the user picks the target file/folder in Drive **once**, the
  app calls `takePersistableUriPermission` and thereafter rewrites its own
  file after every change (fail soft → re-prompt if the permission was revoked).
  Import = manual pick. Full automation option: a folder-mirror app (e.g.
  Autosync) mirroring a local folder ↔ Drive; the app just uses local files.

## 5. Event streams (future convention, not yet implemented)

If apps ever publish cross-app *activity feeds* (as opposed to §3 stores), the
line format is a versioned envelope:

```json
{"v":1, "app":"mastishka", "ts":"2026-07-03T22:05:11Z", "kind":"sit.completed", ...payload}
```

Nothing implements this today. Vishrama's existing `events/*.jsonl` is
app-private and unchanged; Mastishka sit files intentionally use bare schema-1
records so backup and sync stay mutually convertible.

## Change log

- **v1 (2026-07-03):** initial contract — sit schema 1, `mastishka://sit`,
  `vishrama://sitCompleted`, `nishparadox/` layout, single-writer jsonl,
  read-time union.
