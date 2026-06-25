from __future__ import annotations

from agents import Agent

from ..prompts.resolver import resolve_agent_prompt_kwargs


def create_learning_asset_copilot_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("learning_asset_copilot")
    return Agent(
        name="Learning Asset Copilot Agent",
        model=model,
        **prompt_kwargs,
    )
