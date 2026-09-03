# Quick Aside — UX/UI Reference v3

Status: **Accepted visual/product baseline**  
Canonical image: `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png`

## Branding status of the v3 image

The v3 PNG was generated before the project rename and may still show the legacy `VoiceApp` heading inside the image itself. That embedded label is obsolete and MUST NOT be copied into implementation. The accepted product name is **Quick Aside**; see `docs/NAMING.md`. The image remains canonical for layout, hierarchy, density, interaction direction, and visual personality only.

## Purpose

This document and the canonical image prevent UI drift across sessions, models, and implementation agents.

The image is a visual direction artifact. This file is the written contract that explains what is intentional in the image and what is merely illustrative.

## Required use during UI work

For every change that creates or materially changes user-visible UI:

1. Inspect the canonical image before writing the plan.
2. Read this document and the relevant `PROJECT_SPEC` section.
3. State in the active change spec which reference screens/invariants are affected.
4. Implement Android-native behavior first; use the image for hierarchy, density, grouping, and visual personality.
5. Produce screenshots or real-device evidence for review.
6. If implementation must deviate, document the reason and get product approval when the deviation affects product behavior or visual direction.

## Source hierarchy for UI decisions

1. Latest explicit user/product decision.
2. Active change acceptance scenarios.
3. Written UX invariants in this file and `PROJECT_SPEC.md`.
4. Canonical visual reference image.
5. Material 3 / Android platform conventions.

This ordering matters because generated mockups can contain incidental copy, spacing, timestamps, or impossible interactions. Preserve intent, not accidents.

## Accepted UX thesis

> **Capture invisibly. Manage explicitly.**

Capture should trend toward a system capability that disappears after success. Management/search/correction/history should remain an explicit, information-dense but calm app experience.

## Accepted primary flow

`Invocar → Hablar/Escribir → Guardar → Receipt → Continuar`

Not part of the default happy path:

- mandatory transcript confirmation;
- mandatory interpretation confirmation;
- mandatory manual Save button.

### Optional review branch

Receipt or live capture may expose `Editar`.

The editor must distinguish:

- transcript correction;
- interpretation/destination correction.

### Low-confidence branch

Ask one focused question or present a small choice surface. Do not force a full-screen review workflow when one tap can resolve the ambiguity.

## Screen/reference intent

### 1. Inicio

Purpose: immediate capture + compact contextual state.

Preserve:

- dominant voice CTA;
- first-class text entry;
- small contextual cards for Mandado, next event, Personal, Trabajo;
- no analytics/dashboard clutter.

### 2. Captura

Purpose: reassure the user that Quick Aside is listening and provide a lightweight opportunity to stop/correct.

Preserve:

- transcript visible but secondary;
- obvious listening/processing state;
- minimal controls;
- voice orb may be used here as product identity/status feedback.

### 3. Guardado / Receipt

Purpose: trust and reversibility after automatic execution.

Preserve:

- saved state is already committed;
- grouped summary by destination;
- Undo immediately available;
- Edit available but optional;
- adapt size to complexity instead of forcing a full screen for trivial captures.

Implementation may use snackbar, bottom sheet, overlay, or another native pattern depending on capture complexity.

### 4. Ambigüedad

Purpose: smallest interruption necessary to safely continue.

Preserve:

- one focused question;
- few high-confidence choices;
- optional learning from user's correction when appropriate;
- Cancel/keep-in-Inbox path when the answer cannot be safely inferred.

### 5. Pendientes

Purpose: explicit task management.

Preserve:

- Personal/Trabajo distinction;
- visible Google Tasks sync state without dominating the screen;
- normal task lifecycle and due dates;
- Android-native task list behavior.

### 6. Listas

Purpose: list management and historical sessions.

Preserve:

- Mandado session model;
- Compras continuous model;
- previous Mandado sessions discoverable;
- fast checkbox interactions.

### 7. Memoria

Purpose: unified retrieval and durable personal history.

Preserve:

- global search prominence;
- Notes, Structured Logs, History, Archive/Backup accessible without creating many top-level destinations;
- examples like gym logs represented as structured records rather than chat bubbles;
- export/backup discoverability without turning the home screen into an admin console.

## Navigation baseline

Main destinations:

- Inicio
- Pendientes
- Listas
- Memoria

Capture is a global action, not a permanent navigation destination.

The exact Material implementation (FAB, center action, etc.) may be decided during implementation based on accessibility and ergonomics, but the information architecture above should remain stable unless product evidence says otherwise.

## Visual language

The canonical reference currently suggests:

- light mode as initial default;
- clean neutral surfaces;
- blue/teal primary accents;
- semantic accents for list/task/event types when useful;
- rounded Material-style surfaces;
- clear hierarchy and generous spacing;
- native Android iconography;
- custom orb/voice motif concentrated in capture/listening states.

Not frozen yet:

- exact colors/tokens;
- dark theme;
- final icon/brand mark;
- motion spec.

Do not interpret the reference as permission to build a custom-rendered fantasy UI at the expense of Android conventions.

## One-handed and accessibility principles

- Primary capture action within comfortable reach.
- Minimum recommended touch targets.
- Text scaling must not break critical flows.
- State should not be conveyed only by color.
- Important actions require accessible labels/content descriptions.
- Low-confidence prompts should remain usable with one hand.
- Receipt/Undo timing must not make accessibility impossible; critical corrections remain reachable from history/activity.

## Visual review contract

A UI change is not complete solely because it compiles.

For meaningful UI changes, provide appropriate evidence:

- screenshots at representative state(s);
- real-device interaction for voice/reminder/system-surface behavior where simulation is insufficient;
- comparison against the canonical reference and active change acceptance criteria;
- accessibility/layout checks appropriate to the touched screens.

## Future visual references

If a future approved visual direction supersedes v3:

1. add a new versioned image, e.g. `QUICK_ASIDE_UX_UI_REFERENCE_V4.png`;
2. update this document with what changed and why;
3. preserve older references for decision history unless they are clearly marked obsolete;
4. update agent pointers atomically.

Do not silently replace the canonical image while active UI work depends on it.
