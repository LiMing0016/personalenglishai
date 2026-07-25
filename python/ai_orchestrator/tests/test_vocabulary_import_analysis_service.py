from __future__ import annotations

import os
import unittest
from unittest.mock import AsyncMock, patch

from python.ai_orchestrator.schemas.vocabulary_import_analysis import (
    VocabularyImportAnalysisRequest,
)
from python.ai_orchestrator.services.vocabulary_import_analysis import (
    VocabularyImportAnalysisService,
)
from python.ai_orchestrator.workflows.vocabulary_import_analysis import (
    VocabularyImportAnalysisError,
)


def request() -> VocabularyImportAnalysisRequest:
    return VocabularyImportAnalysisRequest(
        contractVersion=1,
        traceId="vocab-import-123",
        inputFingerprint="a" * 64,
        language="en",
        text="package",
    )


class VocabularyImportAnalysisServiceTest(unittest.IsolatedAsyncioTestCase):
    def test_from_env_uses_import_configuration_and_exact_default_timeout(self) -> None:
        with patch.dict(
            os.environ,
            {
                "VOCABULARY_IMPORT_ANALYSIS_MODEL": "gpt-test",
                "VOCABULARY_GENERATION_INTERNAL_TOKEN": "internal-token",
                "OPENAI_API_KEY": "test-key",
            },
            clear=True,
        ):
            service = VocabularyImportAnalysisService.from_env()
            self.assertEqual(service.model, "gpt-test")
            self.assertEqual(service.internal_token, "internal-token")
            self.assertEqual(service.timeout_ms, 45_000)
            self.assertTrue(service.is_configured())

    async def test_invalid_timeout_never_builds_or_runs_workflow(self) -> None:
        with patch.dict(
            os.environ,
            {
                "VOCABULARY_IMPORT_ANALYSIS_MODEL": "gpt-test",
                "VOCABULARY_IMPORT_ANALYSIS_TIMEOUT_MS": "45001",
                "OPENAI_API_KEY": "test-key",
            },
            clear=True,
        ):
            service = VocabularyImportAnalysisService.from_env()

        self.assertFalse(service.is_configured())
        with self.assertRaises(VocabularyImportAnalysisError) as raised:
            await service.analyze(request())
        self.assertEqual(raised.exception.code, "IMPORT_ANALYSIS_NOT_CONFIGURED")

    async def test_analyze_delegates_to_single_cached_workflow(self) -> None:
        service = VocabularyImportAnalysisService(
            model="gpt-test",
            internal_token="internal-token",
            timeout_ms=45_000,
        )
        workflow = AsyncMock()
        workflow.analyze.return_value = object()
        service._workflow = workflow

        with patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=False):
            first = await service.analyze(request())
            second = await service.analyze(request())

        self.assertIs(first, workflow.analyze.return_value)
        self.assertIs(second, workflow.analyze.return_value)
        self.assertEqual(workflow.analyze.await_count, 2)


if __name__ == "__main__":
    unittest.main()
