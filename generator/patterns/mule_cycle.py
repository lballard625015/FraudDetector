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
    cycles: int,
) -> list[Transaction]:
    events: list[Transaction] = []
    if len(accounts) < 3:
        return events

    for cycle_index in range(cycles):
        members = [accounts[(cycle_index * 3 + offset) % len(accounts)] for offset in range(3)]
        amount = Decimal(rng.randint(2000, 9000))
        for offset, sender in enumerate(members):
            receiver = members[(offset + 1) % len(members)]
            events.append(
                Transaction(
                    transaction_id=uuid4(),
                    from_account=sender.account_id,
                    to_account=receiver.account_id,
                    amount=amount - Decimal(offset * rng.randint(10, 40)),
                    currency="USD",
                    device_id=sender.device_id,
                    geo_lat=sender.geo_lat,
                    geo_lon=sender.geo_lon,
                    occurred_at=start + timedelta(minutes=offset * 5),
                    fraud_pattern_type="mule_cycle",
                )
            )
    return events
