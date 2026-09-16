from pathlib import Path

import pytest

from quickaside_gateway.auth_store import (
    AuthStore,
    DeviceAlreadyRegisteredError,
    PairingCodeError,
    ReplayDetectedError,
)


PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
TEST-PUBLIC-KEY
-----END PUBLIC KEY-----
"""


def make_store(tmp_path: Path) -> AuthStore:
    store = AuthStore(tmp_path / "auth.db")
    store.initialize()
    return store


def test_initialize_creates_restricted_database(tmp_path):
    store = make_store(tmp_path)

    assert store.db_path.exists()
    assert store.db_path.stat().st_mode & 0o777 == 0o600


def test_pairing_code_registers_device_once(tmp_path):
    store = make_store(tmp_path)
    code = store.create_pairing_code(
        600,
        now=1_000,
    )

    device = store.consume_pairing_code(
        code=code,
        device_id="oppo-1",
        label="Oppo",
        public_key_pem=PUBLIC_KEY,
        now=1_010,
    )

    assert device.device_id == "oppo-1"
    assert device.label == "Oppo"
    assert device.public_key_pem == PUBLIC_KEY
    assert device.status == "active"

    with pytest.raises(PairingCodeError):
        store.consume_pairing_code(
            code=code,
            device_id="oppo-2",
            label="Other",
            public_key_pem=PUBLIC_KEY,
            now=1_011,
        )


def test_expired_pairing_code_is_rejected(tmp_path):
    store = make_store(tmp_path)
    code = store.create_pairing_code(
        10,
        now=1_000,
    )

    with pytest.raises(PairingCodeError):
        store.consume_pairing_code(
            code=code,
            device_id="oppo-1",
            label="Oppo",
            public_key_pem=PUBLIC_KEY,
            now=1_011,
        )


def test_pairing_cannot_replace_existing_device_key(tmp_path):
    store = make_store(tmp_path)

    first = store.create_pairing_code(600, now=1_000)
    store.consume_pairing_code(
        code=first,
        device_id="oppo-1",
        label="Oppo",
        public_key_pem=PUBLIC_KEY,
        now=1_001,
    )

    second = store.create_pairing_code(600, now=1_002)

    with pytest.raises(DeviceAlreadyRegisteredError):
        store.consume_pairing_code(
            code=second,
            device_id="oppo-1",
            label="Attacker",
            public_key_pem="different-key",
            now=1_003,
        )

    device = store.get_device("oppo-1")
    assert device is not None
    assert device.public_key_pem == PUBLIC_KEY


def test_device_can_be_revoked(tmp_path):
    store = make_store(tmp_path)
    code = store.create_pairing_code(600, now=1_000)

    store.consume_pairing_code(
        code=code,
        device_id="oppo-1",
        label="Oppo",
        public_key_pem=PUBLIC_KEY,
        now=1_001,
    )

    assert store.revoke_device(
        "oppo-1",
        now=2_000,
    )

    device = store.get_device("oppo-1")
    assert device is not None
    assert device.status == "revoked"
    assert device.revoked_at == 2_000


def test_replay_nonce_survives_store_recreation(tmp_path):
    db_path = tmp_path / "auth.db"

    first = AuthStore(db_path)
    first.initialize()

    code = first.create_pairing_code(600, now=1_000)
    first.consume_pairing_code(
        code=code,
        device_id="oppo-1",
        label="Oppo",
        public_key_pem=PUBLIC_KEY,
        now=1_001,
    )

    first.accept_nonce(
        device_id="oppo-1",
        nonce="0123456789abcdef0123456789abcdef",
        ttl_seconds=300,
        now=1_010,
    )

    second = AuthStore(db_path)
    second.initialize()

    with pytest.raises(ReplayDetectedError):
        second.accept_nonce(
            device_id="oppo-1",
            nonce="0123456789abcdef0123456789abcdef",
            ttl_seconds=300,
            now=1_020,
        )


def test_expired_nonce_can_be_pruned(tmp_path):
    store = make_store(tmp_path)

    code = store.create_pairing_code(600, now=1_000)
    store.consume_pairing_code(
        code=code,
        device_id="oppo-1",
        label="Oppo",
        public_key_pem=PUBLIC_KEY,
        now=1_001,
    )

    nonce = "0123456789abcdef0123456789abcdef"

    store.accept_nonce(
        device_id="oppo-1",
        nonce=nonce,
        ttl_seconds=100,
        now=1_010,
    )

    store.accept_nonce(
        device_id="oppo-1",
        nonce=nonce,
        ttl_seconds=100,
        now=1_111,
    )
