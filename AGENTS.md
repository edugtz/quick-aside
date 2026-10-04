# Quick Aside — Agent Operating Rules

Quick Aside is a personal-first Android utility for zero-friction capture and explicit management of structured personal information.

## Source-of-truth order

When sources disagree, use this order unless the user explicitly overrides it:

1. The user's latest explicit product decision.
2. The active Change's orchestration context and current branch/diff, when one exists.
3. `docs/PROJECT_SPEC.md` for accepted product behavior and scope.
4. Accepted ADRs under `docs/adr/` for durable architectural decisions and supersession history.
5. `docs/NAMING.md` for the accepted product name and legacy-codename rule.
6. `docs/ARCHITECTURE.md` for system boundaries and invariants.
7. `docs/UX_UI_REFERENCE.md` + `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png` for UI/UX intent.
8. Actual repository code/manifests/tests for current implementation facts.
9. Platform/vendor documentation for API behavior.

Do not re-plan from zero when `docs/ACTIVE_WORK.md` already points to active work.

## Product naming hard rule

- Accepted product/project name: **Quick Aside**.
- `VoiceApp` is an obsolete pre-bootstrap codename and MUST NOT be introduced in new UI copy, code identifiers, repository names, or external-service identifiers.
- Read `docs/NAMING.md` before creating package/application IDs or brand-facing assets.
- The current v3 PNG may still contain the legacy name inside the image pixels; ignore that embedded label.

## Governance

Use `software-project-orchestrator` proportional governance:

- QUICK: trivial, local, reversible fixes with no durable contract impact.
- STANDARD: normal features, bugs, integrations, UI flows, persistence, or multi-file changes.
- HIGH-ASSURANCE: auth/permissions, data-loss risk, migrations, destructive archive/prune, sync correctness/idempotency, live VPS/network/runtime changes, release/cutover, or platform behavior requiring real-device evidence.

The user retains product, merge, and release authority by default. Successful builders may receive delegated commit/push authority under the successful-publication workflow below; the user retains merge, protected-branch, release, and intervention authority.


## Repository/device safety and Change-scope hard rule

These are hard constraints, not cleanup preferences:

- Work is limited to the **current active Change**. Builders/reviewers MUST NOT add unrelated refactors, future-roadmap work, opportunistic cleanup, auth/device lifecycle work, or other “while we are here” changes.
- Routine work MUST NOT reset or clean developer/device state merely to obtain a “clean” test environment. Do not run `git clean`, delete working-state data, clear Room/app storage, wipe credentials/identity, reset emulator/device state, delete build/caches, or run Gradle `clean` unless the active Change itself requires that operation or a concrete failure/reviewer finding makes it necessary. Disposable test fixtures/databases that are created and owned by the test are allowed.
- The physical QA1 Android phone is **durable environment state**, not disposable test infrastructure. Beginning with CHG-034, QA1 OPPO is the default target whenever the active Change has an applicable connected/instrumentation/UI/device-automation/visual gate. Use an emulator only when QA1 is genuinely unavailable or a concrete technical/property-specific reason makes emulator execution more appropriate; report that reason. TEST THE CHANGE DELTA remains authoritative: Android involvement alone does not create a gate. JVM/unit, compile/build/static, and schema properties retain their appropriate host-side owner; do not repeat already-proven Room/JVM invariants on QA1. This policy is non-retroactive: CHG-033 emulator evidence remains accepted and valid, with no rerun or additional gate required.
- Before any connected execution, inspect `adb devices -l` and explicitly select one exact target with `ANDROID_SERIAL`. When QA1 is connected/available and an applicable Android gate exists, use the exact connected OPPO serial and focused Change-specific filters. Do not rely on ambiguous ADB selection or `-Pandroid.injected.device.serial` as the target-selection mechanism.
- QA1 pairing, installed-app state, app data, and Android Keystore identity are durable environment state. Normal focused instrumentation mechanics that preserve app data/auth/pairing/identity are allowed. Routine QA1 verification MUST NOT uninstall/reinstall Quick Aside as cleanup, run `pm clear`, clear app storage, wipe/reset the device, reset Room/user state, delete/regenerate/rotate Android Keystore identity, revoke/reset/delete/recreate/rotate pairing, generate replacement pairing codes, or mutate auth lifecycle merely to obtain test evidence. Lifecycle mutation requires both explicit active-Change scope and separate mutation authorization. If QA1 has an auth, pairing, or install-state problem, STOP AND REPORT; do not repair it as routine verification. Manual/user-operated QA remains exceptional and requires separate authorization.
- Full JVM suites, full connected/instrumentation suites, repository-wide lint, broad unrelated regressions, privacy rescans, and other generic “run everything” gates MUST NOT be run by default. They require a repository/CI mandate, an actual cross-cutting blast radius, a concrete failure/reviewer finding, or the user's explicit request.
- THE ACTIVE CHANGE OWNS ITS VERIFICATION. Required automated verification is limited to focused tests for behavior introduced or changed by the active Change, focused directly affected regressions only when there is a concrete Change-specific reason, and applicable build/static/schema checks. Do not execute a historical test class or suite merely because an earlier Change created it, the current Change touches the same method, it offers generic regression confidence, or it was prior evidence; touching the same production method alone is insufficient. Reuse prior-Change evidence for unchanged behavior. A prior-Change test/class may run only for a concrete observed failure, reviewer finding, genuine cross-cutting blast radius, repository/CI policy, or explicit user request.

## UI/UX hard rule

Any change that creates or materially alters UI/UX MUST, before implementation:

1. Read `docs/UX_UI_REFERENCE.md`.
2. Inspect `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`.
3. Identify which accepted UX invariants are affected in the active Change's orchestration context.
4. Preserve the visual/product direction unless the user explicitly approves a deviation.
5. Verify material visual changes with the smallest suitable visual check; beginning with CHG-034, use QA1 OPPO by default for an applicable visual/device gate. Emulator fallback requires genuine QA1 unavailability or a concrete technical/property-specific reason, which must be reported.

The image is a **canonical visual-direction reference, not a pixel-perfect implementation spec**. Do not blindly copy generated text, dimensions, or impossible controls. Prefer native Android/Material behavior, accessibility, touch targets, responsive layout, and the written UX contract when the image is ambiguous.

For generative visual assets, follow the project AI workflow: Google AI Plus primary when available; ChatGPT image generation fallback. Standard semantic UI icons should come from Material/platform icon sets rather than generated artwork.

## Core UX invariants

- Primary happy path: **invoke → speak/type → interpret/save → lightweight receipt → continue**.
- Transcript review and interpretation review are optional; they are never mandatory gates for high-confidence captures.
- Low confidence asks the smallest possible clarifying question instead of opening a large editor.
- `Capture` is an action, not a permanent navigation destination.
- Main management destinations: `Inicio`, `Pendientes`, `Listas`, `Memoria`.
- Voice is primary, text is first-class.
- Undo must remain readily available after automated changes.
- UI should feel Android-native with custom personality, not like a generic AI chat app.
- The voice orb is a capture-state/brand motif, not the center of every management screen.

## Data invariants

- AI interprets data; it is not the system's memory.
- User data is stored as structured records with original/raw capture retained when useful.
- Never silently delete durable personal information.
- Never use destructive Room migrations for user data.
- Archive/prune must require a verified export/backup before destructive deletion.
- Google Tasks synchronization is a must-have for Personal/Trabajo tasks.
- Google Calendar synchronization is a must-have for events.
- Local reminders are the MVP mechanism for user-configured Note/Task reminders; FCM is deferred.

## Runtime AI policy

Runtime interpretation remains provider-abstracted. QAG-1 is now
**complete — PASS**.

Current runtime direction for the personal MVP:

1. GPT-5.6 Luna — explicit Low reasoning — primary model via ChatGPT Plus /
   Codex OAuth.
2. First gateway provider invocation: `codex exec --ephemeral`, one fresh
   bounded process per interpretation request.
3. The runtime uses a Quick Aside-specific `CODEX_HOME`, ignores unrelated
   user/project Codex config/rules for the invocation, uses a read-only
   sandbox, and requires strict structured output.
4. DeepSeek V4 Flash via OpenCode Go remains a fallback candidate only,
   evidence-triggered.
5. MiMo-V2.5 is not primary; the completed runtime evaluation observed
   worse schema/contract reliability than the finalists.
6. No automatic reasoning escalation. Fallback, if implemented, is for
   eligible provider/runtime failure classes rather than semantic
   disagreement.

The persistent Python SDK/app-server route was proven but is not selected
for v1 because QAG-1 observed increasing resident memory across fresh
ephemeral threads on the target VPS. It remains a future alternative if
requirements change.

Provider credentials and auth state stay on the isolated Quick Aside
gateway/runtime side and must never be stored in the Android app or owned by
Personal Admin/Hermes. Remote model output is untrusted and must be
validated locally before execution. A remote AI dependency must not make
capture lossy: persist the capture locally first.

QAG-2 minimal gateway implementation, QAG-003R private Tailnet deployment,
QAG-004 Android gateway integration, and QAG-004H Android gateway client
hardening are complete. QAG-004H closed the trust-boundary debt before local
automatic execution was introduced. CHG-027/028 subsequently added validated
all-`AddListItem` local execution and normal text/voice Capture wiring.
CHG-029/030 subsequently added validated all-`CreateTask` local execution and
normal text/voice Capture wiring. CHG-030 is COMPLETE and integrated into main.
Google Tasks sync, Calendar/Event execution, reminders, and other action
families remain separate future scope.

Do not spend time on broad model benchmarking without observed product
evidence requiring it. Model/provider changes must not alter the domain
contract or stored data format.

See:

- `docs/adr/0001-private-remote-ai-runtime.md`
- `docs/adr/0002-quick-aside-owned-private-ai-gateway.md`
- `docs/adr/0003-codex-exec-ephemeral-runtime-protocol.md`
- `docs/changes/QAG-001-runtime-protocol-decision/`

## Verification

Treat deterministic tools and direct runtime evidence as stronger than model self-report. Do not claim PASS for a required gate that was not run.

### Change documentation and evidence

- Normal Changes MUST NOT create or persist `docs/changes/<id>/` packages by default.
- Change-local SPEC/PLAN/TASKS/QA and implementation reports are ephemeral orchestration context unless the user explicitly requests repository persistence or a durable operational need justifies it.
- Persist only durable project contracts such as `PROJECT_SPEC`, `ARCHITECTURE`, `ROADMAP`, `ACCEPTANCE_CRITERIA`, `AI_WORKFLOW`, UX/UI references, runbooks, migration docs, and warranted ADRs.
- Do not create repository-local evidence trees by default. Test output, CI, source/tests, and the implementation report are the normal verification record. Keep a separate artifact only when it has lasting review value that those sources cannot reasonably represent.


### Builder implementation-report hard rule

When a builder completes implementation and the required Change-specific automated verification, its final implementation report **MUST** use the canonical Quick Aside format below.

This is a hard workflow contract:

- Builders MUST read this section directly from `AGENTS.md`; orchestration prompts should point here rather than inventing a different report schema.
- The report MUST be concise, numbered, evidence-oriented, and specific to the active Change.
- Report only verification that was actually run. Never imply that an unrun full suite, lint gate, device check, or other generic gate passed.
- Items 4–7 may use domain-specific evidence labels when that is clearer for the Change (for example `Compose/UI test evidence`, `Task action evidence`, `Room/schema evidence`, `Calendar/lifecycle evidence`). Preserve the numbered structure and evidence-first style rather than replacing it with a generic acceptance matrix.
- Do not add an acceptance-ownership matrix, evidence package, or verbose per-requirement checklist unless the user explicitly requests one.
- Implementation reports are ephemeral orchestration output by default and MUST NOT be persisted in `docs/changes/` or another evidence tree unless the user explicitly requests repository persistence or a durable operational need justifies it.
- If implementation is blocked before completion, do not use the completion footer below. Stop at the blocker and follow the failure/escalation handoff rules instead.

Canonical successful-builder format:

```text
# <CHANGE-ID> IMPLEMENTATION REPORT

1. **Baseline**
   Verified branch, baseline commit, and relevant starting repository state.

2. **Scope implemented**
   Concise summary of exactly what the active Change implemented.

3. **Files changed**
   Only files actually changed, with each file's role. Include useful file/line
   references when the coding environment can provide them.

4. **Primary changed-behavior evidence**
   Focused automated evidence for the core behavior introduced or changed.
   Rename this heading to a more specific domain label when useful.

5. **Directly affected behavior/regression evidence**
   Focused evidence for directly affected regressions, failure paths, Undo,
   atomicity, or adjacent invariants justified by the touched boundary.
   Rename when a more specific evidence label is clearer.

6. **UI/environment/integration evidence**
   Include only when the active Change actually requires it. Otherwise state
   briefly that it was not required; do not manufacture generic QA.

7. **Build/static/schema evidence**
   Applicable build, compile, static, migration, or schema evidence only.
   State explicitly when schema/dependencies were unchanged if material.

8. **Verification commands/results**
   Every automated verification command actually run, with exact result/pass
   count when available and explicit emulator/device target when applicable.

9. **Git diff/status evidence**
   `git diff --check`, changed-file/diff summary, unexpected-file status, and
   branch, published commit SHA/message, push result, confirmation that only the
   declared feature branch changed, and confirmation that main/dev were untouched.

10. **Remaining gates**
    Only gates that genuinely remain after implementation verification.

11. **Exact next gate**
    The single next action: independent review of the published committed diff.

**IMPLEMENTATION COMPLETE — REVIEW PENDING**

STOP.
```

### Successful builder publication and protected branches

When implementation is complete, all required active-Change gates pass, only expected Change-scoped files are present, and the current branch exactly matches the declared feature branch, the builder MUST publish the successful Change before the final report.

Before publishing:

- inspect `git status --short` and `git branch --show-current`;
- require the exact declared feature branch, not `main`, `dev`, detached, empty, or mismatched state;
- verify that no unexpected files are present;
- stage only expected files;
- inspect `git diff --cached --stat`, `git diff --cached --name-only`, and `git diff --cached --check`;
- create an appropriate commit;
- push only the same feature branch (`git push origin "$branch"` if upstream exists, otherwise `git push -u origin "$branch"`).

Never push `main` or `dev`; never use force/force-with-lease or protected-branch refspecs. If work is partial or blocked, a required gate is missing or failing, unexpected files exist, the branch mismatches, or state is uncertain, do not stage, commit, or push; stop and report and require user intervention. The user retains merge, release, protected-branch, and intervention authority.

### Minimum sufficient verification

- The active Change owns its verification: test changed/new behavior, directly affected regressions only for a concrete Change-specific reason, and applicable build/static/schema checks. Historical test classes or suites are not automatic gates merely because an earlier Change created them, the current Change touches the same method, or they offer generic confidence; touching the same production method alone is insufficient.
- A prior-Change test/class may run only for a concrete observed failure, reviewer finding, genuine cross-cutting blast radius, repository/CI policy, or explicit user request. Do not reopen accepted Changes merely to re-prove unchanged behavior.
- Full JVM/connected suites, broad unrelated regressions, repository-wide lint, and real-device QA are not automatic gates. Require a repository/CI rule, justified cross-cutting blast radius, a concrete failure/reviewer finding, or explicit user request.
- The implementation agent runs the required automated checks and reports exact commands/results.
- Reuse valid PASS results while the relevant source/config/environment remains unchanged. A new session/agent, HIGH-ASSURANCE, commit/push of identical source, documentation edits, review stage, or desire for fresher timestamps is not an invalidator.
- Use commit SHA/diff and existing provenance to establish identity. Create a special source manifest only when it materially improves a high-risk review; never create one by default.
- Give each acceptance property one owner gate. Applicable Android connected/UI/device/visual properties use QA1 by default beginning with CHG-034; deterministic host/Room properties retain their appropriate test owner and are not repeated on QA1 merely because it exists.
- User-operated QA is exceptional. Request it only when a newly changed material property genuinely needs human or unavailable external interaction and no reasonable automated substitute exists. Before asking, state the changed property, why automation is insufficient, and the single minimal human action.
- Beginning with CHG-034, applicable Android connected/instrumentation/UI/device-automation/visual gates default to QA1 OPPO under **Repository/device safety and Change-scope hard rule** above, including exact `ANDROID_SERIAL` selection, reported emulator fallback reasons, focused filters, and durable-state protections. Normal focused instrumentation must preserve app data/auth/pairing/identity. Android involvement alone creates no gate; deterministic host/Room properties are not repeated on QA1. The policy is non-retroactive: CHG-033 emulator evidence remains accepted with no rerun or additional gate. Manual QA remains exceptional and requires separate authorization.
- Once implementation and required active-Change verification pass, the builder must publish successful work under Successful builder publication and protected branches above. Independent review evaluates the published committed diff, relevant tests, durable contracts, and the implementation report. Review may request new execution only for a concrete uncovered risk or invalidated prior result.
- For partial or blocked work, do not use the successful completion footer and do not commit or push; stop and require user intervention.
