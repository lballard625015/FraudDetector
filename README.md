# Fraud Detection Platform

A local, end-to-end fraud operations platform built with Spring Boot, PostgreSQL, C++, Python, React, Kafka, Prometheus, and Grafana.

The project demonstrates a complete analyst workflow:

- Synthetic transaction generation with configurable fraud patterns.
- Bounded C++ rule detection for velocity, structuring, and device/geo changes.
- ML anomaly scoring with Isolation Forest.
- Graph-based cycle and cluster evidence.
- Weighted composite risk scoring.
- Alert deduplication and automatic case creation.
- Analyst login, registration, case transitions, notes, and audit-chain verification.
- Searchable account-specific risk history.
- Prometheus metrics and a Grafana operations dashboard.

This repository is designed for local demos and portfolio review. Compose ports bind to `127.0.0.1` by default so services are not exposed to the network.

## Quick Start

### Requirements

- Docker Desktop with Compose v2.
- Git.
- At least 6 GB of available Docker memory for the full stack.

### Start the demo

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose --profile full up --build
```

Open the analyst console at [http://localhost:3000](http://localhost:3000). Register an analyst account, then use the dashboard to review alerts, investigate cases, inspect risk history, and verify audit chains.

The first build downloads several images and dependencies. Later starts are faster.

### Demo URLs

| Service | URL |
| --- | --- |
| Analyst console | [http://localhost:3000](http://localhost:3000) |
| API health | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| ML API docs | [http://localhost:8000/docs](http://localhost:8000/docs) |
| Prometheus | [http://localhost:9090](http://localhost:9090) |
| Grafana | [http://localhost:3001](http://localhost:3001) |

All ports are localhost-only by default. Do not remove that restriction without adding TLS, authentication, rate limiting, and network access controls.

## Demo Walkthrough

1. Register an analyst account on the login screen.
2. Open **Command center** to see signals detected, open alerts, and active cases.
3. Open **Alert queue** and filter by Open, Investigating, Escalated, Resolved, or Dismissed.
4. Select a case and use Investigate, Escalate, Resolve, or Dismiss.
5. Review the workflow trail, classification explanation, risk calculation, notes, and audit verification.
6. Open **Account risk**, search by account name or account number, and select a time range.
7. Open Grafana to inspect service and detection metrics.

The database seed scripts create repeatable demo accounts, transactions, signals, alerts, and account-risk history. They run automatically on a new database volume. To intentionally recreate all local data:

```powershell
docker compose down -v
docker compose --profile full up --build
```

## Architecture

| Component | Responsibility |
| --- | --- |
| `frontend/` | React analyst console with authenticated workflow UI. |
| `api-service/` | Spring Boot API, case management, analyst auth, audit chain, and account risk endpoint. |
| `database/` | PostgreSQL schema and idempotent demo seed migrations. |
| `detection-engine/` | C++ bounded ingestion, feature extraction, and rule scoring. |
| `ml-service/` | FastAPI anomaly, graph, and combined scoring endpoints. |
| `generator/` | Synthetic normal and fraud-pattern transaction generation. |
| `observability/` | Prometheus configuration, Grafana dashboard, and evaluation tools. |

## Development Checks

Validate Compose and build the application services:

```powershell
docker compose config
Push-Location api-service
mvn test
Pop-Location
Push-Location frontend
npm run build
Pop-Location
docker build -t fraud-detection-engine-test detection-engine
```

Run the local stack in the background:

```powershell
docker compose --profile full up -d
```

Stop containers while preserving the local database:

```powershell
docker compose down
```

## Security Notes

This is a portfolio/demo deployment, not a public production deployment. The default Compose profile binds services to localhost, and `.env` is ignored by Git. Before deploying to a server:

- Use a managed secret store and rotate database credentials.
- Put the frontend and API behind HTTPS.
- Disable open registration or make it invitation-only.
- Protect Kafka, PostgreSQL, ML, metrics, and WebSocket endpoints.
- Add rate limiting, audit logging, backups, and monitoring.
- Store sessions in secure HTTP-only cookies or use a managed identity provider.

## Project Notes

Detailed component documentation lives in:

- [generator/README.md](generator/README.md)
- [detection-engine/README.md](detection-engine/README.md)
- [ml-service/README.md](ml-service/README.md)
- [observability/README.md](observability/README.md)
