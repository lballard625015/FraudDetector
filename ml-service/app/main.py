"""FastAPI service for online anomaly, graph, and combined scoring."""

from __future__ import annotations

import os
from pathlib import Path
from threading import Lock
from typing import Any

from fastapi import FastAPI
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, ConfigDict, Field

from .graph_detector import RollingGraph
from .scoring import ScoringModel, combine_scores

ARTIFACT_PATH = Path(os.getenv("MODEL_ARTIFACT", "models/isolation_forest.joblib"))
RULE_WEIGHT = float(os.getenv("RULE_WEIGHT", "0.4"))
ML_WEIGHT = float(os.getenv("ML_WEIGHT", "0.35"))
GRAPH_WEIGHT = float(os.getenv("GRAPH_WEIGHT", "0.25"))

app = FastAPI(title="Fraud ML and Graph Service", version="0.3.0")
model = ScoringModel(ARTIFACT_PATH)
graph = RollingGraph(window_seconds=int(os.getenv("GRAPH_WINDOW_SECONDS", "3600")))
graph_lock = Lock()


class ScoreRequest(BaseModel):
    model_config = ConfigDict(extra="allow")
    transaction_id: str | None = None
    from_account: str | None = None
    to_account: str | None = None
    device_id: str | None = None
    amount: float = Field(default=0, ge=0)
    rule_score: float = Field(default=0, ge=0, le=1)
    velocity_score: float = Field(default=0, ge=0, le=1)
    structuring_score: float = Field(default=0, ge=0, le=1)
    device_geo_score: float = Field(default=0, ge=0, le=1)


@app.get("/health")
def health() -> dict[str, str | bool]:
    return {"status": "ok", "model_loaded": model.model is not None}


@app.post("/score")
def score(request: ScoreRequest) -> dict[str, Any]:
    event = request.model_dump()
    with graph_lock:
        graph.add(event)
        graph_signal = graph.score()
    ml_score = model.score(event)
    rule_score = float(event.get("rule_score", 0.0))
    combined = combine_scores(rule_score, ml_score, float(graph_signal["graph_score"]), RULE_WEIGHT, ML_WEIGHT, GRAPH_WEIGHT)
    return {
        "transaction_id": request.transaction_id,
        "ml_score": ml_score,
        "graph_score": graph_signal["graph_score"],
        "combined_score": combined,
        "graph": graph_signal,
        "weights": {"rule": RULE_WEIGHT, "ml": ML_WEIGHT, "graph": GRAPH_WEIGHT},
    }


@app.post("/combined-score")
def combined_score(request: ScoreRequest) -> dict[str, Any]:
    return score(request)


@app.get("/metrics", response_class=PlainTextResponse)
def metrics() -> str:
    return "# TYPE ml_service_graph_events gauge\nml_service_graph_events %d\n" % len(graph.events)


@app.post("/reset")
def reset_graph() -> dict[str, str]:
    """Clear in-memory demo state; production deployments should restart the worker instead."""
    with graph_lock:
        graph.events.clear()
        graph.graph.clear()
        graph.device_graph.clear()
    return {"status": "reset"}
