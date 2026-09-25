# C++ Detection Engine

The engine consumes Kafka transactions by default in Compose, maintains fixed-size per-account windows, evaluates JSON-configured rules, and publishes scored events to Kafka. JSONL replay and TCP are lightweight local-test fallbacks.

## Build and test

```powershell
cmake -S detection-engine -B detection-engine/build -DBUILD_TESTING=ON
cmake --build detection-engine/build --config Release
ctest --test-dir detection-engine/build --output-on-failure -C Release
```

## Replay generator output

```powershell
python generator/generate.py --no-db --stream jsonl --stream-file transactions.jsonl --duration-seconds 10 --account-count 10 --patterns structuring
./detection-engine/build/detection-engine --input transactions.jsonl --output scored-events.jsonl --rules detection-engine/config/rules.json
```

On Windows, run `detection-engine.exe` from the selected CMake configuration directory.

The deployment build also installs `librdkafka`, compiles the native Kafka consumer/producer, and runs the C++ unit tests:

```powershell
docker build -t fraud-detection-engine-kafka detection-engine
```

## TCP input

The socket mode accepts one JSON transaction per line:

```powershell
./detection-engine/build/detection-engine --input socket --socket-port 9100 --output scored-events.jsonl
```

The Prometheus-compatible metrics endpoint is available at `http://localhost:9101/metrics`. Rule changes can be applied with `--rules` on the next process start; hot reload is planned for a later phase.

## Kafka input and output

Create the input and output topics:

```powershell
docker compose --profile full up -d kafka
docker compose exec -T kafka kafka-topics.sh --create --if-not-exists --topic transactions --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
docker compose exec -T kafka kafka-topics.sh --create --if-not-exists --topic scored-events --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

Run a finite Kafka replay. Omit `--max-messages` for a long-running consumer:

```powershell
docker run --rm --network frauddetection_default fraud-detection-engine-kafka `
	--input kafka --output kafka --kafka-brokers kafka:9092 `
	--kafka-input-topic transactions --kafka-output-topic scored-events `
	--kafka-group detection-engine --max-messages 4 --idle-timeout-ms 1000
```

Fresh consumer groups use `auto.offset.reset=earliest`, so test events already in the topic are replayed.
