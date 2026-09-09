# Change 019 — Visual Evidence

Status: COMPLETE — both real-device screenshots captured and inspected.

## Device

- Attributed capture device for recovered artifact: CPH2791 / Android 16
  (per authoritative Change 019 device-test evidence).
- Evidence-closeout on 2026-09-09: `adb devices -l` confirmed CPH2791
  (Android 16). Branch: `chg-019-list-item-create-undo`. The real production
  app was launched and used through Listas → Compras; `Leche de avena` was
  created and the screenshot was captured directly with adb. No production
  code was modified and no JVM/Room/Compose tests, assemble, or lint were
  rerun per the visual-evidence-only gate.

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
- `evidence/compras-create-undo.png` — PRESENT.
  - State shown: Compras screen with newly created `Leche de avena` visible;
    native snackbar "Producto agregado" with action "Deshacer"; global
    lavender mic Capture FAB; and exactly four bottom destinations Inicio /
    Pendientes / Listas (selected) / Memoria.

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

Result for `compras-create-undo.png`: PASS. The product row, snackbar message,
Deshacer action, Capture FAB, and four destinations are fully visible and
legible. The snackbar does not materially collide with the FAB or navigation;
no material clipping or unexpected visual regression was observed.

## Result

Both required real-device screenshots exist and pass visual inspection. The
evidence gate is complete; independent engineering/visual review remains
pending and unchecked.
