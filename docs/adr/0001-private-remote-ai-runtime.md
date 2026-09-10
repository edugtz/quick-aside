# ADR-0001 — Private Remote AI Runtime for Capture Interpretation

Status: **Accepted — pending independent review of PLN-001**
Date: 2026-09-10
Decision owner: user
Related: `docs/ARCHITECTURE.md` §5, `docs/AI_WORKFLOW.md` §3, `docs/ROADMAP.md` M2,
`docs/changes/PLN-001-runtime-ai-realignment/`

## Context

- Quick Aside interprets natural-language captures through a provider-neutral
  `AIProvider` / `CaptureInterpreter` boundary (Change 021). Remote model output
  is untrusted and is validated locally before any execution.
- The completed runtime model evaluation selected **GPT-5.6 Luna — Low
  reasoning** as the primary target, with **DeepSeek V4 Flash via OpenCode Go**
  as an evidence-triggered fallback candidate. MiMo-V2.5 was not selected as
  primary because observed schema/contract reliability was worse than the
  finalists.
- The preferred entitlement path is ChatGPT Plus / Codex OAuth rather than
  pay-as-you-go API billing.
- Android-local Codex was investigated separately. The official ARM64-musl
  Codex app-server executes and initializes on the test Android device without
  root, proot, an embedded Linux distribution, Mac-hosted runtime, or VPS; the
  official 0.154.0 runtime was approximately 169 MiB. However, the
  Codex/reqwest Linux-musl transport failed before device-code issuance, even
  after supplying the 145 active Android Conscrypt CA roots through
  `CODEX_CA_CERTIFICATE`. No Cloudflare challenge was observed. Authentication,
  ChatGPT Plus recognition, Luna inference, quota, latency, and lifecycle
  behavior were never demonstrated on Android. The spike stopped there and
  Quick Aside was not modified.
- The Personal Admin project is independently moving toward an always-on
  personal VPS runtime.

## Decision

- Defer Android-embedded Codex. It is not integrated, and another
  Android/Bionic/Termux/fork spike is not planned without materially better
  upstream evidence or official Android support.
- Target a narrow **private remote AI runtime gateway** hosted on shared
  personal VPS infrastructure, reached from Quick Aside Android through a
  private, authenticated path.
- Primary target behind that gateway: GPT-5.6 Luna Low via ChatGPT Plus / Codex
  OAuth.
- Keep DeepSeek V4 Flash via OpenCode Go as a fallback candidate only;
  fallback behavior is evidence-triggered and not yet implemented.
- Quick Aside validates all remote output locally before any future execution.
- Quick Aside shares infrastructure with the Personal Admin/Hermes runtime but
  must not use the Hermes agent conversation or context as its interpretation
  service. The gateway is a separate, bounded, logically isolated consumer.
- Provider credentials, OAuth tokens, and auth state remain on the personal
  runtime side; the Android app does not own provider secrets.

## Consequences

- AI interpretation requires network and shared-VPS availability. Capture is
  persisted locally first, so a remote outage must never make capture lossy;
  deferred/retry interpretation is future focused work.
- M2 completion and final personal-MVP acceptance depend on runtime gateway
  readiness. Provider-independent roadmap work can still proceed (see
  `docs/ROADMAP.md`).
- Keeping provider auth/secrets off-device also keeps the public-distribution
  client-credential problem out of the Android app.
- The gateway becomes a cross-project infrastructure dependency between Quick
  Aside and the Personal Admin/shared-runtime project.
- Provider/model choices remain replaceable behind `AIProvider` and must not
  change domain contracts or stored data.

## Open questions (not decided here)

- Gateway endpoint, authentication, and network mechanism (private/
  Tailscale-style connectivity preferred if consistent with Personal Admin
  architecture).
- Deployment topology, process management, and Codex invocation contract.
- Deferred-interpretation/retry policy after remote unavailability.
- Whether an official Android-supported Codex runtime eventually makes
  Android-local execution viable again.
