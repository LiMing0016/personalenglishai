import os
import unittest
from unittest.mock import patch

from pydantic import ValidationError

from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.services.assistant_runtime_mode import (
    AssistantRuntimeModeResolver,
    build_session_key,
)


class AssistantRuntimeModeTest(unittest.TestCase):
    def test_defaults_to_multi_agent(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            resolver = AssistantRuntimeModeResolver.from_env()

        self.assertEqual(resolver.resolve(None), "multi_agent")

    def test_allows_request_override_when_enabled(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_AGENT_MODE": "multi_agent",
                "AI_ASSISTANT_AGENT_MODE_REQUEST_OVERRIDE_ENABLED": "true",
            },
            clear=True,
        ):
            resolver = AssistantRuntimeModeResolver.from_env()

        self.assertEqual(resolver.resolve("single_agent_raw"), "single_agent_raw")

    def test_ignores_request_override_when_disabled(self) -> None:
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_AGENT_MODE": "multi_agent",
                "AI_ASSISTANT_AGENT_MODE_REQUEST_OVERRIDE_ENABLED": "false",
            },
            clear=True,
        ):
            resolver = AssistantRuntimeModeResolver.from_env()

        self.assertEqual(resolver.resolve("single_agent_raw"), "multi_agent")

    def test_rejects_unknown_request_mode(self) -> None:
        with self.assertRaises(ValidationError):
            AssistantRequest.model_validate(
                {
                    "clientMessageId": "client-1",
                    "agentMode": "unknown",
                    "mode": "daily_explain",
                    "intent": "free_chat",
                    "message": {"text": "hello"},
                }
            )

    def test_builds_isolated_session_keys(self) -> None:
        self.assertEqual(build_session_key("multi_agent", "conv-1"), "multi:conv-1")
        self.assertEqual(build_session_key("single_agent_raw", "conv-1"), "single-raw:conv-1")

    def test_rejects_removed_tool_enabled_single_agent_mode(self) -> None:
        with self.assertRaises(ValidationError):
            AssistantRequest.model_validate(
                {
                    "clientMessageId": "client-1",
                    "agentMode": "single_agent_tools",
                    "mode": "daily_explain",
                    "intent": "free_chat",
                    "message": {"text": "安顺今天的天气怎么样？"},
                }
            )


if __name__ == "__main__":
    unittest.main()
