# Change 004 — Local Text Capture — SPEC

Governance: **STANDARD**  
Status: **IN PROGRESS**

## Objective

Make the first local capture path usable from Inicio:

`Inicio → escribir → enviar → guardar CaptureInput.Text → receipt → continuar`

For this change, input such as `Compra pollo` is stored as one raw text
Capture. It is not interpreted, classified, routed, or turned into a list
item, task, note, structured log, or event.

## In scope

- A first-class text field on Inicio below the dominant voice CTA.
- One-handed submission through the keyboard IME action and a trailing send
  action, both with accessible labels.
- Blank and whitespace-only validation without creating a Capture.
- Preservation of the exact submitted text for every valid Capture.
- New Capture ID and capture timestamp generated outside the domain model.
- A small application-layer capture submission boundary backed by the
  existing Change 003 Capture DAO/database.
- A lifecycle-safe, app-scoped production database owner.
- Lightweight native success and failure feedback.
- Deterministic unit tests plus a real Android UI/persistence integration
  test for the production wiring.

## Out of scope

- Voice/STT, transcript editing, AI, CapturePlan, classification, routing, or
  interpretation.
- Mandado, Compras, tasks, notes, structured logs, calendar, Google APIs,
  search/history, undo, reminders, backup/export, or new navigation.
- A new Capture table, database version, schema, migration, or persistence
  framework.
- Hilt, service locators, generic repositories, command buses, or a generic
  use-case hierarchy.

## Domain and persistence contract

The saved record is:

- a new `CaptureId`;
- `CaptureInput.Text` containing exactly the user's valid input;
- the current capture timestamp.

The existing `QuickAsideDatabase` remains version 1 and Capture-only. The
existing `CaptureDao` and entity/domain mapping remain the sole persistence
representation; no second table or schema is introduced.

ID and timestamp providers are explicit dependencies of the application
boundary so tests can be deterministic. Production uses a UUID-based ID and
the current `Instant` without adding a framework.

## UI and UX contract

The affected reference screen is Inicio in
`docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`. The written invariants that
apply are:

- voice remains the dominant capture CTA;
- text is a first-class alternative and is reachable without opening a new
  destination;
- the four management destinations remain `Inicio`, `Pendientes`, `Listas`,
  and `Memoria`;
- the happy path ends in a lightweight receipt and remains on Inicio;
- the UI stays calm, native Material 3, accessible, and usable one-handed.

The legacy product label embedded in the reference image is not product copy
and is not reproduced.

After successful persistence, the field clears and the user sees
`Captura guardada`. If persistence fails, the field remains unchanged and a
small understandable error is shown without a crash.

## Acceptance scenarios

1. Submitting `Compra pollo` through Inicio writes exactly one Capture with a
   `CaptureInput.Text` containing `Compra pollo`.
2. Valid input with leading/trailing whitespace preserves that exact string;
   validation may call `isBlank()` but does not trim valid content.
3. Empty and whitespace-only input creates no Capture.
4. Successful save clears the field, stays on Inicio, and exposes
   `Captura guardada`.
5. A persistence failure leaves the user's text in the field, exposes an
   understandable error, and does not crash.
6. Production wiring uses the existing Room database and DAO; a close/reopen
   of the database can read the saved Capture.
7. Generated Capture IDs are distinct for distinct production submissions.
8. The Room database remains version 1 with the semantically unchanged
   Capture-only schema.
9. No AI, STT, Google, new navigation destination, or production dependency
   is introduced.

## Required evidence

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check` and `git status --short`
- Android screenshots showing Inicio with an empty field, entered text, and
  the successful `Captura guardada` receipt when a device is available.
- Inspection showing database version 1, unchanged schema JSON, Room 3.0.2,
  KSP 2.3.6, and no new AI/STT/Google dependency.
