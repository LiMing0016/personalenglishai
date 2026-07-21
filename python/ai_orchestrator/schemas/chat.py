from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal, TypedDict, Union

from pydantic import BaseModel, Field

try:
    from .assistant_request import AssistantRunMetadata
    from .learning_blocks import AssistantLearningBlock
except ImportError:  # pragma: no cover - script mode fallback
    from assistant_request import AssistantRunMetadata
    from learning_blocks import AssistantLearningBlock


class UploadedAttachment(TypedDict):
    filename: str
    content_type: str
    content: bytes


@dataclass(slots=True)
class AssistantReply:
    reply: str
    agent_name: str | None
    run: AssistantRunMetadata | None = None
    parts: list[AssistantLearningBlock] = field(default_factory=list)


class ChatResponse(BaseModel):
    reply: str
    conversation_id: str = Field(alias="conversationId")
    agent_name: str | None = Field(default=None, alias="agentName")

    model_config = {"populate_by_name": True}


class AssistantRunResponse(BaseModel):
    reply: str
    conversation_id: str = Field(alias="conversationId")
    agent_name: str | None = Field(default=None, alias="agentName")
    run: AssistantRunMetadata
    parts: list[AssistantLearningBlock] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class AssistantErrorPayload(BaseModel):
    code: str
    message: str
    details: dict | list | str | int | float | bool | None = None


class RunStartedEvent(BaseModel):
    type: Literal["run.started"] = "run.started"
    run_id: str = Field(alias="runId")
    trace_id: str | None = Field(default=None, alias="traceId")
    agent_name: str = Field(alias="agentName")
    model: str

    model_config = {"populate_by_name": True}


class HandoffEvent(BaseModel):
    type: Literal["handoff"] = "handoff"
    run_id: str = Field(alias="runId")
    from_agent: str = Field(alias="fromAgent")
    to_agent: str = Field(alias="toAgent")

    model_config = {"populate_by_name": True}


class MessageCreatedEvent(BaseModel):
    type: Literal["message.created"] = "message.created"
    run_id: str = Field(alias="runId")
    message_id: str = Field(alias="messageId")
    role: Literal["assistant"] = "assistant"

    model_config = {"populate_by_name": True}


class MessageDeltaEvent(BaseModel):
    type: Literal["message.delta"] = "message.delta"
    run_id: str = Field(alias="runId")
    message_id: str = Field(alias="messageId")
    delta: str

    model_config = {"populate_by_name": True}


class MessageCompletedEvent(BaseModel):
    type: Literal["message.completed"] = "message.completed"
    run_id: str = Field(alias="runId")
    message_id: str = Field(alias="messageId")
    content: str
    parts: list[AssistantLearningBlock] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class RunCompletedEvent(BaseModel):
    type: Literal["run.completed"] = "run.completed"
    run_id: str = Field(alias="runId")
    usage: dict | None = None
    openai: dict | None = None

    model_config = {"populate_by_name": True}


class RunFailedEvent(BaseModel):
    type: Literal["run.failed"] = "run.failed"
    run_id: str = Field(alias="runId")
    error: AssistantErrorPayload

    model_config = {"populate_by_name": True}


AssistantStreamEvent = Union[
    RunStartedEvent,
    HandoffEvent,
    MessageCreatedEvent,
    MessageDeltaEvent,
    MessageCompletedEvent,
    RunCompletedEvent,
    RunFailedEvent,
]
