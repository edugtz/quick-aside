# QAG-001 — Runtime / Protocol Decision — QA Evidence

Date: 2026-09-12
Environment: target shared VPS, isolated Quick Aside spike namespace

## Evidence scope

This file records the evidence used to close QAG-1.

It is not Android end-to-end performance evidence and does not establish a
numeric production latency SLO.

## QAG-0 prerequisite snapshot

Read-only preflight established:

- Ubuntu 26.04.1 LTS;
- 2 vCPU;
- approximately 3.7 GiB RAM;
- no swap;
- approximately 32 GiB root filesystem free at preflight;
- Personal Admin/Hermes and ACK services already present;
- existing Personal Admin private Tailscale mapping reserved and untouched;
- UFW public exposure limited to existing host requirements;
- no Quick Aside service, systemd unit, Unix user, or listener existed;
- `/opt/quickaside-gateway`, `/var/lib/quickaside`, and
  `/etc/quickaside-gateway` were available namespaces;
- Personal Admin/Hermes application state under `/home/hermes` remained
  isolated and was not reused.

The investigation later installed only the previously approved Python venv
support required for the isolated QAG-1 spike. No Quick Aside production
service/network configuration was created.

## Runtime environment

Temporary spike root:

`/tmp/quickaside-qag1`

Verified:

- Python `3.14.4`;
- pip `26.2.1`;
- `openai-codex` SDK `0.154.0`;
- bundled Codex CLI `0.154.0`;
- isolated Quick Aside `CODEX_HOME`;
- ChatGPT/Codex OAuth operational.

The temporary `/tmp` paths are evidence-only and are not production
deployment paths.

## Model discovery

Live model discovery returned:

```text
gpt-6-astra      default=low
gpt-5.6-sol      default=low
gpt-5.6-terra    default=medium
gpt-5.6-luna     default=medium
gpt-5.5          default=medium
```

`gpt-5.6-luna` explicitly supported:

`low, medium, high, xhigh, max`

Therefore Quick Aside must explicitly request **Low**; Luna's runtime
default is Medium.

## SDK smoke test

Input:

`Compra Chobani, pollo y leche`

Result:

```json
{
  "actions": [
    {"type":"AddListItem","listDefinitionId":"mandado","text":"Chobani"},
    {"type":"AddListItem","listDefinitionId":"mandado","text":"pollo"},
    {"type":"AddListItem","listDefinitionId":"mandado","text":"leche"}
  ]
}
```

Evidence:

- status: `completed`;
- Codex `duration_ms`: `4602`;
- measured wall time: `4630.8 ms`;
- schema validation: PASS.

## Persistent SDK/app-server representative benchmark

Six fresh ephemeral threads were created through one persistent
`Codex()` client/app-server.

| Case | Wall ms | Codex duration ms |
|---|---:|---:|
| Compra Chobani, pollo y leche | 3196.7 | 3183 |
| Necesito comprar cuerdas para la guitarra | 2932.2 | 2906 |
| Mañana revisa el PR | 3265.9 | 3246 |
| Guarda como nota: llamar al taller por la Forester | 3103.1 | 3091 |
| Deshaz lo último | 3132.8 | 3112 |
| Agrega jabón y papel de baño al mandado | 2847.2 | 2828 |

Summary:

- minimum wall: `2847.2 ms`;
- median wall: `3118.0 ms`;
- maximum wall: `3265.9 ms`;
- median Codex duration: `3101.5 ms`;
- 6/6 structured outputs: PASS.

An initial reported `app_server_startup_ms: 0.0` was discarded as an
invalid measurement: the SDK starts/initializes the runtime during
`Codex()` construction, before that timer began.

## Corrected cold start and memory behavior

Correctly measured `Codex()` initialization:

`244.3 ms`

RSS immediately after initialization:

`104248 KiB` (~101.8 MiB)

Repeated equivalent requests:

| Request | Thread start ms | Wall ms | Codex duration ms | RSS KiB |
|---|---:|---:|---:|---:|
| 1 | 266.2 | 3595.7 | 3567 | 217268 |
| 2 | 46.6 | 2639.3 | 2606 | 242116 |
| 3 | 65.8 | 3892.9 | 3879 | 284000 |
| 4 | 66.3 | 2627.1 | 2616 | 314128 |
| 5 | 74.3 | 3749.6 | 3717 | 329364 |
| 6 | 58.0 | 5440.5 | 5415 | 344000 |

Memory increased on every request and did not demonstrate a clear plateau
during the spike.

After `Codex.close()`:

`app_server process: none`

## `codex exec --ephemeral` control

Equivalent strict-schema request was executed three times.

| Run | Elapsed s | Max RSS KiB | Tokens | Process after exit |
|---|---:|---:|---:|---|
| 1 | 4.36 | 205696 | 12428 | none |
| 2 | 4.17 | 207380 | 12426 | none |
| 3 | 3.65 | 204220 | 12430 | none |

All three returned:

```json
{"actions":[{"type":"AddListItem","listDefinitionId":"mandado","text":"leche"}]}
```

Evidence:

- 3/3 schema results: PASS;
- model: `gpt-5.6-luna`;
- reasoning effort: `low`;
- sandbox: read-only;
- approval mode observed: never;
- no Codex process remained after any run.

Codex warned that the temporary `/tmp` `CODEX_HOME` was unsuitable for
helper PATH aliases. That warning is specific to the temporary spike
location and does not define the production namespace.

Codex also reported no system `bubblewrap` on PATH and used its bundled
bubblewrap fallback successfully. Production sandbox prerequisites remain a
deployment preflight item rather than a QAG-1 blocker.

## Equivalent SDK token/context check

Equivalent SDK request:

- total wall: `4782.4 ms`;
- turn duration: `4313 ms`;
- input tokens: `12377`;
- cached input tokens: `8960`;
- output tokens: `52`;
- reasoning output tokens: `17`;
- total tokens: `12429`.

CLI controls used:

`12428 / 12426 / 12430`

Conclusion: there was no material token/context-use advantage for the
persistent SDK route in the equivalent check.

## Decision

**PASS**

QAG-1 selects:

```text
codex exec --ephemeral
model = gpt-5.6-luna
reasoning = low
strict output schema
isolated Quick Aside CODEX_HOME
fresh process per interpretation request
```

The persistent Python SDK/app-server remains a supported future alternative
if Quick Aside later needs session-oriented/streaming behavior or upstream
lifecycle behavior changes.

## Remaining evidence gates

Not proven by QAG-1:

- Android -> private network -> gateway -> provider -> Android end-to-end
  latency;
- production gateway timeout;
- production concurrency/resource limits;
- reboot/restart behavior;
- production OAuth readiness/refresh behavior;
- exact HTTP request/response contract;
- Tailscale exposure/auth mechanism;
- systemd hardening;
- Android client behavior;
- DeepSeek fallback.

Those belong to later QAG gates.
