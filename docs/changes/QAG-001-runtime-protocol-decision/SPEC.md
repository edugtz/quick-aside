# QAG-001 — Runtime / Protocol Decision — SPEC

Governance: **STANDARD**
Status: **COMPLETE — EVIDENCE PASS**
Expected branch: `docs/qag1-runtime-protocol-decision`

## Objective

Close QAG-1 by selecting the smallest viable provider invocation mechanism
for the Quick Aside-owned private AI gateway using current supported Codex
behavior and measured evidence from the target VPS.

This gate chooses the provider-runtime invocation boundary only. It does not
implement the gateway HTTP service, private-network exposure, Android
networking, production systemd service, or fallback provider.

## Proven baseline

- QAG-0 read-only VPS preflight completed and passed.
- Quick Aside owns its private AI gateway under ADR-0002.
- Personal Admin/Hermes shares infrastructure only and is not a Quick Aside
  application dependency.
- Android currently has no remote `AIProvider` implementation or HTTP client.
- Provider credentials must remain off Android.
- GPT-5.6 Luna with explicit Low reasoning remains the accepted primary
  runtime target.
- Local `CapturePlan` validation and the provider-neutral `AIProvider` /
  `CaptureInterpreter` boundary remain accepted.
- Trusted `sourceCaptureId` provenance remains Android-owned.

## Decision

For the first usable Quick Aside gateway runtime, provider inference uses a
fresh bounded Codex CLI process per interpretation request:

```text
Quick Aside gateway
    -> codex exec --ephemeral
    -> GPT-5.6 Luna / Low
    -> strict structured output
    -> process exits
```

The validated invocation profile is:

- Codex CLI validated at `0.154.0`;
- ChatGPT/Codex OAuth stored in a Quick Aside-specific `CODEX_HOME`;
- model `gpt-5.6-luna`;
- explicit `model_reasoning_effort="low"`;
- `--ephemeral`;
- `--ignore-user-config`;
- `--ignore-rules`;
- read-only sandbox;
- JSON Schema-constrained output;
- final response captured separately from CLI diagnostics.

Production paths, version pinning/update policy, timeout, concurrency,
endpoint schema, gateway server language, process manager, Tailscale route,
app-layer authentication, and fallback behavior remain QAG-2/QAG-3
decisions.

## Why this route

The official Python SDK and persistent `codex app-server` path was proven
functional and produced valid structured results, but repeated fresh
ephemeral threads caused resident memory to grow during the spike.

The process-per-request CLI path:

- produced valid structured output on every control run;
- explicitly used Luna Low;
- remained in the same seconds-scale latency range;
- had bounded per-request memory;
- left no Codex process resident after completion;
- consumed effectively the same Codex token/context amount as the SDK path.

For Quick Aside's stateless interpretation shape, bounded process lifecycle
is preferred over keeping a long-lived app-server with accumulated
ephemeral-thread state.

## Provider-neutral contract constraints

The runtime result remains untrusted.

The provider must not become authoritative for:

- `sourceCaptureId`;
- local persistence identity;
- action execution;
- Google Tasks/Calendar mutation;
- reminder scheduling;
- trusted capture provenance.

QAG-2 must include trusted temporal context sufficient to interpret relative
dates. The current Android `AIInterpretationRequest` contains only
`inputText`, so the production wire contract must resolve that gap without
making the provider authoritative for local identity.

## Explicit exclusions

QAG-1 does not:

- create a Quick Aside Linux user;
- create or enable systemd services;
- modify Tailscale Serve/Services/ACLs;
- modify UFW;
- expose a public endpoint;
- add Android `INTERNET` permission or an HTTP client;
- implement the gateway server;
- implement DeepSeek V4 Flash fallback;
- define a numeric end-to-end latency budget;
- implement retries/deferred interpretation;
- change Room/domain schemas;
- modify Personal Admin/Hermes/ACK.

## Acceptance scenarios

1. The live Codex runtime exposes `gpt-5.6-luna` and Low reasoning is set
   explicitly rather than relying on Luna's Medium default.
2. ChatGPT/Codex OAuth works from an isolated Quick Aside `CODEX_HOME`.
3. A fresh interpretation can return strict JSON matching the supplied
   schema.
4. The selected route does not retain a Codex process after a completed
   request.
5. Provider output excludes trusted local provenance such as
   `sourceCaptureId`.
6. The rejected persistent-app-server alternative is documented with the
   measured memory evidence that drove the decision.
7. QAG-1 results do not claim Android-to-provider end-to-end latency.
8. No Personal Admin/Hermes runtime or state is modified by the
   investigation.

## Result

**QAG-1: PASS**

Selected provider invocation for v1:

`codex exec --ephemeral` + `gpt-5.6-luna` + explicit Low reasoning.

Detailed evidence is recorded in `QA.md`.
