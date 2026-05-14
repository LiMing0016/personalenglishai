import unittest
from pydantic import ValidationError

from python.ai_orchestrator.schemas.routing import (
    RouteRequest,
    RouteRequestContext,
    RoutingDecision,
)


class RouteAgentSchemaTest(unittest.TestCase):
    def test_route_request_keeps_user_message_and_runtime_context_separate(self) -> None:
        request = RouteRequest(
            message="帮我看看这篇作文是否跑题",
            conversation_id="conv-1",
            user_id="user-1",
            study_stage="middle_school",
            assistant_mode="exam_boost",
            context=RouteRequestContext(
                essay_text="I think phones are useful.",
                topic_prompt="Should students use phones at school?",
                selected_text="I think phones are useful.",
                current_page="writing_editor",
            ),
        )

        self.assertEqual(request.message, "帮我看看这篇作文是否跑题")
        self.assertEqual(request.context.topic_prompt, "Should students use phones at school?")
        self.assertTrue(request.context.has_essay_text)
        self.assertTrue(request.context.has_topic_prompt)

    def test_routing_decision_requires_confidence_in_range(self) -> None:
        with self.assertRaises(ValidationError):
            RoutingDecision(
                intent="writing_evaluation",
                route_type="run_workflow",
                workflow="writing_evaluation",
                target_agent="writing_evaluation",
                confidence=1.2,
                required_inputs=["essay_text", "topic_prompt"],
                missing_inputs=[],
                reason="confidence must be normalized",
            )

    def test_run_workflow_decision_requires_workflow_and_target_agent(self) -> None:
        with self.assertRaises(ValidationError):
            RoutingDecision(
                intent="writing_evaluation",
                route_type="run_workflow",
                workflow=None,
                target_agent=None,
                confidence=0.8,
                required_inputs=["essay_text"],
                missing_inputs=[],
                reason="missing workflow should be rejected",
            )

    def test_ask_clarification_decision_requires_missing_inputs(self) -> None:
        with self.assertRaises(ValidationError):
            RoutingDecision(
                intent="writing_evaluation",
                route_type="ask_clarification",
                workflow=None,
                target_agent=None,
                confidence=0.7,
                required_inputs=["essay_text"],
                missing_inputs=[],
                reason="clarification must identify the missing input",
            )


if __name__ == "__main__":
    unittest.main()
