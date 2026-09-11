# Change 023 — Task Completion State Foundation — PLAN

Governance: **HIGH-ASSURANCE**  
Status: **COMPLETE — REVIEW PASS**
Expected branch: `chg-023-task-completion-state`

This plan follows the completed Change 022 local Task persistence boundary. It
does not re-plan the project or resume the paused runtime-AI track.

## Preflight findings

- Clean `main` was verified at `8aace0eca7c35c086d3f23db4d8ca91301ad9e1d`.
- The requested Change 023 branch is checked out without a commit.
- `Task` currently has stable ID, exact title, Personal/Trabajo space, and
  nullable date-only due date.
- `TaskEntity` currently stores the four corresponding values; timestamp
  fields elsewhere use nullable/non-nullable epoch milliseconds.
- `TaskStore` already exposes stable-ID save/upsert, lookup, and deterministic
  reads; no new method is justified.
- `QuickAsideDatabase` is version 6 with explicit migrations through 5→6,
  generated schema 6, and no destructive fallback.
- `TaskPersistenceDatabaseTest` already provides a real on-disk v5 fixture and
  focused Room conventions. It will be adapted to a tracked-schema v6 fixture
  so this change directly proves 6→7 rather than re-proving 5→6.
- The change has no UI/UX impact, so the canonical image is not an affected
  source and no screenshot is required.

## Implementation sequence

1. Create this SPEC/PLAN/TASKS package and point `docs/ACTIVE_WORK.md` at
   Change 023.
2. Add `completedAt: Instant? = null` after the existing defaulted `dueDate` so
   existing positional/named constructor calls remain compatible.
3. Add nullable `completed_at_epoch_millis` to `TaskEntity` and map it with the
   established epoch-millis convention. Keep `TaskStore` unchanged.
4. Move `QuickAsideDatabase` from version 6 to 7 and register an explicit
   `MIGRATION_6_7` that only adds the nullable completion column. Leave
   migrations 1→6 untouched.
5. Extend JVM mapping coverage for pending/completed states, exact Instant,
   due-date independence, and unchanged spaces/title/ID. Document why a
   malformed completion-value parser test is not applicable to the typed
   nullable INTEGER representation.
6. Extend the focused Android test with fresh-v7 lifecycle coverage and a real
   on-disk v6 fixture containing both legacy Task spaces plus unrelated durable
   rows. Assert schema/table/index preservation, migration defaults, stable-ID
   transitions, no duplicates, and close/reopen behavior.
7. Update only stale current-version assertions in existing Room tests, when
   compilation/test inspection shows they assert the current database version.
8. Generate schema 7 through Room/KSP. Compare schemas 1–6 byte-for-byte with
   the starting tree and inspect schema 7 for the exact one-column Task delta.
9. Run focused checks first, then the applicable device/data.local suite, full
   JVM tests, assemble, lint, diff check, and final repository evidence. Update
   TASKS and ACTIVE_WORK only with actual evidence.

## v6 migration fixture approach

The focused Android migration test will create a unique on-disk SQLite file
using the exact tracked schema-6 table/index definitions and identity hash
`bcce741653c243cf77bf048e39e33f9a`. It will set `PRAGMA user_version = 6` and
insert:

- a Personal Task with null due date;
- a Trabajo Task with an ISO date-only due date;
- Text and corrected Voice Captures;
- Mandado/Compras definitions, a Mandado session/item, and a Compras item;
- a Note linked to a Capture;
- a StructuredLog with multiple fields linked to a Capture;
- an Action Ledger entry with an ordered mutation child row.

The test snapshots unrelated table/index definitions and the pre-migration
four-column Task definition, opens the fixture through production
`QuickAsideDatabase.create`, and proves version 7, unchanged legacy values,
pending defaults, the exact five-column Task definition, and preservation of
all unrelated data. It then completes one migrated Task, closes/reopens, saves
it as reopened, and closes/reopens again to prove both transitions persist
without a duplicate row.

## Expected implementation files

Production:

- `app/src/main/java/com/edu/quickaside/domain/tasks/Task.kt`
- `app/src/main/java/com/edu/quickaside/data/local/TaskEntities.kt`
- `app/src/main/java/com/edu/quickaside/data/local/QuickAsideDatabase.kt`

Tests/schema:

- `app/src/test/java/com/edu/quickaside/data/local/TaskEntityMappingTest.kt`
- `app/src/androidTest/java/com/edu/quickaside/data/local/TaskPersistenceDatabaseTest.kt`
- stale current-version assertions in existing Room tests, if required;
- `app/schemas/com.edu.quickaside.data.local.QuickAsideDatabase/7.json`.

No TaskStore interface expansion, UI, dependency, provider, executor, Google,
reminder, sync, or historical change-package edits are expected.

## Verification and stop signals

- Run the focused JVM mapping test first after the domain/mapping change.
- Run focused Task instrumentation, including the real v6→v7 fixture, on
  CPH2791 / Android 16 when available.
- If `adb` fails initially, perform at most one targeted
  `adb kill-server`, `adb start-server`, `adb devices -l` recovery attempt.
- Run the applicable existing Room/database connected suite, full JVM tests,
  assemble, lint, schema comparison, diff check, status, and diff stat.
- Stop at the first product/root validation error; fix it and rerun the
  smallest relevant gate. Do not weaken assertions or disable tests.
- Do not commit, push, merge, or release.

## Independent review closeout

Verdict: **PASS** — `BLOCKER 0`, `MAJOR 0`, `MINOR 0`.

The independent review confirmed the recorded implementation and verification
evidence. No additional implementation or verification gates are introduced by
this closeout.
