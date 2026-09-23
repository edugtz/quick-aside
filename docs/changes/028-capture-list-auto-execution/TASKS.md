# Change 028 — Capture List Auto-Execution + Receipt/Undo Integration — TASKS

- Governance: **HIGH-ASSURANCE**
- Status: **COMPLETED — MERGED INTO MAIN; ROUND 2 PASS_WITH_NOTES**
- Branch: `chg-028-capture-list-auto-execution`

## Post-merge provenance

- Implementation commit: `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`.
- Evidence remediation commit: `d8d5acdd9ae6982cb790054bdccaefdc0b1701be`.
- Evidence command correction commit / merged branch head:
  `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`.
- Round 1: **BLOCKED** due evidence provenance only.
- Round 2: **PASS_WITH_NOTES** — 0 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE.
- Integrated main SHA: `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`.

## Preflight and package

- [x] Fetch and verify live `main`, `origin/main`, and clean starting HEAD at
      `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`.
- [x] Confirm no existing branch/package conflict.
- [x] Read governing documents, CHG-027, relevant CHG-026 conventions, and
      inspect the canonical v3 image.
- [x] Inspect current capture, interpretation, executor, app/UI, list refresh,
      Undo, Action Ledger, and relevant test boundaries.
- [x] Confirm the pre-CHG-028 production flow stopped at validated
      `CapturePlan`; CHG-028 later added list-only execution wiring.
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
      A fresh focused run of `CaptureSubmissionListExecutionDatabaseTest` on
      `CHG028_Room_API35(AVD)` / API 35 passed 4/4 at unchanged reviewed
      implementation SHA `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`. Its
      JUnit XML and exact command/result record are canonical in
      `evidence/device/room/`. This is a fresh verification of the same Room
      instrumentation gate, not a reproduction of the original OPPO
      historical 4/4 run. The original historical note, OPPO signing-conflict
      attempt, Pixel 9 Pro boot failure, and first API 35 infrastructure
      failure remain preserved separately.
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
      screenshot. Text receipt/Undo and sampled privacy evidence are included;
      the accepted-marker voice execution screenshot was not recovered. The
      original acceptance closeout ran no tests. The later evidence closeout
      verified the Room instrumentation gate on the clean API 35 emulator at
      the unchanged implementation SHA. It did not touch the OPPO, repeat
      production acceptance, or change production/test source. No re-pair or
      production acceptance rerun occurred.
- [x] Run the requested Git whitespace, status, stat, and name-status checks.
- [x] Prepare the required CHG-028 implementation evidence record without
      assigning the independent engineering verdict.

## Stop state

The implementation and prior production acceptance are complete as recorded
in `QA.md`; evidence closeout is complete and the change is merged into
`main` with Round 2 **PASS_WITH_NOTES**.
Machine evidence for the focused/full JVM, Room 4/4, Compose, existing
connected regressions, build, lint, text acceptance, and privacy sample is
indexed under `evidence/`. The fresh API 35 Room result independently verifies
the same instrumentation gate at unchanged reviewed implementation SHA; it
does not reproduce the original OPPO historical run. The earlier Room
historical note and infrastructure failures remain preserved. Stop before any
further gate, OPPO interaction, app uninstall, re-pair, commit, push, merge,
release, another review round, or CHG-029.
The earlier workstation-synthesized attempt produced a separate `comprar
carne` Capture without a ledger or mutation; it is not human-voice acceptance.
No synthesized speech or capture was initiated during documentation closeout.
