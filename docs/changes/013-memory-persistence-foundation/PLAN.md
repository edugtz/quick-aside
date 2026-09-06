# Change 013 — Memory Persistence Foundation — PLAN

Governance: **HIGH-ASSURANCE**  
Status: **IN PROGRESS**

## Preflight

- Confirm the Change 013 branch and clean worktree.
- Read the accepted project, architecture, roadmap, acceptance, naming, UX,
  and historical Changes 002, 003, 007, and 009 guidance.
- Inspect the current memory domain, typed IDs, Capture/List persistence,
  database builder, migrations, application wiring, tests, and schemas 1–3.
- Preserve Room 3.0.2, KSP 2.3.6, SQLite 2.7.0, and all unrelated
  dependencies.

## Implementation approach

1. Extend `Note` and `StructuredLog` with required `createdAt` and domain
   validation; update current compile-time usages explicitly.
2. Add local-only memory entities, DAOs, and explicit mappers. Store
   timestamps as epoch milliseconds and fields as normalized child rows.
3. Add the focused `MemoryStore`, injectable ID/clock providers, deterministic
   result contracts, and `RoomMemoryStore` with cancellation propagation.
4. Add version 4 entities to `QuickAsideDatabase`, preserve migrations 1→2 and
   2→3, and register explicit non-destructive `MIGRATION_3_4`.
5. Validate optional source Captures in the same Room write transaction as
   creation. Use `withWriteTransaction` for atomic StructuredLog parent/field
   insertion. Do not wire memory UI.
6. Add deterministic JVM domain/mapping tests and a dedicated Android test
   database covering memory invariants, exact values, ordering, missing
   sources, atomicity, close/reopen, and Capture/List regressions.
7. Generate schema 4 and inspect it against the unchanged schemas 1–3 and the
   intended SQL topology.

## Migration fixture approach

The Android test creates a dedicated on-disk SQLite file with the exact
tracked schema-3 Capture/List tables, schema-3 identity hash, and
`user_version = 3`. It inserts known Text and corrected Voice Captures,
Mandado/Compras definitions, a Mandado session with items, and a Compras item.
The file is opened with `QuickAsideDatabase.create`, exercising the production
3→4 migration and Room validation. The test verifies all old values, v4,
empty memory tables, table/index presence, and close/reopen durability.

## Test isolation and verification

- Every new Android test uses a unique named database.
- The production `quick_aside.db` is never opened or deleted by tests.
- Dedicated fixture files are closed and deleted in cleanup.
- Run targeted tests while implementing, then all required unit, assemble,
  lint, connected instrumentation, schema, diff, and status gates.
- Report actual device/emulator evidence and any blocked gate without claiming
  a final PASS.

