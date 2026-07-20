from __future__ import annotations

import asyncio
import logging
import time
import unicodedata
from collections.abc import Callable
from pathlib import PurePath
from typing import Any

from agents import ModelBehaviorError, RunConfig, Runner
from pydantic import ValidationError

from ..adapters.openai_input_items import build_input_items
from ..agents.vocabulary_image_recognition import build_vocabulary_image_recognition_agent
from ..schemas.chat import UploadedAttachment
from ..schemas.vocabulary_image_recognition import (
    MAX_CANDIDATES,
    MAX_IMAGE_BYTES,
    MAX_MODEL_CALLS,
    PROMPT_VERSION,
    VocabularyImageRecognitionGeneration,
    VocabularyImageRecognitionItem,
    VocabularyImageRecognitionModelItem,
    VocabularyImageRecognitionModelOutput,
    VocabularyImageRecognitionRequest,
    VocabularyImageRecognitionResponse,
    VocabularyImageRecognitionUsage,
)
from ..services.agent_session_runner import extract_usage


WORKFLOW_NAME = "Vocabulary Image Recognition"
PROVIDER = "openai"
SUPPORTED_EXTENSIONS_BY_CONTENT_TYPE = {
    "image/jpeg": frozenset({".jpg", ".jpeg"}),
    "image/png": frozenset({".png"}),
    "image/webp": frozenset({".webp"}),
}
INPUT_INSTRUCTION = (
    "Extract the visible English vocabulary candidates from this image and return "
    "only the configured structured output."
)

log = logging.getLogger("uvicorn.error")


class VocabularyImageRecognitionError(RuntimeError):
    def __init__(self, code: str, retryable: bool) -> None:
        super().__init__(code)
        self.code = code
        self.retryable = retryable


class VocabularyImageRecognitionWorkflow:
    def __init__(
        self,
        *,
        model: str,
        timeout_seconds: float,
        monotonic_clock: Callable[[], float] | None = None,
    ) -> None:
        self._model = model.strip()
        self._timeout_seconds = timeout_seconds
        self._clock = monotonic_clock or time.monotonic
        self._agent = build_vocabulary_image_recognition_agent(self._model)

    async def recognize(
        self,
        request: VocabularyImageRecognitionRequest,
    ) -> VocabularyImageRecognitionResponse:
        self._validate_request(request)
        started_at = self._clock()
        terminal_error: VocabularyImageRecognitionError | None = None

        for call_number in range(1, MAX_MODEL_CALLS + 1):
            try:
                result = await asyncio.wait_for(
                    Runner.run(
                        self._agent,
                        self._input_items(request),
                        run_config=self._run_config(request),
                    ),
                    timeout=self._timeout_seconds,
                )
                output = self._require_model_output(result.final_output)
                items, warnings = self._sanitize_items(output.items)
                usage = extract_usage(result)
                response = self._response(
                    request=request,
                    items=items,
                    warnings=warnings,
                    usage=usage,
                    call_number=call_number,
                )
                self._log_result(
                    request=request,
                    candidate_count=len(items),
                    suspected_typo_count=sum(
                        candidate.status == "suspected_typo" for candidate in items
                    ),
                    call_number=call_number,
                    started_at=started_at,
                )
                return response
            except asyncio.CancelledError:
                raise
            except (ModelBehaviorError, ValidationError, TypeError, ValueError):
                if call_number == MAX_MODEL_CALLS:
                    terminal_error = VocabularyImageRecognitionError(
                        "MODEL_OUTPUT_INVALID",
                        True,
                    )
            except Exception as exc:
                terminal_error = self._map_model_error(exc)

            if terminal_error is not None:
                self._log_error(request, terminal_error, call_number, started_at)
                break

        if terminal_error is None:  # pragma: no cover - loop invariant
            terminal_error = VocabularyImageRecognitionError("MODEL_OUTPUT_INVALID", True)
        raise terminal_error

    def _validate_request(self, request: VocabularyImageRecognitionRequest) -> None:
        if request.contract_version != 1 or request.language != "en":
            raise VocabularyImageRecognitionError("INVALID_IMAGE_REQUEST", False)
        if not request.content or len(request.content) > MAX_IMAGE_BYTES:
            code = (
                "IMAGE_TOO_LARGE"
                if len(request.content) > MAX_IMAGE_BYTES
                else "INVALID_IMAGE_REQUEST"
            )
            raise VocabularyImageRecognitionError(code, False)

        supported_extensions = SUPPORTED_EXTENSIONS_BY_CONTENT_TYPE.get(request.content_type)
        if supported_extensions is None:
            raise VocabularyImageRecognitionError("UNSUPPORTED_IMAGE_TYPE", False)
        extension = PurePath(request.file_name).suffix.casefold()
        if extension not in supported_extensions:
            raise VocabularyImageRecognitionError("UNSUPPORTED_IMAGE_TYPE", False)

    def _input_items(self, request: VocabularyImageRecognitionRequest) -> list[dict]:
        attachment: UploadedAttachment = {
            "filename": "image",
            "content_type": request.content_type,
            "content": request.content,
        }
        return build_input_items(INPUT_INSTRUCTION, [attachment])

    def _run_config(self, request: VocabularyImageRecognitionRequest) -> RunConfig:
        return RunConfig(
            workflow_name=WORKFLOW_NAME,
            trace_include_sensitive_data=False,
            trace_metadata={"trace_id": request.trace_id},
        )

    def _require_model_output(self, output: Any) -> VocabularyImageRecognitionModelOutput:
        if not isinstance(output, VocabularyImageRecognitionModelOutput):
            raise TypeError("invalid structured output")
        return VocabularyImageRecognitionModelOutput.model_validate(
            output.model_dump(by_alias=True, mode="json")
        )

    def _sanitize_items(
        self,
        model_items: list[VocabularyImageRecognitionModelItem],
    ) -> tuple[list[VocabularyImageRecognitionItem], list[str]]:
        candidates: list[VocabularyImageRecognitionItem] = []
        seen_terms: set[str] = set()
        limit_reached = False

        for model_item in model_items:
            observed_text = model_item.observed_text.strip()
            normalized_term = self._normalize_term(observed_text)
            if not normalized_term:
                raise ValueError("candidate normalizes to an empty term")
            if normalized_term in seen_terms:
                continue
            seen_terms.add(normalized_term)
            if len(candidates) >= MAX_CANDIDATES:
                limit_reached = True
                continue

            candidates.append(
                VocabularyImageRecognitionItem(
                    itemId=f"item-{len(candidates) + 1}",
                    observedText=observed_text,
                    normalizedTerm=normalized_term,
                    status=model_item.status,
                    suggestions=self._sanitize_suggestions(model_item.suggestions),
                    contextText=(model_item.context_text or "").strip() or None,
                    confidence=model_item.confidence,
                )
            )

        warnings = ["CANDIDATE_LIMIT_REACHED"] if limit_reached else []
        return candidates, warnings

    def _sanitize_suggestions(self, suggestions: list[str]) -> list[str]:
        normalized: list[str] = []
        seen: set[str] = set()
        for suggestion in suggestions:
            value = self._normalize_term(suggestion)
            if not value or value in seen:
                continue
            normalized.append(value)
            seen.add(value)
        return normalized

    @staticmethod
    def _normalize_term(value: str) -> str:
        normalized = value.strip()
        while normalized and unicodedata.category(normalized[0]).startswith("P"):
            normalized = normalized[1:].lstrip()
        while normalized and unicodedata.category(normalized[-1]).startswith("P"):
            normalized = normalized[:-1].rstrip()
        return normalized.casefold()

    def _response(
        self,
        *,
        request: VocabularyImageRecognitionRequest,
        items: list[VocabularyImageRecognitionItem],
        warnings: list[str],
        usage: Any,
        call_number: int,
    ) -> VocabularyImageRecognitionResponse:
        return VocabularyImageRecognitionResponse(
            contractVersion=1,
            traceId=request.trace_id,
            rawText="",
            warnings=warnings,
            items=items,
            generation=VocabularyImageRecognitionGeneration(
                provider=PROVIDER,
                model=self._model,
                promptVersion=PROMPT_VERSION,
                modelCallCount=call_number,
                traceId=request.trace_id,
                usage=VocabularyImageRecognitionUsage(
                    inputTokens=usage.input_tokens,
                    outputTokens=usage.output_tokens,
                ),
            ),
        )

    def _map_model_error(self, exc: Exception) -> VocabularyImageRecognitionError:
        if isinstance(exc, TimeoutError) or exc.__class__.__name__ in {
            "APITimeoutError",
            "TimeoutException",
        }:
            return VocabularyImageRecognitionError("MODEL_TIMEOUT", True)
        return VocabularyImageRecognitionError("MODEL_UPSTREAM_UNAVAILABLE", True)

    def _log_result(
        self,
        *,
        request: VocabularyImageRecognitionRequest,
        candidate_count: int,
        suspected_typo_count: int,
        call_number: int,
        started_at: float,
    ) -> None:
        log.info(
            "Vocabulary image recognition completed",
            extra={
                "trace_id": request.trace_id,
                "image_bytes": len(request.content),
                "candidate_count": candidate_count,
                "suspected_typo_count": suspected_typo_count,
                "model_call_count": call_number,
                "provider": PROVIDER,
                "model": self._model,
                "prompt_version": PROMPT_VERSION,
                "elapsed_ms": round((self._clock() - started_at) * 1_000),
            },
        )

    def _log_error(
        self,
        request: VocabularyImageRecognitionRequest,
        error: VocabularyImageRecognitionError,
        call_number: int,
        started_at: float,
    ) -> None:
        log.warning(
            "Vocabulary image recognition failed",
            extra={
                "trace_id": request.trace_id,
                "image_bytes": len(request.content),
                "model_call_count": call_number,
                "provider": PROVIDER,
                "model": self._model,
                "prompt_version": PROMPT_VERSION,
                "elapsed_ms": round((self._clock() - started_at) * 1_000),
                "error_code": error.code,
            },
        )


__all__ = [
    "VocabularyImageRecognitionError",
    "VocabularyImageRecognitionWorkflow",
]
