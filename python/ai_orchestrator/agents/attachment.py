from __future__ import annotations

from agents import Agent

from ..prompts.agents import load_agent_instructions


def create_attachment_agent(model: str) -> Agent:
    return Agent(
        name="Attachment Agent",
        model=model,
        instructions=load_agent_instructions("attachment"),
    )
