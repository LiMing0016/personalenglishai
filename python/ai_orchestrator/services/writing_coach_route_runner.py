from __future__ import annotations

import json
import logging
from typing import Any

from python.ai_orchestrator.agents.writing_coach_route import create_writing_coach_route_agent
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.schemas.writing_coach import WritingCoachRouteDecision


log = logging.getLogger("uvicorn.error")


def _trace_metadata_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


class WritingCoachRouteRunner:
    def __init__(self, model: str, *, trace_include_sensitive_data: bool = True) -> None:
        self._model = model
        self._agent = create_writing_coach_route_agent(model)
        self._trace_include_sensitive_data = trace_include_sensitive_data

    async def route(self, request: AssistantRequest, *, flush_trace: bool = True) -> WritingCoachRouteDecision:
        from agents import RunConfig, Runner

        result = await Runner.run(
            self._agent,
            self._build_agent_input(request),
            run_config=RunConfig(
                workflow_name="PEAI WritingCoachRoute",
                group_id=request.app_conversation_id,
                trace_include_sensitive_data=self._trace_include_sensitive_data,
                trace_metadata=self._build_trace_metadata(request),
            ),
        )
        final_output = getattr(result, "final_output", None)
        try:
            if isinstance(final_output, WritingCoachRouteDecision):
                return final_output
            if isinstance(final_output, dict):
                return WritingCoachRouteDecision.model_validate(final_output)
            raise ValueError("WritingCoachRouteAgent returned invalid structured output")
        finally:
            if flush_trace:
                self._flush_trace_export()

    def _build_agent_input(self, request: AssistantRequest) -> str:
        context = request.writing_coach_context
        selection_text = ""
        if request.selection is not None:
            selection_text = request.selection.text
        elif context is not None and context.selected_text:
            selection_text = context.selected_text

        payload = {
            "message": (request.message.text or "").strip(),
            "conversation_id": request.app_conversation_id,
            "mode": request.mode,
            "scope": request.scope,
            "has_selected_text": bool(selection_text.strip()),
            "has_draft_text": bool((context.draft_text if context else "") or ""),
            "has_topic_prompt": bool((context.essay_question if context else "") or ""),
            "writing_context": {
                "action": context.action if context else None,
                "writing_mode": context.writing_mode if context else None,
                "study_stage": context.study_stage if context else None,
                "task_type": context.task_type if context else None,
                "essay_question": context.essay_question if context else None,
                "include_draft": context.include_draft if context else None,
                "topic_analysis_done": context.topic_analysis_done if context else None,
            },
        }
        return json.dumps(payload, ensure_ascii=False)

    def _build_trace_metadata(self, request: AssistantRequest) -> dict[str, Any]:
        context = request.writing_coach_context
        metadata = {
            "component": "writing_coach_route_runner",
            "agent": "WritingCoachRouteAgent",
            "conversation_id": request.app_conversation_id,
            "mode": request.mode,
            "scope": request.scope,
            "action": context.action if context else None,
            "has_topic_prompt": bool((context.essay_question if context else "") or ""),
            "has_draft_text": bool((context.draft_text if context else "") or ""),
            "has_selected_text": bool(
                (request.selection.text if request.selection else "")
                or (context.selected_text if context else "")
            ),
        }
        return {key: _trace_metadata_value(value) for key, value in metadata.items()}

    def _flush_trace_export(self) -> None:
        try:
            from agents import flush_traces

            flush_traces()
        except Exception:
            log.warning("WritingCoachRouteAgent trace flush failed", exc_info=True)
        try:
            from python.ai_orchestrator.observability import flush_observability

            flush_observability()
        except Exception:
            log.warning("WritingCoachRouteAgent observability flush failed", exc_info=True)
