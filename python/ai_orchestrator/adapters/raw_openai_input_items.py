from __future__ import annotations

from dataclasses import dataclass

from ..schemas.assistant_request import AssistantAttachmentRef
from ..schemas.assistant_request import AssistantRequest


@dataclass(frozen=True, slots=True)
class RawAssistantInput:
    agent_input: str | list[dict]
    use_session: bool


def build_raw_assistant_input(request: AssistantRequest) -> RawAssistantInput:
    message = request.message.text or ""
    has_explicit_history = bool(request.conversation_history)
    has_rich_input = bool(request.selection or request.attachments)

    if not has_explicit_history and not has_rich_input:
        return RawAssistantInput(agent_input=message, use_session=True)

    items = _history_items(request)
    content: list[dict] = []
    if message.strip():
        content.append({"type": "input_text", "text": message})
    if request.selection and request.selection.text.strip():
        content.append({"type": "input_text", "text": request.selection.text})
    content.extend(_attachment_part(attachment) for attachment in request.attachments)
    if content:
        items.append({"role": "user", "content": content})
    return RawAssistantInput(agent_input=items, use_session=not has_explicit_history)


def _history_items(request: AssistantRequest) -> list[dict]:
    items: list[dict] = []
    for message in request.conversation_history:
        content = message.content
        if content.strip():
            content_type = "output_text" if message.role == "assistant" else "input_text"
            items.append(
                {
                    "role": message.role,
                    "content": [{"type": content_type, "text": content}],
                }
            )
    return items


def _attachment_part(attachment: AssistantAttachmentRef) -> dict:
    if attachment.kind == "image":
        part = {"type": "input_image"}
        if attachment.openai_file_id:
            part["file_id"] = attachment.openai_file_id
        elif attachment.url:
            part["image_url"] = attachment.url
        if attachment.model_input and attachment.model_input.image_detail:
            part["detail"] = attachment.model_input.image_detail
        return part

    if attachment.openai_file_id:
        return {"type": "input_file", "file_id": attachment.openai_file_id}

    return {
        "type": "input_text",
        "text": attachment.processing.extracted_text or "",
    }
