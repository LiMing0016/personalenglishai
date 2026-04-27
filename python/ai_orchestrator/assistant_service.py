from __future__ import annotations

import logging
import os
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence
from uuid import uuid4

try:
    from .adapters.openai_input_items import build_input_items
    from .agents.router import create_router_agent
    from .prompts.user_context import build_contextual_user_message
    from .schemas.chat import AssistantReply
    from .schemas.chat import UploadedAttachment
    from .schemas.routing import RoutingIntent
    from .schemas.routing_state import ActiveTaskState
    from .schemas.routing_state import ContinuationClassifierInput
    from .schemas.routing_state import ContinuationDecision
    from .schemas.routing_state import TaskOutputType
    from .services.active_task_state import ActiveTaskStateStore
    from .services.active_task_state import InMemoryActiveTaskStateStore
    from .services.agent_session_runner import run_agent_session
    from .services.continuation_classifier import AgentsSdkContinuationClassifierClient
    from .services.continuation_classifier import ContinuationClassifier
except ImportError:  # pragma: no cover - script mode fallback
    from adapters.openai_input_items import build_input_items
    from agents.router import create_router_agent
    from prompts.user_context import build_contextual_user_message
    from schemas.chat import AssistantReply
    from schemas.chat import UploadedAttachment
    from schemas.routing import RoutingIntent
    from schemas.routing_state import ActiveTaskState
    from schemas.routing_state import ContinuationClassifierInput
    from schemas.routing_state import ContinuationDecision
    from schemas.routing_state import TaskOutputType
    from services.active_task_state import ActiveTaskStateStore
    from services.active_task_state import InMemoryActiveTaskStateStore
    from services.agent_session_runner import run_agent_session
    from services.continuation_classifier import AgentsSdkContinuationClassifierClient
    from services.continuation_classifier import ContinuationClassifier


class AssistantConfigError(RuntimeError):
    pass


log = logging.getLogger("uvicorn.error")


@dataclass(frozen=True, slots=True)
class AssistantRunContext:
    conversation_id: str


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


def _summarize_text(text: str, *, limit: int = 160) -> str:
    normalized = " ".join(text.split())
    if len(normalized) <= limit:
        return normalized
    return f"{normalized[:limit].rstrip()}..."


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


class AssistantAgentService:
    def __init__(
        self,
        *,
        model: str,
        session_db_path: str,
        active_task_store: ActiveTaskStateStore | None = None,
        continuation_classifier=None,
    ) -> None:
        self.model = model
        self.session_db_path = session_db_path
        self._router_agent = None
        self._active_task_store = active_task_store or InMemoryActiveTaskStateStore()
        self._continuation_classifier = continuation_classifier or ContinuationClassifier(
            AgentsSdkContinuationClassifierClient(model)
        )

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
        return cls(model=model, session_db_path=session_db_path)

    def is_configured(self) -> bool:
        return bool(os.getenv("OPENAI_API_KEY", "").strip())

    def _get_router_agent(self):
        if self._router_agent is not None:
            return self._router_agent

        if not self.is_configured():
            raise AssistantConfigError("OPENAI_API_KEY 未配置，学习助手暂时不可用。")

        self._router_agent = create_router_agent(self.model)
        return self._router_agent

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
            router_agent = self._get_router_agent()
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
                agent=router_agent,
                agent_input=agent_input,
                conversation_id=conversation_id,
                session_db_path=self.session_db_path,
                use_session=use_session,
                run_context=AssistantRunContext(conversation_id=conversation_id),
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
