# QAG-004 — Android Gateway Integration — SPEC

Governance: **HIGH-ASSURANCE**
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**
Expected branch: `qag-004-android-gateway-integration`
Verified base: `main` at `9bf585404ecb73604ca43420397c4434bd8160e1`

The authorized Oppo real-device Keystore, pairing, signed interpretation,
persistence, receipt, latency, and privacy checks have completed. The full
connected Android suite still reproduces an unrelated existing
`PendientesUiTest` Compose timing failure. Static comparison confirms that the
failing test and its task-management production path are outside the QAG-004
diff, and the exact test passes in isolation on the same device. The anomaly is
recorded as a non-QAG-004 full-suite/order-timing finding; QAG-004 advances to
review pending.

## Objective

Connect the production Android application to the already deployed private Quick Aside gateway while preserving the local-first and provider-neutral boundary:

```text
CaptureSubmission
  -> persist Capture locally
  -> CaptureInterpreter
  -> AIProvider
  -> HTTPS/Tailscale private gateway
  -> QA1 authentication
  -> GPT-5.6 Luna Low
  -> provider-neutral untrusted candidate
  -> CapturePlanValidator
  -> validated CapturePlan or explicit interpretation failure
```

QAG-004 ends at an observable validated interpretation result. It does not execute the returned actions.

## Proven baseline

- `main` HEAD is `9bf585404ecb73604ca43420397c4434bd8160e1` (`docs: close QAG-003R after merge`).
- QAG-003R is complete and integrated.
- Production ingress is private Tailscale Services/Serve only:
  `https://quickaside.taildc9db9.ts.net -> 127.0.0.1:2588`.
- QA1 is the application authorization boundary; existing tailnet Access Controls remain unchanged.
- `POST /v1/pair` and QA1-protected `POST /v1/interpret` are implemented server-side.
- Android currently has `AIProvider`, `ProviderCaptureInterpreter`, `CapturePlanValidator`, local `CaptureSubmission`, and Room persistence but no production gateway adapter, QA1 identity, `INTERNET` permission, or live runtime wiring.
- Room remains version 7. No Room/schema change is required.

## Security and transport contract

- Production endpoint is exactly `https://quickaside.taildc9db9.ts.net`.
- No cleartext fallback, public endpoint, custom trust bypass, permissive hostname verifier, or TLS validation disablement is allowed.
- Redirects are not followed for signed requests.
- Android owns no ChatGPT/Codex/provider credentials.
- QA1 device private key is generated as P-256 in Android Keystore and is non-exportable.
- Stable device ID is derived from the public key; only the public key is exported in PEM for pairing.
- Every protected request uses a fresh cryptographically random nonce and current Unix-seconds timestamp.
- Android rejects request bodies larger than the gateway's 32 KiB request bound before opening a connection, and bounds response bodies locally.
- The canonical request is exactly LF-separated:

```text
QA1
METHOD
PATH
DEVICE_ID
TIMESTAMP
NONCE
SHA256(body)
```

- ECDSA uses SHA-256 and the Android/JCA DER signature bytes; signature and nonce use Base64URL without padding.
- The exact serialized body bytes hashed/signed are the bytes written to the HTTP request.
- No automatic retry reuses a nonce/signature. QAG-004 adds no automatic retry at all.

## Wire contract

`POST /v1/interpret` sends only:

```json
{
  "inputText": "...",
  "capturedAt": "2026-09-18T20:00:00Z",
  "timeZone": "America/Mexico_City"
}
```

- `inputText` is the effective text selected from the persisted Capture.
- `capturedAt` is the already persisted `Capture.capturedAt`.
- `timeZone` comes from an explicit/testable Android source and must be a tzdb/IANA identifier.
- `sourceCaptureId` is never sent remotely; Android attaches it locally before validation.

Supported response actions remain:

- `AddListItem`
- `CreateTask`
- `CreateNote`
- `CreateStructuredLog`
- `UndoLast`

## Failure contract

Gateway/network failures become provider-neutral Android failure reasons covering:

- network/Tailscale/connect unavailable;
- DNS failure;
- TLS failure;
- client timeout;
- HTTP 401 `authentication_failed`;
- HTTP 413 `request_too_large`;
- HTTP 422 `invalid_request`;
- HTTP 429 `rate_limited`;
- HTTP 502 `provider_invalid_output`;
- HTTP 503 `provider_unavailable`;
- HTTP 504 `provider_timeout`;
- unexpected HTTP status;
- malformed/oversized success response.

`CancellationException` propagates. No automatic retry is introduced.

## Pairing flow

The first authenticated failure can expose a small native pairing dialog. The one-time code exists only in transient Compose state, is visually masked, and is sent to `POST /v1/pair`; it is not committed, logged, or persisted.

Real production pairing is a stop gate. A fresh server-side one-time code must be generated only after explicit user action.

## Capture pipeline contract

Both text and voice use the same `CaptureSubmission.submitInput` persistence-first path. Production `CaptureSubmission` receives the production `CaptureInterpreter` and calls it only after `CaptureWriter.save(capture)` succeeds.

A provider/network failure returns a saved capture plus an explicit interpretation failure. It must not convert the local save into a persistence failure or delete the Capture.

UI feedback distinguishes persistence from interpretation, including the explicit phrase that a successful interpretation is **not applied**.

## Explicit exclusions

QAG-004 does not implement:

- ActionExecutor or any automatic plan execution;
- DeepSeek fallback or reasoning escalation;
- Google Tasks/Calendar;
- reminders or WorkManager retry/deferred interpretation;
- generic Settings architecture;
- public gateway support;
- Tailscale Access Control changes;
- gateway/server contract changes;
- Personal Admin/Hermes changes;
- Room schema/migration changes.

## Acceptance scenarios

1. Text and voice persist their Capture before any gateway call.
2. Wire request maps effective text, persisted timestamp and current IANA timezone; it does not contain `sourceCaptureId`.
3. Android Keystore provides one stable P-256 signing identity whose private key is non-exportable.
4. QA1 canonical bytes, SHA-256 body hash, fresh nonce, timestamp and Base64URL encoding match the server contract.
5. The exact body bytes signed are the bytes transmitted and redirects/retries do not create signature ambiguity.
6. All currently supported gateway actions decode in order and still pass `CapturePlanValidator` before producing `CapturePlan`.
7. All documented network/HTTP/malformed-response failures map explicitly; cancellation propagates.
8. Provider/network failure leaves the Capture durable and user-visible.
9. Pairing code is transient only and no provider credentials are introduced on Android.
10. Real Oppo/Tailscale pairing and a signed production Luna request succeed, with measured end-to-end latency and no sensitive Logcat leakage.
11. The implementation stops at validated CapturePlan + observable result; no action is applied.
