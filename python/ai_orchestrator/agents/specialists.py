from __future__ import annotations

import logging
import re
from dataclasses import dataclass

from agents import Agent
from agents import RunContextWrapper
from agents import handoff

from ..prompts.resolver import resolve_agent_prompt_kwargs
from ..schemas.routing import HandoffRoutingMetadata


log = logging.getLogger("uvicorn.error")


@dataclass(frozen=True, slots=True)
class SpecialistAgentSpec:
    prompt_key: str
    name: str
    handoff_description: str
    tool_name: str
    tool_description: str


SPECIALIST_AGENT_SPECS: tuple[SpecialistAgentSpec, ...] = (
    SpecialistAgentSpec(
        prompt_key="polish",
        name="Polish Agent",
        handoff_description="Use for polishing, rewriting, and improving English expression while preserving meaning.",
        tool_name="polish_text",
        tool_description="Polish or rewrite English text and explain the most important expression improvements.",
    ),
    SpecialistAgentSpec(
        prompt_key="sentence_structure",
        name="Sentence Structure Agent",
        handoff_description="Use for sentence structure, grammar pattern, clause, and long-sentence analysis.",
        tool_name="analyze_sentence_structure",
        tool_description="Analyze English sentence structure, grammar patterns, clauses, and readability.",
    ),
    SpecialistAgentSpec(
        prompt_key="vocab",
        name="Vocab Agent",
        handoff_description="Use for words, phrases, collocations, usage, nuance, and common mistakes.",
        tool_name="explain_vocab",
        tool_description="Explain English words, phrases, collocations, usage nuance, and common mistakes.",
    ),
    SpecialistAgentSpec(
        prompt_key="translation",
        name="Translation Agent",
        handoff_description="Use for Chinese-English or English-Chinese translation and translation quality explanation.",
        tool_name="translate_text",
        tool_description="Translate text between Chinese and English and explain key expression differences.",
    ),
    SpecialistAgentSpec(
        prompt_key="scoring",
        name="Scoring Agent",
        handoff_description="Use for scoring, evaluating, diagnosing, and giving improvement advice for English writing.",
        tool_name="score_english",
        tool_description="Score or evaluate English writing and diagnose key improvement priorities.",
    ),
    SpecialistAgentSpec(
        prompt_key="prompt_design",
        name="Prompt Design Agent",
        handoff_description="Use for generating English practice tasks, writing prompts, exercises, and training requirements.",
        tool_name="design_practice_prompt",
        tool_description="Design concrete English practice prompts, exercises, and task requirements.",
    ),
    SpecialistAgentSpec(
        prompt_key="ability_profile",
        name="Ability Profile Agent",
        handoff_description="Use when the user asks about their English ability profile, strengths, weaknesses, or current level.",
        tool_name="explain_ability_profile",
        tool_description="Explain a learner's English ability profile from the available context and stage.",
    ),
    SpecialistAgentSpec(
        prompt_key="learning_planner",
        name="Learning Planner Agent",
        handoff_description="Use for learning plans, study paths, priorities, and short-term English training suggestions.",
        tool_name="plan_learning_path",
        tool_description="Create a practical English learning path or short-term study plan from the available context.",
    ),
)


def create_specialist_agent(spec: SpecialistAgentSpec, model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs(spec.prompt_key, dynamic=True)
    return Agent(
        name=spec.name,
        model=model,
        handoff_description=spec.handoff_description,
        **prompt_kwargs,
    )


def _log_handoff(agent_name: str):
    async def on_handoff(ctx: RunContextWrapper[None], input_data: HandoffRoutingMetadata) -> None:
        conversation_id = getattr(getattr(ctx, "context", None), "conversation_id", "")
        log.info(
            "[ASSISTANT_ROUTING] conversation_id=%s handoff agent=%s intent=%s confidence=%.2f reason=%s",
            conversation_id,
            agent_name,
            input_data.intent,
            input_data.confidence,
            input_data.reason,
        )

    return on_handoff


def _handoff_tool_name(agent_name: str) -> str:
    normalized_name = re.sub(r"[^a-z0-9]+", "_", agent_name.lower()).strip("_")
    return f"transfer_to_{normalized_name}"


def create_specialist_agents(model: str) -> list[Agent]:
    return [create_specialist_agent(spec, model) for spec in SPECIALIST_AGENT_SPECS]


def create_specialist_handoffs(model: str):
    return [
        handoff(
            create_specialist_agent(spec, model),
            tool_name_override=_handoff_tool_name(spec.name),
            on_handoff=_log_handoff(spec.name),
            input_type=HandoffRoutingMetadata,
        )
        for spec in SPECIALIST_AGENT_SPECS
    ]


def create_specialist_tools(model: str):
    return [
        create_specialist_agent(spec, model).as_tool(
            tool_name=spec.tool_name,
            tool_description=spec.tool_description,
        )
        for spec in SPECIALIST_AGENT_SPECS
    ]
