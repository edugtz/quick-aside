# Quick Aside — Active Work

## Active change

`docs/changes/026-local-pendientes-ui/`

Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**

Governance: **STANDARD**

CHG-026 was implemented on `chg-026-local-pendientes-ui` from the verified
`origin/main` baseline `b4333a11e05403c1afe96890e0ecf73ec1f634ea`. It replaces
the Pendientes placeholder with a local-first Personal/Trabajo Task management
surface using the existing TaskStore and ReversibleTaskActions boundaries.
Google Tasks synchronization and runtime AI remain unavailable and out of
scope.

Implementation and obtainable verification are complete. Independent review
of reviewed head `eb20cc18f1b832259e4a510b713327e524111f07` found exactly one
**MINOR**: manual creation could fabricate `Loaded(listOf(newTask))` from
Loading/Failed. The targeted patch gates creation on Loaded and makes
`withTask` preserve unavailable states. Post-patch `PendientesUiTest` passed
14/14 on both connected devices and `QuickAsideAppTest` passed 1/1 on both;
the full JVM suite passed 120/120, CHG-024 passed 12/12, CHG-025 passed 14/14,
and assemble/lint/schema/diff checks passed. The retained connected result
identified `MandadoUiTest.undoFailureReloadsVisibleStateAndShowsConciseError`
timing out at `MandadoUiTest.kt:198` on a displayed-node wait; the exact method
also passed on the detached `origin/main` baseline, so it was a pre-existing
test synchronization defect/flake. The test-only semantic-presence repair
passed 3/3 focused repetitions, `MandadoUiTest` passed 12/12,
`MandadoHistoryUiTest` 7/7, and `QuickAsideAppTest` 1/1. The pre-review-patch
connected suite passed 249/249 with 0 skipped and 0 failures on
`Pixel_9_Pro(AVD) - 15` (API 35); the full suite was not rerun for this
targeted Pendientes-only review patch. No Mandado production behavior
changed.

## State

- Changes 020, 021, 023, 024, and 025 are merged/complete at this verified
  base.
- CHG-024's Task CREATE ledger contract and CHG-025's completion contract
  remain unchanged.
- Room remains v7 and runtime AI remains paused.
- CHG-026 changes only the Pendientes UI and its shell wiring plus focused UI
  tests, the pre-existing Mandado test synchronization repair, and
  documentation. This targeted review patch does not change domain, Room,
  reversible actions, synchronization, AI, navigation architecture, or
  unrelated UI.

## Independent engineering review

Latest review finding: **MINOR** — unavailable Pendientes snapshots could be
promoted to partial Loaded state. The targeted follow-up is applied;
independent review remains unchecked.

## Proven baseline

- Product name accepted: **Quick Aside**.
- Canonical written UX contract: `docs/UX_UI_REFERENCE.md`.
- Canonical visual-direction reference:
  `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Durable runtime decision: `docs/adr/0001-private-remote-ai-runtime.md`.
- Roadmap and acceptance sources: `docs/ROADMAP.md` and
  `docs/ACCEPTANCE_CRITERIA.md`.

## Exact next gate

User-authorized commit/push of CHG-026 followed by independent review of
`main...chg-026-local-pendientes-ui`. Do not select CHG-027.
