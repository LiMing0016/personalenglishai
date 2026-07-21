import unittest
from types import SimpleNamespace

from python.ai_orchestrator.schemas.assistant_request import AssistantRequest
from python.ai_orchestrator.schemas.learning_blocks import SentenceReorderGeneration
from python.ai_orchestrator.workflows.generate_sentence_reorder import SentenceReorderWorkflow


class ReverseRandom:
    def shuffle(self, values) -> None:
        values.reverse()


class FakeRunner:
    def __init__(self, output) -> None:
        self.output = output
        self.calls = []

    async def run(self, agent, agent_input, *, run_config):
        self.calls.append((agent, agent_input, run_config))
        return SimpleNamespace(
            final_output=self.output,
            context_wrapper=SimpleNamespace(usage=None),
            new_items=[],
            raw_responses=[],
        )


class SentenceReorderWorkflowTest(unittest.IsolatedAsyncioTestCase):
    async def test_model_chunks_are_converted_into_deterministic_learning_block(self) -> None:
        runner = FakeRunner(
            SentenceReorderGeneration.model_validate(
                {
                    "intro": "把单词组成正确的句子。",
                    "questions": [
                        {
                            "instruction": "组成句子",
                            "chunks": ["I", "learn", "English", "every day"],
                            "translation": "我每天学习英语。",
                            "hint": "先找主语。",
                        }
                    ],
                }
            )
        )
        workflow = SentenceReorderWorkflow(
            model="test-model",
            runner=runner,
            random_source=ReverseRandom(),
        )
        request = AssistantRequest.model_validate(
            {
                "clientMessageId": "client-1",
                "mode": "daily_explain",
                "intent": "free_chat",
                "message": {"text": "开始重组成句练习"},
                "interaction": {
                    "source": "quick_action",
                    "uiIntent": "start_practice",
                    "context": {"exerciseType": "sentence_reorder"},
                },
            }
        )

        result = await workflow.generate(request)

        self.assertEqual(result.content, "把单词组成正确的句子。")
        self.assertEqual(len(result.parts), 1)
        block = result.parts[0]
        item = block.data.items[0]
        self.assertEqual([token.id for token in item.tokens], ["q1-t1", "q1-t2", "q1-t3", "q1-t4"])
        self.assertEqual(item.accepted_orders, [["q1-t1", "q1-t2", "q1-t3", "q1-t4"]])
        self.assertEqual(item.initial_order, ["q1-t4", "q1-t3", "q1-t2", "q1-t1"])
        self.assertIn("studyStage", runner.calls[0][1])

    async def test_unchanged_shuffle_is_rotated_once(self) -> None:
        class NoopRandom:
            def shuffle(self, values) -> None:
                return None

        runner = FakeRunner(
            {"intro": "练习", "questions": [{"instruction": "组成句子", "chunks": ["Hello", "world"]}]}
        )
        workflow = SentenceReorderWorkflow(model="test-model", runner=runner, random_source=NoopRandom())
        request = AssistantRequest(
            clientMessageId="client-2",
            mode="daily_explain",
            intent="free_chat",
        )

        result = await workflow.generate(request)

        self.assertEqual(result.parts[0].data.items[0].initial_order, ["q1-t2", "q1-t1"])


if __name__ == "__main__":
    unittest.main()
