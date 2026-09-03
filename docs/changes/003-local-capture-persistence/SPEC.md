# Change 003 — Local Capture Persistence — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Introduce the first durable local persistence slice for Quick Aside: a
Room-backed version-1 database that stores the existing `Capture` domain
model without changing the user-visible UI or making the domain depend on
Android or Room.

## In scope

- Room 3.0.2 and the smallest compatible KSP/SQLite toolchain configuration,
  without upgrading the existing Android/Kotlin toolchain.
- `QuickAsideDatabase`, version 1, with only the Capture table.
- A Capture DAO for insert and lookup by ID, with duplicate IDs rejected.
- Explicit persistence/domain mapping for text and voice captures.
- Version-controlled Room schema export under `app/schemas/`.
- Deterministic mapper/JVM tests and real Android database tests.
- Close/reopen durability evidence using the actual Room database.
- Explicit Android cloud-backup and device-transfer exclusions for the
  database until the dedicated backup/recovery milestone exists.

## Out of scope

- Persistence for lists, tasks, notes, structured logs, reminders, or the
  action ledger.
- Repositories, generic database abstractions, DI/Hilt, UI capture, voice/STT,
  AI, CapturePlan, Google APIs, sync, reminders, search/history UI,
  backup/export product features, archive, encryption, or raw audio.
- Changes to the historical Change 001 or Change 002 packages.

## Persistence/domain separation

`com.edu.quickaside.domain` remains pure Kotlin. Room annotations, entities,
DAOs, database configuration, and mapping code live under
`com.edu.quickaside.data.local`. Persistence code may map to domain types;
domain types must not import Room, Android, SQLite, serialization, or provider
types.

## Capture persistence contract

The v1 table stores exactly the durable information already present in the
domain model:

- Capture ID as the primary key;
- deterministic kind discriminator (`TEXT` or `VOICE`);
- non-null original textual input/transcript;
- `capturedAt` as epoch milliseconds.

`CaptureInput.Text` and `CaptureInput.Voice` must both reconstruct with their
original text and kind. Raw audio is not stored. Inserts use abort semantics;
an existing Capture ID must not be silently overwritten. No delete operation
is part of this change.

The database file is named `quick_aside.db`, uses version 1, and has no
destructive migration fallback. There is no migration from an earlier schema;
future version changes must provide explicit migration evidence.

## Backup/privacy decision

Because captures are personal content, the database is explicitly excluded
from both Android cloud Auto Backup and device transfer in the legacy
`backup_rules.xml` and Android 12+ `data_extraction_rules.xml` formats. This
is intentional until Quick Aside has an explicit, user-controlled
backup/recovery security contract. The M5 export/backup feature and encryption
architecture remain deferred.

## Acceptance scenarios

1. A text Capture round-trips domain → persistence → database → persistence →
   domain with the same ID, kind, original text, and timestamp.
2. A voice Capture round-trips with its transcript and `VOICE` kind intact.
3. Inserting a duplicate Capture ID fails without replacing the original row.
4. A named test database retains a Capture after close and reopening the same
   database, then is cleaned up.
5. The exported v1 schema contains only the intended Capture table and its
   four columns; no future-domain tables exist.
6. Both backup-rule formats exclude the database domain.
7. The UI remains unchanged.

## Evidence required

- Room 3/KSP code-generation compatibility with the existing baseline.
- `app/schemas/1.json` inspection.
- `./gradlew :app:testDebugUnitTest`.
- `./gradlew :app:assembleDebug`.
- `./gradlew :app:lintDebug`.
- `./gradlew :app:connectedDebugAndroidTest` on an actual device/emulator,
  including close/reopen output.
- `git diff --check` and accurate working-tree/diff-stat reporting.

