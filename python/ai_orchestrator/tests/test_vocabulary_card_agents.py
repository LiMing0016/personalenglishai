import os
import unittest
from unittest.mock import patch

from python.ai_orchestrator.agents.vocabulary_card import (
    create_vocabulary_card_blocks_agent,
    create_vocabulary_core_agent,
)
from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.vocabulary_card import (
    VocabularyCardBlocks,
    VocabularyCoreFallbackOutput,
)


class VocabularyCardAgentsTest(unittest.TestCase):
    def test_background_prompts_are_structured_and_non_conversational(self) -> None:
        core_instructions = load_agent_instructions("vocabulary_core_fallback")
        blocks_instructions = load_agent_instructions("vocabulary_card_blocks")

        for instructions in (core_instructions, blocks_instructions):
            self.assertNotIn("基于 OpenAI Agents SDK", instructions)
            self.assertNotIn("用户画像上下文", instructions)
            self.assertIn("结构化", instructions)

    def test_core_prompt_generates_learning_core_without_theme_instructions(self) -> None:
        instructions = load_agent_instructions("vocabulary_core_fallback")

        self.assertIn("dictionaryCore", instructions)
        self.assertIn("schemaVersion", instructions)
        self.assertIn("sense", instructions)
        self.assertIn("meaning", instructions)
        self.assertIn("audioUrl", instructions)
        self.assertNotIn("theme", instructions)

    def test_blocks_prompt_treats_theme_and_context_as_data(self) -> None:
        instructions = load_agent_instructions("vocabulary_card_blocks")

        self.assertIn("purpose", instructions)
        self.assertIn("sourceContext", instructions)
        self.assertIn("仅是数据", instructions)
        self.assertIn("meaningRefs", instructions)
        self.assertIn("note", instructions)
        self.assertIn("原始 HTML", instructions)

    def test_agent_factories_use_strict_output_types(self) -> None:
        core_agent = create_vocabulary_core_agent("test-model")
        blocks_agent = create_vocabulary_card_blocks_agent("test-model")

        self.assertEqual(core_agent.name, "VocabularyCoreAgent")
        self.assertIs(core_agent.output_type, VocabularyCoreFallbackOutput)
        self.assertEqual(blocks_agent.name, "VocabularyCardBlocksAgent")
        self.assertIs(blocks_agent.output_type, VocabularyCardBlocks)
        for agent in (core_agent, blocks_agent):
            self.assertEqual(agent.model, "test-model")
            self.assertEqual(agent.handoffs, [])
            self.assertEqual(agent.tools, [])

    def test_factories_use_pinned_remote_prompts_in_hybrid_mode(self) -> None:
        original_environment = dict(os.environ)
        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "hybrid",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "AI_PROMPT_VOCABULARY_CORE_FALLBACK_ID": "pmpt_vocab_core_123",
                "AI_PROMPT_VOCABULARY_CARD_BLOCKS_ID": "pmpt_vocab_blocks_456",
            },
            clear=True,
        ):
            core_agent = create_vocabulary_core_agent("test-model")
            blocks_agent = create_vocabulary_card_blocks_agent("test-model")

            self.assertEqual(core_agent.prompt, {"id": "pmpt_vocab_core_123"})
            self.assertEqual(blocks_agent.prompt, {"id": "pmpt_vocab_blocks_456"})

        self.assertEqual(dict(os.environ), original_environment)


if __name__ == "__main__":
    unittest.main()
