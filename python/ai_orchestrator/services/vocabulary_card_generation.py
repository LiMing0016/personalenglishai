from __future__ import annotations

import os
from collections.abc import Callable

from ..prompts.resolver import PromptResolutionError
from ..schemas.vocabulary_card import (
    VocabularyCardGenerationRequest,
    VocabularyCardGenerationResponse,
)
from ..workflows.vocabulary_card_generation import (
    VocabularyCardGenerationError,
    VocabularyCardGenerationWorkflow,
)


class VocabularyCardGenerationService:
    def __init__(
        self,
        *,
        model: str,
        internal_token: str = "",
        monotonic_clock: Callable[[], float] | None = None,
    ) -> None:
        self.model = model.strip()
        self.internal_token = internal_token.strip()
        self._monotonic_clock = monotonic_clock
        self._workflow: VocabularyCardGenerationWorkflow | None = None

    @classmethod
    def from_env(cls) -> "VocabularyCardGenerationService":
        return cls(
            model=os.getenv("VOCABULARY_GENERATION_MODEL", ""),
            internal_token=os.getenv("VOCABULARY_GENERATION_INTERNAL_TOKEN", ""),
        )

    def is_configured(self) -> bool:
        if not self._has_model_configuration():
            return False
        try:
            self._ensure_workflow()
        except PromptResolutionError:
            return False
        return True

    async def generate(
        self,
        request: VocabularyCardGenerationRequest,
    ) -> VocabularyCardGenerationResponse:
        if not self._has_model_configuration():
            raise VocabularyCardGenerationError(
                "VOCABULARY_GENERATION_NOT_CONFIGURED",
                False,
                "Vocabulary generation model configuration is missing.",
            )
        try:
            workflow = self._ensure_workflow()
        except PromptResolutionError as exc:
            raise VocabularyCardGenerationError(
                "VOCABULARY_GENERATION_NOT_CONFIGURED",
                False,
                "Vocabulary generation prompt configuration is invalid.",
            ) from exc
        return await workflow.generate(request)

    def _has_model_configuration(self) -> bool:
        return bool(self.model and os.getenv("OPENAI_API_KEY", "").strip())

    def _ensure_workflow(self) -> VocabularyCardGenerationWorkflow:
        if self._workflow is None:
            self._workflow = VocabularyCardGenerationWorkflow(
                model=self.model,
                monotonic_clock=self._monotonic_clock,
            )
        return self._workflow


__all__ = ["VocabularyCardGenerationError", "VocabularyCardGenerationService"]
