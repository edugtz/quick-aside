import json
from datetime import UTC, datetime
from pathlib import Path

import pytest
from pydantic import ValidationError

import quickaside_gateway
from quickaside_gateway.contracts import (
    InterpretRequest,
    ProviderCandidate,
    ProviderOutputError,
    to_interpret_response,
)


def provider_action(action_type: str, **overrides):
    payload = {
        "type": action_type,
        "listDefinitionId": None,
        "text": None,
        "space": None,
        "title": None,
        "dueDate": None,
        "fields": None,
    }
    payload.update(overrides)
    return payload


def test_request_normalizes_trusted_time_and_timezone():
    request = InterpretRequest(
        inputText="Mañana revisa el PR",
        capturedAt="2026-09-13T21:24:00Z",
        timeZone="America/Mexico_City",
    )
    assert request.capturedAt == datetime(2026, 9, 13, 21, 24, tzinfo=UTC)
    assert request.local_captured_at.isoformat() == "2026-09-13T15:24:00-06:00"


@pytest.mark.parametrize(
    "payload",
    [
        {"inputText": "   ", "capturedAt": "2026-09-13T21:24:00Z", "timeZone": "America/Mexico_City"},
        {"inputText": "x", "capturedAt": "2026-09-13T21:24:00", "timeZone": "America/Mexico_City"},
        {"inputText": "x", "capturedAt": "2026-09-13 21:24:00Z", "timeZone": "America/Mexico_City"},
        {"inputText": "x", "capturedAt": 1789334640, "timeZone": "America/Mexico_City"},
        {"inputText": "x", "capturedAt": "2026-09-13T21:24:00Z", "timeZone": "No/Such_Zone"},
        {"inputText": "x" * 4001, "capturedAt": "2026-09-13T21:24:00Z", "timeZone": "America/Mexico_City"},
    ],
)
def test_invalid_request_context_is_rejected(payload):
    with pytest.raises(ValidationError):
        InterpretRequest.model_validate(payload)


def test_all_current_action_families_decode():
    candidate = ProviderCandidate.model_validate(
        {
            "actions": [
                provider_action("AddListItem", listDefinitionId="mandado", text="leche"),
                provider_action("CreateTask", space="TRABAJO", title="Revisar PR", dueDate="2026-09-14"),
                provider_action("CreateNote", text="Llamar al taller"),
                provider_action(
                    "CreateStructuredLog",
                    fields=[
                        {"key": "exercise", "value": "press inclinado"},
                        {"key": "weight", "value": "210 lbs"},
                    ],
                ),
                provider_action("UndoLast"),
            ]
        }
    )
    response = to_interpret_response(candidate)
    assert len(response.actions) == 5
    assert response.actions[3].fields == {"exercise": "press inclinado", "weight": "210 lbs"}


def test_duplicate_structured_log_keys_rejected():
    candidate = ProviderCandidate.model_validate(
        {
            "actions": [
                provider_action(
                    "CreateStructuredLog",
                    fields=[
                        {"key": "weight", "value": "210 lbs"},
                        {"key": "weight", "value": "220 lbs"},
                    ],
                )
            ]
        }
    )
    with pytest.raises(ProviderOutputError):
        to_interpret_response(candidate)


def test_action_specific_required_fields_are_enforced_locally():
    candidate = ProviderCandidate.model_validate({"actions": [provider_action("CreateTask")]})
    with pytest.raises(ProviderOutputError):
        to_interpret_response(candidate)



def test_non_applicable_provider_fields_are_rejected():
    candidate = ProviderCandidate.model_validate(
        {
            "actions": [
                provider_action(
                    "CreateTask",
                    space="TRABAJO",
                    title="Revisar PR",
                    text="must be null",
                )
            ]
        }
    )
    with pytest.raises(ProviderOutputError):
        to_interpret_response(candidate)


def test_provider_action_count_is_bounded():
    with pytest.raises(ValidationError):
        ProviderCandidate.model_validate(
            {"actions": [provider_action("UndoLast") for _ in range(17)]}
        )

def test_provider_schema_uses_flat_strict_action_shape():
    schema_path = Path(quickaside_gateway.__file__).with_name("provider_output_schema.json")
    schema = json.loads(schema_path.read_text())
    item = schema["properties"]["actions"]["items"]
    assert item["type"] == "object"
    assert item["additionalProperties"] is False
    assert set(item["required"]) == set(item["properties"])
    assert "oneOf" not in json.dumps(schema)
