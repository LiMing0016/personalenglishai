from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


PROMPT_VERSION = "vocabulary-import-analysis-v1"
MAX_CANDIDATES = 30
MAX_IMAGE_BYTES = 10 * 1024 * 1024
MAX_TEXT_LENGTH = 20_000
MAX_MODEL_CALLS = 2

MAX_OPAQUE_ID_LENGTH = 128
OPAQUE_ID_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"
FINGERPRINT_PATTERN = r"^[0-9a-f]{64}$"

RecognitionStatus = Literal["accepted", "suspected_typo"]
EvidenceType = Literal["text", "image", "text_image"]
PythonRecognitionWarning = Literal["CANDIDATE_LIMIT_REACHED"]
NonBlankSuggestion = Annotated[str, Field(min_length=1, max_length=200, pattern=r"\S")]


class StrictImportModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class VocabularyImportAnalysisModelItem(StrictImportModel):
    observed_text: str = Field(alias="observedText", min_length=1, max_length=200)
    normalized_term: str = Field(alias="normalizedTerm", min_length=1, max_length=200)
    status: RecognitionStatus
    suggestions: list[NonBlankSuggestion] = Field(max_length=3)
    context_text: str | None = Field(default=None, alias="contextText", max_length=2_000)
    confidence: float = Field(ge=0, le=1)
    evidence: EvidenceType

    @model_validator(mode="after")
    def validate_suggestion_state(self) -> "VocabularyImportAnalysisModelItem":
        if self.status == "suspected_typo" and not self.suggestions:
            raise ValueError("suspected_typo requires at least one suggestion")
        if self.status == "accepted" and self.suggestions:
            raise ValueError("accepted items must not include suggestions")
        return self


class VocabularyImportAnalysisModelOutput(StrictImportModel):
    raw_text: str = Field(alias="rawText", max_length=MAX_TEXT_LENGTH)
    items: list[VocabularyImportAnalysisModelItem] = Field(max_length=100)


class VocabularyImportAnalysisUsage(StrictImportModel):
    input_tokens: int = Field(alias="inputTokens", ge=0)
    output_tokens: int = Field(alias="outputTokens", ge=0)


class VocabularyImportAnalysisRequest(StrictImportModel):
    contract_version: Literal[1] = Field(alias="contractVersion")
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    input_fingerprint: str = Field(alias="inputFingerprint", pattern=FINGERPRINT_PATTERN)
    language: Literal["en"]
    text: str = Field(default="", max_length=MAX_TEXT_LENGTH)
    file_name: str | None = Field(default=None, alias="fileName", min_length=1, max_length=255)
    content_type: Literal["image/jpeg", "image/png", "image/webp"] | None = Field(
        default=None,
        alias="contentType",
    )
    content: bytes | None = Field(default=None, max_length=MAX_IMAGE_BYTES)

    @model_validator(mode="after")
    def validate_input(self) -> "VocabularyImportAnalysisRequest":
        has_text = bool(self.text.strip())
        has_content = bool(self.content)
        metadata_complete = self.file_name is not None and self.content_type is not None
        if not has_text and not has_content:
            raise ValueError("text or image is required")
        if has_content != metadata_complete:
            raise ValueError("image content and metadata must be provided together")
        return self


class VocabularyImportAnalysisItem(VocabularyImportAnalysisModelItem):
    item_id: str = Field(alias="itemId", min_length=1, max_length=128)


class VocabularyImportAnalysisGeneration(StrictImportModel):
    provider: str = Field(min_length=1, max_length=100)
    model: str = Field(min_length=1, max_length=200)
    prompt_version: Literal["vocabulary-import-analysis-v1"] = Field(alias="promptVersion")
    model_call_count: int = Field(alias="modelCallCount", ge=1, le=MAX_MODEL_CALLS)
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    usage: VocabularyImportAnalysisUsage | None = None


class VocabularyImportAnalysisResponse(StrictImportModel):
    contract_version: Literal[1] = Field(alias="contractVersion")
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    input_fingerprint: str = Field(alias="inputFingerprint", pattern=FINGERPRINT_PATTERN)
    raw_text: str = Field(alias="rawText", max_length=MAX_TEXT_LENGTH)
    warnings: list[PythonRecognitionWarning] = Field(max_length=1)
    items: list[VocabularyImportAnalysisItem] = Field(max_length=MAX_CANDIDATES)
    generation: VocabularyImportAnalysisGeneration

    @model_validator(mode="after")
    def validate_generation_trace_id(self) -> "VocabularyImportAnalysisResponse":
        if self.generation.trace_id != self.trace_id:
            raise ValueError("generation.traceId must match traceId")
        return self

