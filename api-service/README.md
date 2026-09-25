# Java Case Management API

Phase 4 owns alert ingestion, automatic case creation, analyst workflow, audit-chain verification, and real-time STOMP notifications.

## Endpoints

- `POST /api/alerts/ingest` creates an alert and auto-creates a case when `combinedScore >= FRAUD_CASE_AUTO_CREATE_THRESHOLD`.
- `GET /api/alerts?status=open` lists alerts.
- `GET /api/cases` and `GET /api/cases/{id}` retrieve cases.
- `PATCH /api/cases/{id}/status` accepts `investigating`, `escalated`, `dismissed`, or `resolved`.
- `POST /api/cases/{id}/notes` appends an analyst note to the audit chain.
- `GET /api/cases/{id}/timeline` returns ordered audit events.
- `GET /api/cases/{id}/verify` validates the complete hash chain.
- `GET /actuator/health` reports service health.

## WebSocket

Connect a STOMP client to `/ws` and subscribe to:

- `/topic/alerts` for newly ingested alerts
- `/topic/cases` for case creation, notes, and status changes

## Visible validation

```powershell
Push-Location api-service
mvn test
Pop-Location
docker compose up --build postgres api-service
```

The audit service canonicalizes JSON recursively, hashes with SHA-256, and normalizes timestamps to PostgreSQL microsecond precision before writing `case_events`.

Alert ingestion applies an open-alert cooldown: a repeated alert for the same transaction and category returns the existing open alert instead of creating another case. After the alert is resolved, a later occurrence may create a new alert.
