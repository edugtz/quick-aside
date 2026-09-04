# Change 007 — Transcript Correction Persistence — TASKS

## Change package and preflight

- [x] Confirm the expected Change 007 branch and clean repository state.
- [x] Read the required project, architecture, roadmap, acceptance, naming,
      UX, and historical Change 003/005/006 guidance.
- [x] Inspect the canonical v3 UI reference.
- [x] Confirm the current Capture model, Room v1 schema, database wiring,
      writer/reader, submission path, and tests.
- [x] Confirm Room 3.0.2 / KSP 2.3.6 / SQLite 2.7.0 remain the baseline.

## Domain and persistence

- [x] Add Voice-only nullable transcript correction and effective transcript.
- [x] Add nullable `corrected_transcript` to the Capture entity/mapping.
- [x] Move `QuickAsideDatabase` to version 2.
- [x] Add and register explicit 1→2 migration.
- [x] Preserve schema v1 and generate/inspect schema v2.
- [x] Add the focused CaptureId correction write boundary.
- [x] Wire the boundary without adding editing UI.
- [x] Render effective Voice transcript in existing Memoria history.

## Tests and migration evidence

- [x] Add unit coverage for domain validation, mapping, Voice submission, and
      correction results.
- [x] Add a real v1 SQLite fixture migration test with known Text and Voice
      rows.
- [x] Verify exact migrated IDs, kinds, original values, timestamps, null
      corrections, row count, and schema version.
- [x] Verify correction, second correction, blank/Text/missing/failure behavior.
- [x] Verify existing text/Voice submission against v2.
- [x] Verify migrated database close/reopen durability.
- [x] Verify no destructive migration fallback exists.

## Verification

- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Run `./gradlew :app:connectedDebugAndroidTest`; complete run on
      `CPH2791 - 16` passed all 36 tests. Two post-verification retries after
      a naming-only cleanup were blocked before test execution by the same UTP
      split-APK installation timeout; no further retries per stop rule.
- [x] Run `git diff --check`.
- [x] Report `git status --short` and `git diff --stat`.
- [x] Record all unavailable/failed gates accurately; do not declare PASS.

## Scope and authority

- [x] Do not modify historical Change 001–006 packages.
- [x] Do not change backup exclusions, AI/STT dependencies, or Room/KSP
      versions.
- [x] Do not merge or push.
- [x] Leave final engineering verdict to the independent reviewer/orchestrator.
