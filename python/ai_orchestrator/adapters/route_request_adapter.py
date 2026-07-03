from __future__ import annotations

from typing import Any

from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.schemas.routing import RouteRequest, RouteRequestContext


ROUTE_HISTORY_MESSAGE_LIMIT = 6
ROUTE_HISTORY_MESSAGE_CHARS = 600


def build_route_request(
    request: AssistantRequest,
    *,
    user_id: str | None = None,
    essay_text: str | None = None,
    topic_prompt: str | None = None,
    selected_text: str | None = None,
    current_page: str | None = None,
    active_task: dict[str, Any] | None = None,
) -> RouteRequest:
    selection_text = selected_text
    if selection_text is None and request.selection is not None:
        selection_text = request.selection.text

    source_page = current_page
    if source_page is None and request.client_meta is not None:
        source_page = request.client_meta.source_page

    study_stage = None
    if request.study_context is not None:
        study_stage = request.study_context.study_stage

    return RouteRequest(
        message=(request.message.text or "").strip(),
        conversation_id=request.app_conversation_id,
        user_id=user_id,
        study_stage=study_stage,
        assistant_mode=request.mode,
        context=RouteRequestContext(
            essay_text=essay_text,
            topic_prompt=topic_prompt,
            selected_text=selection_text,
            current_page=source_page,
            conversation_history=_build_route_conversation_history(request),
            active_task=active_task,
        ),
    )


def _build_route_conversation_history(request: AssistantRequest) -> list[dict[str, str]]:
    history = []
    for message in request.conversation_history[-ROUTE_HISTORY_MESSAGE_LIMIT:]:
        content = _truncate_route_history_content(message.content)
        if not content:
            continue
        history.append({"role": message.role, "content": content})
    return history


def _truncate_route_history_content(content: str | None) -> str:
    normalized = (content or "").strip()
    if len(normalized) <= ROUTE_HISTORY_MESSAGE_CHARS:
        return normalized
    return normalized[: ROUTE_HISTORY_MESSAGE_CHARS - 3].strip() + "..."
