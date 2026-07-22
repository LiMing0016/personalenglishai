from __future__ import annotations

from typing import Annotated, Literal

from markdown_it import MarkdownIt
from markdown_it.token import Token
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


VocabularyGenerationOutcome = Literal["complete", "partial"]
VocabularyCardBlockSource = Literal["ai", "user", "assistant", "legacy"]

MAX_TERM_LENGTH = 200
MAX_SOURCE_CONTEXT_LENGTH = 10_000
MAX_SCALAR_LENGTH = 2_000
MAX_MARKDOWN_LENGTH = 20_000
MAX_PHONETIC_COUNT = 10
MAX_SENSE_COUNT = 20
MAX_MEANING_COUNT = 30
MAX_BLOCK_COUNT = 50
MAX_BLOCK_ITEM_COUNT = 50
MAX_TIMEOUT_BUDGET_MS = 60_000
MAX_OPAQUE_ID_LENGTH = 128
OPAQUE_ID_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"
MAX_PROMPT_STRATEGY_KEY_LENGTH = 100
PROMPT_STRATEGY_KEY_PATTERN = r"^[a-z0-9][a-z0-9-]{0,99}$"
_COMMONMARK = MarkdownIt("commonmark")


def _contains_raw_html(value: str) -> bool:
    return _token_contains_raw_html(_COMMONMARK.parse(value))


def _token_contains_raw_html(tokens: list[Token]) -> bool:
    for token in tokens:
        if token.type in {"html_inline", "html_block"}:
            return True
        if _token_contains_raw_html(token.children or []):
            return True
    return False


def validate_markdown_content(value: str, *, require_nonempty: bool) -> str:
    if require_nonempty and not value.strip():
        raise ValueError("Markdown content must be non-empty")
    if _contains_raw_html(value):
        raise ValueError("Markdown content must not contain raw HTML")
    return value


def _require_nonblank(value: str, field_name: str) -> str:
    if not value.strip():
        raise ValueError(f"{field_name} must contain a non-whitespace character")
    return value


class StrictVocabularyModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class VocabularyPhonetic(StrictVocabularyModel):
    region: Literal["uk", "us", "other"]
    text: str = Field(max_length=MAX_SCALAR_LENGTH)
    audio_url: str | None = Field(alias="audioUrl", max_length=MAX_SCALAR_LENGTH)


class VocabularyMeaning(StrictVocabularyModel):
    id: str = Field(
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    definition_en: str = Field(alias="definitionEn", max_length=MAX_SCALAR_LENGTH)
    definition_zh: str = Field(alias="definitionZh", max_length=MAX_SCALAR_LENGTH)


class VocabularySense(StrictVocabularyModel):
    id: str = Field(
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    part_of_speech: str = Field(alias="partOfSpeech", max_length=MAX_SCALAR_LENGTH)
    meanings: list[VocabularyMeaning] = Field(max_length=MAX_MEANING_COUNT)


class VocabularyCore(StrictVocabularyModel):
    schema_version: Literal[2] = Field(alias="schemaVersion")
    term: str = Field(min_length=1, max_length=MAX_TERM_LENGTH)
    phonetics: list[VocabularyPhonetic] = Field(max_length=MAX_PHONETIC_COUNT)
    senses: list[VocabularySense] = Field(max_length=MAX_SENSE_COUNT)

    @field_validator("term")
    @classmethod
    def require_nonblank_term(cls, value: str) -> str:
        return _require_nonblank(value, "term")

    @model_validator(mode="after")
    def require_unique_core_ids(self) -> "VocabularyCore":
        sense_ids = [sense.id for sense in self.senses]
        if len(sense_ids) != len(set(sense_ids)):
            raise ValueError("sense ids must be unique")

        meaning_ids = [meaning.id for sense in self.senses for meaning in sense.meanings]
        if len(meaning_ids) != len(set(meaning_ids)):
            raise ValueError("meaning ids must be unique across the core")
        return self


class VocabularyThemeSnapshot(StrictVocabularyModel):
    uid: str = Field(min_length=1, max_length=200)
    version: int = Field(ge=1)
    name: str = Field(min_length=1, max_length=200)
    purpose: str = Field(max_length=MAX_SOURCE_CONTEXT_LENGTH)
    prompt_strategy_key: str = Field(
        alias="promptStrategyKey",
        min_length=1,
        max_length=MAX_PROMPT_STRATEGY_KEY_LENGTH,
        pattern=PROMPT_STRATEGY_KEY_PATTERN,
    )
    content_format_version: Literal[1] = Field(alias="contentFormatVersion")


class VocabularyCardGenerationRequest(StrictVocabularyModel):
    contract_version: Literal[2] = Field(alias="contractVersion")
    core_schema_version: Literal[2] = Field(alias="coreSchemaVersion")
    card_blocks_schema_version: Literal[1] = Field(alias="cardBlocksSchemaVersion")
    request_id: str = Field(
        alias="requestId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    timeout_budget_ms: int = Field(alias="timeoutBudgetMs", ge=1, le=MAX_TIMEOUT_BUDGET_MS)
    term: str = Field(min_length=1, max_length=MAX_TERM_LENGTH)
    dictionary_core: VocabularyCore = Field(alias="dictionaryCore")
    source_context: str = Field(default="", alias="sourceContext", max_length=MAX_SOURCE_CONTEXT_LENGTH)
    theme: VocabularyThemeSnapshot

    @field_validator("term")
    @classmethod
    def require_nonblank_term(cls, value: str) -> str:
        return _require_nonblank(value, "term")

    @model_validator(mode="after")
    def validate_dictionary_core_term(self) -> "VocabularyCardGenerationRequest":
        if self.dictionary_core.term != self.term:
            raise ValueError("dictionaryCore.term must match term")
        return self


class VocabularyExampleItem(StrictVocabularyModel):
    sentence: str = Field(min_length=1, max_length=MAX_SCALAR_LENGTH)
    translation: str = Field(min_length=1, max_length=MAX_SCALAR_LENGTH)


class VocabularyExampleListContent(StrictVocabularyModel):
    items: list[VocabularyExampleItem] = Field(min_length=1, max_length=MAX_BLOCK_ITEM_COUNT)


class VocabularyCollocationItem(StrictVocabularyModel):
    expression: str = Field(min_length=1, max_length=MAX_SCALAR_LENGTH)
    translation: str = Field(min_length=1, max_length=MAX_SCALAR_LENGTH)


class VocabularyCollocationListContent(StrictVocabularyModel):
    items: list[VocabularyCollocationItem] = Field(min_length=1, max_length=MAX_BLOCK_ITEM_COUNT)


class VocabularyUsageBoundaryContent(StrictVocabularyModel):
    use_when: list[str] = Field(alias="useWhen", max_length=MAX_BLOCK_ITEM_COUNT)
    avoid_when: list[str] = Field(alias="avoidWhen", max_length=MAX_BLOCK_ITEM_COUNT)

    @model_validator(mode="after")
    def require_at_least_one_boundary(self) -> "VocabularyUsageBoundaryContent":
        if not self.use_when and not self.avoid_when:
            raise ValueError("usageBoundary must contain at least one boundary")
        return self


class VocabularyContrastRow(StrictVocabularyModel):
    term: str = Field(min_length=1, max_length=MAX_SCALAR_LENGTH)
    focus: str = Field(min_length=1, max_length=MAX_SCALAR_LENGTH)
    typical_context: str = Field(alias="typicalContext", min_length=1, max_length=MAX_SCALAR_LENGTH)


class VocabularyContrastTableContent(StrictVocabularyModel):
    rows: list[VocabularyContrastRow] = Field(min_length=1, max_length=MAX_BLOCK_ITEM_COUNT)


class VocabularyMemoryTipContent(StrictVocabularyModel):
    points: list[str] = Field(min_length=1, max_length=MAX_BLOCK_ITEM_COUNT)


class VocabularyCardBlockBase(StrictVocabularyModel):
    id: str = Field(
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    title: str = Field(min_length=1, max_length=200)
    meaning_refs: list[str] = Field(
        alias="meaningRefs",
        max_length=MAX_MEANING_COUNT,
    )
    source: VocabularyCardBlockSource
    source_ref: str | None = Field(
        alias="sourceRef",
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )
    sort_order: int = Field(alias="sortOrder", ge=0, le=1_000_000)
    user_edited: bool = Field(alias="userEdited")
    locked: bool

    @field_validator("meaning_refs")
    @classmethod
    def require_unique_meaning_refs(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("meaningRefs must be unique within a block")
        for value in values:
            if not value or len(value) > MAX_OPAQUE_ID_LENGTH:
                raise ValueError("meaningRefs must contain valid opaque ids")
            import re

            if re.fullmatch(OPAQUE_ID_PATTERN, value) is None:
                raise ValueError("meaningRefs must contain valid opaque ids")
        return values


class VocabularyExampleListBlock(VocabularyCardBlockBase):
    type: Literal["exampleList"]
    format: Literal["structured"]
    content: VocabularyExampleListContent


class VocabularyCollocationListBlock(VocabularyCardBlockBase):
    type: Literal["collocationList"]
    format: Literal["structured"]
    content: VocabularyCollocationListContent


class VocabularyUsageBoundaryBlock(VocabularyCardBlockBase):
    type: Literal["usageBoundary"]
    format: Literal["structured"]
    content: VocabularyUsageBoundaryContent


class VocabularyContrastTableBlock(VocabularyCardBlockBase):
    type: Literal["contrastTable"]
    format: Literal["structured"]
    content: VocabularyContrastTableContent


class VocabularyMemoryTipBlock(VocabularyCardBlockBase):
    type: Literal["memoryTip"]
    format: Literal["structured"]
    content: VocabularyMemoryTipContent


class VocabularyNoteBlock(VocabularyCardBlockBase):
    type: Literal["note"]
    format: Literal["markdown"]
    content: str = Field(min_length=1, max_length=MAX_MARKDOWN_LENGTH)

    @field_validator("content")
    @classmethod
    def reject_invalid_markdown(cls, value: str) -> str:
        return validate_markdown_content(value, require_nonempty=True)


VocabularyCardBlock = Annotated[
    VocabularyExampleListBlock
    | VocabularyCollocationListBlock
    | VocabularyUsageBoundaryBlock
    | VocabularyContrastTableBlock
    | VocabularyMemoryTipBlock
    | VocabularyNoteBlock,
    Field(discriminator="type"),
]


class VocabularyCardBlocks(StrictVocabularyModel):
    schema_version: Literal[1] = Field(alias="schemaVersion")
    blocks: list[VocabularyCardBlock] = Field(max_length=MAX_BLOCK_COUNT)

    @model_validator(mode="after")
    def require_unique_block_ids(self) -> "VocabularyCardBlocks":
        block_ids = [block.id for block in self.blocks]
        if len(block_ids) != len(set(block_ids)):
            raise ValueError("block ids must be unique")
        return self


class VocabularyGenerationMetadata(StrictVocabularyModel):
    provider: str = Field(min_length=1, max_length=100)
    model: str = Field(min_length=1, max_length=200)
    prompt_version: str = Field(alias="promptVersion", min_length=1, max_length=200)
    model_call_count: int = Field(alias="modelCallCount", ge=1, le=2)
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
        max_length=MAX_OPAQUE_ID_LENGTH,
        pattern=OPAQUE_ID_PATTERN,
    )


class VocabularyCardGenerationResponse(StrictVocabularyModel):
    contract_version: Literal[2] = Field(alias="contractVersion")
    core_schema_version: Literal[2] = Field(alias="coreSchemaVersion")
    card_blocks_schema_version: Literal[1] = Field(alias="cardBlocksSchemaVersion")
    core: VocabularyCore
    card_blocks: VocabularyCardBlocks = Field(alias="cardBlocks")
    outcome: VocabularyGenerationOutcome
    warning: Literal["card_blocks_unavailable"] | None = None
    generation: VocabularyGenerationMetadata

    @model_validator(mode="after")
    def validate_card_blocks_state_and_references(self) -> "VocabularyCardGenerationResponse":
        if self.card_blocks_schema_version != self.card_blocks.schema_version:
            raise ValueError("cardBlocks schema version must match cardBlocksSchemaVersion")

        meaning_ids = {
            meaning.id
            for sense in self.core.senses
            for meaning in sense.meanings
        }
        dangling_refs = {
            meaning_ref
            for block in self.card_blocks.blocks
            for meaning_ref in block.meaning_refs
            if meaning_ref not in meaning_ids
        }
        if dangling_refs:
            raise ValueError("cardBlocks contain dangling meaningRefs")

        if self.outcome == "complete":
            if not self.card_blocks.blocks:
                raise ValueError("complete response must contain at least one card block")
            if self.warning is not None:
                raise ValueError("complete response must have warning: null")
            return self

        if self.card_blocks.blocks:
            raise ValueError("partial response must have empty cardBlocks")
        if self.warning != "card_blocks_unavailable":
            raise ValueError("partial response must have warning: card_blocks_unavailable")
        return self


class VocabularyCoreFallbackOutput(VocabularyCore):
    pass


# Kept for historical Markdown revisions and compatibility readers. New generation
# responses use VocabularyCardBlocks instead.
class VocabularyMarkdownOutput(StrictVocabularyModel):
    content_markdown: str = Field(
        min_length=1,
        max_length=MAX_MARKDOWN_LENGTH,
        validation_alias="contentMarkdown",
        serialization_alias="contentMarkdown",
    )

    @field_validator("content_markdown")
    @classmethod
    def reject_invalid_markdown(cls, value: str) -> str:
        return validate_markdown_content(value, require_nonempty=True)
