# ML and Graph Service

Phase 3 provides feature export, offline IsolationForest training, FastAPI inference, rolling NetworkX graph analysis, and weighted combined scoring.

## Build

```powershell
docker build -t fraud-detection-ml-service ml-service
```

## Export and train

Generate engine-compatible JSONL, flatten it into a training CSV, then serialize the model:

```powershell
python generator/generate.py --no-db --stream jsonl --stream-file ml-service/training-events.jsonl --account-count 12 --duration-seconds 20 --rate-per-second 2 --patterns structuring,mule_cycle,coordinated_burst

docker run --rm -v "${PWD}\ml-service:/workspace" -w /app fraud-detection-ml-service python -m app.feature_export /workspace/training-events.jsonl /workspace/training-features.csv
docker run --rm -v "${PWD}\ml-service:/workspace" -w /app fraud-detection-ml-service python -m app.train /workspace/training-features.csv --artifact /workspace/models/isolation_forest.joblib
```

Training uses normal rows when available and stores the feature contract with the `joblib` artifact.

## Run and score

```powershell
docker run --rm -p 8000:8000 -v "${PWD}\ml-service\models:/app/models" fraud-detection-ml-service
```

Endpoints:

- `GET /health`
- `POST /score`
- `POST /combined-score`
- `GET /metrics`

`/score` returns `ml_score`, `graph_score`, `combined_score`, graph cycle/cluster details, and configured weights. The graph keeps a one-hour rolling window by default; configure it with `GRAPH_WINDOW_SECONDS`.
