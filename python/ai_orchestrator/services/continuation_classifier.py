from __future__ import annotations

import json
from datetime import datetime, timezone
from importlib.resources import files
from typing import Any, Protocol

from python.ai_orchestrator import prompts
from python.ai_orchestrator.schemas.routing_state import (
    ActiveTaskState,
    ContinuationClassifierInput,
    ContinuationDecision,
)

_CONTEXTUAL_HINTS = (
    "还有",
    "其他方案",
    "再给",
    "再来",
    "换一种",
    "继续",
    "更详细",
    "详细点",
    "简单点",
    "简单一点",
    "高级点",
    "高级一点",
    "按刚才",
    "第二个方案",
    "另一个",
)

_ACKNOWLEDGEMENT_HINTS = (
    "不错",
    "很好",
    "挺好",
    "可以",
    "明白",
    "懂了",
    "谢谢",
    "收到",
)

_NEW_TASK_HINTS = (
    "润色",
    "翻译",
    "这个单词",
    "这个短语",
    "分析这个句子",
    "分析句子",
    "给这段作文评分",
    "评分",
    "出几道",
    "出题",
    "制定一个",
    "规划一下",
)


class ContinuationClassifierClient(Protocol):
    async def classify(self, prompt: str, payload: ContinuationClassifierInput) -> dict[str, Any] | ContinuationDecision:
        ...


class AgentsSdkContinuationClassifierClient:
    def __init__(self, model: str) -> None:
        self._model = model

    async def classify(self, prompt: str, payload: ContinuationClassifierInput) -> dict[str, Any] | ContinuationDecision:
        from agents import Agent
        from agents import Runner

        classifier_agent = Agent(
            name="Continuation Classifier",
            model=self._model,
            instructions=prompt,
            output_type=ContinuationDecision,
        )
        result = await Runner.run(
            classifier_agent,
            json.dumps(payload.model_dump(mode="json"), ensure_ascii=False),
        )
        return getattr(result, "final_output", None)


def load_continuation_classifier_prompt() -> str:
    prompt_asset = files(prompts).joinpath("shared/continuation_classifier.md")
    return prompt_asset.read_text(encoding="utf-8")


def should_run_continuation_classifier(
    message: str,
    state: ActiveTaskState | None,
    *,
    now: datetime | None = None,
) -> bool:
    normalized = message.strip()
    if not normalized or state is None or state.status != "active":
        return False

    current_time = now or datetime.now(timezone.utc)
    if state.expires_at is not None and state.expires_at <= current_time:
        return False

    if any(hint in normalized for hint in _NEW_TASK_HINTS):
        return False

    if any(hint in normalized for hint in _ACKNOWLEDGEMENT_HINTS):
        return False

    return any(hint in normalized for hint in _CONTEXTUAL_HINTS)


class ContinuationClassifier:
    def __init__(self, llm_client: ContinuationClassifierClient | None) -> None:
        self._llm_client = llm_client
        self._prompt = load_continuation_classifier_prompt()

    async def classify(self, payload: ContinuationClassifierInput) -> ContinuationDecision:
        if not should_run_continuation_classifier(payload.current_user_message, payload.active_task_state):
            return self._skipped_decision(payload)

        if self._llm_client is None:
            return ContinuationDecision(
                relation="ambiguous",
                resolved_intent=None,
                continuation_action="none",
                reason="continuation classifier client is not configured",
                confidence=0.0,
            )

        try:
            raw_decision = await self._llm_client.classify(self._prompt, payload)
            decision = (
                raw_decision
                if isinstance(raw_decision, ContinuationDecision)
                else ContinuationDecision.model_validate(raw_decision)
            )
        except Exception:
            return ContinuationDecision(
                relation="ambiguous",
                resolved_intent=None,
                continuation_action="none",
                reason="classifier output validation failed",
                confidence=0.0,
            )

        return self._enforce_state_invariants(decision, payload.active_task_state)

    def _skipped_decision(self, payload: ContinuationClassifierInput) -> ContinuationDecision:
        if payload.active_task_state is None and payload.current_user_message.strip():
            return ContinuationDecision(
                relation="ambiguous",
                resolved_intent=None,
                continuation_action="none",
                reason="no active task state is available for continuation",
                confidence=0.0,
            )

        return ContinuationDecision(
            relation="new_task",
            resolved_intent=None,
            continuation_action="none",
            reason="classifier skipped by cheap precheck",
            confidence=1.0,
        )

    def _enforce_state_invariants(
        self,
        decision: ContinuationDecision,
        state: ActiveTaskState | None,
    ) -> ContinuationDecision:
        continuation_relations = {
            "continue_previous_task",
            "modify_previous_output",
            "clarify_previous_task",
        }
        if decision.relation not in continuation_relations:
            return decision

        if state is None:
            return ContinuationDecision(
                relation="ambiguous",
                resolved_intent=None,
                continuation_action="none",
                reason="classifier selected continuation without active task state",
                confidence=0.0,
            )

        if decision.resolved_intent is None:
            return decision.model_copy(update={"resolved_intent": state.active_intent})

        return decision
