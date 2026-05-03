from __future__ import annotations

from dataclasses import dataclass
from typing import TypedDict

from pydantic import BaseModel, Field

try:
    from .assistant_request import AssistantRunMetadata
except ImportError:  # pragma: no cover - script mode fallback
    from assistant_request import AssistantRunMetadata


class UploadedAttachment(TypedDict):
    filename: str
    content_type: str
    content: bytes


@dataclass(slots=True)
class AssistantReply:
    reply: str
    agent_name: str | None
    run: AssistantRunMetadata | None = None


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

    model_config = {"populate_by_name": True}
