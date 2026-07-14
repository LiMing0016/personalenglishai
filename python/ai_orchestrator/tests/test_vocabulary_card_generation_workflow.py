from __future__ import annotations

import os
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock, patch

from python.ai_orchestrator.schemas.vocabulary_card import (
    VocabularyCardGenerationRequest,
    VocabularyCore,
    VocabularyCoreFallbackOutput,
    VocabularyMarkdownOutput,
)
from python.ai_orchestrator.services.vocabulary_card_generation import (
    VocabularyCardGenerationError,
    VocabularyCardGenerationService,
)
from python.ai_orchestrator.workflows.vocabulary_card_generation import merge_missing_core


def core_payload(*, phonetics: list[dict] | None = None, senses: list[dict] | None = None) -> dict:
    return {
        "schemaVersion": 1,
        "term": "supposed",
        "phonetics": phonetics if phonetics is not None else [{"region": "uk", "text": "səˈpəʊzd", "audioUrl": None}],
        "senses": senses
        if senses is not None
        else [
            {
                "partOfSpeech": "adjective",
                "meanings": [
                    {
                        "definitionEn": "generally believed or expected",
                        "definitionZh": "一般认为的；预期的",
                    }
                ],
            }
        ],
    }


def request(*, core: dict | None = None, strategy: str = "exam-markdown-v1") -> VocabularyCardGenerationRequest:
    return VocabularyCardGenerationRequest.model_validate(
        {
            "contractVersion": 1,
            "coreSchemaVersion": 1,
            "requestId": "job_123:attempt_1",
            "traceId": "vocab-job_123-attempt_1",
            "timeoutBudgetMs": 45_000,
            "term": "supposed",
            "dictionaryCore": core or core_payload(),
            "sourceContext": "It is supposed to be easy.",
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


class VocabularyCardGenerationWorkflowTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self._environment = patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=False)
        self._environment.start()

    def tearDown(self) -> None:
        self._environment.stop()

    def service(self, *, clock: Mock | None = None) -> VocabularyCardGenerationService:
        return VocabularyCardGenerationService(model="test-model", monotonic_clock=clock)

    async def test_complete_core_calls_only_markdown_agent_and_returns_complete_response(self) -> None:
        markdown = VocabularyMarkdownOutput(contentMarkdown="## 考试重点\n\n重点搭配。")

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=markdown)) as run:
            response = await self.service().generate(request())

        self.assertEqual(response.outcome, "complete")
        self.assertEqual(response.content_markdown, markdown.content_markdown)
        self.assertEqual(response.generation.model_call_count, 1)
        run.assert_awaited_once()
        agent, agent_input = run.call_args.args
        self.assertEqual(agent.name, "VocabularyCardMarkdownAgent")
        self.assertNotIn("session", run.call_args.kwargs)
        self.assertEqual(__import__("json").loads(agent_input)["term"], "supposed")

    async def test_incomplete_core_uses_fallback_then_markdown_without_mutating_dictionary_truth(self) -> None:
        original = core_payload(
            phonetics=[],
            senses=[
                {
                    "partOfSpeech": "adjective",
                    "meanings": [{"definitionEn": "trusted definition", "definitionZh": "可信释义"}],
                }
            ],
        )
        fallback = VocabularyCoreFallbackOutput.model_validate(
            core_payload(
                phonetics=[{"region": "us", "text": "different", "audioUrl": None}],
                senses=[
                    {
                        "partOfSpeech": "verb",
                        "meanings": [{"definitionEn": "changed", "definitionZh": "已修改"}],
                    }
                ],
            )
        )
        markdown = VocabularyMarkdownOutput(contentMarkdown="## 考试重点")

        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[SimpleNamespace(final_output=fallback), SimpleNamespace(final_output=markdown)],
        ) as run:
            response = await self.service().generate(request(core=original))

        self.assertEqual(run.await_count, 2)
        self.assertEqual(run.call_args_list[0].args[0].name, "VocabularyCoreFallbackAgent")
        self.assertEqual(run.call_args_list[1].args[0].name, "VocabularyCardMarkdownAgent")
        self.assertEqual(response.core.term, "supposed")
        self.assertEqual(response.core.schema_version, 1)
        self.assertEqual(response.core.phonetics, fallback.phonetics)
        self.assertEqual(response.core.senses, VocabularyCore.model_validate(original).senses)
        self.assertEqual(response.core.senses[0].meanings[0].definition_en, "trusted definition")
        self.assertEqual(response.generation.model_call_count, 2)

    def test_merge_never_overwrites_nonempty_dictionary_core_fields(self) -> None:
        trusted = VocabularyCore.model_validate(core_payload())
        fallback = VocabularyCoreFallbackOutput.model_construct(
            schema_version=2,
            term="different",
            phonetics=[{"region": "us", "text": "different", "audioUrl": None}],
            senses=[
                {
                    "partOfSpeech": "verb",
                    "meanings": [{"definitionEn": "changed", "definitionZh": "已修改"}],
                }
            ],
        )

        merged = merge_missing_core(trusted, fallback)

        self.assertEqual(merged.schema_version, trusted.schema_version)
        self.assertEqual(merged.term, trusted.term)
        self.assertEqual(merged.phonetics, trusted.phonetics)
        self.assertEqual(merged.senses, trusted.senses)
        self.assertEqual(
            merged.senses[0].meanings[0].definition_en,
            trusted.senses[0].meanings[0].definition_en,
        )

    async def test_invalid_fallback_never_generates_markdown_and_raises_core_error(self) -> None:
        invalid_fallback = VocabularyCoreFallbackOutput.model_validate(core_payload(phonetics=[], senses=[]))

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=invalid_fallback)) as run:
            with self.assertRaisesRegex(VocabularyCardGenerationError, "CORE_CONTENT_UNAVAILABLE") as raised:
                await self.service().generate(request(core=core_payload(phonetics=[], senses=[])))

        self.assertEqual(raised.exception.code, "CORE_CONTENT_UNAVAILABLE")
        self.assertTrue(raised.exception.retryable)
        run.assert_awaited_once()

    async def test_invalid_markdown_returns_partial_only_after_valid_core(self) -> None:
        invalid_markdown = VocabularyMarkdownOutput.model_construct(content_markdown="<script>alert('x')</script>")

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=invalid_markdown)):
            response = await self.service().generate(request())

        self.assertEqual(response.outcome, "partial")
        self.assertEqual(response.warning, "markdown_unavailable")
        self.assertEqual(response.content_markdown, "")

    async def test_empty_and_oversized_markdown_return_partial(self) -> None:
        for content in ("", "a" * 20_001):
            with self.subTest(content_length=len(content)):
                invalid_markdown = VocabularyMarkdownOutput.model_construct(content_markdown=content)
                with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=invalid_markdown)):
                    response = await self.service().generate(request())
                self.assertEqual(response.outcome, "partial")

    async def test_unknown_strategy_is_non_retryable_and_makes_no_model_calls(self) -> None:
        unsupported = request().model_copy(
            update={"theme": request().theme.model_copy(update={"prompt_strategy_key": "unknown-markdown-v1"})}
        )

        with patch("agents.Runner.run", new_callable=AsyncMock) as run:
            with self.assertRaisesRegex(VocabularyCardGenerationError, "UNSUPPORTED_PROMPT_STRATEGY") as raised:
                await self.service().generate(unsupported)

        self.assertFalse(raised.exception.retryable)
        run.assert_not_awaited()

    async def test_unsupported_contract_version_is_non_retryable_and_makes_no_model_calls(self) -> None:
        unsupported = request().model_copy(update={"contract_version": 2})

        with patch("agents.Runner.run", new_callable=AsyncMock) as run:
            with self.assertRaisesRegex(VocabularyCardGenerationError, "UNSUPPORTED_CONTRACT_VERSION") as raised:
                await self.service().generate(unsupported)

        self.assertFalse(raised.exception.retryable)
        run.assert_not_awaited()

    async def test_exhausted_monotonic_timeout_budget_is_retryable_and_makes_no_model_calls(self) -> None:
        clock = Mock(side_effect=[100.0, 145.001])

        with patch("agents.Runner.run", new_callable=AsyncMock) as run:
            with self.assertRaisesRegex(VocabularyCardGenerationError, "MODEL_TIMEOUT") as raised:
                await self.service(clock=clock).generate(request())

        self.assertTrue(raised.exception.retryable)
        run.assert_not_awaited()

    async def test_fallback_timeout_and_upstream_errors_are_retryable(self) -> None:
        for failure, expected_code in ((TimeoutError(), "MODEL_TIMEOUT"), (ConnectionError(), "MODEL_UPSTREAM_UNAVAILABLE")):
            with self.subTest(failure=expected_code):
                with patch("agents.Runner.run", new_callable=AsyncMock, side_effect=failure) as run:
                    with self.assertRaisesRegex(VocabularyCardGenerationError, expected_code) as raised:
                        await self.service().generate(request(core=core_payload(phonetics=[], senses=[])))
                self.assertTrue(raised.exception.retryable)
                run.assert_awaited_once()

    async def test_markdown_model_failure_returns_partial_for_valid_core(self) -> None:
        with patch("agents.Runner.run", new_callable=AsyncMock, side_effect=TimeoutError()) as run:
            response = await self.service().generate(request())

        self.assertEqual(response.outcome, "partial")
        self.assertEqual(response.warning, "markdown_unavailable")
        run.assert_awaited_once()

    async def test_run_config_uses_private_trace_with_only_safe_ids_and_counts(self) -> None:
        markdown = VocabularyMarkdownOutput(contentMarkdown="## 考试重点")

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=markdown)) as run:
            await self.service().generate(request())

        config = run.call_args.kwargs["run_config"]
        self.assertEqual(config.workflow_name, "PEAI Vocabulary Card Generation")
        self.assertFalse(config.trace_include_sensitive_data)
        self.assertIsNone(config.group_id)
        self.assertEqual(
            config.trace_metadata,
            {
                "request_id": "job_123:attempt_1",
                "trace_id": "vocab-job_123-attempt_1",
                "theme_uid": "theme_system_exam",
                "theme_version": 1,
                "model_call_number": 1,
            },
        )

    def test_from_env_is_observable_when_model_or_api_key_is_missing(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            service = VocabularyCardGenerationService.from_env()
            self.assertFalse(service.is_configured())

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
