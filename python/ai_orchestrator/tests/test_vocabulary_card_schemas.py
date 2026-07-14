import copy
import unittest

from markdown_it import MarkdownIt
from pydantic import ValidationError

from python.ai_orchestrator.schemas.vocabulary_card import (
    VocabularyCardGenerationRequest,
    VocabularyCardGenerationResponse,
    VocabularyCore,
    VocabularyCoreFallbackOutput,
    VocabularyGenerationMetadata,
    VocabularyMarkdownOutput,
    VocabularyMeaning,
    VocabularyPhonetic,
    VocabularySense,
    VocabularyThemeSnapshot,
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


def response_payload(
    *,
    outcome: str = "complete",
    content_markdown: str = "## Exam focus\n\nUse **supposed to** accurately.",
    warning: str | None = None,
) -> dict[str, object]:
    return {
        "contractVersion": 1,
        "coreSchemaVersion": 1,
        "core": complete_core_payload(),
        "contentMarkdown": content_markdown,
        "contentFormatVersion": 1,
        "outcome": outcome,
        "warning": warning,
        "generation": {
            "provider": "openai",
            "model": "configured-model",
            "promptVersion": "vocabulary-card-markdown-v1",
            "modelCallCount": 1,
            "traceId": "vocab-job_123-attempt_1",
        },
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

    def test_nested_core_models_require_every_wire_field_and_forbid_extras(self) -> None:
        cases = (
            (
                VocabularyPhonetic,
                complete_core_payload()["phonetics"][0],
                ("region", "text", "audioUrl"),
            ),
            (
                VocabularyMeaning,
                complete_core_payload()["senses"][0]["meanings"][0],
                ("definitionEn", "definitionZh"),
            ),
            (
                VocabularySense,
                complete_core_payload()["senses"][0],
                ("partOfSpeech", "meanings"),
            ),
            (
                VocabularyCore,
                complete_core_payload(),
                ("schemaVersion", "term", "phonetics", "senses"),
            ),
        )

        for model, payload, required_fields in cases:
            for field in required_fields:
                with self.subTest(model=model.__name__, missing=field):
                    missing_field = copy.deepcopy(payload)
                    del missing_field[field]
                    with self.assertRaises(ValidationError):
                        model.model_validate(missing_field)

            with self.subTest(model=model.__name__, extra=True):
                unexpected_field = copy.deepcopy(payload)
                unexpected_field["unexpected"] = True
                with self.assertRaises(ValidationError):
                    model.model_validate(unexpected_field)

    def test_nested_request_and_response_models_require_every_field_and_forbid_extras(self) -> None:
        cases = (
            (
                VocabularyThemeSnapshot,
                request_payload()["theme"],
                ("uid", "version", "name", "purpose", "promptStrategyKey", "contentFormatVersion"),
            ),
            (
                VocabularyGenerationMetadata,
                response_payload()["generation"],
                ("provider", "model", "promptVersion", "modelCallCount", "traceId"),
            ),
        )

        for model, payload, required_fields in cases:
            for field in required_fields:
                with self.subTest(model=model.__name__, missing=field):
                    missing_field = copy.deepcopy(payload)
                    del missing_field[field]
                    with self.assertRaises(ValidationError):
                        model.model_validate(missing_field)

            with self.subTest(model=model.__name__, extra=True):
                unexpected_field = copy.deepcopy(payload)
                unexpected_field["unexpected"] = True
                with self.assertRaises(ValidationError):
                    model.model_validate(unexpected_field)

    def test_core_output_json_schema_requires_java_wire_keys(self) -> None:
        schema = VocabularyCoreFallbackOutput.model_json_schema()
        definitions = schema["$defs"]

        self.assertTrue(
            {"schemaVersion", "term", "phonetics", "senses"}.issubset(schema["required"])
        )
        self.assertTrue(
            {"region", "text", "audioUrl"}.issubset(definitions["VocabularyPhonetic"]["required"])
        )
        self.assertTrue(
            {"partOfSpeech", "meanings"}.issubset(definitions["VocabularySense"]["required"])
        )
        self.assertTrue(
            {"definitionEn", "definitionZh"}.issubset(definitions["VocabularyMeaning"]["required"])
        )

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

    def test_term_identity_requires_nonblank_values_without_trimming(self) -> None:
        blank_request_term = request_payload()
        blank_request_term["term"] = "   "
        blank_request_term["dictionaryCore"] = {
            **blank_request_term["dictionaryCore"],
            "term": "   ",
        }
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(blank_request_term)

        blank_core_term = complete_core_payload()
        blank_core_term["term"] = "   "
        with self.assertRaises(ValidationError):
            VocabularyCore.model_validate(blank_core_term)

        exact_term = request_payload()
        exact_term["term"] = " supposed "
        exact_term["dictionaryCore"] = {
            **exact_term["dictionaryCore"],
            "term": " supposed ",
        }
        request = VocabularyCardGenerationRequest.model_validate(exact_term)
        self.assertEqual(request.term, " supposed ")
        self.assertEqual(request.dictionary_core.term, " supposed ")

    def test_metadata_ids_require_opaque_safe_syntax(self) -> None:
        for field, invalid_value in (
            ("requestId", "sourceContext=private sentence"),
            ("traceId", "private sentence"),
            ("requestId", "a" * 129),
        ):
            with self.subTest(field=field, invalid_value=invalid_value):
                payload = request_payload()
                payload[field] = invalid_value
                with self.assertRaises(ValidationError):
                    VocabularyCardGenerationRequest.model_validate(payload)

        valid_ids = request_payload()
        valid_ids["requestId"] = "job_123:attempt-1.2"
        valid_ids["traceId"] = "trace_123:attempt-1.2"
        self.assertEqual(
            VocabularyCardGenerationRequest.model_validate(valid_ids).request_id,
            "job_123:attempt-1.2",
        )

        invalid_metadata = response_payload()["generation"]
        invalid_metadata["traceId"] = "private sentence"
        with self.assertRaises(ValidationError):
            VocabularyGenerationMetadata.model_validate(invalid_metadata)

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
        response = VocabularyCardGenerationResponse.model_validate(response_payload())

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

    def test_response_requires_exactly_the_complete_or_partial_markdown_state(self) -> None:
        valid_markdown = "## Valid Markdown"
        valid_states = (
            ("complete", valid_markdown, None),
            ("partial", "", "markdown_unavailable"),
        )
        for outcome, content_markdown, warning in valid_states:
            with self.subTest(outcome=outcome, valid=True):
                response = VocabularyCardGenerationResponse.model_validate(
                    response_payload(
                        outcome=outcome,
                        content_markdown=content_markdown,
                        warning=warning,
                    )
                )
                self.assertEqual(response.outcome, outcome)

        for outcome in ("complete", "partial"):
            for content_markdown in ("", valid_markdown):
                for warning in (None, "markdown_unavailable"):
                    state = (outcome, content_markdown, warning)
                    if state in valid_states:
                        continue
                    with self.subTest(outcome=outcome, markdown=content_markdown, warning=warning):
                        with self.assertRaises(ValidationError):
                            VocabularyCardGenerationResponse.model_validate(
                                response_payload(
                                    outcome=outcome,
                                    content_markdown=content_markdown,
                                    warning=warning,
                                )
                            )

        with self.assertRaises(ValidationError):
            VocabularyCardGenerationResponse.model_validate(
                response_payload(content_markdown="   ")
            )

    def test_markdown_models_reject_raw_html_without_rejecting_markdown_boundaries(self) -> None:
        ordinary_markdown = (
            "## Comparison\n\n"
            "Keep `a < b` distinct from `c > d`.\n\n"
            "<https://example.com/guide?q=1>\n\n"
            "<mailto:study@example.com>"
        )
        self.assertEqual(
            VocabularyMarkdownOutput.model_validate(
                {"contentMarkdown": ordinary_markdown}
            ).content_markdown,
            ordinary_markdown,
        )
        self.assertEqual(
            VocabularyCardGenerationResponse.model_validate(
                response_payload(content_markdown=ordinary_markdown)
            ).content_markdown,
            ordinary_markdown,
        )

        for raw_html in (
            "<script>alert('x')</script>",
            "<SCRIPT>alert('x')</SCRIPT>",
            "<img src=\"https://example.com/x.png\">",
            "<DIV class=\"note\">content</DIV>",
            "before </section> after",
            "line break<br />",
        ):
            with self.subTest(raw_html=raw_html, model="agent"):
                with self.assertRaises(ValidationError):
                    VocabularyMarkdownOutput.model_validate({"contentMarkdown": raw_html})
            with self.subTest(raw_html=raw_html, model="http"):
                with self.assertRaises(ValidationError):
                    VocabularyCardGenerationResponse.model_validate(
                        response_payload(content_markdown=raw_html)
                    )

    def test_markdown_models_reject_comments_declarations_processing_instructions_and_tags(self) -> None:
        prohibited_html = (
            "<!-- hidden note -->",
            "<!DOCTYPE html>",
            "<!doctype vocabulary>",
            "<!ENTITY author \"student\">",
            "<?xml version=\"1.0\"?>",
            "<SCRIPT data-topic=\"grammar\">alert('x')</SCRIPT>",
            "</DiV>",
            "<img src=\"https://example.com/card.png\" />",
        )

        for raw_html in prohibited_html:
            with self.subTest(raw_html=raw_html, model="agent"):
                with self.assertRaises(ValidationError):
                    VocabularyMarkdownOutput.model_validate({"contentMarkdown": raw_html})
            with self.subTest(raw_html=raw_html, model="http"):
                with self.assertRaises(ValidationError):
                    VocabularyCardGenerationResponse.model_validate(
                        response_payload(content_markdown=raw_html)
                    )

    def test_markdown_models_allow_tag_literals_in_closed_fenced_and_inline_code(self) -> None:
        markdown_with_code_literals = (
            "```html\n"
            "<SCRIPT data-topic=\"grammar\">alert('x')</SCRIPT>\n"
            "```\n\n"
            "~~~xml\n"
            "<!DOCTYPE vocabulary>\n"
            "<?xml version=\"1.0\"?>\n"
            "~~~\n\n"
            "Use `<img src=\"card.png\" />` literally."
        )

        self.assertEqual(
            VocabularyMarkdownOutput.model_validate(
                {"contentMarkdown": markdown_with_code_literals}
            ).content_markdown,
            markdown_with_code_literals,
        )
        self.assertEqual(
            VocabularyCardGenerationResponse.model_validate(
                response_payload(content_markdown=markdown_with_code_literals)
            ).content_markdown,
            markdown_with_code_literals,
        )

    def test_markdown_models_reject_html_inside_unclosed_inline_code(self) -> None:
        unclosed_inline_code = "Use `<img src=\"card.png\" /> literally."

        for markdown in (unclosed_inline_code,):
            with self.subTest(markdown=markdown, model="agent"):
                with self.assertRaises(ValidationError):
                    VocabularyMarkdownOutput.model_validate({"contentMarkdown": markdown})
            with self.subTest(markdown=markdown, model="http"):
                with self.assertRaises(ValidationError):
                    VocabularyCardGenerationResponse.model_validate(
                        response_payload(content_markdown=markdown)
                    )

    def test_markdown_models_allow_html_literals_in_unclosed_commonmark_fences(self) -> None:
        unclosed_fences = (
            "```html\n<script>alert('x')</script>",
            "~~~xml\n<?xml version=\"1.0\"?>",
        )

        for markdown in unclosed_fences:
            with self.subTest(markdown=markdown, model="agent"):
                self.assertEqual(
                    VocabularyMarkdownOutput.model_validate(
                        {"contentMarkdown": markdown}
                    ).content_markdown,
                    markdown,
                )
            with self.subTest(markdown=markdown, model="http"):
                self.assertEqual(
                    VocabularyCardGenerationResponse.model_validate(
                        response_payload(content_markdown=markdown)
                    ).content_markdown,
                    markdown,
                )

    def test_markdown_models_reject_html_after_pseudo_fences(self) -> None:
        pseudo_fences = (
            "\t```html\n<script>alert('x')</script>\n\t```",
            "    ```html\n<script>alert('x')</script>\n    ```",
        )

        for markdown in pseudo_fences:
            with self.subTest(markdown=markdown, model="agent"):
                with self.assertRaises(ValidationError):
                    VocabularyMarkdownOutput.model_validate({"contentMarkdown": markdown})
            with self.subTest(markdown=markdown, model="http"):
                with self.assertRaises(ValidationError):
                    VocabularyCardGenerationResponse.model_validate(
                        response_payload(content_markdown=markdown)
                    )

    def test_markdown_models_honor_backslash_parity_before_code_delimiters(self) -> None:
        escaped_opener = "\\`<script>alert('x')</script>`"
        literal_backslash_then_code = "\\\\`<script>alert('x')</script>`"

        for markdown in (escaped_opener,):
            with self.subTest(markdown=markdown, model="agent"):
                with self.assertRaises(ValidationError):
                    VocabularyMarkdownOutput.model_validate({"contentMarkdown": markdown})
            with self.subTest(markdown=markdown, model="http"):
                with self.assertRaises(ValidationError):
                    VocabularyCardGenerationResponse.model_validate(
                        response_payload(content_markdown=markdown)
                    )

        self.assertEqual(
            VocabularyMarkdownOutput.model_validate(
                {"contentMarkdown": literal_backslash_then_code}
            ).content_markdown,
            literal_backslash_then_code,
        )
        self.assertEqual(
            VocabularyCardGenerationResponse.model_validate(
                response_payload(content_markdown=literal_backslash_then_code)
            ).content_markdown,
            literal_backslash_then_code,
        )

    def test_commonmark_parser_exposes_html_tokens_for_p1_bypass_regressions(self) -> None:
        parser = MarkdownIt("commonmark")
        markdown_inputs = (
            ("\t```html\n<script>alert('x')</script>\n\t```", {"html_block"}),
            ("    ```html\n<script>alert('x')</script>\n    ```", {"html_block"}),
            ("\\`<script>alert('x')</script>`", {"html_inline"}),
            ("\\\\`<script>alert('x')</script>`", {"code_inline"}),
        )

        for markdown, expected_token_types in markdown_inputs:
            with self.subTest(markdown=markdown):
                tokens = parser.parse(markdown)
                token_types = {token.type for token in tokens}
                token_types.update(
                    child.type
                    for token in tokens
                    for child in token.children or ()
                )
                self.assertTrue(expected_token_types <= token_types)

    def test_markdown_output_json_schema_requires_content_markdown(self) -> None:
        schema = VocabularyMarkdownOutput.model_json_schema()
        self.assertIn("contentMarkdown", schema["required"])


if __name__ == "__main__":
    unittest.main()
