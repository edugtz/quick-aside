# Change 001 — Project Bootstrap — SPEC

Governance: **STANDARD**

## Objective

Create a minimal, reviewable Android project foundation for Quick Aside that builds, tests, preserves the canonical docs/design reference, and establishes the application boundaries needed for later feature changes.

## In scope

- Android app repository bootstrap using the accepted display/project name **Quick Aside**.
- Kotlin + Compose baseline.
- Material 3 baseline.
- Room dependency/foundation without implementing the final domain schema prematurely.
- Basic test/build/lint infrastructure supported by the chosen Android tooling.
- Package/module structure suitable for feature/domain/data separation without speculative over-abstraction.
- App theme/design tokens sufficient to implement a simple UI shell.
- Navigation shell for accepted destinations: Inicio, Pendientes, Listas, Memoria.
- Global capture action placeholder that is explicitly an action, not a destination.
- Preserve/add the canonical project docs and UX reference in the repository.

## Out of scope

- Actual voice/STT behavior.
- Runtime AI provider integration.
- Google Tasks OAuth/sync.
- Google Calendar integration.
- Reminders/notifications.
- Final Room entity schema.
- Archive/export.
- Quick Settings/widget/system invocation.
- Final production branding/icon work beyond using the accepted `Quick Aside` display name.
- Polished feature screens beyond what is needed to validate navigation/theme foundation.

## Acceptance scenarios

1. Repository can be built using documented deterministic command(s).
2. Repository has at least one deterministic automated test executed successfully.
3. Lint/static checks configured by the selected Android template/tooling run successfully or any unavailable gate is explicitly documented as PENDING.
4. App launches to a minimal shell with Inicio / Pendientes / Listas / Memoria destinations and global Capture action.
5. Shell behavior respects the written UX contract; it does not introduce `Captura` as a fifth navigation destination.
6. UI implementation uses `docs/UX_UI_REFERENCE.md` and `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png` as required references.
7. No future feature is implemented merely to make the shell look complete.

## Evidence required

- build output;
- test output;
- lint/static output when configured;
- screenshot(s) of the shell on emulator/device;
- concise comparison to UX v3 reference.
