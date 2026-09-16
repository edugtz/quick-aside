# QAG-003 — Live Authenticated Gateway Deployment — QA

Status: **ACTIVE**

## Gate A — Live VPS preflight

Status: **PASS**

Observed 2026-09-14 UTC:

- Ubuntu 26.04.1 LTS;
- kernel `7.0.0-31-generic`;
- 2 vCPU;
- 3.7 GiB RAM;
- approximately 2.5 GiB available;
- no swap;
- root filesystem 38 GiB with approximately 32 GiB free;
- Python 3.14.4;
- no `quickaside` user;
- `/opt/quickaside-gateway`: absent;
- `/var/lib/quickaside`: absent;
- `/etc/quickaside-gateway`: absent;
- `/home/quickaside`: absent;
- proposed port `2588`: free;
- Personal Admin ACK active on `127.0.0.1:2587`;
- `personal-admin-ack.service`: active/running/enabled;
- `hermes-gateway.service`: active/running/enabled;
- Personal Admin `/health`: HTTP 200;
- UFW active with default incoming deny;
- no Codex executable under `ubuntu`;
- no Codex executable under `hermes`.

Tailscale exists for unrelated VPS use but is not part of Quick Aside runtime.

## Network/security decision

ADR-0004 defines:

- normal Internet HTTPS ingress;
- Caddy TLS termination;
- FastAPI loopback-only at `127.0.0.1:2588`;
- Android Keystore P-256 device identity;
- ECDSA SHA-256 request signatures;
- timestamp + persistent nonce anti-replay;
- one-time pairing;
- application rate limiting;
- isolated Quick Aside Codex OAuth/runtime;
- no Personal Admin/Hermes dependency;
- no Quick Aside Tailscale dependency.

## Deterministic security evidence

Status: **PASS — remediation evidence current**

Evidence:

- full gateway suite: **99 passed, 2 upstream warnings**;
- pairing-code concurrent race: **20/20 PASS**;
- replay-nonce concurrent race: **20/20 PASS**;
- high-cardinality rate-limit storage is explicitly bounded;
- expired rate-limit keys are pruned;
- `python -m compileall -q quickaside_gateway`: PASS;
- `git diff --check`: PASS.

The two warnings are upstream FastAPI/Starlette deprecation
warnings and are not gateway correctness failures.

## Independent security review

Status: **BLOCKED — remediation in progress**

Initial Gate D independent review:

- 0 BLOCKER;
- 2 MAJOR;
- 4 MINOR;
- 4 NOTE.

MAJOR-1, unbounded rate-limiter key cardinality, has been
remediated locally and deterministically verified.

Gate D round-2 MAJOR remediation candidates are implemented locally:

MAJOR-1 — HTTP :80 Automatic HTTPS listener:
- explicit `servers :80` hardening;
- finite header/body/write/idle timeouts;
- 16 KiB header cap;
- explicit HTTP listener declaration for Automatic HTTPS/redirect handling.

MAJOR-2 — high-cardinality pre-auth churn:
- existing bounded per-source limiter retained;
- independent global pre-auth budget added;
- global budget is not keyed by source IP;
- high-cardinality churn test verifies aggregate admission remains bounded.

Additional remediation:
- Uvicorn access logging explicitly disabled;
- HTTPS public-route allowlist and localhost-only upstream retained;
- Uvicorn remains fixed to one worker;
- proxy trust remains restricted to loopback.

Current deterministic suite: 99 passed, 2 upstream warnings.

Gate D round 3 completed BLOCKED; its MAJOR was remediated and subsequently reviewed in round 4.

Gate E remains blocked.

## Deployment evidence

Status: **PENDING**

## External HTTPS evidence

Status: **PENDING**

## Restart / rollback evidence

Status: **PENDING**

## Host reboot evidence

Status: **PENDING — separate explicit user approval required**

## Final verdict

**PENDING**


## Gate D round-3 MAJOR remediation

Status: **IMPLEMENTED LOCALLY — independent re-review required**

Caddy Admin control-plane remediation:

- default TCP Admin API at localhost:2019 is not used;
- Caddy Admin uses `unix//run/caddy-admin/admin.sock|0600`;
- systemd drop-in creates `/run/caddy-admin` with mode `0700`;
- the Quick Aside service runs as dedicated user/group `quickaside`;
- deployment contract requires runtime proof that `quickaside` cannot connect
  to the Caddy Admin socket;
- Caddy reload procedure targets the reviewed Unix socket explicitly.

Additional ingress remediation:

- HTTP catch-all now returns 404;
- deployment regression tests cover the Caddy Admin boundary and HTTP
  catch-all behavior.

Current evidence:

- full gateway suite: **99 passed, 2 upstream warnings**;
- deployment contract suite: **9 passed**;
- pairing race: **20/20 PASS**;
- replay race: **20/20 PASS**;
- compileall: PASS;
- git diff --check: PASS.

Gate E remains blocked until independent Gate D round 4 has zero BLOCKER
and zero MAJOR findings.


## Gate D round-4 result and remediation

Round 4 result:

- 0 BLOCKER;
- 1 MAJOR;
- 3 MINOR;
- 4 NOTE;
- verdict: BLOCKED.

Round-4 MAJOR:

The documented direct `caddy reload` path did not use the same
`EnvironmentFile` contract as validation, so Caddyfile parse-time
environment substitution could differ between validation and reload.

Remediation:

- Caddy reload is now performed through `systemctl reload caddy`;
- the Caddy systemd drop-in owns the `ExecReload` command;
- `ExecReload` targets the permissioned Unix Admin socket;
- reload executes inside `caddy.service`, which loads
  `/etc/quickaside-gateway/caddy.env`;
- direct operator-shell `caddy reload` is prohibited by the runbook;
- regression coverage verifies the environment/reload contract;
- :80/:443 contract tests now also pin write and idle timeouts.

Current evidence:

- full gateway suite: **99 passed, 2 upstream warnings**;
- deployment contract suite: **10 passed**;
- pairing concurrency: **20/20 PASS**;
- replay concurrency: **20/20 PASS**;
- compileall: PASS;
- git diff --check: PASS.

Gate D round 5 completed BLOCKED; its MAJOR was remediated before round 6.


## Gate D round-5 result and remediation

Round 5 result:

- 0 BLOCKER;
- 1 MAJOR;
- 4 MINOR;
- 4 NOTE;
- verdict: BLOCKED.

The Round-4 reload/environment MAJOR was verified RESOLVED.

Round-5 MAJOR:

The deployment contract allowed `Caddy >= 2.10`, which did not guarantee
that the Internet-facing reverse proxy was an upstream-supported `2.latest`
release.

Remediation:

- QAG-003 records an exact reviewed Caddy version;
- reviewed version: `v2.11.4`;
- Gate E must verify the installed version equals that reviewed value;
- Gate E must independently verify the reviewed version is still upstream's
  supported stable `2.latest`;
- publication of a newer stable release blocks activation until the version
  delta is explicitly reviewed.

Gate D round 6 completed PASS_WITH_NOTES; Gate E is eligible to open.


## Gate D round-6 candidate state

Round-5 MAJOR remediation is implemented locally.

Caddy version security contract:

- QAG-003 reviewed version: `v2.11.4`;
- reviewed version is persisted in `deploy/reviewed-versions.env`;
- Gate E must verify installed Caddy equals the reviewed version;
- Gate E must independently verify that version is still upstream's
  supported stable `2.latest`;
- if upstream publishes a newer stable release before activation,
  deployment remains blocked until that delta is reviewed.

Current evidence:

- full gateway suite: **99 passed, 2 upstream warnings**;
- deployment contract suite: **12 passed**;
- pairing concurrency: **20/20 PASS**;
- replay concurrency: **20/20 PASS**;
- compileall: PASS;
- git diff --check: PASS.

Gate D round 6 completed PASS_WITH_NOTES; Gate E is eligible to open.


## Gate D round-6 result

Status: **PASS_WITH_NOTES**

Independent review result:

- 0 BLOCKER;
- 0 MAJOR;
- 4 MINOR;
- 5 NOTE.

The Round-5 Caddy version-contract MAJOR was verified RESOLVED.

Gate D is closed. Gate E may open for explicit mutation approval only.
This does not authorize deployment.

Current evidence:

- full gateway suite: **99 passed, 2 upstream warnings**;
- deployment contract suite: **12 passed**;
- pairing concurrency: **20/20 PASS**;
- replay concurrency: **20/20 PASS**;
- compileall: PASS;
- git diff --check: PASS.
