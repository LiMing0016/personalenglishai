from __future__ import annotations

import json
import secrets
from dataclasses import dataclass
from typing import Any

from agents import RunConfig

from python.ai_orchestrator.agents.sentence_reorder import create_sentence_reorder_agent
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.schemas.learning_blocks import (
    AssistantLearningBlock,
    SentenceReorderBlock,
    SentenceReorderData,
    SentenceReorderGeneration,
    SentenceReorderItem,
    SentenceReorderToken,
)
from python.ai_orchestrator.services.agent_session_runner import (
    AgentSessionRunItems,
    AgentSessionUsage,
    extract_run_items,
    extract_usage,
)


@dataclass(slots=True)
class SentenceReorderWorkflowResult:
    content: str
    parts: list[AssistantLearningBlock]
    usage: AgentSessionUsage
    run_items: AgentSessionRunItems


class SentenceReorderWorkflow:
    def __init__(self, model: str, *, runner=None, random_source=None) -> None:
        self._model = model
        self._agent = create_sentence_reorder_agent(model)
        self._runner = runner
        self._random = random_source or secrets.SystemRandom()

    async def generate(
        self,
        request: AssistantRequest,
        *,
        flush_trace: bool = True,
    ) -> SentenceReorderWorkflowResult:
        if self._runner is None:
            from agents import Runner

            runner = Runner
        else:
            runner = self._runner

        result = await runner.run(
            self._agent,
            self._build_agent_input(request),
            run_config=RunConfig(
                workflow_name="PEAI Sentence Reorder",
                group_id=request.app_conversation_id or request.client_message_id,
                trace_metadata={
                    "component": "sentence_reorder_workflow",
                    "study_stage": request.study_context.study_stage if request.study_context else "",
                },
            ),
        )
        try:
            generation = self._parse_generation(getattr(result, "final_output", None))
            block = self._build_block(request, generation)
            return SentenceReorderWorkflowResult(
                content=generation.intro,
                parts=[block],
                usage=extract_usage(result),
                run_items=extract_run_items(result),
            )
        finally:
            if flush_trace:
                self._flush_trace_export()

    def _build_agent_input(self, request: AssistantRequest) -> str:
        interaction_context = request.interaction.context if request.interaction else None
        payload = {
            "message": request.message.text or "",
            "studyStage": request.study_context.study_stage if request.study_context else None,
            "cefrLevel": request.study_context.cefr_level if request.study_context else None,
            "topic": interaction_context.topic if interaction_context else None,
            "difficulty": interaction_context.difficulty if interaction_context else None,
        }
        return json.dumps(payload, ensure_ascii=False)

    @staticmethod
    def _parse_generation(value: Any) -> SentenceReorderGeneration:
        if isinstance(value, SentenceReorderGeneration):
            return value
        if isinstance(value, dict):
            return SentenceReorderGeneration.model_validate(value)
        raise ValueError("Sentence Reorder Agent returned invalid structured output")

    def _build_block(
        self,
        request: AssistantRequest,
        generation: SentenceReorderGeneration,
    ) -> SentenceReorderBlock:
        items: list[SentenceReorderItem] = []
        fallback_lines = [f"### {generation.intro}"]
        for question_index, question in enumerate(generation.questions, start=1):
            tokens = [
                SentenceReorderToken(id=f"q{question_index}-t{token_index}", text=chunk)
                for token_index, chunk in enumerate(question.chunks, start=1)
            ]
            correct_order = [token.id for token in tokens]
            initial_order = list(correct_order)
            self._random.shuffle(initial_order)
            if initial_order == correct_order:
                initial_order = initial_order[1:] + initial_order[:1]
            items.append(
                SentenceReorderItem(
                    id=f"q{question_index}",
                    instruction=question.instruction,
                    translation=question.translation,
                    tokens=tokens,
                    initialOrder=initial_order,
                    acceptedOrders=[correct_order],
                    explanation=question.explanation,
                    hint=question.hint,
                )
            )
            fallback_lines.append(f"{question_index}. {' '.join(question.chunks)}")

        stable_id = request.client_message_id.replace(" ", "-")
        return SentenceReorderBlock(
            id=f"sentence-reorder-{stable_id}",
            fallbackMarkdown="\n\n".join(fallback_lines),
            data=SentenceReorderData(
                activityId=f"activity-{stable_id}",
                items=items,
            ),
        )

    @staticmethod
    def _flush_trace_export() -> None:
        try:
            from agents import flush_traces

            flush_traces()
        except Exception:
            pass
