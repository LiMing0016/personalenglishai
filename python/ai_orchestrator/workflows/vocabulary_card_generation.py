from __future__ import annotations

import asyncio
import json
import time
from collections.abc import Callable
from typing import Any

from agents import RunConfig, Runner

from ..agents.vocabulary_card import (
    create_vocabulary_card_markdown_agent,
    create_vocabulary_core_fallback_agent,
    resolved_prompt_audit_version,
)
from ..schemas.vocabulary_card import (
    MAX_MEANING_COUNT,
    MAX_PHONETIC_COUNT,
    MAX_SENSE_COUNT,
    VocabularyCardGenerationRequest,
    VocabularyCardGenerationResponse,
    VocabularyCore,
    VocabularyCoreFallbackOutput,
    VocabularyGenerationMetadata,
    VocabularyMarkdownOutput,
    VocabularyMeaning,
    VocabularySense,
    validate_markdown_content,
)


WORKFLOW_NAME = "PEAI Vocabulary Card Generation"
CORE_PROMPT_VERSION = "vocabulary-core-fallback-v1"
MARKDOWN_PROMPT_VERSION = "vocabulary-card-markdown-v1"
SUPPORTED_PROMPT_STRATEGIES = frozenset(
    {
        "basic-markdown-v1",
        "exam-markdown-v1",
        "reading-markdown-v1",
        "custom-markdown-v1",
    }
)


class VocabularyCardGenerationError(RuntimeError):
    def __init__(self, code: str, retryable: bool, message: str) -> None:
        super().__init__(f"{code}: {message}")
        self.code = code
        self.retryable = retryable
        self.message = message


def is_core_complete(core: VocabularyCore, *, term: str) -> bool:
    if not term.strip() or not core.term.strip() or core.schema_version != 1 or core.term != term:
        return False
    if not any(phonetic.text.strip() for phonetic in core.phonetics):
        return False
    return any(
        sense.part_of_speech.strip()
        and any(
            meaning.definition_en.strip() or meaning.definition_zh.strip()
            for meaning in sense.meanings
        )
        for sense in core.senses
    )


def _is_blank(value: str | None) -> bool:
    return value is None or not value.strip()


def _fill_blank_scalar(trusted: str, fallback: str) -> str:
    if _is_blank(trusted) and not _is_blank(fallback):
        return fallback
    return trusted


def _fill_blank_optional_scalar(trusted: str | None, fallback: str | None) -> str | None:
    if _is_blank(trusted) and not _is_blank(fallback):
        return fallback
    return trusted


def _normalized_key(value: str) -> str:
    return value.strip().casefold()


def _meanings_overlap(left: VocabularyMeaning, right: VocabularyMeaning) -> bool:
    left_en = _normalized_key(left.definition_en)
    right_en = _normalized_key(right.definition_en)
    left_zh = _normalized_key(left.definition_zh)
    right_zh = _normalized_key(right.definition_zh)
    return bool(left_en and right_en and left_en == right_en) or bool(
        left_zh and right_zh and left_zh == right_zh
    )


def _senses_share_part_of_speech(left: VocabularySense, right: VocabularySense) -> bool:
    left_part_of_speech = _normalized_key(left.part_of_speech)
    right_part_of_speech = _normalized_key(right.part_of_speech)
    return bool(
        left_part_of_speech
        and right_part_of_speech
        and left_part_of_speech == right_part_of_speech
    )


def _senses_have_compatible_part_of_speech(
    left: VocabularySense, right: VocabularySense
) -> bool:
    left_part_of_speech = _normalized_key(left.part_of_speech)
    right_part_of_speech = _normalized_key(right.part_of_speech)
    return (
        not left_part_of_speech
        or not right_part_of_speech
        or left_part_of_speech == right_part_of_speech
    )


def _sense_meanings_overlap(left: VocabularySense, right: VocabularySense) -> bool:
    return any(
        _meanings_overlap(left_meaning, right_meaning)
        for left_meaning in left.meanings
        for right_meaning in right.meanings
    )


def _sense_has_identifiable_meaning(sense: VocabularySense) -> bool:
    return any(
        not _is_blank(meaning.definition_en) or not _is_blank(meaning.definition_zh)
        for meaning in sense.meanings
    )


def _matching_fallback_index(
    *,
    fallback_values: list[Any],
    used_indices: set[int],
    matches_semantically: Callable[[Any], bool],
) -> int | None:
    for fallback_index, fallback_value in enumerate(fallback_values):
        if fallback_index not in used_indices and matches_semantically(fallback_value):
            return fallback_index
    return None


def _matching_fallback_sense_index(
    *,
    trusted_sense: VocabularySense,
    fallback_senses: list[VocabularySense],
    used_indices: set[int],
) -> int | None:
    available = [
        (index, sense)
        for index, sense in enumerate(fallback_senses)
        if index not in used_indices
    ]
    for fallback_index, fallback_sense in available:
        if _senses_have_compatible_part_of_speech(
            trusted_sense, fallback_sense
        ) and _sense_meanings_overlap(trusted_sense, fallback_sense):
            return fallback_index

    same_part_of_speech = [
        (index, sense)
        for index, sense in available
        if _senses_share_part_of_speech(trusted_sense, sense)
    ]
    if len(same_part_of_speech) != 1:
        return None
    fallback_index, fallback_sense = same_part_of_speech[0]
    if _sense_has_identifiable_meaning(trusted_sense) and _sense_has_identifiable_meaning(
        fallback_sense
    ):
        return None
    return fallback_index


def _senses_are_duplicates(left: VocabularySense, right: VocabularySense) -> bool:
    return left == right or (
        _senses_have_compatible_part_of_speech(left, right)
        and _sense_meanings_overlap(left, right)
    )


def merge_missing_core(trusted: VocabularyCore, fallback: VocabularyCore) -> VocabularyCore:
    """Fill blank trusted fields by semantic key and append distinct fallback structures."""
    used_phonetics: set[int] = set()
    phonetics: list[dict[str, Any]] = []
    for trusted_phonetic in trusted.phonetics:
        fallback_index = _matching_fallback_index(
            fallback_values=fallback.phonetics,
            used_indices=used_phonetics,
            matches_semantically=lambda candidate: candidate.region == trusted_phonetic.region,
        )
        fallback_phonetic = (
            fallback.phonetics[fallback_index] if fallback_index is not None else None
        )
        if fallback_index is not None:
            used_phonetics.add(fallback_index)
        phonetics.append(
            {
                "region": trusted_phonetic.region,
                "text": _fill_blank_scalar(
                    trusted_phonetic.text,
                    fallback_phonetic.text if fallback_phonetic else "",
                ),
                "audioUrl": _fill_blank_optional_scalar(
                    trusted_phonetic.audio_url,
                    fallback_phonetic.audio_url if fallback_phonetic else None,
                ),
            }
        )
    known_regions = {phonetic.region for phonetic in trusted.phonetics}
    for fallback_index, phonetic in enumerate(fallback.phonetics):
        if fallback_index in used_phonetics or phonetic.region in known_regions:
            continue
        if len(phonetics) >= MAX_PHONETIC_COUNT:
            break
        phonetics.append(phonetic.model_dump(by_alias=True, mode="json"))
        known_regions.add(phonetic.region)

    used_senses: set[int] = set()
    senses: list[dict[str, Any]] = []
    for trusted_sense in trusted.senses:
        fallback_index = _matching_fallback_sense_index(
            trusted_sense=trusted_sense,
            fallback_senses=fallback.senses,
            used_indices=used_senses,
        )
        fallback_sense = fallback.senses[fallback_index] if fallback_index is not None else None
        if fallback_index is not None:
            used_senses.add(fallback_index)

        used_meanings: set[int] = set()
        meanings: list[dict[str, str]] = []
        fallback_meanings = fallback_sense.meanings if fallback_sense else []
        for trusted_meaning in trusted_sense.meanings:
            fallback_meaning_index = _matching_fallback_index(
                fallback_values=fallback_meanings,
                used_indices=used_meanings,
                matches_semantically=lambda candidate: _meanings_overlap(trusted_meaning, candidate),
            )
            fallback_meaning = (
                fallback_meanings[fallback_meaning_index]
                if fallback_meaning_index is not None
                else None
            )
            if fallback_meaning_index is not None:
                used_meanings.add(fallback_meaning_index)
            meanings.append(
                {
                    "definitionEn": _fill_blank_scalar(
                        trusted_meaning.definition_en,
                        fallback_meaning.definition_en if fallback_meaning else "",
                    ),
                    "definitionZh": _fill_blank_scalar(
                        trusted_meaning.definition_zh,
                        fallback_meaning.definition_zh if fallback_meaning else "",
                    ),
                }
            )
        known_meanings = list(trusted_sense.meanings)
        for fallback_meaning_index, meaning in enumerate(fallback_meanings):
            if fallback_meaning_index in used_meanings or any(
                meaning == known_meaning or _meanings_overlap(meaning, known_meaning)
                for known_meaning in known_meanings
            ):
                continue
            if len(meanings) >= MAX_MEANING_COUNT:
                break
            meanings.append(meaning.model_dump(by_alias=True, mode="json"))
            known_meanings.append(meaning)
        senses.append(
            {
                "partOfSpeech": _fill_blank_scalar(
                    trusted_sense.part_of_speech,
                    fallback_sense.part_of_speech if fallback_sense else "",
                ),
                "meanings": meanings,
            }
        )
    known_senses = list(trusted.senses)
    for fallback_index, sense in enumerate(fallback.senses):
        if fallback_index in used_senses or any(
            _senses_are_duplicates(sense, known_sense) for known_sense in known_senses
        ):
            continue
        if len(senses) >= MAX_SENSE_COUNT:
            break
        senses.append(sense.model_dump(by_alias=True, mode="json"))
        known_senses.append(sense)

    return VocabularyCore.model_validate(
        {
            "schemaVersion": trusted.schema_version,
            "term": trusted.term,
            "phonetics": phonetics,
            "senses": senses,
        }
    )


def serialize_core_fallback_input(request: VocabularyCardGenerationRequest) -> str:
    return json.dumps(
        {
            "term": request.term,
            "dictionaryCore": request.dictionary_core.model_dump(by_alias=True, mode="json"),
            "sourceContext": request.source_context,
        },
        ensure_ascii=False,
        sort_keys=True,
    )


def serialize_markdown_input(
    request: VocabularyCardGenerationRequest,
    core: VocabularyCore,
) -> str:
    return json.dumps(
        {
            "term": request.term,
            "core": core.model_dump(by_alias=True, mode="json"),
            "sourceContext": request.source_context,
            "theme": request.theme.model_dump(by_alias=True, mode="json"),
        },
        ensure_ascii=False,
        sort_keys=True,
    )


class VocabularyCardGenerationWorkflow:
    def __init__(
        self,
        *,
        model: str,
        monotonic_clock: Callable[[], float] | None = None,
    ) -> None:
        self._model = model
        self._clock = monotonic_clock or time.monotonic
        self._core_fallback_agent = create_vocabulary_core_fallback_agent(model)
        self._markdown_agent = create_vocabulary_card_markdown_agent(model)
        self._core_prompt_version = resolved_prompt_audit_version(
            self._core_fallback_agent, CORE_PROMPT_VERSION
        )
        self._markdown_prompt_version = resolved_prompt_audit_version(
            self._markdown_agent, MARKDOWN_PROMPT_VERSION
        )

    async def generate(
        self,
        request: VocabularyCardGenerationRequest,
    ) -> VocabularyCardGenerationResponse:
        self._validate_request_version_and_strategy(request)
        started_at = self._clock()
        model_call_count = 0
        core = self._validate_core(request.dictionary_core)

        if not is_core_complete(core, term=request.term):
            self._require_remaining_budget(request.timeout_budget_ms, started_at)
            fallback_output = await self._run_agent(
                agent=self._core_fallback_agent,
                agent_input=serialize_core_fallback_input(request),
                request=request,
                model_call_number=1,
                started_at=started_at,
            )
            model_call_count = 1
            fallback_core = self._validate_fallback_output(fallback_output)
            try:
                core = merge_missing_core(core, fallback_core)
            except Exception as exc:
                raise VocabularyCardGenerationError(
                    "CORE_CONTENT_UNAVAILABLE",
                    True,
                    "The vocabulary core could not be merged safely.",
                ) from exc
            if not is_core_complete(core, term=request.term):
                raise VocabularyCardGenerationError(
                    "CORE_CONTENT_UNAVAILABLE",
                    True,
                    "The vocabulary core remains incomplete after fallback.",
                )

        markdown_call_number = model_call_count + 1
        self._require_remaining_budget(request.timeout_budget_ms, started_at)
        markdown_call_started = False

        def mark_markdown_call_started() -> None:
            nonlocal markdown_call_started
            markdown_call_started = True

        try:
            markdown_output = await self._run_agent(
                agent=self._markdown_agent,
                agent_input=serialize_markdown_input(request, core),
                request=request,
                model_call_number=markdown_call_number,
                started_at=started_at,
                on_start=mark_markdown_call_started,
            )
            model_call_count = markdown_call_number
            content_markdown = self._validate_markdown_output(markdown_output)
        except asyncio.CancelledError:
            raise
        except Exception:
            if not markdown_call_started:
                raise
            return self._partial_response(request, core, markdown_call_number)

        return VocabularyCardGenerationResponse(
            contractVersion=1,
            coreSchemaVersion=1,
            core=core,
            contentMarkdown=content_markdown,
            contentFormatVersion=1,
            outcome="complete",
            warning=None,
            generation=self._metadata(request, model_call_count),
        )

    def _validate_request_version_and_strategy(
        self,
        request: VocabularyCardGenerationRequest,
    ) -> None:
        if request.contract_version != 1 or request.core_schema_version != 1:
            raise VocabularyCardGenerationError(
                "UNSUPPORTED_CONTRACT_VERSION",
                False,
                "The vocabulary generation contract version is unsupported.",
            )
        if request.theme.content_format_version != 1:
            raise VocabularyCardGenerationError(
                "UNSUPPORTED_CONTENT_FORMAT_VERSION",
                False,
                "The vocabulary content format version is unsupported.",
            )
        if request.theme.prompt_strategy_key not in SUPPORTED_PROMPT_STRATEGIES:
            raise VocabularyCardGenerationError(
                "UNSUPPORTED_PROMPT_STRATEGY",
                False,
                "The vocabulary prompt strategy is unsupported.",
            )

    def _validate_core(self, core: VocabularyCore) -> VocabularyCore:
        try:
            return VocabularyCore.model_validate(core.model_dump(by_alias=True, mode="json"))
        except Exception as exc:
            raise VocabularyCardGenerationError(
                "CORE_CONTENT_UNAVAILABLE",
                True,
                "The dictionary core is invalid.",
            ) from exc

    def _validate_fallback_output(self, output: Any) -> VocabularyCore:
        if not isinstance(output, VocabularyCoreFallbackOutput):
            raise VocabularyCardGenerationError(
                "CORE_CONTENT_UNAVAILABLE",
                True,
                "The core fallback returned invalid structured output.",
            )
        try:
            return VocabularyCore.model_validate(output.model_dump(by_alias=True, mode="json"))
        except Exception as exc:
            raise VocabularyCardGenerationError(
                "CORE_CONTENT_UNAVAILABLE",
                True,
                "The core fallback returned invalid structured output.",
            ) from exc

    def _validate_markdown_output(self, output: Any) -> str:
        if not isinstance(output, VocabularyMarkdownOutput):
            raise ValueError("Markdown agent returned invalid structured output")
        content = output.content_markdown
        validate_markdown_content(content, require_nonempty=True)
        if len(content) > 20_000:
            raise ValueError("Markdown output exceeds the maximum length")
        return content

    async def _run_agent(
        self,
        *,
        agent: Any,
        agent_input: str,
        request: VocabularyCardGenerationRequest,
        model_call_number: int,
        started_at: float,
        on_start: Callable[[], None] | None = None,
    ) -> Any:
        timeout_seconds = self._remaining_timeout_seconds(
            request.timeout_budget_ms,
            started_at,
        )
        try:
            run = Runner.run(
                agent,
                agent_input,
                run_config=RunConfig(
                    workflow_name=WORKFLOW_NAME,
                    trace_include_sensitive_data=False,
                    trace_metadata={
                        "request_id": request.request_id,
                        "trace_id": request.trace_id,
                        "model_call_number": model_call_number,
                    },
                ),
            )
            if on_start is not None:
                on_start()
            result = await asyncio.wait_for(
                run,
                timeout=timeout_seconds,
            )
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            raise self._map_model_error(exc) from None
        return getattr(result, "final_output", None)

    def _remaining_timeout_seconds(self, timeout_budget_ms: int, started_at: float) -> float:
        elapsed_ms = (self._clock() - started_at) * 1_000
        remaining_ms = timeout_budget_ms - elapsed_ms
        if remaining_ms <= 0:
            raise VocabularyCardGenerationError(
                "MODEL_TIMEOUT",
                True,
                "The vocabulary generation timeout budget is exhausted.",
            )
        return remaining_ms / 1_000

    def _require_remaining_budget(self, timeout_budget_ms: int, started_at: float) -> None:
        self._remaining_timeout_seconds(timeout_budget_ms, started_at)

    def _map_model_error(self, exc: Exception) -> VocabularyCardGenerationError:
        if isinstance(exc, TimeoutError) or exc.__class__.__name__ in {"APITimeoutError", "TimeoutException"}:
            return VocabularyCardGenerationError("MODEL_TIMEOUT", True, "The model request timed out.")
        if isinstance(exc, ConnectionError) or exc.__class__.__name__ in {
            "APIConnectionError",
            "APIStatusError",
            "InternalServerError",
            "RateLimitError",
        }:
            return VocabularyCardGenerationError(
                "MODEL_UPSTREAM_UNAVAILABLE",
                True,
                "The model upstream is unavailable.",
            )
        return VocabularyCardGenerationError(
            "GENERATION_INTERNAL_ERROR",
            True,
            "The vocabulary generation encountered an internal error.",
        )

    def _partial_response(
        self,
        request: VocabularyCardGenerationRequest,
        core: VocabularyCore,
        model_call_count: int,
    ) -> VocabularyCardGenerationResponse:
        return VocabularyCardGenerationResponse(
            contractVersion=1,
            coreSchemaVersion=1,
            core=core,
            contentMarkdown="",
            contentFormatVersion=1,
            outcome="partial",
            warning="markdown_unavailable",
            generation=self._metadata(request, model_call_count),
        )

    def _metadata(
        self,
        request: VocabularyCardGenerationRequest,
        model_call_count: int,
    ) -> VocabularyGenerationMetadata:
        prompt_version = self._markdown_prompt_version
        if model_call_count == 2:
            prompt_version = (
                f"core={self._core_prompt_version};"
                f"markdown={self._markdown_prompt_version}"
            )
        return VocabularyGenerationMetadata(
            provider="openai",
            model=self._model,
            promptVersion=prompt_version,
            modelCallCount=model_call_count,
            traceId=request.trace_id,
        )
