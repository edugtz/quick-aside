import json
from pathlib import Path

import pytest

from quickaside_gateway.admin_cli import main
from quickaside_gateway.auth_store import AuthStore


PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
TEST-PUBLIC-KEY
-----END PUBLIC KEY-----
"""


def register_device(
    db_path: Path,
    *,
    device_id: str = "oppo-test-1",
):
    store = AuthStore(db_path)
    store.initialize()

    code = store.create_pairing_code(
        600,
        now=1000,
    )

    store.consume_pairing_code(
        code=code,
        device_id=device_id,
        label="Test Oppo",
        public_key_pem=PUBLIC_KEY,
        now=1001,
    )


def test_create_pairing_code_persists_secret(
    capsys,
    tmp_path,
):
    db_path = tmp_path / "auth.db"

    result = main(
        [
            "--db",
            str(db_path),
            "create-pairing-code",
            "--ttl",
            "60",
        ]
    )

    assert result == 0

    code = capsys.readouterr().out.strip()

    assert len(code) >= 16

    store = AuthStore(db_path)

    device = store.consume_pairing_code(
        code=code,
        device_id="oppo-test-1",
        label="Test Oppo",
        public_key_pem=PUBLIC_KEY,
    )

    assert device.status == "active"


def test_list_devices_excludes_public_key(
    capsys,
    tmp_path,
):
    db_path = tmp_path / "auth.db"

    register_device(db_path)

    result = main(
        [
            "--db",
            str(db_path),
            "list-devices",
        ]
    )

    assert result == 0

    output = capsys.readouterr().out.strip()
    devices = json.loads(output)

    assert devices == [
        {
            "createdAt": 1001,
            "deviceId": "oppo-test-1",
            "label": "Test Oppo",
            "revokedAt": None,
            "status": "active",
        }
    ]

    assert PUBLIC_KEY not in output
    assert "publicKey" not in output


def test_revoke_device(
    capsys,
    tmp_path,
):
    db_path = tmp_path / "auth.db"

    register_device(db_path)

    result = main(
        [
            "--db",
            str(db_path),
            "revoke-device",
            "oppo-test-1",
        ]
    )

    assert result == 0

    response = json.loads(
        capsys.readouterr().out
    )

    assert response == {
        "deviceId": "oppo-test-1",
        "status": "revoked",
    }

    store = AuthStore(db_path)
    device = store.get_device("oppo-test-1")

    assert device is not None
    assert device.status == "revoked"


def test_revoke_unknown_or_already_revoked_is_nonzero(
    capsys,
    tmp_path,
):
    db_path = tmp_path / "auth.db"

    result = main(
        [
            "--db",
            str(db_path),
            "revoke-device",
            "unknown-device",
        ]
    )

    assert result == 2

    response = json.loads(
        capsys.readouterr().out
    )

    assert response == {
        "deviceId": "unknown-device",
        "status": "not_changed",
    }



def test_create_pairing_code_rejects_excessive_ttl(
    tmp_path,
):
    db_path = tmp_path / "auth.db"

    with pytest.raises(
        SystemExit,
        match="--ttl must be between 1 and 900 seconds",
    ):
        main(
            [
                "--db",
                str(db_path),
                "create-pairing-code",
                "--ttl",
                "901",
            ]
        )
