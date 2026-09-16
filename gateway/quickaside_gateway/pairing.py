from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class PairRequest(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        populate_by_name=True,
    )

    pairing_code: str = Field(
        alias="pairingCode",
        min_length=16,
        max_length=128,
    )
    device_id: str = Field(
        alias="deviceId",
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9._-]+$",
    )
    label: str = Field(
        min_length=1,
        max_length=100,
    )
    public_key_pem: str = Field(
        alias="publicKeyPem",
        min_length=1,
        max_length=4096,
    )


class PairResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    device_id: str = Field(alias="deviceId")
    status: str
