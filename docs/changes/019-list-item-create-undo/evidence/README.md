# Change 019 — Visual Evidence

Status: PARTIAL — Mandado recovered and inspected; Compras blocked (no device).

## Device

- Attributed capture device for recovered artifact: CPH2791 / Android 16
  (per authoritative Change 019 device-test evidence).
- Evidence-closeout host check on 2026-09-09 (UTC): `adb devices` and
  `adb devices -l` both return `List of devices attached` with no devices
  attached. No new device capture was possible in this turn.

## Files

- `evidence/mandado-create-undo.png` — PRESENT.
  - Valid PNG: `PNG image data, 1080 x 2354, 8-bit/color RGBA, non-interlaced`
    (verified via `file`); `sips` confirms 1080 x 2354, format png.
  - Size: 165179 bytes.
  - State shown: Mandado / "Mandado actual" screen; "Agregar producto"
    field cleared (placeholder "Ej. leche"); disabled "+ Agregar" pill;
    legible product rows Chobani, Arroz, Fruta; native snackbar
    "Producto agregado" with action "Deshacer"; "Terminar mandado" button;
    global lavender mic Capture FAB bottom-right; bottom bar with exactly
    four destinations Inicio / Pendientes / Listas (selected) / Memoria.
- `evidence/compras-create-undo.png` — MISSING (blocked).
  - Required state (not captured): Compras screen; newly created product
    visible; native snackbar "Producto agregado" with action "Deshacer";
    global Capture FAB; four bottom destinations.
  - No fake/test-only UI was used as a substitute. No file was fabricated.

## Visual inspection (actual saved PNGs vs. contract)

References used:

- `docs/UX_UI_REFERENCE.md`
- `docs/design/QUICK_ASIDE_UX_UI_REFERENCE_V3.png` (layout/hierarchy/density
  direction only; embedded legacy `VoiceApp` label ignored per naming rule)
- `docs/changes/019-list-item-create-undo/SPEC.md`

Result for `mandado-create-undo.png`: PASS on all checked points.

- Snackbar/FAB/navigation collision: none observed. Snackbar sits above the
  "Terminar mandado" row; mic FAB and bottom navigation are fully separate.
- Clipping: none material. Snackbar message and "Deshacer" action are fully
  visible; the snackbar overlays only the lower card edge (expected native
  overlay behavior), product text remains readable.
- Legibility: header, field, rows, snackbar, button, and navigation labels
  are all legible.
- Android-native Material appearance: outlined text field, pill button,
  Material cards with checkboxes, native dark snackbar, Material 3 bottom
  navigation with selected pill, circular mic FAB. Consistent with light-mode
  neutral-surface direction.
- Four destinations preserved: Inicio, Pendientes, Listas, Memoria.
- Capture remains an action, not a fifth destination: capture is the
  separate mic FAB; bottom bar contains only the four destinations.
- Product row remains readable: Chobani and Arroz fully; Fruta text readable.
- No unexpected visual regression observed.

Result for `compras-create-undo.png`: N/A — file does not exist; nothing to
inspect and nothing counted as PASS.

## Limitation / blocker

Compras visual evidence could not be captured in this turn because the
CPH2791 / Android 16 device is genuinely unavailable (`adb devices -l`
empty). The visual evidence gate therefore remains incomplete. Independent
review must treat the missing Compras screenshot as blocked evidence, not as
a passing gate, and may require a clean real-production-app capture before
approval.
