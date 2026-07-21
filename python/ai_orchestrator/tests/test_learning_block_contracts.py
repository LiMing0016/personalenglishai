import unittest

from pydantic import ValidationError

from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.schemas.learning_blocks import (
    SentenceReorderBlock,
    SentenceReorderGeneration,
)


class LearningBlockContractsTest(unittest.TestCase):
    def test_request_parses_camel_case_interaction(self) -> None:
        request = AssistantRequest.model_validate(
            {
                "clientMessageId": "client-1",
                "mode": "daily_explain",
                "intent": "free_chat",
                "message": {"text": "开始重组成句练习"},
                "interaction": {
                    "source": "quick_action",
                    "uiIntent": "start_practice",
                    "activeActivityId": "activity-1",
                    "context": {"exerciseType": "sentence_reorder", "difficulty": "easy"},
                },
            }
        )

        self.assertEqual(request.interaction.ui_intent, "start_practice")
        self.assertEqual(request.interaction.active_activity_id, "activity-1")
        self.assertEqual(request.interaction.context.exercise_type, "sentence_reorder")

    def test_request_rejects_unknown_exercise_type(self) -> None:
        with self.assertRaises(ValidationError):
            AssistantRequest.model_validate(
                {
                    "clientMessageId": "client-1",
                    "mode": "daily_explain",
                    "intent": "free_chat",
                    "interaction": {
                        "source": "quick_action",
                        "uiIntent": "start_practice",
                        "context": {"exerciseType": "unsupported"},
                    },
                }
            )

    def test_sentence_reorder_block_serializes_versioned_fallback(self) -> None:
        block = SentenceReorderBlock.model_validate(
            {
                "id": "block-1",
                "type": "sentence_reorder",
                "version": 1,
                "fallbackMarkdown": "### 重组成句",
                "data": {
                    "activityId": "activity-1",
                    "items": [
                        {
                            "id": "q1",
                            "instruction": "组成句子",
                            "tokens": [{"id": "t1", "text": "Hello"}, {"id": "t2", "text": "world"}],
                            "initialOrder": ["t2", "t1"],
                            "acceptedOrders": [["t1", "t2"]],
                        }
                    ],
                },
            }
        )

        body = block.model_dump(by_alias=True)
        self.assertEqual(body["type"], "sentence_reorder")
        self.assertEqual(body["version"], 1)
        self.assertEqual(body["fallbackMarkdown"], "### 重组成句")

    def test_generation_rejects_questions_with_fewer_than_two_chunks(self) -> None:
        with self.assertRaises(ValidationError):
            SentenceReorderGeneration.model_validate(
                {"intro": "开始练习", "questions": [{"instruction": "组成句子", "chunks": ["Hello"]}]}
            )


if __name__ == "__main__":
    unittest.main()
