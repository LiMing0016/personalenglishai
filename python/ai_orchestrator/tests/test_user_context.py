import unittest
from importlib.resources import files
from inspect import getsource

from python.ai_orchestrator import prompts
from python.ai_orchestrator.prompts import user_context
from python.ai_orchestrator.prompts.user_context import build_contextual_user_message


class UserContextTest(unittest.TestCase):
    def test_primary_stage_injects_textbook_style_standard(self) -> None:
        message = build_contextual_user_message("Explain this sentence.", study_stage="primary")

        self.assertIn("- 学段: 小学", message)
        self.assertIn("[学段输出标准]", message)
        self.assertIn("小学独立标准", message)
        self.assertIn("校内教材", message)
        self.assertIn("新概念英语", message)
        self.assertIn("原创例句", message)
        self.assertIn("不要声称例句来自真实教材", message)

    def test_junior_and_senior_stages_use_different_standards(self) -> None:
        junior = build_contextual_user_message("Give me examples.", study_stage="junior")
        senior = build_contextual_user_message("Give me examples.", study_stage="senior")

        self.assertIn("初中独立标准", junior)
        self.assertIn("高中独立标准", senior)
        self.assertIn("基础语法", junior)
        self.assertIn("高考", senior)
        self.assertNotEqual(junior, senior)

    def test_exam_stages_inject_individual_exam_difficulty_standards(self) -> None:
        cases = {
            "cet4": ("四级独立标准", "大学英语四级"),
            "cet6": ("六级独立标准", "大学英语六级"),
            "postgrad": ("考研独立标准", "考研英语"),
            "ielts": ("雅思独立标准", "IELTS"),
            "toefl": ("托福独立标准", "TOEFL"),
        }

        for stage, markers in cases.items():
            with self.subTest(stage=stage):
                message = build_contextual_user_message("Polish this paragraph.", study_stage=stage)

                for marker in markers:
                    self.assertIn(marker, message)
                self.assertIn("真题", message)
                self.assertIn("外刊", message)
                self.assertIn("杂志", message)
                self.assertIn("原创例句", message)
                self.assertIn("不要复制或声称来自真实真题", message)

    def test_unknown_stage_only_injects_stage_label_without_specific_standard(self) -> None:
        message = build_contextual_user_message("Help me.", study_stage="custom")

        self.assertIn("- 学段: custom", message)
        self.assertNotIn("[学段输出标准]", message)
        self.assertIn("[用户消息]", message)

    def test_exam_mode_still_combines_with_stage_standard(self) -> None:
        message = build_contextual_user_message(
            "Evaluate this essay.",
            study_stage="postgrad",
            assistant_mode="exam",
        )

        self.assertIn("考研独立标准", message)
        self.assertIn("[对话模式上下文]", message)
        self.assertIn("- 当前模式: 考试模式", message)

    def test_exam_boost_mode_is_normalized_to_exam_context(self) -> None:
        message = build_contextual_user_message("Evaluate this essay.", assistant_mode="exam_boost")

        self.assertIn("[对话模式上下文]", message)
        self.assertIn("- 当前模式: 考试模式", message)

    def test_stage_standards_are_loaded_from_prompt_asset(self) -> None:
        prompt_asset = files(prompts).joinpath("shared/stage_output_standards.md")

        self.assertTrue(prompt_asset.is_file())
        self.assertIn("小学独立标准", prompt_asset.read_text(encoding="utf-8"))
        self.assertNotIn("小学独立标准", getsource(user_context))
        self.assertNotIn("例句贴近真题", getsource(user_context))


if __name__ == "__main__":
    unittest.main()
