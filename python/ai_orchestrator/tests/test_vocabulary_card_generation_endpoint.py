import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from python.ai_orchestrator.app import app
from python.ai_orchestrator.workflows.vocabulary_card_generation import (
    VocabularyCardGenerationError,
)


def request_payload() -> dict:
    return {
        "contractVersion": 1,
        "coreSchemaVersion": 1,
        "requestId": "job_123:attempt_1",
        "traceId": "vocab-job_123-attempt_1",
        "timeoutBudgetMs": 45_000,
        "term": "supposed",
        "dictionaryCore": {
            "schemaVersion": 1,
            "term": "supposed",
            "phonetics": [{"region": "uk", "text": "səˈpəʊzd", "audioUrl": None}],
            "senses": [
                {
                    "partOfSpeech": "adjective",
                    "meanings": [
                        {
                            "definitionEn": "generally believed or expected",
                            "definitionZh": "一般认为的；预期的",
                        }
                    ],
                }
            ],
        },
        "sourceContext": "It is supposed to be easy.",
        "theme": {
            "uid": "theme_system_exam",
            "version": 1,
            "name": "Exam",
            "purpose": "用于考试词义、搭配和易错点学习",
            "promptStrategyKey": "exam-markdown-v1",
            "contentFormatVersion": 1,
        },
    }


def complete_response(*, extra: dict | None = None) -> dict:
    response = {
        "contractVersion": 1,
        "coreSchemaVersion": 1,
        "core": request_payload()["dictionaryCore"],
        "contentMarkdown": "## Exam focus\n\nUseful collocation.",
        "contentFormatVersion": 1,
        "outcome": "complete",
        "warning": None,
        "generation": {
            "provider": "openai",
            "model": "test-model",
            "promptVersion": "vocabulary-card-markdown-v1",
            "modelCallCount": 1,
            "traceId": "vocab-job_123-attempt_1",
        },
    }
    if extra:
        response.update(extra)
    return response


def partial_response() -> dict:
    response = complete_response()
    response.update(
        {
            "contentMarkdown": "",
            "outcome": "partial",
            "warning": "markdown_unavailable",
        }
    )
    return response


class CapturingVocabularyGenerationService:
    def __init__(self, result: dict | None = None, error: Exception | None = None) -> None:
        self.internal_token = "internal-test-token"
        self.result = result or complete_response()
        self.error = error
        self.received = None

    def is_configured(self) -> bool:
        return True

    async def generate(self, request):
        self.received = request
        if self.error:
            raise self.error
        return self.result


class VocabularyCardGenerationEndpointTest(unittest.TestCase):
    endpoint = "/internal/v1/vocabulary/card-generations"

    def post(self, client: TestClient, *, payload: dict | None = None, token: str | None = "internal-test-token"):
        headers = {} if token is None else {"Authorization": f"Bearer {token}"}
        body = request_payload() if payload is None else payload
        return client.post(self.endpoint, json=body, headers=headers)

    def test_missing_internal_token_is_unauthorized(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService()

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client, token=None)

        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json()["detail"]["code"], "INTERNAL_AUTH_FAILED")
        self.assertIsNone(service.received)

    def test_wrong_internal_token_is_forbidden(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService()

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client, token="wrong-token")

        self.assertEqual(response.status_code, 403)
        self.assertEqual(response.json()["detail"]["code"], "INTERNAL_AUTH_FAILED")
        self.assertIsNone(service.received)

    def test_authentication_precedes_body_validation(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService()

        for token, expected_status in ((None, 401), ("wrong-token", 403)):
            with self.subTest(token=token), patch(
                "python.ai_orchestrator.app.vocabulary_card_generation_service", service
            ):
                response = self.post(client, payload={}, token=token)

            self.assertEqual(response.status_code, expected_status)
            self.assertEqual(response.json()["detail"]["code"], "INTERNAL_AUTH_FAILED")
            self.assertIsNone(service.received)

    def test_valid_token_forwards_a_typed_request_and_returns_complete_response(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService()

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["outcome"], "complete")
        self.assertEqual(response.json()["contentMarkdown"], "## Exam focus\n\nUseful collocation.")
        self.assertEqual(service.received.term, "supposed")
        self.assertEqual(service.received.theme.prompt_strategy_key, "exam-markdown-v1")

    def test_valid_token_returns_partial_response(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService(result=partial_response())

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["outcome"], "partial")
        self.assertEqual(response.json()["contentMarkdown"], "")
        self.assertEqual(response.json()["warning"], "markdown_unavailable")

    def test_invalid_schema_returns_pydantic_422_without_calling_service(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService()
        payload = request_payload()
        payload.pop("term")

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client, payload=payload)

        self.assertEqual(response.status_code, 422)
        self.assertIsNone(service.received)

    def test_response_model_filters_uncontracted_service_fields(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService(
            result=complete_response(extra={"modelOutput": "never expose", "debug": {"prompt": "secret"}})
        )

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client)

        self.assertEqual(response.status_code, 200)
        self.assertNotIn("modelOutput", response.json())
        self.assertNotIn("debug", response.json())

    def test_non_retryable_contract_error_maps_to_400(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService(
            error=VocabularyCardGenerationError(
                "UNSUPPORTED_PROMPT_STRATEGY", False, "Prompt private model response"
            )
        )

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client)

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["detail"]["code"], "UNSUPPORTED_PROMPT_STRATEGY")
        self.assertNotIn("private model response", response.text)

    def test_service_auth_errors_map_to_401_and_403(self) -> None:
        client = TestClient(app)

        for code, expected_status in (("INTERNAL_AUTH_MISSING", 401), ("INTERNAL_AUTH_FAILED", 403)):
            service = CapturingVocabularyGenerationService(
                error=VocabularyCardGenerationError(code, False, "Prompt private model response")
            )
            with self.subTest(code=code), patch(
                "python.ai_orchestrator.app.vocabulary_card_generation_service", service
            ):
                response = self.post(client)

            self.assertEqual(response.status_code, expected_status)
            self.assertEqual(response.json()["detail"]["code"], "INTERNAL_AUTH_FAILED")
            self.assertNotIn("private model response", response.text)

    def test_not_configured_and_upstream_errors_map_to_503(self) -> None:
        client = TestClient(app)

        for code in ("VOCABULARY_GENERATION_NOT_CONFIGURED", "MODEL_UPSTREAM_UNAVAILABLE"):
            service = CapturingVocabularyGenerationService(
                error=VocabularyCardGenerationError(code, True, "Prompt private model response")
            )
            with self.subTest(code=code), patch(
                "python.ai_orchestrator.app.vocabulary_card_generation_service", service
            ):
                response = self.post(client)

            self.assertEqual(response.status_code, 503)
            self.assertEqual(response.json()["detail"]["code"], code)
            self.assertNotIn("private model response", response.text)

    def test_timeout_error_maps_to_504(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService(
            error=VocabularyCardGenerationError("MODEL_TIMEOUT", True, "Prompt private model response")
        )

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client)

        self.assertEqual(response.status_code, 504)
        self.assertEqual(response.json()["detail"]["code"], "MODEL_TIMEOUT")
        self.assertNotIn("private model response", response.text)

    def test_unexpected_error_returns_sanitized_500(self) -> None:
        client = TestClient(app)
        service = CapturingVocabularyGenerationService(
            error=RuntimeError("Prompt: secret prompt; model output: secret completion")
        )

        with patch("python.ai_orchestrator.app.vocabulary_card_generation_service", service):
            response = self.post(client)

        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json()["detail"]["code"], "GENERATION_INTERNAL_ERROR")
        self.assertNotIn("secret prompt", response.text)
        self.assertNotIn("secret completion", response.text)


if __name__ == "__main__":
    unittest.main()
