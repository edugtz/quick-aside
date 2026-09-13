# ADR-0003 — Codex Exec Ephemeral Runtime Protocol

Status: **Accepted**
Date: 2026-09-12
Decision owner: user

Related:

- ADR-0001 — Private Remote AI Runtime for Capture Interpretation
- ADR-0002 — Quick Aside Owns Its Private AI Gateway

This ADR extends those accepted decisions. It does not supersede them.

## Context

ADR-0002 intentionally left the provider invocation mechanism unresolved
until QAG-1 could measure current supported Codex behavior on the target VPS.

Quick Aside's initial runtime shape is stateless:

```text
one capture
    -> one fresh interpretation
    -> one provider-neutral structured result
```

It does not require a persistent provider conversation or shared agent
session.

QAG-1 proved both:

- the official Python SDK using a persistent Codex app-server; and
- `codex exec --ephemeral`.

The SDK/app-server path produced correct structured results and good warm
latency, but resident memory increased across fresh ephemeral threads.
`codex exec --ephemeral` produced correct structured results with bounded
per-request process lifetime and left no Codex process resident after each
request.

Equivalent SDK and CLI requests consumed effectively identical Codex
token/context totals.

## Decision

The first usable Quick Aside private gateway invokes Codex through a fresh
`codex exec --ephemeral` child process for each interpretation request.

The validated provider profile is:

- GPT-5.6 Luna;
- explicit Low reasoning;
- ChatGPT/Codex OAuth;
- Quick Aside-specific `CODEX_HOME`;
- user/project config ignored for provider invocation;
- user/project exec rules ignored for provider invocation;
- read-only sandbox;
- strict JSON Schema final output.

Conceptually:

```text
Quick Aside Android
    -> private Quick Aside gateway
    -> bounded codex exec --ephemeral
    -> ChatGPT/Codex OAuth
    -> GPT-5.6 Luna / Low
    -> provider-neutral structured result
    -> Quick Aside validation
```

Provider output remains untrusted. Trusted local provenance such as
`sourceCaptureId` is not delegated to Codex.

## Consequences

- The gateway owns child-process lifecycle, timeout, cancellation/kill,
  exit-code handling, output parsing, and bounded concurrency.
- Codex memory is naturally released when each invocation exits.
- There is no long-lived Codex session whose ephemeral threads must be
  reclaimed by Quick Aside.
- The provider invocation remains replaceable behind the gateway and
  Android `AIProvider` boundary.
- A dedicated production `CODEX_HOME` is required; the temporary QAG-1
  `/tmp` path is not a deployment contract.
- The production Codex version must be pinned/tested rather than silently
  floating.
- Structured output must remain schema-constrained and locally validated.
- Logs must avoid provider credentials and raw capture content by default.
- A persistent Python SDK/app-server remains a future option if product
  requirements or upstream lifecycle behavior justify revisiting it.

## Alternatives not selected for v1

### Persistent Python SDK/app-server

Proven functional but not selected because QAG-1 observed increasing
resident memory across fresh ephemeral threads on the target VPS.

### Direct app-server JSON-RPC

Adds lower-level protocol/lifecycle ownership without a current Quick Aside
requirement.

### Direct OpenAI API

Not the accepted primary route; the current architecture uses
ChatGPT/Codex OAuth.

## Not decided by this ADR

This ADR does not choose:

- Python vs another language for the gateway HTTP service;
- HTTP endpoint/path or final wire schema;
- Tailscale Service/path;
- app-layer authentication beyond private-network isolation;
- timeout value;
- concurrency limit;
- systemd resource limits;
- exact production filesystem paths;
- numeric Android end-to-end latency budget;
- retry/deferred interpretation policy;
- DeepSeek fallback inclusion;
- Android networking implementation.

Those remain later QAG decisions.
