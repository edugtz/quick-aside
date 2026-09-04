# Change 007 — Transcript Correction Persistence — PLAN

## Preflight

- Confirm the Change 007 branch and clean working tree.
- Read the accepted project, architecture, roadmap, acceptance, naming, and
  UX contracts plus Changes 003, 005, and 006.
- Inspect the existing Capture domain, entity/mapping, DAO, database builder,
  writer/reader, submission path, exported v1 schema, and current tests.
- Confirm Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, and the existing backup-rule
  exclusions remain unchanged.

## Implementation approach

1. Extend `Capture` with a nullable Voice-only correction and derived effective
   transcript, validating blank and non-Voice corrections at the domain edge.
2. Extend `CaptureEntity` with only the nullable
   `corrected_transcript` column and preserve explicit domain/persistence
   mapping for Text and Voice.
3. Add one DAO update operation that returns the affected-row count; keep the
   existing insert and read operations unchanged.
4. Add explicit `MIGRATION_1_2` using `ALTER TABLE ... ADD COLUMN` and register
   it in the production Room builder. Do not add destructive fallback.
5. Add a focused application correction boundary and Room implementation that
   validates inputs, distinguishes missing/Text/failure, updates only the new
   column, and rereads the corrected row.
6. Wire the production correction boundary from `QuickAsideApplication` for
   the future editor without adding UI.
7. Update the existing Memoria read card to show the effective Voice transcript
   while retaining original data in the domain object.
8. Generate and inspect schema v2, confirming schema v1 is byte-for-byte
   unchanged and v2 adds one nullable column only.
9. Add JVM tests for domain/mapping/submission/correction result behavior and
   Android tests for a real v1 fixture migration, exact values, correction
   updates, failure cases, close/reopen, and v2 capture regressions.

## Migration fixture approach

Room 3's migration API is available, but the project does not need a second
testing framework. Android instrumentation will create a dedicated on-disk
SQLite file with the exact exported v1 table, `room_master_table`, identity
hash, and `user_version = 1` using the bundled SQLite driver. The test then
opens that file with `QuickAsideDatabase.create`, allowing the production
1→2 migration and Room schema validation to run.

The fixture will contain known Text and Voice rows. After opening v2, tests will
verify IDs, kinds, original text/transcripts, timestamps, nullable correction,
row count, schema version, and column metadata. The same migrated file will be
closed and reopened through the production database and checked again.

## Test isolation

- Every Android test uses a unique or dedicated named database.
- Production `quick_aside.db` is never used for fixtures and is never deleted
  by tests.
- Fixture cleanup closes the Room database/raw connection and deletes only the
  dedicated test file.
- Existing Change 003–006 tests remain in place and are not rewritten as
  historical packages.

## Verification sequence

- Run targeted JVM tests after domain/mapping changes.
- Run the migration/correction Android tests after database wiring.
- Generate and inspect schemas.
- Run all required unit, assemble, lint, connected instrumentation, diff, and
  status gates.
- Report exact device/emulator evidence and any blocked gate without claiming
  PASS.

## Stop signals

Stop on the first real migration validation failure. Do not weaken assertions,
rewrite schema history, add a destructive fallback, or broaden the feature to
UI editing/interpretation correction.
