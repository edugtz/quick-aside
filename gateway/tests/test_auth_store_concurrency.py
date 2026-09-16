from concurrent.futures import ThreadPoolExecutor
from threading import Barrier

from quickaside_gateway.auth_store import (
    AuthStore,
    PairingCodeError,
    ReplayDetectedError,
)


def test_pairing_code_has_exactly_one_concurrent_winner(
    tmp_path,
):
    db_path = tmp_path / "auth.db"

    store = AuthStore(db_path)
    store.initialize()

    code = store.create_pairing_code(
        ttl_seconds=600,
        now=1000,
    )

    barrier = Barrier(2)

    def attempt(device_id: str) -> str:
        concurrent_store = AuthStore(db_path)
        barrier.wait()

        try:
            concurrent_store.consume_pairing_code(
                code=code,
                device_id=device_id,
                label=device_id,
                public_key_pem="test-public-key",
                now=1001,
            )
            return "success"
        except PairingCodeError:
            return "rejected"

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(
            executor.map(
                attempt,
                [
                    "oppo-race-1",
                    "oppo-race-2",
                ],
            )
        )

    assert sorted(results) == [
        "rejected",
        "success",
    ]

    devices = store.list_devices()

    assert len(devices) == 1
    assert devices[0].device_id in {
        "oppo-race-1",
        "oppo-race-2",
    }


def test_replay_nonce_has_exactly_one_concurrent_winner(
    tmp_path,
):
    db_path = tmp_path / "auth.db"

    store = AuthStore(db_path)
    store.initialize()

    code = store.create_pairing_code(
        ttl_seconds=600,
        now=900,
    )

    store.consume_pairing_code(
        code=code,
        device_id="oppo-test-1",
        label="Test Oppo",
        public_key_pem="test-public-key",
        now=901,
    )

    barrier = Barrier(2)

    def attempt(_: int) -> str:
        concurrent_store = AuthStore(db_path)
        barrier.wait()

        try:
            concurrent_store.accept_nonce(
                device_id="oppo-test-1",
                nonce="same-concurrent-nonce",
                ttl_seconds=300,
                now=1000,
            )
            return "success"
        except ReplayDetectedError:
            return "rejected"

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(
            executor.map(
                attempt,
                [1, 2],
            )
        )

    assert sorted(results) == [
        "rejected",
        "success",
    ]
