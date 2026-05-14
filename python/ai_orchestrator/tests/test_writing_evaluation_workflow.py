import unittest

from python.ai_orchestrator.schemas.routing import RouteRequest, RouteRequestContext, RoutingDecision
from python.ai_orchestrator.workflows.writing_evaluation import run_writing_evaluation_workflow


class WritingEvaluationWorkflowTest(unittest.TestCase):
    def test_workflow_placeholder_requires_essay_text_and_topic_prompt(self) -> None:
        decision = RoutingDecision(
            intent="writing_evaluation",
            route_type="run_workflow",
            workflow="writing_evaluation",
            target_agent="writing_evaluation",
            confidence=0.9,
            required_inputs=["essay_text", "topic_prompt"],
            missing_inputs=[],
            reason="Evaluate essay relevance and quality.",
        )

        result = run_writing_evaluation_workflow(
            RouteRequest(message="帮我评分", context=RouteRequestContext(essay_text="Only essay text.")),
            decision,
        )

        self.assertEqual(result.status, "needs_clarification")
        self.assertIn("topic_prompt", result.missing_inputs)

    def test_workflow_placeholder_accepts_complete_inputs(self) -> None:
        decision = RoutingDecision(
            intent="writing_evaluation",
            route_type="run_workflow",
            workflow="writing_evaluation",
            target_agent="writing_evaluation",
            confidence=0.9,
            required_inputs=["essay_text", "topic_prompt"],
            missing_inputs=[],
            reason="Evaluate essay relevance and quality.",
        )

        result = run_writing_evaluation_workflow(
            RouteRequest(
                message="帮我评分",
                context=RouteRequestContext(
                    essay_text="Students should use phones carefully.",
                    topic_prompt="Should students use phones at school?",
                ),
            ),
            decision,
        )

        self.assertEqual(result.status, "ready")
        self.assertEqual(result.workflow, "writing_evaluation")


if __name__ == "__main__":
    unittest.main()
