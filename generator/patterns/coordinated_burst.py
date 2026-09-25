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
    bursts: int,
) -> list[Transaction]:
    events: list[Transaction] = []
    if len(accounts) < 3:
        return events

    for burst_index in range(bursts):
        shared_device = uuid4()
        selected = accounts[burst_index % len(accounts) :]
        selected = selected[: min(5, len(selected))]
        for offset, account in enumerate(selected):
            events.append(
                Transaction(
                    transaction_id=uuid4(),
                    from_account=account.account_id,
                    to_account=accounts[(burst_index + offset + 1) % len(accounts)].account_id,
                    amount=Decimal(rng.randint(300, 1800)),
                    currency="USD",
                    device_id=shared_device,
                    geo_lat=account.geo_lat,
                    geo_lon=account.geo_lon,
                    occurred_at=start + timedelta(seconds=offset * 20),
                    fraud_pattern_type="coordinated_burst",
                )
            )
    return events
