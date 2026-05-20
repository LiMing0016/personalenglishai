from __future__ import annotations

from agents import Agent

from ..prompts.resolver import resolve_agent_prompt_kwargs


def create_attachment_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("attachment", dynamic=True)
    return Agent(
        name="Attachment Agent",
        model=model,
        **prompt_kwargs,
    )
