# Synthetic Generator

The generator creates normal traffic plus independently enabled fraud patterns. It writes each event to PostgreSQL before publishing it to the selected stream sink.

## Local dry run

From the repository root:

```powershell
python generator/generate.py --no-db --stream jsonl --stream-file transactions.jsonl --duration-seconds 60 --account-count 25
```

Check the normal-only toggle:

```powershell
python generator/generate.py --no-db --stream stdout --patterns none --account-count 10 --duration-seconds 10
```

## PostgreSQL and stdout

With the Postgres container running:

```powershell
docker compose run --rm generator python generate.py --stream stdout --patterns structuring,mule_cycle
```

The command above requires PostgreSQL to be running first:

```powershell
docker compose up -d postgres
```

Available patterns are `structuring`, `device_takeover`, `mule_cycle`, and `coordinated_burst`. Use `--patterns none` for normal traffic only.

## Kafka

The full Compose profile configures the generator for Kafka:

```powershell
docker compose --profile full up -d --build postgres kafka
docker compose exec -T kafka kafka-topics.sh --create --if-not-exists --topic transactions --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
docker compose run --rm generator python generate.py --account-count 4 --duration-seconds 1 --rate-per-second 1 --patterns structuring --stream kafka --kafka-bootstrap kafka:9092 --kafka-topic transactions
docker compose exec -T kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic transactions --from-beginning --timeout-ms 10000
```

The consumer should print the generated JSON events and report the number processed. Kafka uses `bitnamilegacy/kafka:3.7.0` in Compose because the older Bitnami image tags are no longer available in the main registry.

Stop the Phase 1 test services while preserving PostgreSQL data:

```powershell
docker compose --profile full down
```
