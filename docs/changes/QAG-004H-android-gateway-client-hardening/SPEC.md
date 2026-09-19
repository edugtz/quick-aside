# QAG-004H — Android Gateway Client Hardening — SPEC

Governance: **HIGH-ASSURANCE**
Status: **ROUND 2 REVIEW COMPLETE — PASS_WITH_NOTES — MERGE PENDING**
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
  successful coroutine result once blocking I/O returns.
- The connection, output stream, and response/error stream remain closed on
  every exit path.
- No retry, redirect following, signed-request reuse, or networking dependency
  is added.
- Connect and read phases have explicit timeouts. Coroutine activity is checked
  after blocking output, response-code, and response-read work, and streams plus
  the connection retain `use`/`finally` cleanup.
- `HttpsURLConnection.disconnect()` is best-effort completion cleanup. The
  platform does not guarantee immediate interruption of blocking I/O, and this
  stack provides no general write timeout or guarantee that a blocking output
  write is bounded. No retry is introduced.

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

The original implementation was committed as
`f17058e9a01026a2fa258c05be3501c44fed0e69`, pushed to GitHub, and independently
reviewed in Round 1, which returned **BLOCKED** with 0 BLOCKER / 1 MAJOR /
2 MINOR / 1 NOTE. The Round 2 remediation was committed and pushed as
`66d96d91d0e1207415d9cfc449993df70093d25f`. An independent HIGH-ASSURANCE
re-review completed directly from GitHub at that SHA: the Round 1 MAJOR is
resolved, and the final findings are 0 BLOCKER / 0 MAJOR / 1 MINOR / 2 NOTE,
with verdict **PASS_WITH_NOTES**. The accepted Round 1 cancellation MINOR
documents the partial `HttpsURLConnection` limitation described above. The
remaining MINOR is documentation/provenance only and is corrected by this
documentation-only closeout. Stale governance/provenance was largely corrected
in Round 2; no Round 3 independent security review is required for this
correction.

QAG-004H is not merged into `main`. It remains stopped at validated
`CapturePlan`, with no `ActionExecutor` or automatic mutation. The next gate is
user-authorized commit/push of this documentation-only closeout, followed by
user-authorized merge / final repository closeout. No connected Android suite,
production pairing, provider request, release, VPS, or Tailscale action is part
of this closeout.
