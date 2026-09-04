# Change 005 — Recent Capture History — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Make locally persisted Captures visible from the existing `Memoria`
destination:

`Inicio → guardar una o más Captures → Memoria → ver capturas recientes`

This is the first explicit retrieval surface for the local Capture data
already created by Changes 003–004. It proves that saved data remains useful
after navigation and database/app reuse without introducing interpretation or
any broader memory system.

## In scope

- A deterministic recent-Capture query on the existing Capture DAO.
- A focused application-layer read boundary that maps persistence records to
  the existing `Capture` domain type.
- A bounded recent-history read of the latest 50 Captures.
- A restrained `Memoria` view showing recent Captures newest first.
- Original text/transcript, input kind when useful, and a local human-readable
  timestamp for every visible Capture.
- An explicit empty state when no Captures exist.
- Lifecycle-safe refresh when entering Memoria and after a new Capture is
  saved.
- Deterministic tests for text, voice, ordering, empty state, UI visibility,
  and close/reopen persistence evidence.
- Visual evidence for empty, one-Capture, and multiple-Capture Memoria states.

## Out of scope

- Search, filters, tabs, Notes, Structured Logs, editing, deletion, archive,
  backup/export, pagination infrastructure, interpretation, AI, STT, Google
  APIs, reminders, or new navigation destinations.
- Voice capture or speech recognition. Existing `CaptureInput.Voice` rows are
  read and rendered only.
- Room schema changes, migrations, tables, columns, speculative indexes, or
  production delete APIs.
- Hilt, a generic repository/CRUD abstraction, or a ViewModel architecture
  added only for this screen.

## Reference surfaces and UX invariants

The affected reference surface is `Memoria` in
`docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`, interpreted with
`docs/UX_UI_REFERENCE.md` and the accepted navigation baseline.

The implementation preserves:

- `Inicio`, `Pendientes`, `Listas`, and `Memoria` as the only management
  destinations;
- the `Capture` action as an action rather than a fifth destination;
- calm Material 3 hierarchy and information that is easy to scan;
- original/raw capture content as structured history, not an AI chat;
- device-local timezone conversion at display time;
- the existing Inicio, Pendientes, and Listas behavior.

Because search and Notes/Logs are not part of this change, Memoria does not
show a non-functional search field or placeholder tabs. The first view is
limited to recent Captures and an empty state.

## Database and query contract

`QuickAsideDatabase` remains version 1 and the exported schema remains
semantically unchanged. The DAO adds only a read query over `captures`:

```sql
SELECT * FROM captures
ORDER BY captured_at_epoch_millis DESC, id DESC
LIMIT :limit
```

The `id DESC` tie-breaker makes equal timestamps deterministic. The
application read boundary supplies `limit = 50`; a larger history/archive
strategy is deferred until real usage requires it.

Room entities are not exposed to Compose. Persistence maps each row back to
the existing `Capture` domain type, preserving `CaptureInput.Text` and
`CaptureInput.Voice` separately.

## Timestamp contract

`Instant` remains the persisted source value. Memoria converts it using the
device's `ZoneId.systemDefault()` and current locale at display time. Relative
labels such as `Hoy` and `Ayer` are allowed; formatted strings are never
persisted. Formatter tests inject a fixed clock, timezone, and locale so they
do not depend on the test machine.

## Acceptance scenarios

1. A dedicated empty test database produces the Memoria empty state.
2. One persisted Text Capture appears with its exact original text.
3. Multiple Captures appear newest first.
4. Equal timestamps use deterministic ID ordering.
5. An existing Voice Capture is mapped and rendered as voice/transcript
   history without assuming all rows are Text.
6. Saving through the existing Inicio submission path makes the Capture
   visible in Memoria without an app restart.
7. A persisted Capture remains visible after closing and reopening the same
   dedicated database.
8. The four navigation destinations remain unchanged.

## Required verification evidence

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check` and `git status --short`
- Inspection confirming database version 1, unchanged schema JSON, no
  migration, Room 3.0.2, KSP 2.3.6, and no AI/STT/Google dependency.
- Representative Android screenshots for empty, one-Capture, and
  multiple-Capture Memoria states.

## Stop conditions

Stop and report if this read surface requires a schema change, migration,
deletion semantics, search infrastructure, AI/STT, Google integration, or a
major navigation redesign.

## Authority

Do not merge or push. The final engineering verdict belongs to the independent
reviewer/orchestrator.
