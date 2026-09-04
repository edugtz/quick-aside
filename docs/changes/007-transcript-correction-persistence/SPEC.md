# Change 007 — Transcript Correction Persistence — SPEC

Governance: **HIGH-ASSURANCE**  
Status: **IN PROGRESS**

## Objective

Persist an optional corrected transcript for Voice Captures while retaining the
original speech-recognition result unchanged and recoverable forever.

Example:

```text
original transcript:  comprar leche manana
corrected transcript: Comprar leche mañana
```

This change adds domain and persistence support only. Transcript-editing UI is
deferred to Change 008.

## In scope

- Add an optional Voice-only `transcriptCorrection` to the pure-Kotlin
  `Capture` domain model.
- Derive an effective Voice transcript as the correction when present, or the
  original transcript otherwise.
- Add one nullable `corrected_transcript` column to the existing `captures`
  table.
- Move `QuickAsideDatabase` from version 1 to version 2.
- Add an explicit Room 1→2 migration that only adds the nullable column.
- Preserve and track the historical version-1 Room schema unchanged, and
  export the version-2 schema.
- Add the smallest focused correction write boundary addressed by `CaptureId`.
- Make missing Capture, Text Capture, blank correction, and persistence failure
  deterministic non-success outcomes.
- Make Memoria read the effective Voice transcript when a correction exists,
  without adding editing controls, badges, or receipt changes.
- Add deterministic unit, Android Room, migration, close/reopen, and capture
  regression coverage using dedicated test databases.

## Out of scope

- Transcript-editing UI, Edit buttons, correction badges, or receipt redesign.
- Interpretation/routing correction, `CapturePlan`, AI, STT changes, raw audio,
  or recognition metadata.
- Text Capture corrections or any mutable Text Capture semantics.
- New tables, indexes, generic CRUD/repository abstractions, delete/prune
  behavior, backup/export changes, sync, reminders, Google APIs, or new
  navigation destinations.
- Changes to historical Change 001–006 documentation packages.

## Domain contract

`Capture.originalInput` remains the immutable original input. For a Voice
Capture, `transcriptCorrection: String?` is optional and preserves the exact
user-provided correction when non-null. A correction is valid only when it is
non-blank and the Capture is Voice. Text Captures retain their existing
behavior and cannot receive a correction.

The domain exposes both values without conflating them:

- original: `CaptureInput.Voice.originalTranscript`;
- correction: `Capture.transcriptCorrection`;
- effective/current: `Capture.effectiveTranscript`.

`effectiveTranscript` resolves to `transcriptCorrection` when present and to
the original transcript otherwise. No automatic normalization or correction is
performed during Voice submission; a new Voice Capture always stores a null
correction.

## Persistence contract

The v2 Capture table retains the v1 columns exactly:

- `id` TEXT primary key;
- `kind` TEXT;
- `original_text` TEXT;
- `captured_at_epoch_millis` INTEGER.

It adds only:

- `corrected_transcript` TEXT NULL.

The existing `original_text` column is never renamed, overwritten, or rewritten
by this change. Existing v1 rows migrate with `corrected_transcript = NULL` and
all original IDs, kinds, text/transcripts, and timestamps unchanged.

The database uses an explicit `Migration(1, 2)` and does not configure
`fallbackToDestructiveMigration` or any other destructive migration path.

## Correction write boundary

The focused correction operation:

1. rejects a blank correction before touching the database;
2. loads one Capture by `CaptureId`;
3. returns a distinct missing outcome when no row exists;
4. returns a distinct non-Voice outcome for Text Captures;
5. updates only `corrected_transcript` for the Voice row;
6. rereads the row and returns both original and corrected values;
7. returns a failure outcome if persistence or post-write verification fails.

Cancellation is propagated. A second correction replaces only the correction
field and never changes the original input.

## Read behavior and UX reference

The affected existing reference surface is Memoria in
`docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`, interpreted with
`docs/UX_UI_REFERENCE.md`. The implementation keeps the four management
destinations and the existing calm, structured history cards. When a Voice
Capture has a correction, Memoria renders the effective transcript; the
original remains available through the domain/persistence read model for the
future editor. Text Captures continue to render their original text.

No new user interaction is introduced in this change, so no additional visual
evidence beyond regression verification is required for a new UI surface. The
effective-transcript behavior is covered by deterministic read tests.

## Acceptance scenarios

1. A newly submitted Voice Capture persists its exact original transcript and a
   null correction.
2. A v1 Text row migrates to v2 with every v1 value unchanged and a null
   correction.
3. A v1 Voice row migrates to v2 with every v1 value unchanged and a null
   correction.
4. A corrected Voice Capture read returns its original transcript, correction,
   and effective transcript separately.
5. A blank correction is rejected without changing the row.
6. A Text Capture correction is rejected without changing the row.
7. A missing Capture is not reported as successfully corrected.
8. A persistence failure is returned as a failure, not success.
9. A second correction changes only `corrected_transcript`.
10. Existing text and Voice submission paths still work against v2.
11. A migrated database survives close/reopen and retains the same values.
12. The version-1 schema JSON remains unchanged; version 2 adds only the
    nullable correction column.
13. No destructive migration fallback is configured and Room/tool versions,
    AI/STT dependencies, and backup exclusions remain unchanged.

## Required verification evidence

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check` and `git status --short`
- Inspection of both tracked Room schema JSON files.
- Real v1 SQLite fixture opened through the production v2 database and exact
  row-value verification after migration and close/reopen.
- Explicit reporting of any unavailable or failed gate; no final PASS claim.

## Stop conditions

Stop and report if Room migration behavior is unclear, schema v1 is rewritten,
table recreation becomes necessary, an existing row fails exact verification,
destructive migration is required, or preserving original input would require a
broader Capture redesign.

## Authority

Do not merge or push. The final engineering verdict belongs to the independent
reviewer/orchestrator.
