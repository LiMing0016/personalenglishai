from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


PromptType = Literal["general", "material", "chart", "comic"]
CanvasAction = Literal[
    "chat_only",
    "ask_clarification",
    "propose_patch",
    "create_prompt_sheet",
    "update_prompt_sheet",
    "replace_prompt_sheet",
]


class PromptSheetPatch(BaseModel):
    task_type: str | None = Field(default=None, alias="taskType")
    prompt_type: PromptType | None = Field(default=None, alias="promptType")
    genre: str | None = None
    word_range: str | None = Field(default=None, alias="wordRange")
    requirements: str | None = None
    topic: str | None = None

    model_config = {"populate_by_name": True}


class PromptSheetChatRequest(BaseModel):
    message: str
    study_stage: str | None = Field(default=None, alias="studyStage")
    task_type: str | None = Field(default=None, alias="taskType")
    prompt_type: PromptType | None = Field(default=None, alias="promptType")
    genre: str | None = None
    word_range: str | None = Field(default=None, alias="wordRange")
    requirements: str | None = None
    current_topic: str | None = Field(default=None, alias="currentTopic")
    current_prompt_text: str | None = Field(default=None, alias="currentPromptText")
    has_canvas: bool | None = Field(default=None, alias="hasCanvas")
    ai_provider: str | None = Field(default=None, alias="aiProvider")
    user_id: int | None = Field(default=None, alias="userId")

    model_config = {"populate_by_name": True}


class PromptSheetCanvasToolInput(BaseModel):
    instruction: str
    topic: str | None = None
    task_type: str | None = Field(default=None, alias="taskType")
    genre: str | None = None
    word_range: str | None = Field(default=None, alias="wordRange")
    requirements: str | None = None
    preserve_details: list[str] = Field(default_factory=list, alias="preserveDetails")

    model_config = {"populate_by_name": True}


class GenerateExamPromptRequest(BaseModel):
    original_input: str = Field(alias="originalInput")
    topic: str
    study_stage: str | None = Field(default=None, alias="studyStage")
    prompt_type: PromptType | None = Field(default=None, alias="promptType")
    task_type: str | None = Field(default=None, alias="taskType")
    genre: str | None = None
    word_range: str | None = Field(default=None, alias="wordRange")
    requirements: str | None = None
    max_score: int | None = Field(default=None, alias="maxScore")
    ai_provider: str | None = Field(default=None, alias="aiProvider")
    user_id: int | None = Field(default=None, alias="userId")
    preserve_details: list[str] = Field(default_factory=list, alias="preserveDetails")

    model_config = {"populate_by_name": True}


class AgentUsage(BaseModel):
    requests: int = 0
    input_tokens: int = Field(default=0, alias="inputTokens")
    cached_input_tokens: int = Field(default=0, alias="cachedInputTokens")
    output_tokens: int = Field(default=0, alias="outputTokens")
    reasoning_tokens: int = Field(default=0, alias="reasoningTokens")
    total_tokens: int = Field(default=0, alias="totalTokens")
    response_id: str | None = Field(default=None, alias="responseId")
    model: str | None = None
    provider: str = "openai_agents"

    model_config = {"populate_by_name": True}


class ChartSpec(BaseModel):
    title: str | None = None
    display_type: Literal["table", "chart"] | None = Field(default=None, alias="displayType")
    columns: list[str] = Field(default_factory=list)
    rows: list[list[str]] = Field(default_factory=list)
    summary: str | None = None

    model_config = {"populate_by_name": True}


class ComicScene(BaseModel):
    title: str | None = None
    description: str | None = None
    dialogue: str | None = None


class GenerateExamPromptResponse(BaseModel):
    prompt_type: PromptType = Field(default="general", alias="promptType")
    paper: str | None = None
    prompt_sheet_id: int | None = Field(default=None, alias="promptSheetId")
    topic: str
    prompt_text: str = Field(alias="promptText")
    part: str | None = None
    question_no: str | None = Field(default=None, alias="questionNo")
    directions: str | None = None
    requirements: str | None = None
    genre: str | None = None
    word_range: str | None = Field(default=None, alias="wordRange")
    max_score: int | None = Field(default=None, alias="maxScore")
    source_type: Literal["ai_generated"] = Field(default="ai_generated", alias="sourceType")
    task_type: str | None = Field(default=None, alias="taskType")
    min_words: int | None = Field(default=None, alias="minWords")
    recommended_max_words: int | None = Field(default=None, alias="recommendedMaxWords")
    attachment_type: Literal["none", "material", "visual"] | None = Field(default=None, alias="attachmentType")
    attachment_title: str | None = Field(default=None, alias="attachmentTitle")
    attachment_content: str | None = Field(default=None, alias="attachmentContent")
    attachment_image_url: str | None = Field(default=None, alias="attachmentImageUrl")
    visual_kind: Literal["image", "comic", "chart", "table"] | None = Field(default=None, alias="visualKind")
    material_text: str | None = Field(default=None, alias="materialText")
    chart_spec: ChartSpec | None = Field(default=None, alias="chartSpec")
    comic_scenes: list[ComicScene] = Field(default_factory=list, alias="comicScenes")
    usage: AgentUsage | None = Field(default=None, alias="_usage")

    model_config = {"populate_by_name": True}


class PromptSheetChatResponse(BaseModel):
    reply: str
    action: CanvasAction = "chat_only"
    needs_canvas_update: bool = Field(default=False, alias="needsCanvasUpdate")
    needs_confirmation: bool = Field(default=False, alias="needsConfirmation")
    canvas_instruction: str | None = Field(default=None, alias="canvasInstruction")
    patch: PromptSheetPatch | None = None
    prompt_sheet: GenerateExamPromptResponse | None = Field(default=None, alias="promptSheet")
    usage: AgentUsage | None = Field(default=None, alias="_usage")

    model_config = {"populate_by_name": True}
