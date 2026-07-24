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
        self.handoffs = kwargs.get("handoffs", [])
        self.output_type = kwargs.get("output_type")
        self.kwargs = kwargs


class RawSingleAgentTest(unittest.TestCase):
    def test_agent_has_no_application_instructions_tools_handoffs_or_output_schema(self) -> None:
        fake_agents = types.SimpleNamespace(Agent=FakeAgent)
        with patch.dict(sys.modules, {"agents": fake_agents}):
            agent = create_raw_single_agent("test-model")

        self.assertEqual(agent.name, "Raw Single Agent")
        self.assertEqual(agent.model, "test-model")
        self.assertIsNone(agent.instructions)
        self.assertEqual(agent.tools, [])
        self.assertEqual(agent.handoffs, [])
        self.assertIsNone(agent.output_type)
        self.assertEqual(agent.kwargs, {"name": "Raw Single Agent", "model": "test-model"})


if __name__ == "__main__":
    unittest.main()
