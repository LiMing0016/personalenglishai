import unittest

from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.services.assistant_request_validator import (
    AssistantRequestValidationError,
    infer_input_scope,
    validate_assistant_request,
)


class AssistantRequestValidatorTest(unittest.TestCase):
    def test_rejects_empty_input(self) -> None:
        request = AssistantRequest(clientMessageId="client-1", mode="daily_explain", intent="free_chat")

        with self.assertRaises(AssistantRequestValidationError) as context:
            validate_assistant_request(request)

        self.assertEqual(context.exception.code, "MISSING_INPUT")

    def test_infers_selection_and_message_scope(self) -> None:
        request = AssistantRequest(
            clientMessageId="client-1",
            mode="daily_explain",
            intent="explain",
            message={"text": "请解释"},
            selection={"text": "Selected text", "source": "page_selection"},
        )

        self.assertEqual(infer_input_scope(request), "selection_and_message")
        self.assertEqual(validate_assistant_request(request).scope, "selection_and_message")

    def test_infers_attachments_and_message_scope(self) -> None:
        request = AssistantRequest(
            clientMessageId="client-1",
            mode="daily_explain",
            intent="translate",
            message={"text": "翻译图片"},
            attachments=[self._ready_image()],
        )

        self.assertEqual(infer_input_scope(request), "attachments_and_message")

    def test_infers_selection_attachments_and_message_scope(self) -> None:
        request = AssistantRequest(
            clientMessageId="client-1",
            mode="daily_explain",
            intent="explain",
            message={"text": "结合图片解释"},
            selection={"text": "Selected text", "source": "page_selection"},
            attachments=[self._ready_image()],
        )

        self.assertEqual(infer_input_scope(request), "selection_attachments_and_message")

    def test_rejects_attachment_that_is_not_ready(self) -> None:
        image = self._ready_image()
        image["processing"]["status"] = "processing"
        request = AssistantRequest(
            clientMessageId="client-1",
            mode="daily_explain",
            intent="translate",
            message={"text": "翻译图片"},
            attachments=[image],
        )

        with self.assertRaises(AssistantRequestValidationError) as context:
            validate_assistant_request(request)

        self.assertEqual(context.exception.code, "ATTACHMENT_NOT_READY")

    def _ready_image(self) -> dict:
        return {
            "attachmentId": "att-1",
            "provider": "openai_files",
            "openaiFileId": "file-1",
            "name": "image.png",
            "mimeType": "image/png",
            "sizeBytes": 100,
            "kind": "image",
            "processing": {"status": "ready"},
            "modelInput": {"preferredPart": "input_image", "imageDetail": "auto"},
        }


if __name__ == "__main__":
    unittest.main()
