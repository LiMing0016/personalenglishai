from __future__ import annotations

from dataclasses import dataclass

try:
    from ..schemas.assistant_request import AssistantRequest
except ImportError:  # pragma: no cover - script mode fallback
    from schemas.assistant_request import AssistantRequest


@dataclass(frozen=True, slots=True)
class AssistantRoute:
    from_agent: str
    to_agent: str
    agent_name: str
    handoff_required: bool


def route_assistant_agent(request: AssistantRequest) -> AssistantRoute:
    route = _resolve_route(request)
    return AssistantRoute(
        from_agent="triageAgent",
        to_agent=route[0],
        agent_name=route[1],
        handoff_required=route[0] != "dailyExplainAgent",
    )


def _resolve_route(request: AssistantRequest) -> tuple[str, str]:
    if request.intent == "translate":
        return "translationAgent", "Translation Agent"

    if request.intent == "polish":
        return "writingCoachAgent", "Polish Agent"

    if request.intent == "grade_writing":
        return "writingCoachAgent", "Scoring Agent"

    if request.intent == "first_draft_coach":
        return "writingCoachAgent", "Prompt Design Agent"

    if request.intent == "analyze_question":
        # The existing Prompt Design Agent owns practice prompt and question/task analysis.
        return "questionAnalysisAgent", "Prompt Design Agent"

    if request.mode == "exam_boost":
        # P0 keeps the product-level examBoostAgent id while reusing the existing scoring specialist.
        return "examBoostAgent", "Scoring Agent"

    # There is no separate daily explain specialist yet; the Router Agent remains the safe default.
    return "dailyExplainAgent", "Router Agent"
