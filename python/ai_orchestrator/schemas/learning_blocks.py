from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class SentenceReorderGeneratedQuestion(BaseModel):
    instruction: str = Field(min_length=1)
    chunks: list[str] = Field(min_length=2, max_length=12)
    translation: str | None = None
    explanation: str | None = None
    hint: str | None = None


class SentenceReorderGeneration(BaseModel):
    intro: str = Field(min_length=1)
    questions: list[SentenceReorderGeneratedQuestion] = Field(min_length=1, max_length=3)


class SentenceReorderToken(BaseModel):
    id: str
    text: str


class SentenceReorderItem(BaseModel):
    id: str
    instruction: str
    translation: str | None = None
    tokens: list[SentenceReorderToken] = Field(min_length=2)
    initial_order: list[str] = Field(alias="initialOrder")
    accepted_orders: list[list[str]] = Field(alias="acceptedOrders", min_length=1)
    explanation: str | None = None
    hint: str | None = None

    model_config = {"populate_by_name": True}


class SentenceReorderData(BaseModel):
    activity_id: str = Field(alias="activityId")
    items: list[SentenceReorderItem] = Field(min_length=1)

    model_config = {"populate_by_name": True}


class SentenceReorderBlock(BaseModel):
    id: str
    type: Literal["sentence_reorder"] = "sentence_reorder"
    version: Literal[1] = 1
    title: str | None = None
    fallback_markdown: str = Field(alias="fallbackMarkdown")
    data: SentenceReorderData

    model_config = {"populate_by_name": True}


AssistantLearningBlock = SentenceReorderBlock
