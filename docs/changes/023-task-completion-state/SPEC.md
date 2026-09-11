# Change 023 — Task Completion State Foundation — SPEC

Governance: **HIGH-ASSURANCE**  
Status: **COMPLETE — REVIEW PASS**
Expected branch: `chg-023-task-completion-state`

## Objective

Add the minimum provider-independent completion state required by the accepted
Quick Aside Task lifecycle. A Task must retain the instant at which it became
completed, and reopening must be represented by clearing that value. The
change extends the existing local Room persistence boundary without adding
application actions, UI, sync, or provider behavior.

## Ground truth

- The verified clean starting point is `main` at
  `8aace0eca7c35c086d3f23db4d8ca91301ad9e1d`, the Change 022 closeout.
- Change 022 is merged and complete. It established the local Room-backed
  Task boundary at database version 6.
- The current Task contract is `TaskId`, exact `title`,
  `TaskSpace.PERSONAL`/`TRABAJO`, and nullable date-only `LocalDate dueDate`.
- Room/KSP, timestamp, migration, and focused instrumentation conventions are
  already established in the repository.
- Runtime AI remains paused at the provider/runtime boundary.

## Domain contract

Extend the existing data class minimally:

```kotlin
data class Task(
    val id: TaskId,
    val title: String,
    val space: TaskSpace,
    val dueDate: LocalDate? = null,
    val completedAt: Instant? = null,
)
```

Semantics:

- `completedAt == null` means the task is pending and needs action.
- `completedAt != null` means the task was completed at that exact persisted
  instant.
- Setting `completedAt` to `null` reopens the task.

The stable ID, exact title, Personal/Trabajo space, nullable `LocalDate`
dueDate, constructor compatibility supplied by default values, and data-class
equality remain intact. `dueDate` remains date-only and independent from
completion time.

No separate persisted boolean, Google status, external ID, sync metadata,
reminder state, capture reference, or Action Ledger metadata is part of this
contract.

## In scope

- `Task.completedAt: Instant? = null`.
- Nullable epoch-millis persistence using the repository convention:
  `completed_at_epoch_millis INTEGER NULL`.
- Explicit Task entity/domain mapping for pending, completed, and reopened
  states.
- Room database version 7 and explicit `MIGRATION_6_7`.
- A real on-disk tracked-schema v6 fixture and post-migration lifecycle
  assertions.
- Focused JVM mapping/domain coverage and focused Room/device coverage.
- Generated schema 7 and live evidence in this package and
  `docs/ACTIVE_WORK.md`.

## Out of scope

- UI, navigation, Pendientes, screenshots, or visual redesign.
- `ReversibleTaskActions`, `Generic ActionExecutor`, CapturePlan execution,
  Undo, or Task Action Ledger behavior.
- Google OAuth, Google Tasks APIs, sync/outbox/conflict/idempotency metadata,
  external mappings, or provider-specific status values.
- Reminders, Calendar, backup/archive/export, or unrelated refactors.
- AI providers/runtime, prompts, temporal context, network, Codex, Luna,
  DeepSeek, OpenCode Go, or the private VPS gateway.
- Destructive migration fallback or edits to schemas 1–6.
- New production dependencies.

## Persistence contract

The version-7 Task table is:

```text
tasks(
  id TEXT PRIMARY KEY NOT NULL,
  title TEXT NOT NULL,
  space TEXT NOT NULL,
  due_date TEXT NULL,
  completed_at_epoch_millis INTEGER NULL
)
```

`Instant.toEpochMilli()` and `Instant.ofEpochMilli()` follow the existing
repository timestamp convention. A nullable SQLite INTEGER is represented in
the Room entity as `Long?`; no string/date parser is introduced for completion
state. Therefore malformed text parsing is not an applicable domain-mapping
path for this representation; typed Room/SQLite read failures must remain
visible rather than being converted into a pending task.

`TaskStore` remains unchanged. Existing stable-ID upsert semantics must persist
pending → completed and completed → reopened without creating duplicate rows.

## Migration acceptance

- Previous database version: 6.
- New database version: 7.
- `MIGRATION_6_7` is registered after the unchanged migrations 1→2→3→4→5→6.
- The migration performs only `ALTER TABLE tasks ADD COLUMN
  completed_at_epoch_millis INTEGER`.
- Existing v6 tasks migrate with `completedAt == null`.
- Existing v6 table/index definitions remain unchanged except for that
  intentional Task-column addition.
- No destructive migration fallback is configured.
- Room generates schema 7; schemas 1–6 remain byte-for-byte unchanged.

The real on-disk v6 fixture uses the tracked schema-6 identity hash and
`PRAGMA user_version = 6`. It contains at least one Personal task with a null
due date, one Trabajo task with a due date, and representative Capture, List,
Memory, and Action Ledger data. Production database opening must preserve all
legacy task fields and unrelated durable data, report version 7, and support
completion and reopening after migration and close/reopen.

## Acceptance scenarios

1. A pending Task maps and round-trips with `completedAt == null`.
2. A completed Task maps and round-trips with the exact millisecond Instant.
3. `dueDate` and `completedAt` survive independently.
4. Personal and Trabajo spaces remain unchanged.
5. A fresh v7 database persists pending and completed Tasks.
6. The same stable ID transitions pending → completed → reopened with one
   logical row and durable state after close/reopen.
7. A real v6 database migrates to v7; both legacy Tasks preserve ID, exact
   title, space, and dueDate, and both become pending.
8. Representative non-Task durable rows and old table/index definitions
   survive migration unchanged.
9. A migrated Task can be completed, read, reopened, and read as pending after
   another close/reopen.
10. No UI, AI/runtime, Google, sync metadata, reminders, actions, or speculative
    Task fields are introduced.

## Required evidence

- Focused JVM Task mapping/domain tests.
- Focused Task Room tests on the authorized Oppo CPH2791 / Android 16 device
  when available.
- A real on-disk v6→v7 migration test with representative legacy data.
- Applicable existing `com.edu.quickaside.data.local` instrumentation suite.
- Full `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, and
  `git diff --check`.
- Generated schema 7 inspection, byte-for-byte comparison of schemas 1–6,
  migration registration/fallback inspection, accurate status/stat output,
  and explicit pending/failed-gate reporting.

This builder report records evidence only. It does not assign the independent
engineering review verdict.

## Governance and authority

This is HIGH-ASSURANCE because it changes durable domain state and a Room
migration. Do not commit, push, merge, or release. The user retains those
authorities. Stop and report scope drift if the implementation requires UI,
AI/runtime, execution, Google, reminders, sync metadata, historical schema
mutation, or destructive migration behavior.

## Independent review closeout

Verdict: **PASS**

- BLOCKER 0
- MAJOR 0
- MINOR 0

Independent engineering review confirmed the implementation, migration,
schema, preservation, device, JVM, build, lint, and scope evidence recorded in
this change package.
