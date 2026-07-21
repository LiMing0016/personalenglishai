from __future__ import annotations

from agents import Agent

from ..prompts.resolver import resolve_agent_prompt_kwargs
from ..schemas.vocabulary_import_analysis import VocabularyImportAnalysisModelOutput


def build_vocabulary_import_analysis_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("vocabulary_import_analysis")
    return Agent(
        name="Vocabulary import analysis",
        model=model,
        output_type=VocabularyImportAnalysisModelOutput,
        **prompt_kwargs,
    )

