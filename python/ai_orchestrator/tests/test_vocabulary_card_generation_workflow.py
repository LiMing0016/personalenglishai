from __future__ import annotations

import asyncio
import os
import time
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
        self.assertEqual(response.core.senses[0], VocabularyCore.model_validate(original).senses[0])
        self.assertEqual(response.core.senses[1], fallback.senses[0])
        self.assertEqual(response.core.senses[0].meanings[0].definition_en, "trusted definition")
        self.assertEqual(response.generation.model_call_count, 2)

    def test_merge_never_overwrites_nonempty_dictionary_core_fields(self) -> None:
        trusted = VocabularyCore.model_validate(core_payload())
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
        ).model_copy(
            update={"schema_version": 2, "term": "different"}
        )

        merged = merge_missing_core(trusted, fallback)

        self.assertEqual(merged.schema_version, trusted.schema_version)
        self.assertEqual(merged.term, trusted.term)
        self.assertEqual(merged.phonetics[0], trusted.phonetics[0])
        self.assertEqual(merged.senses[0], trusted.senses[0])
        self.assertEqual(
            merged.senses[0].meanings[0].definition_en,
            trusted.senses[0].meanings[0].definition_en,
        )
        self.assertEqual(merged.phonetics[1], fallback.phonetics[0])
        self.assertEqual(merged.senses[1], fallback.senses[0])

    def test_merge_fills_blank_scalars_and_appends_new_fallback_structures(self) -> None:
        trusted = VocabularyCore.model_validate(
            core_payload(
                phonetics=[
                    {"region": "uk", "text": "", "audioUrl": None},
                    {"region": "us", "text": "trusted us", "audioUrl": None},
                ],
                senses=[
                    {
                        "partOfSpeech": "noun",
                        "meanings": [
                            {"definitionEn": "trusted English", "definitionZh": ""},
                            {"definitionEn": "", "definitionZh": "可信中文"},
                        ],
                    },
                    {
                        "partOfSpeech": "",
                        "meanings": [{"definitionEn": "", "definitionZh": ""}],
                    },
                ],
            )
        )
        fallback = VocabularyCoreFallbackOutput.model_validate(
            core_payload(
                phonetics=[
                    {"region": "uk", "text": "fallback uk", "audioUrl": "https://audio/uk"},
                    {"region": "us", "text": "changed us", "audioUrl": "https://audio/us"},
                    {"region": "other", "text": "new phonetic", "audioUrl": None},
                ],
                senses=[
                    {
                        "partOfSpeech": "noun",
                        "meanings": [
                            {"definitionEn": "trusted English", "definitionZh": "回退中文"},
                            {"definitionEn": "fallback English", "definitionZh": "可信中文"},
                            {"definitionEn": "new noun meaning", "definitionZh": "新名词释义"},
                        ],
                    },
                    {
                        "partOfSpeech": "verb",
                        "meanings": [
                            {"definitionEn": "fallback verb", "definitionZh": "动词释义"},
                            {"definitionEn": "new verb meaning", "definitionZh": "新动词释义"},
                        ],
                    },
                    {
                        "partOfSpeech": "adjective",
                        "meanings": [{"definitionEn": "new sense", "definitionZh": "新词义"}],
                    },
                ],
            )
        )

        merged = merge_missing_core(trusted, fallback)

        self.assertEqual(merged.schema_version, 1)
        self.assertEqual(merged.term, "supposed")
        self.assertEqual(merged.phonetics[0].region, "uk")
        self.assertEqual(merged.phonetics[0].text, "fallback uk")
        self.assertEqual(merged.phonetics[0].audio_url, "https://audio/uk")
        self.assertEqual(merged.phonetics[1].text, "trusted us")
        self.assertEqual(merged.phonetics[1].audio_url, "https://audio/us")
        self.assertEqual(merged.phonetics[2].text, "new phonetic")
        self.assertEqual(merged.senses[0].part_of_speech, "noun")
        self.assertEqual(merged.senses[0].meanings[0].definition_en, "trusted English")
        self.assertEqual(merged.senses[0].meanings[0].definition_zh, "回退中文")
        self.assertEqual(merged.senses[0].meanings[1].definition_en, "fallback English")
        self.assertEqual(merged.senses[0].meanings[1].definition_zh, "可信中文")
        self.assertEqual(merged.senses[0].meanings[2].definition_en, "new noun meaning")
        self.assertEqual(merged.senses[1], trusted.senses[1])
        self.assertEqual(merged.senses[2].part_of_speech, "verb")
        self.assertEqual(merged.senses[2].meanings[0].definition_en, "fallback verb")
        self.assertEqual(merged.senses[2].meanings[1].definition_en, "new verb meaning")
        self.assertEqual(merged.senses[3].part_of_speech, "adjective")

    def test_merge_uses_semantic_keys_without_corrupting_or_duplicating_structures(self) -> None:
        trusted = VocabularyCore.model_validate(
            core_payload(
                phonetics=[
                    {"region": "uk", "text": "", "audioUrl": None},
                    {"region": "us", "text": "", "audioUrl": None},
                ],
                senses=[
                    {
                        "partOfSpeech": "noun",
                        "meanings": [
                            {"definitionEn": "trusted noun", "definitionZh": ""},
                            {"definitionEn": "", "definitionZh": ""},
                        ],
                    },
                    {
                        "partOfSpeech": "verb",
                        "meanings": [{"definitionEn": "trusted verb", "definitionZh": ""}],
                    },
                    {
                        "partOfSpeech": "",
                        "meanings": [{"definitionEn": "shared definition", "definitionZh": ""}],
                    },
                    {
                        "partOfSpeech": "",
                        "meanings": [{"definitionEn": "", "definitionZh": ""}],
                    },
                ],
            )
        )
        fallback = VocabularyCoreFallbackOutput.model_validate(
            core_payload(
                phonetics=[
                    {"region": "us", "text": "fallback us", "audioUrl": None},
                    {"region": "uk", "text": "fallback uk", "audioUrl": None},
                    {"region": "uk", "text": "duplicate uk", "audioUrl": None},
                    {"region": "other", "text": "fallback other", "audioUrl": None},
                ],
                senses=[
                    {
                        "partOfSpeech": "verb",
                        "meanings": [
                            {"definitionEn": "trusted verb", "definitionZh": "动词释义"},
                            {"definitionEn": "new verb meaning", "definitionZh": "新动词释义"},
                        ],
                    },
                    {
                        "partOfSpeech": "noun",
                        "meanings": [
                            {"definitionEn": "trusted noun", "definitionZh": "名词释义"},
                            {"definitionEn": "trusted noun", "definitionZh": "重复名词释义"},
                            {"definitionEn": "new noun meaning", "definitionZh": "新名词释义"},
                        ],
                    },
                    {
                        "partOfSpeech": "adjective",
                        "meanings": [{"definitionEn": "shared definition", "definitionZh": "共享释义"}],
                    },
                    {
                        "partOfSpeech": "adverb",
                        "meanings": [{"definitionEn": "unrelated fallback", "definitionZh": "不相关释义"}],
                    },
                ],
            )
        )

        merged = merge_missing_core(trusted, fallback)

        self.assertEqual(
            [(phonetic.region, phonetic.text) for phonetic in merged.phonetics],
            [("uk", "fallback uk"), ("us", "fallback us"), ("other", "fallback other")],
        )
        self.assertEqual(
            [sense.part_of_speech for sense in merged.senses],
            ["noun", "verb", "adjective", "", "adverb"],
        )
        self.assertEqual(merged.senses[0].meanings[0].definition_zh, "名词释义")
        self.assertEqual(merged.senses[0].meanings[1].definition_en, "")
        self.assertEqual(merged.senses[0].meanings[2].definition_en, "new noun meaning")
        self.assertEqual(merged.senses[1].meanings[0].definition_zh, "动词释义")
        self.assertEqual(merged.senses[1].meanings[1].definition_en, "new verb meaning")
        self.assertEqual(merged.senses[2].meanings[0].definition_zh, "共享释义")
        self.assertEqual(merged.senses[3].meanings[0].definition_en, "")
        self.assertEqual(merged.senses[4].part_of_speech, "adverb")
        self.assertEqual(
            sum(
                meaning.definition_en == "trusted noun"
                for meaning in merged.senses[0].meanings
            ),
            1,
        )

    def test_merge_matches_reordered_same_pos_senses_by_meaning_before_part_of_speech(self) -> None:
        trusted = VocabularyCore.model_validate(
            core_payload(
                senses=[
                    {
                        "partOfSpeech": "noun",
                        "meanings": [{"definitionEn": "first sense", "definitionZh": ""}],
                    },
                    {
                        "partOfSpeech": "noun",
                        "meanings": [{"definitionEn": "second sense", "definitionZh": ""}],
                    },
                ]
            )
        )
        fallback = VocabularyCoreFallbackOutput.model_validate(
            core_payload(
                senses=[
                    {
                        "partOfSpeech": "noun",
                        "meanings": [
                            {"definitionEn": "second sense", "definitionZh": "第二个义项"}
                        ],
                    },
                    {
                        "partOfSpeech": "noun",
                        "meanings": [
                            {"definitionEn": "first sense", "definitionZh": "第一个义项"}
                        ],
                    },
                ]
            )
        )

        merged = merge_missing_core(trusted, fallback)

        self.assertEqual(len(merged.senses), 2)
        self.assertEqual(len(merged.senses[0].meanings), 1)
        self.assertEqual(merged.senses[0].meanings[0].definition_zh, "第一个义项")
        self.assertEqual(len(merged.senses[1].meanings), 1)
        self.assertEqual(merged.senses[1].meanings[0].definition_zh, "第二个义项")

    def test_merge_never_matches_equal_meanings_across_conflicting_parts_of_speech(self) -> None:
        trusted = VocabularyCore.model_validate(
            core_payload(
                senses=[
                    {
                        "partOfSpeech": "noun",
                        "meanings": [{"definitionEn": "record", "definitionZh": ""}],
                    },
                    {
                        "partOfSpeech": "verb",
                        "meanings": [{"definitionEn": "record", "definitionZh": ""}],
                    },
                ]
            )
        )
        fallback = VocabularyCoreFallbackOutput.model_validate(
            core_payload(
                senses=[
                    {
                        "partOfSpeech": "verb",
                        "meanings": [{"definitionEn": "record", "definitionZh": "动词释义"}],
                    },
                    {
                        "partOfSpeech": "noun",
                        "meanings": [{"definitionEn": "record", "definitionZh": "名词释义"}],
                    },
                ]
            )
        )

        merged = merge_missing_core(trusted, fallback)

        self.assertEqual(len(merged.senses), 2)
        self.assertEqual(merged.senses[0].part_of_speech, "noun")
        self.assertEqual(merged.senses[0].meanings[0].definition_zh, "名词释义")
        self.assertEqual(merged.senses[1].part_of_speech, "verb")
        self.assertEqual(merged.senses[1].meanings[0].definition_zh, "动词释义")

    def test_merge_deduplicates_identical_blank_fallback_structures(self) -> None:
        blank_sense = {
            "partOfSpeech": "",
            "meanings": [{"definitionEn": "", "definitionZh": ""}],
        }
        trusted = VocabularyCore.model_validate(core_payload(senses=[blank_sense]))
        fallback = VocabularyCoreFallbackOutput.model_validate(
            core_payload(senses=[blank_sense, blank_sense])
        )

        merged = merge_missing_core(trusted, fallback)

        self.assertEqual(len(merged.senses), 1)
        self.assertEqual(merged.senses[0], trusted.senses[0])

    async def test_merge_capacity_exhaustion_returns_stable_core_content_error(self) -> None:
        blank_sense = {
            "partOfSpeech": "",
            "meanings": [{"definitionEn": "", "definitionZh": ""}],
        }
        original = core_payload(phonetics=[], senses=[blank_sense] * 20)
        fallback = VocabularyCoreFallbackOutput.model_validate(
            core_payload(
                phonetics=[{"region": "uk", "text": "fallback", "audioUrl": None}],
                senses=[
                    {
                        "partOfSpeech": f"part-{index}",
                        "meanings": [
                            {
                                "definitionEn": f"fallback meaning {index}",
                                "definitionZh": f"回退释义 {index}",
                            }
                        ],
                    }
                    for index in range(20)
                ],
            )
        )

        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            return_value=SimpleNamespace(final_output=fallback),
        ) as run:
            with self.assertRaisesRegex(
                VocabularyCardGenerationError, "CORE_CONTENT_UNAVAILABLE"
            ) as raised:
                await self.service().generate(request(core=original))

        self.assertEqual(raised.exception.code, "CORE_CONTENT_UNAVAILABLE")
        self.assertTrue(raised.exception.retryable)
        run.assert_awaited_once()

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
        for failure, expected_code in (
            (TimeoutError(), "MODEL_TIMEOUT"),
            (ConnectionError(), "MODEL_UPSTREAM_UNAVAILABLE"),
            (RuntimeError("local failure"), "GENERATION_INTERNAL_ERROR"),
        ):
            with self.subTest(failure=expected_code):
                with patch("agents.Runner.run", new_callable=AsyncMock, side_effect=failure) as run:
                    with self.assertRaisesRegex(VocabularyCardGenerationError, expected_code) as raised:
                        await self.service().generate(request(core=core_payload(phonetics=[], senses=[])))
                self.assertTrue(raised.exception.retryable)
                run.assert_awaited_once()

    async def test_inflight_fallback_timeout_uses_remaining_monotonic_budget(self) -> None:
        async def sleeping_run(*args: object, **kwargs: object) -> SimpleNamespace:
            await asyncio.sleep(0.2)
            return SimpleNamespace(final_output=None)

        timeout_request = request(core=core_payload(phonetics=[], senses=[])).model_copy(
            update={"timeout_budget_ms": 10}
        )
        started_at = time.monotonic()
        with patch("agents.Runner.run", new=sleeping_run):
            with self.assertRaisesRegex(VocabularyCardGenerationError, "MODEL_TIMEOUT") as raised:
                await self.service().generate(timeout_request)

        self.assertTrue(raised.exception.retryable)
        self.assertLess(time.monotonic() - started_at, 0.12)

    async def test_budget_expiring_before_runner_starts_returns_model_timeout(self) -> None:
        clock = Mock(side_effect=[100.0, 100.0, 145.001])

        with patch("agents.Runner.run", new_callable=AsyncMock) as run:
            with self.assertRaisesRegex(VocabularyCardGenerationError, "MODEL_TIMEOUT") as raised:
                await self.service(clock=clock).generate(
                    request(core=core_payload(phonetics=[], senses=[]))
                )

        self.assertTrue(raised.exception.retryable)
        run.assert_not_awaited()

    async def test_markdown_budget_expiring_before_runner_starts_returns_model_timeout(self) -> None:
        clock = Mock(side_effect=[100.0, 100.0, 145.001])

        with patch("agents.Runner.run", new_callable=AsyncMock) as run:
            with self.assertRaisesRegex(
                VocabularyCardGenerationError, "MODEL_TIMEOUT"
            ) as raised:
                await self.service(clock=clock).generate(request())

        self.assertTrue(raised.exception.retryable)
        run.assert_not_awaited()

    async def test_runner_cancellation_propagates_without_partial_response(self) -> None:
        with patch("agents.Runner.run", new_callable=AsyncMock, side_effect=asyncio.CancelledError):
            with self.assertRaises(asyncio.CancelledError):
                await self.service().generate(request(core=core_payload(phonetics=[], senses=[])))

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
