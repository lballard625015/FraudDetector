"""Measure engine alerts against generator ground-truth labels."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def read_events(path: Path) -> dict[str, dict]:
    return {event["transaction_id"]: event for event in (json.loads(line) for line in path.read_text().splitlines() if line.strip())}


def evaluate(ground_truth_path: Path, scored_path: Path, threshold: float) -> dict[str, float | int]:
    ground_truth = read_events(ground_truth_path)
    scored = read_events(scored_path)
    true_positive = false_positive = false_negative = true_negative = 0
    for transaction_id, event in scored.items():
        actual = bool(ground_truth.get(transaction_id, event).get("is_synthetic_fraud", False))
        predicted = float(event.get("rule_score", 0.0)) >= threshold
        if predicted and actual:
            true_positive += 1
        elif predicted:
            false_positive += 1
        elif actual:
            false_negative += 1
        else:
            true_negative += 1
    precision = true_positive / (true_positive + false_positive) if true_positive + false_positive else 0.0
    recall = true_positive / (true_positive + false_negative) if true_positive + false_negative else 0.0
    return {
        "events": len(scored), "threshold": threshold, "true_positive": true_positive,
        "false_positive": false_positive, "false_negative": false_negative, "true_negative": true_negative,
        "precision": round(precision, 4), "recall": round(recall, 4),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ground_truth", type=Path)
    parser.add_argument("scored", type=Path)
    parser.add_argument("--threshold", type=float, default=0.5)
    args = parser.parse_args()
    print(json.dumps(evaluate(args.ground_truth, args.scored, args.threshold), indent=2))


if __name__ == "__main__":
    main()
