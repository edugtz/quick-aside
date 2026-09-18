# Superseded

`QAG-003-live-gateway-deployment` conserva la implementación y el historial
de revisión del diseño anterior de ingress público mediante Caddy.

Ese diseño de deployment no fue adoptado como arquitectura final de
producción.

La arquitectura activa de reemplazo está definida en:

    docs/changes/QAG-003R-private-tailnet-deployment/

y en:

    docs/adr/0005-private-tailnet-gateway-ingress.md

El código válido introducido durante QAG-003 —incluyendo gateway, QA1,
pairing/revocation, replay protection, rate limiting, packaging y el
localhost-only systemd service— permanece vigente salvo que una change
posterior lo modifique explícitamente.

Este directorio queda como evidencia histórica y no debe utilizarse como
runbook activo de deployment.
