from __future__ import annotations

import asyncio
import base64
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from agents import ModelBehaviorError

from python.ai_orchestrator.schemas.vocabulary_image_recognition import (
    MAX_CANDIDATES,
    PROMPT_VERSION,
    VocabularyImageRecognitionModelItem,
    VocabularyImageRecognitionModelOutput,
    VocabularyImageRecognitionRequest,
)
from python.ai_orchestrator.workflows.vocabulary_image_recognition import (
    VocabularyImageRecognitionError,
    VocabularyImageRecognitionWorkflow,
)


IMAGE_MARKER = b"private-image-marker"


def request(**overrides: object) -> VocabularyImageRecognitionRequest:
    payload: dict[str, object] = {
        "contractVersion": 1,
        "traceId": "vocab-image-123",
        "language": "en",
        "fileName": "words.png",
        "contentType": "image/png",
        "content": IMAGE_MARKER,
    }
    return VocabularyImageRecognitionRequest.model_validate({**payload, **overrides})


def item(
    observed_text: str = "Package",
    normalized_term: str = "package",
    **overrides: object,
) -> VocabularyImageRecognitionModelItem:
    payload: dict[str, object] = {
        "observedText": observed_text,
        "normalizedTerm": normalized_term,
        "status": "accepted",
        "suggestions": [],
        "contextText": "package delivery",
        "confidence": 0.95,
    }
    return VocabularyImageRecognitionModelItem.model_validate({**payload, **overrides})


def model_output(
    *items: VocabularyImageRecognitionModelItem,
    raw_text: str = "Package",
) -> VocabularyImageRecognitionModelOutput:
    return VocabularyImageRecognitionModelOutput(rawText=raw_text, items=list(items))


def run_result(output: object, *, input_tokens: int = 120, output_tokens: int = 18):
    return SimpleNamespace(
        final_output=output,
        context_wrapper=SimpleNamespace(
            usage=SimpleNamespace(
                input_tokens=input_tokens,
                output_tokens=output_tokens,
            )
        ),
    )


def workflow(*, timeout_seconds: float = 45.0) -> VocabularyImageRecognitionWorkflow:
    return VocabularyImageRecognitionWorkflow(
        model="test-model",
        timeout_seconds=timeout_seconds,
    )


class VocabularyImageRecognitionWorkflowTest(unittest.IsolatedAsyncioTestCase):
    async def test_recognition_uses_one_text_instruction_and_image_data_url(self) -> None:
        result = run_result(model_output(item()))

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=result) as run:
            response = await workflow().recognize(request())

        self.assertEqual(run.await_count, 1)
        agent, input_items = run.call_args.args
        self.assertEqual(agent.name, "Vocabulary image recognition")
        self.assertEqual(input_items[0]["role"], "user")
        self.assertEqual([part["type"] for part in input_items[0]["content"]], ["input_text", "input_image"])
        self.assertNotIn("words.png", input_items[0]["content"][0]["text"])
        expected_url = "data:image/png;base64," + base64.b64encode(IMAGE_MARKER).decode("ascii")
        self.assertEqual(input_items[0]["content"][1]["image_url"], expected_url)

        run_config = run.call_args.kwargs["run_config"]
        self.assertEqual(run_config.workflow_name, "Vocabulary Image Recognition")
        self.assertFalse(run_config.trace_include_sensitive_data)
        self.assertEqual(run_config.trace_metadata, {"trace_id": "vocab-image-123"})
        self.assertEqual(response.trace_id, "vocab-image-123")

    async def test_recognition_deduplicates_normalized_terms_and_assigns_stable_ids(self) -> None:
        output = model_output(
            item("  “Package,”  ", "not-trusted"),
            item("package", "package"),
            item("RECEIVE!", "receive"),
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=run_result(output)):
            response = await workflow().recognize(request())

        self.assertEqual([entry.item_id for entry in response.items], ["item-1", "item-2"])
        self.assertEqual([entry.normalized_term for entry in response.items], ["package", "receive"])
        self.assertEqual(response.items[0].observed_text, "“Package,”")

    async def test_recognition_truncates_to_thirty_candidates_with_warning(self) -> None:
        output = model_output(
            *(item(f"Word{index}", f"word{index}") for index in range(MAX_CANDIDATES + 1))
        )

        with patch("agents.Runner.run", new_callable=AsyncMock, return_value=run_result(output)):
            response = await workflow().recognize(request())

        self.assertEqual(len(response.items), MAX_CANDIDATES)
        self.assertEqual(response.items[-1].item_id, "item-30")
        self.assertEqual(response.warnings, ["CANDIDATE_LIMIT_REACHED"])

    async def test_recognition_returns_usage_metadata_and_empty_results(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            return_value=run_result(model_output(raw_text=""), input_tokens=17, output_tokens=3),
        ):
            response = await workflow().recognize(request())

        self.assertEqual(response.items, [])
        self.assertEqual(response.warnings, [])
        self.assertEqual(response.raw_text, "")
        self.assertEqual(response.generation.provider, "openai")
        self.assertEqual(response.generation.model, "test-model")
        self.assertEqual(response.generation.prompt_version, PROMPT_VERSION)
        self.assertEqual(response.generation.model_call_count, 1)
        self.assertEqual(response.generation.usage.input_tokens, 17)
        self.assertEqual(response.generation.usage.output_tokens, 3)

    async def test_response_does_not_expose_complete_raw_text(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            return_value=run_result(model_output(item(), raw_text="private complete raw text")),
        ):
            response = await workflow().recognize(request())

        self.assertEqual(response.raw_text, "")

    async def test_invalid_structured_output_retries_only_once(self) -> None:
        invalid_output = object()
        valid_result = run_result(model_output(item()))

        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=[run_result(invalid_output), valid_result],
        ) as run:
            response = await workflow().recognize(request())

        self.assertEqual(run.await_count, 2)
        self.assertEqual(response.generation.model_call_count, 2)

    async def test_repeated_schema_failure_returns_stable_sanitized_error(self) -> None:
        private_response = "private provider response"

        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=ModelBehaviorError(private_response),
        ) as run:
            with self.assertRaises(VocabularyImageRecognitionError) as raised:
                await workflow().recognize(request())

        self.assertEqual(run.await_count, 2)
        self.assertEqual(raised.exception.code, "MODEL_OUTPUT_INVALID")
        self.assertTrue(raised.exception.retryable)
        self.assertNotIn(private_response, str(raised.exception))
        self.assertIsNone(raised.exception.__cause__)
        self.assertIsNone(raised.exception.__context__)

    async def test_timeout_and_upstream_failures_map_to_stable_errors(self) -> None:
        cases = (
            (TimeoutError("private timeout"), "MODEL_TIMEOUT"),
            (ConnectionError("private upstream"), "MODEL_UPSTREAM_UNAVAILABLE"),
            (type("RateLimitError", (Exception,), {})("private rate limit"), "MODEL_UPSTREAM_UNAVAILABLE"),
        )

        for exception, expected_code in cases:
            with self.subTest(expected_code=expected_code), patch(
                "agents.Runner.run",
                new_callable=AsyncMock,
                side_effect=exception,
            ):
                with self.assertRaises(VocabularyImageRecognitionError) as raised:
                    await workflow(timeout_seconds=0.01).recognize(request())

            self.assertEqual(raised.exception.code, expected_code)
            self.assertTrue(raised.exception.retryable)
            self.assertNotIn("private", str(raised.exception))
            self.assertIsNone(raised.exception.__cause__)
            self.assertIsNone(raised.exception.__context__)

    async def test_cancellation_propagates_without_retry(self) -> None:
        with patch(
            "agents.Runner.run",
            new_callable=AsyncMock,
            side_effect=asyncio.CancelledError,
        ) as run:
            with self.assertRaises(asyncio.CancelledError):
                await workflow().recognize(request())

        self.assertEqual(run.await_count, 1)


if __name__ == "__main__":
    unittest.main()
