from __future__ import annotations

import asyncio
import json
import time
from collections.abc import Callable
from typing import Any

from agents import RunConfig, Runner

from ..agents.vocabulary_card import (
    create_vocabulary_card_blocks_agent,
    create_vocabulary_core_agent,
    resolved_prompt_audit_version,
)
from ..schemas.vocabulary_card import (
    VocabularyCardBlocks,
    VocabularyCardGenerationRequest,
    VocabularyCardGenerationResponse,
    VocabularyCore,
    VocabularyCoreFallbackOutput,
    VocabularyGenerationMetadata,
    VocabularyGenerationUsage,
)
from ..services.agent_session_runner import AgentSessionUsage, extract_usage


WORKFLOW_NAME = "PEAI Vocabulary Card Generation"
CORE_PROMPT_VERSION = "vocabulary-core-v2"
CARD_BLOCKS_PROMPT_VERSION = "vocabulary-card-blocks-v1"
SUPPORTED_PROMPT_STRATEGIES = frozenset(
    {
        "basic-blocks-v1",
        "exam-blocks-v1",
        "reading-blocks-v1",
        "custom-blocks-v1",
    }
)


class VocabularyCardGenerationError(RuntimeError):
    def __init__(self, code: str, retryable: bool, message: str) -> None:
        super().__init__(f"{code}: {message}")
        self.code = code
        self.retryable = retryable
        self.message = message


def is_core_complete(core: VocabularyCore, *, term: str) -> bool:
    if core.schema_version != 2 or core.term != term or not term.strip():
        return False
    return any(
        sense.id
        and sense.part_of_speech.strip()
        and any(
            meaning.id
            and meaning.definition_en.strip()
            and meaning.definition_zh.strip()
            for meaning in sense.meanings
        )
        for sense in core.senses
    )


def serialize_core_input(request: VocabularyCardGenerationRequest) -> str:
    return json.dumps(
        {
            "term": request.term,
            "dictionaryCore": request.dictionary_core.model_dump(
                by_alias=True,
                mode="json",
            ),
            "sourceContext": request.source_context,
        },
        ensure_ascii=False,
        sort_keys=True,
    )


def serialize_card_blocks_input(
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


def apply_trusted_dictionary_phonetics(
    generated: VocabularyCore,
    dictionary: VocabularyCore,
) -> VocabularyCore:
    """Keep generated meanings while making dictionary audio and phonetics authoritative."""
    generated_by_region: dict[str, Any] = {}
    for phonetic in generated.phonetics:
        generated_by_region.setdefault(phonetic.region, phonetic)

    phonetics: list[dict[str, Any]] = []
    seen_regions: set[str] = set()
    dictionary_standard_regions = {
        phonetic.region
        for phonetic in dictionary.phonetics
        if phonetic.region in {"uk", "us"} and phonetic.text.strip()
    }

    for trusted in dictionary.phonetics:
        if trusted.region in seen_regions:
            continue
        if trusted.region == "other" and dictionary_standard_regions:
            continue
        generated_match = generated_by_region.get(trusted.region)
        text = trusted.text if trusted.text.strip() else (
            generated_match.text if generated_match is not None else ""
        )
        if not text.strip():
            continue
        phonetics.append(
            {
                "region": trusted.region,
                "text": text,
                "audioUrl": trusted.audio_url,
            }
        )
        seen_regions.add(trusted.region)

    if not phonetics:
        generated_standard_regions = {
            phonetic.region
            for phonetic in generated.phonetics
            if phonetic.region in {"uk", "us"} and phonetic.text.strip()
        }
        for candidate in generated.phonetics:
            if candidate.region in seen_regions or not candidate.text.strip():
                continue
            if candidate.region == "other" and generated_standard_regions:
                continue
            phonetics.append(
                {
                    "region": candidate.region,
                    "text": candidate.text,
                    "audioUrl": None,
                }
            )
            seen_regions.add(candidate.region)

    return VocabularyCore.model_validate(
        {
            **generated.model_dump(by_alias=True, mode="json"),
            "phonetics": phonetics,
        }
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
        self._core_agent = create_vocabulary_core_agent(model)
        self._card_blocks_agent = create_vocabulary_card_blocks_agent(model)
        self._core_prompt_version = resolved_prompt_audit_version(
            self._core_agent,
            CORE_PROMPT_VERSION,
        )
        self._card_blocks_prompt_version = resolved_prompt_audit_version(
            self._card_blocks_agent,
            CARD_BLOCKS_PROMPT_VERSION,
        )

    async def generate(
        self,
        request: VocabularyCardGenerationRequest,
    ) -> VocabularyCardGenerationResponse:
        self._validate_request_version_and_strategy(request)
        started_at = self._clock()
        usages: list[AgentSessionUsage] = []

        self._require_remaining_budget(request.timeout_budget_ms, started_at)
        core_output, core_usage = await self._run_agent(
            agent=self._core_agent,
            agent_input=serialize_core_input(request),
            request=request,
            model_call_number=1,
            started_at=started_at,
        )
        if core_usage is not None:
            usages.append(core_usage)
        core = self._validate_core_output(core_output, request)

        self._require_remaining_budget(request.timeout_budget_ms, started_at)
        blocks_call_started = False

        def mark_blocks_call_started() -> None:
            nonlocal blocks_call_started
            blocks_call_started = True

        try:
            blocks_output, blocks_usage = await self._run_agent(
                agent=self._card_blocks_agent,
                agent_input=serialize_card_blocks_input(request, core),
                request=request,
                model_call_number=2,
                started_at=started_at,
                on_start=mark_blocks_call_started,
            )
            if blocks_usage is not None:
                usages.append(blocks_usage)
            card_blocks = self._validate_card_blocks_output(blocks_output, core)
        except asyncio.CancelledError:
            raise
        except Exception:
            if not blocks_call_started:
                raise
            return self._partial_response(request, core, usages)

        return VocabularyCardGenerationResponse(
            contractVersion=2,
            coreSchemaVersion=2,
            cardBlocksSchemaVersion=1,
            core=core,
            cardBlocks=card_blocks,
            outcome="complete",
            warning=None,
            generation=self._metadata(request, usages),
        )

    def _validate_request_version_and_strategy(
        self,
        request: VocabularyCardGenerationRequest,
    ) -> None:
        if (
            request.contract_version != 2
            or request.core_schema_version != 2
            or request.card_blocks_schema_version != 1
        ):
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

    def _validate_core_output(
        self,
        output: Any,
        request: VocabularyCardGenerationRequest,
    ) -> VocabularyCore:
        if not isinstance(output, VocabularyCoreFallbackOutput):
            raise VocabularyCardGenerationError(
                "CORE_CONTENT_UNAVAILABLE",
                True,
                "The core agent returned invalid structured output.",
            )
        try:
            generated = VocabularyCore.model_validate(
                output.model_dump(by_alias=True, mode="json")
            )
            if not is_core_complete(generated, term=request.term):
                raise ValueError("generated core is incomplete or has the wrong term")
            return apply_trusted_dictionary_phonetics(
                generated,
                request.dictionary_core,
            )
        except Exception as exc:
            raise VocabularyCardGenerationError(
                "CORE_CONTENT_UNAVAILABLE",
                True,
                "The core agent returned invalid structured output.",
            ) from exc

    def _validate_card_blocks_output(
        self,
        output: Any,
        core: VocabularyCore,
    ) -> VocabularyCardBlocks:
        if not isinstance(output, VocabularyCardBlocks):
            raise ValueError("Card Blocks agent returned invalid structured output")
        validated = VocabularyCardBlocks.model_validate(
            output.model_dump(by_alias=True, mode="json")
        )
        if not validated.blocks:
            raise ValueError("Card Blocks agent returned no blocks")

        meaning_ids = {
            meaning.id
            for sense in core.senses
            for meaning in sense.meanings
        }
        if any(
            meaning_ref not in meaning_ids
            for block in validated.blocks
            for meaning_ref in block.meaning_refs
        ):
            raise ValueError("Card Blocks agent returned dangling meaningRefs")
        return validated

    async def _run_agent(
        self,
        *,
        agent: Any,
        agent_input: str,
        request: VocabularyCardGenerationRequest,
        model_call_number: int,
        started_at: float,
        on_start: Callable[[], None] | None = None,
    ) -> tuple[Any, AgentSessionUsage | None]:
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
                        "model_call_number": str(model_call_number),
                    },
                ),
            )
            if on_start is not None:
                on_start()
            result = await asyncio.wait_for(run, timeout=timeout_seconds)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            raise self._map_model_error(exc) from None
        raw_usage = getattr(getattr(result, "context_wrapper", None), "usage", None)
        usage = extract_usage(result) if raw_usage is not None else None
        return getattr(result, "final_output", None), usage

    def _remaining_timeout_seconds(
        self,
        timeout_budget_ms: int,
        started_at: float,
    ) -> float:
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
        if isinstance(exc, TimeoutError) or exc.__class__.__name__ in {
            "APITimeoutError",
            "TimeoutException",
        }:
            return VocabularyCardGenerationError(
                "MODEL_TIMEOUT",
                True,
                "The model request timed out.",
            )
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
        usages: list[AgentSessionUsage],
    ) -> VocabularyCardGenerationResponse:
        return VocabularyCardGenerationResponse(
            contractVersion=2,
            coreSchemaVersion=2,
            cardBlocksSchemaVersion=1,
            core=core,
            cardBlocks={"schemaVersion": 1, "blocks": []},
            outcome="partial",
            warning="card_blocks_unavailable",
            generation=self._metadata(request, usages),
        )

    def _metadata(
        self,
        request: VocabularyCardGenerationRequest,
        usages: list[AgentSessionUsage],
    ) -> VocabularyGenerationMetadata:
        return VocabularyGenerationMetadata(
            provider="openai",
            model=self._model,
            promptVersion=(
                f"core={self._core_prompt_version};"
                f"blocks={self._card_blocks_prompt_version}"
            ),
            modelCallCount=2,
            traceId=request.trace_id,
            usage=self._combined_usage(usages),
        )

    @staticmethod
    def _combined_usage(
        usages: list[AgentSessionUsage],
    ) -> VocabularyGenerationUsage | None:
        if not usages:
            return None
        return VocabularyGenerationUsage(
            inputTokens=sum(item.input_tokens for item in usages),
            cachedInputTokens=sum(item.cached_input_tokens for item in usages),
            outputTokens=sum(item.output_tokens for item in usages),
            totalTokens=sum(item.total_tokens for item in usages),
            requests=sum(item.requests for item in usages),
        )
