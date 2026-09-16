import base64
import json
import secrets
import time
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from quickaside_gateway.app import create_app
from quickaside_gateway.auth_store import AuthStore
from quickaside_gateway.codex_runner import (
    ProviderInvalidOutputError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    ReadinessError,
)
from quickaside_gateway.config import GatewaySettings
from quickaside_gateway.contracts import (
    AddListItemAction,
    InterpretResponse,
)
from quickaside_gateway.request_auth import (
    build_qa1_canonical_request,
)


DEVICE_ID = "oppo-test-1"


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
        auth_db_path=tmp_path / "auth.db",
    )


def request_json():
    return {
        "inputText": "Agrega leche al mandado",
        "capturedAt": "2026-09-13T21:24:00Z",
        "timeZone": "America/Mexico_City",
    }


def success_response():
    return InterpretResponse(
        actions=[
            AddListItemAction(
                type="AddListItem",
                listDefinitionId="mandado",
                text="leche",
            )
        ]
    )


def b64url(value: bytes) -> str:
    return (
        base64.urlsafe_b64encode(value)
        .rstrip(b"=")
        .decode("ascii")
    )


def register_device(
    test_settings: GatewaySettings,
):
    private_key = ec.generate_private_key(
        ec.SECP256R1()
    )

    public_key_pem = (
        private_key.public_key()
        .public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        .decode("ascii")
    )

    store = AuthStore(test_settings.auth_db_path)
    store.initialize()

    code = store.create_pairing_code(
        test_settings.pairing_code_ttl_seconds,
    )

    store.consume_pairing_code(
        code=code,
        device_id=DEVICE_ID,
        label="Test Oppo",
        public_key_pem=public_key_pem,
    )

    return private_key


def json_body(payload) -> bytes:
    return json.dumps(
        payload,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")


def auth_headers(
    private_key,
    body: bytes,
    *,
    nonce: str | None = None,
    timestamp: str | None = None,
    signed_path: str = "/v1/interpret",
):
    timestamp = timestamp or str(int(time.time()))
    nonce = nonce or b64url(secrets.token_bytes(16))

    canonical = build_qa1_canonical_request(
        method="POST",
        path=signed_path,
        body=body,
        device_id=DEVICE_ID,
        timestamp=timestamp,
        nonce=nonce,
    )

    signature = private_key.sign(
        canonical,
        ec.ECDSA(hashes.SHA256()),
    )

    return {
        "content-type": "application/json",
        "X-QA-Device-Id": DEVICE_ID,
        "X-QA-Timestamp": timestamp,
        "X-QA-Nonce": nonce,
        "X-QA-Signature": b64url(signature),
    }


def signed_post(
    client,
    private_key,
    payload,
):
    body = json_body(payload)

    return client.post(
        "/v1/interpret",
        content=body,
        headers=auth_headers(
            private_key,
            body,
        ),
    )


def test_health_does_not_invoke_provider(tmp_path):
    runner = FakeRunner()
    client = TestClient(
        create_app(settings(tmp_path), runner)
    )

    response = client.get("/healthz")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert runner.interpret_calls == 0
    assert runner.readiness_calls == 0


def test_readiness_maps_failure_without_details(tmp_path):
    client = TestClient(
        create_app(
            settings(tmp_path),
            FakeRunner(ready=False),
        )
    )

    response = client.get("/readyz")

    assert response.status_code == 503
    assert response.json() == {
        "status": "not_ready"
    }


def test_interpret_success(tmp_path):
    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    response = signed_post(
        client,
        private_key,
        request_json(),
    )

    assert response.status_code == 200
    assert response.json()["actions"][0]["text"] == "leche"
    assert runner.interpret_calls == 1


def test_missing_auth_is_rejected_before_provider(tmp_path):
    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(settings(tmp_path), runner)
    )

    response = client.post(
        "/v1/interpret",
        json=request_json(),
    )

    assert response.status_code == 401
    assert response.json() == {
        "code": "authentication_failed"
    }
    assert runner.interpret_calls == 0


def test_invalid_request_response_is_privacy_safe(tmp_path):
    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    runner = FakeRunner()

    client = TestClient(
        create_app(test_settings, runner)
    )

    payload = request_json()
    payload["inputText"] = "SECRET CAPTURE"
    payload["capturedAt"] = "not-a-date"

    response = signed_post(
        client,
        private_key,
        payload,
    )

    assert response.status_code == 422
    assert response.json() == {
        "code": "invalid_request"
    }
    assert "SECRET CAPTURE" not in response.text
    assert runner.interpret_calls == 0


def test_provider_failures_map_to_stable_codes(tmp_path):
    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    cases = [
        (
            ProviderTimeoutError("secret"),
            504,
            "provider_timeout",
        ),
        (
            ProviderUnavailableError("secret"),
            503,
            "provider_unavailable",
        ),
        (
            ProviderInvalidOutputError("secret"),
            502,
            "provider_invalid_output",
        ),
    ]

    for failure, status, code in cases:
        runner = FakeRunner(failure=failure)

        client = TestClient(
            create_app(test_settings, runner)
        )

        payload = request_json()
        payload["inputText"] = "private input"

        response = signed_post(
            client,
            private_key,
            payload,
        )

        assert response.status_code == status
        assert response.json() == {"code": code}
        assert "private input" not in response.text
        assert "secret" not in response.text
        assert runner.interpret_calls == 1


def test_logs_do_not_include_capture_or_error_details(
    caplog,
    tmp_path,
):
    caplog.set_level(
        "INFO",
        logger="quickaside.gateway",
    )

    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    client = TestClient(
        create_app(
            test_settings,
            FakeRunner(
                failure=ProviderUnavailableError(
                    "PROVIDER SECRET DETAIL"
                )
            ),
        )
    )

    payload = request_json()
    payload["inputText"] = "PRIVATE CAPTURE TEXT"

    response = signed_post(
        client,
        private_key,
        payload,
    )

    assert response.status_code == 503

    combined = "\n".join(
        record.getMessage()
        for record in caplog.records
    )

    assert "PRIVATE CAPTURE TEXT" not in combined
    assert "PROVIDER SECRET DETAIL" not in combined
    assert "provider_unavailable" in combined


def test_request_body_size_is_bounded_before_provider(
    tmp_path,
):
    runner = FakeRunner()

    test_settings = settings(tmp_path)

    object.__setattr__(
        test_settings,
        "max_request_body_bytes",
        64,
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    response = client.post(
        "/v1/interpret",
        content=b"{" + b"x" * 128 + b"}",
        headers={
            "content-type": "application/json"
        },
    )

    assert response.status_code == 413
    assert response.json() == {
        "code": "request_too_large"
    }
    assert runner.interpret_calls == 0


def test_tampered_body_is_rejected_before_provider(
    tmp_path,
):
    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    original_body = json_body(request_json())

    headers = auth_headers(
        private_key,
        original_body,
    )

    tampered = request_json()
    tampered["inputText"] = "tampered"

    response = client.post(
        "/v1/interpret",
        content=json_body(tampered),
        headers=headers,
    )

    assert response.status_code == 401
    assert response.json() == {
        "code": "authentication_failed"
    }
    assert runner.interpret_calls == 0


def test_replay_is_rejected_before_second_provider_call(
    tmp_path,
):
    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    body = json_body(request_json())

    headers = auth_headers(
        private_key,
        body,
    )

    first = client.post(
        "/v1/interpret",
        content=body,
        headers=headers,
    )

    second = client.post(
        "/v1/interpret",
        content=body,
        headers=headers,
    )

    assert first.status_code == 200
    assert second.status_code == 401
    assert second.json() == {
        "code": "authentication_failed"
    }
    assert runner.interpret_calls == 1


def test_revoked_device_is_rejected_before_provider(
    tmp_path,
):
    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    store = AuthStore(test_settings.auth_db_path)

    assert store.revoke_device(DEVICE_ID)

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    response = signed_post(
        client,
        private_key,
        request_json(),
    )

    assert response.status_code == 401
    assert runner.interpret_calls == 0


def test_malformed_signature_is_rejected_before_provider(
    tmp_path,
):
    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    body = json_body(request_json())
    headers = auth_headers(private_key, body)

    headers["X-QA-Signature"] = "not-a-valid-signature"

    response = client.post(
        "/v1/interpret",
        content=body,
        headers=headers,
    )

    assert response.status_code == 401
    assert runner.interpret_calls == 0


def test_signature_is_bound_to_request_path(tmp_path):
    test_settings = settings(tmp_path)
    private_key = register_device(test_settings)

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    body = json_body(request_json())

    headers = auth_headers(
        private_key,
        body,
        signed_path="/v1/not-interpret",
    )

    response = client.post(
        "/v1/interpret",
        content=body,
        headers=headers,
    )

    assert response.status_code == 401
    assert runner.interpret_calls == 0


def test_interpret_rate_limit_blocks_before_provider(
    tmp_path,
):
    test_settings = settings(tmp_path)

    object.__setattr__(
        test_settings,
        "interpret_rate_limit_count",
        1,
    )

    private_key = register_device(test_settings)

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    first = signed_post(
        client,
        private_key,
        request_json(),
    )

    second = signed_post(
        client,
        private_key,
        request_json(),
    )

    assert first.status_code == 200
    assert second.status_code == 429
    assert second.json() == {
        "code": "rate_limited"
    }
    assert runner.interpret_calls == 1


def test_auth_failure_does_not_consume_interpret_limit(
    tmp_path,
):
    test_settings = settings(tmp_path)

    object.__setattr__(
        test_settings,
        "interpret_rate_limit_count",
        1,
    )

    private_key = register_device(test_settings)

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    anonymous = client.post(
        "/v1/interpret",
        json=request_json(),
    )

    signed = signed_post(
        client,
        private_key,
        request_json(),
    )

    assert anonymous.status_code == 401
    assert signed.status_code == 200
    assert runner.interpret_calls == 1



def test_auth_attempt_rate_limit_blocks_before_auth_and_provider(
    tmp_path,
):
    test_settings = settings(tmp_path)

    object.__setattr__(
        test_settings,
        "auth_attempt_rate_limit_count",
        1,
    )

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    first = client.post(
        "/v1/interpret",
        json=request_json(),
    )

    second = client.post(
        "/v1/interpret",
        json=request_json(),
    )

    assert first.status_code == 401
    assert second.status_code == 429
    assert second.json() == {
        "code": "rate_limited"
    }
    assert runner.interpret_calls == 0



def test_global_auth_attempt_limit_blocks_before_auth_and_provider(
    tmp_path,
):
    test_settings = settings(tmp_path)

    object.__setattr__(
        test_settings,
        "auth_global_rate_limit_count",
        1,
    )

    runner = FakeRunner(
        result=success_response()
    )

    client = TestClient(
        create_app(test_settings, runner)
    )

    first = client.post(
        "/v1/interpret",
        json=request_json(),
    )

    second = client.post(
        "/v1/interpret",
        json=request_json(),
    )

    assert first.status_code == 401
    assert second.status_code == 429
    assert second.json() == {
        "code": "rate_limited"
    }

    assert runner.interpret_calls == 0
