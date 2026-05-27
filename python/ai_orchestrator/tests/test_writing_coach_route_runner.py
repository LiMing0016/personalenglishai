import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.schemas.writing_coach import WritingCoachRouteDecision
from python.ai_orchestrator.services.writing_coach_route_runner import WritingCoachRouteRunner


class WritingCoachRouteRunnerTest(unittest.IsolatedAsyncioTestCase):
    async def test_runner_returns_structured_final_output(self) -> None:
        decision = WritingCoachRouteDecision(
            routeType="run_stage",
            targetAction="polish",
            editIntent="replace_selection",
            contextPolicy={
                "includeTopic": True,
                "includeRubric": True,
                "includeSelection": True,
                "includeDraft": False,
                "includeRecentMessages": True,
            },
            confidence=0.91,
            missingInputs=[],
            reason="User asks to polish selected text.",
        )
        request = AssistantRequest(
            appConversationId="conv-writing-route-1",
            clientMessageId="client-writing-route-1",
            mode="exam_boost",
            intent="first_draft_coach",
            scope="selection_and_message",
            message={"text": "帮我润色这句话"},
            selection={"text": "This sentence is not good.", "source": "writing_editor"},
            writingCoachContext={
                "action": "coach",
                "essayQuestion": "Should College Chinese be compulsory?",
                "selectedText": "This sentence is not good.",
            },
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output=decision)) as run:
            runner = WritingCoachRouteRunner(model="test-model")
            result = await runner.route(request)

        self.assertEqual(result.route_type, "run_stage")
        self.assertEqual(result.target_action, "polish")
        self.assertEqual(result.edit_intent, "replace_selection")
        run.assert_awaited_once()
        agent, agent_input = run.call_args.args
        self.assertEqual(agent.name, "WritingCoachRouteAgent")
        self.assertIn("帮我润色这句话", agent_input)
        self.assertIn("has_selected_text", agent_input)

    async def test_runner_rejects_invalid_final_output(self) -> None:
        request = AssistantRequest(
            appConversationId="conv-writing-route-2",
            clientMessageId="client-writing-route-2",
            mode="exam_boost",
            intent="first_draft_coach",
            scope="message_only",
            message={"text": "随便聊聊"},
            writingCoachContext={"action": "coach"},
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=SimpleNamespace(final_output="bad")):
            runner = WritingCoachRouteRunner(model="test-model")

            with self.assertRaises(ValueError):
                await runner.route(request)


if __name__ == "__main__":
    unittest.main()
