# Change 012 visual evidence

Captured on the connected `CPH2791` device running Android 16 from the
focused `MandadoHistoryUiTest` instrumentation flow.

- `mandado-history-affordance.png` — current Mandado with the restrained
  `Historial` action visible.
- `mandado-history-list.png` — two completed sessions with local date/time and
  item counts.
- `mandado-history-detail.png` — one completed session with mixed checked and
  unchecked retained items.

The screenshots use deterministic test data and an injected UTC/English
formatter so the visual states are repeatable; production defaults use the
device's local timezone and locale.
