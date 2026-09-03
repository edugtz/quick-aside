# Change 003 — Local Capture Persistence — TASKS

## Preflight

- [x] Confirm expected branch and repository state.
- [x] Read project, architecture, roadmap, acceptance, naming, and Change 002
      guidance.
- [x] Resolve exact Room 3.0.2, KSP, SQLite, and Room Gradle Plugin versions:
      Room 3.0.2, KSP 2.3.6, SQLite 2.7.0.
- [x] Prove Room 3/KSP generation compiles with the existing toolchain.

## Implementation

- [x] Configure the minimal Room 3/KSP/SQLite dependency and plugin set.
- [x] Add the v1 Capture entity and DAO only.
- [x] Add `QuickAsideDatabase` with `quick_aside.db`, version 1, and no
      destructive migration fallback.
- [x] Add explicit domain/persistence mapping for text and voice captures.
- [x] Preserve the pure-Kotlin domain package boundary.

## Schema export

- [x] Configure Room Gradle Plugin schema export.
- [x] Generate and inspect the tracked version-1 schema JSON.
- [x] Confirm no future-domain tables are present.

## Android persistence tests

- [x] Add deterministic text and voice database round-trip tests.
- [x] Add duplicate-ID non-overwrite test.
- [x] Add close/reopen persistence test with named test database.
- [x] Clean up the named test database afterward.
- [x] Record actual device/emulator evidence: `CPH2791 - 16`.

## Backup/privacy

- [x] Exclude the database from cloud Auto Backup in `backup_rules.xml`.
- [x] Exclude the database from device transfer and cloud backup in
      `data_extraction_rules.xml`.
- [x] Verify manifest references remain correct.

## Verification

- [x] Run `./gradlew :app:testDebugUnitTest`.
- [x] Run `./gradlew :app:assembleDebug`.
- [x] Run `./gradlew :app:lintDebug`.
- [x] Verify Android database behavior on `CPH2791 - 16`: 5 instrumented
  tests passed before the KSP integration correction; the subsequent
  build-integration-only rerun stalled twice during UTP package install
  without producing a test failure. Independent review accepted the
  existing runtime evidence because persistence and test sources were
  unchanged.
- [x] Run `git diff --check`.
- [x] Inspect `app/schemas/com.edu.quickaside.data.local.QuickAsideDatabase/1.json`.
- [x] Report `git status --short` and the applicable diff stat.

## Final independent review

- [x] Final independent review; verdict: PASS_WITH_NOTES.