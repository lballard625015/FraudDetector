"""Bridge scored engine events through ML and Java for a visible analyst demo."""

from __future__ import annotations

import argparse
import json
import urllib.request
from pathlib import Path


def post(url: str, payload: dict, method: str = "POST") -> dict:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method=method,
    )
    with urllib.request.urlopen(request) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if body else {}


def get(url: str) -> list[dict]:
    with urllib.request.urlopen(url) as response:
        return json.loads(response.read().decode("utf-8"))


def reset_active_cases(api_url: str) -> int:
    reset = 0
    for case in get(f"{api_url.rstrip('/')}/api/cases"):
        if case.get("status") in {"open", "investigating", "escalated"}:
            post(f"{api_url.rstrip('/')}/api/cases/{case['caseId']}/status", {"status": "resolved"}, "PATCH")
            reset += 1
    return reset


def run(scored_path: Path, ml_url: str, api_url: str, threshold: float, reset: bool) -> tuple[int, int, int]:
    if reset:
        post(f"{ml_url.rstrip('/')}/reset", {})
        post(f"{api_url.rstrip('/')}/api/signals/reset", {})
    reset_count = reset_active_cases(api_url) if reset else 0
    evaluated = alerts = 0
    for line in scored_path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        event = json.loads(line)
        result = post(f"{ml_url.rstrip('/')}/score", event)
        evaluated += 1
        category = event.get("category") or ("graph_ring" if result["graph_score"] > 0 else "anomaly")
        post(f"{api_url.rstrip('/')}/api/signals/ingest", {
            "transactionId": None,
            "ruleScore": event.get("rule_score", 0),
            "mlScore": result["ml_score"],
            "graphScore": result["graph_score"],
            "combinedScore": result["combined_score"],
            "category": category,
        })
        if result["combined_score"] < threshold:
            continue
        severity = "critical" if result["combined_score"] >= 0.85 else "high"
        post(f"{api_url.rstrip('/')}/api/alerts/ingest", {
            # JSONL replay is intentionally offline; transaction_id is not in the API database.
            "transactionId": None,
            "ruleScore": event.get("rule_score", 0),
            "mlScore": result["ml_score"],
            "graphScore": result["graph_score"],
            "combinedScore": result["combined_score"],
            "category": category,
            "severity": severity,
        })
        alerts += 1
    return reset_count, evaluated, alerts


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scored", type=Path)
    parser.add_argument("--ml-url", default="http://localhost:8000")
    parser.add_argument("--api-url", default="http://localhost:8080")
    parser.add_argument("--threshold", type=float, default=0.7)
    parser.add_argument("--keep-existing", action="store_true", help="Do not resolve active cases from earlier demo runs.")
    args = parser.parse_args()
    reset_count, evaluated, alerts = run(args.scored, args.ml_url, args.api_url, args.threshold, not args.keep_existing)
    print(f"Active cases reset: {reset_count}; events evaluated: {evaluated}; alerts pushed to dashboard: {alerts}")


if __name__ == "__main__":
    main()
