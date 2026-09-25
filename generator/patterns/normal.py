from __future__ import annotations

from datetime import datetime, timedelta
from decimal import Decimal
from random import Random
from uuid import uuid4

from models import AccountProfile, Transaction


def generate(
    accounts: list[AccountProfile],
    start: datetime,
    duration_seconds: int,
    rate_per_second: float,
    rng: Random,
) -> list[Transaction]:
    events: list[Transaction] = []
    event_count = max(1, int(duration_seconds * rate_per_second))
    for index in range(event_count):
        sender = accounts[rng.randrange(len(accounts))]
        receiver = accounts[rng.randrange(len(accounts))]
        while receiver.account_id == sender.account_id and len(accounts) > 1:
            receiver = accounts[rng.randrange(len(accounts))]
        # Log-normal amounts are less uniform and closer to typical payment traffic.
        amount = Decimal(str(round(min(rng.lognormvariate(3.6, 0.8), 5000), 2)))
        occurred_at = start + timedelta(seconds=index / max(rate_per_second, 0.001))
        events.append(
            Transaction(
                transaction_id=uuid4(),
                from_account=sender.account_id,
                to_account=receiver.account_id,
                amount=amount,
                currency="USD",
                device_id=sender.device_id,
                geo_lat=sender.geo_lat,
                geo_lon=sender.geo_lon,
                occurred_at=occurred_at,
            )
        )
    return events
