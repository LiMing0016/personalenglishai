from __future__ import annotations

from datetime import UTC, datetime
from typing import Literal

from pydantic import BaseModel, Field


class DictionaryRawEntry(BaseModel):
    headword: str
    html: str
    source_entry_id: str | None = None


class DictionaryPhonetic(BaseModel):
    text: str
    region: str | None = None


class DictionaryExample(BaseModel):
    headword: str | None = None
    text_en: str
    text_zh: str | None = None
    source: str | None = None


class DictionarySense(BaseModel):
    definition_en: str | None = None
    definition_zh: str | None = None
    examples: list[DictionaryExample] = Field(default_factory=list)


class DictionaryPhrase(BaseModel):
    text: str
    definition_en: str | None = None
    definition_zh: str | None = None
    examples: list[DictionaryExample] = Field(default_factory=list)


class DictionaryCleanEntry(BaseModel):
    word: str
    part_of_speech: str | None = None
    phonetics: list[DictionaryPhonetic] = Field(default_factory=list)
    senses: list[DictionarySense] = Field(default_factory=list)
    phrases: list[DictionaryPhrase] = Field(default_factory=list)
    usage_notes: list[str] = Field(default_factory=list)
    clean_text: str
    source_entry_id: str | None = None


class DictionaryCleaningSummary(BaseModel):
    entry_count: int = 0
    sense_count: int = 0
    example_count: int = 0
    phrase_count: int = 0
    warning_count: int = 0


class DictionaryCleaningResult(BaseModel):
    workflow: Literal["dictionary_cleaning"] = "dictionary_cleaning"
    status: Literal["completed", "completed_with_warnings", "failed"]
    source_code: str
    display_name: str
    generated_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    summary: DictionaryCleaningSummary
    entries: list[DictionaryCleanEntry] = Field(default_factory=list)
    resources: list[dict[str, object]] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
