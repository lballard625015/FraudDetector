from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Iterable, Protocol

from models import AccountProfile, Transaction


class TransactionStream(Protocol):
    def publish(self, transaction: Transaction) -> None:
        ...

    def close(self) -> None:
        ...


class StdoutStream:
    def publish(self, transaction: Transaction) -> None:
        print(json.dumps(transaction.as_dict(), separators=(",", ":")))

    def close(self) -> None:
        pass


class JsonlStream:
    def __init__(self, path: str | Path) -> None:
        self._file = Path(path).open("a", encoding="utf-8")

    def publish(self, transaction: Transaction) -> None:
        self._file.write(json.dumps(transaction.as_dict(), separators=(",", ":")) + "\n")
        self._file.flush()

    def close(self) -> None:
        self._file.close()


class KafkaStream:
    def __init__(self, bootstrap_servers: str, topic: str) -> None:
        from kafka import KafkaProducer

        self._producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers.split(","),
            value_serializer=lambda value: json.dumps(value).encode("utf-8"),
        )
        self._topic = topic

    def publish(self, transaction: Transaction) -> None:
        self._producer.send(self._topic, transaction.as_dict())

    def close(self) -> None:
        self._producer.flush()
        self._producer.close()


class PostgresSink:
    def __init__(self, database_url: str) -> None:
        import psycopg

        self._connection = psycopg.connect(database_url)
        self._connection.autocommit = False

    def seed_accounts(self, accounts: Iterable[AccountProfile]) -> None:
        with self._connection.cursor() as cursor:
            for account in accounts:
                cursor.execute(
                    """
                    INSERT INTO accounts (account_id, owner_name)
                    VALUES (%s, %s)
                    ON CONFLICT (account_id) DO NOTHING
                    """,
                    (account.account_id, account.owner_name),
                )
                cursor.execute(
                    """
                    INSERT INTO devices (device_id, fingerprint)
                    VALUES (%s, %s)
                    ON CONFLICT (device_id) DO NOTHING
                    """,
                    (account.device_id, account.fingerprint),
                )
                cursor.execute(
                    """
                    INSERT INTO account_devices (account_id, device_id)
                    VALUES (%s, %s)
                    ON CONFLICT (account_id, device_id) DO NOTHING
                    """,
                    (account.account_id, account.device_id),
                )
        self._connection.commit()

    def persist(self, transaction: Transaction) -> None:
        with self._connection.cursor() as cursor:
            # Fraud patterns can introduce a new device, so satisfy both foreign keys first.
            cursor.execute(
                """
                INSERT INTO devices (device_id, fingerprint)
                VALUES (%s, %s)
                ON CONFLICT (device_id) DO NOTHING
                """,
                (transaction.device_id, f"synthetic-device-{transaction.device_id}"),
            )
            cursor.execute(
                """
                INSERT INTO account_devices (account_id, device_id)
                VALUES (%s, %s)
                ON CONFLICT (account_id, device_id) DO NOTHING
                """,
                (transaction.from_account, transaction.device_id),
            )
            cursor.execute(
                """
                INSERT INTO transactions (
                    transaction_id, from_account, to_account, amount, currency,
                    device_id, geo_lat, geo_lon, occurred_at,
                    is_synthetic_fraud, fraud_pattern_type
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (transaction_id) DO NOTHING
                """,
                (
                    transaction.transaction_id,
                    transaction.from_account,
                    transaction.to_account,
                    transaction.amount,
                    transaction.currency,
                    transaction.device_id,
                    transaction.geo_lat,
                    transaction.geo_lon,
                    transaction.occurred_at,
                    transaction.is_synthetic_fraud,
                    transaction.fraud_pattern_type,
                ),
            )
        self._connection.commit()

    def close(self) -> None:
        self._connection.close()


def database_url_from_environment() -> str:
    return os.getenv(
        "DATABASE_URL",
        "postgresql://fraud:fraud@localhost:5432/fraud_detection",
    )
