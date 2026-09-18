# ACTIVE WORK

Status: **QAG-004 IMPLEMENTATION COMPLETE — REVIEW PENDING**

## Active change

- Change: `QAG-004 — Android Gateway Integration`
- Package: `docs/changes/QAG-004-android-gateway-integration/`
- Expected branch: `qag-004-android-gateway-integration`
- Verified base: `main` at `9bf585404ecb73604ca43420397c4434bd8160e1`
- Governance: **HIGH-ASSURANCE**
- Objective: connect persisted Android captures to the deployed private
  gateway through QA1, return provider-neutral untrusted output through the
  existing `CapturePlanValidator`, and stop before action execution.
- Current gate: private real-device runtime and visual acceptance are complete.
- The repeated full-suite `PendientesUiTest` timing anomaly is recorded as a
  non-QAG-004 regression finding; the next gate is independent HIGH-ASSURANCE
  review.
- Production pairing-code generation/use was completed with explicit user
  authorization for this gate; the code and device-sensitive registration
  material are not recorded in the repository.

## Most recently completed change

`docs/changes/QAG-003R-private-tailnet-deployment/`

Branch:

`qag-003r-private-tailnet-deployment`

Base:

`13442a90d818511c072b316a77c1398fbc93089b`

## Change

QAG-003R — Private Tailnet Gateway Deployment.

QAG-003R replaces only the superseded public-Caddy ingress architecture from
QAG-003 while preserving the valid gateway, QA1, packaging and localhost-only
systemd work already implemented.

Governance: **HIGH-ASSURANCE**

## Current architecture

ADR-0005 supersedes ADR-0004 for production ingress.

    Quick Aside Android
      -> Tailscale
      -> svc:quickaside
      -> Tailscale Serve HTTPS
      -> http://127.0.0.1:2588
      -> Quick Aside gateway
      -> provider runtime
      -> GPT-5.6 Luna Low

Quick Aside remains loopback-only on `127.0.0.1:2588`.

The existing protected Personal Admin path remains independent:

    svc:personal-admin-ack
      -> http://127.0.0.1:2587

No Quick Aside Funnel or public Quick Aside firewall rule is part of the
architecture.

## Tailnet access decision

Gate E inspected the existing Tailscale Access Controls and found the tailnet
uses a broad existing `* -> * -> *` grant.

Explicit product/operations decision:

- preserve existing Access Controls to avoid affecting Personal Admin or other
  services;
- Tailscale is the private-network boundary;
- QA1 remains the application authorization boundary for protected Quick Aside
  operations;
- non-tailnet exposure remains prohibited.

## Gate D

COMPLETE — **PASS_WITH_NOTES**.

Focused round 2:

- 0 BLOCKER;
- 0 MAJOR;
- 2 accepted MINOR;
- verdict: PASS_WITH_NOTES.

Deterministic evidence before Gate E:

- deployment contract: 19 passed;
- full gateway suite: 108 passed;
- 2 known dependency deprecation warnings;
- compileall: PASS;
- `git diff --check`: PASS.

## Gate E

COMPLETE — **PASS**.

Real-environment evidence:

- `quickaside-gateway.service`: active and enabled;
- `personal-admin-ack.service`: active after Quick Aside activation/restart/reboot;
- Quick Aside listener: `127.0.0.1:2588`;
- Personal Admin listener: `127.0.0.1:2587`;
- `svc:quickaside -> http://127.0.0.1:2588`;
- `svc:personal-admin-ack -> http://127.0.0.1:2587`;
- both Services: tailnet-only;
- local and private `/healthz`: PASS;
- local and private `/readyz`: PASS;
- anonymous protected request: HTTP 401;
- paired synthetic P-256 device: PASS;
- valid QA1 signed Luna Low request: HTTP 200;
- replay rejection: HTTP 401;
- synthetic Gate E device: revoked;
- raw controlled capture absent from Quick Aside journal;
- controlled service restart: PASS;
- user-approved full VPS reboot: PASS;
- Quick Aside, Personal Admin, Hermes and tailscaled active after reboot;
- Tailscale Service mappings persisted after reboot.

## Final repository verification

COMPLETE — **PASS**.

Run on the actual branch after Gate E documentation updates:

    gateway/.venv/bin/python -m pytest gateway/tests/test_deployment_contract.py -q
      -> 19 passed

    gateway/.venv/bin/python -m pytest gateway/tests -q
      -> 108 passed, 2 known dependency deprecation warnings, 6.58s

    gateway/.venv/bin/python -m compileall -q gateway/quickaside_gateway gateway/tests
      -> PASS (no output)

    git diff --check
      -> PASS (no output)

The two warnings remain the previously accepted dependency deprecations:

- Starlette TestClient/httpx deprecation;
- anyio BlockingPortal alias deprecation.

## Engineering closure

QAG-003R implementation, independent review, real-environment Gate E and final
deterministic verification are complete.

Final engineering verdict: **PASS_WITH_NOTES**.

Accepted notes:

- the two existing Gate D MINOR findings remain non-blocking;
- a live destructive rollback drill was intentionally not performed after the
  successful production activation; the reviewed scoped rollback contract is
  deterministically protected and remains available if operationally needed.

No additional security-review round is required without new evidence.

The user retains commit, push, merge and release authority.

## Current stop boundary

QAG-004 ends at validated `CapturePlan` + observable interpretation result.
It does not implement `ActionExecutor`, automatic mutations, fallback, Google
integrations, reminders, public ingress, Tailscale policy changes, Personal
Admin/Hermes changes, commit, push, merge, or release.

The user retains commit, push, merge and release authority.
