"""Feature contract shared by training and online inference."""

from __future__ import annotations

from typing import Any

import numpy as np

FEATURE_NAMES = (
    "amount",
    "rule_score",
    "velocity_score",
    "structuring_score",
    "device_geo_score",
    "rolling_mean",
    "rolling_stddev",
    "velocity_per_minute",
    "seconds_since_device_change",
    "device_geo_distance_km",
    "transaction_count",
)


def _number(value: Any, default: float = 0.0) -> float:
    if value is None:
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def vectorize_event(event: dict[str, Any]) -> np.ndarray:
    nested = event.get("features") or {}
    values = [
        _number(event.get("amount")),
        _number(event.get("rule_score")),
        _number(event.get("velocity_score")),
        _number(event.get("structuring_score")),
        _number(event.get("device_geo_score")),
        _number(nested.get("rolling_mean")),
        _number(nested.get("rolling_stddev")),
        _number(nested.get("velocity_per_minute")),
        _number(nested.get("seconds_since_device_change")),
        _number(nested.get("device_geo_distance_km")),
        _number(nested.get("transaction_count"), 1.0),
    ]
    return np.asarray(values, dtype=np.float64)


def export_rows(events: list[dict[str, Any]]) -> tuple[list[str], np.ndarray]:
    if not events:
        raise ValueError("No events were supplied for feature export")
    return list(FEATURE_NAMES), np.vstack([vectorize_event(event) for event in events])
