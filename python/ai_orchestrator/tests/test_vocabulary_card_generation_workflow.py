from __future__ import annotations

import asyncio
import json
import os
import time
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock, patch

from python.ai_orchestrator.schemas.vocabulary_card import (
    VocabularyCardBlocks,
    VocabularyCardGenerationRequest,
    VocabularyCoreFallbackOutput,
)
from python.ai_orchestrator.services.vocabulary_card_generation import (
    VocabularyCardGenerationError,
    VocabularyCardGenerationService,
)


def core_payload(
    *,
    term: str = "anthropic",
    audio_url: str | None = "https://trusted.example/audio.mp3",
) -> dict[str, object]:
    return {
        "schemaVersion": 2,
        "term": term,
        "phonetics": [
            {"region": "uk", "text": "anˈθrɒpɪk", "audioUrl": audio_url}
        ],
        "senses": [
            {
                "id": "sense_adjective_01",
                "partOfSpeech": "adjective",
                "meanings": [
                    {
                        "id": "meaning_human_01",
                        "definitionEn": "related to human existence or influence",
                        "definitionZh": "与人类存在或影响有关的",
                    }
                ],
            }
        ],
    }


def blocks_payload() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "blocks": [
            {
                "id": "block_examples_01",
                "type": "exampleList",
                "title": "常用例句",
                "meaningRefs": ["meaning_human_01"],
                "format": "structured",
                "content": {
                    "items": [
                        {
                            "sentence": "The anthropic principle concerns human existence.",
                            "translation": "人择原理关注人类存在。",
                        }
                    ]
                },
                "source": "ai",
                "sourceRef": None,
                "sortOrder": 10,
                "userEdited": False,
                "locked": False,
            }
        ],
    }


def request(*, strategy: str = "exam-blocks-v1") -> VocabularyCardGenerationRequest:
    return VocabularyCardGenerationRequest.model_validate(
        {
            "contractVersion": 2,
            "coreSchemaVersion": 2,
            "cardBlocksSchemaVersion": 1,
            "requestId": "job_123:attempt_1",
            "traceId": "vocab-job_123-attempt_1",
            "timeoutBudgetMs": 45_000,
            "term": "anthropic",
            "dictionaryCore": core_payload(),
            "sourceContext": "The anthropic principle is discussed here.",
            "theme": {
                "uid": "theme_system_exam",
                "version": 1,
                "name": "Exam",
                "purpose": "用于考试词义、搭配和易错点学习",
                "promptStrategyKey": strategy,
                "contentFormatVersion": 1,
            },
        }
    )


def core_output(*, audio_url: str | None = "https://untrusted.example/fake.mp3") -> VocabularyCoreFallbackOutput:
    return VocabularyCoreFallbackOutput.model_validate(
        core_payload(audio_url=audio_url)
    )


def blocks_output() -> VocabularyCardBlocks:
    return VocabularyCardBlocks.model_validate(blocks_payload())


class VocabularyCardGenerationWorkflowTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self._environment = patch.dict(
            os.environ,
            {"OPENAI_API_KEY": "test-key", "AI_ASSISTANT_PROMPT_SOURCE": "local"},
            clear=False,
        )
        self._environment.start()

    def tearDown(self) -> None:
        self._environment.stop()

    def service(self, *, clock: Mock | None = None) -> VocabularyCardGenerationService:
        return VocabularyCardGenerationService(model="test-model", monotonic_clock=clock)

    async def test_generation_always_runs_core_then_blocks(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[
                SimpleNamespace(final_output=core_output()),
                SimpleNamespace(final_output=blocks_output()),
            ],
        ) as run:
            response = await self.service().generate(request())

        self.assertEqual(response.outcome, "complete")
        self.assertEqual(response.generation.model_call_count, 2)
        self.assertEqual(response.core.schema_version, 2)
        self.assertEqual(response.card_blocks.schema_version, 1)
        self.assertEqual(run.await_count, 2)
        self.assertEqual(run.call_args_list[0].args[0].name, "VocabularyCoreAgent")
        self.assertEqual(run.call_args_list[1].args[0].name, "VocabularyCardBlocksAgent")

    async def test_theme_reaches_only_blocks_input(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[
                SimpleNamespace(final_output=core_output()),
                SimpleNamespace(final_output=blocks_output()),
            ],
        ) as run:
            await self.service().generate(request())

        core_input = json.loads(run.call_args_list[0].args[1])
        blocks_input = json.loads(run.call_args_list[1].args[1])
        self.assertNotIn("theme", core_input)
        self.assertEqual(blocks_input["theme"]["name"], "Exam")
        self.assertEqual(blocks_input["core"]["term"], "anthropic")

    async def test_dictionary_audio_replaces_ai_audio(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[
                SimpleNamespace(final_output=core_output()),
                SimpleNamespace(final_output=blocks_output()),
            ],
        ):
            response = await self.service().generate(request())

        self.assertEqual(
            response.core.phonetics[0].audio_url,
            "https://trusted.example/audio.mp3",
        )

    async def test_ai_audio_is_removed_when_dictionary_has_none(self) -> None:
        no_audio_request = request().model_copy(
            update={
                "dictionary_core": request().dictionary_core.model_copy(
                    update={
                        "phonetics": [
                            request().dictionary_core.phonetics[0].model_copy(
                                update={"audio_url": None}
                            )
                        ]
                    }
                )
            }
        )
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[
                SimpleNamespace(final_output=core_output()),
                SimpleNamespace(final_output=blocks_output()),
            ],
        ):
            response = await self.service().generate(no_audio_request)

        self.assertIsNone(response.core.phonetics[0].audio_url)

    async def test_blocks_failure_returns_partial_after_valid_core(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[SimpleNamespace(final_output=core_output()), TimeoutError()],
        ) as run:
            response = await self.service().generate(request())

        self.assertEqual(response.outcome, "partial")
        self.assertEqual(response.warning, "card_blocks_unavailable")
        self.assertEqual(response.card_blocks.blocks, [])
        self.assertEqual(response.generation.model_call_count, 2)
        self.assertEqual(run.await_count, 2)

    async def test_invalid_blocks_references_return_partial(self) -> None:
        invalid_blocks = blocks_output().model_copy(deep=True)
        invalid_blocks.blocks[0].meaning_refs = ["meaning_missing"]
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[
                SimpleNamespace(final_output=core_output()),
                SimpleNamespace(final_output=invalid_blocks),
            ],
        ):
            response = await self.service().generate(request())

        self.assertEqual(response.outcome, "partial")
        self.assertEqual(response.warning, "card_blocks_unavailable")

    async def test_invalid_core_is_a_retryable_core_error(self) -> None:
        wrong_term = VocabularyCoreFallbackOutput.model_validate(
            core_payload(term="human")
        )
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            return_value=SimpleNamespace(final_output=wrong_term),
        ) as run:
            with self.assertRaisesRegex(
                VocabularyCardGenerationError,
                "CORE_CONTENT_UNAVAILABLE",
            ) as raised:
                await self.service().generate(request())

        self.assertTrue(raised.exception.retryable)
        run.assert_awaited_once()

    async def test_unknown_strategy_is_non_retryable_without_model_calls(self) -> None:
        unsupported = request(strategy="unknown-blocks-v1")
        with patch("agents.Runner.run", new_callable=AsyncMock) as run:
            with self.assertRaisesRegex(
                VocabularyCardGenerationError,
                "UNSUPPORTED_PROMPT_STRATEGY",
            ) as raised:
                await self.service().generate(unsupported)

        self.assertFalse(raised.exception.retryable)
        run.assert_not_awaited()

    async def test_exhausted_budget_is_retryable_without_model_calls(self) -> None:
        clock = Mock(side_effect=[100.0, 145.001])
        with patch("agents.Runner.run", new_callable=AsyncMock) as run:
            with self.assertRaisesRegex(
                VocabularyCardGenerationError,
                "MODEL_TIMEOUT",
            ):
                await self.service(clock=clock).generate(request())
        run.assert_not_awaited()

    async def test_runner_cancellation_propagates(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=asyncio.CancelledError,
        ):
            with self.assertRaises(asyncio.CancelledError):
                await self.service().generate(request())

    async def test_inflight_timeout_uses_remaining_budget(self) -> None:
        async def sleeping_run(*args: object, **kwargs: object) -> SimpleNamespace:
            await asyncio.sleep(0.2)
            return SimpleNamespace(final_output=None)

        short_request = request().model_copy(update={"timeout_budget_ms": 10})
        started_at = time.monotonic()
        with patch("agents.Runner.run", new=sleeping_run):
            with self.assertRaisesRegex(VocabularyCardGenerationError, "MODEL_TIMEOUT"):
                await self.service().generate(short_request)
        self.assertLess(time.monotonic() - started_at, 0.12)

    async def test_run_config_excludes_sensitive_inputs(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[
                SimpleNamespace(final_output=core_output()),
                SimpleNamespace(final_output=blocks_output()),
            ],
        ) as run:
            await self.service().generate(request())

        for index, call in enumerate(run.call_args_list, start=1):
            config = call.kwargs["run_config"]
            self.assertEqual(config.workflow_name, "PEAI Vocabulary Card Generation")
            self.assertFalse(config.trace_include_sensitive_data)
            self.assertEqual(
                config.trace_metadata,
                {
                    "request_id": "job_123:attempt_1",
                    "trace_id": "vocab-job_123-attempt_1",
                    "model_call_number": str(index),
                },
            )

    def test_from_env_is_observable_when_model_or_api_key_is_missing(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            self.assertFalse(VocabularyCardGenerationService.from_env().is_configured())

        with patch.dict(
            os.environ,
            {"OPENAI_API_KEY": "test-key", "VOCABULARY_GENERATION_MODEL": "env-model"},
            clear=True,
        ):
            service = VocabularyCardGenerationService.from_env()
            self.assertTrue(service.is_configured())
            self.assertEqual(service.model, "env-model")


if __name__ == "__main__":
    unittest.main()
