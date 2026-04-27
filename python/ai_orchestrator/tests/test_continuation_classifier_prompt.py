import unittest
from importlib.resources import files

from python.ai_orchestrator import prompts


class ContinuationClassifierPromptTest(unittest.TestCase):
    def test_prompt_asset_exists_and_requires_structured_output(self) -> None:
        prompt_asset = files(prompts).joinpath("shared/continuation_classifier.md")

        self.assertTrue(prompt_asset.is_file())
        prompt = prompt_asset.read_text(encoding="utf-8")
        self.assertIn("续问判定器", prompt)
        self.assertIn("relation", prompt)
        self.assertIn("resolved_intent", prompt)
        self.assertIn("confidence", prompt)
        self.assertIn("不要生成最终学习内容", prompt)


if __name__ == "__main__":
    unittest.main()
