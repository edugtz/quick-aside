# Change 006 Evidence

Screenshots were captured from the attached physical `CPH2791` device running
Android API 36 / Android 16 with the debug build:

- `permission-dialog.png` — Android runtime prompt for `RECORD_AUDIO` shown
  after invoking voice capture.
- `voice-listening.png` — temporary dark listening surface with the dominant
  microphone orb, `Escuchando…`, and an explicit Cancel action.
- `voice-no-speech.png` — real-device no-match outcome with the concise
  Spanish retry message and no saved-capture receipt.
- `memoria-voice.png` — Memoria showing a real Spanish voice transcript with
  `Voz · Hoy, 20:05`.
- `after-cancel.png` — return to Inicio after canceling an active session.

During manual QA the device first reported an on-device recognizer but did not
support the default Spanish language for that session. The implementation
therefore exercised its normal system-recognizer fallback; a subsequent real
Spanish session produced partial text and a final transcript, which was saved
and rendered in Memoria. The deterministic instrumentation suite covers the
same partial-transcript rendering and exact persistence without depending on
the physical microphone.
