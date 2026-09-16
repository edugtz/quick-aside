import pytest
from cryptography.exceptions import UnsupportedAlgorithm

from quickaside_gateway import key_validation
from quickaside_gateway.auth_store import AuthStore, PairingCodeError
from quickaside_gateway.key_validation import InvalidPublicKeyError


def test_unsupported_public_key_algorithm_is_controlled(
    monkeypatch,
):
    def raise_unsupported(_value):
        raise UnsupportedAlgorithm("unsupported algorithm")

    monkeypatch.setattr(
        key_validation.serialization,
        "load_pem_public_key",
        raise_unsupported,
    )

    with pytest.raises(InvalidPublicKeyError):
        key_validation.load_p256_public_key(
            "-----BEGIN PUBLIC KEY-----\n"
            "unused\n"
            "-----END PUBLIC KEY-----\n"
        )


def test_pairing_code_is_expired_at_exact_expiry_time(
    tmp_path,
):
    store = AuthStore(tmp_path / "auth.db")
    store.initialize()

    code = store.create_pairing_code(
        ttl_seconds=60,
        now=1000,
    )

    with pytest.raises(PairingCodeError):
        store.consume_pairing_code(
            code=code,
            device_id="oppo-test-1",
            label="Test Oppo",
            public_key_pem="unused",
            now=1060,
        )
