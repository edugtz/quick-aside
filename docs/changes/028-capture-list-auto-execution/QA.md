# CHG-028 QA and Evidence Record

- Governance: **HIGH-ASSURANCE**
- Status: **IMPLEMENTED — TEXT AND REAL-HUMAN VOICE DEVICE ACCEPTANCE PASS; REVIEW PENDING**
- Branch: `chg-028-capture-list-auto-execution`
- Verified base: `6ede3d08f298a376cfdfd7749fc2d92a2eca3f5c`
- At the time acceptance evidence was recorded, local HEAD remained the base
  SHA; the user subsequently authorized the CHG-028 commit and push.

## Baseline and device

- `git fetch origin main` completed after repository-write authorization.
- Fetched live GitHub `main`, `origin/main`, and clean starting `HEAD` matched
  the verified base exactly.
- Device: OPPO CPH2791, Android 16, API 36; ADB state was `device`.
- Room baseline and final state are version 7 with migrations through 6→7.

## Deterministic verification

| Gate | Exact command | Result |
|---|---|---|
| Existing focused submission regression / initial compile | `./gradlew :app:testDebugUnitTest --tests com.edu.quickaside.application.capture.CaptureSubmissionTest --tests com.edu.quickaside.application.capture.CaptureSubmissionRemoteIntegrationTest` | PASS. Initial default-sandbox wrapper attempt could not open the external Gradle cache lock; the identical approved-cache rerun built successfully. |
| Focused CHG-028 orchestration + submission regressions | `./gradlew :app:testDebugUnitTest --tests com.edu.quickaside.application.capture.CaptureSubmissionExecutionTest --tests com.edu.quickaside.application.capture.CaptureSubmissionTest --tests com.edu.quickaside.application.capture.CaptureSubmissionRemoteIntegrationTest` | PASS: 17/17 total (9 CHG-028, 6 existing submission, 2 existing remote integration), 0 failed/errors/skipped. |
| Android-test compile before final UI test source | `./gradlew :app:compileDebugAndroidTestKotlin` | PASS. |
| Android-test compile after final UI test source | `./gradlew :app:compileDebugAndroidTestKotlin` | First run failed only on two invalid Compose test imports; one focused import correction was applied. Rerun PASS. |
| Real-Room CHG-028 pipeline | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.data.local.CaptureSubmissionListExecutionDatabaseTest` | PASS on CPH2791 / Android 16: 4/4, 0 failed, 0 skipped. |
| Focused CHG-028 Compose class | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.CaptureListAutoExecutionUiTest` | BLOCKED before feature assertion. The first of 8 methods reported no Compose hierarchy; the runner was manually interrupted after it did not finish unwinding. |
| Exact failed Compose method | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.CaptureListAutoExecutionUiTest#executorRejectionShowsNonSuccessCopyWithoutUndo` | BLOCKED 1/1 with the same no-Compose-hierarchy startup condition. Device diagnostics immediately afterward showed `mWakefulness=Dozing`, active dreaming lockscreen, and NotificationShade focus. No third attempt was made under the two-attempt rule. |
| Full JVM | `./gradlew :app:testDebugUnitTest` | PASS: 174/174 across 28 reports, 0 failed/errors/skipped. |
| Debug assembly | `./gradlew :app:assembleDebug` | PASS. |
| Lint | `./gradlew :app:lintDebug` | PASS: 0 errors, 16 warnings, 2 hints. |

The focused Room test proves:

- eligible Capture persistence followed by real atomic list execution;
- exact returned ListItem IDs, ordered ledger mutations, and source Capture;
- mixed unsupported plan preserves Capture and adds no items/ledger entry;
- Mandado with no active session preserves Capture and writes nothing;
- exact returned ledger/item identities drive targeted Undo, delete the exact
  batch, mark the ledger undone, and preserve the original Capture.

## Continuation run — connected gates completed

Resumed from the same unmodified worktree (production sources last modified
18:50–18:56, test sources 18:54–18:56) with the OPPO CPH2791 / Android 16 /
API 36 awake, charging, screen on, and app focus confirmed before any
production pairing. The two rows above recording BLOCKED Compose attempts are
superseded by the passing focused run below; those attempts failed only on the
dozing-lockscreen startup condition, not on a product/test defect.

| Gate | Evidence | Result |
|---|---|---|
| Directly affected existing device regressions | One five-class connected run; combined report `TEST-CPH2791 - 16-_app-.xml` inspected at 19:40 local before the later focused run replaced the same output directory | PASS: **35/35**, 0 failed/errors/skipped, `time=48.308`, report timestamp `2026-09-20T01:26:27`, exit code 0. Exact classes/counts: `QuickAsideAppTest` 1 (`inicioMicrophoneOpensCaptureWithoutAddingNavigationDestination`); `CaptureTextSubmissionTest` 2 (`enteredTextShowsReceiptClearsFieldAndUsesRealRoomTestDatabase`, `failedPersistenceKeepsEnteredTextAndShowsError`); `VoiceCaptureTest` 10 (including `finalTranscriptCreatesExactlyOneVoiceCaptureAndPreservesItExactly`, `successfulVoiceCaptureClosesSurfaceShowsReceiptAndIsVisibleInMemoria`); `MandadoUiTest` 12 (including `addFailureRetainsInputAndShowsError`, `exactItemTextUsesReversibleBoundaryShowsReceiptAndUndoRemovesExactItem`, `backFromMandadoReturnsToListasRoot`, `listasExposesInteractiveMandadoAndComprasWithoutAFifthDestination`); `ComprasUiTest` 10 (including `bottomNavigationSwitchResetsNestedListRoute`, `androidBackFromComprasReturnsToListasRoot`, `exactTextUsesReversibleBoundaryWithNullSessionAndUndoRemovesExactItem`). |
| Focused CHG-028 Compose class (previously BLOCKED) | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.edu.quickaside.CaptureListAutoExecutionUiTest` | PASS: **8/8**, 0 failed/errors/skipped, `time=11.738`, report timestamp `2026-09-20T01:45:08`, exit code 0. Methods: `textSingleExecutionShowsMutationReceiptAndUndo`, `textBatchShowsAccurateCountAndForwardsOneExactOrderedUndo`, `unsupportedValidPlanKeepsHonestNotAppliedCopyWithoutUndo`, `executorRejectionShowsNonSuccessCopyWithoutUndo`, `executorFailureShowsNonSuccessCopyWithoutUndo`, `undoFailureDoesNotClaimSuccessAndShowsGenericError`, `voiceUsesSameReceiptExecutesOnceAndClosesCaptureSurface`, `globalVoiceCaptureRefreshesVisibleComprasAndUndoRemovesTheExactItem`. |
| Real-Room pipeline class | `CaptureSubmissionListExecutionDatabaseTest` from the deterministic table above | PASS 4/4 retained; not rerun because source/test revision is unchanged. |

No production, test, schema, dependency, gateway, or configuration file changed
during this continuation. Full JVM (174/174), debug assembly, lint,
Android-test compile, schema/config comparisons, and the real-Room gate were
not rerun because their passing evidence from the same revision stands.

The eight focused Compose scenarios now have passing execution evidence
(`CaptureListAutoExecutionUiTest` 8/8). They cover single and batch receipts,
one exact Undo, unsupported/rejected/failed execution, Undo failure, voice
parity/exactly-once/close behavior, and visible Compras refresh after
execution and Undo.

## Production pairing prerequisite — historical, superseded

The earlier post-install attempt reached the production authentication
boundary and required pairing for that reinstalled identity. That historical
state is superseded by current production evidence: the user reported three
successful text executions, and the local Room database contains three prior
capture-linked execution ledgers. The current controlled text execution also
reached the executor. The QA1 identity was therefore treated as active; no
pairing dialog was open, and no re-pairing or pairing-code operation was
performed in this continuation. No gateway, VPS, Tailscale, provider, or auth
implementation was changed.

## Historical real-production acceptance attempt — partial evidence (pre-re-pairing; superseded)

After the deterministic gates and before re-pairing, one unique disposable
text capture was submitted through the real production path:
`Agrega servilletas QA028-T1 a la lista de compras`.

Observed, all on the reinstalled debug app against the private gateway:

- **Persistence-first holds:** the Capture is present in `Memoria` even
  though interpretation failed at the authentication boundary.
- **Correct auth boundary:** the gateway rejected the unpaired new identity
  and the app opened the masked "Vincular Quick Aside" dialog; this is a
  pairing gate, not an interpreter failure.
- **Zero mutation:** `Compras` still showed `Aún no hay productos.` after the
  rejected attempt; no item was created and no execution ran.
- **Privacy:** the app-process Logcat window for the attempt (pid 7203)
  contained zero matches for the capture text, QA/device auth headers,
  signature material, or provider request bodies; only system/UI noise
  (InsetsController, DynamicFramerate, WindowOnBackDispatcher, etc.).
- Screenshots retained outside the repository at
  `/tmp/chg028/memoria_rejected.png` and `/tmp/chg028/compras_rejected.png`.

The masked pairing dialog was then reopened with a disposable probe capture
and left open on the device so the user can enter a fresh pairing code. Once
the current identity is active, the real disposable text and voice acceptance
is repeated from a new capture, as the app prompts: "Dispositivo vinculado.
Haz una nueva captura para interpretar."

This describes the earlier unpaired identity only. The pairing prompt and
next-capture instruction were superseded before the current controlled run.

## Historical controlled production acceptance — text Undo incomplete (superseded)

The device had gone into doze. It was woken and unlocked with a swipe; the
installed app and QA1 identity were left intact. No tests, installs,
uninstalls, `pm clear`, source edits, JDWP debugging, or repeated captures were
performed.

Exactly one controlled text capture was submitted:

`Agrega servilletas QA028-T-FINAL a Compras`

Read-only Room inspection after execution found:

- Capture `b292d2f8-5b4b-4e69-80b9-3ee6570f35e2`, kind `TEXT`, with the exact
  original text above; the Capture remains stored.
- One linked ledger entry `6bea4c2a-236b-45ba-b15c-67d935c237e3`, with one
  mutation at position 0: `CREATE` / `list_item`, targeting
  `084ec99d-98bc-478a-a813-a6d845b651c7`.
- That item appears once in `compras` as `servilletas QA028-T-FINAL`.
  Compras increased from 1 to 2 items; Mandado remains at 0. Both lists have
  zero active sessions. Compras is `CONTINUOUS`, so no session was required.
- The success receipt and the linked `CREATE list_item` mutation targeting
  `compras` are local evidence of AddListItem execution. Room does not retain a
  separate serialized plan object for direct inspection.

The UI exposed `Producto agregado` and `Deshacer` in the post-submit
accessibility hierarchy. The action expired before it was invoked. The current
ledger row has `undone_at_epoch_millis = NULL`, and the item remains in
Compras. Therefore the text control did not complete its required Undo and is
not a PASS. The later screenshot
`/tmp/chg028/text_compras_list_detail.png` visibly shows the item in Compras;
`/tmp/chg028/text_success_receipt.png` was captured after the transient receipt
was no longer visible, so it is not receipt evidence. The original Capture
remains in Room; no duplicate marker item exists.

That run's stop state is superseded by the 2026-09-20 continuation below.
Existing prior batch screenshots remain at
`/tmp/chg028/batch_receipt.png` and `/tmp/chg028/batch_post_undo.png`; this run
did not repeat or alter that acceptance.

## CHG-028 acceptance evidence reconciliation — 2026-09-20

| Acceptance evidence | Status | Evidence / remaining check |
|---|---|---|
| TEXT execution | **PASS** | One Capture, one Compras CREATE, success receipt, and no duplicate. |
| TEXT Undo | **PASS** | `Deshacer` succeeded; item absent and Capture retained in Room. |
| VOICE human execution | **PASS** | Human VOICE Capture links to exactly one CREATE mutation; the execution screenshot shows the target in Compras, and the Capture remains after Undo. |
| VOICE Undo | **PASS** | Latest human VOICE Capture has one CREATE mutation; the ledger is marked undone and its target no longer exists. User screenshot shows `Cambio deshecho`. |
| Visual evidence | **PASS** | Text success/Undo/Capture screenshots, existing Compras execution screenshot, and user-provided human voice Undo screenshot are retained as evidence. |
| Privacy evidence | **PASS** | Previously recorded post-voice 32-line app-process Logcat sample has zero targeted transcript/item, auth/signature, API-key, request-body, or prompt matches; no logging-sensitive behavior changed. |

Latency and UI responsiveness observations are recorded below. All requested
device acceptance evidence is complete. The user authorized the CHG-028
commit/push; the next gate after push is independent HIGH-ASSURANCE review.

### Environment correction supplied by the user

- The earlier `Captura guardada · interpretación no disponible` occurred
  because Tailscale was disabled on the OPPO. After Tailscale was enabled, both
  real text and real-human voice captures reached the private gateway and
  executed successfully in Compras. This was an environment/preflight failure,
  not a CHG-028 production defect. The previous ProviderFailure was not
  investigated further.
- NOTE — without the required private Tailscale route, Quick Aside currently
  surfaces generic `interpretación no disponible` feedback. This is
  environmental UX feedback debt, not a CHG-028 execution defect.
- NOTE — the accepted text flow took approximately **7 seconds** from
  submission to success receipt. Remote interpretation latency is perceptible
  and should be evaluated separately after CHG-028; do not redesign the async
  flow in this change.
- NOTE — during the text wait, the UI appeared to be waiting in the capture
  state rather than frozen or unresponsive. No stress-tapping was performed.

### Controlled text acceptance

The successful text acceptance used one unique marker:

`Agrega pan QA028-TEXT-ACCEPT-20260920 a Compras`

- Room contains exactly one matching `TEXT` Capture:
  `c29df422-79a6-4eab-8fd7-ad5513d663d5`. It remains after Undo.
- The Capture has exactly one linked Action Ledger entry:
  `eb4c4fa5-42b2-4021-855c-fda30ebfde81`, with exactly one position-0
  `CREATE` mutation of target type `list_item`.
- The visible receipt was `Producto agregado`, with one `Deshacer` action.
  The action was invoked about one second after the receipt was detected. The
  UI then showed `Cambio deshecho`.
- Read-only Room evidence shows the ledger entry has a non-null
  `undone_at_epoch_millis`, the target item no longer exists in `list_items`,
  and the original Capture remains. Compras contained no duplicate of this
  marker after the Undo.
- Approximate submission-to-receipt latency: **7 seconds**. This is a
  perceptible wait, not a failed request. No visible freeze or unresponsive
  state was observed; the app remained on Inicio/Captura while the request was
  pending and displayed the receipt. The UI was not stress-tapped during the
  wait.
- Visual evidence: `/tmp/chg028/text_retry_receipt.png` shows the success
  receipt and Undo; `/tmp/chg028/text_retry_undo.png` shows `Cambio deshecho`.
  `/tmp/chg028/text_capture_remains_memoria.png` shows the accepted Capture
  still listed in Memoria after Undo.
  The screen uses the existing light Material surface, the global capture
  action, and the established concise snackbar pattern; the receipt remains
  lightweight and consistent with the accepted v3 direction.

### Exploratory text attempt and remaining test data

An earlier text attempt in this continuation used
`Agrega leche QA028-TEXT-0920 a Compras`. Its Capture persisted and one item
was created, but its transient Undo expired before the tap. The matching item
and active ledger entry remain in Compras; this is a distinct test marker, not
a duplicate of the accepted text marker. The Capture ID is
`b8b33eef-feb5-4e80-a748-db429b772354`; the active ledger ID is
`327ffb9b-b46c-45a6-bbc5-da8b8ff738ce`. This side effect is left intact and
reported rather than removed outside the acceptance Undo path. The earlier
`QA028-T-FINAL` item from the previous controlled run also remains as recorded
above.

### Real-human voice execution and Undo acceptance

- The newest human VOICE Capture for the coordinated Undo acceptance is
  `f9bcffbf-429e-424d-9981-fe2184405f3d`, captured at
  `2026-09-20T18:34:06-06:00`. Its exact stored transcript is
  `agregar uvas moradas a compras` (`corrected_transcript` is null). The
  Capture remains persisted after Undo.
- It has exactly one linked ActionLedgerEntry:
  `16b4bd34-c05a-4aaa-ac10-4b9e8d48bdda`. The ledger contains exactly one
  mutation: position 0, `CREATE`, target type `list_item`, target ListItem ID
  `b044cec6-1b94-47a4-9f56-426a35f78bf7`. The existing Compras execution
  screenshot identifies the target list as `listDefinitionId=compras`. The
  current Room database no longer has the deleted item row; this executor's
  CREATE mutation stores null before/after state, so the list ID is established
  by that execution screenshot rather than a remaining item row.
- The ledger's `undone_at_epoch_millis` is populated at
  `2026-09-20T18:34:14-06:00`. The target item ID has **0** current rows, the
  `uvas moradas` text has **0** current matches in Compras, and exactly one
  mutation row references the target ID. Thus the target was undone, no
  duplicate item remains, and no duplicate mutation was recorded.
- The user-provided screenshot
  `/var/folders/6_/wvl8zb0936x0d5jsdxbqs8s80000gn/T/codex-clipboard-71d4390f-1596-406d-9831-a798be0af2b3.png`
  visibly shows `Cambio deshecho`. Together with the read-only Room state, this
  closes real-human voice execution and Undo: **PASS**.
- The earlier human voice execution Capture
  `b7bd11ad-e075-45b6-9494-b2e8737fa41f` (`comprar Giovanni en Costco`) is a
  separate prior run. Its one `CREATE/list_item` ledger remains active and its
  item `Giovanni en Costco` exists once in Compras; it is not the target of the
  coordinated Undo. The existing Compras screenshot also shows
  `chocolate de prueba`; Room provenance maps that item to TEXT Capture
  `eab060c3-d62b-4459-803b-76ca002dda2d`, transcript
  `agregar chocolate de prueba a compras`, not to a VOICE Capture.
- The later workstation-synthesized artifact
  `04eed633-87b3-4cc8-8283-a3a24869b5c3` (`comprar carne`) still has no linked
  ActionLedgerEntry or mutation. It is not acceptance evidence; no synthesized
  speech was used during this closeout.
- The earlier successful human voice execution took approximately **8
  seconds** from Capture at 17:25:43 to ledger/item creation at 17:25:51. For
  the coordinated Undo run, Capture at 18:34:06 reached ledger execution at
  18:34:12, approximately **6 seconds**, then Undo completed at 18:34:14.
  These durable intervals are perceptible waits, not failures.
- The UI appeared to wait in the capture/saving flow rather than freeze or
  become unresponsive. No UI freeze was observed.
- No new capture was initiated during this documentation closeout. No install,
  re-pair, app-data clear, source edit, test, or connected test was performed.

### Visual and privacy evidence

- The accepted v3 visual reference was inspected. The observed text flow keeps
  the existing four management destinations and global capture affordance;
  the success and Undo receipts use concise Material snackbar feedback, with
  no new route, modal, or mandatory review step.
- Visual evidence is **PASS**: `/tmp/chg028/text_retry_receipt.png`
  shows the success receipt and `Deshacer`;
  `/tmp/chg028/text_retry_undo.png` shows `Cambio deshecho`; and
  `/tmp/chg028/text_capture_remains_memoria.png` shows the Capture retained.
  The existing Compras execution screenshot shows the real-human voice target
  in Compras. The user-provided voice Undo screenshot above shows
  `Cambio deshecho`.
- App-process Logcat snapshot `/tmp/chg028/logcat_acceptance_all.txt` was
  captured at approximately `2026-09-20 17:45:46-06:00`, after the human voice
  Capture at 17:25. Count-only scanning found 0 matches for the exact voice
  transcript/item name and keywords, capture/transcript/raw-text patterns,
  authorization/bearer, QA1/signature, API-key, and provider/request-body or
  prompt patterns. The snapshot contains 32 app-process lines. No raw logs,
  secrets, or capture payloads were printed or added. Privacy evidence is
  **PASS** for this sampled app-process log. The coordinated Undo introduced
  no new logging-sensitive behavior, so this previously recorded privacy PASS
  is retained without a new logging run.

## Schema, dependency, security, and privacy

- Room: version 7 before and after; no schema/entity/table/index/foreign-key or
  migration change; `app/schemas` unchanged.
- Dependencies, app manifest, backup/network-security XML: unchanged.
- Gateway, VPS, Tailscale, QA1, provider/auth, transport, prompt/schema, and
  remote client code: unchanged.
- No new logging was added. No provider request/body/auth data is logged.
- No secret or credential was added.

No commit, push, merge, release, or future Change work was performed during
the acceptance evidence closeout.
