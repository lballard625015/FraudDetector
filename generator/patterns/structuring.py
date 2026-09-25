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
    groups: int,
    threshold: Decimal = Decimal("10000"),
) -> list[Transaction]:
    events: list[Transaction] = []
    if not accounts:
        return events

    for group_index in range(groups):
        account = accounts[group_index % len(accounts)]
        count = rng.randint(3, 5)
        for offset in range(count):
            amount = threshold - Decimal(rng.randint(100, 1000))
            events.append(
                Transaction(
                    transaction_id=uuid4(),
                    from_account=account.account_id,
                    to_account=accounts[(group_index + offset + 1) % len(accounts)].account_id,
                    amount=amount,
                    currency="USD",
                    device_id=account.device_id,
                    geo_lat=account.geo_lat,
                    geo_lon=account.geo_lon,
                    occurred_at=start + timedelta(minutes=offset * 4),
                    fraud_pattern_type="structuring",
                )
            )
    return events
