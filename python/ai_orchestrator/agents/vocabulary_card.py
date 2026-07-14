from __future__ import annotations

from agents import Agent

from ..prompts.resolver import resolve_agent_prompt_kwargs
from ..schemas.vocabulary_card import VocabularyCoreFallbackOutput, VocabularyMarkdownOutput


def create_vocabulary_core_fallback_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("vocabulary_core_fallback")
    return Agent(
        name="VocabularyCoreFallbackAgent",
        model=model,
        output_type=VocabularyCoreFallbackOutput,
        **prompt_kwargs,
    )


def create_vocabulary_card_markdown_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("vocabulary_card_markdown")
    return Agent(
        name="VocabularyCardMarkdownAgent",
        model=model,
        output_type=VocabularyMarkdownOutput,
        **prompt_kwargs,
    )
