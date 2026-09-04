# Change 005 Evidence

Screenshots were captured from the local `Pixel_9_Pro` emulator (`emulator-5554`)
using the normal Inicio capture flow and then opening Memoria:

- `memoria-empty.png` — isolated app data, explicit empty state.
- `memoria-one-capture.png` — one persisted Text capture with original text,
  kind, and local timestamp.
- `memoria-multiple-captures.png` — three persisted Text captures rendered
  newest first.

The isolated Android instrumentation suite was also run directly on that
emulator with `AndroidJUnitRunner`; all 16 tests completed successfully. The
required Gradle connected gate targeted the attached physical device, but UTP
timed out installing the split APK before test execution on both allowed
attempts.
