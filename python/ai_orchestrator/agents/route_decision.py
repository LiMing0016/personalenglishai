from __future__ import annotations

from agents import Agent

from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.routing import RoutingDecision


def create_route_agent(model: str) -> Agent:
    return Agent(
        name="RouteAgent",
        model=model,
        instructions=load_agent_instructions("route_decision"),
        output_type=RoutingDecision,
    )
