"""Export engine JSONL events as a flat training CSV."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from .features import export_rows


def export_jsonl(input_path: Path, output_path: Path) -> int:
    events = [json.loads(line) for line in input_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    names, matrix = export_rows(events)
    with output_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow([*names, "is_synthetic_fraud", "fraud_pattern_type"])
        for event, row in zip(events, matrix):
            writer.writerow([
                *row.tolist(),
                int(bool(event.get("is_synthetic_fraud", False))),
                event.get("fraud_pattern_type") or "",
            ])
    return len(events)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print(f"Exported {export_jsonl(args.input, args.output)} events to {args.output}")


if __name__ == "__main__":
    main()
