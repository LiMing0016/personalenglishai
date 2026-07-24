import unittest
from unittest.mock import AsyncMock

from python.ai_orchestrator.assistant_runtime import AssistantRuntime
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.services.assistant_runtime_mode import AssistantRuntimeModeResolver


class AssistantRuntimeTest(unittest.IsolatedAsyncioTestCase):
    async def test_dispatches_each_mode_to_its_own_service(self) -> None:
        multi = AsyncMock()
        raw = AsyncMock()
        resolver = AssistantRuntimeModeResolver(
            default_mode="multi_agent",
            request_override_enabled=True,
        )
        runtime = AssistantRuntime(multi_agent_service=multi, raw_service=raw, mode_resolver=resolver)

        multi_request = self._request(None)
        raw_request = self._request("single_agent_raw")
        await runtime.run_assistant_request(multi_request, authorization="Bearer token")
        await runtime.run_assistant_request(raw_request, authorization="Bearer token")

        multi.run_assistant_request.assert_awaited_once_with(
            multi_request,
            authorization="Bearer token",
        )
        raw.run_assistant_request.assert_awaited_once_with(
            raw_request,
            authorization="Bearer token",
        )

    async def test_raw_failure_is_not_fallen_back_to_multi_agent(self) -> None:
        multi = AsyncMock()
        raw = AsyncMock()
        raw.run_assistant_request.side_effect = RuntimeError("raw failed")
        runtime = AssistantRuntime(
            multi_agent_service=multi,
            raw_service=raw,
            mode_resolver=AssistantRuntimeModeResolver(
                default_mode="multi_agent",
                request_override_enabled=True,
            ),
        )

        with self.assertRaisesRegex(RuntimeError, "raw failed"):
            await runtime.run_assistant_request(self._request("single_agent_raw"))

        multi.run_assistant_request.assert_not_awaited()

    async def test_route_debug_remains_on_existing_multi_agent_service(self) -> None:
        multi = AsyncMock()
        raw = AsyncMock()
        runtime = AssistantRuntime(
            multi_agent_service=multi,
            raw_service=raw,
            mode_resolver=AssistantRuntimeModeResolver(
                default_mode="single_agent_raw",
                request_override_enabled=True,
            ),
        )
        request = self._request("single_agent_raw")

        await runtime.route_assistant_request(request)

        multi.route_assistant_request.assert_awaited_once_with(request, authorization=None)
        raw.route_assistant_request.assert_not_called()

    def _request(self, agent_mode: str | None) -> AssistantRequest:
        return AssistantRequest.model_validate(
            {
                "appConversationId": "conv-1",
                "clientMessageId": "client-1",
                "agentMode": agent_mode,
                "mode": "daily_explain",
                "intent": "free_chat",
                "message": {"text": "hello"},
            }
        )


if __name__ == "__main__":
    unittest.main()
