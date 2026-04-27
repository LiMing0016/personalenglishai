import unittest

from python.ai_orchestrator.agents.routing_policy import ROUTING_REGRESSION_CASES
from python.ai_orchestrator.schemas.routing import HandoffRoutingMetadata


class RoutingPolicyTest(unittest.TestCase):
    def test_routing_regression_cases_cover_core_agents(self) -> None:
        expected_targets = {
            "Polish Agent",
            "Sentence Structure Agent",
            "Vocab Agent",
            "Translation Agent",
            "Scoring Agent",
            "Prompt Design Agent",
            "Ability Profile Agent",
            "Learning Planner Agent",
        }

        actual_targets = {
            case.expected_targets[0]
            for case in ROUTING_REGRESSION_CASES
            if not case.multi_intent and not case.out_of_scope
        }

        self.assertEqual(actual_targets, expected_targets)

    def test_routing_regression_cases_include_multi_intent_and_out_of_scope(self) -> None:
        multi_intent_cases = [case for case in ROUTING_REGRESSION_CASES if case.multi_intent]
        out_of_scope_cases = [case for case in ROUTING_REGRESSION_CASES if case.out_of_scope]

        self.assertTrue(multi_intent_cases)
        self.assertTrue(out_of_scope_cases)
        self.assertIn("Translation Agent", multi_intent_cases[0].expected_targets)
        self.assertIn("Polish Agent", multi_intent_cases[0].expected_targets)

    def test_handoff_metadata_restricts_intent_to_known_values(self) -> None:
        schema = HandoffRoutingMetadata.model_json_schema()
        intent_schema = schema["properties"]["intent"]

        self.assertEqual(
            set(intent_schema["enum"]),
            {
                "polish",
                "sentence_structure",
                "vocab",
                "translation",
                "scoring",
                "practice_design",
                "ability_profile",
                "learning_planner",
            },
        )


if __name__ == "__main__":
    unittest.main()
