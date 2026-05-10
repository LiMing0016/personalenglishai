import unittest

from python.ai_orchestrator.prompts.agents import load_agent_instructions


class AssistantOutputFormatPromptTest(unittest.TestCase):
    def test_agent_instructions_include_markdown_learning_output_rules(self) -> None:
        instructions = load_agent_instructions("vocab")

        self.assertIn("Markdown 输出规范", instructions)
        self.assertIn("对比类问题", instructions)
        self.assertIn("| 词 | 核心含义 | 重点 | 常见中文 |", instructions)
        self.assertIn("message.delta", instructions)
        self.assertIn("不要输出大段无标题长文本", instructions)


if __name__ == "__main__":
    unittest.main()
