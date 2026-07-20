import copy

import pytest
from pydantic import ValidationError

from python.ai_orchestrator.schemas.vocabulary_image_recognition import (
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


def model_item_payload(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "observedText": "package",
        "normalizedTerm": "package",
        "status": "accepted",
        "suggestions": [],
        "contextText": "package delivery",
        "confidence": 0.95,
    }
    return {**payload, **overrides}


def accepted_item(**overrides: object) -> VocabularyImageRecognitionItem:
    payload: dict[str, object] = {
        "itemId": "item-1",
        **model_item_payload(),
    }
    return VocabularyImageRecognitionItem(**{**payload, **overrides})


def generation(trace_id: str, **overrides: object) -> VocabularyImageRecognitionGeneration:
    payload: dict[str, object] = {
        "provider": "openai",
        "model": "gpt-4.1-mini",
        "promptVersion": PROMPT_VERSION,
        "modelCallCount": 1,
        "traceId": trace_id,
        "usage": {"inputTokens": 120, "outputTokens": 18},
    }
    return VocabularyImageRecognitionGeneration(**{**payload, **overrides})


def request_payload(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "contractVersion": 1,
        "traceId": "vocab-image-123",
        "language": "en",
        "fileName": "words.png",
        "contentType": "image/png",
        "content": b"png-bytes",
    }
    return {**payload, **overrides}


def response_payload(**overrides: object) -> dict[str, object]:
    trace_id = "vocab-image-123"
    payload: dict[str, object] = {
        "contractVersion": 1,
        "traceId": trace_id,
        "rawText": "package",
        "warnings": [],
        "items": [accepted_item().model_dump(by_alias=True)],
        "generation": generation(trace_id).model_dump(by_alias=True),
    }
    return {**payload, **overrides}


def test_declares_exact_recognition_constants() -> None:
    assert PROMPT_VERSION == "vocabulary-image-recognition-v1"
    assert MAX_CANDIDATES == 30
    assert MAX_IMAGE_BYTES == 10 * 1024 * 1024
    assert MAX_MODEL_CALLS == 2


def test_request_uses_exact_public_aliases_and_rejects_unknown_fields() -> None:
    request = VocabularyImageRecognitionRequest.model_validate(request_payload())

    assert request.model_dump(by_alias=True, mode="json") == {
        "contractVersion": 1,
        "traceId": "vocab-image-123",
        "language": "en",
        "fileName": "words.png",
        "contentType": "image/png",
        "content": "png-bytes",
    }

    payload = request_payload()
    payload["unexpected"] = True
    with pytest.raises(ValidationError):
        VocabularyImageRecognitionRequest.model_validate(payload)


def test_request_rejects_invalid_trace_ids_and_image_bounds() -> None:
    for invalid_trace_id in ("private sentence", "a" * 129):
        with pytest.raises(ValidationError):
            VocabularyImageRecognitionRequest.model_validate(
                request_payload(traceId=invalid_trace_id)
            )

    with pytest.raises(ValidationError):
        VocabularyImageRecognitionRequest.model_validate(request_payload(content=b""))

    with pytest.raises(ValidationError):
        VocabularyImageRecognitionRequest.model_validate(
            request_payload(content=b"a" * (MAX_IMAGE_BYTES + 1))
        )


def test_suspected_typo_requires_one_to_three_suggestions() -> None:
    with pytest.raises(ValidationError):
        VocabularyImageRecognitionModelItem(
            **model_item_payload(status="suspected_typo", suggestions=[])
        )

    with pytest.raises(ValidationError):
        VocabularyImageRecognitionModelItem(
            **model_item_payload(
                status="suspected_typo",
                suggestions=["receive", "recipe", "recede", "recede"],
            )
        )

    item = VocabularyImageRecognitionModelItem(
        **model_item_payload(status="suspected_typo", suggestions=["receive"])
    )
    assert item.suggestions == ["receive"]


def test_suggestions_reject_empty_or_whitespace_only_values() -> None:
    for suggestion in ("", "   ", "\t"):
        with pytest.raises(ValidationError):
            VocabularyImageRecognitionModelItem(
                **model_item_payload(status="suspected_typo", suggestions=[suggestion])
            )


def test_accepted_item_must_not_include_suggestions() -> None:
    with pytest.raises(ValidationError):
        VocabularyImageRecognitionItem(
            itemId="item-1",
            **model_item_payload(suggestions=["package"]),
        )


def test_confidence_has_explicit_inclusive_bounds() -> None:
    for confidence in (-0.01, 1.01):
        with pytest.raises(ValidationError):
            VocabularyImageRecognitionModelItem(
                **model_item_payload(confidence=confidence)
            )

    assert VocabularyImageRecognitionModelItem(
        **model_item_payload(confidence=0)
    ).confidence == 0
    assert VocabularyImageRecognitionModelItem(
        **model_item_payload(confidence=1)
    ).confidence == 1


def test_model_output_and_response_items_are_strict_and_separate() -> None:
    output = VocabularyImageRecognitionModelOutput(
        rawText="package",
        items=[model_item_payload()],
    )
    assert output.items[0].observed_text == "package"

    response_item = accepted_item()
    assert response_item.item_id == "item-1"

    payload = model_item_payload()
    payload["itemId"] = "item-1"
    with pytest.raises(ValidationError):
        VocabularyImageRecognitionModelItem(**payload)


def test_generation_usage_is_nullable_and_strict() -> None:
    metadata = generation("vocab-image-123", usage=None)
    assert metadata.usage is None

    usage = VocabularyImageRecognitionUsage(inputTokens=0, outputTokens=0)
    assert usage.input_tokens == 0
    assert usage.output_tokens == 0

    with pytest.raises(ValidationError):
        VocabularyImageRecognitionGeneration(
            **generation("vocab-image-123").model_dump(by_alias=True),
            unexpected=True,
        )

    with pytest.raises(ValidationError):
        VocabularyImageRecognitionGeneration(
            **generation("vocab-image-123", modelCallCount=MAX_MODEL_CALLS + 1).model_dump(
                by_alias=True
            )
        )


def test_response_contract_uses_exact_public_aliases() -> None:
    response = VocabularyImageRecognitionResponse(
        contractVersion=1,
        traceId="vocab-image-123",
        rawText="package",
        warnings=[],
        items=[accepted_item()],
        generation=generation("vocab-image-123"),
    )
    payload = response.model_dump(by_alias=True, mode="json")
    assert set(payload) == {
        "contractVersion",
        "traceId",
        "rawText",
        "warnings",
        "items",
        "generation",
    }
    assert payload["generation"]["promptVersion"] == "vocabulary-image-recognition-v1"


def test_response_requires_matching_trace_id_and_supported_warning_values() -> None:
    mismatched_trace = response_payload()
    mismatched_trace["generation"] = {
        **mismatched_trace["generation"],
        "traceId": "different-trace",
    }
    with pytest.raises(ValidationError):
        VocabularyImageRecognitionResponse.model_validate(mismatched_trace)

    unsupported_warning = response_payload()
    unsupported_warning["warnings"] = ["UNKNOWN_WARNING"]
    with pytest.raises(ValidationError):
        VocabularyImageRecognitionResponse.model_validate(unsupported_warning)

    response = VocabularyImageRecognitionResponse.model_validate(
        response_payload(warnings=["CANDIDATE_LIMIT_REACHED"])
    )
    assert response.warnings == ["CANDIDATE_LIMIT_REACHED"]


def test_response_rejects_unknown_nested_fields() -> None:
    payload = copy.deepcopy(response_payload())
    payload["items"][0]["unexpected"] = True
    with pytest.raises(ValidationError):
        VocabularyImageRecognitionResponse.model_validate(payload)
