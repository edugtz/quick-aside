# ACTIVE WORK

Status: **QAG-004 COMPLETE — INTEGRATED INTO `main`**

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
server-side. Personal Admin/Hermes remains independent.

## Next change

No next change is automatically selected.

QAG-5 normal-use hardening remains a roadmap candidate after real usage.
M3/M4/M5 foundations and other provider-independent work remain available
options, but beginning any new reviewable change requires a fresh user decision.

The user retains product, commit, push, merge, release, and production authority.
