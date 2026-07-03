import unittest

from python.ai_orchestrator.adapters.route_request_adapter import build_route_request
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest


class RouteRequestAdapterTest(unittest.TestCase):
    def test_build_route_request_from_assistant_request(self) -> None:
        assistant_request = AssistantRequest.model_validate(
            {
                "appConversationId": "conv-1",
                "clientMessageId": "msg-1",
                "mode": "exam_boost",
                "intent": "grade_writing",
                "message": {"text": "帮我判断是否跑题"},
                "selection": {
                    "text": "In my opinion, phones are helpful.",
                    "source": "writing_editor",
                    "documentId": "doc-1",
                },
                "studyContext": {"studyStage": "middle_school", "targetExam": "中考"},
                "clientMeta": {"sourcePage": "writing_editor"},
            }
        )

        route_request = build_route_request(
            assistant_request,
            user_id="user-1",
            essay_text="In my opinion, phones are helpful.",
            topic_prompt="Should students bring phones to school?",
        )

        self.assertEqual(route_request.message, "帮我判断是否跑题")
        self.assertEqual(route_request.conversation_id, "conv-1")
        self.assertEqual(route_request.user_id, "user-1")
        self.assertEqual(route_request.study_stage, "middle_school")
        self.assertEqual(route_request.assistant_mode, "exam_boost")
        self.assertEqual(route_request.context.selected_text, "In my opinion, phones are helpful.")
        self.assertEqual(route_request.context.current_page, "writing_editor")
        self.assertTrue(route_request.context.has_essay_text)
        self.assertTrue(route_request.context.has_topic_prompt)

    def test_build_route_request_prefers_explicit_runtime_context(self) -> None:
        assistant_request = AssistantRequest(
            clientMessageId="msg-1",
            mode="daily_explain",
            intent="free_chat",
            message={"text": "这句话怎么样"},
            selection={"text": "old selection", "source": "writing_editor"},
        )

        route_request = build_route_request(assistant_request, selected_text="new selection", current_page="coach")

        self.assertEqual(route_request.context.selected_text, "new selection")
        self.assertEqual(route_request.context.current_page, "coach")

    def test_build_route_request_includes_bounded_recent_conversation_history(self) -> None:
        assistant_request = AssistantRequest(
            clientMessageId="msg-1",
            mode="daily_explain",
            intent="free_chat",
            message={"text": "那它怎么造句？"},
            conversationHistory=[
                {"role": "user", "content": "old"},
                {"role": "assistant", "content": "old reply"},
                {"role": "user", "content": "citation 是什么意思？"},
                {"role": "assistant", "content": "citation 表示引用、引证。"},
                {"role": "user", "content": "那它和 reference 有什么区别？"},
                {"role": "assistant", "content": "reference 更偏文献或参考对象。"},
                {"role": "user", "content": " "},
            ],
        )

        route_request = build_route_request(assistant_request)

        self.assertEqual(len(route_request.context.conversation_history), 5)
        self.assertEqual(route_request.context.conversation_history[0].content, "old reply")
        self.assertEqual(route_request.context.conversation_history[-1].content, "reference 更偏文献或参考对象。")


if __name__ == "__main__":
    unittest.main()
