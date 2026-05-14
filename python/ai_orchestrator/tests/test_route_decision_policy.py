import unittest

from python.ai_orchestrator.agents.route_decision_policy import ROUTE_DECISION_REGRESSION_CASES


class RouteDecisionPolicyTest(unittest.TestCase):
    def test_route_decision_policy_has_core_workflow_coverage(self) -> None:
        cases_by_intent = {case.expected_intent for case in ROUTE_DECISION_REGRESSION_CASES}

        self.assertIn("writing_evaluation", cases_by_intent)
        self.assertIn("first_draft_coach", cases_by_intent)
        self.assertIn("realtime_sentence_feedback", cases_by_intent)
        self.assertIn("polish", cases_by_intent)
        self.assertIn("translation", cases_by_intent)
        self.assertIn("practice_design", cases_by_intent)
        self.assertIn("free_chat", cases_by_intent)

    def test_route_decision_policy_covers_missing_input_and_out_of_scope_cases(self) -> None:
        self.assertTrue(any(case.expected_route_type == "ask_clarification" for case in ROUTE_DECISION_REGRESSION_CASES))
        self.assertTrue(any(case.expected_route_type == "out_of_scope" for case in ROUTE_DECISION_REGRESSION_CASES))


if __name__ == "__main__":
    unittest.main()
