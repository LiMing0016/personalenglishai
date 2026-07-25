import sys
import types
import unittest
from unittest.mock import patch

from python.ai_orchestrator.agents.raw_single import create_raw_single_agent


class FakeAgent:
    def __init__(self, **kwargs) -> None:
        self.name = kwargs.get("name")
        self.model = kwargs.get("model")
        self.instructions = kwargs.get("instructions")
        self.tools = kwargs.get("tools", [])
        self.mcp_servers = kwargs.get("mcp_servers", [])
        self.handoffs = kwargs.get("handoffs", [])
        self.output_type = kwargs.get("output_type")
        self.kwargs = kwargs


class FakeWebSearchTool:
    pass


class RawSingleAgentTest(unittest.TestCase):
    def test_agent_has_web_search_without_application_instructions_handoffs_or_output_schema(self) -> None:
        fake_agents = types.SimpleNamespace(
            Agent=FakeAgent,
            WebSearchTool=FakeWebSearchTool,
        )
        with patch.dict(sys.modules, {"agents": fake_agents}):
            agent = create_raw_single_agent("test-model")

        self.assertEqual(agent.name, "Raw Single Agent")
        self.assertEqual(agent.model, "test-model")
        self.assertIsNone(agent.instructions)
        self.assertEqual(len(agent.tools), 1)
        self.assertIsInstance(agent.tools[0], FakeWebSearchTool)
        self.assertEqual(agent.mcp_servers, [])
        self.assertEqual(agent.handoffs, [])
        self.assertIsNone(agent.output_type)

    def test_agent_accepts_fetch_mcp_without_adding_instructions(self) -> None:
        fetch_server = object()
        fake_agents = types.SimpleNamespace(
            Agent=FakeAgent,
            WebSearchTool=FakeWebSearchTool,
        )
        with patch.dict(sys.modules, {"agents": fake_agents}):
            agent = create_raw_single_agent(
                "test-model",
                mcp_servers=(fetch_server,),
            )

        self.assertEqual(agent.mcp_servers, [fetch_server])
        self.assertIsNone(agent.instructions)
        self.assertEqual(len(agent.tools), 1)
        self.assertIsInstance(agent.tools[0], FakeWebSearchTool)


if __name__ == "__main__":
    unittest.main()
