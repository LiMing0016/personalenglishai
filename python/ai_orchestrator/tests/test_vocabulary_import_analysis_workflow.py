from __future__ import annotations

import base64
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock, patch

from python.ai_orchestrator.schemas.vocabulary_import_analysis import (
    PROMPT_VERSION,
    VocabularyImportAnalysisModelItem,
    VocabularyImportAnalysisModelOutput,
    VocabularyImportAnalysisRequest,
)
from python.ai_orchestrator.workflows.vocabulary_import_analysis import (
    VocabularyImportAnalysisError,
    VocabularyImportAnalysisWorkflow,
)


FINGERPRINT = "a" * 64
IMAGE = b"private-image-marker"


def request(**overrides: object) -> VocabularyImportAnalysisRequest:
    payload: dict[str, object] = {
        "contractVersion": 1,
        "traceId": "vocab-import-123",
        "inputFingerprint": FINGERPRINT,
        "language": "en",
        "text": "package\nreceive",
        "fileName": "words.png",
        "contentType": "image/png",
        "content": IMAGE,
    }
    return VocabularyImportAnalysisRequest.model_validate({**payload, **overrides})


def item(**overrides: object) -> VocabularyImportAnalysisModelItem:
    payload: dict[str, object] = {
        "observedText": "Package",
        "normalizedTerm": "package",
        "status": "accepted",
        "suggestions": [],
        "contextText": None,
        "confidence": 0.95,
        "evidence": "text_image",
    }
    return VocabularyImportAnalysisModelItem.model_validate({**payload, **overrides})


def output() -> VocabularyImportAnalysisModelOutput:
    return VocabularyImportAnalysisModelOutput(rawText="Package", items=[item()])


def result(final_output: object):
    return SimpleNamespace(final_output=final_output, context_wrapper=SimpleNamespace(usage=None))


def workflow(clock: Mock | None = None) -> VocabularyImportAnalysisWorkflow:
    return VocabularyImportAnalysisWorkflow(
        model="test-model",
        timeout_seconds=45,
        monotonic_clock=clock,
    )


class VocabularyImportAnalysisWorkflowTest(unittest.IsolatedAsyncioTestCase):
    async def test_combined_input_uses_one_instruction_with_text_and_image(self) -> None:
        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=result(output())) as run:
            response = await workflow().analyze(request())

        self.assertEqual(run.await_count, 1)
        agent, input_items = run.call_args.args
        self.assertEqual(agent.name, "Vocabulary import analysis")
        parts = input_items[0]["content"]
        self.assertEqual([part["type"] for part in parts], ["input_text", "input_image"])
        self.assertIn("package\nreceive", parts[0]["text"])
        self.assertEqual(
            parts[1]["image_url"],
            "data:image/png;base64," + base64.b64encode(IMAGE).decode("ascii"),
        )
        self.assertEqual(response.input_fingerprint, FINGERPRINT)
        self.assertEqual(response.items[0].evidence, "text_image")
        self.assertEqual(response.generation.prompt_version, PROMPT_VERSION)

    async def test_retry_uses_remaining_part_of_single_45_second_budget(self) -> None:
        clock = Mock(side_effect=[100.0, 100.0, 130.0, 131.0])
        timeouts: list[float] = []

        async def immediate_wait_for(awaitable, *, timeout: float):
            timeouts.append(timeout)
            return await awaitable

        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[result(object()), result(output())],
        ) as run, patch(
            "python.ai_orchestrator.workflows.vocabulary_import_analysis.asyncio.wait_for",
            new=immediate_wait_for,
        ):
            response = await workflow(clock).analyze(request())

        self.assertEqual(run.await_count, 2)
        self.assertEqual(timeouts, [45.0, 15.0])
        self.assertEqual(response.generation.model_call_count, 2)

    async def test_exhausted_budget_does_not_start_a_third_call(self) -> None:
        clock = Mock(side_effect=[200.0, 200.0, 245.0, 245.0])

        async def immediate_wait_for(awaitable, *, timeout: float):
            return await awaitable

        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            return_value=result(object()),
        ) as run, patch(
            "python.ai_orchestrator.workflows.vocabulary_import_analysis.asyncio.wait_for",
            new=immediate_wait_for,
        ):
            with self.assertRaises(VocabularyImportAnalysisError) as raised:
                await workflow(clock).analyze(request())

        self.assertEqual(run.await_count, 1)
        self.assertEqual(raised.exception.code, "MODEL_TIMEOUT")
        self.assertTrue(raised.exception.retryable)


if __name__ == "__main__":
    unittest.main()
