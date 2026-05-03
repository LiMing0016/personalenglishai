from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


LearningMode = Literal["daily_explain", "exam_boost"]
AssistantIntent = Literal[
    "free_chat",
    "explain",
    "translate",
    "polish",
    "summarize",
    "grade_writing",
    "generate_examples",
    "analyze_question",
]
InputScope = Literal[
    "message_only",
    "selection",
    "attachments",
    "selection_and_message",
    "attachments_and_message",
    "selection_attachments_and_message",
]
SelectionSource = Literal[
    "assistant_message",
    "writing_editor",
    "page_selection",
    "uploaded_image_ocr",
]
AttachmentProvider = Literal["app_storage", "openai_files", "external_url"]
AttachmentKind = Literal["image", "pdf", "txt", "docx", "doc", "other"]
AttachmentProcessingStatus = Literal["uploaded", "processing", "ready", "failed"]
PreferredModelInputPart = Literal["input_image", "input_file", "input_text"]
ImageDetail = Literal["low", "high", "auto"]


class AssistantAttachmentProcessing(BaseModel):
    status: AttachmentProcessingStatus
    error_code: str | None = Field(default=None, alias="errorCode")
    extracted_text_available: bool | None = Field(default=None, alias="extractedTextAvailable")
    extracted_text: str | None = Field(default=None, alias="extractedText")
    page_count: int | None = Field(default=None, alias="pageCount")
    checksum: str | None = None

    model_config = {"populate_by_name": True}


class AssistantAttachmentModelInput(BaseModel):
    preferred_part: PreferredModelInputPart | None = Field(default=None, alias="preferredPart")
    image_detail: ImageDetail | None = Field(default=None, alias="imageDetail")

    model_config = {"populate_by_name": True}


class AssistantAttachmentRef(BaseModel):
    attachment_id: str = Field(alias="attachmentId")
    provider: AttachmentProvider
    openai_file_id: str | None = Field(default=None, alias="openaiFileId")
    storage_key: str | None = Field(default=None, alias="storageKey")
    url: str | None = None
    name: str
    mime_type: str = Field(alias="mimeType")
    size_bytes: int = Field(alias="sizeBytes")
    kind: AttachmentKind
    processing: AssistantAttachmentProcessing
    model_input: AssistantAttachmentModelInput | None = Field(default=None, alias="modelInput")

    model_config = {"populate_by_name": True}


class AssistantSelectionRange(BaseModel):
    start: int | None = None
    end: int | None = None


class AssistantSelection(BaseModel):
    text: str
    source: SelectionSource
    source_id: str | None = Field(default=None, alias="sourceId")
    message_id: str | None = Field(default=None, alias="messageId")
    document_id: str | None = Field(default=None, alias="documentId")
    range: AssistantSelectionRange | None = None

    model_config = {"populate_by_name": True}


class AssistantRequestMessage(BaseModel):
    text: str | None = None


class AssistantStudyContext(BaseModel):
    study_stage: Literal["beginner", "intermediate", "advanced"] | None = Field(default=None, alias="studyStage")
    cefr_level: Literal["A1", "A2", "B1", "B2", "C1", "C2"] | None = Field(default=None, alias="cefrLevel")
    target_exam: Literal["ielts", "toefl", "cet4", "cet6", "gaokao"] | None = Field(default=None, alias="targetExam")
    locale: Literal["zh-CN", "en-US"] | None = None
    response_language: Literal["zh-CN", "en-US", "mixed"] | None = Field(default=None, alias="responseLanguage")

    model_config = {"populate_by_name": True}


class AssistantClientMeta(BaseModel):
    source_page: str | None = Field(default=None, alias="sourcePage")
    timezone: str | None = None
    user_agent: str | None = Field(default=None, alias="userAgent")

    model_config = {"populate_by_name": True}


class AssistantRequest(BaseModel):
    app_conversation_id: str | None = Field(default=None, alias="appConversationId")
    client_message_id: str = Field(alias="clientMessageId")
    idempotency_key: str | None = Field(default=None, alias="idempotencyKey")
    mode: LearningMode
    intent: AssistantIntent
    scope: InputScope | None = None
    message: AssistantRequestMessage = Field(default_factory=AssistantRequestMessage)
    selection: AssistantSelection | None = None
    attachments: list[AssistantAttachmentRef] = Field(default_factory=list)
    study_context: AssistantStudyContext | None = Field(default=None, alias="studyContext")
    client_meta: AssistantClientMeta | None = Field(default=None, alias="clientMeta")

    model_config = {"populate_by_name": True}


class AssistantUsage(BaseModel):
    input_tokens: int | None = Field(default=None, alias="inputTokens")
    output_tokens: int | None = Field(default=None, alias="outputTokens")
    total_tokens: int | None = Field(default=None, alias="totalTokens")
    requests: int | None = None

    model_config = {"populate_by_name": True}


class AssistantOpenAIState(BaseModel):
    response_id: str | None = Field(default=None, alias="responseId")
    conversation_id: str | None = Field(default=None, alias="conversationId")
    previous_response_id: str | None = Field(default=None, alias="previousResponseId")

    model_config = {"populate_by_name": True}


class AssistantRunMetadata(BaseModel):
    run_id: str = Field(alias="runId")
    trace_id: str | None = Field(default=None, alias="traceId")
    agent_name: str = Field(alias="agentName")
    model: str
    mode: LearningMode
    intent: AssistantIntent
    scope: InputScope
    finish_reason: str | None = Field(default=None, alias="finishReason")

    model_config = {"populate_by_name": True}
