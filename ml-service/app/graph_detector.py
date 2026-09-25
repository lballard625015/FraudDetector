"""Rolling account graph with cycle and shared-device signals."""

from __future__ import annotations

from collections import deque
from datetime import datetime, timezone
from typing import Any

import networkx as nx


class RollingGraph:
    def __init__(self, window_seconds: int = 3600, max_events: int = 10000) -> None:
        self.window_seconds = window_seconds
        self.max_events = max_events
        self.events: deque[dict[str, Any]] = deque(maxlen=max_events)
        self.graph = nx.DiGraph()
        self.device_graph = nx.Graph()

    def add(self, event: dict[str, Any]) -> None:
        self.events.append(event)
        self._prune()
        sender = event.get("from_account")
        receiver = event.get("to_account")
        device = event.get("device_id")
        if sender and receiver:
            self.graph.add_edge(sender, receiver, transaction_id=event.get("transaction_id"))
        if device and sender:
            self.device_graph.add_edge(f"account:{sender}", f"device:{device}")

    def _prune(self) -> None:
        cutoff = datetime.now(timezone.utc).timestamp() - self.window_seconds
        while self.events:
            timestamp = self.events[0].get("occurred_at")
            if not timestamp:
                break
            try:
                event_time = datetime.fromisoformat(timestamp.replace("Z", "+00:00")).timestamp()
            except ValueError:
                event_time = cutoff
            if event_time >= cutoff:
                break
            self.events.popleft()
        active_accounts = {value for event in self.events for value in (event.get("from_account"), event.get("to_account")) if value}
        self.graph.remove_nodes_from([node for node in self.graph if node not in active_accounts])

    def score(self) -> dict[str, Any]:
        cycles = [cycle for cycle in nx.simple_cycles(self.graph) if len(cycle) >= 3]
        components = list(nx.connected_components(self.device_graph))
        largest_cluster = max((len(component) for component in components), default=0)
        cycle_score = 0.85 if cycles else 0.0
        cluster_score = min(0.85, largest_cluster / 10.0) if largest_cluster >= 4 else 0.0
        return {
            "graph_score": max(cycle_score, cluster_score),
            "cycle_count": len(cycles),
            "largest_device_cluster": largest_cluster,
        }
