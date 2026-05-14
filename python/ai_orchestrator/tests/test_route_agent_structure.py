import unittest

from python.ai_orchestrator.agents.route_decision import create_route_agent
from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.routing import RoutingDecision


class RouteAgentStructureTest(unittest.TestCase):
    def test_route_agent_uses_structured_output_schema(self) -> None:
        agent = create_route_agent("test-model")

        self.assertEqual(agent.name, "RouteAgent")
        self.assertEqual(agent.model, "test-model")
        self.assertIs(agent.output_type, RoutingDecision)
        self.assertEqual(agent.handoffs, [])
        self.assertEqual(agent.tools, [])

    def test_route_prompt_defines_router_boundaries(self) -> None:
        instructions = load_agent_instructions("route_decision")

        self.assertIn("只做路由决策", instructions)
        self.assertIn("必须输出 RoutingDecision", instructions)
        self.assertIn("不要直接评分", instructions)
        self.assertIn("不要直接润色", instructions)
        self.assertIn("route_type", instructions)
        self.assertIn("run_workflow", instructions)
        self.assertIn("ask_clarification", instructions)
        self.assertIn("writing_evaluation", instructions)
        self.assertIn("first_draft_coach", instructions)


if __name__ == "__main__":
    unittest.main()
