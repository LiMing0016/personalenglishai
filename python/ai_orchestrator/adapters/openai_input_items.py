from __future__ import annotations

import base64
from typing import Iterable

try:
    from ..schemas.chat import UploadedAttachment
except ImportError:  # pragma: no cover - script mode fallback
    from schemas.chat import UploadedAttachment


def build_input_items(message: str, attachments: Iterable[UploadedAttachment]) -> list[dict]:
    text = (message or "").strip() or "请查看我上传的内容并结合它回答。"
    content: list[dict[str, str]] = [{"type": "input_text", "text": text}]

    for attachment in attachments:
        filename = (attachment.get("filename") or "attachment").strip() or "attachment"
        content_type = (attachment.get("content_type") or "application/octet-stream").strip() or "application/octet-stream"
        encoded = base64.b64encode(attachment.get("content") or b"").decode("ascii")

        if content_type.startswith("image/"):
            content.append(
                {
                    "type": "input_image",
                    "image_url": f"data:{content_type};base64,{encoded}",
                }
            )
            continue

        content.append(
            {
                "type": "input_file",
                "filename": filename,
                "file_data": encoded,
            }
        )

    return [{"role": "user", "content": content}]
