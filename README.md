# Fraud Detection Platform

A local fraud-operations platform that turns synthetic transaction activity into scored signals, analyst alerts, investigation cases, and observable risk history.

The project utilizes Spring Boot, PostgreSQL, C++, Python, React, Kafka, Prometheus, and Grafana in one runnable demonstration.

## What You Can Explore

- Synthetic transactions with structuring, device takeover, mule cycle, and coordinated burst patterns.
- C++ rule detection for velocity, structuring, and device/geo changes.
- Isolation Forest anomaly scoring.
- Graph evidence for connected accounts, cycles, and clusters.
- Weighted composite risk scoring.
- Alert deduplication and automatic case creation.
- Analyst registration and login.
- Case investigation, escalation, resolution, dismissal, notes, and audit-chain verification.
- Searchable account-specific risk history.
- A page-aware Investigation Copilot on every dashboard page.
- Confirmation-based Copilot proposals for Investigate, Escalate, Resolve, and Dismiss actions.
- Prometheus metrics and a provisioned Grafana dashboard.

Compose services bind to localhost by default.

## Run the Demo

### Prerequisites

Install:

- Docker Desktop with Docker Compose v2.
- Git.
- At least 6 GB of memory available to Docker for the full stack.

### Start

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose --profile full up --build
```

The first build downloads the required images and dependencies. Future starts are faster.

Open the application at:

[http://localhost:3000](http://localhost:3000)

Register an analyst account on the login screen to begin.

## Suggested Demo Flow

1. Open **Command center** to see detected signals, open alerts, and active cases.
2. Open **Alert queue** and filter by Open, Investigating, Escalated, Resolved, or Dismissed.
3. Select an alert or case to open its investigation workspace.
4. Use Investigate, Escalate, Resolve, or Dismiss and observe the status and counters update.
5. Add an analyst note and verify the audit chain.
6. Ask the **Investigation Copilot** what is happening on the current page or why a case deserves review.
7. Select a case before requesting a status action. Review the Copilot proposal and explicitly confirm it before the case changes.
8. Open **Account risk**, search by account name or account number, and select a time range.
9. Open Grafana to inspect service and detection metrics.

The Copilot is read-only until confirmation. It can summarize page and case evidence, propose a workflow action, and show the reason for that proposal. It cannot change a case state without an analyst confirmation click.

## Service URLs

| Service | URL |
| --- | --- |
| Analyst console | [http://localhost:3000](http://localhost:3000) |
| API health | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| ML API documentation | [http://localhost:8000/docs](http://localhost:8000/docs) |
| Copilot health | [http://localhost:8010/health](http://localhost:8010/health) |
| Prometheus | [http://localhost:9090](http://localhost:9090) |
| Grafana | [http://localhost:3001](http://localhost:3001) |

## How It Fits Together

```text
Synthetic transactions
        |
        v
Kafka and PostgreSQL
        |
        +--> C++ rule engine
        +--> ML anomaly service
        +--> Graph detector
                    |
                    v
             Composite risk score
                    |
                    v
              Spring Boot API
                    |
                    v
             React analyst console
                    |
                    +--> Investigation Copilot
                    +--> Prometheus / Grafana
```

| Directory | Purpose |
| --- | --- |
| `frontend/` | React analyst console and account-risk UI. |
| `api-service/` | Spring Boot API, authentication, alerts, cases, audit events, and account risk. |
| `database/` | PostgreSQL schema and repeatable demo seed scripts. |
| `detection-engine/` | C++ bounded ingestion, feature extraction, and rule scoring. |
| `ml-service/` | FastAPI anomaly, graph, and combined scoring service. |
| `agent-service/` | Page-aware Investigation Copilot with authenticated read-only tools and confirmation proposals. |
| `generator/` | Synthetic transaction generation. |
| `observability/` | Prometheus, Grafana, and evaluation tooling. |

## Demo Data

The database initialization scripts create accounts, transactions, signals, alerts, cases, and risk-history points. Demo history is seeded across every account so account-risk charts have meaningful data. Seed data is repeatable and is applied automatically when PostgreSQL starts with a new volume.

To completely reset local demo data:

```powershell
docker compose down -v
docker compose --profile full up --build
```

The `-v` flag deletes the local PostgreSQL and Grafana volumes. Use it only when you want a clean demonstration database.

## Development Checks

Validate the Compose configuration and build the main services:

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

Run the stack in the background:

```powershell
docker compose --profile full up -d
```

Stop containers while preserving database data:

```powershell
docker compose down
```

## Configuration

Copy `.env.example` to `.env` before starting the stack. The `.env` file is ignored by Git.

The local configuration includes:

- PostgreSQL database, user, and password values.
- Localhost-only published ports.
- The default fraud case threshold.

Do not commit passwords, API keys, tokens, or other secrets.

## Security Boundary

This project is built locally. Before running it on a public server, add HTTPS, managed secrets, invitation-only registration, rate limiting, protected Kafka/PostgreSQL/metrics endpoints, secure HTTP-only sessions, backups, and production network controls.

## Component Documentation

- [Generator guide](generator/README.md)
- [Detection engine guide](detection-engine/README.md)
- [ML and graph service guide](ml-service/README.md)
- [Observability guide](observability/README.md)
