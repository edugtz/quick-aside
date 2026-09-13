# QAG-001 — Runtime / Protocol Decision — PLAN

Governance: **STANDARD**

## Approach

QAG-1 is an investigation/decision gate rather than a production
implementation change.

The sequence used was:

1. prove the isolated Python/Codex environment on the target VPS;
2. discover live models through the authenticated runtime;
3. verify the installed official Python SDK API;
4. run one Luna Low structured-output smoke test;
5. measure repeated requests through one persistent SDK/app-server process;
6. measure app-server memory across fresh ephemeral threads;
7. compare against `codex exec --ephemeral`;
8. compare token/context usage between SDK and CLI routes;
9. select the smallest runtime protocol consistent with Quick Aside's
   stateless capture semantics;
10. record the durable decision in ADR-0003 and reconcile canonical docs.

## Alternatives evaluated

### Python SDK + persistent app-server

Advantages:

- official typed SDK;
- structured output;
- explicit model/reasoning selection;
- low per-turn SDK overhead;
- natural fit for streaming/multi-turn/session-oriented applications.

Rejected for Quick Aside v1 because the measured long-lived app-server
retained increasing resident memory while fresh ephemeral threads were
created.

### `codex exec --ephemeral`

Selected for v1.

Advantages:

- fresh process/request lifecycle;
- strict JSON Schema output;
- explicit model/reasoning configuration;
- isolated `CODEX_HOME`;
- no persistent Codex process after completion;
- no observed token/context penalty relative to the SDK route;
- simpler failure containment for a small VPS without swap.

### Direct app-server JSON-RPC

Not selected.

It adds protocol/lifecycle ownership without evidence that Quick Aside needs
capabilities unavailable through the supported CLI or SDK.

### Direct OpenAI API

Outside the selected primary route because the accepted runtime direction
uses ChatGPT/Codex OAuth.

### TypeScript/Node SDK path

Not selected for QAG-1. There is no requirement that justifies adding a
second server runtime merely for provider invocation.

## Risks carried into QAG-2

- real Android-to-gateway-to-provider latency is still unmeasured;
- gateway timeout and child-process kill behavior must be explicit;
- concurrency must be bounded for the target VPS;
- exact production Codex version and update policy must be pinned/tested;
- production `CODEX_HOME` must not use the temporary spike path;
- trusted capture timestamp/timezone must be represented in the gateway
  request contract;
- CLI stdout/stderr/final-response handling must avoid logging raw captures
  or provider credentials;
- bundled-vs-system sandbox prerequisites must be verified during deployment
  preflight;
- OAuth expiry/refresh and readiness behavior need gateway tests.

## Rollback

QAG-1 makes no production runtime changes.

The spike can be removed independently from `/tmp`; production Personal
Admin/Hermes/Tailscale/UFW/systemd state is unchanged.

## Verification

QAG-1 closes only if:

- live Luna Low inference succeeds;
- strict structured output succeeds;
- SDK and CLI behavior are measured rather than assumed;
- memory/process lifecycle is observed;
- evidence limitations are recorded;
- canonical architecture/docs point to QAG-2 as the next gate.
