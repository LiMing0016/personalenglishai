from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

try:
    from ..schemas.assistant_request import AssistantRequest
    from ..schemas.assistant_request import InputScope
except ImportError:  # pragma: no cover - script mode fallback
    from schemas.assistant_request import AssistantRequest
    from schemas.assistant_request import InputScope


AssistantRequestErrorCode = Literal[
    "INVALID_REQUEST",
    "MISSING_INPUT",
    "ATTACHMENT_NOT_READY",
    "ATTACHMENT_KIND_UNSUPPORTED",
    "ATTACHMENT_IMAGE_NOT_READABLE",
    "ATTACHMENT_FILE_NOT_READABLE",
]


class AssistantRequestValidationError(ValueError):
    def __init__(self, code: AssistantRequestErrorCode, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass(frozen=True, slots=True)
class ValidatedAssistantRequest:
    request: AssistantRequest
    scope: InputScope


def infer_input_scope(request: AssistantRequest) -> InputScope:
    has_message = bool((request.message.text if request.message else None) and request.message.text.strip())
    has_selection = bool(request.selection and request.selection.text.strip())
    has_attachments = bool(request.attachments)

    if has_message and has_selection and has_attachments:
        return "selection_attachments_and_message"
    if has_selection and has_message:
        return "selection_and_message"
    if has_attachments and has_message:
        return "attachments_and_message"
    if has_selection:
        return "selection"
    if has_attachments:
        return "attachments"
    return "message_only"


def validate_assistant_request(request: AssistantRequest) -> ValidatedAssistantRequest:
    if not request.client_message_id.strip():
        raise AssistantRequestValidationError("INVALID_REQUEST", "clientMessageId 不能为空。")

    has_message = bool((request.message.text if request.message else None) and request.message.text.strip())
    has_selection = bool(request.selection and request.selection.text.strip())
    has_attachments = bool(request.attachments)

    if not has_message and not has_selection and not has_attachments:
        raise AssistantRequestValidationError("MISSING_INPUT", "message.text、selection.text、attachments 至少需要一个。")

    if len(request.attachments) > 5:
        raise AssistantRequestValidationError("INVALID_REQUEST", "附件最多支持 5 个。")

    for attachment in request.attachments:
        if attachment.processing.status != "ready":
            raise AssistantRequestValidationError("ATTACHMENT_NOT_READY", "附件还在处理中，请稍后再发送。")

        preferred_part = attachment.model_input.preferred_part if attachment.model_input else None
        if attachment.kind == "image":
            if preferred_part not in {None, "input_image"}:
                raise AssistantRequestValidationError("ATTACHMENT_IMAGE_NOT_READABLE", "图片附件不能映射为 input_image。")
            if not attachment.openai_file_id and not attachment.url:
                raise AssistantRequestValidationError("ATTACHMENT_IMAGE_NOT_READABLE", "图片附件缺少可读取引用。")
            continue

        if attachment.kind in {"pdf", "txt", "docx", "doc"}:
            has_file_ref = bool(attachment.openai_file_id)
            has_extracted_text = bool(
                attachment.processing.extracted_text_available and (attachment.processing.extracted_text or "").strip()
            )
            if not has_file_ref and not has_extracted_text:
                raise AssistantRequestValidationError("ATTACHMENT_FILE_NOT_READABLE", "文件附件缺少 file id 或抽取文本。")
            continue

        raise AssistantRequestValidationError("ATTACHMENT_KIND_UNSUPPORTED", "暂不支持该附件类型。")

    return ValidatedAssistantRequest(request=request, scope=infer_input_scope(request))
