# ACTIVE WORK

Status: **CHG-027 IMPLEMENTATION COMPLETE — REVIEW PENDING — HIGH-ASSURANCE**

## Active change

- Change: `CHG-027 — CapturePlan List Execution Foundation`
- Package: `docs/changes/027-captureplan-list-execution/`
- Branch: `chg-027-captureplan-list-execution`
- Verified base: `origin/main` at
  `cb67494a7b57d0f7a939ec06396ccbc665edff7c`
- Governance: **HIGH-ASSURANCE**
- Scope: add a narrow, atomic executor for validated all-AddListItem
  CapturePlans and targeted batch Undo. The normal capture path remains
  stopped at validated CapturePlan; there is no capture/UI auto-wiring.
- Verification: focused/full JVM checks, Android test Kotlin compilation,
  debug assembly, lint, and schema/config comparisons completed successfully.
  On OPPO CPH2791 / Android 16 / API 36, the new Room class passed 21/21 and
  the existing manual Room regression passed 9/9. Both device gates passed.
- No code or test changes were required after the device runs.
- Final local HEAD remains the verified base SHA; no commit or push was
  created.
- The user retains product, commit, push, merge, release, and production
  authority.

## Most recently completed change

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
- QAG-004H remains stopped at validated `CapturePlan`; there is no
  `ActionExecutor` or automatic mutation.
- Integration: branch head `9db98f2e808d076ab29ca1e1dd7dddb74c6fed49`
  was fast-forwarded into `main` after explicit user authorization.
- The documentation-only review closeout required no Round 3 security review.

## Previous completed change

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
with **PASS_WITH_NOTES** and remains stopped at validated `CapturePlan` with no
`ActionExecutor` or automatic mutation.

## Workstream and active implementation

The specialized Quick Aside Gateway workstream is complete and closed for
now. Its historical QAG identifiers and records remain unchanged; QAG
identifiers do not replace or renumber the global Change sequence. No QAG-005
implementation change is reserved.

Normal-use gateway hardening, historically Phase QAG-5 in the gateway
initiative, remains dependent on evidence from actual use. It is not
automatically scheduled as the next implementation change or reserved under a
global Change ID.

The normal global reviewable-change history remains Change 001 through Change
026. The user has selected Change 027 as the CapturePlan List Execution
Foundation; its active scope and verification contract are recorded in
`docs/changes/027-captureplan-list-execution/`. No later Change ID is
reserved.

M3/M4/M5 foundations and other provider-independent work remain candidates
only; this closeout does not schedule them.

The user retains product, commit, push, merge, release, and production authority.
