# ADR-0002 — Quick Aside Owns Its Private AI Gateway

Status: **Accepted**
Date: 2026-09-12
Decision owner: user

Supersedes: `ADR-0001 — Private Remote AI Runtime for Capture Interpretation`
**only where ADR-0001 assigns gateway/runtime planning or ownership to
Personal Admin/shared-runtime work.**

ADR-0001 remains accepted for the decision to use a private remote AI runtime,
keep provider credentials off Android, use GPT-5.6 Luna Low as the primary
runtime target, keep DeepSeek V4 Flash as an evidence-triggered fallback
candidate, validate remote output locally, and avoid Hermes conversation/context
as the interpretation service.

## Context

Quick Aside needs a small private server-side AI gateway so the Android app can
send captured text for fresh, bounded interpretation without embedding provider
credentials or a full provider runtime on the phone.

ADR-0001 correctly selected a private VPS-hosted runtime and explicitly rejected
using ordinary Hermes agent conversation/context as the Quick Aside inference
service. However, it also described the gateway as a cross-project dependency
whose endpoint, authentication/network mechanism, deployment topology, process
management, and Codex invocation contract belonged to Personal Admin/shared
runtime planning.

That ownership model is no longer desired.

The user has explicitly decided that the Quick Aside gateway is part of
**Quick Aside**. The existing VPS may host both Quick Aside and Personal Admin,
but that is infrastructure reuse only.

Read-only VPS preflight also confirmed that the two workloads can be isolated:
Personal Admin already has its own Hermes/ACK services and Tailscale mapping,
while no Quick Aside service identity, systemd unit, application path, or
runtime currently exists.

## Decision

Quick Aside owns the private AI gateway and all Quick Aside-specific runtime
decisions.

The target responsibility boundary is:

```text
Quick Aside Android
    -> private Quick Aside AI gateway
    -> fresh / bounded inference
    -> configured model provider
    -> provider-neutral Quick Aside result
    -> Quick Aside validation / execution / persistence / UX
```

Quick Aside owns:

- capture and provenance;
- trusted capture-time/timezone inputs;
- the provider-neutral request/result contract;
- Android-side validation and action execution;
- Quick Aside persistence and UX;
- gateway API behavior;
- server-side provider invocation;
- Quick Aside-specific provider/model/reasoning configuration;
- Quick Aside-specific provider credential/auth lifecycle;
- gateway timeout/error/fallback policy;
- gateway health/readiness and operational logging.

The VPS is shared infrastructure only and owns generic host concerns such as:

- Linux;
- systemd;
- resource limits;
- Tailscale/private networking;
- firewall and OS maintenance.

Personal Admin/Hermes is **not** an application dependency of Quick Aside.

The Quick Aside gateway must not depend on or route through:

- Personal Admin skills, cron, state, databases, or prompts;
- ACK/Ackline delivery;
- Gmail/Calendar/Tasks integrations;
- Hermes conversation history or memory;
- Hermes global model/fallback policy;
- `/home/hermes` as the Quick Aside application/configuration namespace.

Deploying or rolling back Quick Aside must not require modifying Personal Admin
runtime authority, ACK behavior, cron/state, or existing private-service
mappings.

## Consequences

- Gateway planning, implementation, deployment, verification, and future
  maintenance belong to the Quick Aside project.
- Personal Admin documentation is not authoritative for Quick Aside gateway
  behavior.
- The existing VPS, systemd conventions, firewall posture, and Tailscale
  networking may be reused where non-conflicting.
- Quick Aside should use its own service identity, configuration/state
  namespace, systemd service, provider auth state, and private network exposure
  unless later evidence justifies a different isolated design.
- Provider secrets remain server-side and are never stored in the Android app.
- Remote model output remains untrusted and must pass Quick Aside validation
  before any mutation.
- Quick Aside capture remains local-first; remote runtime failure must not
  silently lose the original capture.
- Rollback must be Quick-Aside-local and leave Personal Admin operationally
  unchanged.

## Not decided by this ADR

This ADR intentionally does **not** choose:

- Python vs Node or another server runtime;
- Codex SDK vs app-server vs CLI/process integration;
- exact HTTP endpoint or wire schema;
- port number;
- Tailscale Service/path naming;
- whether app-layer authentication is needed in addition to tailnet isolation;
- timeout or concurrency values;
- numeric latency budgets;
- whether DeepSeek fallback ships in the first usable version.

Those are QAG-1 runtime/protocol decisions and must be based on current supported
behavior plus measured requirements, especially the voice-capture-to-UI latency
path.
