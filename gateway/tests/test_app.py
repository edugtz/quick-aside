from pathlib import Path

from fastapi.testclient import TestClient

from quickaside_gateway.app import create_app
from quickaside_gateway.codex_runner import (
    ProviderInvalidOutputError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    ReadinessError,
)
from quickaside_gateway.config import GatewaySettings
from quickaside_gateway.contracts import AddListItemAction, InterpretResponse


class FakeRunner:
    def __init__(self, result=None, failure=None, ready=True):
        self.result = result
        self.failure = failure
        self.ready = ready
        self.interpret_calls = 0
        self.readiness_calls = 0

    async def readiness(self):
        self.readiness_calls += 1
        if not self.ready:
            raise ReadinessError("not ready")

    async def interpret(self, _request):
        self.interpret_calls += 1
        if self.failure:
            raise self.failure
        return self.result


def settings(tmp_path: Path) -> GatewaySettings:
    return GatewaySettings(
        codex_bin="codex",
        codex_home=tmp_path,
        workspace=tmp_path,
        schema_path=tmp_path / "schema.json",
    )


def request_json():
    return {
        "inputText": "Agrega leche al mandado",
        "capturedAt": "2026-09-13T21:24:00Z",
        "timeZone": "America/Mexico_City",
    }


def test_health_does_not_invoke_provider(tmp_path):
    runner = FakeRunner()
    client = TestClient(create_app(settings(tmp_path), runner))
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert runner.interpret_calls == 0
    assert runner.readiness_calls == 0


def test_readiness_maps_failure_without_details(tmp_path):
    client = TestClient(create_app(settings(tmp_path), FakeRunner(ready=False)))
    response = client.get("/readyz")
    assert response.status_code == 503
    assert response.json() == {"status": "not_ready"}


def test_interpret_success(tmp_path):
    runner = FakeRunner(
        result=InterpretResponse(
            actions=[
                AddListItemAction(type="AddListItem", listDefinitionId="mandado", text="leche")
            ]
        )
    )
    client = TestClient(create_app(settings(tmp_path), runner))
    response = client.post("/v1/interpret", json=request_json())
    assert response.status_code == 200
    assert response.json()["actions"][0]["text"] == "leche"


def test_invalid_request_response_is_privacy_safe(tmp_path):
    client = TestClient(create_app(settings(tmp_path), FakeRunner()))
    payload = request_json()
    payload["inputText"] = "SECRET CAPTURE"
    payload["capturedAt"] = "not-a-date"
    response = client.post("/v1/interpret", json=payload)
    assert response.status_code == 422
    assert response.json() == {"code": "invalid_request"}
    assert "SECRET CAPTURE" not in response.text


def test_provider_failures_map_to_stable_codes(tmp_path):
    cases = [
        (ProviderTimeoutError("secret"), 504, "provider_timeout"),
        (ProviderUnavailableError("secret"), 503, "provider_unavailable"),
        (ProviderInvalidOutputError("secret"), 502, "provider_invalid_output"),
    ]
    for failure, status, code in cases:
        client = TestClient(create_app(settings(tmp_path), FakeRunner(failure=failure)))
        payload = request_json()
        payload["inputText"] = "private input"
        response = client.post("/v1/interpret", json=payload)
        assert response.status_code == status
        assert response.json() == {"code": code}
        assert "private input" not in response.text
        assert "secret" not in response.text


def test_logs_do_not_include_capture_or_error_details(caplog, tmp_path):
    caplog.set_level("INFO", logger="quickaside.gateway")
    client = TestClient(
        create_app(
            settings(tmp_path),
            FakeRunner(failure=ProviderUnavailableError("PROVIDER SECRET DETAIL")),
        )
    )
    payload = request_json()
    payload["inputText"] = "PRIVATE CAPTURE TEXT"
    response = client.post("/v1/interpret", json=payload)
    assert response.status_code == 503
    combined = "\n".join(record.getMessage() for record in caplog.records)
    assert "PRIVATE CAPTURE TEXT" not in combined
    assert "PROVIDER SECRET DETAIL" not in combined
    assert "provider_unavailable" in combined

def test_request_body_size_is_bounded_before_provider(tmp_path):
    runner = FakeRunner()
    test_settings = settings(tmp_path)
    object.__setattr__(test_settings, "max_request_body_bytes", 64)
    client = TestClient(create_app(test_settings, runner))

    response = client.post(
        "/v1/interpret",
        content=b"{" + b"x" * 128 + b"}",
        headers={"content-type": "application/json"},
    )

    assert response.status_code == 413
    assert response.json() == {"code": "request_too_large"}
    assert runner.interpret_calls == 0
