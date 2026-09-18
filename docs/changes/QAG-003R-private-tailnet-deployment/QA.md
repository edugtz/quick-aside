# QAG-003R — QA Evidence

Status: ENGINEERING COMPLETE — PASS_WITH_NOTES

## Governance

HIGH-ASSURANCE.

Production infrastructure, authentication boundaries and shared-VPS isolation
require deterministic verification, independent review, explicit rollback and
real-environment evidence.

## Canonical repository baseline

Branch base:

    13442a90d818511c072b316a77c1398fbc93089b

Baseline gateway verification before QAG-003R:

    101 passed
    2 dependency deprecation warnings

The warnings were:

- Starlette TestClient/httpx deprecation;
- anyio BlockingPortal alias deprecation.

No baseline test failure was observed.

## Git ancestry

QAG-003R is based on the existing QAG-003 branch rather than `main`.

Relevant commits above `main`:

    44306ba feat(gateway): add authenticated public ingress deployment
    13442a9 Fix gateway package discovery for deployment

This base is intentionally retained because those commits contain valid
gateway, QA1/authentication, packaging and deployment-runtime work in addition
to the superseded Caddy ingress design.

QAG-003R replaces the ingress architecture only.

## VPS baseline

Observed before QAG-003R implementation:

Host:

    vps-6480fd75

Tailscale:

    1.102.4

Tailscale device identity:

    HostName: personal-admin-vps
    DNSName: personal-admin-vps.taildc9db9.ts.net
    Tags:
      - tag:personal-admin

Existing protected Service:

    svc:personal-admin-ack
      tcp:443
      -> http://127.0.0.1:2587

Observed Serve status identified it as tailnet-only.

Quick Aside state:

    systemd enabled: disabled
    systemd active: inactive

Caddy state:

    package: absent
    service: not-found/inactive

Relevant listener state:

    127.0.0.1:2587

No Quick Aside listener on `2588` was observed.

## Firewall baseline

UFW is active.

Observed allowed inbound rules:

- SSH TCP 22;
- Tailscale UDP 41641;
- traffic on `tailscale0`;
- equivalent IPv6 rules.

No public Quick Aside TCP 80/443/2588 rule was observed.

## Installed Tailscale CLI contract

The installed Tailscale 1.102.4 CLI was inspected directly.

Observed `tailscale serve` capabilities include:

- `--service`;
- `--https`;
- `status`;
- `reset`;
- `drain`;
- `clear`;
- `advertise`;
- `get-config`;
- `set-config`.

Observed CLI documentation states that when `--service` is used, background
mode defaults to true.

Observed `drain` semantics:

- stop accepting new connections for the named Service;
- allow existing connections to continue until closed.

Observed `advertise` semantics:

- advertise the node as a Service proxy;
- useful after a Service has been drained;
- not required when `tailscale serve` initializes the Service.

Observed Funnel contract:

- Funnel exposes local services to the Internet;
- Serve exposes services only within the tailnet.

Therefore Quick Aside must use Serve, never Funnel.

## Protected infrastructure

The following existing resource is outside QAG-003R mutation scope:

    svc:personal-admin-ack
      -> http://127.0.0.1:2587

QAG-003R rollback and deployment operations must remain scoped to
`svc:quickaside`.

## Required repository evidence before Gate D

Pending:

- `git diff --check`;
- targeted deployment-contract tests;
- full gateway test suite;
- Python compile/static verification;
- stale active-contract audit;
- actual diff review;
- Gate D artifact SHA-256.

## Required Gate E evidence

The following items were defined before live activation. Gate E evidence is
recorded later in this document:

- Quick Aside control-plane Service definition/resource/access;
- localhost-only `127.0.0.1:2588`;
- local `/healthz`;
- private Tailscale `/healthz`;
- no Quick Aside Funnel;
- no public Quick Aside listener/UFW rule;
- Personal Admin unchanged;
- QA1 valid signed request;
- replay rejection;
- revocation;
- pairing behavior;
- application rate limits;
- healthy-path synthetic log-canary inspection;
- backend-unavailable synthetic log-canary inspection;
- Quick Aside restart persistence;
- approved reboot persistence;
- graceful scoped rollback;
- Personal Admin unaffected after rollback.

At the time of this pre-Gate-D section, no Gate E production mutation had occurred.

## Repository verification — 2026-09-18

QAG-003R deterministic repository verification:

- deployment-contract tests: 15 passed;
- full gateway suite: 104 passed;
- dependency warnings: 2 known deprecation warnings;
- Python compileall: PASS;
- `git diff --check`: PASS.

Active-contract stale-reference audit:

- no active `QUICKASIDE_PUBLIC_HOST`;
- no active `Caddyfile.quickaside`;
- no active `caddy.env`;
- no active `sslip.io`;
- no active Caddy start/reload/enable procedure;
- no active statement requiring Quick Aside to operate independently of
  Tailscale.

Tailscale operational-command audit:

- Funnel appears only as `tailscale funnel status`;
- no executable `tailscale serve reset` exists;
- all executable Serve clear operations are scoped to
  `svc:quickaside`;
- normal rollback drains `svc:quickaside` before clearing it;
- `svc:personal-admin-ack` remains explicitly protected.

No production VPS, Tailscale control-plane or Android mutation was performed
during this repository verification.

## Gate D round 1 remediation

Independent Gate D round 1 result:

- 0 BLOCKER;
- 2 MAJOR;
- 2 MINOR;
- verdict: BLOCKED.

Required remediation completed:

### GDR1-001

Deployment-contract command parsing now normalizes:

- `sudo` wrappers;
- multiline shell commands.

Regression coverage now rejects:

- Funnel activation;
- global `tailscale serve reset`;
- Quick Aside clear/drain operations scoped to another Service;
- public UFW mutation.

Targeted deployment-contract result after remediation:

    19 passed

### GDR1-002

Round 1 remediation originally required per-principal policy evidence for:

    svc:quickaside:443

That requirement was later superseded by the explicit Gate E product/operations
decision to preserve the existing tailnet-wide Access Controls rather than
rewrite them solely for Quick Aside. The final runtime boundary is recorded
below.

Gate D round 1 MINOR findings GDR1-003 and GDR1-004 remain documented as
non-blocking and were intentionally not expanded into additional scope.

Post-remediation full verification:

- full gateway suite: 108 passed;
- dependency warnings: 2 known deprecation warnings;
- Python compileall: PASS;
- `git diff --check`: PASS.


## Gate D round 2 close

Focused independent re-review of the round-1 remediations concluded:

- GDR1-001: RESOLVED;
- GDR1-002: RESOLVED under the then-current per-principal policy-test contract;
- new BLOCKER: 0;
- new MAJOR: 0;
- existing MINOR findings GDR1-003 and GDR1-004: unchanged and accepted;
- verdict: PASS_WITH_NOTES.

Gate D is closed. No additional review round was opened.

Round-2 artifact SHA-256 recorded during the review workflow:

    c11e63a579f1e2b45b33aa42f76a1160314e49eede4d4f495394ee047c32099e

## Gate E tailnet access decision

During Gate E preflight, the existing Tailscale Access Controls were inspected.

Observed policy:

    src: ["*"]
    dst: ["*"]
    ip:  ["*"]

The tailnet therefore intentionally permits broad connectivity between current
tailnet principals. Replacing that policy solely for Quick Aside could affect
Personal Admin and other existing services.

Explicit product/operations decision:

- preserve the existing tailnet Access Controls unchanged;
- do not introduce Quick Aside-specific grants in QAG-003R;
- Tailscale remains the private network boundary;
- QA1 remains the application authorization boundary for protected Quick Aside
  operations;
- non-tailnet exposure remains prohibited;
- do not modify `svc:personal-admin-ack`.

This decision supersedes the temporary round-1 requirement for positive and
negative per-principal Tailscale policy tests.

Repository deployment-contract tests were updated to encode this decision and
continued to pass:

    19 passed

`git diff --check` also remained clean after the access-policy documentation
update.

## Gate E control-plane activation evidence

Tailscale Service created/verified:

    svc:quickaside
    tcp:443
    quickaside.taildc9db9.ts.net

The VPS was configured as a Service proxy with:

    sudo tailscale serve \
      --service=svc:quickaside \
      --https=443 \
      http://127.0.0.1:2588

The initial advertisement required Tailscale admin approval. After approval,
`tailscale serve status` reported:

    https://personal-admin-ack.taildc9db9.ts.net (tailnet only)
      -> http://127.0.0.1:2587

    https://quickaside.taildc9db9.ts.net (tailnet only)
      -> http://127.0.0.1:2588

Machine-readable Serve configuration preserved both Services independently:

    svc:personal-admin-ack -> http://127.0.0.1:2587
    svc:quickaside         -> http://127.0.0.1:2588

No Funnel activation was performed. No new public Quick Aside UFW ingress was
introduced.

## Gate E local and private health evidence

After starting `quickaside-gateway`:

    systemctl is-active quickaside-gateway
    active

Loopback listener:

    127.0.0.1:2588

Local health:

    {"status":"ok"}

Local readiness:

    {"status":"ready"}

From the Mac through the private Tailscale Service:

    https://quickaside.taildc9db9.ts.net/healthz
    {"status":"ok"}

    https://quickaside.taildc9db9.ts.net/readyz
    {"status":"ready"}

## Gate E QA1 end-to-end evidence

Anonymous protected request through the private endpoint:

    POST /v1/interpret
    -> HTTP 401
    -> {"code":"authentication_failed"}

A short-lived one-time pairing code was created locally on the VPS under the
`quickaside` service identity. A synthetic P-256 device was paired:

    deviceId: gate-e-smoke-1
    label: Gate E smoke

A real QA1-signed request was sent through:

    Mac
      -> Tailscale
      -> svc:quickaside
      -> 127.0.0.1:2588
      -> QA1
      -> provider runtime
      -> GPT-5.6 Luna Low

Input used for the controlled smoke:

    Agrega leche al mandado

Observed provider-neutral response:

    HTTP 200
    {"actions":[{"type":"AddListItem","listDefinitionId":"mandado","text":"Leche"}]}

The exact same signed request was replayed without changing timestamp, nonce,
body or signature:

    HTTP 401
    {"code":"authentication_failed"}

The synthetic device was then revoked. `list-devices` confirmed:

    gate-e-smoke-1 -> revoked

Earlier temporary Gate G/H smoke devices also remained revoked.

The one-time pairing code was consumed and is not recorded in repository
artifacts. Temporary private-key and request files were removed from the Mac.

## Gate E privacy evidence

The Quick Aside journal was inspected for the exact controlled capture text:

    Agrega leche al mandado

Result:

    PASS: raw capture absent from logs

The deterministic gateway suite continues to cover authentication failures,
revoked devices, rate limiting and privacy-safe provider failure mapping.

No provider credential, QA1 secret, private key or pairing code is recorded in
this QA document.

## Gate E isolation evidence

Before persistence testing:

    quickaside-gateway.service   active
    personal-admin-ack.service   active

Listeners:

    127.0.0.1:2587  Personal Admin
    127.0.0.1:2588  Quick Aside

Serve configuration:

    svc:personal-admin-ack -> http://127.0.0.1:2587
    svc:quickaside         -> http://127.0.0.1:2588

Personal Admin remained independent and unchanged by Quick Aside activation.

## Gate E restart persistence

A user-approved controlled restart of only `quickaside-gateway.service` was
performed.

After restart:

- `quickaside-gateway.service`: active;
- `personal-admin-ack.service`: active;
- `127.0.0.1:2588`: loopback-only;
- `127.0.0.1:2587`: loopback-only;
- local `/healthz`: PASS;
- local `/readyz`: PASS;
- private Tailscale `/healthz`: PASS;
- private Tailscale `/readyz`: PASS;
- both Tailscale Service mappings preserved.

`quickaside-gateway.service` was initially observed as `disabled`. The service
was then explicitly enabled for boot and verified:

    enabled
    active

## Gate E reboot persistence

Before reboot, the following existing services were confirmed enabled:

- `tailscaled.service`;
- `personal-admin-ack.service`;
- `hermes-gateway.service`;
- `cron.service`;
- `quickaside-gateway.service`.

The user explicitly approved and executed a full VPS reboot.

After reconnecting to the VPS:

    quickaside-gateway.service   active
    personal-admin-ack.service   active
    hermes-gateway.service       active
    tailscaled.service           active

Listeners after reboot:

    127.0.0.1:2588
    127.0.0.1:2587

Machine-readable Tailscale configuration after reboot still contained:

    svc:personal-admin-ack -> http://127.0.0.1:2587
    svc:quickaside         -> http://127.0.0.1:2588

From the separate Mac terminal after reboot:

    /healthz -> {"status":"ok"}
    /readyz  -> {"status":"ready"}

This is real-environment evidence that Quick Aside, the Tailscale Service
mapping and the protected Personal Admin service survive VPS reboot.

## Rollback evidence and proportional-governance note

A live destructive rollback was not executed after successful activation.
Doing so would deliberately remove the now-working Quick Aside Service and
then require reactivation.

The rollback contract is instead protected by deterministic repository tests
and reviewed runbook invariants:

- drain before clear;
- drain/clear scoped only to `svc:quickaside`;
- global `tailscale serve reset` prohibited;
- `svc:personal-admin-ack` outside mutation scope;
- emergency containment remains Quick-Aside-scoped.

If an operational rollback becomes necessary, the reviewed scoped procedure is
the authority. This accepted limitation does not authorize mutation of Personal
Admin.

## Gate E verdict

Gate E runtime deployment evidence: PASS.

Established in the real environment:

- private Tailscale-only ingress;
- localhost-only FastAPI;
- QA1 enforced through the private endpoint;
- real signed Luna Low request succeeds;
- replay is rejected;
- temporary device pairing and revocation work;
- raw capture text is absent from Quick Aside production logs;
- Personal Admin remains isolated and available;
- restart persistence passes;
- boot enablement is configured;
- full VPS reboot persistence passes.

No Android endpoint change is part of QAG-003R.

## Final deterministic repository verification

After the Gate E evidence documentation updates, the final verification was
run on the actual branch.

Deployment contract:

    gateway/.venv/bin/python -m pytest gateway/tests/test_deployment_contract.py -q

Observed:

    19 passed in 0.01s

Full gateway suite:

    gateway/.venv/bin/python -m pytest gateway/tests -q

Observed:

    108 passed, 2 warnings in 6.58s

The warnings are the same known dependency deprecations already accepted in
the baseline:

- Starlette TestClient/httpx deprecation;
- anyio BlockingPortal alias deprecation.

Compile verification:

    gateway/.venv/bin/python -m compileall -q \
      gateway/quickaside_gateway gateway/tests

Observed:

    PASS (no output)

Whitespace/error-marker verification:

    git diff --check

Observed:

    PASS (no output)

No deterministic failure was observed.

## Closure status

QAG-003R has completed:

- repository implementation;
- deterministic deployment-contract verification;
- independent HIGH-ASSURANCE Gate D review;
- Gate D remediation and focused re-review;
- real-environment Gate E deployment;
- private end-to-end Tailscale reachability;
- QA1 signed-request, replay and revocation evidence;
- production-log privacy evidence;
- Personal Admin isolation evidence;
- controlled service restart;
- boot enablement;
- user-approved VPS reboot and post-reboot persistence;
- final deterministic repository verification.

Final engineering verdict: **PASS_WITH_NOTES**.

Accepted notes:

1. The two Gate D MINOR findings remain accepted and non-blocking.
2. A live destructive rollback drill was intentionally not performed after a
   successful production activation. The reviewed Quick-Aside-scoped rollback
   procedure remains the authority if rollback becomes operationally necessary.

No new BLOCKER or MAJOR finding is open.

No additional Gate D round is required without new evidence.

No Android endpoint change is included in QAG-003R. QAG-4 remains a separate
reviewable change.

The user retains commit, push, merge and release authority.
