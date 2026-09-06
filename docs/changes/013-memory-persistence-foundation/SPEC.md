# Change 013 — Memory Persistence Foundation — SPEC

Governance: **HIGH-ASSURANCE**  
Status: **IN PROGRESS**

## Objective

Establish durable local Room persistence and focused application boundaries
for `Note` and `StructuredLog`, without adding their UI, search, AI
extraction, reminders, or routing behavior.

## In scope

- Add deterministic `createdAt` timestamps to `Note` and `StructuredLog`.
- Enforce Note text and StructuredLog field invariants at the domain and
  application boundary.
- Move the production Room database from version 3 to version 4.
- Add normalized `notes`, `structured_logs`, and
  `structured_log_fields` tables through an explicit 3→4 migration.
- Preserve the existing Capture and List tables and semantics exactly.
- Add non-cascading optional foreign keys from memory records to Captures.
- Add a focused `MemoryStore`/Room implementation with injectable ID and
  clock providers and deterministic creation outcomes.
- Add JVM mapping/domain tests and Android persistence, migration, atomicity,
  close/reopen, and v4 regression tests.
- Export and inspect schema 4 while preserving schemas 1, 2, and 3.

## Out of scope

- Notes or Structured Logs UI, editors, navigation, search, FTS, AI
  extraction, Capture→memory routing, reminders, Action Ledger, Undo,
  update/delete, export/archive, Google APIs, or domain-specific schemas.
- JSON/opaque persistence for StructuredLog fields.
- Generic `Repository<T>` abstractions or new production dependencies.
- Changes to historical Change 001–012 documentation packages or schemas.

## Domain contract

`Note` contains a stable `NoteId`, exact non-blank text, an optional
`CaptureId`, and required `createdAt: Instant`. Valid text is never trimmed or
normalized.

`StructuredLog` contains a stable `StructuredLogId`, a non-empty map of exact
key/value strings, an optional `CaptureId`, and required `createdAt: Instant`.
Blank keys and blank values are rejected. The shape remains general-purpose;
no fitness or other typed schema is introduced.

IDs and timestamps are supplied outside entities through focused injectable
providers. Production providers use UUIDs and system time; tests provide
deterministic values. No compatibility constructor uses `Instant.EPOCH`.

## Persistence contract

Version 4 keeps the existing `captures`, `list_definitions`, `list_sessions`,
and `list_items` tables unchanged and adds only:

- `notes(id, text, source_capture_id, created_at_epoch_millis)`;
- `structured_logs(id, source_capture_id, created_at_epoch_millis)`;
- `structured_log_fields(structured_log_id, field_key, field_value)` with a
  composite primary key of `(structured_log_id, field_key)`.

The memory-to-Capture and field-to-parent foreign keys use `NO ACTION` on
update/delete. There is no cascade deletion and no destructive migration
fallback. Source-Capture validation happens inside the write transaction so a
missing supplied ID returns a deterministic non-success result instead of a
foreign-key exception escaping the boundary.

StructuredLog fields are normalized child rows, read in `field_key ASC`, and
reconstructed only through explicit entity/domain mapping. Unknown or corrupt
persistence fails visibly rather than fabricating a domain object.

## Application boundary

`MemoryStore` exposes only the foundation operations:

- `createNote`, `readRecentNotes`, and `getNote`;
- `createStructuredLog`, `readRecentStructuredLogs`, and `getStructuredLog`.

Creation returns explicit outcomes for saved, invalid input, missing source
Capture, and persistence failure. `CancellationException` propagates. There
are no update or delete APIs.

Reads are deterministic and newest-first:

- Notes: `created_at_epoch_millis DESC, id DESC`;
- Structured Logs: `created_at_epoch_millis DESC, id DESC`.

## Migration acceptance

`MIGRATION_3_4` creates only the three memory tables and required indexes. A
dedicated real version-3 on-disk fixture matching tracked schema 3 contains a
Text Capture, corrected Voice Capture, Mandado definition/session/items, and a
Compras item. Opening it through the production v4 database must preserve all
old values, set `user_version` to 4, leave memory tables empty, and retain all
data after close/reopen.

Schemas 1, 2, and 3 must remain byte-for-byte unchanged; schema 4 may add only
the intended memory structures. No UI changes are allowed.

## Evidence required

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check`, `git status --short`, and diff statistics.
- Real v3→v4 migration and close/reopen evidence on a dedicated database.
- Explicit reporting of every unavailable or failed gate; this report must
  not declare the final engineering verdict.

## Authority and stop conditions

Do not merge or push. Stop and report if migration requires rebuilding an old
table, schemas 1–3 change, non-destructive foreign keys are insufficient,
StructuredLog atomicity cannot be implemented with a Room transaction, or the
scope starts requiring UI, AI, reminders, search, or Action Ledger behavior.

