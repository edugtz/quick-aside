# QAG-004 — Android Gateway Integration — QA

Status: **COMPLETE — PASS_WITH_NOTES — INTEGRATED INTO `main`**

## Verified preflight evidence

- Remote repository: `edugtz/quick-aside`.
- `main` HEAD observed at `9bf585404ecb73604ca43420397c4434bd8160e1`.
- The checkout is on the isolated `qag-004-android-gateway-integration` branch.
- `docs/ACTIVE_WORK.md` identifies QAG-003R as complete/integrated and QAG-4 as the next eligible change.
- Current Android manifest has `RECORD_AUDIO` but no `INTERNET` permission.
- Current app has no general HTTP client dependency.
- Current `AIInterpretationRequest` contains only `inputText`.
- Current `ProviderCaptureInterpreter` attaches trusted local `sourceCaptureId` before `CapturePlanValidator`.
- Current `CaptureSubmission` persists through `CaptureWriter.save` before returning `Saved`.
- Current production gateway contract and QA1 canonicalization were re-read directly from `contracts.py`, `pairing.py`, `request_auth.py`, and `app.py`.

## Official platform verification

Current Android documentation was checked for:

- Android Keystore `KeyGenParameterSpec` EC/P-256 signing usage;
- `setAlgorithmParameterSpec(ECGenParameterSpec(...))` and digest authorization;
- `INTERNET` as the normal permission to open network sockets;
- Network Security Configuration / cleartext behavior for API 28+;
- `HttpURLConnection` redirect behavior, which defaults to following redirects unless disabled.

Implementation therefore uses Android Keystore P-256/SHA-256, explicit HTTPS-only configuration, `INTERNET`, and per-connection redirects disabled.

## Deterministic verification

Executed on the actual `qag-004-android-gateway-integration` checkout after
the focused `nonceSource` correction and transport-bound hardening:

- focused `Qa1Test`: **PASS**;
- focused QA1/network/provider/codec/persistence/interpreter JVM tests:
  **PASS**;
- focused bounded-transport regression tests: **PASS**;
- `./gradlew :app:compileDebugAndroidTestKotlin`: **PASS** (device not required; existing deprecation warnings only);
- `./gradlew :app:testDebugUnitTest`: **PASS** (136 JVM tests);
- `./gradlew :app:assembleDebug`: **PASS**;
- `./gradlew :app:lintDebug`: **PASS**;
- `git diff --check`: **PASS**.

The first sandboxed Gradle attempt could not access the existing wrapper cache
outside the workspace; the same focused command was then rerun with the
required environment access and passed. This is environment evidence, not an
application failure.

Required remaining connected/device checks are:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The authorized Oppo became available later in the task. The focused Keystore
test and the live private-gateway flow were then executed; the full connected
suite remains non-green because of an unrelated existing UI-test timing failure
described below.

## Real-device / private-production evidence

Device:

- model: `CPH2791`;
- Android: `16`;
- API: `36`;
- debug APK was installed from the current QAG-004 worktree.

Focused Android Keystore command:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.data.remote.gateway.AndroidKeystoreQa1DeviceIdentityTest
```

Result: **PASS**, 1/1 test on `CPH2791 - 16`.

The real device proved Android Keystore identity stability, P-256 key
generation, private-key non-exportability, public-key PEM parsing, and a
verifiable `SHA256withECDSA` signature.

The pre-pairing capture reached the private gateway, received the expected
authentication failure, remained visible in Memoria, and opened the masked
`Vincular Quick Aside` dialog.

The user generated the one-time pairing code outside the repository and entered
it directly into the device UI. The VPS `list-devices` result showed a redacted
`Quick Aside Android` entry with `status` equal to `active`.

A newly signed capture then completed the real path through the private
gateway and returned the UI receipt:

```text
Captura guardada · interpretación lista, sin aplicar
```

Observed end-to-end device receipt latency was approximately 9 seconds for one
successful interpretation. No interpreted action was executed.

Representative screenshots were captured locally, outside the repository, for:

- masked pairing dialog;
- successful pairing snackbar: `Dispositivo vinculado. Haz una nueva captura para interpretar.`;
- persisted pre-pairing capture;
- successful saved/interpreted-but-not-applied receipt.

The successful-pairing screenshot contains no pairing code or other secret.
The complete visual-evidence set is therefore obtained:

1. masked pairing dialog;
2. pairing-success snackbar;
3. persisted Capture visible in Memoria;
4. `interpretación lista, sin aplicar` receipt.

App-process and recent-device Logcat review found no raw capture text, pairing
code, QA1 canonical request, QA1 headers/signature, private-key material, or
provider credentials. No sensitive material was copied into this document.

## Connected regression result

The full command was attempted twice:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Both full-suite attempts reproduced the same first failure in the existing
unrelated test:

```text
com.edu.quickaside.PendientesUiTest
  unavailableSnapshotBlocksCreatePreservesInputAndRetryKeepsExistingTasks
  ComposeTimeoutException at waitForEnabled("Agregar pendiente")
```

The first run reached 36/251 before the failure; the rerun reached 44/251.
The exact test passes in isolation on the same Oppo:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.PendientesUiTest#unavailableSnapshotBlocksCreatePreservesInputAndRetryKeepsExistingTasks
```

That isolated run was **PASS**, 1/1. This is evidence of a full-suite/device
timing or ordering issue, not evidence of a QAG-004 implementation failure.
The connected regression anomaly is dispositioned below as a non-QAG-004
full-suite/order-timing finding. No unrelated test was weakened.

## Production gate

The production gate was authorized by the user. The one-time code was created
and entered on the Oppo; it is intentionally not recorded here.

The user ran this on the VPS shell to create the five-minute code:

```bash
sudo -u quickaside /opt/quickaside-gateway/gateway/.venv/bin/python \
  -m quickaside_gateway.admin_cli \
  --db /var/lib/quickaside/auth.db \
  create-pairing-code --ttl 300
```

With Tailscale connected on the Oppo, the saved-capture response opened
`Vincular Quick Aside`; the code was entered in the masked `Código de
vinculación` field and submitted. The code was transient and was not persisted
by Android.

Verify registration on the VPS with:

```bash
sudo -u quickaside /opt/quickaside-gateway/gateway/.venv/bin/python \
  -m quickaside_gateway.admin_cli \
  --db /var/lib/quickaside/auth.db \
  list-devices
```

The output contained the newly paired `Quick Aside Android` device with
`status` equal to `active`. Device identifiers and other sensitive registration
material are intentionally omitted.

## Current disposition

QAG-004 runtime and visual evidence is complete for the private Android path.
The full connected-suite anomaly is a non-QAG-004 regression finding and did
not block the QAG-004 implementation status. Independent HIGH-ASSURANCE review
was completed directly against the GitHub branch and returned
**PASS_WITH_NOTES**. The reviewed commit was then integrated into `main` after
explicit user authorization.

## Static disposition of connected-suite anomaly

Finding:

```text
PendientesUiTest
unavailableSnapshotBlocksCreatePreservesInputAndRetryKeepsExistingTasks
```

Evidence:

- The failing test source is unchanged by QAG-004.
- `PendientesScreen.kt` and the task-management production path are unchanged.
- The test injects `QuickAsideApp` directly with `devicePairer = null` by
  default, so the new pairing dialog and interpretation-observation behavior
  cannot activate in this scenario.
- The failure occurs at the test's existing five-second
  `waitForEnabled("Agregar pendiente")` after the test-controlled retry.
- The exact test passed 1/1 in isolation on the same Oppo.
- The full suite reproduced the same first failure twice.
- The later `SearchUiTest` failure appeared after the suite was already
  degraded/cancelled and is not the first root failure.

Disposition: **non-QAG-004 full-suite/order-dependent Compose timing finding**.
No QAG-004 code or test behavior was changed to suppress it. It remains a
repository regression finding for independent review/triage, not a blocker to
the QAG-004 implementation status.


## Independent HIGH-ASSURANCE review

GitHub review baseline:

- base: `main @ 9bf585404ecb73604ca43420397c4434bd8160e1`;
- reviewed head: `87a2715d1da4bde7910b91049a36dbf9a51d9487`;
- branch was 1 commit ahead and 0 behind;
- no GitHub Actions/status checks were reported for the reviewed commit, so
  deterministic/device evidence remains the recorded local/runtime evidence
  above.

Independent findings:

- 0 BLOCKER;
- 0 MAJOR;
- 3 MINOR;
- 3 NOTE.

Accepted MINOR findings:

1. pairing success should verify the returned `deviceId` equals the local QA1
   identity;
2. Android's local validation does not yet mirror every gateway action-count,
   field-size, and list-definition bound; this must be hardened before future
   automatic action execution;
3. `HttpsURLConnection` disconnect-on-job-completion bounds cleanup but does
   not guarantee immediate interruption of blocking I/O.

Accepted NOTEs:

- stale pre-merge documentation was corrected during closeout;
- no dedicated JVM test currently targets `QuickAsideGatewayPairer`;
- GitHub CI/status checks were absent for the reviewed commit.

Final engineering verdict: **PASS_WITH_NOTES**.

The `PendientesUiTest` full-suite timing anomaly was independently reviewed
and accepted as non-QAG-004 based on unchanged task UI/test code, inactive
pairing behavior in that scenario, two full-suite reproductions, and a 1/1
isolated pass on the same Oppo.

## Integration closeout

The exact reviewed commit
`87a2715d1da4bde7910b91049a36dbf9a51d9487` was fast-forwarded into `main`
after explicit user authorization. QAG-004 is closed. No release or additional
production mutation was performed as part of repository closeout.
