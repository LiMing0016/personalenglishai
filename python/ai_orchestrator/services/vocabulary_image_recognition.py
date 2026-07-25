from __future__ import annotations

import os

from ..prompts.resolver import PromptResolutionError
from ..schemas.vocabulary_image_recognition import (
    VocabularyImageRecognitionRequest,
    VocabularyImageRecognitionResponse,
)
from ..workflows.vocabulary_image_recognition import (
    VocabularyImageRecognitionError,
    VocabularyImageRecognitionWorkflow,
)


DEFAULT_TIMEOUT_MS = 45_000
MIN_TIMEOUT_MS = 1
MAX_TIMEOUT_MS = 45_000


class VocabularyImageRecognitionService:
    def __init__(
        self,
        *,
        model: str,
        internal_token: str = "",
        timeout_ms: int = DEFAULT_TIMEOUT_MS,
        timeout_configuration_valid: bool = True,
    ) -> None:
        self.model = model.strip()
        self.internal_token = internal_token.strip()
        self.timeout_ms = timeout_ms
        self._timeout_configuration_valid = timeout_configuration_valid
        self._workflow: VocabularyImageRecognitionWorkflow | None = None

    @classmethod
    def from_env(cls) -> "VocabularyImageRecognitionService":
        timeout_ms, timeout_configuration_valid = cls._timeout_from_env()
        return cls(
            model=os.getenv("VOCABULARY_IMAGE_RECOGNITION_MODEL", ""),
            internal_token=os.getenv("VOCABULARY_GENERATION_INTERNAL_TOKEN", ""),
            timeout_ms=timeout_ms,
            timeout_configuration_valid=timeout_configuration_valid,
        )

    def is_configured(self) -> bool:
        if not self._has_model_configuration():
            return False
        try:
            self._ensure_workflow()
        except PromptResolutionError:
            return False
        return True

    async def recognize(
        self,
        request: VocabularyImageRecognitionRequest,
    ) -> VocabularyImageRecognitionResponse:
        if not self._has_model_configuration():
            raise VocabularyImageRecognitionError(
                "IMAGE_RECOGNITION_NOT_CONFIGURED",
                False,
            )
        try:
            workflow = self._ensure_workflow()
        except PromptResolutionError:
            workflow = None
        if workflow is None:
            raise VocabularyImageRecognitionError(
                "IMAGE_RECOGNITION_NOT_CONFIGURED",
                False,
            )
        return await workflow.recognize(request)

    def _has_model_configuration(self) -> bool:
        return bool(
            self._timeout_configuration_valid
            and self.model
            and os.getenv("OPENAI_API_KEY", "").strip()
        )

    def _ensure_workflow(self) -> VocabularyImageRecognitionWorkflow:
        if self._workflow is None:
            self._workflow = VocabularyImageRecognitionWorkflow(
                model=self.model,
                timeout_seconds=self.timeout_ms / 1_000,
            )
        return self._workflow

    @staticmethod
    def _timeout_from_env() -> tuple[int, bool]:
        value = os.getenv(
            "VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS",
            str(DEFAULT_TIMEOUT_MS),
        ).strip()
        try:
            timeout_ms = int(value)
        except ValueError:
            return DEFAULT_TIMEOUT_MS, False
        if not MIN_TIMEOUT_MS <= timeout_ms <= MAX_TIMEOUT_MS:
            return timeout_ms, False
        return timeout_ms, True


__all__ = ["VocabularyImageRecognitionError", "VocabularyImageRecognitionService"]
