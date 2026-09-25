"""Train and serialize the IsolationForest artifact."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest

from .features import FEATURE_NAMES


def train(input_path: Path, artifact_path: Path, contamination: float = 0.05) -> int:
    with input_path.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    if len(rows) < 2:
        raise ValueError("At least two feature rows are required for training")
    matrix = np.asarray([[float(row[name]) for name in FEATURE_NAMES] for row in rows], dtype=np.float64)
    normal_rows = [row for row in rows if row.get("is_synthetic_fraud", "0") not in {"1", "true", "True"}]
    training_matrix = np.asarray(
        [[float(row[name]) for name in FEATURE_NAMES] for row in normal_rows], dtype=np.float64
    ) if len(normal_rows) >= 2 else matrix
    model = IsolationForest(
        n_estimators=150,
        contamination=min(max(contamination, 0.001), 0.5),
        random_state=42,
    )
    model.fit(training_matrix)
    artifact_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": model, "feature_names": list(FEATURE_NAMES)}, artifact_path)
    return len(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--artifact", type=Path, default=Path("models/isolation_forest.joblib"))
    args = parser.parse_args()
    print(f"Trained artifact on {train(args.input, args.artifact)} rows: {args.artifact}")


if __name__ == "__main__":
    main()
