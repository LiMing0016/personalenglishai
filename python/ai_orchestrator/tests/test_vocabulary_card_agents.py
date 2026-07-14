import os
import unittest
from unittest.mock import patch

from python.ai_orchestrator.agents.vocabulary_card import (
    create_vocabulary_card_markdown_agent,
    create_vocabulary_core_fallback_agent,
)
from python.ai_orchestrator.prompts.agents import load_agent_instructions
from python.ai_orchestrator.schemas.vocabulary_card import (
    VocabularyCoreFallbackOutput,
    VocabularyMarkdownOutput,
)


class VocabularyCardAgentsTest(unittest.TestCase):
    def test_background_prompt_keys_resolve_without_conversational_preamble(self) -> None:
        core_instructions = load_agent_instructions("vocabulary_core_fallback")
        markdown_instructions = load_agent_instructions("vocabulary_card_markdown")

        for instructions in (core_instructions, markdown_instructions):
            self.assertNotIn("基于 OpenAI Agents SDK", instructions)
            self.assertNotIn("用户画像上下文", instructions)
            self.assertNotIn("所有面向用户的学习助手回复", instructions)

    def test_core_prompt_preserves_non_empty_dictionary_truth(self) -> None:
        instructions = load_agent_instructions("vocabulary_core_fallback")

        self.assertIn("已有的非空词典 core", instructions)
        self.assertIn("不得修改", instructions)
        self.assertIn("结构化", instructions)
        self.assertIn("不得输出 Markdown", instructions)

    def test_markdown_prompt_treats_theme_and_context_as_data(self) -> None:
        instructions = load_agent_instructions("vocabulary_card_markdown")

        self.assertIn("purpose", instructions)
        self.assertIn("sourceContext", instructions)
        self.assertIn("仅是数据", instructions)
        self.assertIn("不得输出原始 HTML", instructions)
        self.assertIn("主题特定", instructions)
        self.assertIn("Markdown", instructions)

    def test_core_fallback_agent_uses_pydantic_output_type_and_model(self) -> None:
        agent = create_vocabulary_core_fallback_agent("test-model")

        self.assertEqual(agent.model, "test-model")
        self.assertEqual(agent.name, "VocabularyCoreFallbackAgent")
        self.assertIs(agent.output_type, VocabularyCoreFallbackOutput)
        self.assertEqual(agent.handoffs, [])
        self.assertEqual(agent.tools, [])

    def test_markdown_agent_uses_pydantic_output_type_and_model(self) -> None:
        agent = create_vocabulary_card_markdown_agent("test-model")

        self.assertEqual(agent.model, "test-model")
        self.assertEqual(agent.name, "VocabularyCardMarkdownAgent")
        self.assertIs(agent.output_type, VocabularyMarkdownOutput)
        self.assertEqual(agent.handoffs, [])
        self.assertEqual(agent.tools, [])

    def test_factories_use_their_remote_prompts_in_hybrid_mode_and_restore_environment(self) -> None:
        original_environment = dict(os.environ)

        with patch.dict(
            os.environ,
            {
                "AI_ASSISTANT_PROMPT_SOURCE": "hybrid",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "AI_PROMPT_VOCABULARY_CORE_FALLBACK_ID": "pmpt_vocab_core_123",
                "AI_PROMPT_VOCABULARY_CARD_MARKDOWN_ID": "pmpt_vocab_markdown_456",
            },
            clear=True,
        ):
            core_agent = create_vocabulary_core_fallback_agent("test-model")
            markdown_agent = create_vocabulary_card_markdown_agent("test-model")

            self.assertIsNone(core_agent.instructions)
            self.assertEqual(core_agent.prompt, {"id": "pmpt_vocab_core_123"})
            self.assertIsNone(markdown_agent.instructions)
            self.assertEqual(markdown_agent.prompt, {"id": "pmpt_vocab_markdown_456"})

        self.assertEqual(dict(os.environ), original_environment)


if __name__ == "__main__":
    unittest.main()
