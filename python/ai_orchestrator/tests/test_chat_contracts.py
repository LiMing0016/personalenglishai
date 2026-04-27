import unittest

from python.ai_orchestrator.schemas.chat import AssistantReply, ChatResponse, UploadedAttachment


class ChatContractsTest(unittest.TestCase):
    def test_chat_response_serializes_frontend_aliases(self) -> None:
        response = ChatResponse(
            reply="hello",
            conversationId="conv-1",
            agentName="Router Agent",
        )

        self.assertEqual(
            response.model_dump(by_alias=True),
            {
                "reply": "hello",
                "conversationId": "conv-1",
                "agentName": "Router Agent",
            },
        )

    def test_service_reply_and_uploaded_attachment_contracts_are_importable(self) -> None:
        reply = AssistantReply(reply="ok", agent_name="Polish Agent")
        attachment: UploadedAttachment = {
            "filename": "draft.png",
            "content_type": "image/png",
            "content": b"image",
        }

        self.assertEqual(reply.agent_name, "Polish Agent")
        self.assertEqual(attachment["content_type"], "image/png")


if __name__ == "__main__":
    unittest.main()
