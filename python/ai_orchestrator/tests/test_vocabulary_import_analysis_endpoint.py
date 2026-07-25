from __future__ import annotations

import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from python.ai_orchestrator.app import app
from python.ai_orchestrator.workflows.vocabulary_import_analysis import (
    VocabularyImportAnalysisError,
)


FINGERPRINT = "a" * 64


def response_payload() -> dict:
    return {
        "contractVersion": 1,
        "traceId": "vocab-import-123",
        "inputFingerprint": FINGERPRINT,
        "rawText": "package",
        "warnings": [],
        "items": [
            {
                "itemId": "item-1",
                "observedText": "package",
                "normalizedTerm": "package",
                "status": "accepted",
                "suggestions": [],
                "contextText": None,
                "confidence": 0.95,
                "evidence": "text",
            }
        ],
        "generation": {
            "provider": "openai",
            "model": "test-model",
            "promptVersion": "vocabulary-import-analysis-v1",
            "modelCallCount": 1,
            "traceId": "vocab-import-123",
            "usage": None,
        },
    }


class CapturingService:
    def __init__(self, error: Exception | None = None) -> None:
        self.internal_token = "internal-test-token"
        self.error = error
        self.received = None

    def is_configured(self) -> bool:
        return True

    async def analyze(self, request):
        self.received = request
        if self.error is not None:
            raise self.error
        return response_payload()


class VocabularyImportAnalysisEndpointTest(unittest.TestCase):
    endpoint = "/internal/v1/vocabulary/import-analyses"

    def post(
        self,
        *,
        token: str | None = "internal-test-token",
        text: str = "package",
        file: tuple[str, bytes, str] | None = None,
        fingerprint: str = FINGERPRINT,
    ):
        headers = {} if token is None else {"Authorization": f"Bearer {token}"}
        data = {
            "contractVersion": "1",
            "traceId": "vocab-import-123",
            "inputFingerprint": fingerprint,
            "language": "en",
            "text": text,
        }
        files = None if file is None else {"file": file}
        return TestClient(app).post(self.endpoint, data=data, files=files, headers=headers)

    def test_text_only_and_combined_requests_are_forwarded(self) -> None:
        for file in (None, ("words.png", b"png-bytes", "image/png")):
            service = CapturingService()
            with self.subTest(has_file=file is not None), patch(
                "python.ai_orchestrator.app.vocabulary_import_analysis_service",
                service,
            ):
                response = self.post(file=file)

            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json()["inputFingerprint"], FINGERPRINT)
            self.assertEqual(service.received.text, "package")
            self.assertEqual(service.received.content, None if file is None else b"png-bytes")

    def test_missing_input_and_invalid_fingerprint_are_rejected_before_service(self) -> None:
        service = CapturingService()
        with patch(
            "python.ai_orchestrator.app.vocabulary_import_analysis_service",
            service,
        ):
            missing = self.post(text="")
            invalid_fingerprint = self.post(fingerprint="ABC")

        self.assertEqual(missing.status_code, 422)
        self.assertEqual(invalid_fingerprint.status_code, 422)
        self.assertIsNone(service.received)

    def test_auth_and_stable_timeout_errors_are_sanitized(self) -> None:
        service = CapturingService(VocabularyImportAnalysisError("MODEL_TIMEOUT", True))
        with patch(
            "python.ai_orchestrator.app.vocabulary_import_analysis_service",
            service,
        ):
            unauthorized = self.post(token=None)
            timeout = self.post()

        self.assertEqual(unauthorized.status_code, 401)
        self.assertEqual(timeout.status_code, 504)
        self.assertEqual(timeout.json()["detail"]["code"], "MODEL_TIMEOUT")

    def test_health_reports_import_analysis_configuration(self) -> None:
        service = CapturingService()
        with patch(
            "python.ai_orchestrator.app.vocabulary_import_analysis_service",
            service,
        ):
            response = TestClient(app).get("/health")

        self.assertTrue(response.json()["vocabularyImportAnalysisConfigured"])


if __name__ == "__main__":
    unittest.main()
