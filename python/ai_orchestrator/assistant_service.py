from __future__ import annotations

import logging
import os
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence
from uuid import uuid4

try:
    from .adapters.openai_input_items import build_assistant_input_items
    from .adapters.openai_input_items import build_input_items
    from .adapters.route_request_adapter import build_route_request
    from .agents.attachment import create_attachment_agent
    from .agents.assistant_routing import route_assistant_agent
    from .agents.router import create_router_agent
    from .agents.specialists import SPECIALIST_AGENT_SPECS
    from .agents.specialists import create_specialist_agent
    from .agents.writing_coach import create_writing_coach_stage_agent
    from .agents.writing_coach import structured_writing_coach_action
    from .agents.writing_coach import writing_coach_stage_agent_name
    from .prompts.user_context import build_contextual_user_message
    from .schemas.assistant_request import AssistantRequest
    from .schemas.assistant_request import AssistantRunMetadata
    from .schemas.assistant_request import AssistantOpenAIState
    from .schemas.chat import AssistantReply
    from .schemas.chat import UploadedAttachment
    from .schemas.assistant_request import AssistantUsage
    from .schemas.routing import RouteRequest
    from .schemas.routing import RoutingIntent
    from .schemas.routing import RoutingDecision
    from .schemas.routing_state import ActiveTaskState
    from .schemas.routing_state import ContinuationClassifierInput
    from .schemas.routing_state import ContinuationDecision
    from .schemas.routing_state import TaskOutputType
    from .services.active_task_state import ActiveTaskStateStore
    from .services.active_task_state import InMemoryActiveTaskStateStore
    from .services.agent_session_runner import run_agent_session
    from .services.agent_session_runner import stream_agent_session
    from .services.assistant_request_validator import validate_assistant_request
    from .services.continuation_classifier import AgentsSdkContinuationClassifierClient
    from .services.continuation_classifier import ContinuationClassifier
    from .services.route_decision_runner import RouteDecisionRunner
    from .services.writing_coach_route_runner import WritingCoachRouteRunner
except ImportError:  # pragma: no cover - script mode fallback
    from adapters.openai_input_items import build_assistant_input_items
    from adapters.openai_input_items import build_input_items
    from adapters.route_request_adapter import build_route_request
    from agents.attachment import create_attachment_agent
    from agents.assistant_routing import route_assistant_agent
    from agents.router import create_router_agent
    from agents.specialists import SPECIALIST_AGENT_SPECS
    from agents.specialists import create_specialist_agent
    from agents.writing_coach import create_writing_coach_stage_agent
    from agents.writing_coach import structured_writing_coach_action
    from agents.writing_coach import writing_coach_stage_agent_name
    from prompts.user_context import build_contextual_user_message
    from schemas.assistant_request import AssistantRequest
    from schemas.assistant_request import AssistantRunMetadata
    from schemas.assistant_request import AssistantOpenAIState
    from schemas.chat import AssistantReply
    from schemas.chat import UploadedAttachment
    from schemas.assistant_request import AssistantUsage
    from schemas.routing import RouteRequest
    from schemas.routing import RoutingIntent
    from schemas.routing import RoutingDecision
    from schemas.routing_state import ActiveTaskState
    from schemas.routing_state import ContinuationClassifierInput
    from schemas.routing_state import ContinuationDecision
    from schemas.routing_state import TaskOutputType
    from services.active_task_state import ActiveTaskStateStore
    from services.active_task_state import InMemoryActiveTaskStateStore
    from services.agent_session_runner import run_agent_session
    from services.agent_session_runner import stream_agent_session
    from services.assistant_request_validator import validate_assistant_request
    from services.continuation_classifier import AgentsSdkContinuationClassifierClient
    from services.continuation_classifier import ContinuationClassifier
    from services.route_decision_runner import RouteDecisionRunner
    from services.writing_coach_route_runner import WritingCoachRouteRunner


class AssistantConfigError(RuntimeError):
    pass


log = logging.getLogger("uvicorn.error")


@dataclass(frozen=True, slots=True)
class AssistantRunContext:
    conversation_id: str
    study_stage: str | None = None
    assistant_mode: str | None = None


@dataclass(frozen=True, slots=True)
class ActiveTaskMetadata:
    intent: RoutingIntent
    output_type: TaskOutputType
    continuation_capabilities: set[str]


_AGENT_TASK_METADATA: dict[str, ActiveTaskMetadata] = {
    "Polish Agent": ActiveTaskMetadata("polish", "polished_text", {"rewrite_variant", "make_harder", "simplify"}),
    "Sentence Structure Agent": ActiveTaskMetadata(
        "sentence_structure",
        "sentence_analysis",
        {"expand_detail", "simplify", "generate_practice"},
    ),
    "Vocab Agent": ActiveTaskMetadata("vocab", "vocab_explanation", {"expand_detail", "generate_practice"}),
    "Translation Agent": ActiveTaskMetadata("translation", "translation", {"rewrite_variant", "expand_detail"}),
    "Scoring Agent": ActiveTaskMetadata("scoring", "score_feedback", {"expand_detail", "generate_practice"}),
    "Prompt Design Agent": ActiveTaskMetadata("practice_design", "practice_set", {"more_options", "make_harder"}),
    "Ability Profile Agent": ActiveTaskMetadata("ability_profile", "ability_profile", {"expand_detail"}),
    "Learning Planner Agent": ActiveTaskMetadata(
        "learning_planner",
        "plan",
        {"more_options", "expand_detail", "simplify"},
    ),
}

_ROUTE_DECISION_TARGET_AGENTS: dict[str, str] = {
    "polish": "Polish Agent",
    "sentence_structure": "Sentence Structure Agent",
    "vocab": "Vocab Agent",
    "translation": "Translation Agent",
    "scoring": "Scoring Agent",
    "practice_design": "Prompt Design Agent",
    "ability_profile": "Ability Profile Agent",
    "learning_planner": "Learning Planner Agent",
    "writing_evaluation": "Scoring Agent",
    "first_draft_coach": "Prompt Design Agent",
    "realtime_sentence_feedback": "Sentence Structure Agent",
}


def _summarize_text(text: str, *, limit: int = 160) -> str:
    normalized = " ".join(text.split())
    if len(normalized) <= limit:
        return normalized
    return f"{normalized[:limit].rstrip()}..."


def _trace_metadata_value(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)[:500]


def _build_continuation_routing_message(
    message: str,
    *,
    decision: ContinuationDecision,
    state: ActiveTaskState,
) -> str:
    return "\n".join(
        [
            "[续问判定上下文]",
            f"- 判定关系: {decision.relation}",
            f"- 目标 intent: {decision.resolved_intent or state.active_intent}",
            f"- 续问动作: {decision.continuation_action}",
            f"- 上一轮任务: {state.task_title}",
            f"- 上一轮摘要: {state.task_summary}",
            "- 路由要求: 当前用户消息应优先按目标 intent 延续上一轮任务；不要向用户暴露 classifier、intent 或内部路由信息。",
            "",
            "[当前用户消息]",
            message,
        ]
    )


def _should_use_sdk_session_for_request(request: AssistantRequest) -> bool:
    # AssistantRequest is converted to Responses API input item lists so it can
    # carry text, selections, and attachments consistently. Agents SDK session
    # memory only accepts plain string input, not list input items.
    return False


class AssistantAgentService:
    def __init__(
        self,
        *,
        model: str,
        session_db_path: str,
        active_task_store: ActiveTaskStateStore | None = None,
        continuation_classifier=None,
        route_decision_runner=None,
        writing_coach_route_runner=None,
        route_decision_enabled: bool = False,
    ) -> None:
        self.model = model
        self.session_db_path = session_db_path
        self._router_agent = None
        self._attachment_agent = None
        self._specialist_agents = {}
        self._writing_coach_stage_agents = {}
        self._active_task_store = active_task_store or InMemoryActiveTaskStateStore()
        self._continuation_classifier = continuation_classifier or ContinuationClassifier(
            AgentsSdkContinuationClassifierClient(model)
        )
        self._route_decision_runner = route_decision_runner
        self._writing_coach_route_runner = writing_coach_route_runner
        self._route_decision_enabled = route_decision_enabled

    @classmethod
    def from_env(cls) -> "AssistantAgentService":
        model = (
            os.getenv("AI_ASSISTANT_MODEL", "").strip()
            or os.getenv("AI_PROVIDER_OPENAI_MODEL", "").strip()
            or "gpt-5.4-mini"
        )
        session_db_path = os.getenv(
            "AI_ASSISTANT_SESSION_DB_PATH",
            str(Path(__file__).resolve().parent / "data" / "assistant_sessions.db"),
        )
        route_decision_enabled = os.getenv("AI_ASSISTANT_ROUTE_DECISION_ENABLED", "true").strip().lower() not in {
            "0",
            "false",
            "no",
            "off",
        }
        return cls(
            model=model,
            session_db_path=session_db_path,
            route_decision_enabled=route_decision_enabled,
        )

    def is_configured(self) -> bool:
        return bool(os.getenv("OPENAI_API_KEY", "").strip())

    def _get_router_agent(self):
        if self._router_agent is not None:
            return self._router_agent

        if not self.is_configured():
            raise AssistantConfigError("OPENAI_API_KEY 未配置，学习助手暂时不可用。")

        self._router_agent = create_router_agent(self.model)
        return self._router_agent

    def _get_attachment_agent(self):
        if self._attachment_agent is not None:
            return self._attachment_agent

        if not self.is_configured():
            raise AssistantConfigError("OPENAI_API_KEY 未配置，学习助手暂时不可用。")

        self._attachment_agent = create_attachment_agent(self.model)
        return self._attachment_agent

    def _get_agent_by_name(self, agent_name: str):
        if agent_name == "Router Agent":
            return self._get_router_agent()
        if agent_name in self._specialist_agents:
            return self._specialist_agents[agent_name]

        if not self.is_configured():
            raise AssistantConfigError("OPENAI_API_KEY 未配置，学习助手暂时不可用。")

        spec = next((candidate for candidate in SPECIALIST_AGENT_SPECS if candidate.name == agent_name), None)
        if spec is None:
            raise AssistantConfigError(f"未知学习助手 Agent: {agent_name}")
        agent = create_specialist_agent(spec, self.model)
        self._specialist_agents[agent_name] = agent
        return agent

    def _get_writing_coach_stage_agent(self, action):
        if action in self._writing_coach_stage_agents:
            return self._writing_coach_stage_agents[action]

        if not self.is_configured():
            raise AssistantConfigError("OPENAI_API_KEY 未配置，写作教练暂时不可用。")

        agent = create_writing_coach_stage_agent(action, self.model)
        self._writing_coach_stage_agents[action] = agent
        return agent

    def _build_run_context(self, request: AssistantRequest, *, conversation_id: str) -> AssistantRunContext:
        study_context = request.study_context
        return AssistantRunContext(
            conversation_id=conversation_id,
            study_stage=study_context.study_stage if study_context else None,
            assistant_mode=request.mode,
        )

    def _get_route_decision_runner(self):
        if self._route_decision_runner is None:
            if not self.is_configured():
                raise AssistantConfigError("OPENAI_API_KEY 未配置，学习助手暂时不可用。")
            self._route_decision_runner = RouteDecisionRunner(model=self.model)
        return self._route_decision_runner

    def _get_writing_coach_route_runner(self):
        if self._writing_coach_route_runner is None:
            if not self.is_configured():
                raise AssistantConfigError("OPENAI_API_KEY 未配置，写作教练暂时不可用。")
            self._writing_coach_route_runner = WritingCoachRouteRunner(model=self.model)
        return self._writing_coach_route_runner

    async def _resolve_writing_coach_action(
        self,
        request: AssistantRequest,
        *,
        run_id: str,
        trace_id: str,
        flush_trace: bool = False,
    ):
        explicit_action = structured_writing_coach_action(request)
        if explicit_action is not None:
            return explicit_action
        if request.intent != "first_draft_coach":
            return None

        context_action = request.writing_coach_context.action if request.writing_coach_context else None
        if context_action not in {None, "coach"}:
            return None

        try:
            decision = await self._get_writing_coach_route_runner().route(request, flush_trace=flush_trace)
        except Exception:
            log.error(
                "[WRITING_COACH_ROUTE_ERROR] run_id=%s trace_id=%s conversation_id=%s",
                run_id,
                trace_id,
                request.app_conversation_id or request.client_message_id,
                exc_info=True,
            )
            return None

        log.info(
            "[WRITING_COACH_ROUTE_DONE] run_id=%s trace_id=%s conversation_id=%s route_type=%s "
            "target_action=%s edit_intent=%s confidence=%.2f missing_inputs=%s",
            run_id,
            trace_id,
            request.app_conversation_id or request.client_message_id,
            decision.route_type,
            decision.target_action or "",
            decision.edit_intent,
            decision.confidence,
            decision.missing_inputs,
        )
        if decision.route_type == "run_stage":
            return decision.target_action
        return None

    async def route_assistant_request(
        self,
        request: AssistantRequest,
        authorization: str | None = None,
    ) -> RoutingDecision:
        validate_assistant_request(request)
        return await self._run_route_decision(request)

    async def _run_route_decision(self, request: AssistantRequest, *, flush_trace: bool = True) -> RoutingDecision:
        route_request, decision = await self._run_route_decision_with_request(request, flush_trace=flush_trace)
        return decision

    async def _run_route_decision_with_request(
        self,
        request: AssistantRequest,
        *,
        flush_trace: bool = True,
    ) -> tuple[RouteRequest, RoutingDecision]:
        route_request = build_route_request(request)
        decision = await self._get_route_decision_runner().route(route_request, flush_trace=flush_trace)
        return route_request, decision

    async def _maybe_run_route_decision_with_request(
        self,
        request: AssistantRequest,
        *,
        run_id: str,
        trace_id: str,
        flush_trace: bool = True,
    ) -> tuple[RouteRequest | None, RoutingDecision | None]:
        if not self._route_decision_enabled:
            return None, None

        try:
            route_request, decision = await self._run_route_decision_with_request(request, flush_trace=flush_trace)
        except Exception:
            log.error(
                "[ROUTE_DECISION_ERROR] run_id=%s trace_id=%s conversation_id=%s",
                run_id,
                trace_id,
                request.app_conversation_id or request.client_message_id,
                exc_info=True,
            )
            return None, None

        log.info(
            "[ROUTE_DECISION_DONE] run_id=%s trace_id=%s conversation_id=%s intent=%s route_type=%s "
            "workflow=%s target_agent=%s confidence=%.2f missing_inputs=%s",
            run_id,
            trace_id,
            request.app_conversation_id or request.client_message_id,
            decision.intent,
            decision.route_type,
            decision.workflow or "",
            decision.target_agent or "",
            decision.confidence,
            decision.missing_inputs,
        )
        return route_request, decision

    async def _maybe_run_route_decision(
        self,
        request: AssistantRequest,
        *,
        run_id: str,
        trace_id: str,
        flush_trace: bool = True,
    ) -> RoutingDecision | None:
        _, decision = await self._maybe_run_route_decision_with_request(
            request,
            run_id=run_id,
            trace_id=trace_id,
            flush_trace=flush_trace,
        )
        return decision

    def _build_run_metadata(
        self,
        request: AssistantRequest,
        *,
        run_id: str,
        trace_id: str,
        validated_scope: str,
        agent_name: str,
        route_request: RouteRequest | None,
        route_decision: RoutingDecision | None,
        usage,
        run_items,
        latency_ms: float,
    ) -> AssistantRunMetadata:
        return AssistantRunMetadata(
            runId=run_id,
            traceId=trace_id,
            agentName=agent_name,
            model=self.model,
            mode=request.mode,
            intent=request.intent,
            scope=validated_scope,
            latencyMs=int(latency_ms),
            usage=AssistantUsage(
                requests=usage.requests,
                inputTokens=usage.input_tokens,
                cachedInputTokens=usage.cached_input_tokens,
                outputTokens=usage.output_tokens,
                totalTokens=usage.total_tokens,
            ),
            openai=AssistantOpenAIState(responseId=run_items.last_response_id),
            routeRequest=route_request.model_dump(mode="json") if route_request is not None else None,
            routingDecision=route_decision.model_dump(mode="json") if route_decision is not None else None,
            steps=[
                {
                    "stepType": "route_agent",
                    "agentName": "RouteAgent",
                    "output": route_decision.model_dump(mode="json") if route_decision is not None else None,
                },
                {
                    "stepType": "target_agent",
                    "agentName": agent_name,
                    "usage": {
                        "requests": usage.requests,
                        "inputTokens": usage.input_tokens,
                        "cachedInputTokens": usage.cached_input_tokens,
                        "outputTokens": usage.output_tokens,
                        "totalTokens": usage.total_tokens,
                    },
                    "responseId": run_items.last_response_id,
                },
            ],
            promptSnapshots=[],
        )

    def _resolve_agent_name_from_route_decision(self, decision: RoutingDecision | None) -> str | None:
        if decision is None:
            return None
        if decision.route_type != "run_workflow" or decision.target_agent is None:
            return None
        return _ROUTE_DECISION_TARGET_AGENTS.get(decision.target_agent)

    def _assistant_trace_metadata(
        self,
        request: AssistantRequest,
        *,
        run_id: str,
        trace_id: str,
        scope: str,
    ) -> dict[str, object]:
        study_context = request.study_context
        client_meta = request.client_meta
        metadata = {
            "environment": os.getenv("APP_ENV") or os.getenv("ENV") or "local",
            "component": "assistant_agent_service",
            "run_id": run_id,
            "trace_id": trace_id,
            "conversation_id": request.app_conversation_id,
            "client_message_id": request.client_message_id,
            "model": self.model,
            "mode": request.mode,
            "intent": request.intent,
            "scope": scope,
            "study_stage": study_context.study_stage if study_context is not None else None,
            "target_exam": study_context.target_exam if study_context is not None else None,
            "source_page": client_meta.source_page if client_meta is not None else None,
            "attachment_count": len(request.attachments),
            "has_selection": request.selection is not None,
            "route_decision_enabled": self._route_decision_enabled,
        }
        return {key: _trace_metadata_value(value) for key, value in metadata.items()}

    def _target_agent_trace_metadata(
        self,
        request: AssistantRequest,
        *,
        run_id: str,
        trace_id: str,
        scope: str,
        agent_name: str,
        route_decision: RoutingDecision | None,
    ) -> dict[str, object]:
        study_context = request.study_context
        client_meta = request.client_meta
        metadata = {
            "environment": os.getenv("APP_ENV") or os.getenv("ENV") or "local",
            "component": "assistant_target_agent",
            "run_id": run_id,
            "trace_id": trace_id,
            "conversation_id": request.app_conversation_id,
            "client_message_id": request.client_message_id,
            "model": self.model,
            "mode": request.mode,
            "intent": request.intent,
            "scope": scope,
            "study_stage": study_context.study_stage if study_context is not None else None,
            "target_exam": study_context.target_exam if study_context is not None else None,
            "source_page": client_meta.source_page if client_meta is not None else None,
            "agent_name": agent_name,
            "route_type": route_decision.route_type if route_decision is not None else "",
            "target_agent": route_decision.target_agent if route_decision is not None else "",
        }
        return {key: _trace_metadata_value(value) for key, value in metadata.items()}

    def _legacy_chat_trace_metadata(
        self,
        *,
        conversation_id: str,
        study_stage: str | None,
        assistant_mode: str | None,
        has_attachments: bool,
        use_session: bool,
    ) -> dict[str, object]:
        metadata = {
            "app": "peai",
            "environment": os.getenv("APP_ENV") or os.getenv("ENV") or "local",
            "component": "assistant_legacy_chat",
            "run_type": "live",
            "conversation_id": conversation_id,
            "model": self.model,
            "study_stage": study_stage,
            "assistant_mode": assistant_mode,
            "has_attachments": has_attachments,
            "use_session": use_session,
        }
        return {key: _trace_metadata_value(value) for key, value in metadata.items()}

    def _assistant_workflow_trace(
        self,
        request: AssistantRequest,
        *,
        conversation_id: str | None,
        run_id: str,
        trace_id: str,
        scope: str,
    ):
        from agents import trace

        return trace(
            "PEAI Assistant Workflow",
            group_id=conversation_id,
            metadata=self._assistant_trace_metadata(request, run_id=run_id, trace_id=trace_id, scope=scope),
        )

    def _flush_trace_export(self) -> None:
        try:
            from agents import flush_traces

            flush_traces()
        except Exception:
            log.warning("Assistant workflow trace flush failed", exc_info=True)
        try:
            from python.ai_orchestrator.observability import flush_observability

            flush_observability()
        except Exception:
            log.warning("Assistant observability flush failed", exc_info=True)

    async def run_assistant_request(
        self,
        request: AssistantRequest,
        authorization: str | None = None,
    ) -> AssistantReply:
        started_at = time.perf_counter()
        validated = validate_assistant_request(request)
        route = route_assistant_agent(request)
        conversation_id = request.app_conversation_id or request.client_message_id
        run_id = f"run_{uuid4().hex}"
        trace_id = f"trace_{uuid4().hex}"

        try:
            with self._assistant_workflow_trace(
                request,
                conversation_id=conversation_id,
                run_id=run_id,
                trace_id=trace_id,
                scope=validated.scope,
            ):
                route_request, route_decision = await self._maybe_run_route_decision_with_request(
                    request,
                    run_id=run_id,
                    trace_id=trace_id,
                    flush_trace=False,
                )
                writing_coach_action = await self._resolve_writing_coach_action(
                    request,
                    run_id=run_id,
                    trace_id=trace_id,
                    flush_trace=False,
                )
                resolved_agent_name = (
                    writing_coach_stage_agent_name(writing_coach_action)
                    if writing_coach_action is not None
                    else self._resolve_agent_name_from_route_decision(route_decision) or route.agent_name
                )

                log.info(
                    "[ASSISTANT_RUN_START] run_id=%s trace_id=%s conversation_id=%s model=%s mode=%s "
                    "intent=%s scope=%s agent=%s attachment_count=%s authorization_present=%s",
                    run_id,
                    trace_id,
                    conversation_id,
                    self.model,
                    request.mode,
                    request.intent,
                    validated.scope,
                    resolved_agent_name,
                    len(request.attachments),
                    bool(authorization),
                )

                agent = (
                    self._get_writing_coach_stage_agent(writing_coach_action)
                    if writing_coach_action is not None
                    else self._get_agent_by_name(resolved_agent_name)
                )
                agent_input = build_assistant_input_items(request)
                use_session = _should_use_sdk_session_for_request(request)
                result = await run_agent_session(
                    agent=agent,
                    agent_input=agent_input,
                    conversation_id=conversation_id,
                    session_db_path=self.session_db_path,
                    use_session=use_session,
                    run_context=self._build_run_context(request, conversation_id=conversation_id),
                    trace_workflow_name="PEAI Target Agent",
                    trace_metadata=self._target_agent_trace_metadata(
                        request,
                        run_id=run_id,
                        trace_id=trace_id,
                        scope=validated.scope,
                        agent_name=resolved_agent_name,
                        route_decision=route_decision,
                    ),
                )
                if not result.final_output:
                    raise AssistantConfigError("学习助手没有返回内容。")

                final_agent_name = result.agent_name or resolved_agent_name
                duration_ms = (time.perf_counter() - started_at) * 1000
                run_metadata = self._build_run_metadata(
                    request,
                    run_id=run_id,
                    trace_id=trace_id,
                    validated_scope=validated.scope,
                    agent_name=final_agent_name,
                    route_request=route_request,
                    route_decision=route_decision,
                    usage=result.usage,
                    run_items=result.run_items,
                    latency_ms=duration_ms,
                )
                log.info(
                    "[ASSISTANT_RUN_DONE] run_id=%s trace_id=%s conversation_id=%s agent=%s duration_ms=%.1f "
                    "response_id=%s total_tokens=%s",
                    run_id,
                    trace_id,
                    conversation_id,
                    final_agent_name,
                    duration_ms,
                    result.run_items.last_response_id or "",
                    result.usage.total_tokens,
                )
        except Exception:
            duration_ms = (time.perf_counter() - started_at) * 1000
            log.error(
                "[ASSISTANT_RUN_ERROR] run_id=%s trace_id=%s conversation_id=%s duration_ms=%.1f",
                run_id,
                trace_id,
                conversation_id,
                duration_ms,
                exc_info=True,
            )
            raise
        finally:
            self._flush_trace_export()

        return AssistantReply(
            reply=result.final_output,
            agent_name=final_agent_name,
            run=run_metadata,
        )

    async def stream_assistant_request(
        self,
        request: AssistantRequest,
        authorization: str | None = None,
    ):
        started_at = time.perf_counter()
        validated = validate_assistant_request(request)
        route = route_assistant_agent(request)
        conversation_id = request.app_conversation_id or request.client_message_id
        run_id = f"run_{uuid4().hex}"
        trace_id = f"trace_{uuid4().hex}"
        message_id = f"msg_{uuid4().hex}"

        content_parts: list[str] = []
        try:
            with self._assistant_workflow_trace(
                request,
                conversation_id=conversation_id,
                run_id=run_id,
                trace_id=trace_id,
                scope=validated.scope,
            ):
                route_request, route_decision = await self._maybe_run_route_decision_with_request(
                    request,
                    run_id=run_id,
                    trace_id=trace_id,
                    flush_trace=False,
                )
                writing_coach_action = await self._resolve_writing_coach_action(
                    request,
                    run_id=run_id,
                    trace_id=trace_id,
                    flush_trace=False,
                )
                resolved_agent_name = (
                    writing_coach_stage_agent_name(writing_coach_action)
                    if writing_coach_action is not None
                    else self._resolve_agent_name_from_route_decision(route_decision) or route.agent_name
                )

                log.info(
                    "[ASSISTANT_STREAM_START] run_id=%s trace_id=%s conversation_id=%s model=%s mode=%s "
                    "intent=%s scope=%s agent=%s attachment_count=%s authorization_present=%s",
                    run_id,
                    trace_id,
                    conversation_id,
                    self.model,
                    request.mode,
                    request.intent,
                    validated.scope,
                    resolved_agent_name,
                    len(request.attachments),
                    bool(authorization),
                )

                yield {
                    "type": "run.started",
                    "runId": run_id,
                    "traceId": trace_id,
                    "agentName": resolved_agent_name,
                    "model": self.model,
                }
                yield {
                    "type": "message.created",
                    "runId": run_id,
                    "messageId": message_id,
                    "role": "assistant",
                }

                agent = (
                    self._get_writing_coach_stage_agent(writing_coach_action)
                    if writing_coach_action is not None
                    else self._get_agent_by_name(resolved_agent_name)
                )
                agent_input = build_assistant_input_items(request)
                use_session = _should_use_sdk_session_for_request(request)
                final_result = None
                async for event in stream_agent_session(
                    agent=agent,
                    agent_input=agent_input,
                    conversation_id=conversation_id,
                    session_db_path=self.session_db_path,
                    use_session=use_session,
                    run_context=self._build_run_context(request, conversation_id=conversation_id),
                    trace_workflow_name="PEAI Target Agent",
                    trace_metadata=self._target_agent_trace_metadata(
                        request,
                        run_id=run_id,
                        trace_id=trace_id,
                        scope=validated.scope,
                        agent_name=resolved_agent_name,
                        route_decision=route_decision,
                    ),
                ):
                    if event.type == "delta":
                        content_parts.append(event.delta)
                        yield {
                            "type": "message.delta",
                            "runId": run_id,
                            "messageId": message_id,
                            "delta": event.delta,
                        }
                        continue
                    final_result = event.result

                if final_result is None or not final_result.final_output:
                    raise AssistantConfigError("学习助手没有返回内容。")

                final_agent_name = final_result.agent_name or resolved_agent_name
                duration_ms = (time.perf_counter() - started_at) * 1000
                run_metadata = self._build_run_metadata(
                    request,
                    run_id=run_id,
                    trace_id=trace_id,
                    validated_scope=validated.scope,
                    agent_name=final_agent_name,
                    route_request=route_request,
                    route_decision=route_decision,
                    usage=final_result.usage,
                    run_items=final_result.run_items,
                    latency_ms=duration_ms,
                )
                yield {
                    "type": "message.completed",
                    "runId": run_id,
                    "messageId": message_id,
                    "content": final_result.final_output or "".join(content_parts),
                }
                yield {
                    "type": "run.completed",
                    "runId": run_id,
                    "run": run_metadata.model_dump(by_alias=True),
                }

                log.info(
                    "[ASSISTANT_STREAM_DONE] run_id=%s trace_id=%s conversation_id=%s agent=%s duration_ms=%.1f total_tokens=%s",
                    run_id,
                    trace_id,
                    conversation_id,
                    final_agent_name,
                    duration_ms,
                    final_result.usage.total_tokens,
                )
        except Exception as exc:
            duration_ms = (time.perf_counter() - started_at) * 1000
            log.error(
                "[ASSISTANT_STREAM_ERROR] run_id=%s trace_id=%s conversation_id=%s duration_ms=%.1f",
                run_id,
                trace_id,
                conversation_id,
                duration_ms,
                exc_info=True,
            )
            yield {
                "type": "run.failed",
                "runId": run_id,
                "error": {
                    "code": "OPENAI_RUN_FAILED",
                    "message": str(exc),
                },
            }
        finally:
            self._flush_trace_export()

    async def chat(
        self,
        *,
        message: str,
        conversation_id: str,
        attachments: Sequence[UploadedAttachment],
        study_stage: str | None = None,
        assistant_mode: str | None = None,
        authorization: str | None = None,
    ) -> AssistantReply:
        started_at = time.perf_counter()
        has_attachments = len(attachments) > 0
        use_session = not has_attachments
        attachment_types = tuple(str(attachment.get("content_type", "")) for attachment in attachments)
        log.info(
            "[ASSISTANT_CHAT_START] conversation_id=%s model=%s study_stage=%s message_chars=%s "
            "assistant_mode=%s attachment_count=%s attachment_types=%s use_session=%s",
            conversation_id,
            self.model,
            study_stage or "",
            len(message),
            assistant_mode or "",
            len(attachments),
            attachment_types,
            use_session,
        )

        try:
            agent = self._get_attachment_agent() if has_attachments else self._get_router_agent()
            active_task_state = self._active_task_store.get(conversation_id) if use_session else None
            continuation_decision = None
            routed_message = message
            if active_task_state is not None:
                continuation_decision = await self._continuation_classifier.classify(
                    ContinuationClassifierInput(
                        current_user_message=message,
                        active_task_state=active_task_state,
                        study_stage=study_stage,
                        assistant_mode=assistant_mode,
                    )
                )
                log.info(
                    "[ASSISTANT_CONTINUATION_CLASSIFIER] conversation_id=%s relation=%s intent=%s "
                    "action=%s confidence=%.2f reason=%s",
                    conversation_id,
                    continuation_decision.relation,
                    continuation_decision.resolved_intent or "",
                    continuation_decision.continuation_action,
                    continuation_decision.confidence,
                    continuation_decision.reason,
                )
                if continuation_decision.relation in {
                    "continue_previous_task",
                    "modify_previous_output",
                    "clarify_previous_task",
                }:
                    routed_message = _build_continuation_routing_message(
                        message,
                        decision=continuation_decision,
                        state=active_task_state,
                    )

            contextual_message = build_contextual_user_message(
                routed_message,
                study_stage=study_stage,
                assistant_mode=assistant_mode,
            )
            agent_input = build_input_items(contextual_message, attachments) if has_attachments else contextual_message
            result = await run_agent_session(
                agent=agent,
                agent_input=agent_input,
                conversation_id=conversation_id,
                session_db_path=self.session_db_path,
                use_session=use_session,
                run_context=AssistantRunContext(
                    conversation_id=conversation_id,
                    study_stage=study_stage,
                    assistant_mode=assistant_mode,
                ),
                trace_workflow_name="PEAI Legacy Assistant Chat",
                trace_metadata=self._legacy_chat_trace_metadata(
                    conversation_id=conversation_id,
                    study_stage=study_stage,
                    assistant_mode=assistant_mode,
                    has_attachments=has_attachments,
                    use_session=use_session,
                ),
            )

            run_items = result.run_items
            log.info(
                "[ASSISTANT_RUN_ITEMS] conversation_id=%s agent=%s new_items=%s tool_calls=%s "
                "tool_names=%s handoffs=%s raw_responses=%s response_ids=%s last_response_id=%s",
                conversation_id,
                result.agent_name,
                run_items.new_items_count,
                run_items.tool_call_count,
                run_items.tool_names,
                run_items.handoff_count,
                run_items.raw_response_count,
                run_items.response_ids,
                run_items.last_response_id,
            )

            usage = result.usage
            log.info(
                "[ASSISTANT_PROMPT_CACHE] conversation_id=%s agent=%s requests=%s input_tokens=%s "
                "cached_input_tokens=%s prompt_cache_hit=%s prompt_cache_hit_rate=%.2f output_tokens=%s reasoning_tokens=%s "
                "total_tokens=%s use_session=%s has_attachments=%s",
                conversation_id,
                result.agent_name,
                usage.requests,
                usage.input_tokens,
                usage.cached_input_tokens,
                usage.prompt_cache_hit,
                usage.prompt_cache_hit_rate,
                usage.output_tokens,
                usage.reasoning_tokens,
                usage.total_tokens,
                use_session,
                has_attachments,
            )
            log.info(
                "[OPENAI_AGENTS_RUN] workflow=assistant conversation_id=%s model=%s agent=%s "
                "requests=%s input_tokens=%s input_cached_tokens=%s prompt_cache_hit=%s "
                "output_tokens=%s reasoning_tokens=%s total_tokens=%s tool_calls=%s tool_names=%s "
                "handoffs=%s raw_responses=%s response_ids=%s response_models=%s last_response_id=%s",
                conversation_id,
                self.model,
                result.agent_name,
                usage.requests,
                usage.input_tokens,
                usage.cached_input_tokens,
                usage.prompt_cache_hit,
                usage.output_tokens,
                usage.reasoning_tokens,
                usage.total_tokens,
                run_items.tool_call_count,
                run_items.tool_names,
                run_items.handoff_count,
                run_items.raw_response_count,
                run_items.response_ids,
                run_items.response_models,
                run_items.last_response_id,
            )

            if not result.final_output:
                raise AssistantConfigError("学习助手没有返回内容。")

            self._save_active_task_state(
                conversation_id=conversation_id,
                message=message,
                result=result,
            )

            duration_ms = (time.perf_counter() - started_at) * 1000
            log.info(
                "[ASSISTANT_CHAT_DONE] conversation_id=%s agent=%s duration_ms=%.1f reply_chars=%s "
                "total_tokens=%s cached_input_tokens=%s prompt_cache_hit=%s prompt_cache_hit_rate=%.2f",
                conversation_id,
                result.agent_name,
                duration_ms,
                len(result.final_output),
                usage.total_tokens,
                usage.cached_input_tokens,
                usage.prompt_cache_hit,
                usage.prompt_cache_hit_rate,
            )
        except Exception as exc:
            duration_ms = (time.perf_counter() - started_at) * 1000
            log.error(
                "[ASSISTANT_CHAT_ERROR] conversation_id=%s error_type=%s duration_ms=%.1f message=%s",
                conversation_id,
                type(exc).__name__,
                duration_ms,
                str(exc),
                exc_info=True,
            )
            raise

        return AssistantReply(reply=result.final_output, agent_name=result.agent_name)

    def _save_active_task_state(self, *, conversation_id: str, message: str, result) -> None:
        agent_name = result.agent_name or ""
        metadata = _AGENT_TASK_METADATA.get(agent_name)
        if metadata is None:
            return

        self._active_task_store.save(
            ActiveTaskState(
                conversation_id=conversation_id,
                active_intent=metadata.intent,
                active_agent=agent_name,
                task_title=self._build_task_title(metadata.intent),
                task_summary=_summarize_text(message),
                last_user_message=message,
                last_assistant_summary=_summarize_text(result.final_output),
                last_output_type=metadata.output_type,
                continuation_capabilities=metadata.continuation_capabilities,
                turn_id=result.run_items.last_response_id or str(uuid4()),
            )
        )

    def _build_task_title(self, intent: RoutingIntent) -> str:
        titles = {
            "polish": "英语表达润色",
            "sentence_structure": "句子结构分析",
            "vocab": "词汇讲解",
            "translation": "翻译与表达解释",
            "scoring": "英语写作评分",
            "practice_design": "英语练习设计",
            "ability_profile": "英语能力画像解读",
            "learning_planner": "英语学习规划",
        }
        return titles[intent]
