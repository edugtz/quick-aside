# QAG-004 — Android Gateway Integration — PLAN

## Approach

1. Reconcile the active change package and only stale QAG status text in long-lived docs.
2. Extend the provider-neutral interpretation request with trusted `capturedAt` and `timeZone`, leaving local Capture identity out of the request.
3. Keep `CaptureSubmission` as the shared text/voice persistence boundary; optionally invoke `CaptureInterpreter` only after successful persistence.
4. Add the smallest gateway infrastructure adapter using platform networking and `org.json`; add no production HTTP/DI/serialization framework.
5. Add QA1 primitives as testable pure Kotlin plus an Android-Keystore-backed device identity.
6. Add transient pairing through the existing gateway contract; no stored pairing code or generic Settings architecture.
7. Wire production dependencies in `QuickAsideApplication` and surface honest interpretation receipts without action execution.
8. Run focused JVM tests first, then full unit/build/lint and connected real-device tests.
9. Stop before real production pairing, provide the exact code-generation command, and resume only after explicit user action.
10. After user-authorized pairing, perform the real Oppo/Tailscale/Luna acceptance and sensitive-log review. Do not proceed to independent review automatically.

## Dependency decision

Production implementation uses Android/JDK platform APIs:

- `HttpsURLConnection` for HTTPS;
- `org.json` already present on Android for small fixed DTOs;
- Android Keystore/JCA for P-256/ECDSA;
- `SecureRandom`, `MessageDigest`, and `java.util.Base64` for QA1 primitives.

No Retrofit, Ktor, Moshi, Kotlin Serialization, Hilt, or Koin is introduced.

A **test-only** `org.json:json` dependency is added so local JVM tests exercise the same codec instead of Android mock stubs.

## Failure/rollback strategy

- Before merge, rollback is a normal source revert; there is no data/schema migration.
- Android pairing registration on the server is revocable independently using the existing gateway admin CLI.
- The Keystore identity is app-local. QAG-004 does not delete or rotate it automatically.
- QAG-004 does not mutate Tailscale, systemd, firewall, gateway deployment, Personal Admin, or provider auth.
- Network/provider failure degrades interpretation only; local capture remains durable.

## Reviewability

This is one coherent security-sensitive vertical slice: Android identity + signed gateway request + pairing + provider adapter + persistence-first wiring. Future plan execution, fallback, sync and reminders remain separate changes.

## Real-device gate outcome

The authorized Oppo CPH2791 / Android 16 gate completed for the QAG-004 path:
Keystore instrumentation passed, the device paired through the private
gateway, a genuine signed `/v1/interpret` returned a locally validated result,
the Capture remained persisted, no action was executed, Logcat review found no
prohibited sensitive material, and one end-to-end receipt measured
approximately 9 seconds. The visual-evidence set is complete: masked pairing
dialog, pairing-success snackbar, persisted Capture in Memoria, and the
interpreted-but-not-applied receipt. The pairing-success screenshot contains no
pairing code or other secret.

The full connected suite was attempted twice and reproduced the existing
`PendientesUiTest.unavailableSnapshotBlocksCreatePreservesInputAndRetryKeepsExistingTasks`
Compose timeout. The exact test passes in isolation on the same device. Static
review confirms the failing test, `PendientesScreen`, and task-management path
are unchanged by QAG-004, while the test invokes `QuickAsideApp` without a
`DevicePairer`, leaving the new pairing/interpretation state inactive.

Disposition: **non-QAG-004 full-suite/order-dependent Compose timing finding**.
No code change was made to bypass it. Independent review subsequently accepted
this disposition and returned **PASS_WITH_NOTES**. The reviewed QAG-004 commit
was then fast-forwarded into `main` with explicit user authorization.


## Closeout

Independent HIGH-ASSURANCE review counts:

- BLOCKER: 0
- MAJOR: 0
- MINOR: 3
- NOTE: 3
- verdict: **PASS_WITH_NOTES**

QAG-004 is integrated and closed. No release/deployment action is implied by
this repository merge. Future execution/hardening scope remains separate.
