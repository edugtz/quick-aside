# Change 016 — Local Search Foundation — PLAN

Governance: **STANDARD**  
Status: **PLAN/DOCS ONLY — IN PROGRESS**

## Preflight

- Confirm the `chg-016-local-search-foundation` branch and clean worktree.
- Read the accepted project, architecture, roadmap, acceptance, naming, UX,
  and prior Changes 005, 012, 013, 014, and 015 guidance.
- Inspect the canonical v3 visual reference. The image's Memoria search
  affordance informs the later UI direction, but no UI is implemented here.
- Inspect Capture, Lists, Memory, all requested DAOs/stores/readers, Room
  schema 4, schemas 1–4, and existing persistence/integration test patterns.
- Confirm the current Room/SQLite/KSP/dependency baseline and do not change it.

## Implementation approach

1. Add a focused application search package containing `LocalSearch`, the
   closed four-kind `LocalSearchResult` contract, the 50-result limit, and the
   exact query normalization/escaping rules from the SPEC.
2. Add only read-only search methods/projections to `CaptureDao`, the Memory
   DAOs, and `ListItemDao`. Query durable source tables directly; do not reuse
   `getRecent()` or current-screen store methods.
3. Implement `RoomLocalSearch` in the data layer. Read all source matches in a
   single Room read transaction, reconstruct Structured Log fields without
   duplicates, attach List definition/session context, merge, sort, and cap
   results deterministically.
4. Wire one production `LocalSearch` owner through `QuickAsideApplication`.
   Keep Compose and every existing source UI dependent on their current
   boundaries; do not add a Memoria route or search control.
5. Add focused JVM coverage for query escaping, trim/blank behavior, result
   mapping, the comparator, and limit policy.
6. Add a named-database Android integration test that seeds more than the
   recent-screen limit plus all four source kinds, then verifies corrected
   Voice behavior, field-key/value matching, one-result de-duplication,
   current/historical/completed/continuous List Items, global ordering, and
   close/reopen durability.
7. Run the required gates and inspect that Room version 4, schemas 1–4,
   dependencies, four destinations, and existing store behavior remain
   unchanged. Capture no UI evidence because this change has no UI surface.

## Test isolation

- JVM tests do not access Android UI or the production database.
- Room integration uses a unique named database and deletes only that
  dedicated test database during cleanup.
- Seed data uses explicit IDs and timestamps so ordering and beyond-recent
  behavior are observable.
- Existing Capture, List, Memory, and Compose tests remain unchanged unless a
  narrowly required production-wiring regression is identified.

## Stop signals

Stop and report if a normal table query cannot satisfy the bounded M1
contains-search requirement, if a schema/index migration appears necessary,
if FTS becomes necessary, if result mapping requires AI or source fabrication,
if UI/navigation work expands into scope, or if a global ranking policy beyond
the documented comparator is proposed.

