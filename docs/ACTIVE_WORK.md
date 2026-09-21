# ACTIVE WORK

Status: **CHG-028 IMPLEMENTED — ACCEPTANCE RECORDED; EVIDENCE CLOSEOUT COMPLETE; REVIEW PENDING**

## Current Change selection

- Change: `CHG-028 — Capture List Auto-Execution + Receipt/Undo Integration`
- Package: `docs/changes/028-capture-list-auto-execution/`
- Branch: `chg-028-capture-list-auto-execution`
- Verified base: live GitHub `main` / `origin/main` / starting `HEAD` at
  `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`.
- Governance: **HIGH-ASSURANCE**.
- Objective: connect persistence-first text and voice Capture submission to
  the CHG-027 executor only for validated all-`AddListItem` plans, then expose
  an accurate lightweight receipt and exact targeted batch Undo.
- Room remains v7; no schema, migration, dependency, gateway, provider, Google,
  reminder, or other action-family work is selected.
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
- No production or test source changed during this evidence closeout. The
  original production acceptance closeout ran no tests. This closeout ran only
  the focused Room instrumentation gate on the clean API 35 emulator; it did
  not touch the OPPO, rerun another gate, or repeat production acceptance. No
  re-pair, `pm clear`, JDWP, commit, push, merge, release, or CHG-029 work
  occurred. The post-voice app-process Logcat privacy scan found no voice
  transcript/item keywords, auth/signature headers, API-key pattern, request
  body, or prompt pattern (0 matches).
- The user retains commit, push, merge, release, and production authority.

## Most recently completed normal Change

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
- Normal capture/UI still stops at the validated `CapturePlan`; it does not
  automatically execute the plan.
- CHG-028 is now selected by explicit user instruction. No later Change ID is
  reserved.
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
- The QAG-004H gateway/client flow remains stopped at validated `CapturePlan`.
  CHG-027 adds a separate provider-independent list executor for plans whose
  every action is `AddListItem`, but the QAG flow does not call it and normal
  capture/UI does not execute plans automatically.
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
- QAG-004 remains stopped at validated `CapturePlan` + observable result.
  No interpreted action is executed automatically.

## Current project state

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
authority. No other action family is selected.

## Workstream and Change selection

The specialized Quick Aside Gateway workstream is complete and closed for
now. Its historical QAG identifiers and records remain unchanged; QAG
identifiers do not replace or renumber the global Change sequence. No QAG-005
implementation change is reserved.

Normal-use gateway hardening, historically Phase QAG-5 in the gateway
initiative, remains dependent on evidence from actual use. It is not
automatically scheduled as the next implementation change or reserved under a
global Change ID.

The completed normal global reviewable-change history runs through Change 027.
CHG-027 — CapturePlan List Execution Foundation is the most recently completed
normal Change and is integrated into `main` with **PASS_WITH_NOTES**. Its
implementation scope and verification record remain in
`docs/changes/027-captureplan-list-execution/`. CHG-028 is the selected active
Change; no later Change ID is reserved.

M3/M4/M5 foundations and other provider-independent work remain candidates
only; this closeout does not schedule them.

The user retains product, commit, push, merge, release, and production authority.
