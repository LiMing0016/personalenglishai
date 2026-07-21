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
from ..agents.vocabulary_import_analysis import build_vocabulary_import_analysis_agent
from ..schemas.chat import UploadedAttachment
from ..schemas.vocabulary_import_analysis import (
    MAX_CANDIDATES,
    MAX_IMAGE_BYTES,
    MAX_MODEL_CALLS,
    PROMPT_VERSION,
    VocabularyImportAnalysisGeneration,
    VocabularyImportAnalysisItem,
    VocabularyImportAnalysisModelItem,
    VocabularyImportAnalysisModelOutput,
    VocabularyImportAnalysisRequest,
    VocabularyImportAnalysisResponse,
    VocabularyImportAnalysisUsage,
)
from ..services.agent_session_runner import AgentSessionUsage, extract_usage


WORKFLOW_NAME = "Vocabulary Import Analysis"
PROVIDER = "openai"
SUPPORTED_EXTENSIONS_BY_CONTENT_TYPE = {
    "image/jpeg": frozenset({".jpg", ".jpeg"}),
    "image/png": frozenset({".png"}),
    "image/webp": frozenset({".webp"}),
}
BASE_INSTRUCTION = (
    "Extract English vocabulary candidates from the supplied evidence and return "
    "only the configured structured output."
)

log = logging.getLogger("uvicorn.error")


class VocabularyImportAnalysisError(RuntimeError):
    def __init__(self, code: str, retryable: bool) -> None:
        super().__init__(code)
        self.code = code
        self.retryable = retryable


class VocabularyImportAnalysisWorkflow:
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
        self._agent = build_vocabulary_import_analysis_agent(self._model)

    async def analyze(
        self,
        request: VocabularyImportAnalysisRequest,
    ) -> VocabularyImportAnalysisResponse:
        self._validate_request(request)
        started_at = self._clock()
        deadline = started_at + self._timeout_seconds
        input_items = self._input_items(request)
        run_config = RunConfig(
            workflow_name=WORKFLOW_NAME,
            trace_include_sensitive_data=False,
            trace_metadata={"trace_id": request.trace_id},
        )
        terminal_error: VocabularyImportAnalysisError | None = None
        model_call_count = 0

        for call_number in range(1, MAX_MODEL_CALLS + 1):
            remaining_timeout = deadline - self._clock()
            if remaining_timeout <= 0:
                terminal_error = VocabularyImportAnalysisError("MODEL_TIMEOUT", True)
                self._log_error(request, terminal_error, model_call_count, started_at)
                break

            model_call_count += 1
            try:
                result = await asyncio.wait_for(
                    Runner.run(self._agent, input_items, run_config=run_config),
                    timeout=remaining_timeout,
                )
                output = self._require_model_output(result.final_output)
                items, warnings = self._sanitize_items(output.items)
                response = self._response(
                    request=request,
                    raw_text=output.raw_text,
                    items=items,
                    warnings=warnings,
                    usage=self._extract_optional_usage(result),
                    call_number=model_call_count,
                )
                self._log_result(request, response, started_at)
                return response
            except asyncio.CancelledError:
                raise
            except (ModelBehaviorError, ValidationError, TypeError, ValueError):
                if call_number == MAX_MODEL_CALLS:
                    terminal_error = VocabularyImportAnalysisError(
                        "MODEL_OUTPUT_INVALID",
                        True,
                    )
            except Exception as exc:
                terminal_error = self._map_model_error(exc)

            if terminal_error is not None:
                self._log_error(request, terminal_error, model_call_count, started_at)
                break

        if terminal_error is None:  # pragma: no cover - loop invariant
            terminal_error = VocabularyImportAnalysisError("MODEL_OUTPUT_INVALID", True)
        raise terminal_error

    def _validate_request(self, request: VocabularyImportAnalysisRequest) -> None:
        if request.contract_version != 1 or request.language != "en":
            raise VocabularyImportAnalysisError("INVALID_IMPORT_REQUEST", False)
        if request.content is None:
            return
        if not request.content or len(request.content) > MAX_IMAGE_BYTES:
            code = "IMAGE_TOO_LARGE" if len(request.content) > MAX_IMAGE_BYTES else "INVALID_IMPORT_REQUEST"
            raise VocabularyImportAnalysisError(code, False)
        supported_extensions = SUPPORTED_EXTENSIONS_BY_CONTENT_TYPE.get(request.content_type or "")
        if supported_extensions is None:
            raise VocabularyImportAnalysisError("UNSUPPORTED_IMAGE_TYPE", False)
        if PurePath(request.file_name or "").suffix.casefold() not in supported_extensions:
            raise VocabularyImportAnalysisError("UNSUPPORTED_IMAGE_TYPE", False)

    def _input_items(self, request: VocabularyImportAnalysisRequest) -> list[dict]:
        instruction = BASE_INSTRUCTION
        if request.text.strip():
            instruction = f"{instruction}\n\nUser supplied text:\n{request.text}"
        attachments: list[UploadedAttachment] = []
        if request.content is not None and request.content_type is not None:
            attachments.append(
                {
                    "filename": "image",
                    "content_type": request.content_type,
                    "content": request.content,
                }
            )
        return build_input_items(instruction, attachments)

    @staticmethod
    def _require_model_output(output: Any) -> VocabularyImportAnalysisModelOutput:
        if not isinstance(output, VocabularyImportAnalysisModelOutput):
            raise TypeError("invalid structured output")
        return VocabularyImportAnalysisModelOutput.model_validate(
            output.model_dump(by_alias=True, mode="json")
        )

    @staticmethod
    def _extract_optional_usage(result: Any) -> AgentSessionUsage | None:
        context_wrapper = getattr(result, "context_wrapper", None)
        if getattr(context_wrapper, "usage", None) is None:
            return None
        return extract_usage(result)

    def _sanitize_items(
        self,
        model_items: list[VocabularyImportAnalysisModelItem],
    ) -> tuple[list[VocabularyImportAnalysisItem], list[str]]:
        candidates: list[VocabularyImportAnalysisItem] = []
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
                VocabularyImportAnalysisItem(
                    itemId=f"item-{len(candidates) + 1}",
                    observedText=observed_text,
                    normalizedTerm=normalized_term,
                    status=model_item.status,
                    suggestions=self._sanitize_suggestions(model_item.suggestions),
                    contextText=(model_item.context_text or "").strip() or None,
                    confidence=model_item.confidence,
                    evidence=model_item.evidence,
                )
            )
        return candidates, ["CANDIDATE_LIMIT_REACHED"] if limit_reached else []

    def _sanitize_suggestions(self, suggestions: list[str]) -> list[str]:
        normalized: list[str] = []
        seen: set[str] = set()
        for suggestion in suggestions:
            value = self._normalize_term(suggestion)
            if value and value not in seen:
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
        request: VocabularyImportAnalysisRequest,
        raw_text: str,
        items: list[VocabularyImportAnalysisItem],
        warnings: list[str],
        usage: AgentSessionUsage | None,
        call_number: int,
    ) -> VocabularyImportAnalysisResponse:
        return VocabularyImportAnalysisResponse(
            contractVersion=1,
            traceId=request.trace_id,
            inputFingerprint=request.input_fingerprint,
            rawText=raw_text,
            warnings=warnings,
            items=items,
            generation=VocabularyImportAnalysisGeneration(
                provider=PROVIDER,
                model=self._model,
                promptVersion=PROMPT_VERSION,
                modelCallCount=call_number,
                traceId=request.trace_id,
                usage=(
                    VocabularyImportAnalysisUsage(
                        inputTokens=usage.input_tokens,
                        outputTokens=usage.output_tokens,
                    )
                    if usage is not None
                    else None
                ),
            ),
        )

    @staticmethod
    def _map_model_error(exc: Exception) -> VocabularyImportAnalysisError:
        if isinstance(exc, TimeoutError) or exc.__class__.__name__ in {
            "APITimeoutError",
            "TimeoutException",
        }:
            return VocabularyImportAnalysisError("MODEL_TIMEOUT", True)
        return VocabularyImportAnalysisError("MODEL_UPSTREAM_UNAVAILABLE", True)

    def _log_result(
        self,
        request: VocabularyImportAnalysisRequest,
        response: VocabularyImportAnalysisResponse,
        started_at: float,
    ) -> None:
        log.info(
            "Vocabulary import analysis completed",
            extra={
                "trace_id": request.trace_id,
                "text_length": len(request.text),
                "image_bytes": len(request.content or b""),
                "candidate_count": len(response.items),
                "model_call_count": response.generation.model_call_count,
                "provider": PROVIDER,
                "model": self._model,
                "prompt_version": PROMPT_VERSION,
                "elapsed_ms": round((self._clock() - started_at) * 1_000),
            },
        )

    def _log_error(
        self,
        request: VocabularyImportAnalysisRequest,
        error: VocabularyImportAnalysisError,
        model_call_count: int,
        started_at: float,
    ) -> None:
        log.warning(
            "Vocabulary import analysis failed",
            extra={
                "trace_id": request.trace_id,
                "text_length": len(request.text),
                "image_bytes": len(request.content or b""),
                "model_call_count": model_call_count,
                "provider": PROVIDER,
                "model": self._model,
                "prompt_version": PROMPT_VERSION,
                "elapsed_ms": round((self._clock() - started_at) * 1_000),
                "error_code": error.code,
            },
        )


__all__ = ["VocabularyImportAnalysisError", "VocabularyImportAnalysisWorkflow"]
