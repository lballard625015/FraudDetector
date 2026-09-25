"""Online IsolationForest and weighted score combination."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import joblib
import numpy as np

from .features import vectorize_event


class ScoringModel:
    def __init__(self, artifact_path: Path) -> None:
        self.model = None
        if artifact_path.exists():
            artifact = joblib.load(artifact_path)
            self.model = artifact["model"]

    def score(self, event: dict[str, Any]) -> float:
        if self.model is None:
            return 0.0
        vector = vectorize_event(event).reshape(1, -1)
        # IsolationForest decision_function is higher for normal samples; invert and clamp it.
        normality = float(self.model.decision_function(vector)[0])
        return float(np.clip(0.5 - normality, 0.0, 1.0))


def combine_scores(rule_score: float, ml_score: float, graph_score: float,
                   rule_weight: float = 0.4, ml_weight: float = 0.35,
                   graph_weight: float = 0.25) -> float:
    total = rule_weight + ml_weight + graph_weight
    if total <= 0:
        raise ValueError("Score weights must sum to a positive value")
    return (rule_weight * rule_score + ml_weight * ml_score + graph_weight * graph_score) / total
