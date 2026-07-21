import unittest

from python.ai_orchestrator.schemas.chat import AssistantReply, AssistantRunResponse, ChatResponse, UploadedAttachment
from python.ai_orchestrator.schemas.assistant_request import AssistantRunMetadata
from python.ai_orchestrator.schemas.learning_blocks import SentenceReorderBlock


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
        self.assertEqual(reply.parts, [])
        self.assertEqual(attachment["content_type"], "image/png")

    def test_run_response_serializes_learning_parts(self) -> None:
        block = _sentence_reorder_block()
        response = AssistantRunResponse(
            reply="开始练习",
            conversationId="conv-1",
            agentName="Sentence Reorder Agent",
            run=AssistantRunMetadata(
                runId="run-1",
                agentName="Sentence Reorder Agent",
                model="test-model",
                mode="daily_explain",
                intent="free_chat",
                scope="message_only",
            ),
            parts=[block],
        )

        self.assertEqual(response.model_dump(by_alias=True)["parts"][0]["type"], "sentence_reorder")


def _sentence_reorder_block() -> SentenceReorderBlock:
    return SentenceReorderBlock.model_validate(
        {
            "id": "block-1",
            "fallbackMarkdown": "### 练习",
            "data": {
                "activityId": "activity-1",
                "items": [{
                    "id": "q1",
                    "instruction": "组成句子",
                    "tokens": [{"id": "t1", "text": "Hello"}, {"id": "t2", "text": "world"}],
                    "initialOrder": ["t2", "t1"],
                    "acceptedOrders": [["t1", "t2"]],
                }],
            },
        }
    )


if __name__ == "__main__":
    unittest.main()
