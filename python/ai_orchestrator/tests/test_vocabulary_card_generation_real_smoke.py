from __future__ import annotations

import os
import unittest

from python.ai_orchestrator.schemas.vocabulary_card import VocabularyCardGenerationRequest
from python.ai_orchestrator.services.vocabulary_card_generation import (
    VocabularyCardGenerationService,
)
from python.ai_orchestrator.workflows.vocabulary_card_generation import is_core_complete


def complete_core(term: str) -> dict:
    return {
        "schemaVersion": 1,
        "term": term,
        "phonetics": [{"region": "uk", "text": "test phonetic", "audioUrl": None}],
        "senses": [
            {
                "partOfSpeech": "noun",
                "meanings": [
                    {
                        "definitionEn": "a safe smoke-test definition",
                        "definitionZh": "安全冒烟测试释义",
                    }
                ],
            }
        ],
    }


def request(*, term: str, theme: dict, trace_id: str) -> VocabularyCardGenerationRequest:
    return VocabularyCardGenerationRequest.model_validate(
        {
            "contractVersion": 1,
            "coreSchemaVersion": 1,
            "requestId": f"real-smoke-{term}",
            "traceId": trace_id,
            "timeoutBudgetMs": 45_000,
            "term": term,
            "dictionaryCore": complete_core(term),
            "sourceContext": "",
            "theme": theme,
        }
    )


class VocabularyCardGenerationRealSmokeTest(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls) -> None:
        required = (
            "RUN_VOCABULARY_REAL_MODEL_SMOKE",
            "OPENAI_API_KEY",
            "VOCABULARY_GENERATION_INTERNAL_TOKEN",
        )
        if os.getenv("RUN_VOCABULARY_REAL_MODEL_SMOKE") != "1" or any(
            not os.getenv(name, "").strip() for name in required[1:]
        ):
            raise unittest.SkipTest("real vocabulary model smoke is not explicitly enabled")

        cls.service = VocabularyCardGenerationService.from_env()
        if not cls.service.is_configured():
            raise unittest.SkipTest("real vocabulary model smoke lacks model configuration")

    async def test_basic_dictionary_core_and_custom_theme_complete_without_sensitive_output(self) -> None:
        cases = (
            request(
                term="smoke-basic",
                trace_id="vocab-real-smoke-basic",
                theme={
                    "uid": "theme_system_basic",
                    "version": 1,
                    "name": "Basic",
                    "purpose": "basic vocabulary learning",
                    "promptStrategyKey": "basic-markdown-v1",
                    "contentFormatVersion": 1,
                },
            ),
            request(
                term="smoke-custom",
                trace_id="vocab-real-smoke-custom",
                theme={
                    "uid": "theme_user_real_smoke",
                    "version": 1,
                    "name": "Custom smoke",
                    "purpose": "custom vocabulary learning",
                    "promptStrategyKey": "custom-markdown-v1",
                    "contentFormatVersion": 1,
                },
            ),
        )

        for generation_request in cases:
            with self.subTest(theme_uid=generation_request.theme.uid):
                response = await self.service.generate(generation_request)

                self.assertEqual("complete", response.outcome)
                self.assertEqual(generation_request.term, response.core.term)
                self.assertTrue(is_core_complete(response.core, term=generation_request.term))
                self.assertTrue(bool(response.content_markdown.strip()))
                self.assertEqual("openai", response.generation.provider)
                self.assertEqual(self.service.model, response.generation.model)
                self.assertTrue(bool(response.generation.prompt_version.strip()))
                self.assertEqual(1, response.generation.model_call_count)
                self.assertEqual(generation_request.trace_id, response.generation.trace_id)


if __name__ == "__main__":
    unittest.main()
