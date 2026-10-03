# Quick Aside — Global Acceptance Criteria v0.4

These are product-wide gates. Active changes add narrower acceptance scenarios.

## Capture UX

- High-confidence happy path does not require transcript review, interpretation review, or manual Save.
- Voice and text both reach the same capture/domain pipeline.
- User can correct transcript and interpretation separately.
- Automated changes provide immediate reversible feedback where feasible.
- Low-confidence handling asks the minimum necessary clarification.

## UI/UX consistency

- Any material UI change is reviewed against `docs/UX_UI_REFERENCE.md` and `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
- Main information architecture remains Inicio / Pendientes / Listas / Memoria + global capture action unless product approval changes it.
- UI remains Android-native in behavior and accessible; reference images guide direction rather than override platform correctness.
- Meaningful UI changes receive a visual check appropriate to the touched UI. Emulator screenshots are the default; real-device evidence is required only when the changed property depends on device/platform behavior that reasonable automation cannot prove.

## Persistence

- Durable user data survives app restart/process death.
- Room migrations for user data are explicit and tested.
- No destructive fallback is accepted for durable user data.
- No durable personal information expires or is pruned silently.

## List lifecycle

- Mandado remains session-based with independently retrievable historical sessions; Compras remains continuous and is unaffected by Mandado rollover.
- Mandado's formal weekly period starts Sunday 00:00 local time and reaches its fixed rollover/cutoff at Saturday 14:00 local time.
- Before Saturday 14:00 (for example Saturday 13:59:59), the current period's Mandado remains eligible and is reused.
- At Saturday 14:00 exactly, the ending-period Mandado becomes ineligible for new items, is ended at that boundary exactly once, and remains durably retrievable in history with its items.
- Immediately after Saturday 14:00, Mandado assignment belongs to the next weekly period. There is no inactive window. A capture at Saturday 14:00 or later may materialize/use the next-period Mandado; Sunday 00:00 must not cause a second rollover or create another session.
- A valid high-confidence Capture targeting an eligible Mandado period does not require manual `Iniciar mandado`. If that period has no materialized session, applying the Capture creates exactly one session and its Mandado items atomically; failure/cancellation cannot leave a partial or duplicate session/item result.
- If the app was not running when Saturday 14:00 passed, the next relevant Mandado access/mutation reconciles the boundary idempotently and yields the same lifecycle result as if the app had been running at the cutoff. Correctness does not depend on a background timer firing.
- Passive navigation/history viewing must not create an empty next-period Mandado solely because it was opened, although an already-crossed boundary may be reconciled so the old session is represented as historical.
- Manual `Terminar mandado` before the weekly cutoff ends the current Mandado and makes it historical. For the remainder of that period, Fast Capture must not auto-create/reopen another Mandado. Automatic eligibility resumes only at the next Saturday 14:00 rollover.
- The superseded 7-elapsed-day inactivity/stale/`Continuar`-`Nuevo` policy is not an acceptance path. Age/inactivity alone never selects or revives a Mandado session.
- Calendar rollover, manual Finish, automated mutation, and Undo must not delete or alter unrelated historical Mandado data. Automated Mandado mutations continue to provide targeted reversible feedback where applicable.

## Google Tasks

- Personal/Trabajo tasks are synchronized with the correct Google Task lists.
- Local and external completion/edit/delete behavior follows the active sync contract.
- Offline local mutation remains visible and eventually synchronizes or surfaces a recoverable error.
- No duplicate/corrupt task creation under documented retry scenarios.

## Google Calendar

- Event creation/editing uses the intended Calendar/account and correct date/time/timezone.
- Incremental/background refresh strategy produces acceptable freshness under supported conditions.
- Offline/error states do not silently lose user intent.

## Reminders

- Due date and reminder time remain distinct.
- Scheduled reminders are observed on a real supported Android device under required background/restart scenarios before feature PASS.
- User can snooze.
- Notification actions match record type.
- No engagement/spam notifications are introduced.

## AI interpretation

- Model output is validated before mutations.
- Invalid/unsupported model output cannot directly alter Room/Google data.
- Runtime provider can be switched without migrating user-domain data.
- Primary runtime target is GPT-5.6 Luna Low via the Quick Aside-owned private
  gateway hosted on shared VPS infrastructure; DeepSeek V4 Flash via OpenCode
  Go is an evidence-triggered fallback candidate. Provider changes are driven by
  observed failures, not speculative benchmark work.
- Provider OAuth tokens and credentials are never stored on the Android device.
- Quick Aside provider credentials/auth state are isolated from Personal
  Admin/Hermes.
- A capture is persisted locally before remote interpretation; remote unavailability must not lose user intent or silently discard a capture.
- Remote interpretation cannot directly mutate Room, Google Tasks/Calendar, or local reminders.
- AI does not act as the source of historical truth.
- The gateway must provide fresh/bounded inference without depending on Hermes
  conversation history, memory, prompts, tools, cron, state, or integrations.
- Runtime integration PASS requires measured latency evidence for the real
  fast-capture path against the QAG-1 budget once that budget is established.

## History and retrieval

- Structured logs retain enough fields to answer supported deterministic queries.
- Original/raw capture is retained according to policy when needed to preserve information and correct extraction errors.
- Historical Mandado sessions remain independently retrievable.

## Backup/archive

- User can create a recoverable machine-readable backup before destructive archive/prune becomes available.
- Human-readable export is separate from restoration format.
- Archive/prune never deletes before the required export is successfully written and verified.
- User receives clear warning before any irreversible deletion.

## Security/privacy

- Least-privilege OAuth scopes.
- Secrets are not included in export/backup or logs.
- Diagnostics avoid raw personal/work capture content by default.
- Public distribution cannot use a client-extractable shared provider key architecture.
- Provider credentials and auth state stay on the isolated Quick Aside
  gateway/runtime; the Android client owns no provider secrets.
- Sharing VPS infrastructure with Personal Admin does not permit Quick Aside to
  reuse Hermes/Personal Admin credentials, memory, state, or application data.

## Engineering gates

Before a Change can receive `PASS`, the applicable Change-specific gates must have actual evidence.

Default verification is the minimum sufficient delta:

- automated tests for changed/new behavior;
- directly affected regressions when justified by the touched boundary;
- compile/build/static/schema checks that apply to the changed surface;
- integration/sandbox checks only when an external API or service contract is changed;
- real-VPS or other live-environment checks only when that environment is part of the Change;
- visual checks for material UI changes, normally on the emulator;
- real-device checks only for newly changed device/platform behavior that reasonable automation cannot establish.

Do **not** automatically require full JVM suites, full connected suites, unrelated regressions, repository-wide lint, broad privacy rescans, or broad physical-device acceptance. HIGH-ASSURANCE strengthens verification of the affected risk, provenance, failure-path coverage, and independent review; it does not mean maximizing test count or human involvement.

Hard scope/environment rules:

- Verification and implementation are limited to the current Change. Do not broaden work into unrelated refactors, future roadmap scope, device/auth lifecycle work, or opportunistic cleanup.
- Do not run full JVM suites, full connected/instrumentation suites, repository-wide lint, broad unrelated regressions, or equivalent “run everything” gates unless repository/CI policy, actual cross-cutting blast radius, a concrete failure/reviewer finding, or the user explicitly requires them.
- Do not clean/reset developer or device state merely to get a fresh test run. `git clean`, Gradle `clean`, app-data/Room clearing, credential/identity reset, emulator/device wiping, build/cache deletion, and equivalent cleanup require a Change-specific need or concrete failure/reviewer justification. Test-owned disposable fixtures are allowed.
- QA1 physical-device state is durable. Focused automated connected/instrumentation tests on QA1 are allowed only when the user explicitly authorizes the active Change. Emulator verification remains the default. Before any connected run, inspect `adb devices -l` and select one exact target, preferably with `ANDROID_SERIAL`; do not rely on ambiguous ADB selection or `-Pandroid.injected.device.serial`.
- QA1 test authorization never authorizes destructive state changes. Unless the active Change explicitly tests install/auth lifecycle and separate mutation authorization is provided, do not uninstall/reinstall Quick Aside, run `pm clear`, clear app storage, wipe/reset the device, alter Android Keystore identity, create/revoke/reset/delete/rotate pairing, or generate replacement pairing codes. If QA1 has an auth, pairing, or install-state problem, stop and report rather than repairing it. Manual/user-operated QA is separate from automated QA1 authorization.

A model statement that something works is not evidence. The implementation agent must report exact automated verification commands and results.

### Verification ownership and reuse

For every nontrivial Change, define in orchestration context which gate owns each acceptance property. Use the strongest inexpensive gate that actually proves the property; do not make multiple gates re-prove the same invariant unless they cover a distinct failure mode.

A passed result remains valid while the facts it depends on remain unchanged. Rerun only when there is a concrete invalidator, such as:

- relevant production source changed;
- a materially relevant test seam changed;
- dependency/build/schema/config changed in a way that affects the claim;
- the tested artifact differs from the reviewed artifact;
- a relevant environment changed;
- retained evidence is missing/corrupt/unreviewable;
- a concrete new defect or review finding exposes a failure mode the old gate did not cover.

Not invalidators:

- another chat/session/agent;
- HIGH-ASSURANCE by itself;
- moving from implementation to review;
- commit/push of identical tested source;
- documentation-only edits;
- wanting a newer timestamp.

Use commit SHA/diff and existing test provenance to establish identity. A separate source-manifest artifact is optional and must not be generated by default.

### Manual QA and real environments

User-operated QA is exceptional. Request it only when the Change modifies a material property that inherently requires genuine human or unavailable external interaction and no reasonable automated substitute proves it. Before asking the user, identify the exact newly changed property, why automation is insufficient, and the single minimal human action required.

Voice support, Android use, networking, or physical-device availability alone do not make human QA mandatory. Reuse historical acceptance for unchanged subsystems.

QA1 pairing, installed-app state, app data, and Android Keystore identity on the physical Android phone are durable environment state. Focused automated QA1 connected/instrumentation tests require explicit active-Change authorization, an exact target, and focused filters; emulator verification remains the default. That authorization never permits destructive mutation. Unless the active work explicitly tests the lifecycle and separate mutation authorization is provided, never revoke/reset/delete/recreate/rotate pairing, uninstall/reinstall the app, run `pm clear`, clear app storage, wipe/reset the device, or alter auth/install/identity state. Stop and report QA1 auth, pairing, or install-state problems. Manual/user-operated QA requires separate authorization.

### Documentation and evidence

Normal Changes do not create `docs/changes/<id>/` packages or evidence trees by default. Change-local specification, planning, task decomposition, verification notes, and implementation reports remain ephemeral orchestration context.

Persist repository documentation only when the Change alters a durable project contract. Keep a separate evidence artifact only when it has lasting review value that cannot reasonably be represented by source/tests, CI, or the implementation report.

Once the implementation and required Change-specific automated verification are complete, the Change is commit-ready. Optional manual QA, documentation polish, and independent review do not delay that provenance checkpoint. The user retains commit/push/merge/release authority.
