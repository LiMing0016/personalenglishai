from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


PROMPT_VERSION = "vocabulary-image-recognition-v1"
MAX_CANDIDATES = 30
MAX_IMAGE_BYTES = 10 * 1024 * 1024
MAX_MODEL_CALLS = 2

MAX_OPAQUE_ID_LENGTH = 128
OPAQUE_ID_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"

RecognitionStatus = Literal["accepted", "suspected_typo"]
PythonRecognitionWarning = Literal["CANDIDATE_LIMIT_REACHED"]


class StrictRecognitionModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class VocabularyImageRecognitionModelItem(StrictRecognitionModel):
    observed_text: str = Field(alias="observedText", min_length=1, max_length=200)
    normalized_term: str = Field(alias="normalizedTerm", min_length=1, max_length=200)
    status: RecognitionStatus
    suggestions: list[str] = Field(max_length=3)
    context_text: str | None = Field(default=None, alias="contextText", max_length=2_000)
    confidence: float = Field(ge=0, le=1)

    @model_validator(mode="after")
    def validate_suggestion_state(self) -> "VocabularyImageRecognitionModelItem":
        if self.status == "suspected_typo" and not self.suggestions:
            raise ValueError("suspected_typo requires at least one suggestion")
        if self.status == "accepted" and self.suggestions:
            raise ValueError("accepted items must not include suggestions")
        return self


class VocabularyImageRecognitionModelOutput(StrictRecognitionModel):
    raw_text: str = Field(alias="rawText", max_length=20_000)
    items: list[VocabularyImageRecognitionModelItem] = Field(max_length=100)


class VocabularyImageRecognitionUsage(StrictRecognitionModel):
    input_tokens: int = Field(alias="inputTokens", ge=0)
    output_tokens: int = Field(alias="outputTokens", ge=0)


class VocabularyImageRecognitionRequest(StrictRecognitionModel):
    contract_version: Literal[1] = Field(alias="contractVersion")
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    language: Literal["en"]
    file_name: str = Field(alias="fileName", min_length=1, max_length=255)
    content_type: Literal["image/jpeg", "image/png", "image/webp"] = Field(alias="contentType")
    content: bytes = Field(min_length=1, max_length=MAX_IMAGE_BYTES)


class VocabularyImageRecognitionItem(VocabularyImageRecognitionModelItem):
    item_id: str = Field(alias="itemId", min_length=1, max_length=128)


class VocabularyImageRecognitionGeneration(StrictRecognitionModel):
    provider: str = Field(min_length=1, max_length=100)
    model: str = Field(min_length=1, max_length=200)
    prompt_version: Literal["vocabulary-image-recognition-v1"] = Field(alias="promptVersion")
    model_call_count: int = Field(alias="modelCallCount", ge=1, le=MAX_MODEL_CALLS)
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    usage: VocabularyImageRecognitionUsage | None = None


class VocabularyImageRecognitionResponse(StrictRecognitionModel):
    contract_version: Literal[1] = Field(alias="contractVersion")
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    raw_text: str = Field(alias="rawText", max_length=20_000)
    warnings: list[PythonRecognitionWarning] = Field(max_length=1)
    items: list[VocabularyImageRecognitionItem] = Field(max_length=MAX_CANDIDATES)
    generation: VocabularyImageRecognitionGeneration

    @model_validator(mode="after")
    def validate_generation_trace_id(self) -> "VocabularyImageRecognitionResponse":
        if self.generation.trace_id != self.trace_id:
            raise ValueError("generation.traceId must match traceId")
        return self
