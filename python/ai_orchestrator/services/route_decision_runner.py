from __future__ import annotations

import json
import logging
from typing import Any

from python.ai_orchestrator.agents.route_decision import create_route_agent
from python.ai_orchestrator.schemas.routing import RouteRequest, RoutingDecision


log = logging.getLogger("uvicorn.error")


def _trace_metadata_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


class RouteDecisionRunner:
    def __init__(self, model: str, *, trace_include_sensitive_data: bool = True) -> None:
        self._model = model
        self._agent = create_route_agent(model)
        self._trace_include_sensitive_data = trace_include_sensitive_data

    async def route(self, request: RouteRequest, *, flush_trace: bool = True) -> RoutingDecision:
        from agents import RunConfig, Runner

        result = await Runner.run(
            self._agent,
            self._build_agent_input(request),
            run_config=RunConfig(
                workflow_name="PEAI RouteAgent",
                group_id=request.conversation_id,
                trace_include_sensitive_data=self._trace_include_sensitive_data,
                trace_metadata=self._build_trace_metadata(request),
            ),
        )
        final_output = getattr(result, "final_output", None)
        try:
            if isinstance(final_output, RoutingDecision):
                return final_output
            if isinstance(final_output, dict):
                return RoutingDecision.model_validate(final_output)
            raise ValueError("RouteAgent returned invalid structured output")
        finally:
            if flush_trace:
                self._flush_trace_export()

    def _build_agent_input(self, request: RouteRequest) -> str:
        payload: dict[str, Any] = request.model_dump(mode="json")
        payload["context"]["has_essay_text"] = request.context.has_essay_text
        payload["context"]["has_topic_prompt"] = request.context.has_topic_prompt
        payload["context"]["has_selected_text"] = request.context.has_selected_text
        return json.dumps(payload, ensure_ascii=False)

    def _build_trace_metadata(self, request: RouteRequest) -> dict[str, Any]:
        metadata = {
            "component": "route_decision_runner",
            "agent": "RouteAgent",
            "conversation_id": request.conversation_id,
            "user_id": request.user_id,
            "study_stage": request.study_stage,
            "assistant_mode": request.assistant_mode,
            "current_page": request.context.current_page,
            "has_essay_text": request.context.has_essay_text,
            "has_topic_prompt": request.context.has_topic_prompt,
            "has_selected_text": request.context.has_selected_text,
        }
        return {key: _trace_metadata_value(value) for key, value in metadata.items()}

    def _flush_trace_export(self) -> None:
        try:
            from agents import flush_traces

            flush_traces()
        except Exception:
            log.warning("RouteAgent trace flush failed", exc_info=True)
        try:
            from python.ai_orchestrator.observability import flush_observability

            flush_observability()
        except Exception:
            log.warning("RouteAgent observability flush failed", exc_info=True)
