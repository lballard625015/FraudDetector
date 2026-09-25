# Observability and Evaluation

## Run the monitoring stack

```powershell
docker compose --profile full up -d postgres api-service kafka detection-engine ml-service prometheus grafana
```

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`
- Engine metrics: `http://localhost:9101/metrics`
- ML metrics: `http://localhost:8000/metrics`

Grafana provisions the `Prometheus` datasource and the `Fraud Operations Pipeline` dashboard automatically.

## Ground-truth evaluation

The evaluator joins generator JSONL events to engine scored events by `transaction_id` and treats `rule_score >= 0.5` as an alert:

```powershell
python observability/evaluate.py training-events.jsonl scored-events.jsonl --threshold 0.5
```

The output records event count, confusion-matrix counts, precision, and recall. Use a new generated run for every benchmark; do not compare metrics across different thresholds without recording the threshold.

## End-to-end analyst demo

With the API and ML service running, generate a labeled run, replay it through the C++ engine, then bridge high-confidence results into Java. The frontend receives those alerts over STOMP and creates visible cases when the Java threshold is exceeded:

```powershell
python generator/generate.py --no-db --stream jsonl --stream-file training-events.jsonl --account-count 12 --duration-seconds 20 --rate-per-second 2 --patterns structuring,mule_cycle,device_takeover,coordinated_burst --pattern-count 3 --seed 42
docker run --rm -v "${PWD}:/workspace" fraud-detection-engine-kafka --input /workspace/training-events.jsonl --output /workspace/scored-events.jsonl --rules /app/config/rules.json --metrics-port 0
python observability/demo.py scored-events.jsonl --threshold 0.7
```

Keep `http://localhost:3000` open while the bridge runs. Each run resets the ML rolling graph and resolves active cases from the previous demo run first, so the dashboard count is reproducible instead of accumulating in the persistent database volume. Use `--keep-existing` to opt out. The live signal feed updates through `/topic/alerts`; high-scoring events also appear in the case workspace.

## Recorded benchmark

Using seed `42`, 12 accounts, 20 seconds at 2 events/second, and all four fraud patterns, the run produced 88 events with 48 ground-truth fraud labels. At `rule_score >= 0.5`, the engine produced:

| Metric | Result |
| --- | ---: |
| True positives | 37 |
| False positives | 15 |
| False negatives | 11 |
| True negatives | 25 |
| Precision | 0.7115 |
| Recall | 0.7708 |

These are baseline rule-engine measurements, not claims about the later ML/graph combined score.
