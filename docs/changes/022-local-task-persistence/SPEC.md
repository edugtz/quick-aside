# Change 022 — Local Task Persistence Foundation — SPEC

Governance: **HIGH-ASSURANCE**  
Status: **COMPLETE — REVIEW PASS_WITH_NOTES**
Expected branch: `chg-022-local-task-persistence`

## Objective

Establish the smallest reliable Room-backed local persistence boundary for the
currently accepted Quick Aside `Task` domain. A task must survive a real
database round trip without depending on AI, Google, OAuth, network,
reminders, or UI. This is the local foundation for later Task application and
Google Tasks work; it does not redesign the Task domain for sync.

## Ground truth

- The verified clean starting point is `main` at
  `88787552fcbb327efd6a5bbf958337c89aefc298`, the PLN-001 merge baseline.
- Change 020 (typed CapturePlan + validator) and Change 021 (the
  provider-independent interpreter boundary) are accepted and complete.
- PLN-001 is merged and complete; runtime AI/provider implementation remains
  paused at the shared private runtime boundary.
- Room is currently version 5 with tracked schemas 1–5. Room 3.0.2, KSP
  2.3.6, SQLite 2.7.0, and the existing Android/toolchain versions are the
  current baseline.

## Current Task domain contract

The inspected current contract is pure Kotlin:

```text
Task
  id: TaskId
  title: String
  space: TaskSpace.PERSONAL | TaskSpace.TRABAJO
  dueDate: LocalDate?
```

`TaskId` is the existing stable value class. `title` is preserved exactly;
this change does not add validation, trimming, normalization, completion, or
other semantics. `TaskSpace` remains the existing two-value enum. `dueDate`
remains nullable and date-only; it is not a reminder time. Equality and
nullability remain the current data-class semantics.

## In scope

- A focused `TaskStore` application boundary for saving a Task, reading it by
  stable ID, and reading all persisted Tasks in deterministic order.
- A Room `TaskEntity`, `TaskDao`, explicit entity/domain mapping, and
  `RoomTaskStore` under `com.edu.quickaside.data.local`.
- Upsert/save semantics for the same stable Task ID so repeated saves update
  one logical row rather than create a duplicate, consistent with the
  requested durable save boundary.
- Room database version 6 with only the Task table and an explicit 5→6
  migration, if the table is introduced as planned.
- Explicit fresh-v6 creation and real on-disk v5→v6 migration evidence.
- App-scoped `TaskStore` wiring through `QuickAsideApplication` when required
  by the existing application-store convention.
- Deterministic JVM mapping tests and focused Android Room tests.
- Live evidence updates in this TASKS file and an implementation report.

## Out of scope

- AI providers/runtime, Codex, Luna, OpenCode Go, DeepSeek, network transport,
  prompts, credentials, temporal interpretation, PLAN/CLARIFY/UNSUPPORTED,
  or runtime UI.
- CapturePlan execution, `ActionExecutor`, CreateTask execution, and any
  `CaptureInterpreter` → `TaskStore` wiring.
- Google OAuth, Google Tasks APIs, external IDs, etags, sync tokens, dirty
  flags, account IDs, server timestamps, tombstones, outbox/retry,
  conflict/idempotency engines, or sync-specific schema fields.
- Reminders, completion/delete behavior, Calendar, backup/archive/export,
  UI/navigation/Compose changes, and unrelated refactors.
- Generic repository/framework abstractions or new production dependencies.
- Manual edits to historical schema JSON or historical migration SQL.

## Persistence contract

The Task table stores only the existing Task-domain information:

```text
tasks(
  id TEXT PRIMARY KEY NOT NULL,
  title TEXT NOT NULL,
  space TEXT NOT NULL,
  due_date TEXT NULL
)
```

The exact table/column names follow the repository's established snake_case
Room conventions. `TaskId.value` is stored as the primary-key string;
`TaskSpace.name` is stored as a deterministic string and unknown persisted
values fail visibly during mapping; `LocalDate` is stored as its ISO-8601
date-only string and null remains null. No provider-specific or future-sync
columns are introduced.

`save(task)` is an upsert keyed by the stable Task ID. `getById` returns the
current row or null. `readAll` returns every row in deterministic
`id ASC` order, matching the repository's stable-ID ordering convention for
definition enumeration. The boundary has no delete or completion operation.

## Database and migration acceptance

- `QuickAsideDatabase` moves from version 5 to version 6.
- `MIGRATION_5_6` creates only the new `tasks` table and is registered after
  the unchanged migrations 1→2→3→4→5.
- Historical migrations and durable v5 tables are not rebuilt, rewritten,
  normalized, or deleted.
- No destructive migration fallback is configured.
- Room generates and tracks schema 6; schemas 1–5 remain unchanged.
- A real on-disk v5 fixture, using the tracked v5 schema identity and
  `user_version = 5`, contains representative data from captures, list
  definitions/sessions/items, notes, structured logs/fields, and Action
  Ledger entries/mutations. Opening it through the production database must
  preserve those rows and relationships, create the Task table, report
  `user_version = 6`, and support Task save/read afterward.

## Acceptance scenarios

1. A Personal Task round-trips with stable ID, exact title, space, and null
   due date preserved.
2. A Trabajo Task round-trips with stable ID, exact title, space, and a
   non-null `LocalDate` due date preserved.
3. Entity/domain mapping preserves enum/date/null semantics and fails visibly
   for an unknown persisted space.
4. Saving the same stable Task ID updates one logical row without a duplicate
   and a subsequent read returns the updated value.
5. `readAll` is deterministic and includes both Personal and Trabajo tasks.
6. A fresh v6 database opens and persists Tasks.
7. A real v5 database migrates to v6 without losing representative existing
   data from each available v5 table family.
8. Task persistence works after the real migration and after close/reopen.
9. Store cancellation propagates; `CancellationException` is not converted to
   an ordinary failure. Unexpected ordinary write failures follow existing
   focused-store conventions.
10. No UI, AI, sync, reminders, ActionExecutor, Google, or speculative Task
    fields are introduced.

## Required evidence

- Focused Task JVM mapping/domain tests.
- Focused Task Room persistence test on the authorized Oppo CPH2791 / Android
  16 device when available.
- Real v5→v6 migration test with representative pre-existing data and
  post-migration Task persistence.
- Existing Room/database instrumentation suite as applicable.
- Full JVM unit suite, `assembleDebug`, `lintDebug`, and `git diff --check`.
- Inspection of generated schema 6, preservation of schemas 1–5, registered
  5→6 migration, no destructive fallback, and accurate status/stat output.

The builder report records evidence and pending/failed gates but does not
declare the independent engineering verdict.

## Implementation evidence

- Added the focused TaskStore/Room boundary, Task table, explicit 5→6
  migration, app wiring, mapping tests, and real v5 fixture test source.
- Focused Task JVM mapping tests passed 5/5; the full JVM suite passed 109/109.
- Android instrumentation sources compiled successfully. The focused Task
  suite passed 5/5 on the authorized Oppo CPH2791 / Android 16 device, and the
  applicable `com.edu.quickaside.data.local` suite passed 84/84 with zero
  skips, failures, or errors.
- Independent review verdict: `PASS_WITH_NOTES`; `BLOCKER 0`, `MAJOR 0`,
  `MINOR 0`.
- A broader all-app connected run completed 208/209 tests and observed one
  `MandadoUiTest` Compose timeout. `MandadoUiTest` and its fake list/action
  dependencies are unchanged by Change 022; the failure is outside the
  required Task/Room verification gates, and current evidence does not
  attribute it to Change 022. No baseline-main reproduction was performed, so
  it is not classified here as pre-existing.
- Schema 6 was generated by Room/KSP and inspected. Schemas 1–5 are unchanged;
  schema 6 adds only `tasks`.

## Authority and stop conditions

Do not commit, push, merge, or release. Stop and report
scope drift if this requires UI, AI/runtime, execution, Google, reminders,
sync metadata, a generic repository framework, historical schema mutation, or
destructive migration behavior.
