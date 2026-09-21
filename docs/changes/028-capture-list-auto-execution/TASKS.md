# Change 028 — Capture List Auto-Execution + Receipt/Undo Integration — TASKS

- Governance: **HIGH-ASSURANCE**
- Status: **IMPLEMENTED — TEXT AND REAL-HUMAN VOICE DEVICE ACCEPTANCE PASS; REVIEW PENDING**
- Branch: `chg-028-capture-list-auto-execution`

## Preflight and package

- [x] Fetch and verify live `main`, `origin/main`, and clean starting HEAD at
      `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`.
- [x] Confirm no existing branch/package conflict.
- [x] Read governing documents, CHG-027, relevant CHG-026 conventions, and
      inspect the canonical v3 image.
- [x] Inspect current capture, interpretation, executor, app/UI, list refresh,
      Undo, Action Ledger, and relevant test boundaries.
- [x] Confirm the production flow still stops at validated `CapturePlan`.
- [x] Confirm Room v7/schema/migration sufficiency and attached OPPO device.
- [x] Record unavailable `software-project-orchestrator` skill and follow the
      direct HIGH-ASSURANCE workflow.
- [x] Create the requested branch and SPEC/PLAN/TASKS before production edits.
- [x] Select CHG-028 in `docs/ACTIVE_WORK.md` without reserving CHG-029.

## Production implementation

- [x] Preserve Capture persistence before interpretation and execution.
- [x] Add typed Saved execution outcomes for not attempted, ineligible,
      executed, rejected, and failed.
- [x] Auto-execute only validated all-`AddListItem` plans, once per complete
      plan; mixed/unsupported plans execute nothing.
- [x] Propagate cancellation and preserve the already durable Capture.
- [x] Wire one app-scoped Room executor without a generic registry.
- [x] Give text and voice the same complete submission-result semantics.
- [x] Show accurate one/batch success receipts with one exact targeted Undo.
- [x] Use exact ledger/item IDs returned by CHG-027; do not use manual Undo or
      query/recompute identities.
- [x] Keep unsupported/rejected/failed feedback honest and omit Undo.
- [x] Reload visible Mandado/Compras state after execution and Undo outcomes.
- [x] Preserve existing manual list actions and all explicit exclusions.

## Tests and verification

- [x] Add focused application/JVM orchestration tests for every required
      eligibility, ordering, failure, cancellation, and identity scenario.
- [x] Add focused real-Room pipeline tests for execution, mixed-plan zero
      mutation, rejected Mandado, and exact targeted Undo with Capture retained.
- [x] Add/update focused Compose tests for text and voice receipts, one batch
      Undo, exact IDs, failure honesty, exactly-once behavior, and list freshness.
- [x] Run focused Compose and directly affected existing device regressions on
      the awake OPPO CPH2791 / Android 16 / API 36. Focused
      `CaptureListAutoExecutionUiTest` PASS 8/8. Existing regressions PASS
      35/35: `QuickAsideAppTest` 1, `CaptureTextSubmissionTest` 2,
      `VoiceCaptureTest` 10, `MandadoUiTest` 12, `ComprasUiTest` 10.
      `CaptureSubmissionListExecutionDatabaseTest` remains PASS 4/4 from the
      earlier real-Room run and was not rerun.
- [x] Run full JVM, Android-test compile, assemble, and lint gates.
- [x] Verify Room/schema/migrations/dependencies/manifest/network config and
      gateway/provider/auth/logging remain unchanged.
- [x] Complete focused OPPO UI/private-gateway acceptance and visual QA. The
      text flow now passes: one controlled Capture persisted, one Compras item
      was created, the success receipt appeared, and `Deshacer` removed the
      exact item while retaining the Capture. Receipt/Undo screenshots and
      read-only Room evidence are in `QA.md`. The coordinated human voice
      Capture persisted, created exactly one Compras mutation, and its exact
      target was successfully undone while retaining the Capture. The voice
      Undo ledger and target absence match the user-provided `Cambio deshecho`
      screenshot. Text/voice visual and privacy evidence pass. No tests were
      run during this documentation closeout; no install or re-pair occurred.
- [x] Run final Git whitespace/status/stat/name-status and complete-diff review.
- [x] Prepare the required CHG-028 implementation evidence record without
      assigning the independent engineering verdict.

## Stop state

Deterministic and real-device acceptance are complete: text execution/Undo and
human voice execution/Undo pass, with visual and privacy evidence recorded in
`QA.md`. The user authorized the CHG-028 commit and push for independent
review. Stop before merge, release, or CHG-029.
The earlier workstation-synthesized attempt produced a separate `comprar
carne` Capture without a ledger or mutation; it is not human-voice acceptance.
No synthesized speech or capture was initiated during documentation closeout.
