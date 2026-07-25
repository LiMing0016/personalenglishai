import copy
import unittest

from pydantic import ValidationError

from python.ai_orchestrator.schemas.vocabulary_card import (
    VocabularyCardBlocks,
    VocabularyCardGenerationRequest,
    VocabularyCardGenerationResponse,
    VocabularyCore,
    VocabularyCoreFallbackOutput,
    VocabularyGenerationMetadata,
    VocabularyMeaning,
    VocabularyNoteBlock,
    VocabularyPhonetic,
    VocabularySense,
    VocabularyThemeSnapshot,
)


def core_payload() -> dict[str, object]:
    return {
        "schemaVersion": 2,
        "term": "anthropic",
        "phonetics": [
            {"region": "uk", "text": "anˈθrɒpɪk", "audioUrl": None}
        ],
        "senses": [
            {
                "id": "sense_adjective_01",
                "partOfSpeech": "adjective",
                "meanings": [
                    {
                        "id": "meaning_human_01",
                        "definitionEn": "related to human existence or influence",
                        "definitionZh": "与人类存在或影响有关的",
                    }
                ],
            }
        ],
    }


def card_blocks_payload() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "blocks": [
            {
                "id": "block_examples_01",
                "type": "exampleList",
                "title": "常用例句",
                "meaningRefs": ["meaning_human_01"],
                "format": "structured",
                "content": {
                    "items": [
                        {
                            "sentence": "The anthropic principle concerns human existence.",
                            "translation": "人择原理关注人类存在。",
                        }
                    ]
                },
                "source": "ai",
                "sourceRef": None,
                "sortOrder": 10,
                "userEdited": False,
                "locked": False,
            },
            {
                "id": "block_collocations_01",
                "type": "collocationList",
                "title": "搭配表达",
                "meaningRefs": ["meaning_human_01"],
                "format": "structured",
                "content": {
                    "items": [
                        {
                            "expression": "anthropic principle",
                            "translation": "人择原理",
                        }
                    ]
                },
                "source": "ai",
                "sourceRef": None,
                "sortOrder": 20,
                "userEdited": False,
                "locked": False,
            },
            {
                "id": "block_usage_01",
                "type": "usageBoundary",
                "title": "使用边界",
                "meaningRefs": ["meaning_human_01"],
                "format": "structured",
                "content": {
                    "useWhen": ["哲学、宇宙学或环境科学语境"],
                    "avoidWhen": ["指人类这一生物学物种时"],
                },
                "source": "ai",
                "sourceRef": None,
                "sortOrder": 30,
                "userEdited": False,
                "locked": False,
            },
            {
                "id": "block_contrast_01",
                "type": "contrastTable",
                "title": "易混辨析",
                "meaningRefs": ["meaning_human_01"],
                "format": "structured",
                "content": {
                    "rows": [
                        {
                            "term": "anthropic",
                            "focus": "与人类存在或活动有关",
                            "typicalContext": "哲学、宇宙学、环境科学",
                        }
                    ]
                },
                "source": "ai",
                "sourceRef": None,
                "sortOrder": 40,
                "userEdited": False,
                "locked": False,
            },
            {
                "id": "block_memory_01",
                "type": "memoryTip",
                "title": "记忆提示",
                "meaningRefs": [],
                "format": "structured",
                "content": {
                    "points": ["anthro- 表示人类，-pic 表示相关的。"]
                },
                "source": "ai",
                "sourceRef": None,
                "sortOrder": 50,
                "userEdited": False,
                "locked": False,
            },
            {
                "id": "block_note_01",
                "type": "note",
                "title": "我的笔记",
                "meaningRefs": ["meaning_human_01"],
                "format": "markdown",
                "content": "这个词可以和 **human influence** 联系记忆。",
                "source": "user",
                "sourceRef": None,
                "sortOrder": 60,
                "userEdited": True,
                "locked": True,
            },
        ],
    }


def request_payload() -> dict[str, object]:
    return {
        "contractVersion": 2,
        "coreSchemaVersion": 2,
        "cardBlocksSchemaVersion": 1,
        "requestId": "job_123:attempt_1",
        "traceId": "vocab-job_123-attempt_1",
        "timeoutBudgetMs": 45_000,
        "term": "anthropic",
        "dictionaryCore": core_payload(),
        "sourceContext": "The anthropic principle is discussed in this chapter.",
        "theme": {
            "uid": "theme_system_exam",
            "version": 1,
            "name": "Exam",
            "purpose": "用于考试词义、搭配和易错点学习",
            "promptStrategyKey": "exam-blocks-v1",
            "contentFormatVersion": 1,
        },
    }


def response_payload(
    *,
    outcome: str = "complete",
    warning: str | None = None,
    blocks: dict[str, object] | None = None,
) -> dict[str, object]:
    return {
        "contractVersion": 2,
        "coreSchemaVersion": 2,
        "cardBlocksSchemaVersion": 1,
        "core": core_payload(),
        "cardBlocks": card_blocks_payload() if blocks is None else blocks,
        "outcome": outcome,
        "warning": warning,
        "generation": {
            "provider": "openai",
            "model": "configured-model",
            "promptVersion": "vocabulary-card-blocks-v1",
            "modelCallCount": 2,
            "traceId": "vocab-job_123-attempt_1",
        },
    }


class VocabularyCardSchemasTest(unittest.TestCase):
    def test_core_v2_requires_stable_sense_and_meaning_ids(self) -> None:
        parsed = VocabularyCore.model_validate(core_payload())
        self.assertEqual(parsed.schema_version, 2)
        self.assertEqual(parsed.senses[0].id, "sense_adjective_01")
        self.assertEqual(parsed.senses[0].meanings[0].id, "meaning_human_01")

        for path in (("senses", 0, "id"), ("senses", 0, "meanings", 0, "id")):
            invalid = copy.deepcopy(core_payload())
            cursor = invalid
            for key in path[:-1]:
                cursor = cursor[key]
            del cursor[path[-1]]
            with self.subTest(path=path), self.assertRaises(ValidationError):
                VocabularyCore.model_validate(invalid)

    def test_request_uses_contract_two_and_declares_both_schema_versions(self) -> None:
        request = VocabularyCardGenerationRequest.model_validate(request_payload())

        self.assertEqual(request.contract_version, 2)
        self.assertEqual(request.core_schema_version, 2)
        self.assertEqual(request.card_blocks_schema_version, 1)
        self.assertEqual(
            request.model_dump(by_alias=True)["theme"]["promptStrategyKey"],
            "exam-blocks-v1",
        )

    def test_cross_service_models_forbid_unknown_fields(self) -> None:
        for model, payload in (
            (VocabularyCardGenerationRequest, request_payload()),
            (VocabularyCore, core_payload()),
            (VocabularyCardBlocks, card_blocks_payload()),
        ):
            invalid = copy.deepcopy(payload)
            invalid["unexpected"] = True
            with self.subTest(model=model.__name__), self.assertRaises(ValidationError):
                model.model_validate(invalid)

    def test_nested_core_models_require_every_wire_field(self) -> None:
        cases = (
            (VocabularyPhonetic, core_payload()["phonetics"][0], ("region", "text", "audioUrl")),
            (
                VocabularyMeaning,
                core_payload()["senses"][0]["meanings"][0],
                ("id", "definitionEn", "definitionZh"),
            ),
            (
                VocabularySense,
                core_payload()["senses"][0],
                ("id", "partOfSpeech", "meanings"),
            ),
        )
        for model, payload, fields in cases:
            for field in fields:
                invalid = copy.deepcopy(payload)
                del invalid[field]
                with self.subTest(model=model.__name__, field=field), self.assertRaises(ValidationError):
                    model.model_validate(invalid)

    def test_card_blocks_accept_all_supported_types_and_serialize_aliases(self) -> None:
        parsed = VocabularyCardBlocks.model_validate(card_blocks_payload())

        self.assertEqual(
            [block.type for block in parsed.blocks],
            [
                "exampleList",
                "collocationList",
                "usageBoundary",
                "contrastTable",
                "memoryTip",
                "note",
            ],
        )
        serialized = parsed.model_dump(by_alias=True)
        self.assertEqual(serialized["blocks"][0]["meaningRefs"], ["meaning_human_01"])
        self.assertEqual(serialized["blocks"][-1]["userEdited"], True)

    def test_card_blocks_reject_duplicate_block_ids(self) -> None:
        invalid = card_blocks_payload()
        invalid["blocks"][1]["id"] = invalid["blocks"][0]["id"]

        with self.assertRaises(ValidationError):
            VocabularyCardBlocks.model_validate(invalid)

    def test_response_rejects_dangling_meaning_references(self) -> None:
        invalid_blocks = card_blocks_payload()
        invalid_blocks["blocks"][0]["meaningRefs"] = ["meaning_missing"]

        with self.assertRaises(ValidationError):
            VocabularyCardGenerationResponse.model_validate(
                response_payload(blocks=invalid_blocks)
            )

    def test_card_blocks_reject_unknown_types_and_wrong_structured_content(self) -> None:
        unknown_type = card_blocks_payload()
        unknown_type["blocks"][0]["type"] = "dictionaryDump"
        with self.assertRaises(ValidationError):
            VocabularyCardBlocks.model_validate(unknown_type)

        wrong_content = card_blocks_payload()
        wrong_content["blocks"][0]["content"] = {"points": ["wrong shape"]}
        with self.assertRaises(ValidationError):
            VocabularyCardBlocks.model_validate(wrong_content)

        wrong_format = card_blocks_payload()
        wrong_format["blocks"][1]["format"] = "markdown"
        with self.assertRaises(ValidationError):
            VocabularyCardBlocks.model_validate(wrong_format)

    def test_note_is_the_only_markdown_block_and_rejects_raw_html(self) -> None:
        note = copy.deepcopy(card_blocks_payload()["blocks"][-1])
        parsed = VocabularyNoteBlock.model_validate(note)
        self.assertIn("**human influence**", parsed.content)

        note["content"] = "<script>alert('x')</script>"
        with self.assertRaises(ValidationError):
            VocabularyNoteBlock.model_validate(note)

        note["content"] = "Use `<script>alert('x')</script>` literally."
        self.assertIn("`<script>", VocabularyNoteBlock.model_validate(note).content)

    def test_response_requires_complete_or_partial_card_blocks_state(self) -> None:
        complete = VocabularyCardGenerationResponse.model_validate(response_payload())
        self.assertEqual(complete.outcome, "complete")

        empty_blocks = {"schemaVersion": 1, "blocks": []}
        partial = VocabularyCardGenerationResponse.model_validate(
            response_payload(
                outcome="partial",
                warning="card_blocks_unavailable",
                blocks=empty_blocks,
            )
        )
        self.assertEqual(partial.card_blocks.blocks, [])

        invalid_states = (
            response_payload(outcome="complete", warning="card_blocks_unavailable"),
            response_payload(outcome="complete", blocks=empty_blocks),
            response_payload(outcome="partial", blocks=empty_blocks),
            response_payload(
                outcome="partial",
                warning="card_blocks_unavailable",
            ),
        )
        for payload in invalid_states:
            with self.subTest(payload=payload), self.assertRaises(ValidationError):
                VocabularyCardGenerationResponse.model_validate(payload)

    def test_json_schemas_expose_wire_keys_for_structured_output(self) -> None:
        core_schema = VocabularyCoreFallbackOutput.model_json_schema()
        definitions = core_schema["$defs"]
        self.assertTrue({"schemaVersion", "term", "phonetics", "senses"} <= set(core_schema["required"]))
        self.assertTrue({"id", "partOfSpeech", "meanings"} <= set(definitions["VocabularySense"]["required"]))
        self.assertTrue({"id", "definitionEn", "definitionZh"} <= set(definitions["VocabularyMeaning"]["required"]))

        block_schema = VocabularyCardBlocks.model_json_schema()
        self.assertTrue({"schemaVersion", "blocks"} <= set(block_schema["required"]))

    def test_term_ids_bounds_and_metadata_remain_strict(self) -> None:
        blank = request_payload()
        blank["term"] = "   "
        blank["dictionaryCore"] = {**blank["dictionaryCore"], "term": "   "}
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(blank)

        mismatch = request_payload()
        mismatch["dictionaryCore"] = {**mismatch["dictionaryCore"], "term": "human"}
        with self.assertRaises(ValidationError):
            VocabularyCardGenerationRequest.model_validate(mismatch)

        invalid_id = core_payload()
        invalid_id["senses"][0]["id"] = "private sentence"
        with self.assertRaises(ValidationError):
            VocabularyCore.model_validate(invalid_id)

        invalid_metadata = response_payload()["generation"]
        invalid_metadata["traceId"] = "private sentence"
        with self.assertRaises(ValidationError):
            VocabularyGenerationMetadata.model_validate(invalid_metadata)

    def test_theme_and_response_nested_models_stay_strict(self) -> None:
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
        for model, payload, fields in cases:
            for field in fields:
                invalid = copy.deepcopy(payload)
                del invalid[field]
                with self.subTest(model=model.__name__, field=field), self.assertRaises(ValidationError):
                    model.model_validate(invalid)


if __name__ == "__main__":
    unittest.main()
