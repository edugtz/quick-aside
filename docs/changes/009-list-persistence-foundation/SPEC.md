# Change 009 — List Persistence Foundation — SPEC

Governance: **HIGH-ASSURANCE**  
Status: **IN PROGRESS**

## Objective

Establish the first durable local persistence and application boundary for
Quick Aside lists. The initial built-in definitions are `Mandado`, a
session-based list with durable session history, and `Compras`, a continuous
list whose items are not attached to sessions.

This change is a persistence/domain/application foundation. Lists UI remains a
separate follow-up change.

## In scope

- Minimal extension of `ListItem` with completion state and deterministic
  creation time.
- Stable built-in definitions for Mandado and Compras while preserving the
  extensible `ListDefinition`/`ListBehavior` model.
- Room database version 3 with `list_definitions`, `list_sessions`, and
  `list_items` tables.
- Explicit, non-destructive Room migration 2→3 that leaves `captures`
  untouched and seeds the two built-in definitions.
- Explicit fresh-database seeding for version-3 databases.
- Room entities, DAOs, and domain mappers contained within the local data
  boundary.
- A focused list application boundary for definitions, sessions, items, and
  completion state.
- Transaction-safe single-active-session behavior for session-based
  definitions.
- Dedicated JVM and Android tests, including a real v2 migration fixture,
  close/reopen durability, fresh-database seeding, and capture regressions.

## Out of scope

- Lists UI, checkboxes, Mandado or Compras screens, add-item fields, or
  session-history UI.
- AI/routing, CapturePlan, automatic Capture→ListItem conversion, STT, sync,
  reminders, action ledger/Undo, delete, reorder, quantity, price, category,
  store, custom-list UI, archive/export, or unrelated schema changes.
- Destructive migration fallback or any production delete API.
- Changes to historical Change 001–008 documentation packages or their
  accepted schema files.

## Domain contract

`ListBehavior` remains the open behavior vocabulary with
`SESSION_BASED` and `CONTINUOUS` values. Built-ins use deterministic IDs:

| Definition | ID | Behavior |
| --- | --- | --- |
| Mandado | `mandado` | `SESSION_BASED` |
| Compras | `compras` | `CONTINUOUS` |

`ListItem` retains its original identity, definition identity, exact text, and
optional session identity. It additionally carries `isCompleted` and
`createdAt`. Blank or whitespace-only item text is invalid; valid text is
never trimmed or otherwise normalized.

The application boundary enforces:

1. Session-based items have an active session.
2. Continuous items have no session.
3. A session belongs to one definition.
4. An item session, when present, belongs to the same definition as the item.
5. Ended sessions and all their items remain durable and queryable.
6. Completion updates a row and never deletes it.
7. At most one active session exists per session-based definition through the
   supported boundary.
8. Continuous definitions never create sessions.

Unknown persisted behavior strings fail during mapping; they are not silently
converted to another behavior.

## Persistence contract

The existing `captures` table and its five v2 columns remain unchanged. The
database moves from version 2 to version 3 and adds only:

- `list_definitions(id, name, behavior)`;
- `list_sessions(id, list_definition_id, started_at_epoch_millis,
  ended_at_epoch_millis)`;
- `list_items(id, list_definition_id, list_session_id, text, is_completed,
  created_at_epoch_millis)`.

The three list tables use non-cascading foreign keys to preserve durable
history. Application queries use deterministic ordering:

- current items: `created_at_epoch_millis ASC, id ASC`;
- session history: `started_at_epoch_millis DESC, id DESC`.

Migration 2→3 creates the list tables and seeds the built-ins without
rebuilding or touching `captures`. A focused Room create callback uses the
same explicit seed owner for fresh v3 databases. Neither path creates a
general seed framework or overwrites conflicting rows.

## Application boundary

`ListStore` is a small, domain-specific boundary. It exposes built-in
definition reads, active/current session operations, session history, current
items, item insertion, and completion updates. `RoomListStore` owns the Room
transaction details. No generic `Repository<T>` is introduced and Room
entities do not escape the local data package.

Session creation performs the active-session lookup and insert in one Room
write transaction. A repeated start returns the existing active session.
Ending a session sets `endedAt`, retains the session/items, and returns a
deterministic no-op outcome when no active session exists. IDs and timestamps
are provided outside the pure domain through injectable providers so tests can
be deterministic.

## Acceptance scenarios

1. Mandado maps to `SESSION_BASED`; Compras maps to `CONTINUOUS`.
2. Built-in IDs are stable and a fresh v3 database contains exactly the two
   current built-ins.
3. A real v2 database migrates to v3 with its Text and corrected Voice Capture
   rows byte/value-equivalent and with both built-ins present once.
4. A fresh and a migrated database both report user version 3 and all three
   list tables.
5. Starting Mandado creates one active session; repeating the operation
   returns that same session; concurrent starts do not create two active
   sessions.
6. Ending Mandado preserves the ended session and its completed/uncompleted
   items; a later start creates a new session.
7. Mandado items preserve exact valid text and matching definition/session IDs.
8. Compras items preserve exact valid text and have a null session ID.
9. Blank item text creates no row.
10. Completion updates survive close/reopen and missing item updates are not
    reported as success.
11. Current-item and session-history ordering is deterministic.
12. Text/Voice capture read/write and transcript correction continue to work
    against v3.
13. No destructive migration path is configured; schemas 1 and 2 are
    unchanged and schema 3 contains only the intended structures.
14. No Lists UI or unrelated dependency/version change is introduced.

## Required verification evidence

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check`, `git status --short`, and diff statistics.
- Inspection of tracked schemas 1, 2, and 3.
- Real v2 on-disk fixture migration with exact Capture field checks and
  close/reopen verification.
- Explicit report of every unavailable or failed gate. The implementation
  report must not declare the final engineering verdict.

## Stop conditions

Stop and report if migration requires rebuilding `captures`, schema 1 or 2
changes unexpectedly, fresh and migrated seed ownership cannot be made
deterministic, foreign keys require destructive behavior, Room transactions
cannot clearly provide the active-session invariant, or implementation starts
requiring UI, AI, STT, sync, or a broader list redesign.

## Authority

Do not merge or push. The user retains commit, merge, release, and push
authority. The final engineering verdict belongs to the independent reviewer
orchestrator.
