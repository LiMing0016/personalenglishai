import os
import unittest
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from python.ai_orchestrator.raw_assistant_service import RawSingleAgentService
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.services.agent_session_runner import AgentSessionResult
from python.ai_orchestrator.services.agent_session_runner import AgentSessionRunItems
from python.ai_orchestrator.services.agent_session_runner import AgentSessionSource
from python.ai_orchestrator.services.agent_session_runner import AgentSessionUsage
from python.ai_orchestrator.services.raw_fetch_mcp import RawFetchMcpConfig


class RawAssistantServiceTest(unittest.IsolatedAsyncioTestCase):
    def test_from_env_reads_fetch_mcp_switch(self) -> None:
        with patch.dict(
            os.environ,
            {
                "OPENAI_API_KEY": "test-key",
                "AI_ASSISTANT_RAW_FETCH_MCP_ENABLED": "true",
            },
            clear=True,
        ):
            service = RawSingleAgentService.from_env()

        self.assertTrue(service.fetch_mcp_config.enabled)

    async def test_run_injects_connected_fetch_server_into_same_agent(self) -> None:
        fetch_server = object()
        built_agent = object()
        service = RawSingleAgentService(
            model="test-model",
            session_db_path="unused.db",
            fetch_mcp_config=RawFetchMcpConfig(enabled=True),
        )

        @asynccontextmanager
        async def fake_connected(config):
            self.assertIs(config, service.fetch_mcp_config)
            yield (fetch_server,)

        with patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=True):
            with patch(
                "python.ai_orchestrator.raw_assistant_service.connected_raw_fetch_mcp_servers",
                fake_connected,
            ):
                with patch(
                    "python.ai_orchestrator.raw_assistant_service.create_raw_single_agent",
                    return_value=built_agent,
                ) as create_agent:
                    with patch(
                        "python.ai_orchestrator.raw_assistant_service.run_agent_session",
                        new_callable=AsyncMock,
                        return_value=AgentSessionResult(
                            final_output="回答",
                            agent_name="Raw Single Agent",
                        ),
                    ) as runner:
                        await service.run_assistant_request(self._request("hello"))

        create_agent.assert_called_once_with("test-model", mcp_servers=(fetch_server,))
        self.assertIs(runner.await_args.kwargs["agent"], built_agent)

    async def test_run_uses_cached_plain_agent_when_fetch_connection_falls_back(self) -> None:
        plain_agent = object()
        service = RawSingleAgentService(
            model="test-model",
            session_db_path="unused.db",
            fetch_mcp_config=RawFetchMcpConfig(enabled=True),
        )
        service._agent = plain_agent

        @asynccontextmanager
        async def fake_connected(config):
            self.assertIs(config, service.fetch_mcp_config)
            yield ()

        with patch(
            "python.ai_orchestrator.raw_assistant_service.connected_raw_fetch_mcp_servers",
            fake_connected,
        ):
            with patch(
                "python.ai_orchestrator.raw_assistant_service.create_raw_single_agent"
            ) as create_agent:
                with patch(
                    "python.ai_orchestrator.raw_assistant_service.run_agent_session",
                    new_callable=AsyncMock,
                    return_value=AgentSessionResult(
                        final_output="回答",
                        agent_name="Raw Single Agent",
                    ),
                ) as runner:
                    await service.run_assistant_request(self._request("hello"))

        create_agent.assert_not_called()
        self.assertIs(runner.await_args.kwargs["agent"], plain_agent)

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

    async def test_run_records_web_search_and_appends_returned_sources(self) -> None:
        service = RawSingleAgentService(model="test-model", session_db_path="unused.db")
        service._agent = object()
        result = AgentSessionResult(
            final_output="安顺今天多云。",
            agent_name="Raw Single Agent",
            run_items=AgentSessionRunItems(
                tool_call_count=1,
                tool_names=("web_search",),
            ),
            sources=(
                AgentSessionSource(
                    title="天气来源",
                    url="https://weather.example/anshun",
                ),
            ),
        )

        with patch(
            "python.ai_orchestrator.raw_assistant_service.run_agent_session",
            new_callable=AsyncMock,
            return_value=result,
        ):
            reply = await service.run_assistant_request(self._request("安顺今天的天气怎么样？"))

        self.assertIn("[天气来源](https://weather.example/anshun)", reply.reply)
        self.assertEqual(reply.run.steps[-1]["stepType"], "tool_calls")
        self.assertEqual(reply.run.steps[-1]["toolNames"], ["web_search"])

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

    async def test_stream_keeps_fetch_connection_open_until_sdk_completion(self) -> None:
        fetch_server = object()
        built_agent = object()
        lifecycle = []
        service = RawSingleAgentService(
            model="test-model",
            session_db_path="unused.db",
            fetch_mcp_config=RawFetchMcpConfig(enabled=True),
        )

        @asynccontextmanager
        async def fake_connected(config):
            self.assertIs(config, service.fetch_mcp_config)
            lifecycle.append("connected")
            try:
                yield (fetch_server,)
            finally:
                lifecycle.append("cleaned")

        async def fake_stream(**kwargs):
            self.assertIs(kwargs["agent"], built_agent)
            lifecycle.append("runner/agent")
            yield SimpleNamespace(
                type="completed",
                delta="",
                result=AgentSessionResult(
                    final_output="回答",
                    agent_name="Raw Single Agent",
                ),
            )
            lifecycle.append("completed")

        with patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}, clear=True):
            with patch(
                "python.ai_orchestrator.raw_assistant_service.connected_raw_fetch_mcp_servers",
                fake_connected,
            ):
                with patch(
                    "python.ai_orchestrator.raw_assistant_service.create_raw_single_agent",
                    return_value=built_agent,
                ) as create_agent:
                    with patch(
                        "python.ai_orchestrator.raw_assistant_service.stream_agent_session",
                        fake_stream,
                    ):
                        events = [
                            event
                            async for event in service.stream_assistant_request(
                                self._request("hello")
                            )
                        ]

        create_agent.assert_called_once_with("test-model", mcp_servers=(fetch_server,))
        self.assertEqual(lifecycle, ["connected", "runner/agent", "completed", "cleaned"])
        self.assertEqual(events[-1]["type"], "run.completed")

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
