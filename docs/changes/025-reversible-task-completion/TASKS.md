# Change 025 — Reversible Task Completion Actions Foundation — TASKS

Governance: **HIGH-ASSURANCE**
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
Expected branch: `chg-025-reversible-task-completion`

This is a LIVE EXECUTION CHECKLIST. Mark `[x]` only after the work or evidence
exists. This checklist records builder evidence and does not declare the
independent engineering verdict.

## Change package and preflight

- [x] Verify clean starting worktree on `main`.
- [x] Fetch and verify `origin/main` and `origin/chg-024-reversible-task-create`
      at `9d411908ec529117c9daf9fa23a3c8509a719346`.
- [x] Verify CHG-024 is an ancestor of `origin/main`.
- [x] Create/switch to `chg-025-reversible-task-completion` without committing.
- [x] Read governing docs, source-of-truth files, and CHG-023/024 contracts.
- [x] Inspect Task, TaskStore, Action Ledger, Room, wiring, and precedent tests.
- [x] Confirm Room v7, schemas 1–7, and no migration need.
- [x] Create this CHG-025 SPEC/PLAN/TASKS package.
- [x] Point `docs/ACTIVE_WORK.md` at CHG-025; it is now at the required
      implementation-complete/review-pending stop state.

## Application boundary and Room implementation

- [x] Extend `ReversibleTaskActions` with typed complete/reopen/Undo results.
- [x] Add strict completion payload-v1 codec and transition validation.
- [x] Add nullable completion compare-and-set DAO operation.
- [x] Implement atomic complete with canonical millisecond timestamp.
- [x] Implement atomic reopen preserving the exact prior completion millis.
- [x] Implement exact-shape targeted completion Undo.
- [x] Preserve cancellation propagation and typed ordinary failures.
- [x] Preserve unrelated Task fields and existing CREATE behavior.
- [x] Confirm no UI, AI/runtime, Google/sync, reminders, schema, dependency,
      generic serializer, or unrelated refactor enters the diff.

## Focused tests

- [x] Add JVM result/codec contract tests.
- [x] Add unique-database focused completion Room tests.
- [x] Cover Personal and Trabajo completion, due date/title/space preservation,
      timestamp canonicalization, exact ledger shape, and returned/persisted
      equality.
- [x] Cover complete/reopen no-op and missing-Task behavior with no ledger rows.
- [x] Cover parent/child ledger failure, Task-update failure, and cancellation
      rollback.
- [x] Cover complete/reopen Undo, close/reopen durability, and stale-state
      rejection.
- [x] Cover malformed/unsupported ledger shapes, missing Task, target mismatch,
      and already-undone paths without mutation.
- [x] Cover mark-undone failure/cancellation rollback and narrow-field Undo.
- [x] Cover Task restoration/update failure during completion Undo, preserving
      the completed Task, active ledger entry, and unrelated state.

## Verification gates

- [x] Run focused JVM codec/contract tests: `6/6` passed; skips/failures/errors
      `0`.
- [x] Run focused completion Room/device test: `14/14` passed on CPH2791 /
      Android 16; skips/failures/errors `0`.
- [x] Run existing CHG-024 reversible Task-create instrumentation regression:
      `12/12` passed on CPH2791 / Android 16.
- [x] Run existing Task persistence/lifecycle/migration instrumentation:
      `6/6` passed on CPH2791 / Android 16.
- [x] Run existing Action Ledger persistence regression: `10/10` passed on
      CPH2791 / Android 16.
- [x] Run applicable `com.edu.quickaside.data.local` instrumentation suite:
      `111/111` passed with zero skips/failures/errors.
- [x] Run `./gradlew :app:testDebugUnitTest`: `120/120` passed; skips/failures/
      errors `0`.
- [x] Run `./gradlew :app:assembleDebug`: `BUILD SUCCESSFUL`.
- [x] Run `./gradlew :app:lintDebug`: `BUILD SUCCESSFUL`.
- [x] Inspect Room version/migrations and compare schemas 1–7 byte-for-byte
      with the verified base.
- [x] Run `git diff --check`, inspect status/stat, and inspect exact final diff.
- [x] Prepare builder evidence report without assigning an independent verdict.
- [ ] Independent engineering review remains unchecked.

## Evidence log

- Preflight: clean `main`; both verified remote refs resolve to
  `9d411908ec529117c9daf9fa23a3c8509a719346`; CHG-024 is an ancestor.
- Branch: `chg-025-reversible-task-completion` created from verified
  `origin/main` without a commit.
- `git fetch origin` required the sandbox's repository-write authorization and
  completed successfully.
- Governing docs and applicable CHG-023/024 implementation/tests were read;
  the actual code confirms Room v7 and the existing app-scoped boundary.
- Production implementation adds complete/reopen/targeted completion Undo,
  strict payload-v1 codec, nullable completion CAS, and no schema/entity or
  application-wiring change.
- Focused JVM tests: `6/6` passed with zero skips/failures/errors.
- Focused completion Room tests: `14/14` passed on CPH2791 / Android 16,
  including the explicit Task-restoration failure path during Undo.
- CHG-024 Task-create: `12/12`; Task persistence: `6/6`; Action Ledger:
  `10/10`; all on CPH2791 / Android 16.
- Applicable `com.edu.quickaside.data.local` suite: `111/111` passed on
  CPH2791 / Android 16 with zero skips/failures/errors.
- Full JVM: `120/120` passed with zero skips/failures/errors. Assemble and
  lint succeeded.
- Room remains v7; migrations and schemas 1–7 are unchanged from
  `origin/main`; no destructive fallback or generated schema diff exists.
- `git diff --check` is clean. The final worktree is intentionally uncommitted.
- Two regression commands were initially run concurrently and encountered
  device APK/output lifecycle failures; serial reruns passed. No production
  code changed for that invocation issue.

## Exact next gate

All obtainable implementation and verification work is complete. This package
and `docs/ACTIVE_WORK.md` are now at:

`IMPLEMENTATION COMPLETE — REVIEW PENDING`

Then stop for user-authorized commit/push and independent engineering review.
