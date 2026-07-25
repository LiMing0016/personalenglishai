from __future__ import annotations

import pytest
from pydantic import ValidationError

from python.ai_orchestrator.schemas.vocabulary_import_analysis import (
    MAX_MODEL_CALLS,
    PROMPT_VERSION,
    VocabularyImportAnalysisGeneration,
    VocabularyImportAnalysisItem,
    VocabularyImportAnalysisRequest,
    VocabularyImportAnalysisResponse,
)


FINGERPRINT = "a" * 64


def request_payload(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "contractVersion": 1,
        "traceId": "vocab-import-123",
        "inputFingerprint": FINGERPRINT,
        "language": "en",
        "text": "package",
        "fileName": None,
        "contentType": None,
        "content": None,
    }
    return {**payload, **overrides}


def generation(trace_id: str = "vocab-import-123") -> VocabularyImportAnalysisGeneration:
    return VocabularyImportAnalysisGeneration(
        provider="openai",
        model="test-model",
        promptVersion=PROMPT_VERSION,
        modelCallCount=1,
        traceId=trace_id,
        usage=None,
    )


def item() -> VocabularyImportAnalysisItem:
    return VocabularyImportAnalysisItem(
        itemId="item-1",
        observedText="Package",
        normalizedTerm="package",
        status="accepted",
        suggestions=[],
        contextText=None,
        confidence=0.95,
        evidence="text",
    )


def test_declares_versioned_prompt_and_two_call_limit() -> None:
    assert PROMPT_VERSION == "vocabulary-import-analysis-v1"
    assert MAX_MODEL_CALLS == 2


def test_request_accepts_text_only_image_only_and_combined_input() -> None:
    text_only = VocabularyImportAnalysisRequest.model_validate(request_payload())
    image_only = VocabularyImportAnalysisRequest.model_validate(
        request_payload(
            text="",
            fileName="words.png",
            contentType="image/png",
            content=b"png-bytes",
        )
    )
    combined = VocabularyImportAnalysisRequest.model_validate(
        request_payload(
            fileName="words.webp",
            contentType="image/webp",
            content=b"webp-bytes",
        )
    )

    assert text_only.text == "package"
    assert image_only.content == b"png-bytes"
    assert combined.text == "package"
    assert combined.content == b"webp-bytes"


def test_request_requires_text_or_image_and_complete_image_metadata() -> None:
    with pytest.raises(ValidationError):
        VocabularyImportAnalysisRequest.model_validate(request_payload(text=""))

    with pytest.raises(ValidationError):
        VocabularyImportAnalysisRequest.model_validate(
            request_payload(fileName="words.png", contentType="image/png", content=None)
        )

    with pytest.raises(ValidationError):
        VocabularyImportAnalysisRequest.model_validate(
            request_payload(content=b"png-bytes")
        )


def test_request_requires_lowercase_sha256_fingerprint() -> None:
    for invalid in ("abc", "A" * 64, "g" * 64):
        with pytest.raises(ValidationError):
            VocabularyImportAnalysisRequest.model_validate(
                request_payload(inputFingerprint=invalid)
            )


def test_response_preserves_fingerprint_and_requires_matching_trace() -> None:
    response = VocabularyImportAnalysisResponse(
        contractVersion=1,
        traceId="vocab-import-123",
        inputFingerprint=FINGERPRINT,
        rawText="Package",
        warnings=[],
        items=[item()],
        generation=generation(),
    )

    payload = response.model_dump(by_alias=True, mode="json")
    assert payload["inputFingerprint"] == FINGERPRINT
    assert payload["items"][0]["evidence"] == "text"

    with pytest.raises(ValidationError):
        VocabularyImportAnalysisResponse(
            contractVersion=1,
            traceId="vocab-import-123",
            inputFingerprint=FINGERPRINT,
            rawText="Package",
            warnings=[],
            items=[item()],
            generation=generation("different-trace"),
        )

