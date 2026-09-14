from quickaside_gateway.contracts import InterpretRequest
from quickaside_gateway.prompt import build_prompt


def test_prompt_contains_trusted_temporal_context_and_null_rule():
    request = InterpretRequest(
        inputText="Mañana revisa el PR",
        capturedAt="2026-09-13T21:24:00Z",
        timeZone="America/Mexico_City",
    )
    prompt = build_prompt(request)
    assert "capturedAtUtc: 2026-09-13T21:24:00Z" in prompt
    assert "localDate: 2026-09-13" in prompt
    assert "timeZone: America/Mexico_City" in prompt
    assert "Do not invent sourceCaptureId" in prompt
    assert "Every provider action has all schema properties present." in prompt
    assert "to null." in prompt
    assert "Mañana revisa el PR" in prompt


def test_prompt_defines_required_create_task_fields_and_canonical_work_example():
    request = InterpretRequest(
        inputText="Mañana revisa el PR",
        capturedAt="2026-09-13T21:24:00Z",
        timeZone="America/Mexico_City",
    )
    prompt = build_prompt(request)
    assert "CreateTask: space and title MUST be non-null" in prompt
    assert '"Mañana revisa el PR" -> CreateTask, TRABAJO' in prompt
    assert 'title "Revisar el PR"' in prompt
