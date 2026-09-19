# QAG-004H — Android Gateway Client Hardening — SPEC

Governance: **HIGH-ASSURANCE**  
Status: **IMPLEMENTATION COMPLETE — REVIEW PENDING**  
Expected branch: `qag-004h-android-gateway-client-hardening`  
Verified base: `main` at `9114af73b96fd53a65423beba71d2d645aac8876`

## Objective

Close the three accepted non-blocking client-side findings from the completed
QAG-004 independent review without adding product behavior or changing the
gateway API:

1. bind pairing success to the local QA1 device identity;
2. mirror the gateway's action and field bounds locally before producing a
   `CapturePlanValidationResult.Valid`;
3. make cancellation state checks explicit around bounded blocking
   `HttpsURLConnection` I/O and document the remaining platform limitation.

QAG-004H still stops at a validated `CapturePlan`. It does not execute actions,
change persistence, or change the server.

## Trust boundary

The Android interpretation path remains:

```text
untrusted gateway response
  -> structural GatewayJsonCodec decode
  -> provider-neutral CapturePlanValidator semantic/bound validation
  -> CapturePlanValidationResult.Valid
```

The codec may reject malformed JSON, wrong JSON types, unsupported action
representations, unexpected fields, and invalid date syntax. Semantically
representable but out-of-contract values remain drafts and are rejected by
`CapturePlanValidator` without normalization, truncation, or silent removal.

The Android-side `CapturePlanContract` is the single source of the client
limits used by the validator:

- at most 16 actions;
- list definitions exactly `mandado` or `compras`;
- list-item text at most 1,000 characters;
- task title at most 500 characters;
- note text at most 4,000 characters;
- structured-log fields from 1 through 32;
- structured-log keys at most 128 characters;
- structured-log values at most 1,000 characters.

The validator continues to preserve existing blank-value issues and exact
accepted remote values.

## Pairing contract

For HTTP 200 pairing responses, the client accepts success only when the
structurally valid response has `status == "active"` and its `deviceId` is
exactly equal to the local `Qa1DeviceIdentity.deviceId`. Blank, invalid-status,
malformed, or mismatched responses map to the existing narrow
`DevicePairingFailureReason.MALFORMED_RESPONSE` failure. No local identity
mutation, rotation, persistence of the response value, or automatic retry is
introduced.

Existing HTTP and transport failure mappings remain unchanged.

## Transport contract

- Cancellation propagates as `CancellationException` and cannot become a
  successful response.
- The connection, output stream, and response/error stream remain closed on
  every exit path.
- No retry, redirect following, signed-request reuse, or networking dependency
  is added.
- Explicit connect/read timeouts remain the upper bound for blocking platform
  I/O. `HttpsURLConnection.disconnect()` is used as best-effort cancellation
  cleanup, but Android/JDK platform behavior does not guarantee that it
  immediately interrupts every blocking I/O operation; the client therefore
  checks coroutine activity after blocking operations and retains bounded
  timeouts.

## Scope exclusions

QAG-004H does not implement ActionExecutor, mutations, Undo execution, Google
Tasks/Calendar, reminders, fallback providers, retries/deferred work, Room
migrations, server/gateway changes, Tailscale/VPS changes, provider changes,
or UI behavior.

## Acceptance scenarios

- Pairing succeeds for matching local/returned identity plus active status.
- Mismatched, blank, invalid-status, and malformed pairing responses fail as
  malformed responses.
- Pairing HTTP mappings and cancellation propagation remain deterministic.
- Plans with 16 actions and exact field limits validate; plans with 17 actions,
  unsupported list IDs, or max+1 field values reject before `Valid`.
- Structured logs with 32 fields validate and 33 fields reject.
- Existing blank validation and action order preservation remain intact.
- No rejected remote value becomes a validated domain action.
- Existing QAG-004 QA1, codec, provider, persistence-first, failure-retention,
  build, lint, and Android-test-source compatibility coverage remains green.

## Implementation evidence

The focused pairing, validator, codec-boundary, provider, transport, and
interpreter JVM tests pass. The complete `:app:testDebugUnitTest`,
`:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and
`:app:lintDebug` tasks pass, and `git diff --check` is clean. No connected
Android suite, production pairing, provider request, commit, or push was run or
performed for QAG-004H.
