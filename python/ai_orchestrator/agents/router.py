from __future__ import annotations

from agents import Agent

from ..prompts.agents import load_agent_instructions
from .specialists import create_specialist_handoffs
from .specialists import create_specialist_tools


def create_router_agent(model: str) -> Agent:
    return Agent(
        name="Router Agent",
        model=model,
        instructions=load_agent_instructions("router"),
        handoffs=create_specialist_handoffs(model),
        tools=create_specialist_tools(model),
    )
