# Fraud Detection Platform

Phase 0 provides the PostgreSQL foundation and a Spring Boot CRUD API. Phase 1 adds the labeled synthetic transaction generator.

## Phase 0: Foundation

Run the database and API from the repository root:

```powershell
docker compose up --build postgres api-service
```

The API is available at `http://localhost:8080`.

- `GET/POST /api/accounts`
- `GET/PUT/DELETE /api/accounts/{id}`
- `GET/POST /api/transactions`
- `GET/PUT/DELETE /api/transactions/{id}`

Validate the Compose file and Java service:

```powershell
docker compose config
Push-Location api-service
mvn test
Pop-Location
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
```

Stop Phase 0 services without deleting the database volume:

```powershell
docker compose down
```

Use `docker compose down -v` only when intentionally deleting local database data.

## Phase 1: Synthetic Generator

The generator supports normal traffic and independent `structuring`, `device_takeover`, `mule_cycle`, and `coordinated_burst` patterns. See [generator/README.md](generator/README.md) for dry-run, PostgreSQL, and Kafka commands.

## Phase 2: C++ Detection Engine

The engine provides bounded multithreaded ingestion, fixed-size per-account ring buffers, feature extraction, JSON-configured velocity/structuring/device-geo rules, scored JSON output, TCP input, and Prometheus metrics. See [detection-engine/README.md](detection-engine/README.md).

Build and test it with:

```powershell
docker build -t fraud-detection-engine-test detection-engine
```

The Docker build runs the C++ unit tests and compiles the native `librdkafka` consumer/producer. JSONL replay and TCP remain available as lightweight local-test adapters.

The frontend and observability services remain placeholders for later phases.

## Phase 4: Java Case Management

Phase 4 provides alert ingestion, automatic case creation at the configured threshold, persisted case status transitions, analyst notes, hash-chain audit verification, and STOMP WebSocket topics `/topic/alerts` and `/topic/cases`. See the API endpoints under `/api/alerts` and `/api/cases`.

## Phase 3: ML and Graph Service

Phase 3 provides feature export, offline IsolationForest training, FastAPI `/score` and `/combined-score` endpoints, rolling NetworkX cycle/cluster detection, and weighted rule/ML/graph scoring. See [ml-service/README.md](ml-service/README.md) for build, training, and API commands.

## Phase 6: Observability and Demo Evaluation

Phase 6 adds Prometheus scraping for Java, C++, and Python metrics plus a provisioned Grafana operations dashboard. Start the full monitoring stack with:

```powershell
docker compose --profile full up -d postgres api-service kafka detection-engine ml-service prometheus grafana
```

Open Grafana at `http://localhost:3001` and Prometheus at `http://localhost:9090`. See [observability/README.md](observability/README.md) for the live demo and precision/recall evaluation commands.
