# Change 001 — Project Bootstrap — PLAN

## Preflight

Before editing:

1. Verify current official Android/Compose/Room project guidance and supported toolchain versions.
2. Use **Quick Aside** as the display/project name and select a stable application/package ID with the user; do not reuse the obsolete `VoiceApp` codename.
3. Confirm target/min SDK based on current platform needs rather than stale assumptions.
4. Confirm local JDK/Gradle/Android SDK environment.
5. Confirm repository branch policy; new solo project default is `main` + short-lived change branches where review is useful.

## Implementation approach

1. Create the Android project with the smallest standard app structure.
2. Add only baseline dependencies required for Compose, Material 3, navigation shell, Room foundation, and tests.
3. Establish concise package boundaries, avoiding speculative abstractions.
4. Add a minimal design-system/theme layer informed by UX v3 but do not freeze branding tokens yet.
5. Add navigation destinations: Inicio / Pendientes / Listas / Memoria.
6. Add a global Capture action placeholder.
7. Preserve project docs/design reference and root `AGENTS.md` in repo.
8. Add/confirm deterministic build, test, and lint commands.
9. Run verification from smallest relevant check to full change gates.
10. Capture UI evidence and review against UX v3.

## Risks

- Overbuilding architecture before feature constraints are known.
- Treating the generated reference image as pixel-perfect truth rather than visual intent.
- Freezing outdated Android versions without official-doc preflight.
- Adding integrations prematurely.

## Split signals

If bootstrap expands into real persistence schema, OAuth, AI integration, or multiple fully designed feature screens, stop and split those into separate changes.
