# QAG-004H — Android Gateway Client Hardening — PLAN

## Approach

1. Start from the verified GitHub `main` base and isolate the work on
   `qag-004h-android-gateway-client-hardening`.
2. Add the provider-neutral `CapturePlanContract` limits in the domain layer.
3. Keep `GatewayJsonCodec` structural and route semantically representable
   bounds, supported list IDs, and existing blank checks through
   `CapturePlanValidator`.
4. Make `QuickAsideGatewayPairer` compare the decoded active response identity
   with the local QA1 identity before returning success.
5. Add explicit activity checks around `HttpsURLConnection` blocking work while
   retaining platform networking, resource cleanup, no-retry behavior, and
   bounded connect/read timeouts.
6. Add focused JVM tests for pairing, validation boundaries, order, and
   cancellation, then run the required regression/build/lint checks.
7. Reconcile only stale QAG-3/QAG-4 status wording in long-lived architecture
   documentation and record the exact cancellation limitation.

## Responsibility boundary

`GatewayJsonCodec` answers whether bytes can be structurally represented as a
`CapturePlanDraft`. `CapturePlanValidator` answers whether that draft is within
Quick Aside's provider-neutral domain contract. The validator is the only path
that produces `CapturePlanValidationResult.Valid` for gateway output.

## Failure and rollback strategy

- A malformed, mismatched, or out-of-bounds remote response remains an explicit
  pairing/validation failure.
- Cancellation continues to propagate and never maps to a provider or pairing
  failure.
- No local data is deleted, migrated, rewritten, or executed.
- Rollback is a normal source revert; no database or gateway migration exists.
- If the server contract or domain contract must change, stop and report rather
  than broaden QAG-004H.

## Verification order

1. Focused pairing, validator, codec, provider, and transport-safe JVM tests.
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:compileDebugAndroidTestKotlin`
4. `./gradlew :app:assembleDebug`
5. `./gradlew :app:lintDebug`
6. `git diff --check`

The full connected Android suite is not repeated for this JVM/client-hardening
change. No Keystore production code is changed, so no new real-device gate is
required unless implementation evidence changes that assessment.

## Implementation closeout

The planned implementation is complete on the isolated branch. Deterministic
JVM tests, Android-test source compilation, debug assembly, lint, and diff
whitespace verification have passed. The change stops at
`IMPLEMENTATION COMPLETE — REVIEW PENDING`; no commit or push was performed.
