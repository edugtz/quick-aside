# ACTIVE WORK

Status: **QAG-004H REVIEW COMPLETE — PASS_WITH_NOTES — AWAITING USER MERGE DECISION**

## Active change

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
  `ActionExecutor` or automatic mutation. It is **not merged into `main`**.
- Current next gate: user-authorized commit/push of this documentation-only
  closeout, followed by user-authorized merge / final repository closeout.

## Most recently completed change

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
server-side. Personal Admin/Hermes remains independent. QAG-004H is reviewed
with **PASS_WITH_NOTES**, awaits the user's merge decision, and remains stopped
at validated `CapturePlan` with no `ActionExecutor` or automatic mutation.

## Next change

No next change is automatically selected after QAG-004H.

QAG-5 normal-use hardening remains a roadmap candidate after real usage.
M3/M4/M5 foundations and other provider-independent work remain available
options, but beginning any new reviewable change requires a fresh user decision.

The user retains product, commit, push, merge, release, and production authority.
