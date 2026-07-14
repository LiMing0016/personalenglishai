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
_AUTOLINK_URI_PATTERN = re.compile(
    r"^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^\s<>]*$",
)
_AUTOLINK_EMAIL_PATTERN = re.compile(
    r"^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@"
    r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
    r"(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
)


def _closed_fenced_code_ranges(value: str) -> list[tuple[int, int]]:
    lines = value.splitlines(keepends=True)
    line_offsets: list[int] = []
    offset = 0
    for line in lines:
        line_offsets.append(offset)
        offset += len(line)

    ranges: list[tuple[int, int]] = []
    line_index = 0
    while line_index < len(lines):
        opening = _fence_opening(lines[line_index])
        if opening is None:
            line_index += 1
            continue

        marker, marker_length = opening
        closing_index = line_index + 1
        while closing_index < len(lines):
            if _is_closing_fence(lines[closing_index], marker, marker_length):
                ranges.append((line_offsets[line_index], line_offsets[closing_index] + len(lines[closing_index])))
                line_index = closing_index + 1
                break
            closing_index += 1
        else:
            # An unclosed fence is ordinary Markdown text so it cannot hide raw HTML.
            line_index += 1

    return ranges


def _fence_opening(line: str) -> tuple[str, int] | None:
    content = line.rstrip("\r\n")
    index = 0
    while index < len(content) and content[index] in " \t" and index < 3:
        index += 1
    if index == 3 and len(content) > index and content[index] in " \t":
        return None
    if index == len(content) or content[index] not in "`~":
        return None

    marker = content[index]
    marker_end = index
    while marker_end < len(content) and content[marker_end] == marker:
        marker_end += 1
    marker_length = marker_end - index
    if marker_length < 3:
        return None

    info_string = content[marker_end:]
    if marker == "`" and "`" in info_string:
        return None
    return marker, marker_length


def _is_closing_fence(line: str, marker: str, marker_length: int) -> bool:
    content = line.rstrip("\r\n")
    index = 0
    while index < len(content) and content[index] in " \t" and index < 3:
        index += 1
    if index == 3 and len(content) > index and content[index] in " \t":
        return False

    marker_end = index
    while marker_end < len(content) and content[marker_end] == marker:
        marker_end += 1
    return marker_end - index >= marker_length and content[marker_end:].strip(" \t") == ""


def _markdown_text_segments(value: str) -> list[str]:
    segments: list[str] = []
    segment_start = 0
    for fence_start, fence_end in _closed_fenced_code_ranges(value):
        segments.extend(_text_outside_inline_code(value[segment_start:fence_start]))
        segment_start = fence_end
    segments.extend(_text_outside_inline_code(value[segment_start:]))
    return segments


def _text_outside_inline_code(value: str) -> list[str]:
    segments: list[str] = []
    segment_start = 0
    index = 0
    while index < len(value):
        if value[index] != "`":
            index += 1
            continue

        delimiter_end = index
        while delimiter_end < len(value) and value[delimiter_end] == "`":
            delimiter_end += 1
        delimiter_length = delimiter_end - index
        closing_start = _matching_backtick_run(value, delimiter_end, delimiter_length)
        if closing_start is None:
            index = delimiter_end
            continue

        segments.append(value[segment_start:index])
        index = closing_start + delimiter_length
        segment_start = index
    segments.append(value[segment_start:])
    return segments


def _matching_backtick_run(value: str, start: int, length: int) -> int | None:
    index = start
    while index < len(value):
        next_backtick = value.find("`", index)
        if next_backtick == -1:
            return None

        run_end = next_backtick
        while run_end < len(value) and value[run_end] == "`":
            run_end += 1
        if run_end - next_backtick == length:
            return next_backtick
        index = run_end
    return None


def _contains_raw_html(value: str) -> bool:
    return any(_contains_raw_html_construct(segment) for segment in _markdown_text_segments(value))


def _contains_raw_html_construct(value: str) -> bool:
    index = 0
    while index < len(value):
        if value[index] != "<":
            index += 1
            continue
        if value.startswith("<!", index) or value.startswith("<?", index):
            return True

        closing_bracket = _closing_angle_bracket(value, index)
        if closing_bracket is None:
            index += 1
            continue

        construct = value[index + 1 : closing_bracket]
        if not _is_markdown_autolink(construct) and _is_html_tag(construct):
            return True
        index = closing_bracket + 1
    return False


def _closing_angle_bracket(value: str, start: int) -> int | None:
    quote: str | None = None
    for index in range(start + 1, len(value)):
        character = value[index]
        if quote is not None:
            if character == quote:
                quote = None
            continue
        if character in "\"'":
            quote = character
        elif character == ">":
            return index
    return None


def _is_markdown_autolink(value: str) -> bool:
    return bool(_AUTOLINK_URI_PATTERN.fullmatch(value) or _AUTOLINK_EMAIL_PATTERN.fullmatch(value))


def _is_html_tag(value: str) -> bool:
    index = 0
    closing_tag = value.startswith("/")
    if closing_tag:
        index = 1
    if index == len(value) or not value[index].isascii() or not value[index].isalpha():
        return False

    name_end = index + 1
    while name_end < len(value) and _is_html_tag_name_character(value[name_end]):
        name_end += 1
    remainder = value[name_end:]
    if closing_tag:
        return remainder.strip() == ""
    if remainder == "":
        return True
    if remainder.startswith("/"):
        return remainder[1:].strip() == ""
    return remainder[0].isspace()


def _is_html_tag_name_character(character: str) -> bool:
    return character.isascii() and (character.isalnum() or character in ":-")


def validate_markdown_content(value: str, *, require_nonempty: bool) -> str:
    if require_nonempty and not value.strip():
        raise ValueError("contentMarkdown must be non-empty Markdown")
    if _contains_raw_html(value):
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
