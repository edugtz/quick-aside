# Change 003 — Local Capture Persistence — PLAN

## Current-state inspection

- Confirm Change 001/002 artifacts remain historical and untouched.
- Confirm the existing Capture model and typed IDs are pure Kotlin.
- Inspect the current Gradle/version catalog, manifest, and both backup XML
  formats before adding persistence.
- Keep the user-visible UI and navigation unchanged.

## Room 3 compatibility preflight

- Add only the Room 3.0.2 runtime/compiler/plugin, KSP, and SQLite driver
  coordinates needed for the Capture database.
- Use KSP 2.3.6 for the repository's AGP 9 built-in-Kotlin baseline;
  do not select a version solely by numeric recency.
- Configure KSP and the Room Gradle Plugin without changing AGP, Gradle,
  Kotlin, Compose compiler, SDK, Java, or min/target versions.
- Compile the generated Room sources before implementing the full persistence
  slice. If this fails and a core toolchain upgrade would be required, stop
  with the exact compatibility report instead of switching to Room 2.

Preflight result: Room 3.0.2 code generation compiled with KSP 2.3.6 and
SQLite 2.7.0 on the existing baseline. No
`android.disallowKotlinSourceSets=false` compatibility bridge or manual
generated-source registration is present.

## Persistence package layout

Use a focused `com.edu.quickaside.data.local` boundary:

- Capture entity and kind discriminator;
- Capture DAO;
- `QuickAsideDatabase`;
- explicit entity/domain mapping;
- test-only database construction where needed.

Do not introduce repositories or a generic persistence framework.

## Schema-v1 approach

Define one Room database at version 1 with one Capture entity. Use Capture ID
as the primary key, a non-null original text column, a deterministic string
kind column, and epoch-millisecond timestamp storage. Export the schema to
the tracked `app/schemas/` directory through the Room Gradle Plugin. Use
`BundledSQLiteDriver` and the stable filename `quick_aside.db`; do not add a
destructive migration fallback.

## Mapping approach

Flatten the sealed `CaptureInput` into `kind` plus one `original_text` value.
Map `TEXT` to `CaptureInput.Text` and `VOICE` to `CaptureInput.Voice` with
explicit exhaustive code. Convert `CaptureId.value` and `Instant.toEpochMilli`
at the persistence boundary and reverse those conversions on read.

## DAO scope

Expose only a suspend insert and suspend lookup by Capture ID. Use
`OnConflictStrategy.ABORT` (or the Room 3 equivalent) so duplicates surface
as failures instead of silently replacing user data. Do not add deletes,
updates, or generic CRUD for symmetry.

## Backup-rule update

Add scoped database-domain exclusions to both `backup_rules.xml` and
`data_extraction_rules.xml`, covering cloud backup and device transfer. Keep
the manifest references unchanged and document that this is a temporary
privacy boundary pending the explicit M5 recovery contract.

## Test approach

- JVM tests cover text/voice mapping and deterministic timestamp/kind/ID
  conversion.
- Android instrumentation tests use Room's actual database implementation,
  not mocked DAOs or Robolectric.
- Use a named file in the instrumentation context, insert a text and voice
  Capture, verify duplicate rejection, close the database, reopen the same
  file, read it back, and delete the test database in cleanup.
- Verify the exported schema has only the Capture table and expected columns.

## Verification contract

Run the smallest relevant checks as implementation proceeds, then all required
gates: unit tests, debug assemble, debug lint, connected Android tests,
`git diff --check`, schema inspection, and `git status --short`. Do not claim
success for an unrun gate. Report the actual emulator/device used.

## Split/stop signals

- Stop if Room 3.0.2 cannot compile with the existing toolchain after at most
  two focused fixes for the same root error; report attempted versions and the
  likely Room 2.8.4 compatibility fallback without switching silently.
- Stop on the first real validation failure and do not weaken tests or delete
  evidence to make a gate pass.
- Split or ask for direction if implementation starts requiring UI changes,
  unrelated schemas, repositories, DI, migrations, sync, or backup/export
  product behavior.
