from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from quickaside_gateway.app import create_app
from quickaside_gateway.auth_store import AuthStore
from quickaside_gateway.config import GatewaySettings


class FakeRunner:
    async def readiness(self):
        return None

    async def interpret(self, request):
        raise AssertionError("pairing must never invoke provider")


def settings(
    tmp_path: Path,
    *,
    max_request_body_bytes: int = 32 * 1024,
) -> GatewaySettings:
    return GatewaySettings(
        codex_bin="codex",
        codex_home=None,
        workspace=None,
        schema_path=tmp_path / "schema.json",
        auth_db_path=tmp_path / "auth.db",
        max_request_body_bytes=max_request_body_bytes,
    )


def public_key_pem(curve=None) -> str:
    curve = curve or ec.SECP256R1()
    private_key = ec.generate_private_key(curve)

    return (
        private_key.public_key()
        .public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        .decode("ascii")
    )


def create_pairing_code(
    test_settings: GatewaySettings,
    *,
    now: int | None = None,
) -> str:
    store = AuthStore(test_settings.auth_db_path)
    store.initialize()

    return store.create_pairing_code(
        test_settings.pairing_code_ttl_seconds,
        now=now,
    )


def pair_payload(
    *,
    pairing_code: str,
    device_id: str = "oppo-test-1",
    public_key: str | None = None,
):
    return {
        "pairingCode": pairing_code,
        "deviceId": device_id,
        "label": "Test Oppo",
        "publicKeyPem": public_key or public_key_pem(),
    }


def test_pairing_registers_p256_device(tmp_path):
    test_settings = settings(tmp_path)
    code = create_pairing_code(test_settings)

    client = TestClient(
        create_app(
            test_settings,
            FakeRunner(),
        )
    )

    response = client.post(
        "/v1/pair",
        json=pair_payload(pairing_code=code),
    )

    assert response.status_code == 200
    assert response.json() == {
        "deviceId": "oppo-test-1",
        "status": "active",
    }

    store = AuthStore(test_settings.auth_db_path)
    device = store.get_device("oppo-test-1")

    assert device is not None
    assert device.status == "active"


def test_pairing_rejects_invalid_code(tmp_path):
    test_settings = settings(tmp_path)

    client = TestClient(
        create_app(
            test_settings,
            FakeRunner(),
        )
    )

    response = client.post(
        "/v1/pair",
        json=pair_payload(
            pairing_code="invalid-code-that-is-long-enough",
        ),
    )

    assert response.status_code == 400
    assert response.json() == {
        "code": "pairing_failed",
    }


def test_pairing_code_cannot_be_reused(tmp_path):
    test_settings = settings(tmp_path)
    code = create_pairing_code(test_settings)

    client = TestClient(
        create_app(
            test_settings,
            FakeRunner(),
        )
    )

    first = client.post(
        "/v1/pair",
        json=pair_payload(
            pairing_code=code,
            device_id="oppo-test-1",
        ),
    )

    second = client.post(
        "/v1/pair",
        json=pair_payload(
            pairing_code=code,
            device_id="oppo-test-2",
        ),
    )

    assert first.status_code == 200
    assert second.status_code == 400
    assert second.json() == {
        "code": "pairing_failed",
    }


def test_pairing_rejects_non_p256_key(tmp_path):
    test_settings = settings(tmp_path)
    code = create_pairing_code(test_settings)

    client = TestClient(
        create_app(
            test_settings,
            FakeRunner(),
        )
    )

    response = client.post(
        "/v1/pair",
        json=pair_payload(
            pairing_code=code,
            public_key=public_key_pem(ec.SECP384R1()),
        ),
    )

    assert response.status_code == 400
    assert response.json() == {
        "code": "pairing_failed",
    }


def test_pairing_respects_request_body_limit(tmp_path):
    test_settings = settings(
        tmp_path,
        max_request_body_bytes=128,
    )

    client = TestClient(
        create_app(
            test_settings,
            FakeRunner(),
        )
    )

    response = client.post(
        "/v1/pair",
        json={
            "pairingCode": "x" * 32,
            "deviceId": "oppo-test-1",
            "label": "x" * 100,
            "publicKeyPem": "x" * 4096,
        },
    )

    assert response.status_code == 413
    assert response.json() == {
        "code": "request_too_large",
    }


def test_pairing_rate_limit_is_global(tmp_path):
    test_settings = settings(tmp_path)

    object.__setattr__(
        test_settings,
        "pair_rate_limit_count",
        1,
    )

    first_code = create_pairing_code(test_settings)
    second_code = create_pairing_code(test_settings)

    client = TestClient(
        create_app(
            test_settings,
            FakeRunner(),
        )
    )

    first = client.post(
        "/v1/pair",
        json=pair_payload(
            pairing_code=first_code,
            device_id="oppo-test-1",
        ),
    )

    second = client.post(
        "/v1/pair",
        json=pair_payload(
            pairing_code=second_code,
            device_id="oppo-test-2",
        ),
    )

    assert first.status_code == 200
    assert second.status_code == 429
    assert second.json() == {
        "code": "rate_limited",
    }



def test_invalid_pairing_payload_consumes_rate_limit(
    tmp_path,
):
    test_settings = settings(tmp_path)

    object.__setattr__(
        test_settings,
        "pair_rate_limit_count",
        1,
    )

    code = create_pairing_code(test_settings)

    client = TestClient(
        create_app(
            test_settings,
            FakeRunner(),
        )
    )

    invalid = client.post(
        "/v1/pair",
        content=b"{not-json",
        headers={
            "content-type": "application/json",
        },
    )

    valid = client.post(
        "/v1/pair",
        json=pair_payload(
            pairing_code=code,
            device_id="oppo-test-1",
        ),
    )

    assert invalid.status_code == 422

    assert valid.status_code == 429
    assert valid.json() == {
        "code": "rate_limited",
    }
