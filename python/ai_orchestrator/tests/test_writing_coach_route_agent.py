import unittest

from python.ai_orchestrator.agents.writing_coach_route import create_writing_coach_route_agent
from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.writing_coach import WritingCoachRouteDecision


class WritingCoachRouteAgentTest(unittest.TestCase):
    def test_writing_coach_route_agent_uses_structured_output_schema(self) -> None:
        agent = create_writing_coach_route_agent("test-model")

        self.assertEqual(agent.name, "WritingCoachRouteAgent")
        self.assertEqual(agent.model, "test-model")
        self.assertIs(agent.output_type, WritingCoachRouteDecision)
        self.assertEqual(agent.handoffs, [])
        self.assertEqual(agent.tools, [])

    def test_writing_coach_route_prompt_defines_boundaries(self) -> None:
        instructions = load_agent_instructions("writing_coach_route")

        self.assertIn("只做写作教练内部路由", instructions)
        self.assertIn("WritingCoachRouteDecision", instructions)
        self.assertIn("run_stage", instructions)
        self.assertIn("answer_direct", instructions)
        self.assertIn("replace_selection", instructions)
        self.assertIn("append_paragraph", instructions)
        self.assertIn("不要生成正文", instructions)


if __name__ == "__main__":
    unittest.main()
