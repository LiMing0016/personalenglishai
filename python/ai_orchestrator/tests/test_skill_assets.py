from __future__ import annotations

from pathlib import Path
import unittest


SKILLS_ROOT = Path(__file__).resolve().parents[1] / "skills"


def _read_skill(name: str) -> str:
    return (SKILLS_ROOT / name / "SKILL.md").read_text(encoding="utf-8")


class SkillAssetsTest(unittest.TestCase):
    def test_grammar_check_skill_has_official_frontmatter(self) -> None:
        skill = _read_skill("grammar-check")

        self.assertTrue(skill.startswith("---\n"))
        frontmatter = skill.split("---", 2)[1]
        self.assertIn("name: grammar-check", frontmatter)
        self.assertIn("description:", frontmatter)
        self.assertNotIn("metadata:", frontmatter)

    def test_grammar_check_skill_description_contains_triggers(self) -> None:
        skill = _read_skill("grammar-check")
        frontmatter = skill.split("---", 2)[1]

        self.assertIn("grammar", frontmatter)
        self.assertIn("correction", frontmatter)
        self.assertIn("sentence correctness", frontmatter)
        self.assertIn("before polishing or scoring", frontmatter)

    def test_grammar_check_skill_uses_progressive_disclosure(self) -> None:
        skill = _read_skill("grammar-check")
        taxonomy = SKILLS_ROOT / "grammar-check" / "references" / "grammar-taxonomy.md"

        self.assertTrue(taxonomy.is_file())
        self.assertIn("references/grammar-taxonomy.md", skill)
        self.assertIn("only when", skill.lower())
        self.assertIn("Do not produce the final user-facing polish", skill)
        self.assertLess(len(skill.splitlines()), 120)

    def test_grammar_check_skill_defines_non_error_boundaries(self) -> None:
        skill = _read_skill("grammar-check")
        taxonomy = (SKILLS_ROOT / "grammar-check" / "references" / "grammar-taxonomy.md").read_text(
            encoding="utf-8"
        )

        self.assertIn("不算语法错误", skill)
        self.assertIn("主观风格偏好", skill)
        self.assertIn("只能标注为可优化", skill)
        self.assertIn("不因为个人风格偏好而建议改写", skill)
        self.assertIn("style_suggestion", taxonomy)
        self.assertIn("not_grammar_error", taxonomy)

    def test_grammar_explain_skill_has_official_frontmatter(self) -> None:
        skill = _read_skill("grammar-explain")

        self.assertTrue(skill.startswith("---\n"))
        frontmatter = skill.split("---", 2)[1]
        self.assertIn("name: grammar-explain", frontmatter)
        self.assertIn("description:", frontmatter)
        self.assertNotIn("metadata:", frontmatter)

    def test_grammar_explain_skill_description_contains_triggers(self) -> None:
        skill = _read_skill("grammar-explain")
        frontmatter = skill.split("---", 2)[1]

        self.assertIn("语法讲解", frontmatter)
        self.assertIn("grammar explanation", frontmatter)
        self.assertIn("从句", frontmatter)
        self.assertIn("时态", frontmatter)
        self.assertIn("区别辨析", frontmatter)

    def test_grammar_explain_skill_is_separate_from_grammar_check(self) -> None:
        skill = _read_skill("grammar-explain")
        reference = SKILLS_ROOT / "grammar-explain" / "references" / "explanation-patterns.md"

        self.assertTrue(reference.is_file())
        self.assertIn("references/explanation-patterns.md", skill)
        self.assertIn("不要把语法讲解变成语法检错", skill)
        self.assertIn("不要主动评分", skill)
        self.assertLess(len(skill.splitlines()), 140)


if __name__ == "__main__":
    unittest.main()
