# Change 013 — Memory Persistence Foundation — TASKS

Governance: **HIGH-ASSURANCE**  
Status: **IN PROGRESS**

## Change package and preflight

- [x] Confirm expected branch and clean worktree.
- [x] Read accepted project, architecture, roadmap, acceptance, naming, UX,
      and historical persistence guidance.
- [x] Inspect current domain, Capture/List persistence, Room database,
      migrations, wiring, tests, and schemas 1–3.
- [x] Confirm Room 3.0.2 / KSP 2.3.6 / SQLite 2.7.0 baseline.
- [x] Create Change 013 SPEC, PLAN, and TASKS.
- [x] Point `docs/ACTIVE_WORK.md` at Change 013 IN PROGRESS.

## Domain and application boundary

- [x] Add required `createdAt` to Note and StructuredLog.
- [x] Enforce exact non-blank Note text and non-empty valid log fields.
- [x] Add injectable memory ID and clock providers.
- [x] Add deterministic MemoryStore result contracts and operations.
- [x] Wire the production memory boundary without changing UI.

## Room schema and migration

- [x] Add memory entities, normalized field rows, DAOs, and mappers.
- [x] Move QuickAsideDatabase to version 4.
- [x] Preserve and register migrations 1→2 and 2→3.
- [x] Add and register explicit non-destructive migration 3→4.
- [x] Add only required/justified memory foreign-key indexes.
- [x] Generate schema 4 and confirm schemas 1–3 are unchanged.

## Tests and evidence

- [x] Add deterministic JVM domain/mapping tests.
- [x] Add real v3→v4 migration fixture with Capture and List data.
- [x] Add memory persistence, ordering, atomicity, and close/reopen tests.
- [x] Add v4 Capture/List regression coverage.
- [x] Run unit tests, assemble, lint, connected Android tests, diff check,
      status, and diff statistics (connected runner blocked before execution).
- [x] Inspect schema 4 and report all required gates accurately.
- [x] Prepare the implementation report without declaring the final verdict.

## Scope and authority

- [ ] Do not modify historical Change 001–012 packages or schemas.
- [ ] Do not add UI, search, FTS, AI, reminders, Action Ledger, delete/update,
      export/archive, Google integration, or dependencies.
- [ ] Do not merge or push.
