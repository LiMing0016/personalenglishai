import unittest

from pydantic import ValidationError

from python.ai_orchestrator.schemas.vocabulary_card import (
    VocabularyCardGenerationRequest,
    VocabularyCardGenerationResponse,
    VocabularyCore,
    VocabularyGenerationMetadata,
)


def request_payload() -> dict[str, object]:
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
            "phonetics": [],
            "senses": [],
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


def complete_core_payload() -> dict[str, object]:
    return {
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
    }


class VocabularyCardSchemasTest(unittest.TestCase):
    def test_request_accepts_camel_case_contract_and_serializes_aliases(self) -> None:
        request = VocabularyCardGenerationRequest.model_validate(request_payload())

        self.assertEqual(request.contract_version, 1)
        self.assertEqual(request.core_schema_version, 1)
        self.assertEqual(request.dictionary_core.term, "supposed")
        self.assertEqual(
            request.model_dump(by_alias=True)["theme"]["promptStrategyKey"],
            "exam-markdown-v1",
        )

    def test_cross_service_contract_rejects_unknown_fields(self) -> None:
        payload = request_payload()
        payload["unexpected"] = True

        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(payload)

        core = complete_core_payload()
        core["unexpected"] = True
        with self.assertRaises(ValidationError):
            VocabularyCore.model_validate(core)

    def test_contract_and_core_versions_must_be_exactly_one(self) -> None:
        unsupported_contract = request_payload()
        unsupported_contract["contractVersion"] = 2
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(unsupported_contract)

        unsupported_core = complete_core_payload()
        unsupported_core["schemaVersion"] = 2
        with self.assertRaises(ValidationError):
            VocabularyCore.model_validate(unsupported_core)

    def test_request_bounds_term_source_context_and_timeout_budget(self) -> None:
        term_too_long = request_payload()
        term_too_long["term"] = "a" * 201
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(term_too_long)

        source_context_too_long = request_payload()
        source_context_too_long["sourceContext"] = "a" * 10_001
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(source_context_too_long)

        exhausted_timeout = request_payload()
        exhausted_timeout["timeoutBudgetMs"] = 0
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(exhausted_timeout)

        excessive_timeout = request_payload()
        excessive_timeout["timeoutBudgetMs"] = 60_001
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(excessive_timeout)

    def test_theme_accepts_only_registered_strategy_keys_and_format_version(self) -> None:
        unsupported_strategy = request_payload()
        unsupported_strategy["theme"] = {
            **unsupported_strategy["theme"],
            "promptStrategyKey": "unknown-markdown-v1",
        }
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(unsupported_strategy)

        unsupported_format = request_payload()
        unsupported_format["theme"] = {
            **unsupported_format["theme"],
            "contentFormatVersion": 2,
        }
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(unsupported_format)

    def test_response_outcome_metadata_and_serialization_aliases_are_strict(self) -> None:
        response = VocabularyCardGenerationResponse.model_validate(
            {
                "contractVersion": 1,
                "coreSchemaVersion": 1,
                "core": complete_core_payload(),
                "contentMarkdown": "## Exam focus\n\nUse **supposed to** accurately.",
                "contentFormatVersion": 1,
                "outcome": "complete",
                "warning": None,
                "generation": {
                    "provider": "openai",
                    "model": "configured-model",
                    "promptVersion": "vocabulary-card-markdown-v1",
                    "modelCallCount": 1,
                    "traceId": "vocab-job_123-attempt_1",
                },
            }
        )

        serialized = response.model_dump(by_alias=True)
        self.assertEqual(serialized["contentMarkdown"], response.content_markdown)
        self.assertEqual(serialized["generation"]["modelCallCount"], 1)

        with self.assertRaises(ValidationError):
            VocabularyCardGenerationResponse.model_validate(
                {**response.model_dump(by_alias=True), "outcome": "warning"}
            )

        with self.assertRaises(ValidationError):
            VocabularyGenerationMetadata.model_validate(
                {
                    "provider": "openai",
                    "model": "configured-model",
                    "promptVersion": "vocabulary-card-markdown-v1",
                    "modelCallCount": 3,
                    "traceId": "trace",
                }
            )


if __name__ == "__main__":
    unittest.main()
