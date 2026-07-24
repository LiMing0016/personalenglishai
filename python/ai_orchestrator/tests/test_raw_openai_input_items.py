import unittest

from python.ai_orchestrator.adapters.raw_openai_input_items import build_raw_assistant_input
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest


class RawOpenAIInputItemsTest(unittest.TestCase):
    def test_plain_text_is_verbatim_and_uses_sdk_session(self) -> None:
        request = self._request(message="  hive 是什么意思？\n")

        raw_input = build_raw_assistant_input(request)

        self.assertEqual(raw_input.agent_input, "  hive 是什么意思？\n")
        self.assertTrue(raw_input.use_session)

    def test_business_context_is_not_injected(self) -> None:
        request = AssistantRequest.model_validate(
            {
                "clientMessageId": "client-1",
                "agentMode": "single_agent_raw",
                "mode": "exam_boost",
                "intent": "generate_examples",
                "message": {"text": "再来两个。"},
                "studyContext": {
                    "studyStage": "postgrad",
                    "targetExam": "postgrad",
                    "responseLanguage": "zh-CN",
                },
            }
        )

        raw_input = build_raw_assistant_input(request)

        self.assertEqual(raw_input.agent_input, "再来两个。")
        self.assertNotIn("考研", raw_input.agent_input)
        self.assertNotIn("Markdown", raw_input.agent_input)
        self.assertNotIn("generate_examples", raw_input.agent_input)

    def test_explicit_history_is_replayed_without_sdk_session(self) -> None:
        request = self._request(
            message="再来两个例句。",
            conversationHistory=[
                {"role": "user", "content": "hive 是什么意思？"},
                {"role": "assistant", "content": "hive 可以表示蜂巢。"},
            ],
        )

        raw_input = build_raw_assistant_input(request)

        self.assertFalse(raw_input.use_session)
        self.assertEqual(
            raw_input.agent_input,
            [
                {"role": "user", "content": [{"type": "input_text", "text": "hive 是什么意思？"}]},
                {"role": "assistant", "content": [{"type": "output_text", "text": "hive 可以表示蜂巢。"}]},
                {"role": "user", "content": [{"type": "input_text", "text": "再来两个例句。"}]},
            ],
        )

    def test_selection_is_forwarded_without_business_wrapper_and_keeps_sdk_session(self) -> None:
        request = self._request(
            message="解释这句",
            selection={
                "text": "The plan still needs careful evaluation.",
                "source": "page_selection",
            },
        )

        raw_input = build_raw_assistant_input(request)

        self.assertTrue(raw_input.use_session)
        self.assertEqual(
            raw_input.agent_input[-1]["content"],
            [
                {"type": "input_text", "text": "解释这句"},
                {"type": "input_text", "text": "The plan still needs careful evaluation."},
            ],
        )

    def _request(self, message: str, **overrides) -> AssistantRequest:
        payload = {
            "clientMessageId": "client-1",
            "agentMode": "single_agent_raw",
            "mode": "daily_explain",
            "intent": "free_chat",
            "message": {"text": message},
        }
        payload.update(overrides)
        return AssistantRequest.model_validate(payload)


if __name__ == "__main__":
    unittest.main()
