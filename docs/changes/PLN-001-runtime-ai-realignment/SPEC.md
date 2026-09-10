# PLN-001 — Runtime AI Realignment — SPEC

Governance: **STANDARD**
Status: **PLANNING UPDATE COMPLETE — REVIEW PENDING**
Expected branch: `planning-runtime-ai-realignment`

Identifier note: this is a non-implementation planning package. It deliberately
does not consume the `CHG-022` identifier, which stays reserved for the next
actual implementation change.

## Objective

Reconcile Quick Aside's canonical project documentation with the runtime-AI
decisions and evidence established after the completed Change 021 merge:

1. record the current runtime-model decision;
2. record the abandoned/deferred Android-local Codex approach;
3. record the new shared-private-runtime-gateway direction;
4. pause further AI-interpreter/runtime-provider implementation;
5. document exactly which roadmap outcomes are blocked by that pause;
6. preserve productive non-AI work as available future Change 022 candidates;
7. leave Change 022 unselected until this update is independently reviewed and
   merged.

This package is docs-only. It does not implement production code and does not
re-plan the product from zero.

## Proven baseline

- Change 020 (Typed CapturePlan + Validator Foundation) is complete, reviewed,
  and merged in `main`.
- Change 021 (Capture Interpreter + AIProvider Boundary) is complete, reviewed,
  and merged in `main` (`7190d65 docs: close Change 021`).
- `CapturePlan`, `CapturePlanDraft`, `CapturePlanValidator`, `CaptureInterpreter`,
  `ProviderCaptureInterpreter`, and the provider-neutral `AIProvider` boundary
  exist.
- `sourceCaptureId` is injected by the trusted local interpreter from the
  persisted `Capture`; the provider does not control it.
- Room remains at version 5.
- No real runtime provider, HTTP client, wire schema, provider auth,
  `ActionExecutor`, CapturePlan execution, or runtime AI UI wiring exists.

Both changes remain accepted and are not rolled back by this planning update.

## Decision record

### Runtime model

- Primary target: **GPT-5.6 Luna — Low reasoning**.
- Entitlement/runtime path: ChatGPT Plus / Codex OAuth.
- Fallback candidate: **DeepSeek V4 Flash via OpenCode Go**.
- MiMo-V2.5 is no longer primary. The completed runtime model evaluation
  observed worse schema/contract reliability than the finalists.
- The decision is reversible; provider/model changes must not alter domain
  contracts or stored data.

### Android-local Codex

Two isolated feasibility experiments outside the Quick Aside repository
established:

- the official ARM64-musl Codex app-server runs and initializes locally on the
  test Android device without root, proot, an embedded Linux distribution,
  Mac-hosted runtime, or VPS;
- official 0.154.0 Android-side footprint was approximately 169 MiB storage
  with roughly 29 MiB idle RSS;
- Android-native HTTPS reached the OpenAI device-auth endpoint successfully;
- the Codex/reqwest Linux-musl transport failed before device-code issuance;
- supplying all 145 active Android Conscrypt CA roots through
  `CODEX_CA_CERTIFICATE` did not fix it;
- no Cloudflare challenge was observed;
- authentication was never completed, so ChatGPT Plus recognition, Luna
  inference, quota, latency, and lifecycle behavior were never demonstrated on
  Android;
- Quick Aside remained unmodified.

Decision: **defer** Android-local Codex. Do not continue embedding/porting it
now, and do not propose another Android/Bionic/Termux/fork spike in this
planning update. Android-local Codex may be revisited only with materially
better upstream evidence or official Android support.

### Target runtime architecture

```text
Quick Aside Android
    |
    | private interpretation request
    v
shared personal VPS
    |
    +--> private Quick Aside runtime gateway
    |        |
    |        v
    |    Codex / ChatGPT OAuth
    |        |
    |        v
    |    GPT-5.6 Luna Low
    |
    +--> Hermes / Personal Admin runtime
```

Quick Aside shares infrastructure with Personal Admin/Hermes. It must not send
captures into the normal Hermes agent conversation/context. The gateway is a
separate, bounded, logically isolated consumer/service, not a Hermes chat path:

```text
Quick Aside
    -> private interpretation gateway
    -> bounded/fresh Codex inference
    -> Luna Low
```

### Security/ownership boundary

Quick Aside Android must never receive or own ChatGPT OAuth tokens, Codex auth
state, OpenCode Go credentials, or other provider secrets. Those stay on the
personal VPS/runtime side.

Quick Aside owns capture persistence, original input, trusted provenance, the
CapturePlan domain, validation, future execution, Room/local memory, and UI
behavior. Remote model output remains untrusted:

```text
remote interpretation
    -> untrusted structured result
    -> Quick Aside validation
    -> future execution
```

The remote runtime must never directly mutate Quick Aside Room, Google Tasks,
Google Calendar, or local reminders.

### Local-first failure behavior

A remote AI dependency must not make capture lossy:

```text
capture
    -> persist locally
    -> attempt remote interpretation
```

If remote interpretation is unavailable, the original Capture remains durable
and user intent must not silently disappear. Retry/deferred-interpretation
mechanics are intentionally left unspecified and belong to a future focused
change.

### Interpreter pause

Further AI-interpreter/runtime-provider implementation is paused until the
shared private VPS runtime gateway is planned and proven sufficiently to define
the real remote integration boundary. The pause covers, for now:

- real `AIProvider` implementation;
- Codex provider/client implementation;
- OpenCode Go provider implementation;
- provider wire protocol;
- provider authentication;
- Luna runtime integration;
- DeepSeek fallback implementation;
- production prompt/schema implementation;
- runtime network integration;
- interpreter-contract work performed solely to support the provider,
  including the previously discussed immediate PLAN/CLARIFY/UNSUPPORTED plus
  temporal-context change that was provisionally called Change 022.

Those items are **deferred within M2, not rejected**.

### Shared VPS dependency

Quick Aside now has a cross-project infrastructure dependency:

```text
Personal Admin / personal-runtime project
    -> shared private VPS runtime capability
    -> Quick Aside AI interpretation
```

The required external capability is conceptually "private, authenticated,
bounded AI inference service available from the personal VPS", not "Hermes must
interpret Quick Aside captures".

Exact gateway endpoint, auth/network mechanism (private/Tailscale-style
connectivity preferred if consistent with the Personal Admin architecture),
deployment topology, process management, and Codex invocation contract remain
unresolved and belong to Personal Admin/shared-runtime planning. Quick Aside
must not freeze those details before that project decides them.

## In scope

- Reconcile canonical docs that still name MiMo-V2.5 as the current primary or
  imply runtime provider implementation is ready to start.
- Record the pause and the blocked/unblocked roadmap consequences.
- Record the remote gateway direction and the off-device credential boundary.
- Add a concise ADR for the durable cross-cutting decision.
- This planning package and the `ACTIVE_WORK.md` pointer.

## Out of scope

- Any Kotlin/Gradle/Room/manifest/test/UI/schema/dependency change.
- Implementing any provider, gateway, wire protocol, auth flow, prompt, or
  network integration.
- Retry/outbox mechanism design or over-specification.
- Selecting or creating Change 022.
- Rewriting completed Change 001–021 packages.
- Altering unrelated product, UX, naming, or data decisions.
- Committing, pushing, merging, or releasing.

## Roadmap dependency summary

| Milestone | Status after this update |
|---|---|
| M1 — Local capture and memory core | Not blocked; completed work remains valid |
| M2 — AI interpretation and fast-capture flow | Partially blocked; completion blocked by runtime gateway readiness |
| M3 — Google Tasks + Calendar | Not globally blocked; end-to-end natural-language path blocked until interpretation resumes |
| M4 — Reminders and daily reliability | Not globally blocked; natural-language reminder creation blocked until interpretation resumes |
| M5 — Durable history, backup and archive | Not blocked by AI runtime |
| M6 — Personal MVP polish/adoption | Final completion blocked; other polish may continue |

A milestone having dependencies is not the same as all development stopping.
Provider-independent work may continue where independently justified, but this
planning update does not schedule it and does not pre-select Change 022.

## Verification contract (docs-only)

- `git diff --check`
- `git status --short`
- `git diff --stat`
- Targeted textual checks that: stale current-primary MiMo statements are gone
  from canonical current docs; historical Change 001–021 packages are
  untouched; no source code changed; Change 022 was not created; roadmap
  blocker wording is internally consistent; the model evaluation decision is
  represented accurately; Android-local Codex is described as deferred, not
  impossible; Hermes is not described as the Quick Aside interpretation engine;
  gateway implementation details are not invented.
- No Android build or device test is required for this docs-only turn.

## Exact next gate

Independent review of this planning/docs diff. After review and merge, the
orchestrator inspects non-blocked roadmap work and selects the smallest coherent
Change 022. Change 022 is not pre-selected here.

## Authority

This SPEC defines the planning contract but does not declare an engineering
verdict. The user retains product, commit, push, merge, and release authority.
