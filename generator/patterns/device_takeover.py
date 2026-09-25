from __future__ import annotations

from datetime import datetime, timedelta
from decimal import Decimal
from random import Random
from uuid import uuid4

from models import AccountProfile, Transaction


def generate(
    accounts: list[AccountProfile],
    start: datetime,
    rng: Random,
    events_per_account: int,
) -> list[Transaction]:
    events: list[Transaction] = []
    for index, account in enumerate(accounts):
        if index % 3 != 0:
            continue
        takeover_device = uuid4()
        takeover_lat = account.geo_lat + Decimal("35.0000")
        takeover_lon = account.geo_lon + Decimal("25.0000")
        for offset in range(events_per_account):
            events.append(
                Transaction(
                    transaction_id=uuid4(),
                    from_account=account.account_id,
                    to_account=accounts[(index + offset + 1) % len(accounts)].account_id,
                    amount=Decimal(rng.randint(1500, 7500)),
                    currency="USD",
                    device_id=takeover_device,
                    geo_lat=takeover_lat,
                    geo_lon=takeover_lon,
                    occurred_at=start + timedelta(minutes=offset * 3),
                    fraud_pattern_type="device_takeover",
                )
            )
    return events
