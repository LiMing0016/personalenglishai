import unittest

from pydantic import ValidationError

from python.ai_orchestrator.schemas.routing_state import (
    ActiveTaskState,
    ContinuationAction,
    ContinuationClassifierInput,
    ContinuationDecision,
    ContinuationRelation,
)
from python.ai_orchestrator.services.continuation_classifier import (
    ContinuationClassifier,
    should_run_continuation_classifier,
)


class ContinuationClassifierSchemaTest(unittest.TestCase):
    def test_active_task_state_round_trips_with_capabilities(self) -> None:
        state = ActiveTaskState(
            conversation_id="conv-1",
            active_intent="learning_planner",
            active_agent="Learning Planner Agent",
            task_title="英语作文学习规划",
            task_summary="用户想获得学好英语作文的可执行学习方案。",
            last_user_message="你可以给我规划一下如何学好英语作文？",
            last_output_type="plan",
            continuation_capabilities={"more_options", "expand_detail"},
            turn_id="turn-1",
        )

        restored = ActiveTaskState.model_validate_json(state.model_dump_json())

        self.assertEqual(restored.active_intent, "learning_planner")
        self.assertIn("more_options", restored.continuation_capabilities)

    def test_continuation_decision_rejects_unknown_relation_and_action(self) -> None:
        with self.assertRaises(ValidationError):
            ContinuationDecision(
                relation="maybe_later",
                resolved_intent=None,
                continuation_action="none",
                reason="invalid",
                confidence=0.5,
            )

        with self.assertRaises(ValidationError):
            ContinuationDecision(
                relation="continue_previous_task",
                resolved_intent="learning_planner",
                continuation_action="surprise_me",
                reason="invalid",
                confidence=0.5,
            )

    def test_classifier_input_accepts_optional_active_state(self) -> None:
        payload = ContinuationClassifierInput(current_user_message="继续", active_task_state=None)

        self.assertEqual(payload.current_user_message, "继续")
        self.assertIsNone(payload.active_task_state)


class ContinuationClassifierServiceTest(unittest.IsolatedAsyncioTestCase):
    def test_precheck_skips_when_no_active_state(self) -> None:
        self.assertFalse(should_run_continuation_classifier("继续", None))

    def test_precheck_runs_for_contextual_short_message(self) -> None:
        state = ActiveTaskState(
            conversation_id="conv-1",
            active_intent="learning_planner",
            active_agent="Learning Planner Agent",
            task_title="英语作文学习规划",
            task_summary="用户想获得学好英语作文的可执行学习方案。",
            last_user_message="你可以给我规划一下如何学好英语作文？",
            last_output_type="plan",
            continuation_capabilities={"more_options", "expand_detail"},
            turn_id="turn-1",
        )

        self.assertTrue(should_run_continuation_classifier("还有其他方案吗？", state))

    def test_precheck_skips_for_complete_new_task(self) -> None:
        state = ActiveTaskState(
            conversation_id="conv-1",
            active_intent="learning_planner",
            active_agent="Learning Planner Agent",
            task_title="英语作文学习规划",
            task_summary="用户想获得学好英语作文的可执行学习方案。",
            last_user_message="你可以给我规划一下如何学好英语作文？",
            last_output_type="plan",
            continuation_capabilities={"more_options", "expand_detail"},
            turn_id="turn-1",
        )

        self.assertFalse(should_run_continuation_classifier("润色这句话：I very like English.", state))

    def test_precheck_skips_for_short_acknowledgement(self) -> None:
        state = ActiveTaskState(
            conversation_id="conv-1",
            active_intent="polish",
            active_agent="Polish Agent",
            task_title="英语表达润色",
            task_summary="用户要求润色一个句子。",
            last_user_message="润色这句话：I very like English.",
            last_output_type="polished_text",
            continuation_capabilities={"more_options", "rewrite_variant"},
            turn_id="turn-1",
        )

        self.assertFalse(should_run_continuation_classifier("很不错", state))

    async def test_classifier_returns_ambiguous_when_precheck_skips(self) -> None:
        classifier = ContinuationClassifier(llm_client=None)

        decision = await classifier.classify(
            ContinuationClassifierInput(current_user_message="继续", active_task_state=None)
        )

        self.assertEqual(decision.relation, "ambiguous")
        self.assertIsNone(decision.resolved_intent)
        self.assertEqual(decision.continuation_action, "none")
        self.assertEqual(decision.confidence, 0)

    async def test_classifier_validates_structured_llm_output(self) -> None:
        class FakeClient:
            async def classify(self, prompt: str, payload: ContinuationClassifierInput) -> dict:
                self.prompt = prompt
                self.payload = payload
                return {
                    "relation": "continue_previous_task",
                    "resolved_intent": "learning_planner",
                    "continuation_action": "more_options",
                    "target_task_title": "英语作文学习规划",
                    "reason": "用户要求更多方案，延续上一轮学习规划。",
                    "confidence": 0.92,
                }

        state = ActiveTaskState(
            conversation_id="conv-1",
            active_intent="learning_planner",
            active_agent="Learning Planner Agent",
            task_title="英语作文学习规划",
            task_summary="用户想获得学好英语作文的可执行学习方案。",
            last_user_message="你可以给我规划一下如何学好英语作文？",
            last_output_type="plan",
            continuation_capabilities={"more_options", "expand_detail"},
            turn_id="turn-1",
        )
        fake_client = FakeClient()
        classifier = ContinuationClassifier(llm_client=fake_client)

        decision = await classifier.classify(
            ContinuationClassifierInput(current_user_message="还有其他方案吗？", active_task_state=state)
        )

        self.assertEqual(decision.relation, "continue_previous_task")
        self.assertEqual(decision.resolved_intent, "learning_planner")
        self.assertEqual(decision.continuation_action, "more_options")
        self.assertIn("续问判定器", fake_client.prompt)


if __name__ == "__main__":
    unittest.main()
