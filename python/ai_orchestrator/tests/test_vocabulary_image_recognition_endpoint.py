from __future__ import annotations

import logging
import os
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from python.ai_orchestrator.app import app
from python.ai_orchestrator.schemas.vocabulary_image_recognition import MAX_IMAGE_BYTES
from python.ai_orchestrator.services.vocabulary_image_recognition import (
    VocabularyImageRecognitionService,
)
from python.ai_orchestrator.workflows.vocabulary_image_recognition import (
    VocabularyImageRecognitionError,
)


IMAGE_MARKER = b"private-image-marker"
PRIVATE_PROVIDER_ERROR = "private-image-marker provider raw response"


def response_payload(*, items: list[dict] | None = None) -> dict:
    return {
        "contractVersion": 1,
        "traceId": "vocab-image-123",
        "rawText": "recognized text",
        "warnings": [],
        "items": items
        if items is not None
        else [
            {
                "itemId": "item-1",
                "observedText": "Package",
                "normalizedTerm": "package",
                "status": "accepted",
                "suggestions": [],
                "contextText": None,
                "confidence": 0.95,
            }
        ],
        "generation": {
            "provider": "openai",
            "model": "test-model",
            "promptVersion": "vocabulary-image-recognition-v1",
            "modelCallCount": 1,
            "traceId": "vocab-image-123",
            "usage": {"inputTokens": 10, "outputTokens": 2},
        },
    }


class CapturingRecognitionService:
    def __init__(self, *, result: dict | None = None, error: Exception | None = None) -> None:
        self.internal_token = "internal-test-token"
        self.result = response_payload() if result is None else result
        self.error = error
        self.received = None

    def is_configured(self) -> bool:
        return True

    async def recognize(self, request):
        self.received = request
        if self.error is not None:
            raise self.error
        return self.result


class VocabularyImageRecognitionEndpointTest(unittest.TestCase):
    endpoint = "/internal/v1/vocabulary/image-recognitions"

    def post(
        self,
        client: TestClient,
        *,
        token: str | None = "internal-test-token",
        data: dict[str, str] | None = None,
        content: bytes = IMAGE_MARKER,
        file_name: str = "words.png",
        content_type: str = "image/png",
    ):
        headers = {} if token is None else {"Authorization": f"Bearer {token}"}
        form = {
            "contractVersion": "1",
            "traceId": "vocab-image-123",
            "language": "en",
        }
        return client.post(
            self.endpoint,
            data=form if data is None else data,
            files={"file": (file_name, content, content_type)},
            headers=headers,
        )

    def test_missing_and_wrong_tokens_are_rejected_before_service_call(self) -> None:
        client = TestClient(app)
        service = CapturingRecognitionService()

        for token, expected_status in ((None, 401), ("wrong-token", 403)):
            with self.subTest(token=token), patch(
                "python.ai_orchestrator.app.vocabulary_image_recognition_service", service
            ):
                response = self.post(client, token=token)

            self.assertEqual(response.status_code, expected_status)
            self.assertEqual(response.json()["detail"]["code"], "INTERNAL_AUTH_FAILED")
            self.assertIsNone(service.received)

    def test_missing_model_configuration_is_unavailable(self) -> None:
        client = TestClient(app)
        with patch.dict(
            os.environ,
            {
                "OPENAI_API_KEY": "",
                "VOCABULARY_IMAGE_RECOGNITION_MODEL": "",
                "VOCABULARY_GENERATION_INTERNAL_TOKEN": "internal-test-token",
            },
            clear=False,
        ):
            service = VocabularyImageRecognitionService.from_env()
            with patch(
                "python.ai_orchestrator.app.vocabulary_image_recognition_service", service
            ):
                response = self.post(client)

        self.assertEqual(response.status_code, 503)
        self.assertEqual(
            response.json()["detail"]["code"],
            "IMAGE_RECOGNITION_NOT_CONFIGURED",
        )

    def test_health_reports_image_recognition_configuration(self) -> None:
        service = CapturingRecognitionService()
        with patch("python.ai_orchestrator.app.vocabulary_image_recognition_service", service):
            response = TestClient(app).get("/health")

        self.assertTrue(response.json()["vocabularyImageRecognitionConfigured"])

    def test_multipart_field_aliases_are_strict(self) -> None:
        service = CapturingRecognitionService()
        with patch("python.ai_orchestrator.app.vocabulary_image_recognition_service", service):
            response = self.post(
                TestClient(app),
                data={
                    "contract_version": "1",
                    "trace_id": "vocab-image-123",
                    "language": "en",
                },
            )

        self.assertEqual(response.status_code, 422)
        self.assertIsNone(service.received)

    def test_empty_file_wrong_mime_and_mismatched_extension_are_rejected(self) -> None:
        cases = (
            ({"content": b""}, "INVALID_IMAGE_REQUEST"),
            ({"content_type": "application/octet-stream"}, "UNSUPPORTED_IMAGE_TYPE"),
            ({"file_name": "words.jpg", "content_type": "image/png"}, "UNSUPPORTED_IMAGE_TYPE"),
        )

        for overrides, expected_code in cases:
            service = CapturingRecognitionService()
            with self.subTest(expected_code=expected_code), patch(
                "python.ai_orchestrator.app.vocabulary_image_recognition_service", service
            ):
                response = self.post(TestClient(app), **overrides)

            self.assertEqual(response.status_code, 400)
            self.assertEqual(response.json()["detail"]["code"], expected_code)
            self.assertIsNone(service.received)

    def test_exact_ten_mib_file_is_forwarded(self) -> None:
        service = CapturingRecognitionService()
        with patch("python.ai_orchestrator.app.vocabulary_image_recognition_service", service):
            response = self.post(TestClient(app), content=b"x" * MAX_IMAGE_BYTES)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(service.received.content), MAX_IMAGE_BYTES)

    def test_file_over_ten_mib_is_rejected_without_forwarding_content(self) -> None:
        service = CapturingRecognitionService()
        with patch("python.ai_orchestrator.app.vocabulary_image_recognition_service", service):
            response = self.post(TestClient(app), content=b"x" * (MAX_IMAGE_BYTES + 1))

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["detail"]["code"], "IMAGE_TOO_LARGE")
        self.assertIsNone(service.received)

    def test_success_and_empty_item_responses_preserve_contract(self) -> None:
        for items in (None, []):
            service = CapturingRecognitionService(result=response_payload(items=items))
            with self.subTest(empty=items == []), patch(
                "python.ai_orchestrator.app.vocabulary_image_recognition_service", service
            ):
                response = self.post(TestClient(app))

            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json()["contractVersion"], 1)
            self.assertEqual(response.json()["items"], response_payload(items=items)["items"])
            self.assertEqual(service.received.file_name, "words.png")
            self.assertEqual(service.received.content_type, "image/png")

    def test_stable_workflow_errors_map_to_sanitized_http_statuses(self) -> None:
        cases = (
            ("MODEL_OUTPUT_INVALID", 502),
            ("MODEL_UPSTREAM_UNAVAILABLE", 503),
            ("IMAGE_RECOGNITION_NOT_CONFIGURED", 503),
            ("MODEL_TIMEOUT", 504),
        )

        for code, expected_status in cases:
            service = CapturingRecognitionService(
                error=VocabularyImageRecognitionError(code, True)
            )
            with self.subTest(code=code), patch(
                "python.ai_orchestrator.app.vocabulary_image_recognition_service", service
            ):
                response = self.post(TestClient(app))

            self.assertEqual(response.status_code, expected_status)
            self.assertEqual(response.json()["detail"]["code"], code)
            self.assertNotIn(PRIVATE_PROVIDER_ERROR, response.text)

    def test_unexpected_errors_do_not_leak_image_or_provider_details(self) -> None:
        service = CapturingRecognitionService(error=RuntimeError(PRIVATE_PROVIDER_ERROR))

        with self.assertLogs("uvicorn.error", level=logging.INFO) as captured, patch(
            "python.ai_orchestrator.app.vocabulary_image_recognition_service", service
        ):
            response = self.post(TestClient(app))

        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json()["detail"]["code"], "IMAGE_RECOGNITION_INTERNAL_ERROR")
        combined = response.text + "\n".join(captured.output)
        self.assertNotIn(IMAGE_MARKER.decode("ascii"), combined)
        self.assertNotIn(PRIVATE_PROVIDER_ERROR, combined)


class VocabularyImageRecognitionServiceConfigurationTest(unittest.TestCase):
    def test_from_env_uses_exact_configuration_and_default_timeout(self) -> None:
        with patch.dict(
            os.environ,
            {
                "VOCABULARY_IMAGE_RECOGNITION_MODEL": "gpt-test",
                "VOCABULARY_GENERATION_INTERNAL_TOKEN": "internal-token",
            },
            clear=True,
        ):
            service = VocabularyImageRecognitionService.from_env()

        self.assertEqual(service.model, "gpt-test")
        self.assertEqual(service.internal_token, "internal-token")
        self.assertEqual(service.timeout_ms, 45_000)

    def test_timeout_configuration_accepts_bounds_and_rejects_outside_range(self) -> None:
        for value in ("1", "45000"):
            with self.subTest(value=value), patch.dict(
                os.environ,
                {"VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS": value},
                clear=True,
            ):
                self.assertEqual(VocabularyImageRecognitionService.from_env().timeout_ms, int(value))

        for value in ("0", "45001", "not-a-number"):
            with self.subTest(value=value), patch.dict(
                os.environ,
                {"VOCABULARY_IMAGE_RECOGNITION_TIMEOUT_MS": value},
                clear=True,
            ):
                service = VocabularyImageRecognitionService.from_env()
                self.assertFalse(service.is_configured())
                with self.assertRaises(VocabularyImageRecognitionError) as raised:
                    import asyncio

                    asyncio.run(service.recognize(None))
                self.assertEqual(raised.exception.code, "IMAGE_RECOGNITION_NOT_CONFIGURED")


if __name__ == "__main__":
    unittest.main()
