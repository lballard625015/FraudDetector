"""Read-only investigation copilot for the fraud detection demo."""

from __future__ import annotations

import os
from typing import Any, Literal
from uuid import UUID

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

API_BASE_URL = os.getenv("API_BASE_URL", "http://api-service:8080")
ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY")
ANTHROPIC_MODEL = os.getenv("ANTHROPIC_MODEL", "claude-3-5-haiku-latest")
app = FastAPI(title="Fraud Detection Investigation Copilot", version="0.1.0")


class CopilotRequest(BaseModel):
    case_id: UUID | None = None
    page: str = "overview"
    provider: Literal["copilot", "claude"] = "copilot"
    question: str = Field(min_length=3, max_length=1000)


class CopilotResponse(BaseModel):
    answer: str
    tools_used: list[str]
    provider: str
    read_only: bool = True


async def api_get(path: str, authorization: str) -> Any:
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=10.0) as client:
        response = await client.get(path, headers={"Authorization": authorization})
    if response.status_code == 404:
        raise HTTPException(status_code=404, detail="Case not found")
    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail="Unable to read case evidence")
    return response.json()


def score_percent(value: Any) -> str:
    if value is None:
        return "unknown"
    return f"{float(value) * 100:.0f}%"


def build_answer(case: dict[str, Any] | None, timeline: list[dict[str, Any]], page: str, question: str) -> str:
    if case is None:
        page_guidance = {
            "overview": "Use the funnel to distinguish raw detections, open alerts, and active cases.",
            "alerts": "Start with high-severity or escalated alerts, then open a row to inspect its case evidence.",
            "risk": "Search by account name or number, compare the trend with the 0–1 thresholds, and inspect the contributing alerts.",
            "cases": "Open a case to review its workflow state, risk components, audit trail, and analyst notes.",
        }.get(page, "Review the visible evidence and follow the linked workflow controls.")
        return f"This is the {page} view. {page_guidance} I am operating in read-only mode and will not change analyst state."
    alert_id = case.get("alertId")
    status = str(case.get("status", "unknown")).capitalize()
    priority = str(case.get("priority") or "normal").capitalize()
    event_types = [str(event.get("eventType", "event")).replace("_", " ") for event in timeline]
    evidence = ", ".join(dict.fromkeys(event_types)) or "no recorded audit events"
    question_lower = question.lower()

    if "next" in question_lower or "should" in question_lower or "review" in question_lower:
        recommendation = (
            "Review the alert components, inspect linked-account activity, and verify the audit chain before changing status. "
            "Escalate only if the evidence requires senior or compliance review."
        )
    elif "why" in question_lower or "explain" in question_lower or "risk" in question_lower:
        recommendation = (
            "The risk decision should be grounded in the rule, ML, and graph components visible in the case record; "
            "a high combined score is a prioritization signal, not proof of fraud."
        )
    else:
        recommendation = "Use the case timeline and component scores as the evidence trail, then record the analyst decision as a note."

    return (
        f"Case {str(case.get('caseId', 'unknown'))[:8]} is currently {status} with {priority} priority. "
        f"It is linked to alert {str(alert_id)[:8] if alert_id else 'unknown'}. "
        f"The audit trail includes {evidence}. {recommendation} "
        "This copilot is read-only and does not change case state."
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


async def claude_answer(context: str, question: str) -> str | None:
    if not ANTHROPIC_API_KEY:
        return None
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(
            "https://api.anthropic.com/v1/messages",
            headers={
                "x-api-key": ANTHROPIC_API_KEY,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json",
            },
            json={
                "model": ANTHROPIC_MODEL,
                "max_tokens": 500,
                "system": "You are a read-only fraud operations analyst assistant. Use only the supplied context. Never claim to change case state or make a final fraud decision.",
                "messages": [{"role": "user", "content": f"Context:\n{context}\n\nQuestion: {question}"}],
            },
        )
    if response.status_code >= 400:
        raise HTTPException(status_code=502, detail="Claude provider request failed")
    payload = response.json()
    return "".join(block.get("text", "") for block in payload.get("content", []) if block.get("type") == "text")


@app.post("/copilot/chat", response_model=CopilotResponse)
async def chat(request: CopilotRequest, authorization: str | None = Header(default=None)) -> CopilotResponse:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Authentication required")
    case = await api_get(f"/api/cases/{request.case_id}", authorization) if request.case_id else None
    timeline = await api_get(f"/api/cases/{request.case_id}/timeline", authorization) if request.case_id else []
    answer = build_answer(case, timeline, request.page, request.question)
    tools_used = ["get_case", "get_case_timeline"] if case else ["page_context"]
    if request.provider == "claude":
        claude_result = await claude_answer(str({"case": case, "timeline": timeline, "page": request.page}), request.question)
        if claude_result:
            answer = claude_result
        else:
            answer = "Claude is not configured for this demo, so I used the local read-only Copilot instead.\n\n" + answer
    return CopilotResponse(
        answer=answer,
        tools_used=tools_used,
        provider=request.provider if request.provider == "claude" and ANTHROPIC_API_KEY else "copilot",
    )
