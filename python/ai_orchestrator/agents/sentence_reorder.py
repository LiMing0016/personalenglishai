from __future__ import annotations

from agents import Agent

from python.ai_orchestrator.prompts.resolver import resolve_agent_prompt_kwargs
from python.ai_orchestrator.schemas.learning_blocks import SentenceReorderGeneration


def create_sentence_reorder_agent(model: str) -> Agent:
    return Agent(
        name="Sentence Reorder Agent",
        model=model,
        output_type=SentenceReorderGeneration,
        **resolve_agent_prompt_kwargs("sentence_reorder", dynamic=True),
    )
