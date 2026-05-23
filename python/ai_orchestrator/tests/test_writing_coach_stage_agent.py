import unittest

from python.ai_orchestrator.agents.writing_coach import create_writing_coach_stage_agent
from python.ai_orchestrator.agents.writing_coach import structured_writing_coach_action
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.schemas.writing_coach import WritingCoachOutlineOutput
from python.ai_orchestrator.schemas.writing_coach import WritingCoachFinalDraftOutput
from python.ai_orchestrator.schemas.writing_coach import WritingCoachNextSectionOutput
from python.ai_orchestrator.schemas.writing_coach import WritingCoachPolishOutput
from python.ai_orchestrator.schemas.writing_coach import WritingCoachTopicRelevanceOutput
from python.ai_orchestrator.schemas.writing_coach import WritingCoachTopicAnalysisOutput


class WritingCoachStageAgentTest(unittest.TestCase):
    def test_structured_action_accepts_local_workflow_button_actions(self) -> None:
        analyze_request = AssistantRequest(
            appConversationId="conv-1",
            clientMessageId="client-1",
            mode="exam_boost",
            intent="first_draft_coach",
            scope="message_only",
            message={"text": "请先审题"},
            writingCoachContext={"action": "analyze"},
        )
        outline_request = AssistantRequest(
            appConversationId="conv-2",
            clientMessageId="client-2",
            mode="exam_boost",
            intent="first_draft_coach",
            scope="message_only",
            message={"text": "请搭提纲"},
            writingCoachContext={"action": "outline"},
        )
        topic_check_request = AssistantRequest(
            appConversationId="conv-3",
            clientMessageId="client-3",
            mode="exam_boost",
            intent="first_draft_coach",
            scope="message_only",
            message={"text": "检查偏题"},
            writingCoachContext={"action": "topic"},
        )
        next_request = AssistantRequest(
            appConversationId="conv-4",
            clientMessageId="client-4",
            mode="exam_boost",
            intent="first_draft_coach",
            scope="message_only",
            message={"text": "下一段怎么写"},
            writingCoachContext={"action": "next"},
        )
        polish_request = AssistantRequest(
            appConversationId="conv-5",
            clientMessageId="client-5",
            mode="exam_boost",
            intent="first_draft_coach",
            scope="message_only",
            message={"text": "润色"},
            writingCoachContext={"action": "polish"},
        )
        draft_request = AssistantRequest(
            appConversationId="conv-6",
            clientMessageId="client-6",
            mode="exam_boost",
            intent="first_draft_coach",
            scope="message_only",
            message={"text": "生成终稿"},
            writingCoachContext={"action": "draft"},
        )

        self.assertEqual(structured_writing_coach_action(analyze_request), "analyze")
        self.assertEqual(structured_writing_coach_action(outline_request), "outline")
        self.assertEqual(structured_writing_coach_action(topic_check_request), "topic")
        self.assertEqual(structured_writing_coach_action(next_request), "next")
        self.assertEqual(structured_writing_coach_action(polish_request), "polish")
        self.assertEqual(structured_writing_coach_action(draft_request), "draft")

    def test_stage_agents_use_distinct_output_types(self) -> None:
        analyze_agent = create_writing_coach_stage_agent("analyze", "test-model")
        outline_agent = create_writing_coach_stage_agent("outline", "test-model")
        next_agent = create_writing_coach_stage_agent("next", "test-model")
        topic_agent = create_writing_coach_stage_agent("topic", "test-model")
        polish_agent = create_writing_coach_stage_agent("polish", "test-model")
        draft_agent = create_writing_coach_stage_agent("draft", "test-model")

        self.assertEqual(analyze_agent.name, "Writing Coach Topic Analysis Agent")
        self.assertIs(analyze_agent.output_type, WritingCoachTopicAnalysisOutput)
        self.assertEqual(outline_agent.name, "Writing Coach Outline Agent")
        self.assertIs(outline_agent.output_type, WritingCoachOutlineOutput)
        self.assertEqual(next_agent.name, "Writing Coach Next Section Agent")
        self.assertIs(next_agent.output_type, WritingCoachNextSectionOutput)
        self.assertEqual(topic_agent.name, "Writing Coach Topic Relevance Agent")
        self.assertIs(topic_agent.output_type, WritingCoachTopicRelevanceOutput)
        self.assertEqual(polish_agent.name, "Writing Coach Polish Agent")
        self.assertIs(polish_agent.output_type, WritingCoachPolishOutput)
        self.assertEqual(draft_agent.name, "Writing Coach Final Draft Agent")
        self.assertIs(draft_agent.output_type, WritingCoachFinalDraftOutput)


if __name__ == "__main__":
    unittest.main()
