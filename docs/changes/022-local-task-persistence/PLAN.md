# Change 022 — Local Task Persistence Foundation — PLAN

Governance: **HIGH-ASSURANCE**  
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**  
Expected branch: `chg-022-local-task-persistence`

This plan follows the accepted current Task domain and existing focused Room
store conventions. It does not re-plan the project or the paused runtime-AI
track.

## Preflight findings

- Clean `main` and expected PLN-001 commit were verified before branch
  creation; the requested branch is now checked out.
- `Task.kt` currently contains only `TaskId`, exact `title`,
  `TaskSpace.PERSONAL`/`TRABAJO`, and nullable `LocalDate` `dueDate`.
- `DomainIds.kt` already contains the stable `TaskId` value class.
- Existing stores use focused interfaces, Room DAOs/entities/mappers, direct
  suspend reads, `withReadTransaction`/`withWriteTransaction` where needed,
  deterministic SQL ordering, and explicit cancellation propagation.
- The database is Room version 5 with nine existing durable entities and
  tracked schemas 1–5. The v5 schema identity used by the migration fixture
  will be read from the tracked schema rather than invented.
- No current Task store, Task entity, Task DAO, Task UI, or Task execution
  wiring exists.
- No UI/UX change is intended, so the visual reference is not an affected
  source and no screenshot is required.

## Implementation sequence

1. Create and maintain the Change 022 SPEC/PLAN/TASKS package and point
   `docs/ACTIVE_WORK.md` at the change.
2. Add a minimal `TaskStore` contract with `save`, `getById`, and `readAll`.
   Use upsert semantics only because stable-ID save/update is a required
   persistence proof; do not add delete/completion operations.
3. Add `TaskEntity` and explicit `Task.toEntity`/`TaskEntity.toDomain`
   mapping. Store space as `TaskSpace.name`, due date as an ISO date string,
   and null as SQL null. Do not modify `Task.kt` or `DomainIds.kt` unless
   compilation proves a strictly necessary compatibility change.
4. Add `TaskDao` with suspend upsert, stable-ID lookup, and deterministic
   `id ASC` enumeration. Keep DAO types private to the local data boundary.
5. Add `RoomTaskStore`, using existing read/write transaction conventions and
   rethrowing `CancellationException`. Let save complete without inventing a
   new broad Result hierarchy; reads return domain values.
6. Add the Task entity/DAO accessor to `QuickAsideDatabase`, move the version
   to 6, register an explicit `MIGRATION_5_6` that only creates `tasks`, and
   leave migrations 1–5 SQL untouched. Do not add fallback migration behavior.
7. Wire one lazy app-scoped `TaskStore` through `QuickAsideApplication` if
   the existing store convention requires it. Do not expose the DAO or add UI
   consumers.
8. Add deterministic JVM tests for mapping, spaces, dates/null, unknown
   persisted enum failure, and stable-ID upsert intent. Add focused Android
   tests for fresh v6 persistence, both spaces, due-date/null, deterministic
   listing, upsert, close/reopen, cancellation/failure semantics, and a real
   on-disk v5→v6 migration fixture.
9. Generate schema 6 through the existing Room/KSP mechanism. Inspect it and
   verify schemas 1–5 are byte-for-byte unchanged and only the Task table was
   added.
10. Run focused checks first, then the applicable Room instrumentation suite,
    full JVM suite, assemble, lint, diff check, schema inspection, and final
    repository-state evidence. Reconcile TASKS and ACTIVE_WORK with actual
    results only.

## Migration fixture approach

The focused Android migration test will create a unique on-disk SQLite file
with the exact tracked schema-5 table definitions and v5 Room identity hash,
set `PRAGMA user_version = 5`, and insert representative rows with real
foreign-key relationships:

- Text and corrected Voice Captures;
- Mandado/Compras definitions, a Mandado session/item, and a Compras item;
- a Note linked to a Capture;
- a StructuredLog with multiple fields linked to a Capture;
- an Action Ledger entry with ordered mutation child rows linked to a Capture.

It will snapshot old table/index definitions and domain-visible values before
opening the fixture via `QuickAsideDatabase.create`. After production
migration it will assert version 6, all old values/relationships, the exact
Task-table structure, empty Task rows before insertion, and successful Task
save/read after migration. It will close/reopen and repeat the Task read. The
fixture will clean up only its unique named database.

## Verification and stop signals

- Run focused Task JVM tests after mapping/store changes.
- Run the focused Task Room/migration test on Oppo CPH2791 / Android 16.
- If `adb` fails initially, perform at most one targeted server restart/
  re-enumeration attempt, then report any remaining infrastructure block.
- Run the existing Room/database instrumentation suite, full JVM tests,
  assemble, lint, schema inspection, diff check, status, and diff stat.
- Stop at the first real product/root validation error; fix it and rerun from
  the smallest relevant gate.
- Do not weaken tests, alter historical schema JSON, add speculative fields,
  or broaden into UI, AI, execution, Google, reminders, sync, or backup work.

## Expected implementation files

Production:

- `app/src/main/java/com/edu/quickaside/application/tasks/TaskStore.kt`
- `app/src/main/java/com/edu/quickaside/data/local/TaskEntities.kt`
- `app/src/main/java/com/edu/quickaside/data/local/TaskDao.kt`
- `app/src/main/java/com/edu/quickaside/data/local/RoomTaskStore.kt`
- `app/src/main/java/com/edu/quickaside/data/local/QuickAsideDatabase.kt`
- `app/src/main/java/com/edu/quickaside/QuickAsideApplication.kt`

Tests/schema:

- focused JVM Task mapping tests;
- focused Android Task persistence/migration tests;
- `app/schemas/com.edu.quickaside.data.local.QuickAsideDatabase/6.json`;
- stale current-version assertions in existing migration tests, if needed.

No UI, dependency, provider, executor, Google, reminder, or historical
change-package files are expected to change.

## Implementation closeout evidence

- Production implementation and Android-test sources compile successfully.
- Focused Task JVM mapping tests: 5/5 passed.
- Full `:app:testDebugUnitTest`: 109/109 passed with zero skips, failures, or
  errors.
- `:app:assembleDebug` and `:app:lintDebug`: BUILD SUCCESSFUL.
- Focused `TaskPersistenceDatabaseTest` passed 5/5 on the authorized Oppo
  CPH2791 / Android 16 device. The applicable `com.edu.quickaside.data.local`
  connected suite passed 84/84 with zero skips, failures, or errors. A broader
  all-app connected run executed 209 tests and reported one pre-existing
  `MandadoUiTest` UI timeout outside Change 022's scope.
- Generated schema 6 has identity hash
  `bcce741653c243cf77bf048e39e33f9a`, version 6, the nine unchanged v5
  entities plus `tasks`, and no Task foreign keys or indexes. Schemas 1–5
  compare unchanged to the starting commit.
- `git diff --check` is clean. The final engineering verdict remains with
  independent review.
