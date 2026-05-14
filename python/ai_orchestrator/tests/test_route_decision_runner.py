import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from python.ai_orchestrator.schemas.routing import RouteRequest, RoutingDecision
from python.ai_orchestrator.services.route_decision_runner import RouteDecisionRunner


class RouteDecisionRunnerTest(unittest.IsolatedAsyncioTestCase):
    async def test_runner_returns_structured_final_output(self) -> None:
        decision = RoutingDecision(
            intent="writing_evaluation",
            route_type="run_workflow",
            workflow="writing_evaluation",
            target_agent="writing_evaluation",
            confidence=0.92,
            required_inputs=["essay_text", "topic_prompt"],
            missing_inputs=[],
            reason="User asks to evaluate an essay against a topic.",
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=decision)) as run:
            runner = RouteDecisionRunner(model="test-model")
            result = await runner.route(RouteRequest(message="帮我看看这篇作文是否跑题"))

        self.assertEqual(result.intent, "writing_evaluation")
        self.assertEqual(result.workflow, "writing_evaluation")
        run.assert_awaited_once()
        agent, agent_input = run.call_args.args
        self.assertEqual(agent.name, "RouteAgent")
        self.assertIn("帮我看看这篇作文是否跑题", agent_input)

    async def test_runner_coerces_dict_final_output(self) -> None:
        raw_output = {
            "intent": "free_chat",
            "route_type": "answer_direct",
            "workflow": None,
            "target_agent": None,
            "confidence": 0.6,
            "required_inputs": [],
            "missing_inputs": [],
            "reason": "Small English learning chat can be answered directly.",
        }

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=raw_output)):
            runner = RouteDecisionRunner(model="test-model")
            result = await runner.route(RouteRequest(message="谢谢"))

        self.assertEqual(result.intent, "free_chat")
        self.assertEqual(result.route_type, "answer_direct")

    async def test_runner_names_openai_trace_and_groups_by_conversation(self) -> None:
        decision = RoutingDecision(
            intent="writing_evaluation",
            route_type="run_workflow",
            workflow="writing_evaluation",
            target_agent="writing_evaluation",
            confidence=0.92,
            required_inputs=["essay_text", "topic_prompt"],
            missing_inputs=[],
            reason="User asks to evaluate an essay against a topic.",
        )
        request = RouteRequest(
            message="帮我看看这篇作文是否跑题",
            conversation_id="conv-route-1",
            user_id="user-1",
            study_stage="middle_school",
            assistant_mode="exam_boost",
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=decision)) as run:
            runner = RouteDecisionRunner(model="test-model")
            await runner.route(request)

        run_config = run.call_args.kwargs["run_config"]
        self.assertEqual(run_config.workflow_name, "PEAI RouteAgent")
        self.assertEqual(run_config.group_id, "conv-route-1")
        self.assertTrue(run_config.trace_include_sensitive_data)
        self.assertEqual(run_config.trace_metadata["component"], "route_decision_runner")
        self.assertEqual(run_config.trace_metadata["agent"], "RouteAgent")
        self.assertEqual(run_config.trace_metadata["user_id"], "user-1")
        self.assertEqual(run_config.trace_metadata["study_stage"], "middle_school")
        self.assertEqual(run_config.trace_metadata["assistant_mode"], "exam_boost")
        self.assertEqual(run_config.trace_metadata["has_essay_text"], "false")
        self.assertEqual(run_config.trace_metadata["has_topic_prompt"], "false")

    async def test_runner_flushes_trace_export_after_route_run(self) -> None:
        decision = RoutingDecision(
            intent="out_of_scope",
            route_type="out_of_scope",
            workflow=None,
            target_agent=None,
            confidence=0.98,
            required_inputs=[],
            missing_inputs=[],
            reason="Out of scope.",
        )

        with (
            patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=decision)),
            patch("agents.flush_traces") as flush_traces,
        ):
            runner = RouteDecisionRunner(model="test-model")
            await runner.route(RouteRequest(message="帮我写 Java 文件上传接口"))

        flush_traces.assert_called_once()

    async def test_runner_can_defer_trace_flush_for_outer_workflow_trace(self) -> None:
        decision = RoutingDecision(
            intent="polish",
            route_type="run_workflow",
            workflow="specialist_single_turn",
            target_agent="polish",
            confidence=0.93,
            required_inputs=[],
            missing_inputs=[],
            reason="Polish request.",
        )

        with (
            patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=decision)),
            patch("agents.flush_traces") as flush_traces,
        ):
            runner = RouteDecisionRunner(model="test-model")
            await runner.route(RouteRequest(message="润色这句话"), flush_trace=False)

        flush_traces.assert_not_called()

    async def test_runner_rejects_invalid_final_output(self) -> None:
        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output="not structured")):
            runner = RouteDecisionRunner(model="test-model")

            with self.assertRaises(ValueError):
                await runner.route(RouteRequest(message="帮我评分"))


if __name__ == "__main__":
    unittest.main()
