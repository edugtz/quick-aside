# Change 012 — Mandado History UI — TASKS

## Package and preflight

- [x] Confirm the expected branch and starting worktree.
- [x] Read the accepted project, architecture, roadmap, acceptance, naming,
      UX, and Changes 009–011 guidance.
- [x] Inspect the canonical UX/UI reference image.
- [x] Inspect the existing ListStore, RoomListStore, Mandado/Compras UI,
      navigation shell, application wiring, and current tests.
- [x] Create the Change 012 SPEC, PLAN, and TASKS package.
- [x] Mark `docs/ACTIVE_WORK.md` as Change 012 IN PROGRESS.

## Discoverability and navigation

- [x] Add the minimal accessible `Historial` affordance to Mandado.
- [x] Add local History and History Detail routes without Navigation Compose.
- [x] Preserve four bottom destinations and nested-route reset behavior.
- [x] Preserve Mandado and Compras current-session behavior.

## History list and detail

- [x] Add Loading, Loaded, Empty, and Failed/retry history states.
- [x] Read completed-session candidates through `ListStore` and filter active
      sessions at the presentation boundary.
- [x] Preserve deterministic supplied order and show local date/time plus item
      count without internal identifiers.
- [x] Add read-only historical detail with all items and completion state.
- [x] Ensure detail rows are noninteractive and expose accessible state.
- [x] Propagate cancellation and hide exception details.

## Tests and evidence

- [x] Add deterministic Compose UI coverage for the Change 012 contract,
      including zero-write/read-only and navigation scenarios.
- [x] Add a unique named-database Room integration flow for A/B completed and
      C active sessions, close/reopen, order, attachments, and completion.
- [x] Preserve green Mandado, Compras, text capture, voice capture, and
  transcript correction coverage. The full connected run had seven
  transient Compose-startup failures; the affected TranscriptCorrectionUiTest
  and VoiceCaptureTest classes were rerun on CPH2791 / Android 16 with
  23/23 tests passing.
- [x] Final independent review; verdict: PASS_WITH_NOTES.
- [x] Capture and inspect the three representative screenshots.
- [x] Run unit tests, assemble, lint, and connected Android tests.
- [x] Run `git diff --check`, `git status --short`, and diff stats.
- [x] Verify version 3, schemas 1/2/3 unchanged, no schema 4/migration, no
      dependency or AI/STT/Google change, and no Compose DAO access.
- [x] Prepare the implementation report without declaring the final verdict.

## Scope and authority

- [x] Keep historical Change 001–011 packages and schemas untouched.
- [x] Do not add reopen/delete/copy/edit/export/search/filter/pagination,
      Compras history, AI, sync, reminders, or schema changes.
- [x] Do not merge or push.
- [x] Final independent review; verdict: PASS_WITH_NOTES.