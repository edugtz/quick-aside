# Quick Aside — AI Workflow v0.4

This file distinguishes AI used to **build Quick Aside** from AI used **inside Quick Aside at runtime**.

## 1. Project orchestration

Planner / architect / orchestrator / final engineering reviewer:

- ChatGPT current strongest reasoning model available for the session.
- Current conversation baseline: GPT-5.6 Sol.

Responsibilities:

- product clarification;
- architecture;
- change scoping;
- verification contract;
- review findings;
- final `PASS` / `PASS_WITH_NOTES` / `BLOCKED` engineering verdict.

The user owns product, merge, release, protected-branch, and intervention authority. Successful builders may publish feature-branch commits/pushes only under the successful-builder publication workflow below.

## 2. Implementation routing

Implementation can use the best-fit available coding tool/model based on task and current cost/quality evidence, including:

- Cursor Pro;
- OpenCode Go;
- local models via the user's local stack;
- other approved cloud builders.

Do not make runtime product architecture depend on which coding agent implemented a change.

Prefer deterministic tools (build/tests/lint/static analysis) over builder self-report.


### Verification routing

Implementation agents verify the Change delta themselves and report exact commands/results. THE ACTIVE CHANGE OWNS ITS VERIFICATION: scope is **only the current Change**—changed/new behavior, directly affected regressions only when there is a concrete Change-specific reason, and build/static/schema checks relevant to the changed surface. Do not execute a historical test class or suite merely because an earlier Change created it, the current Change touches the same method, it offers generic regression confidence, or it was prior evidence; touching the same production method alone is insufficient. Reuse prior-Change evidence for unchanged behavior. A prior-Change test/class may run only for a concrete observed failure, reviewer finding, genuine cross-cutting blast radius, repository/CI policy, or explicit user request.

Hard execution rules:

- Do not run full JVM suites, full connected/instrumentation suites, repository-wide lint, broad unrelated regressions, privacy rescans, or other generic “run everything” gates by default. A repository/CI mandate, actual cross-cutting blast radius, concrete failure/reviewer finding, or explicit user request is required to broaden verification.
- Do not perform routine environment cleanup to manufacture a fresh state. No `git clean`, Gradle `clean`, app-data clearing, Room wiping, credential/identity reset, emulator/device reset, build/cache deletion, or equivalent cleanup unless the active Change requires it or a concrete failure/reviewer finding justifies it. Test-owned disposable fixtures are allowed.
- QA1 is durable physical environment state. Routine verification uses an explicitly targeted emulator. The user may explicitly authorize focused automated connected/instrumentation tests against QA1 for the active Change, but that authorization never permits destructive device, install, app-data, auth, pairing, or identity mutation. Before any connected run, inspect `adb devices -l` and select one exact target, preferably with `ANDROID_SERIAL`; do not rely on ambiguous ADB selection or `-Pandroid.injected.device.serial`.
- On QA1, unless the active Change explicitly tests the lifecycle **and separate mutation authorization is provided**, do not uninstall/reinstall Quick Aside, run `pm clear`, clear app storage, wipe/reset the device, alter Android Keystore identity, create/revoke/reset/delete/rotate pairing, generate replacement pairing codes, or otherwise mutate durable auth/device/install state. If QA1 has an auth, pairing, or install-state problem, stop and report instead of repairing it. Manual/user-operated QA is separate from automated QA1 authorization.
- Do not broaden implementation scope with unrelated refactors, future-roadmap features, auth/device lifecycle work, or opportunistic cleanup.

Reuse valid results while the relevant source/config/environment is unchanged. A rerun requires an explicit invalidator. Do not treat a new session/agent, documentation edit, commit/push of identical source, review stage, or a wish for fresher evidence as an invalidator.

User-operated QA is exceptional. Ask only when a newly changed material property cannot reasonably be established by the agent through automated tests, emulator/device automation, source inspection, or existing valid evidence. State the changed property, automation gap, and one minimal human action before asking.

Normal Changes do not persist `docs/changes/<id>/` packages or evidence trees. Change-local SPEC/PLAN/TASKS/QA and implementation reports are ephemeral orchestration records unless the user explicitly requests persistence or a durable operational need justifies it.

After required active-Change verification passes, a successful builder must publish under the successful-builder publication workflow below. Independent review evaluates the published committed diff unless the user chooses another reviewer.


### Canonical builder implementation report

Builder reporting is a durable workflow contract, not something each Change prompt may redefine.

- Every builder MUST read and follow `AGENTS.md` → **Builder implementation-report hard rule** before starting implementation.
- Orchestrator/builder prompts SHOULD reference that canonical rule instead of embedding an alternate report template.
- The report remains concise, numbered, evidence-first, and Change-specific.
- Domain-specific evidence headings may be used inside the canonical numbered structure.
- Do not replace the canonical report with an acceptance matrix, evidence tree, or generic test summary unless the user explicitly changes the reporting contract.
- A successful builder report describes the published implementation; its Git evidence includes the exact feature branch, published commit SHA and message, push result, confirmation that only the feature branch changed, and confirmation that `main`/`dev` were untouched.
- A successful builder report ends with `**IMPLEMENTATION COMPLETE — REVIEW PENDING**` and `STOP.` exactly as defined in `AGENTS.md`.
- A blocked or partial implementation uses the failure/escalation handoff instead, has no successful footer, and has no commit or push; user intervention is required.

### Successful builder publication

When implementation is complete, all required active-Change gates pass, only expected Change-scoped files are present, and the current branch exactly matches the declared feature branch, the builder has delegated authority to publish the successful Change before the final report.

The builder must inspect `git status --short` and `git branch --show-current`; require the exact feature branch and reject `main`, `dev`, detached, empty, mismatched, partial, or uncertain state; verify no unexpected files; stage only expected files; inspect `git diff --cached --stat`, `git diff --cached --name-only`, and `git diff --cached --check`; create an appropriate commit; and push only the same feature branch with `git push origin "$branch"` or `git push -u origin "$branch"` when no upstream exists.

Never push `main` or `dev`, and never use force/force-with-lease or protected-branch refspecs. If a required gate is missing or failing, work is blocked/partial, unexpected files exist, or branch/state is uncertain, do not stage, commit, or push; stop and report and require user intervention. The user retains merge, release, protected-branch, and intervention authority.

## 3. Runtime interpretation models

QAG-1 and QAG-2 are complete.

Accepted runtime direction:

1. **GPT-5.6 Luna — explicit Low reasoning** — primary model via ChatGPT
   Plus / Codex OAuth.
2. **`codex exec --ephemeral`** — first gateway provider invocation route;
   one fresh bounded process per interpretation request.
3. **DeepSeek V4 Flash via OpenCode Go** — fallback candidate,
   evidence-triggered.
4. MiMo-V2.5 was not selected as primary because observed schema/contract
   reliability was worse than the finalists.
5. No automatic reasoning escalation; fallback, if implemented, is for
   eligible provider/runtime failures rather than semantic disagreement.

QAG-1 validated Codex SDK/CLI `0.154.0` and selected the process-per-request
CLI route. QAG-2 implemented the minimal repository gateway around that route
and verified bounded request/output behavior, strict provider validation,
timeout/cancellation, concurrency, health/readiness, safe diagnostics, and
isolated real-provider contract behavior.

Final QAG-2 verification:

- deterministic suite: **33 passed**;
- targeted Luna Low contract smoke: PASS;
- Personal/Trabajo semantic routing smoke: **5/5 PASS**;
- residual Codex process after requests: **NONE**;
- final engineering verdict: **PASS_WITH_NOTES**.

The notes are limited to upstream FastAPI/Starlette deprecation warnings.

The gateway is now live through the QAG-003R private-tailnet deployment:
`svc:quickaside -> Tailscale Serve HTTPS -> 127.0.0.1:2588`. QAG-003R completed
real signed Luna Low, replay-rejection, restart/reboot-persistence, isolation,
and log-privacy evidence with final `PASS_WITH_NOTES`. QAG-004 completed the
HIGH-ASSURANCE Android integration gate, including device pairing, QA1 signing,
the remote `AIProvider` adapter, local validation, and true Android end-to-end
latency measurement. QAG-004H subsequently closed the accepted client-side
trust-boundary hardening debt. CHG-027/028 then added validated all-`AddListItem`
local execution and normal text/voice Capture wiring. CHG-029/030 then added
validated all-`CreateTask` local execution and normal text/voice Capture wiring.
CHG-030 is COMPLETE and integrated into main.

Provider auth and credentials belong to the isolated Quick Aside
gateway/runtime, not Android or Personal Admin/Hermes. Runtime code remains
provider-abstracted; stored domain records and `CapturePlan` remain
provider-independent.

Do not restart broad comparative model benchmarking unless real runtime use
shows a concrete blocker.

See:

- `docs/adr/0003-codex-exec-ephemeral-runtime-protocol.md`
- `docs/changes/QAG-001-runtime-protocol-decision/QA.md`
- `docs/changes/QAG-002-minimal-gateway/QA.md`

## 4. Runtime AI observability

Useful local metrics without storing unnecessary sensitive content:

- provider/model;
- request latency;
- schema validation result;
- fallback invoked yes/no;
- low-confidence clarification yes/no;
- user correction occurred yes/no;
- approximate request count/allowance use if available.

The goal is to know when the primary provider is insufficient without building a benchmark program first.

## 5. Visual asset workflow

Project-specific generative visuals:

`ChatGPT plans/briefs → Google AI Plus primary generation when available → ChatGPT reviews/integration → ChatGPT image generation fallback`

Canonical current UI reference is already stored at:

- `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`

The image was generated before the rename and may contain the legacy `VoiceApp` title inside the pixels. That text is non-canonical; implementation uses **Quick Aside**.

For future UI phases, agents MUST inspect this reference plus `docs/UX_UI_REFERENCE.md` before implementation.

Standard UI icons should come from Material/platform icon sets rather than AI-generated icon artwork.

## 6. UI implementation review

For every material UI change:

- builder references the canonical UX visual + written contract;
- reviewer compares actual visual result against those references and the active Change orchestration acceptance criteria/context;
- visual differences that materially change the product direction require explicit product approval.
