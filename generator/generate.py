"""Generate labeled transactions for local evaluation and streaming demos."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
from decimal import Decimal
from random import Random
from typing import Callable

from models import AccountProfile, Transaction
from patterns import coordinated_burst, device_takeover, mule_cycle, normal, structuring
from sinks import (
    JsonlStream,
    KafkaStream,
    PostgresSink,
    StdoutStream,
    TransactionStream,
    database_url_from_environment,
)

PatternGenerator = Callable[..., list[Transaction]]


def create_accounts(account_count: int) -> list[AccountProfile]:
    return [
        AccountProfile(
            owner_name=f"Synthetic User {index + 1}",
            fingerprint=f"synthetic-device-{index + 1}",
        )
        for index in range(account_count)
    ]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--account-count", type=int, default=25)
    parser.add_argument("--duration-seconds", type=int, default=60)
    parser.add_argument("--rate-per-second", type=float, default=1.0)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--patterns",
        default="structuring,device_takeover,mule_cycle,coordinated_burst",
        help="Comma-separated fraud patterns to enable; use 'none' to disable all.",
    )
    parser.add_argument("--pattern-count", type=int, default=2)
    parser.add_argument("--stream", choices=("stdout", "jsonl", "kafka"), default="stdout")
    parser.add_argument("--stream-file", default="transactions.jsonl")
    parser.add_argument("--kafka-bootstrap", default="localhost:9092")
    parser.add_argument("--kafka-topic", default="transactions")
    parser.add_argument("--database-url", default=database_url_from_environment())
    parser.add_argument("--no-db", action="store_true", help="Skip PostgreSQL for a local dry run.")
    return parser.parse_args()


def build_transactions(args: argparse.Namespace, accounts: list[AccountProfile]) -> list[Transaction]:
    rng = Random(args.seed)
    start = datetime.now(timezone.utc)
    events = normal.generate(accounts, start, args.duration_seconds, args.rate_per_second, rng)
    enabled = {item.strip() for item in args.patterns.split(",") if item.strip() and item.strip() != "none"}
    pattern_calls: dict[str, PatternGenerator] = {
        "structuring": lambda: structuring.generate(accounts, start, rng, args.pattern_count),
        "device_takeover": lambda: device_takeover.generate(accounts, start, rng, max(1, args.pattern_count)),
        "mule_cycle": lambda: mule_cycle.generate(accounts, start, rng, args.pattern_count),
        "coordinated_burst": lambda: coordinated_burst.generate(accounts, start, rng, args.pattern_count),
    }
    for name in enabled:
        if name not in pattern_calls:
            raise ValueError(f"Unknown fraud pattern: {name}")
        events.extend(pattern_calls[name]())
    return sorted(events, key=lambda transaction: transaction.occurred_at)


def create_stream(args: argparse.Namespace) -> TransactionStream:
    if args.stream == "jsonl":
        return JsonlStream(args.stream_file)
    if args.stream == "kafka":
        return KafkaStream(args.kafka_bootstrap, args.kafka_topic)
    return StdoutStream()


def main() -> None:
    args = parse_args()
    if args.account_count < 1 or args.duration_seconds < 1 or args.rate_per_second <= 0:
        raise ValueError("account count, duration, and rate must be positive")

    accounts = create_accounts(args.account_count)
    transactions = build_transactions(args, accounts)
    database = None if args.no_db else PostgresSink(args.database_url)
    stream = create_stream(args)
    try:
        if database is not None:
            database.seed_accounts(accounts)
        for transaction in transactions:
            if database is not None:
                database.persist(transaction)
            stream.publish(transaction)
    finally:
        stream.close()
        if database is not None:
            database.close()
    print(
        f"Generated {len(transactions)} transactions "
        f"({sum(transaction.is_synthetic_fraud for transaction in transactions)} labeled fraud events)."
    )


if __name__ == "__main__":
    main()
