from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field


LearningAssetType = Literal["vocabulary", "sentence", "grammar", "expression"]
LearningAssetCopilotAction = Literal[
    "complete",
    "organize",
    "format",
    "examples",
    "expand",
    "polish",
    "custom",
]


class LearningAssetOrganizeRequest(BaseModel):
    type: LearningAssetType = "vocabulary"
    title: str = ""
    selected_text: Optional[str] = Field(
        default=None,
        validation_alias="selectedText",
        serialization_alias="selectedText",
    )
    context_text: Optional[str] = Field(
        default=None,
        validation_alias="contextText",
        serialization_alias="contextText",
    )
    current_markdown: Optional[str] = Field(
        default=None,
        validation_alias="currentMarkdown",
        serialization_alias="currentMarkdown",
    )
    mode: Literal["create", "format"] | None = None
    action: LearningAssetCopilotAction | None = None
    instruction: str | None = None

    model_config = {"populate_by_name": True}


class LearningAssetOrganizeResponse(BaseModel):
    candidate_markdown: str = Field(
        validation_alias="candidateMarkdown",
        serialization_alias="candidateMarkdown",
    )

    model_config = {"populate_by_name": True}
