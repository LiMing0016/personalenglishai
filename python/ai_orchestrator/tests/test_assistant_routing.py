import unittest

from python.ai_orchestrator.agents.assistant_routing import route_assistant_agent
from python.ai_orchestrator.schemas.assistant_request import AssistantRequest


class AssistantRoutingTest(unittest.TestCase):
    def test_translate_routes_to_translation_agent(self) -> None:
        route = route_assistant_agent(self._request(intent="translate"))

        self.assertEqual(route.to_agent, "translationAgent")
        self.assertEqual(route.agent_name, "Translation Agent")

    def test_polish_routes_to_writing_coach_agent(self) -> None:
        route = route_assistant_agent(self._request(intent="polish"))

        self.assertEqual(route.to_agent, "writingCoachAgent")
        self.assertEqual(route.agent_name, "Polish Agent")

    def test_grade_writing_routes_to_writing_coach_agent(self) -> None:
        route = route_assistant_agent(self._request(intent="grade_writing"))

        self.assertEqual(route.to_agent, "writingCoachAgent")
        self.assertEqual(route.agent_name, "Scoring Agent")

    def test_first_draft_coach_routes_to_prompt_design_agent(self) -> None:
        route = route_assistant_agent(self._request(intent="first_draft_coach", mode="exam_boost"))

        self.assertEqual(route.to_agent, "writingCoachAgent")
        self.assertEqual(route.agent_name, "Prompt Design Agent")

    def test_analyze_question_routes_to_question_analysis_agent(self) -> None:
        route = route_assistant_agent(self._request(intent="analyze_question"))

        self.assertEqual(route.to_agent, "questionAnalysisAgent")
        self.assertEqual(route.agent_name, "Prompt Design Agent")

    def test_exam_boost_fallback_routes_to_exam_boost_agent(self) -> None:
        route = route_assistant_agent(self._request(mode="exam_boost", intent="explain"))

        self.assertEqual(route.to_agent, "examBoostAgent")
        self.assertEqual(route.agent_name, "Scoring Agent")

    def test_default_routes_to_daily_explain_agent(self) -> None:
        route = route_assistant_agent(self._request())

        self.assertEqual(route.to_agent, "dailyExplainAgent")
        self.assertEqual(route.agent_name, "Router Agent")

    def _request(self, *, mode: str = "daily_explain", intent: str = "free_chat") -> AssistantRequest:
        return AssistantRequest(
            clientMessageId="client-1",
            mode=mode,
            intent=intent,
            message={"text": "hello"},
        )


if __name__ == "__main__":
    unittest.main()
