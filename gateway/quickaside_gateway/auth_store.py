from __future__ import annotations

import hashlib
import os
import secrets
import sqlite3
import time
from dataclasses import dataclass
from pathlib import Path


class PairingCodeError(RuntimeError):
    pass


class DeviceAlreadyRegisteredError(RuntimeError):
    pass


class ReplayDetectedError(RuntimeError):
    pass


@dataclass(frozen=True)
class DeviceRecord:
    device_id: str
    label: str
    public_key_pem: str
    status: str
    created_at: int
    revoked_at: int | None


class AuthStore:
    def __init__(self, db_path: Path):
        self.db_path = db_path

    def initialize(self) -> None:
        parent = self.db_path.parent

        if not parent.exists():
            parent.mkdir(parents=True, mode=0o700)

        with self._connect() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS devices (
                    device_id TEXT PRIMARY KEY,
                    label TEXT NOT NULL,
                    public_key_pem TEXT NOT NULL,
                    status TEXT NOT NULL
                        CHECK (status IN ('active', 'revoked')),
                    created_at INTEGER NOT NULL,
                    revoked_at INTEGER
                );

                CREATE TABLE IF NOT EXISTS pairing_codes (
                    code_hash BLOB PRIMARY KEY,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    consumed_at INTEGER
                );

                CREATE TABLE IF NOT EXISTS replay_nonces (
                    device_id TEXT NOT NULL,
                    nonce TEXT NOT NULL,
                    accepted_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    PRIMARY KEY (device_id, nonce),
                    FOREIGN KEY (device_id)
                        REFERENCES devices(device_id)
                        ON DELETE CASCADE
                );

                CREATE INDEX IF NOT EXISTS idx_replay_nonces_expires_at
                    ON replay_nonces(expires_at);

                CREATE INDEX IF NOT EXISTS idx_pairing_codes_expires_at
                    ON pairing_codes(expires_at);

                PRAGMA user_version = 1;
                """
            )

        os.chmod(self.db_path, 0o600)

    def create_pairing_code(
        self,
        ttl_seconds: int,
        *,
        now: int | None = None,
    ) -> str:
        if ttl_seconds <= 0:
            raise ValueError("ttl_seconds must be positive")

        current = self._now(now)
        code = secrets.token_urlsafe(24)
        code_hash = self._hash_pairing_code(code)

        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO pairing_codes (
                    code_hash,
                    created_at,
                    expires_at,
                    consumed_at
                )
                VALUES (?, ?, ?, NULL)
                """,
                (
                    code_hash,
                    current,
                    current + ttl_seconds,
                ),
            )

        return code

    def consume_pairing_code(
        self,
        *,
        code: str,
        device_id: str,
        label: str,
        public_key_pem: str,
        now: int | None = None,
    ) -> DeviceRecord:
        current = self._now(now)
        code_hash = self._hash_pairing_code(code)

        with self._connect() as conn:
            conn.execute("BEGIN IMMEDIATE")

            row = conn.execute(
                """
                SELECT expires_at, consumed_at
                FROM pairing_codes
                WHERE code_hash = ?
                """,
                (code_hash,),
            ).fetchone()

            if (
                row is None
                or row["consumed_at"] is not None
                or current >= row["expires_at"]
            ):
                raise PairingCodeError("invalid pairing code")

            existing = conn.execute(
                """
                SELECT 1
                FROM devices
                WHERE device_id = ?
                """,
                (device_id,),
            ).fetchone()

            if existing is not None:
                raise DeviceAlreadyRegisteredError(
                    "device already registered"
                )

            conn.execute(
                """
                INSERT INTO devices (
                    device_id,
                    label,
                    public_key_pem,
                    status,
                    created_at,
                    revoked_at
                )
                VALUES (?, ?, ?, 'active', ?, NULL)
                """,
                (
                    device_id,
                    label,
                    public_key_pem,
                    current,
                ),
            )

            updated = conn.execute(
                """
                UPDATE pairing_codes
                SET consumed_at = ?
                WHERE code_hash = ?
                  AND consumed_at IS NULL
                """,
                (
                    current,
                    code_hash,
                ),
            )

            if updated.rowcount != 1:
                raise PairingCodeError("invalid pairing code")

        device = self.get_device(device_id)
        assert device is not None
        return device

    def get_device(self, device_id: str) -> DeviceRecord | None:
        with self._connect() as conn:
            row = conn.execute(
                """
                SELECT
                    device_id,
                    label,
                    public_key_pem,
                    status,
                    created_at,
                    revoked_at
                FROM devices
                WHERE device_id = ?
                """,
                (device_id,),
            ).fetchone()

        if row is None:
            return None

        return DeviceRecord(
            device_id=row["device_id"],
            label=row["label"],
            public_key_pem=row["public_key_pem"],
            status=row["status"],
            created_at=row["created_at"],
            revoked_at=row["revoked_at"],
        )

    def list_devices(self) -> list[DeviceRecord]:
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT
                    device_id,
                    label,
                    public_key_pem,
                    status,
                    created_at,
                    revoked_at
                FROM devices
                ORDER BY created_at, device_id
                """
            ).fetchall()

        return [
            DeviceRecord(
                device_id=row["device_id"],
                label=row["label"],
                public_key_pem=row["public_key_pem"],
                status=row["status"],
                created_at=row["created_at"],
                revoked_at=row["revoked_at"],
            )
            for row in rows
        ]

    def revoke_device(
        self,
        device_id: str,
        *,
        now: int | None = None,
    ) -> bool:
        current = self._now(now)

        with self._connect() as conn:
            result = conn.execute(
                """
                UPDATE devices
                SET status = 'revoked',
                    revoked_at = ?
                WHERE device_id = ?
                  AND status = 'active'
                """,
                (
                    current,
                    device_id,
                ),
            )

        return result.rowcount == 1

    def accept_nonce(
        self,
        *,
        device_id: str,
        nonce: str,
        ttl_seconds: int,
        now: int | None = None,
    ) -> None:
        if ttl_seconds <= 0:
            raise ValueError("ttl_seconds must be positive")

        current = self._now(now)

        with self._connect() as conn:
            conn.execute("BEGIN IMMEDIATE")

            conn.execute(
                """
                DELETE FROM replay_nonces
                WHERE expires_at < ?
                """,
                (current,),
            )

            try:
                conn.execute(
                    """
                    INSERT INTO replay_nonces (
                        device_id,
                        nonce,
                        accepted_at,
                        expires_at
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    (
                        device_id,
                        nonce,
                        current,
                        current + ttl_seconds,
                    ),
                )
            except sqlite3.IntegrityError as exc:
                raise ReplayDetectedError(
                    "nonce already accepted"
                ) from exc

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(
            self.db_path,
            timeout=5.0,
        )
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        return conn

    @staticmethod
    def _hash_pairing_code(code: str) -> bytes:
        return hashlib.sha256(code.encode("utf-8")).digest()

    @staticmethod
    def _now(value: int | None) -> int:
        if value is not None:
            return value
        return int(time.time())
