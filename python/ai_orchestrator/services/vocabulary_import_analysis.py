from __future__ import annotations

import os

from ..prompts.resolver import PromptResolutionError
from ..schemas.vocabulary_import_analysis import (
    VocabularyImportAnalysisRequest,
    VocabularyImportAnalysisResponse,
)
from ..workflows.vocabulary_import_analysis import (
    VocabularyImportAnalysisError,
    VocabularyImportAnalysisWorkflow,
)


DEFAULT_TIMEOUT_MS = 45_000
MIN_TIMEOUT_MS = 1
MAX_TIMEOUT_MS = 45_000


class VocabularyImportAnalysisService:
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
        self._workflow: VocabularyImportAnalysisWorkflow | None = None

    @classmethod
    def from_env(cls) -> "VocabularyImportAnalysisService":
        timeout_ms, timeout_configuration_valid = cls._timeout_from_env()
        return cls(
            model=os.getenv(
                "VOCABULARY_IMPORT_ANALYSIS_MODEL",
                os.getenv("VOCABULARY_IMAGE_RECOGNITION_MODEL", ""),
            ),
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

    async def analyze(
        self,
        request: VocabularyImportAnalysisRequest,
    ) -> VocabularyImportAnalysisResponse:
        if not self._has_model_configuration():
            raise VocabularyImportAnalysisError(
                "IMPORT_ANALYSIS_NOT_CONFIGURED",
                False,
            )
        try:
            workflow = self._ensure_workflow()
        except PromptResolutionError:
            workflow = None
        if workflow is None:
            raise VocabularyImportAnalysisError(
                "IMPORT_ANALYSIS_NOT_CONFIGURED",
                False,
            )
        return await workflow.analyze(request)

    def _has_model_configuration(self) -> bool:
        return bool(
            self._timeout_configuration_valid
            and self.model
            and os.getenv("OPENAI_API_KEY", "").strip()
        )

    def _ensure_workflow(self) -> VocabularyImportAnalysisWorkflow:
        if self._workflow is None:
            self._workflow = VocabularyImportAnalysisWorkflow(
                model=self.model,
                timeout_seconds=self.timeout_ms / 1_000,
            )
        return self._workflow

    @staticmethod
    def _timeout_from_env() -> tuple[int, bool]:
        value = os.getenv(
            "VOCABULARY_IMPORT_ANALYSIS_TIMEOUT_MS",
            os.getenv("VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS", str(DEFAULT_TIMEOUT_MS)),
        ).strip()
        try:
            timeout_ms = int(value)
        except ValueError:
            return DEFAULT_TIMEOUT_MS, False
        if not MIN_TIMEOUT_MS <= timeout_ms <= MAX_TIMEOUT_MS:
            return timeout_ms, False
        return timeout_ms, True


__all__ = ["VocabularyImportAnalysisError", "VocabularyImportAnalysisService"]
