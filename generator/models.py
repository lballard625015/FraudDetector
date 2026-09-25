from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal
from typing import Any
from uuid import UUID, uuid4


@dataclass
class AccountProfile:
    account_id: UUID = field(default_factory=uuid4)
    owner_name: str = "Synthetic User"
    device_id: UUID = field(default_factory=uuid4)
    fingerprint: str = ""
    geo_lat: Decimal = Decimal("40.7128")
    geo_lon: Decimal = Decimal("-74.0060")


@dataclass
class Transaction:
    transaction_id: UUID
    from_account: UUID
    to_account: UUID
    amount: Decimal
    currency: str
    device_id: UUID
    geo_lat: Decimal
    geo_lon: Decimal
    occurred_at: datetime
    fraud_pattern_type: str | None = None

    @property
    def is_synthetic_fraud(self) -> bool:
        return self.fraud_pattern_type is not None

    def as_dict(self) -> dict[str, Any]:
        return {
            "transaction_id": str(self.transaction_id),
            "from_account": str(self.from_account),
            "to_account": str(self.to_account),
            "amount": str(self.amount),
            "currency": self.currency,
            "device_id": str(self.device_id),
            "geo_lat": str(self.geo_lat),
            "geo_lon": str(self.geo_lon),
            "occurred_at": self.occurred_at.isoformat(),
            "is_synthetic_fraud": self.is_synthetic_fraud,
            "fraud_pattern_type": self.fraud_pattern_type,
        }
