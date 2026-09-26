# ACTIVE WORK

Status: **CHG-030 SELECTED — PLANNED — IMPLEMENTATION PENDING**

## Current state

- **Selected Change:** `CHG-030 — Capture Task Auto-Execution + Receipt/Undo
  Integration` (`docs/changes/030-capture-task-auto-execution/`), governance
  **HIGH-ASSURANCE**.
- Branch: `chg-030-capture-task-auto-execution`, created locally and **not
  pushed**.
- Verified base: `main` / `origin/main` / starting `HEAD` at
  `61c2c0b8aae6a5adab9306409cc5c6dc903a96da`; starting worktree clean.
- Status: **PLANNED — IMPLEMENTATION PENDING**. Planning created
  `SPEC.md`/`PLAN.md`/`TASKS.md`/`QA.md` and the selection records only. No
  production/test/schema/dependency change, no build/test/lint/instrumentation/
  device gate, and no commit/push/merge/release has occurred.
- Most recently completed Change: `CHG-029 — CapturePlan Task Execution
  Foundation` (`docs/changes/029-captureplan-task-execution/`).
- Integrated main SHA: `bcaa53d1993c44304029f6be29937ce42dbaa1e5`
  (fast-forward; identical to the pre-merge review-closeout SHA).
- Final independent verdict: **PASS_WITH_NOTES** — 0 BLOCKER / 0 MAJOR /
  0 MINOR / 3 NOTE. All Round-1 MAJOR/MINOR findings are closed; production
  correctness defects: 0; test correctness defects requiring remediation: 0;
  unresolved required gates: 0.
- Historical review trail: Round 1 was **BLOCKED** for missing direct executed
  ID-integrity evidence; remediation added the two missing direct Room tests
  and focused Room remediation passed **17/17**; Round 2 returned
  **PASS_WITH_NOTES**. Production source did not change during remediation.
- Capability: the Task execution foundation exists but remains **unwired** to
  normal CaptureSubmission/text/voice. Google Tasks sync remains pending,
  Event execution remains pending, and end-to-end Task natural-language
  mutation remains incomplete.
- CHG-030 is **not implemented**: its typed outcome evolution, Task
  Capture/UI wiring, receipt/Undo, and Pendientes freshness work are planned in
  the selected package. CHG-031 is not created or reserved.

## Selected Change — CHG-030

- Change: `CHG-030 — Capture Task Auto-Execution + Receipt/Undo Integration`.
- Package: `docs/changes/030-capture-task-auto-execution/`.
- Governance: **HIGH-ASSURANCE**.
- Branch: `chg-030-capture-task-auto-execution` (local only; not pushed).
- Base: `main` / `origin/main` / starting `HEAD` at
  `61c2c0b8aae6a5adab9306409cc5c6dc903a96da`; starting worktree clean.
- Status: **PLANNED — IMPLEMENTATION PENDING**.
- Objective: wire the existing, reviewed CHG-029 `CapturePlanTaskExecutor` into
  normal persistence-first text/voice Capture for validated all-`CreateTask`
  plans, with one compact receipt and exact targeted batch Undo, while the
  CHG-028 all-`AddListItem` path stays unchanged in behavior.
- Eligibility remains conservative: all-list executes the list path, all-task
  executes the Task path, and mixed/unsupported/non-representable families
  execute nothing.
- Explicit exclusions: Google Tasks/OAuth/Calendar/sync, Event/Note/
  StructuredLog/UndoLast/reminder execution, mixed-family execution, generic
  executor registries, gateway/provider/QA1/Tailscale/VPS changes, Room
  schema/migration changes, new dependencies, and CHG-031.
- Required HIGH-ASSURANCE acceptance: controlled real-device TEXT Task capture
  and coordinated real-human VOICE Task capture with exact durable
  Task/ledger/Capture provenance, receipt, Pendientes visibility, exact Undo,
  duplicate check, and sanitized visual/privacy evidence. If the environment is
  unavailable, the gate stays PENDING and pre-review readiness is
  `NOT REVIEW-READY — REQUIRED REAL-ENVIRONMENT ACCEPTANCE PENDING`; independent
  implementation review must not begin while it is pending.
- No implementation, test, build, instrumentation, or production-device gate
  has been run for CHG-030; the planned contract and gates are in the package.
- The user retains product, commit, push, merge, release, and production
  authority.

## Completed Change — CHG-029 implementation history

- Change: `CHG-029 — CapturePlan Task Execution Foundation`.
- Package: `docs/changes/029-captureplan-task-execution/`.
- Governance: **HIGH-ASSURANCE**.
- Planning baseline: request-supplied canonical `main` SHA
  `797e1557e8b5d94d4c8611a9b72749a8d7ac53f7`; local `main`, `origin/main`,
  and starting `HEAD` matched. The starting worktree was clean.
- Implementation branch: `chg-029-captureplan-task-execution`.
- Implementation commit: `2dc0425434c3b5b40a3ff25f1feba85bf3130efb`
  (committed and pushed by the user). The implementation branch is no longer
  at the canonical base HEAD.
- Objective: add a provider-independent, atomic Room boundary for validated
  all-`CreateTask` CapturePlans, one source-linked Action Ledger batch, and
  exact ordered batch Undo.
- The boundary is supported by existing Task/Action Ledger DAOs and Task
  contracts. Room remains v7; no schema, migration, dependency, or Task-domain
  change was made.
- The two production additions are
  `CapturePlanTaskExecutor` and `RoomCapturePlanTaskExecutor`; one JVM contract
  test and one real-Room instrumentation class cover the boundary.
- Focused JVM (32), focused Room (15), existing reversible Task Room
  regression (12), and full JVM (178) tests passed. Android-test Kotlin
  compilation, debug assembly, lint, and the Room/schema/config/dependency
  comparison passed. Inspectable outputs and per-run source fingerprints are
  under `docs/changes/029-captureplan-task-execution/evidence/`. The gate
  artifacts were generated before commit against worktree/source fingerprint
  `62d46a058954faf283e449dc9748c9213690738f3c60cb3ae997d36a208d2718` and
  production/test source manifest SHA-256
  `4ed7d3e16821ab9e030bb0c1f3a916c4735720ad10ae4bf62e8da82433143e32`.
  Production and test source in the published commit is unchanged from that
  tested implementation.
- Room tests ran only on `CHG028_Room_API35` (API 35), explicitly targeting
  `emulator-5556`. The connected OPPO was not used. No Capture/UI wiring,
  schema/dependency/config change, or CHG-030 work was included. The builder
  stopped before commit/push; the user subsequently committed and pushed the
  tested implementation.
- Final Git/scope evidence and the pre-review readiness check passed; see the
  CHG-029 evidence index.
- Independent Round-1 review reviewed
  `ad845759c85346c8fe4a976ba211a6f5f53a12c6` and returned **BLOCKED**:
  0 BLOCKER / 1 MAJOR / 2 MINOR / 3 NOTE. Remediation is test/evidence/docs
  only; no production source changed.
- MAJOR-1 added two focused Room tests directly against
  `RoomCapturePlanTaskExecutor`: first persisted Task-ID collision and
  duplicate generated IDs within one batch. Both executed and passed; the
  focused class now reports **17/17** on `CHG028_Room_API35` (API 35,
  `emulator-5556`). New artifacts are under
  `evidence/review-round-1-remediation/`; the original 15-test artifacts are
  preserved unchanged.
- MINOR-1 reconciled stale current-state wording in this package and the
  roadmap. MINOR-2 corrected the schema/config/dependency evidence pointer to
  `schema-config-dependency-run-record.json` without renaming or regenerating
  machine evidence. Round-1 NOTE findings remain preserved.
- Independent Round-2 review reviewed
  `b466407ae8b98400fe52da40750d18529ff9a3b9` and returned
  **PASS_WITH_NOTES**: 0 BLOCKER / 0 MAJOR / 0 MINOR / 3 NOTE. MAJOR-1,
  MINOR-1, and MINOR-2 are **CLOSED**. Production-code correctness defects: 0.
  Test correctness defects requiring remediation: 0. Unresolved required
  gates: 0.
- Round-2 reviewed HEAD `b466407ae8b98400fe52da40750d18529ff9a3b9` contains
  the Round-1 remediation commit on top of implementation commit
  `2dc0425434c3b5b40a3ff25f1feba85bf3130efb` and post-push provenance closeout
  `ad845759c85346c8fe4a976ba211a6f5f53a12c6`. Production source did not change
  during remediation, and the previously accepted gates remain valid.
- Current capability: the CHG-029 Task execution foundation exists but is
  **unwired** to normal CaptureSubmission/text/voice. Google Tasks sync and
  Event execution remain pending, and end-to-end Task natural-language
  mutation is **not** complete.
- CHG-029 is **integrated into `main`** at
  `bcaa53d1993c44304029f6be29937ce42dbaa1e5` after the user-authorized merge.
  CHG-030 was later selected for planning as recorded above; the CHG-029
  foundation remains unwired to normal CaptureSubmission/text/voice.

## CHG-028 closeout context at the selected baseline

- Most recently completed Change at this baseline: `CHG-028 — Capture List
  Auto-Execution + Receipt/Undo Integration`
- Package: `docs/changes/028-capture-list-auto-execution/`
- Integrated main: `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`
- Verified base: live GitHub `main` / `origin/main` / starting `HEAD` at
  `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`.
- Governance: **HIGH-ASSURANCE**.
- Objective: connect persistence-first text and voice Capture submission to
  the CHG-027 executor only for validated all-`AddListItem` plans, then expose
  an accurate lightweight receipt and exact targeted batch Undo.
- Room remains v7; no schema, migration, dependency, gateway, provider,
  Google, reminder, or other action-family work was added by CHG-028.
- The Round-1 independent review was **BLOCKED** historically because required
  verification evidence was not GitHub-inspectable. The evidence package is
  indexed at `docs/changes/028-capture-list-auto-execution/evidence/README.md`.
  Focused JVM 17/17, full JVM 174/174, `CaptureListAutoExecutionUiTest` 8/8,
  the five-class connected regression set 35/35, build/lint outputs, acceptance,
  privacy, and scope evidence are now indexed there. The Room 4/4 gate is
  independently inspectable in the canonical API 35 JUnit artifact and run
  record under `docs/changes/028-capture-list-auto-execution/evidence/device/room/`.
  This was a fresh focused verification of the same instrumentation gate at
  unchanged reviewed implementation SHA `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`,
  not a reproduction of the original OPPO historical 4/4 run. The earlier
  historical note, OPPO signing-conflict attempt, Pixel 9 Pro boot failure,
  and first API 35 infrastructure failure remain preserved.
- The connected gates with retained/recovered passing evidence ran on the awake
  OPPO CPH2791 / Android 16. Regression counts: `QuickAsideAppTest` 1,
  `CaptureTextSubmissionTest` 2, `VoiceCaptureTest` 10, `MandadoUiTest` 12,
  and `ComprasUiTest` 10. The earlier dozing-lockscreen blocker is superseded
  and was environmental, not a product defect.
- Historical note: connected-test cleanup removed the old install; the
  reinstalled app's first real capture was correctly rejected until pairing.
  That no longer describes the current identity. Three earlier production
  text executions and the current capture-linked execution establish that the
  active QA1 identity is working. No re-pairing or pairing-code operation was
  attempted in this continuation.
- The 2026-09-20 text acceptance pass submitted
  `Agrega pan QA028-TEXT-ACCEPT-20260920 a Compras`. It persisted one Capture,
  created exactly one Compras item, showed `Producto agregado`, and the
  snackbar `Deshacer` action succeeded (`Cambio deshecho`). Read-only Room
  evidence confirms the Capture remains, its one ledger entry is marked
  undone, and its item is absent. The receipt appeared about 7 seconds after
  submission; the UI appeared to be waiting in the capture state rather than
  frozen or unresponsive.
- A preceding exploratory text attempt,
  `Agrega leche QA028-TEXT-0920 a Compras`, missed its transient Undo and left
  that distinct test item in Compras. It is not a duplicate of the accepted
  marker; the Capture and active ledger are documented in the CHG-028 QA
  record so the durable side effect is explicit.
- The coordinated real-human voice acceptance is **PASS**. The newest human
  VOICE Capture is `f9bcffbf-429e-424d-9981-fe2184405f3d`, transcript
  `agregar uvas moradas a compras`, captured at `2026-09-20T18:34:06-06:00`.
  It remains persisted with one linked ledger,
  `16b4bd34-c05a-4aaa-ac10-4b9e8d48bdda`, and exactly one CREATE/list_item
  mutation targeting `b044cec6-1b94-47a4-9f56-426a35f78bf7`. The prior QA says
  an execution screenshot established `listDefinitionId=compras`, but that
  exact accepted-marker screenshot was not recovered. After Undo the deleted
  row no longer stores that field, so this package cannot independently verify
  the target list. The ledger was marked
  undone at 18:34:14; Room confirms the target ID and any `uvas moradas` item
  are absent. The user-provided screenshot shows `Cambio deshecho`. The Capture
  remains and no duplicate mutation/item exists.
- An earlier human voice run, Capture `b7bd11ad-e075-45b6-9494-b2e8737fa41f`
  (`comprar Giovanni en Costco`), remains an independent active execution with
  its `Giovanni en Costco` item present once in Compras. It is not the target
  of the coordinated Undo. The earlier workstation-synthesized artifact
  `04eed633-87b3-4cc8-8283-a3a24869b5c3` (`comprar carne`) has no ledger or
  mutation and is not acceptance evidence.
- The accepted text receipt took approximately 7 seconds. The earlier human
  voice run took approximately 8 seconds from Capture to ledger/item creation;
  the coordinated Undo run took approximately 6 seconds to ledger execution
  and about 2 more seconds to Undo. The UI appeared to wait rather than freeze.
- Tailscale had been disabled on the OPPO during the earlier
  `Captura guardada · interpretación no disponible` event. After Tailscale was
  enabled, both real text and real-human voice interpretation/execution
  worked. That was an environment/preflight issue, not a CHG-028 defect; the
  previous ProviderFailure was not investigated further. NOTE — without the
  required private Tailscale route, the app still shows generic
  `interpretación no disponible` feedback; this is environmental UX feedback
  debt. NOTE — remote interpretation latency is perceptible and should be
  evaluated separately after CHG-028; do not redesign the async flow here.
- No production or test source changed during the post-merge documentation
  closeout. The pre-merge evidence closeout ran only the focused Room
  instrumentation gate on the clean API 35 emulator; this post-merge
  documentation closeout ran no tests and did not touch the OPPO or repeat
  production acceptance. No re-pair, `pm clear`, JDWP, commit, push, merge,
  release, or implementation work occurred in that closeout. The post-voice
  app-process Logcat privacy scan found no voice
  transcript/item keywords, auth/signature headers, API-key pattern, request
  body, or prompt pattern (0 matches).
- At this CHG-028 closeout point, persistence-first Capture remained in place.
  Validated all-`AddListItem`
  plans now auto-execute through the CHG-027 executor, with exact targeted
  batch Undo for that list-only path. Other CapturePlan action families remain
  non-executable. That historical closeout preceded the CHG-029 planning
  selection recorded above.
- The user retains commit, push, merge, release, and production authority.

## Previous completed normal Change — CHG-028

- Change: `CHG-028 — Capture List Auto-Execution + Receipt/Undo Integration`
- Package: `docs/changes/028-capture-list-auto-execution/`
- Governance: **HIGH-ASSURANCE**.
- Implementation commit: `b8a14bf4a435b33870ee9bbf2127a2fd8f7b1d67`.
- Evidence remediation commit: `d8d5acdd9ae6982cb790054bdccaefdc0b1701be`.
- Evidence command correction commit / merged branch head:
  `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`.
- Round 1: **BLOCKED** due evidence provenance only.
- Round 2: **PASS_WITH_NOTES** — 0 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE.
- Integrated main SHA: `44c4d3befd97ad37dadc8fcf93fb2dc7a5ab8232`.
- Acceptance: real text execution and exact Undo passed; real human voice
  execution and exact Undo passed. The evidence package is indexed at
  `docs/changes/028-capture-list-auto-execution/evidence/`.
- No schema, dependency, gateway, provider, or other unrelated architecture
  change was introduced. CHG-028 is merged into `main`.

## Earlier completed normal Change — CHG-027

- Change: `CHG-027 — CapturePlan List Execution Foundation`
- Package: `docs/changes/027-captureplan-list-execution/`
- Verified base: `origin/main` at
  `cb67494a7b57d0f7a939ec06396ccbc665edff7c`
- Implementation commit: `b9ba067ff442259225127645c8a4c04eeb65dfc6`.
- Round-1 independent review at the implementation commit returned
  **BLOCKED** with 1 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE.
- Evidence/remediation commit and Round-2 reviewed SHA:
  `eddbfcd505a89ff7f7f0d4a37d511ad92abdcd24`.
- Round-2 verdict: **PASS_WITH_NOTES**.
- Final integrated `main` SHA:
  `979b8e6abb9a57ac5936559c252726a1ca84a98c`.
- Governance: **HIGH-ASSURANCE**.
- Scope: a provider-independent, narrow, atomic execution foundation for
  validated CapturePlans whose every action is `AddListItem`, plus targeted
  batch Undo. This does not make other CapturePlan action types executable.
- Verification: focused/full JVM checks, Android test Kotlin compilation,
  debug assembly, lint, and schema/config comparisons completed successfully.
  On OPPO CPH2791 / Android 16 / API 36, the new Room class passed 21/21 and
  the existing manual Room regression passed 9/9. Both device gates passed.
- The Round-1 evidence BLOCKER was resolved. No production correctness defect
  remained; the final remaining review debt was documentation/evidence
  provenance only.
- Round 2 identified stale post-push wording and QA references to standalone
  build logs that are not in the repository. The final documentation closeout
  corrected those records and distinguished repository machine evidence from
  the historical builder-recorded standalone compile/assembly results. That
  closeout was integrated into `main` at the final integrated SHA above.
- No production code, test source, or test semantics changed during either
  review closeout. No schema, dependency, manifest, or runtime changes were
  introduced during closeout.
- At CHG-027 completion, normal Capture/UI still stopped at the validated
  `CapturePlan`. CHG-028 later superseded that limitation for validated
  all-`AddListItem` plans and added exact targeted batch Undo.
- The user retains product, commit, push, merge, release, and production
  authority.

## Most recently completed QAG change

- Change: `QAG-004H — Android Gateway Client Hardening`
- Package: `docs/changes/QAG-004H-android-gateway-client-hardening/`
- Branch: `qag-004h-android-gateway-client-hardening`
- Verified base: `main` at `9114af73b96fd53a65423beba71d2d645aac8876`
- Governance: **HIGH-ASSURANCE**
- Objective: close the accepted QAG-004 client-side hardening findings before
  any future automatic action execution, while preserving the gateway API and
  stopping at validated `CapturePlan`.
- Round 1 reviewed SHA: `f17058e9a01026a2fa258c05be3501c44fed0e69`.
  Round 1 returned **BLOCKED** with 0 BLOCKER / 1 MAJOR / 2 MINOR / 1 NOTE.
- Round 2 remediation commit: `66d96d91d0e1207415d9cfc449993df70093d25f`,
  committed and pushed to this branch.
- Independent HIGH-ASSURANCE Round 2 re-review completed directly from GitHub
  at `66d96d91d0e1207415d9cfc449993df70093d25f`.
- Round 1 MAJOR is resolved. The Round 1 cancellation MINOR is accepted as a
  documented partial platform limitation: `HttpsURLConnection.disconnect()` is
  best-effort, blocking I/O is not guaranteed to stop immediately, and there is
  no general write-timeout guarantee.
- Final Round 2 findings: 0 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE;
  **PASS_WITH_NOTES**. The remaining MINOR is documentation/provenance only
  and is corrected by this documentation-only closeout.
- Stale governance/provenance was largely corrected in Round 2. No Round 3
  independent security review is required for this documentation-only closeout.
- QAG-004H itself remains a historical gateway/client hardening change and was
  not rewritten by CHG-028. At QAG-004H completion its flow stopped at the
  validated `CapturePlan`; CHG-028 later connected the CHG-027 list-only
  executor to normal text and voice Capture with exact targeted Undo.
- Integration: branch head `9db98f2e808d076ab29ca1e1dd7dddb74c6fed49`
  was fast-forwarded into `main` after explicit user authorization.
- The documentation-only review closeout required no Round 3 security review.

## Previous completed QAG change

- Change: `QAG-004 — Android Gateway Integration`
- Package: `docs/changes/QAG-004-android-gateway-integration/`
- Branch: `qag-004-android-gateway-integration`
- Original base: `9bf585404ecb73604ca43420397c4434bd8160e1`
- Reviewed head: `87a2715d1da4bde7910b91049a36dbf9a51d9487`
- Governance: **HIGH-ASSURANCE**
- Independent engineering verdict: **PASS_WITH_NOTES**
- Review findings: 0 BLOCKER / 0 MAJOR / 3 MINOR / 3 NOTE.
- Integration: fast-forwarded into `main` after explicit user authorization.
- Real-device evidence: Oppo CPH2791 / Android 16 Keystore, private Tailscale
  path, production pairing, genuine QA1-signed interpretation, persistence,
  privacy/Logcat, visual receipts, and measured ~9 s end-to-end latency.
- The full connected-suite `PendientesUiTest` anomaly remains recorded as a
  non-QAG-004 order/timing repository finding; the exact test passed 1/1 in
  isolation on the same Oppo.
- Accepted review debt:
  - pairing response should verify returned `deviceId` equals the local identity;
  - Android validation should mirror remote action bounds before any future
    automatic `ActionExecutor`;
  - `HttpsURLConnection` cancellation cleanup is bounded but not guaranteed
    to abort blocking I/O immediately.
- QAG-004 closed at validated `CapturePlan` + observable result. CHG-028
  later added automatic execution only for all-list-item plans. CHG-029 now
  supplies a Task-only execution foundation, still unwired to product Capture
  flows.

## Historical project context

QAG-003R private Tailnet gateway deployment and QAG-004 Android gateway
integration are both integrated. The private production path is now:

    Quick Aside Android
      -> Tailscale
      -> svc:quickaside
      -> Tailscale Serve HTTPS
      -> http://127.0.0.1:2588
      -> Quick Aside gateway
      -> GPT-5.6 Luna Low
      -> provider-neutral draft
      -> Android CapturePlanValidator
      -> validated CapturePlan

QA1 remains the application authorization boundary. Provider credentials remain
server-side. Personal Admin/Hermes remains independent. QAG-004H is integrated
with **PASS_WITH_NOTES** and its interpretation path remains stopped at
validated `CapturePlan`. CHG-027 adds a separate provider-independent list
execution foundation for validated plans whose every action is `AddListItem`,
with targeted batch Undo. Other CapturePlan action types are not covered, and
CHG-028 connects that list-only boundary to normal text and voice Capture with
exact targeted Undo. Text execution/Undo and human-voice execution/Undo have
passed real-device acceptance with visual and privacy evidence recorded in
the CHG-028 QA package. The user retains commit, push, merge, and release
authority. CHG-029's Task-only execution foundation is implemented and
integrated into `main` at `bcaa53d1993c44304029f6be29937ce42dbaa1e5` with a
Round-2 **PASS_WITH_NOTES** verdict (0 BLOCKER / 0 MAJOR / 0 MINOR / 3 NOTE)
after a Round-1 **BLOCKED** verdict that was resolved by test/evidence/docs
remediation. The foundation remains unwired to normal
CaptureSubmission/text/voice; Google Tasks sync and Event execution remain
pending. CHG-030 is selected for planning only; implementation has not begun.

## Workstream and Change selection

The specialized Quick Aside Gateway workstream is complete and closed for
now. Its historical QAG identifiers and records remain unchanged; QAG
identifiers do not replace or renumber the global Change sequence. No QAG-005
implementation change is reserved.

Normal-use gateway hardening, historically Phase QAG-5 in the gateway
initiative, remains dependent on evidence from actual use. It is not
automatically scheduled as the next implementation change or reserved under a
global Change ID.

The completed normal global reviewable-change history runs through Change 029.
CHG-028 is integrated into `main` with **PASS_WITH_NOTES**. CHG-029 is
**COMPLETE — PASS_WITH_NOTES — INTEGRATED INTO main** at
`bcaa53d1993c44304029f6be29937ce42dbaa1e5`. Round-1 independent review returned
**BLOCKED**; its test/evidence/docs remediation is complete, and Round-2
independent review returned **PASS_WITH_NOTES** (0 BLOCKER / 0 MAJOR /
0 MINOR / 3 NOTE). CHG-030 is now **selected** for the narrow Capture-Task
wiring described above; implementation has not begun. Future work beyond
CHG-030 (Google Tasks/OAuth/Calendar sync, Event execution, reminders, and
M3/M4/M5/M6 scope) remains candidate scope only, and CHG-031 is not reserved.

M3/M4/M5 foundations and other provider-independent work remain candidates
only; this closeout does not schedule them.

The user retains product, commit, push, merge, release, and production authority.
