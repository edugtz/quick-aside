# Quick Aside gateway deployment contract

These files define the reviewed production boundary for QAG-003R.
They are deployment inputs, not evidence that deployment has occurred.

## Required runtime shape

Quick Aside Android
-> Tailscale
-> svc:quickaside
-> Tailscale Serve HTTPS :443
-> 127.0.0.1:2588
-> one Uvicorn worker
-> FastAPI
-> bounded provider runtime

Port 2588 must never be publicly exposed.

Tailscale Funnel is prohibited for Quick Aside.

No public Quick Aside UFW rule is required.

## Protected existing service

The VPS already hosts:

    svc:personal-admin-ack
      -> http://127.0.0.1:2587

Never clear, reset, replace or otherwise mutate that Service as part of
Quick Aside deployment.

Quick Aside operations must be scoped to:

    svc:quickaside

## Uvicorn requirements

The reviewed systemd unit fixes:

- `--factory`
- `--host 127.0.0.1`
- `--port 2588`
- `--workers 1`
- `--limit-concurrency 16`
- `--proxy-headers`
- `--forwarded-allow-ips 127.0.0.1`
- `--timeout-keep-alive 5`
- `--no-server-header`
- `--no-access-log`

Do not replace `--forwarded-allow-ips 127.0.0.1` with `*`.

Application rate limiting is process-local. Production must remain
single-worker unless rate limiting is redesigned around shared state.

## Systemd security requirements

The reviewed unit must retain:

- dedicated `quickaside` user/group;
- `UMask=0077`;
- `NoNewPrivileges=true`;
- `PrivateTmp=true`;
- `ProtectSystem=strict`;
- `ProtectHome=true`;
- restricted read/write paths;
- kernel/control-group protections;
- restricted address families;
- empty capability sets;
- native syscall architecture restriction.

Before activation:

    systemd-analyze verify /etc/systemd/system/quickaside-gateway.service

## Persistent state

Quick Aside owns:

    /var/lib/quickaside/auth.db
    /var/lib/quickaside/codex-home
    /var/lib/quickaside/workspace

The service may write only under `/var/lib/quickaside`.

## Isolation

The deployment must not read, write, restart, reconfigure or reuse:

- `/home/hermes`;
- Personal Admin state or databases;
- Hermes credentials;
- Hermes systemd units;
- Personal Admin ACK application state;
- Personal Admin cron jobs.

Tailscale configuration may be changed only for the explicitly approved
Quick Aside Service. Existing Personal Admin Service configuration remains
outside Quick Aside mutation scope.

## Gate E — read-only preflight

Before any production mutation, verify:

    systemctl status quickaside-gateway --no-pager
    ss -ltnp
    sudo ufw status numbered
    tailscale status
    tailscale serve status
    tailscale funnel status

Confirm:

- `svc:personal-admin-ack` remains unchanged;
- Personal Admin still targets `127.0.0.1:2587`;
- Quick Aside has no existing public listener;
- Quick Aside has no Funnel configuration;
- no public Quick Aside UFW rule exists.

In the Tailscale control plane, verify before activation:

- `svc:quickaside` exists, or creation is explicitly approved;
- resource is `tcp:443`;
- expected DNS identity is `quickaside.taildc9db9.ts.net`;
- this VPS is eligible to advertise the Service;
- intended clients are allowed to reach the Service.

QAG-003R intentionally preserves the existing tailnet-wide access policy.

Do not modify tailnet grants or Access Controls as part of this deployment
solely to restrict Quick Aside.

Network access requirements are:

- Quick Aside is unreachable from outside the tailnet;
- tailnet connectivity follows the existing tailnet policy;
- QA1 remains the application authorization boundary for protected
  Quick Aside operations.

If a Service definition, tag or auto-approval change is required, stop and
obtain explicit user approval before mutation.

## Gate E — local gateway first

Before Tailscale activation, prove:

    systemctl is-active quickaside-gateway
    ss -ltnp | grep '127.0.0.1:2588'
    curl --fail --silent http://127.0.0.1:2588/healthz

Do not activate private ingress unless localhost health succeeds.

## Gate E — private Service activation

Requires explicit user approval.

Configure only Quick Aside:

    tailscale serve \
      --service=svc:quickaside \
      --https=443 \
      http://127.0.0.1:2588

After activation:

    tailscale serve status
    tailscale funnel status

Verify:

- `svc:quickaside` targets `127.0.0.1:2588`;
- the Quick Aside endpoint is tailnet-only;
- Funnel is not enabled for Quick Aside;
- `svc:personal-admin-ack` remains unchanged;
- no public Quick Aside listener/UFW rule was introduced.

From an authorized tailnet client, verify:

    https://quickaside.taildc9db9.ts.net/healthz

## QA1 runtime verification

Through the private endpoint verify:

- valid signed request succeeds;
- replayed nonce is rejected;
- revoked device is rejected;
- pairing/revocation behavior remains correct;
- application rate limits remain effective.

QA1 remains required even though ingress is private.

## Failure-path log verification

Use synthetic values only. Never use real credentials or personal capture text.

Exercise one request while the gateway is healthy and one while the backend
is intentionally unavailable.

Use recognizable synthetic canaries for:

- QA1 signature;
- QA1 nonce;
- capture text.

Inspect:

    journalctl -u tailscaled
    journalctl -u quickaside-gateway

The synthetic signature, nonce and capture canaries must not appear in
either journal.

Restore `quickaside-gateway` immediately after the failure-path check.

## Persistence

Verify after restarting only Quick Aside:

    sudo systemctl restart quickaside-gateway
    systemctl is-active quickaside-gateway
    tailscale serve status

Confirm:

- Quick Aside still listens only on `127.0.0.1:2588`;
- `svc:quickaside` remains correct;
- `svc:personal-admin-ack` remains unchanged.

A VPS reboot requires separate explicit user approval.

## Normal rollback

Rollback must affect Quick Aside only.

Stop accepting new Quick Aside connections:

    tailscale serve drain svc:quickaside

After active Quick Aside requests have completed, remove only its Service
configuration:

    tailscale serve clear svc:quickaside

Then stop and disable only:

    sudo systemctl disable --now quickaside-gateway

Verify afterward:

- no listener remains on `127.0.0.1:2588`;
- `svc:quickaside` is no longer active;
- `svc:personal-admin-ack` remains unchanged;
- Personal Admin remains on `127.0.0.1:2587`.

Never perform a global Tailscale Serve reset for Quick Aside rollback.

## Emergency containment

If graceful draining is inappropriate, containment may clear only:

    tailscale serve clear svc:quickaside

Do not clear, reset or otherwise mutate unrelated Services.

Production mutation commands belong to Gate E and require explicit user
approval.
