from __future__ import annotations

from agents import Agent

from ..prompts.resolver import resolve_agent_prompt_kwargs
from .specialists import create_specialist_handoffs
from .specialists import create_specialist_tools


def create_router_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("router", dynamic=True)
    return Agent(
        name="Router Agent",
        model=model,
        handoffs=create_specialist_handoffs(model),
        tools=create_specialist_tools(model),
        **prompt_kwargs,
    )
