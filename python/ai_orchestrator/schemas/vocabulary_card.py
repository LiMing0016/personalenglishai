from __future__ import annotations

import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


VocabularyPromptStrategyKey = Literal[
    "basic-markdown-v1",
    "exam-markdown-v1",
    "reading-markdown-v1",
    "custom-markdown-v1",
]
VocabularyGenerationOutcome = Literal["complete", "partial"]

MAX_TERM_LENGTH = 200
MAX_SOURCE_CONTEXT_LENGTH = 10_000
MAX_SCALAR_LENGTH = 2_000
MAX_TIMEOUT_BUDGET_MS = 60_000
MAX_TRACE_ID_LENGTH = 80
_RAW_HTML_TAG_PATTERN = re.compile(
    r"</?[A-Za-z][A-Za-z0-9:-]*(?:\s+[^<>]*?)?\s*/?>",
    re.IGNORECASE,
)


def validate_markdown_content(value: str, *, require_nonempty: bool) -> str:
    if require_nonempty and not value.strip():
        raise ValueError("contentMarkdown must be non-empty Markdown")
    if _RAW_HTML_TAG_PATTERN.search(value):
        raise ValueError("contentMarkdown must not contain raw HTML")
    return value


class StrictVocabularyModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class VocabularyPhonetic(StrictVocabularyModel):
    region: Literal["uk", "us", "other"]
    text: str = Field(max_length=MAX_SCALAR_LENGTH)
    audio_url: str | None = Field(alias="audioUrl", max_length=MAX_SCALAR_LENGTH)


class VocabularyMeaning(StrictVocabularyModel):
    definition_en: str = Field(alias="definitionEn", max_length=MAX_SCALAR_LENGTH)
    definition_zh: str = Field(alias="definitionZh", max_length=MAX_SCALAR_LENGTH)


class VocabularySense(StrictVocabularyModel):
    part_of_speech: str = Field(alias="partOfSpeech", max_length=MAX_SCALAR_LENGTH)
    meanings: list[VocabularyMeaning] = Field(max_length=30)


class VocabularyCore(StrictVocabularyModel):
    schema_version: Literal[1] = Field(alias="schemaVersion")
    term: str = Field(min_length=1, max_length=MAX_TERM_LENGTH)
    phonetics: list[VocabularyPhonetic] = Field(max_length=10)
    senses: list[VocabularySense] = Field(max_length=20)


class VocabularyThemeSnapshot(StrictVocabularyModel):
    uid: str = Field(min_length=1, max_length=200)
    version: int = Field(ge=1)
    name: str = Field(min_length=1, max_length=200)
    purpose: str = Field(max_length=MAX_SOURCE_CONTEXT_LENGTH)
    prompt_strategy_key: VocabularyPromptStrategyKey = Field(alias="promptStrategyKey")
    content_format_version: Literal[1] = Field(alias="contentFormatVersion")


class VocabularyCardGenerationRequest(StrictVocabularyModel):
    contract_version: Literal[1] = Field(alias="contractVersion")
    core_schema_version: Literal[1] = Field(alias="coreSchemaVersion")
    request_id: str = Field(alias="requestId", min_length=1, max_length=200)
    trace_id: str = Field(alias="traceId", min_length=1, max_length=MAX_TRACE_ID_LENGTH)
    timeout_budget_ms: int = Field(alias="timeoutBudgetMs", ge=1, le=MAX_TIMEOUT_BUDGET_MS)
    term: str = Field(min_length=1, max_length=MAX_TERM_LENGTH)
    dictionary_core: VocabularyCore = Field(alias="dictionaryCore")
    source_context: str = Field(default="", alias="sourceContext", max_length=MAX_SOURCE_CONTEXT_LENGTH)
    theme: VocabularyThemeSnapshot

    @model_validator(mode="after")
    def validate_dictionary_core_term(self) -> "VocabularyCardGenerationRequest":
        if self.dictionary_core.term != self.term:
            raise ValueError("dictionaryCore.term must match term")
        return self


class VocabularyGenerationMetadata(StrictVocabularyModel):
    provider: str = Field(min_length=1, max_length=100)
    model: str = Field(min_length=1, max_length=200)
    prompt_version: str = Field(alias="promptVersion", min_length=1, max_length=200)
    model_call_count: int = Field(alias="modelCallCount", ge=1, le=2)
    trace_id: str = Field(alias="traceId", min_length=1, max_length=MAX_TRACE_ID_LENGTH)


class VocabularyCardGenerationResponse(StrictVocabularyModel):
    contract_version: Literal[1] = Field(alias="contractVersion")
    core_schema_version: Literal[1] = Field(alias="coreSchemaVersion")
    core: VocabularyCore
    content_markdown: str = Field(alias="contentMarkdown", max_length=20_000)
    content_format_version: Literal[1] = Field(alias="contentFormatVersion")
    outcome: VocabularyGenerationOutcome
    warning: Literal["markdown_unavailable"] | None = None
    generation: VocabularyGenerationMetadata

    @field_validator("content_markdown")
    @classmethod
    def reject_raw_html(cls, value: str) -> str:
        return validate_markdown_content(value, require_nonempty=False)

    @model_validator(mode="after")
    def validate_outcome_markdown_state(self) -> "VocabularyCardGenerationResponse":
        if self.outcome == "complete":
            validate_markdown_content(self.content_markdown, require_nonempty=True)
            if self.warning is not None:
                raise ValueError("complete response must have warning: null")
            return self

        if self.content_markdown != "":
            raise ValueError("partial response must have empty contentMarkdown")
        if self.warning != "markdown_unavailable":
            raise ValueError("partial response must have warning: markdown_unavailable")
        return self


class VocabularyCoreFallbackOutput(VocabularyCore):
    pass


class VocabularyMarkdownOutput(StrictVocabularyModel):
    content_markdown: str = Field(
        min_length=1,
        max_length=20_000,
        validation_alias="contentMarkdown",
        serialization_alias="contentMarkdown",
    )

    @field_validator("content_markdown")
    @classmethod
    def reject_invalid_markdown(cls, value: str) -> str:
        return validate_markdown_content(value, require_nonempty=True)
