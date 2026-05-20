from __future__ import annotations

from agents import Agent

from python.ai_orchestrator.prompts.resolver import resolve_agent_prompt_kwargs
from python.ai_orchestrator.schemas.routing import RoutingDecision


def create_route_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("route_decision")
    return Agent(
        name="RouteAgent",
        model=model,
        output_type=RoutingDecision,
        **prompt_kwargs,
    )
