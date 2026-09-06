# Change 011 — Compras UI — TASKS

## Package and preflight

- [x] Confirm the expected `chg-011-compras-ui` branch and starting worktree.
- [x] Read the project, architecture, roadmap, acceptance, naming, UX, Change
      009, and Change 010 guidance.
- [x] Inspect the canonical UX/UI reference image.
- [x] Inspect the existing continuous list domain, ListStore, RoomListStore,
      navigation shell, application wiring, and current tests.
- [x] Create the Change 011 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 011 IN PROGRESS.

## Listas and Compras UI

- [x] Make Compras interactive without adding a destination.
- [x] Add the focused nested Compras route and screen.
- [x] Add loading, valid empty, loaded, and failure states.
- [x] Add exact-text insertion with the blank-input guard and null session.
- [x] Add completion updates with matching-row replacement and failure
      retention.
- [x] Keep completed rows visible and provide accessible semantics.
- [x] Add native back and nested-route reset behavior.

## Tests and evidence

- [x] Add deterministic fake-store UI tests for the Change 011 contract.
- [x] Add a named-database RoomListStore Compras close/reopen integration flow.
- [x] Preserve green Mandado, text capture, voice capture, and transcript
      correction coverage.
- [x] Capture and inspect the requested representative screenshots.
- [x] Run unit tests, assemble, lint, and connected Android tests.
- [x] Verify database/schema/dependency invariants and the Compose DAO
      boundary.
- [x] Run `git diff --check`, `git status --short`, and diff statistics.
- [x] Prepare the implementation report without declaring the final verdict.

## Scope and authority

- [x] Keep historical Change 001–010 documentation packages and schema files
      untouched.
- [x] Do not add history, delete, filtering, reorder, editing, AI/routing,
      sync, reminders, or schema changes.
- [x] Do not merge or push.
- [ ] Final independent review remains required.

## Verification note

The connected suite was executed on `CPH2791` / Android 16. A completed
invocation recorded 82 scheduled tests with 0 failures, but the UTP result
publisher emitted a `NoClassDefFoundError`; later retries reproduced the same
infrastructure failure before producing a final report. Treat connected
verification as externally blocked pending a healthy test runner.
