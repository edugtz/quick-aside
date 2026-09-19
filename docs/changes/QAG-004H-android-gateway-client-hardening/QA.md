# QAG-004H — Android Gateway Client Hardening — QA

Status: **REMEDIATION ROUND 2 — DETERMINISTIC PASS — RE-REVIEW PENDING**

## Verified preflight

- Repository: `edugtz/quick-aside`.
- GitHub `origin/main`: `9114af73b96fd53a65423beba71d2d645aac8876`.
- Clean local `main` was fast-forwarded to that commit before the isolated
  branch was created.
- Working branch: `qag-004h-android-gateway-client-hardening`.
- Reviewed implementation commit:
  `f17058e9a01026a2fa258c05be3501c44fed0e69`, pushed to GitHub.
- QAG-004 is integrated into `main` with independent verdict
  `PASS_WITH_NOTES`.
- Accepted findings in scope: pairing identity binding, local plan bounds, and
  bounded `HttpsURLConnection` cancellation cleanup.
- Round 1 review disposition: **BLOCKED**, 0 BLOCKER / 1 MAJOR / 2 MINOR /
  1 NOTE.

## Round 2 remediation evidence

`GatewayJsonCodec` now reads every public-contract string with a raw
`JSONObject.get()` plus an explicit Kotlin `String` type check. This covers pair
response `deviceId`/`status`, action `type`, AddListItem fields, CreateTask
`space`/`title`/non-null `dueDate`, CreateNote `text`, and each structured-log
value. JSON null remains the only non-string representation accepted for the
nullable due date.

Focused codec, pairing, provider, validator, and transport tests pass: 55 tests,
0 failures, 0 errors, 0 skipped. Regressions cover primitive/null note text,
non-string list/task fields, every non-string due-date shape, structured-log
values, action type, pair response fields, the provider/interpreter boundary,
and oversized string decode followed by validator rejection.

## Deterministic evidence

Focused `QuickAsideGatewayPairerTest` covers matching identity success,
mismatched/blank/invalid-status/malformed response rejection, documented HTTP
and transport mappings, and cancellation propagation.

`CapturePlanValidatorTest` covers the 16/17 action boundary, both supported list
definitions, unsupported IDs, exact and max+1 text/key/value boundaries, 32/33
structured-log fields, retained blank validation, action order, and rejection
before `CapturePlanValidationResult.Valid`. `GatewayJsonCodecTest` verifies a
semantically representable oversized value remains a draft and fails in the
validator rather than in structural decoding.

Existing QA1, request/response codec, provider mapping,
persistence-before-network, provider-failure retention, and Android Keystore
instrumentation source compatibility tests remain covered.

Executed successfully:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
git diff --check
```

Round 2 result: all commands pass. The full JVM task executed 161 tests with
0 failures, 0 errors, and 0 skipped tests.

## Transport evidence and limitation

The implementation retains explicit connect/read timeouts, closes streams and
the connection in `use`/`finally`, disconnects as best-effort job-completion
cleanup, and checks coroutine activity after blocking I/O so cancellation cannot
become a successful coroutine result once that work returns. The platform does
not guarantee immediate interruption, and `HttpsURLConnection` provides no
general write timeout or guarantee that a blocking output write is bounded. No
retry, replacement HTTP stack, or speculative thread/interruption machinery is
in scope.

## Required commands

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
git diff --check
```

The original implementation commit was pushed and reviewed. This Round 2
remediation remains uncommitted and unpushed. No connected-suite rerun,
production pairing, provider request, VPS/Tailscale change, merge, or release is
performed. The existing full-suite `PendientesUiTest` anomaly is not reopened
because no UI or platform behavior requiring device evidence changed.
