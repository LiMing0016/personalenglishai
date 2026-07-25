from __future__ import annotations

from agents import Agent

from ..prompts.resolver import resolve_agent_prompt_kwargs
from ..schemas.vocabulary_image_recognition import VocabularyImageRecognitionModelOutput


def build_vocabulary_image_recognition_agent(model: str) -> Agent:
    prompt_kwargs = resolve_agent_prompt_kwargs("vocabulary_image_recognition")
    return Agent(
        name="Vocabulary image recognition",
        model=model,
        output_type=VocabularyImageRecognitionModelOutput,
        **prompt_kwargs,
    )
