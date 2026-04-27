from __future__ import annotations

import unittest
from pathlib import Path

from python.ai_orchestrator.tools.exam_prompt_style_reference import ExamPromptStyleReferenceBuilder
from python.ai_orchestrator.tools.exam_prompt_style_reference import PostgradPromptSeedRepository


class ExamPromptStyleReferenceBuilderTest(unittest.TestCase):
    def test_postgrad_chart_reference_uses_seed_without_raw_prompt_text(self) -> None:
        sql_path = (
            Path(__file__).resolve().parents[3]
            / "backend"
            / "src"
            / "main"
            / "resources"
            / "db"
            / "postgrad_prompt_seed.sql"
        )
        builder = ExamPromptStyleReferenceBuilder(PostgradPromptSeedRepository(sql_path))

        reference = builder.build(
            study_stage="postgrad",
            task_type="task2",
            prompt_type="chart",
            topic="中国GDP和通胀率近十年变化",
        )

        self.assertIsNotNone(reference)
        assert reference is not None
        self.assertEqual(reference.study_stage, "postgrad")
        self.assertEqual(reference.display_name, "考研英语")
        rendered = reference.render()
        self.assertIn("[考研英语题库风格参考]", rendered)
        self.assertIn("匹配样本", rendered)
        self.assertIn("图表作文", rendered)
        self.assertIn("原创题单", rendered)
        self.assertNotIn("Mobile-phone subscriptions", rendered)
        self.assertNotIn("Write an essay based on the following chart", rendered)

    def test_non_postgrad_does_not_build_postgrad_reference(self) -> None:
        builder = ExamPromptStyleReferenceBuilder(PostgradPromptSeedRepository(Path("missing.sql")))

        reference = builder.build(
            study_stage="ielts",
            task_type="task1",
            prompt_type="chart",
            topic="GDP trends",
        )

        self.assertIsNone(reference)


if __name__ == "__main__":
    unittest.main()
