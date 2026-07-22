from __future__ import annotations

from agents import Agent

from ..prompts.resolver import PromptResolutionError
from ..prompts.resolver import resolve_agent_prompt_kwargs
from ..schemas.vocabulary_card import VocabularyCardBlocks, VocabularyCoreFallbackOutput


def create_vocabulary_core_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("vocabulary_core_fallback")
    return Agent(
        name="VocabularyCoreAgent",
        model=model,
        output_type=VocabularyCoreFallbackOutput,
        **prompt_kwargs,
    )


def create_vocabulary_card_blocks_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("vocabulary_card_blocks")
    return Agent(
        name="VocabularyCardBlocksAgent",
        model=model,
        output_type=VocabularyCardBlocks,
        **prompt_kwargs,
    )


def resolved_prompt_audit_version(agent: Agent, local_version: str) -> str:
    prompt = getattr(agent, "prompt", None)
    if prompt is None:
        return local_version
    if not isinstance(prompt, dict):
        raise PromptResolutionError("remote vocabulary prompt metadata is invalid")
    prompt_id = str(prompt.get("id", "")).strip()
    version = str(prompt.get("version", "")).strip()
    if not prompt_id or not version:
        raise PromptResolutionError(
            "remote vocabulary prompts require a pinned prompt ID and version"
        )
    audit_version = f"openai:{prompt_id}@{version}"
    if len(audit_version) > 90:
        raise PromptResolutionError("remote vocabulary prompt audit version is too long")
    return audit_version
