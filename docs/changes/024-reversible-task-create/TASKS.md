# Change 024 — Reversible Task Create Action Foundation — TASKS

Governance: **HIGH-ASSURANCE**
Status: **COMPLETE — REVIEW PASS**
Expected branch: `chg-024-reversible-task-create`

This is a LIVE EXECUTION CHECKLIST. Mark `[x]` only after the work or evidence
exists. Failed, skipped, blocked, and unverified gates remain unchecked and are
described in the evidence log. This checklist records builder evidence; it
does not declare an independent engineering verdict.

## Change package and preflight

- [x] Verify clean starting worktree on `main`.
- [x] Fetch and verify `origin/main` and
      `origin/chg-023-task-completion-state` at
      `a3ef7e2c258146015875cb30069e5747b682b23a`.
- [x] Create/switch to `chg-024-reversible-task-create` without committing.
- [x] Read governing docs, Change 019 precedent, and Changes 022/023.
- [x] Confirm the requested `software-project-orchestrator` skill is not
      installed/callable; use repository governance and the CHG-024 brief as
      the explicit fallback.
- [x] Inspect Task domain/store/entity/DAO, Action Ledger, reversible-list
      implementation, database, application wiring, and focused tests.
- [x] Confirm Room version 7, schemas 1–7, no migration need, and current
      TaskStore stable-ID UPSERT behavior.
- [x] Confirm authorized CPH2791 Android 16 device is available.
- [x] Create CHG-024 SPEC, PLAN, and live TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` at CHG-024 with Status IN PROGRESS and
      Governance HIGH-ASSURANCE.

## Application boundary and Room implementation

- [x] Add narrow `ReversibleTaskActions` contract, typed create/Undo results,
      constants, and injectable Task ID provider.
- [x] Add strict Task insert with ABORT conflict behavior and exact-ID delete;
      preserve TaskStore UPSERT.
- [x] Implement atomic Task create with pending-only semantics and exact input
      preservation.
- [x] Persist exactly one Task, one Action Ledger parent, and one CREATE/task
      child mutation on successful create.
- [x] Implement exact-shape, target-checked atomic Undo with one-row proofs.
- [x] Preserve cancellation propagation and typed ordinary failures.
- [x] Wire exactly one app-scoped production implementation.
- [x] Confirm no UI/navigation, CapturePlan/executor, AI/runtime, Google/sync,
      reminders, complete/reopen action, serializer, migration, dependency, or
      unrelated refactor enters the diff.

## Focused tests

- [x] Add focused JVM result/contract tests.
- [x] Add unique-database `ReversibleTaskActionsDatabaseTest`.
- [x] Cover Personal/no-date and Trabajo/date create, exact title, pending
      state, generated ID, and exact ledger shape.
- [x] Cover blank title and generated-ID collision with unchanged existing
      Task and no ledger rows.
- [x] Cover Task/ledger-ID collisions, create child failure rollback, and
      create cancellation.
- [x] Cover successful exact-target Undo, unrelated preservation, ledger mark,
      and close/reopen durability.
- [x] Cover second Undo, missing entry/target, mismatch, unsupported operation
      or target type, unsupported version, and malformed child shape.
- [x] Cover Undo delete failure, mark failure, and cancellation rollback.
- [x] Keep existing TaskStore lifecycle and list reversible coverage green.

## Verification gates

- [x] Run focused JVM action tests: 3/3 passed; skips/failures/errors 0.
- [x] Run focused Task Room/instrumentation test on CPH2791 / Android 16:
      12/12 passed; skips/failures/errors 0.
- [x] Run applicable `com.edu.quickaside.data.local` connected suite: 97/97
      passed on CPH2791 / Android 16; skips/failures/errors 0.
- [x] Compile Android instrumentation sources as part of the focused connected
      run.
- [x] Run `./gradlew :app:testDebugUnitTest`: 114/114 passed; skips/failures/
      errors 0.
- [x] Run `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL.
- [x] Run `./gradlew :app:lintDebug`: BUILD SUCCESSFUL.
- [x] Compare schemas 1–7 byte-for-byte to the verified base; inspect Room
      version, migrations, and destructive-fallback status.
- [x] Run `git diff --check`, inspect `git status --short`, and inspect
      `git diff --stat`.
- [x] Prepare the builder evidence report without assigning an independent
      review verdict.
- [x] Independent engineering review complete: PASS (BLOCKER 0 / MAJOR 0 / MINOR 0).

## Evidence log

- Preflight: clean `main`; remote baseline and previous branch both verified at
  `a3ef7e2c258146015875cb30069e5747b682b23a`.
- Branch: `chg-024-reversible-task-create` created from verified `origin/main`
  without a commit.
- `git fetch origin` required the sandbox's repository-write authorization;
  fetch completed successfully.
- Room v7, explicit migrations through 6→7, generated schemas 1–7, existing
  stable-ID TaskStore UPSERT, and the list reversible-action precedent were
  inspected before implementation.
- Requested `software-project-orchestrator` skill was unavailable in the
  current catalog; repository `AGENTS.md` governance and the CHG-024 brief
  were used as the fallback.
- `adb devices -l` currently shows authorized Oppo CPH2791 / Android 16.
- Focused JVM `ReversibleTaskActionsContractTest`: 3/3 passed; skips, failures,
  and errors 0.
- The first focused connected invocation compiled the new Android test but
  exposed two test-fixture defects: one assertion expected two child rows for
  a one-mutation entry, and one trigger setup closed an unopened lazy database.
  Both were corrected without changing production code.
- After adding parent-ID collision coverage, one focused invocation exposed an
  `Int` versus `Long` assertion mismatch for a persisted epoch value. It was
  corrected without changing production code.
- Focused `ReversibleTaskActionsDatabaseTest`: 12/12 passed on Oppo CPH2791 /
  Android 16; skips, failures, and errors 0. Coverage includes create,
  Task/ledger collision no-overwrite, exact ledger shape, create
  rollback/cancellation, targeted Undo, durability, malformed ledger rejection,
  delete/mark rollback, and Undo cancellation.
- Applicable `com.edu.quickaside.data.local` connected suite: 97/97 passed on
  Oppo CPH2791 / Android 16; skips, failures, and errors 0. This includes the
  existing Task persistence/migration and reversible-list database suites.
- Full `./gradlew :app:testDebugUnitTest`: 114/114 passed; skips, failures, and
  errors 0.
- `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL.
- `./gradlew :app:lintDebug`: BUILD SUCCESSFUL.
- Room version 7, `MIGRATION_6_7`, and schemas 1–7 were inspected; the
  byte-for-byte comparison against `origin/main` was clean. No migration,
  destructive fallback, dependency, or schema change was added.
- `git diff --check` was clean. Final status/stat review found only the
  intended CHG-024 production, test, and documentation files; no applicable
  gate is blocked and no screenshot gate applies.

## Independent engineering review

Independent engineering review:
PASS

BLOCKER 0
MAJOR 0
MINOR 0

Remote review confirmed the narrow Task create/targeted-Undo boundary,
strict no-overwrite create, atomic Task + Action Ledger persistence,
pre-Undo target/type/version/shape validation, rollback/failure behavior,
cancellation propagation, app wiring, Room v7, unchanged schemas 1–7,
and absence of UI, AI/runtime, Google/sync, reminders, complete/reopen
actions, migration, CapturePlan execution, or speculative abstractions.

## Exact next gate

User-authorized commit/push of the CHG-024 docs-only closeout,
followed by remote verification of the closeout docs.
After that verification, the user may merge
chg-024-reversible-task-create into main.
After merge, verify main == branch before selecting the next reviewable change.
