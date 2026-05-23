import unittest

from pydantic import ValidationError

from python.ai_orchestrator.schemas.writing_coach import WritingCoachOutlineOutput
from python.ai_orchestrator.schemas.writing_coach import WritingCoachTopicAnalysisOutput


class WritingCoachSchemaTest(unittest.TestCase):
    def test_topic_analysis_output_uses_stable_task_anchors(self) -> None:
        output = WritingCoachTopicAnalysisOutput.model_validate(
            {
                "schemaVersion": "writing_topic_analysis_v1",
                "stage": "analyze",
                "topicBrief": "讨论 AI 是否有利于教育。",
                "centralTask": "明确 AI 对教育的利弊并给出立场。",
                "taskType": "ielts_task2",
                "genre": "argumentative",
                "stanceRequirement": "需要表明同意程度。",
                "mustAnswerPoints": [
                    {
                        "pointId": "P1",
                        "point": "说明 AI 的教育收益。",
                        "whyRequired": "题目要求判断 AI 是否 beneficial。",
                        "evidenceFromPrompt": "whether AI is beneficial to education",
                    }
                ],
                "taskConstraints": [
                    {
                        "constraintType": "word_count",
                        "value": "250+ words",
                        "impact": "需要两到三个主体段展开。",
                    }
                ],
                "offTopicRisks": [
                    {
                        "risk": "只讨论科技发展，不回到教育。",
                        "reason": "题目限定 education。",
                        "prevention": "每段都回扣 learning 或 teaching。",
                    }
                ],
                "recommendedStructure": [
                    {"step": "Introduction", "purpose": "改写题目并表明立场。"}
                ],
                "rubricFocus": [
                    {
                        "dimension": "task_response",
                        "focus": "回应题目并展开立场。",
                        "whyItMatters": "影响是否切题。",
                    }
                ],
                "missingInfo": [],
                "confidence": "high",
                "nextStepSuggestion": "确认审题后进入提纲。",
            }
        )

        self.assertEqual(output.schema_version, "writing_topic_analysis_v1")
        self.assertEqual(output.must_answer_points[0].point_id, "P1")
        self.assertEqual(output.task_constraints[0].constraint_type, "word_count")
        dumped = output.model_dump(by_alias=True)
        self.assertIn("schemaVersion", dumped)
        self.assertIn("mustAnswerPoints", dumped)
        self.assertIn("rubricFocus", dumped)
        self.assertIn("P1", output.to_markdown())

    def test_topic_analysis_output_forbids_unknown_fields(self) -> None:
        with self.assertRaises(ValidationError):
            WritingCoachTopicAnalysisOutput.model_validate(
                {
                    "schemaVersion": "writing_topic_analysis_v1",
                    "stage": "analyze",
                    "topicBrief": "题目主旨",
                    "centralTask": "中心任务",
                    "taskType": "task2",
                    "genre": "argumentative",
                    "stanceRequirement": "需要表态",
                    "mustAnswerPoints": [],
                    "taskConstraints": [],
                    "offTopicRisks": [],
                    "recommendedStructure": [],
                    "rubricFocus": [],
                    "missingInfo": [],
                    "confidence": "high",
                    "nextStepSuggestion": "进入提纲",
                    "unexpected": "不允许",
                }
            )

    def test_outline_output_reuses_topic_analysis_point_ids(self) -> None:
        output = WritingCoachOutlineOutput.model_validate(
            {
                "schemaVersion": "writing_outline_v1",
                "stage": "outline",
                "basedOnAnalysis": "讨论 AI 是否有利于教育。",
                "controllingIdea": "AI 整体有益，但需要谨慎使用。",
                "outlineMode": "argumentative",
                "paragraphPlan": [
                    {
                        "paragraphId": "B1",
                        "paragraphRole": "body_1",
                        "paragraphGoal": "解释个性化学习收益。",
                        "topicSentence": "AI can make learning more personalized.",
                        "mustAnswerPointIds": ["P1"],
                        "keyContent": ["按学生水平调整练习。"],
                        "evidenceOrExamples": ["adaptive learning apps"],
                        "coherenceDevice": "cause-effect",
                        "avoid": ["不要泛泛写科技很重要。"],
                        "targetWordCount": "70-90 words",
                    }
                ],
                "coverageCheck": [
                    {
                        "pointId": "P1",
                        "coveredBy": ["B1"],
                        "coverageNote": "B1 覆盖 AI 的教育收益。",
                    }
                ],
                "transitionPlan": ["B1 到 B2 用 However 转入风险。"],
                "rubricAlignment": [
                    {
                        "dimension": "coherence",
                        "alignment": "每段只有一个中心功能。",
                    }
                ],
                "writingTips": ["先写清楚观点，再升级表达。"],
                "nextStepSuggestion": "进入下一段陪写。",
            }
        )

        self.assertEqual(output.paragraph_plan[0].must_answer_point_ids, ["P1"])
        self.assertEqual(output.coverage_check[0].covered_by, ["B1"])
        markdown = output.to_markdown()
        self.assertIn("## 提纲", markdown)
        self.assertIn("覆盖检查", markdown)
        self.assertIn("P1", markdown)


if __name__ == "__main__":
    unittest.main()
