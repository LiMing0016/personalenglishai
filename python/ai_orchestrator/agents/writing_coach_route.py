from __future__ import annotations

from agents import Agent

from python.ai_orchestrator.prompts.resolver import resolve_agent_prompt_kwargs
from python.ai_orchestrator.schemas.writing_coach import WritingCoachRouteDecision


def create_writing_coach_route_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("writing_coach_route")
    return Agent(
        name="WritingCoachRouteAgent",
        model=model,
        output_type=WritingCoachRouteDecision,
        **prompt_kwargs,
    )
