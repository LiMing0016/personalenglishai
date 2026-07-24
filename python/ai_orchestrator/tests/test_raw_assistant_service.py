import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from python.ai_orchestrator.raw_assistant_service import RawSingleAgentService
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.services.agent_session_runner import AgentSessionResult
from python.ai_orchestrator.services.agent_session_runner import AgentSessionRunItems
from python.ai_orchestrator.services.agent_session_runner import AgentSessionUsage


class RawAssistantServiceTest(unittest.IsolatedAsyncioTestCase):
    async def test_run_uses_isolated_sdk_session_and_returns_raw_metadata(self) -> None:
        service = RawSingleAgentService(model="test-model", session_db_path="unused.db")
        service._agent = object()
        request = self._request("hive 是什么意思？")
        result = AgentSessionResult(
            final_output="hive 可以表示蜂巢。",
            agent_name="Raw Single Agent",
            usage=AgentSessionUsage(requests=1, input_tokens=10, output_tokens=8, total_tokens=18),
            run_items=AgentSessionRunItems(last_response_id="resp-1"),
        )

        with patch(
            "python.ai_orchestrator.raw_assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=result,
        ) as runner:
            reply = await service.run_assistant_request(request)

        self.assertEqual(reply.reply, "hive 可以表示蜂巢。")
        self.assertEqual(reply.parts, [])
        self.assertEqual(reply.run.agent_mode, "single_agent_raw")
        self.assertIsNone(reply.run.route_request)
        self.assertIsNone(reply.run.routing_decision)
        self.assertEqual(runner.await_args.kwargs["conversation_id"], "single-raw:conv-1")
        self.assertTrue(runner.await_args.kwargs["use_session"])
        self.assertEqual(runner.await_args.kwargs["agent_input"], "hive 是什么意思？")

    async def test_explicit_history_replay_disables_sdk_session(self) -> None:
        service = RawSingleAgentService(model="test-model", session_db_path="unused.db")
        service._agent = object()
        request = self._request(
            "再来两个。",
            conversationHistory=[
                {"role": "user", "content": "hive 是什么意思？"},
                {"role": "assistant", "content": "hive 可以表示蜂巢。"},
            ],
        )

        with patch(
            "python.ai_orchestrator.raw_assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=AgentSessionResult(final_output="例句。", agent_name="Raw Single Agent"),
        ) as runner:
            await service.run_assistant_request(request)

        self.assertFalse(runner.await_args.kwargs["use_session"])
        self.assertIsInstance(runner.await_args.kwargs["agent_input"], list)

    async def test_stream_matches_existing_event_protocol(self) -> None:
        service = RawSingleAgentService(model="test-model", session_db_path="unused.db")
        service._agent = object()

        async def fake_stream(**kwargs):
            yield SimpleNamespace(type="delta", delta="蜂", result=None)
            yield SimpleNamespace(type="delta", delta="巢", result=None)
            yield SimpleNamespace(
                type="completed",
                delta="",
                result=AgentSessionResult(final_output="蜂巢", agent_name="Raw Single Agent"),
            )

        with patch("python.ai_orchestrator.raw_assistant_service.stream_agent_session", fake_stream):
            events = [event async for event in service.stream_assistant_request(self._request("hive"))]

        self.assertEqual(
            [event["type"] for event in events],
            [
                "run.started",
                "message.created",
                "message.delta",
                "message.delta",
                "message.completed",
                "run.completed",
            ],
        )
        self.assertEqual(events[4]["parts"], [])
        self.assertEqual(events[5]["run"]["agentMode"], "single_agent_raw")

    async def test_failure_does_not_invoke_another_service(self) -> None:
        service = RawSingleAgentService(model="test-model", session_db_path="unused.db")
        service._agent = object()

        with patch(
            "python.ai_orchestrator.raw_assistant_service.run_agent_session",
            new_callable=AsyncMock,
            side_effect=RuntimeError("raw failed"),
        ):
            with self.assertRaisesRegex(RuntimeError, "raw failed"):
                await service.run_assistant_request(self._request("hello"))

    def _request(self, message: str, **overrides) -> AssistantRequest:
        payload = {
            "appConversationId": "conv-1",
            "clientMessageId": "client-1",
            "agentMode": "single_agent_raw",
            "mode": "daily_explain",
            "intent": "free_chat",
            "scope": "message_only",
            "message": {"text": message},
        }
        payload.update(overrides)
        return AssistantRequest.model_validate(payload)


if __name__ == "__main__":
    unittest.main()
