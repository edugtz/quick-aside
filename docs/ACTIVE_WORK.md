# Quick Aside — Active Work

## Active change

None.

Status: **NO ACTIVE IMPLEMENTATION CHANGE**

No CHG-027 or other implementation change has been selected.

## Latest completed implementation change

`docs/changes/026-local-pendientes-ui/`

CHG-026 — Local Pendientes UI Foundation — is complete, reviewed, closed,
and merged into `main`.

Latest completed implementation baseline:

`17ee862de90c19a54338397d239332ec908cef25`

Independent engineering review: **PASS**

`BLOCKER 0 / MAJOR 0 / MINOR 0`

## Runtime gateway gates

QAG-0 — read-only VPS preflight:

**COMPLETE — PASS**

QAG-1 — runtime/protocol decision:

**COMPLETE — PASS**

Evidence package:

`docs/changes/QAG-001-runtime-protocol-decision/`

Durable decision:

`docs/adr/0003-codex-exec-ephemeral-runtime-protocol.md`

Selected first-runtime path:

```text
Quick Aside gateway
    -> codex exec --ephemeral
    -> GPT-5.6 Luna / Low
    -> strict provider-neutral structured result
```

The persistent Python SDK/app-server route was proven but not selected for
v1 because QAG-1 observed increasing resident memory across fresh ephemeral
threads. The CLI ephemeral route left no Codex process resident after each
request and showed effectively identical token/context use.

## Current state

- Changes 020 through 026 relevant to the current capture/task foundation
  remain merged/complete.
- Local CapturePlan validation and the provider-neutral
  `CaptureInterpreter` / `AIProvider` boundary remain accepted.
- Local Task persistence, reversible actions, and Pendientes UI foundation
  are complete.
- Room remains version 7.
- Google Tasks synchronization is not implemented.
- Runtime AI integration is not implemented.
- Android still has no remote `AIProvider` implementation or real HTTP
  client.
- Quick Aside owns its private AI gateway; Personal Admin/Hermes shares VPS
  infrastructure only.
- GPT-5.6 Luna Low via ChatGPT/Codex OAuth remains the primary runtime.
- QAG-1 selected `codex exec --ephemeral` as the first provider invocation
  mechanism.
- DeepSeek V4 Flash remains an evidence-triggered fallback candidate only.

## Exact next gate

**QAG-2 — minimal gateway implementation.**

QAG-2 must define and test, before live deployment:

- the minimal private gateway request/result contract;
- trusted capture time/timezone transport;
- strict provider output schema;
- provider subprocess invocation;
- timeout/cancellation and bounded concurrency;
- production Codex version pin;
- isolated provider auth/config paths;
- health/readiness;
- safe diagnostics that avoid raw capture text and credentials;
- deterministic gateway contract/failure tests.

QAG-2 must not include:

- live Tailscale/systemd/UFW deployment;
- Android networking/integration;
- DeepSeek fallback unless separately justified;
- Personal Admin/Hermes changes.

Live VPS/network mutation remains QAG-3 HIGH-ASSURANCE work requiring
explicit user approval.
