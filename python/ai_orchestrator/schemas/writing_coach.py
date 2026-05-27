from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


StructuredWritingCoachAction = Literal["analyze", "outline", "next", "topic", "polish", "draft"]
WritingCoachRouteType = Literal["run_stage", "answer_direct", "ask_clarification"]
WritingCoachEditIntent = Literal[
    "none",
    "replace_selection",
    "insert_after_selection",
    "append_paragraph",
    "replace_document",
]


class StrictWritingCoachModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")


class WritingCoachContextPolicy(StrictWritingCoachModel):
    include_topic: bool = Field(default=True, alias="includeTopic")
    include_rubric: bool = Field(default=True, alias="includeRubric")
    include_selection: bool = Field(default=False, alias="includeSelection")
    include_draft: bool = Field(default=False, alias="includeDraft")
    include_recent_messages: bool = Field(default=True, alias="includeRecentMessages")


class WritingCoachRouteDecision(StrictWritingCoachModel):
    route_type: WritingCoachRouteType = Field(alias="routeType")
    target_action: StructuredWritingCoachAction | None = Field(default=None, alias="targetAction")
    edit_intent: WritingCoachEditIntent = Field(default="none", alias="editIntent")
    context_policy: WritingCoachContextPolicy = Field(default_factory=WritingCoachContextPolicy, alias="contextPolicy")
    confidence: float = Field(ge=0.0, le=1.0)
    missing_inputs: list[str] = Field(default_factory=list, alias="missingInputs")
    reason: str

    @model_validator(mode="after")
    def validate_route_contract(self) -> "WritingCoachRouteDecision":
        if self.route_type == "run_stage" and self.target_action is None:
            raise ValueError("run_stage decisions require targetAction")
        if self.route_type != "run_stage" and self.target_action is not None:
            raise ValueError("targetAction is only allowed for run_stage decisions")
        if self.route_type == "ask_clarification" and not self.missing_inputs:
            raise ValueError("ask_clarification decisions require missingInputs")
        if self.edit_intent != "none" and self.route_type != "run_stage":
            raise ValueError("editIntent is only allowed when routeType is run_stage")
        return self


class MustAnswerPoint(StrictWritingCoachModel):
    point_id: str = Field(alias="pointId")
    point: str
    why_required: str = Field(alias="whyRequired")
    evidence_from_prompt: str = Field(alias="evidenceFromPrompt")


class TaskConstraint(StrictWritingCoachModel):
    constraint_type: Literal["word_count", "audience", "format", "stance", "material", "time", "other"] = Field(
        alias="constraintType"
    )
    value: str
    impact: str


class OffTopicRisk(StrictWritingCoachModel):
    risk: str
    reason: str
    prevention: str


class RecommendedStructureStep(StrictWritingCoachModel):
    step: str
    purpose: str


class RubricFocusItem(StrictWritingCoachModel):
    dimension: str
    focus: str
    why_it_matters: str = Field(alias="whyItMatters")


class WritingCoachTopicAnalysisOutput(StrictWritingCoachModel):
    schema_version: Literal["writing_topic_analysis_v1"] = Field(alias="schemaVersion")
    stage: Literal["analyze"]
    topic_brief: str = Field(alias="topicBrief")
    central_task: str = Field(alias="centralTask")
    task_type: str = Field(alias="taskType")
    genre: str
    stance_requirement: str = Field(alias="stanceRequirement")
    must_answer_points: list[MustAnswerPoint] = Field(alias="mustAnswerPoints")
    task_constraints: list[TaskConstraint] = Field(alias="taskConstraints")
    off_topic_risks: list[OffTopicRisk] = Field(alias="offTopicRisks")
    recommended_structure: list[RecommendedStructureStep] = Field(alias="recommendedStructure")
    rubric_focus: list[RubricFocusItem] = Field(alias="rubricFocus")
    missing_info: list[str] = Field(alias="missingInfo")
    confidence: Literal["high", "medium", "low"]
    next_step_suggestion: str = Field(alias="nextStepSuggestion")

    def to_markdown(self) -> str:
        lines = [
            "## 审题结果",
            "",
            f"**题目主旨**：{self.topic_brief}",
            "",
            f"**中心任务**：{self.central_task}",
            "",
            f"**题型/文体**：{self.task_type} / {self.genre}",
            "",
            f"**立场要求**：{self.stance_requirement}",
            "",
            "**必答点**：",
        ]
        for point in self.must_answer_points:
            lines.append(f"- {point.point_id}: {point.point}")
            lines.append(f"  - 原因：{point.why_required}")
            lines.append(f"  - 题目依据：{point.evidence_from_prompt}")
        lines.extend(["", "**题目限制**："])
        for constraint in self.task_constraints:
            lines.append(f"- {constraint.constraint_type}: {constraint.value}；影响：{constraint.impact}")
        lines.extend(["", "**偏题风险**："])
        for risk in self.off_topic_risks:
            lines.append(f"- {risk.risk}")
            lines.append(f"  - 原因：{risk.reason}")
            lines.append(f"  - 预防：{risk.prevention}")
        lines.extend(["", "**推荐结构**："])
        for step in self.recommended_structure:
            lines.append(f"- {step.step}: {step.purpose}")
        lines.extend(["", "**Rubric 关注点**："])
        for item in self.rubric_focus:
            lines.append(f"- {item.dimension}: {item.focus}；原因：{item.why_it_matters}")
        if self.missing_info:
            lines.extend(["", "**缺失信息**：", *_render_bullets(self.missing_info)])
        lines.extend(["", f"**置信度**：{self.confidence}", "", f"**下一步建议**：{self.next_step_suggestion}"])
        return "\n".join(lines).strip()


class WritingCoachOutlineParagraph(StrictWritingCoachModel):
    paragraph_id: str = Field(alias="paragraphId")
    paragraph_role: Literal["introduction", "overview", "body_1", "body_2", "body_3", "conclusion"] = Field(
        alias="paragraphRole"
    )
    paragraph_goal: str = Field(alias="paragraphGoal")
    topic_sentence: str = Field(alias="topicSentence")
    must_answer_point_ids: list[str] = Field(alias="mustAnswerPointIds")
    key_content: list[str] = Field(alias="keyContent")
    evidence_or_examples: list[str] = Field(alias="evidenceOrExamples")
    coherence_device: str = Field(alias="coherenceDevice")
    avoid: list[str]
    target_word_count: str = Field(alias="targetWordCount")


class CoverageCheckItem(StrictWritingCoachModel):
    point_id: str = Field(alias="pointId")
    covered_by: list[str] = Field(alias="coveredBy")
    coverage_note: str = Field(alias="coverageNote")


class RubricAlignmentItem(StrictWritingCoachModel):
    dimension: str
    alignment: str


class WritingCoachOutlineOutput(StrictWritingCoachModel):
    schema_version: Literal["writing_outline_v1"] = Field(alias="schemaVersion")
    stage: Literal["outline"]
    based_on_analysis: str = Field(alias="basedOnAnalysis")
    controlling_idea: str = Field(alias="controllingIdea")
    outline_mode: Literal["argumentative", "report", "letter", "narrative", "general"] = Field(alias="outlineMode")
    paragraph_plan: list[WritingCoachOutlineParagraph] = Field(alias="paragraphPlan")
    coverage_check: list[CoverageCheckItem] = Field(alias="coverageCheck")
    transition_plan: list[str] = Field(alias="transitionPlan")
    rubric_alignment: list[RubricAlignmentItem] = Field(alias="rubricAlignment")
    writing_tips: list[str] = Field(alias="writingTips")
    next_step_suggestion: str = Field(alias="nextStepSuggestion")

    def to_markdown(self) -> str:
        lines = [
            "## 提纲",
            "",
            f"**审题依据**：{self.based_on_analysis}",
            "",
            f"**核心表达方向**：{self.controlling_idea}",
            "",
            f"**提纲模式**：{self.outline_mode}",
            "",
            "**段落结构**：",
        ]
        for paragraph in self.paragraph_plan:
            lines.extend(
                [
                    f"- **{paragraph.paragraph_id} / {paragraph.paragraph_role}**：{paragraph.paragraph_goal}",
                    f"   - 主题句：{paragraph.topic_sentence}",
                    f"   - 覆盖必答点：{', '.join(paragraph.must_answer_point_ids) or '暂无'}",
                    "   - 核心内容：",
                    *_render_indented_bullets(paragraph.key_content, indent="     "),
                    "   - 例子/证据：",
                    *_render_indented_bullets(paragraph.evidence_or_examples, indent="     "),
                    f"   - 衔接方式：{paragraph.coherence_device}",
                    "   - 避免：",
                    *_render_indented_bullets(paragraph.avoid, indent="     "),
                    f"   - 建议字数：{paragraph.target_word_count}",
                ]
            )
        lines.extend(["", "**覆盖检查**："])
        for item in self.coverage_check:
            lines.append(f"- {item.point_id}: {item.coverage_note}（覆盖段落：{', '.join(item.covered_by) or '暂无'}）")
        lines.extend(["", "**衔接安排**：", *_render_bullets(self.transition_plan)])
        lines.extend(["", "**Rubric 对齐**："])
        for item in self.rubric_alignment:
            lines.append(f"- {item.dimension}: {item.alignment}")
        lines.extend(["", "**写作提醒**：", *_render_bullets(self.writing_tips)])
        lines.extend(["", f"**下一步建议**：{self.next_step_suggestion}"])
        return "\n".join(lines).strip()


class WritingCoachNextSectionOutput(BaseModel):
    section_role: str = Field(alias="sectionRole")
    section_goal: str = Field(alias="sectionGoal")
    target_point: str = Field(alias="targetPoint")
    draft_text: str = Field(alias="draftText")
    checks: list[str] = Field(default_factory=list)
    next_step_suggestion: str = Field(alias="nextStepSuggestion")

    model_config = {"populate_by_name": True}

    def to_markdown(self) -> str:
        return "\n".join(
            [
                "## 下一段建议",
                "",
                f"**段落角色**：{self.section_role}",
                "",
                f"**本段目标**：{self.section_goal}",
                "",
                f"**服务要点**：{self.target_point}",
                "",
                "**参考草稿**：",
                "",
                "```essay-draft",
                self.draft_text.strip(),
                "```",
                "",
                "**切题检查**：",
                *_render_bullets(self.checks),
                "",
                f"**下一步建议**：{self.next_step_suggestion}",
            ]
        ).strip()


class WritingCoachTopicRelevanceOutput(BaseModel):
    status: Literal["aligned", "risk", "off_topic"]
    coverage_summary: str = Field(alias="coverageSummary")
    missing_points: list[str] = Field(default_factory=list, alias="missingPoints")
    risk_points: list[str] = Field(default_factory=list, alias="riskPoints")
    revision_plan: list[str] = Field(default_factory=list, alias="revisionPlan")
    next_step_suggestion: str = Field(alias="nextStepSuggestion")

    model_config = {"populate_by_name": True}

    def to_markdown(self) -> str:
        status_label = {
            "aligned": "基本切题",
            "risk": "存在偏题风险",
            "off_topic": "明显偏题",
        }[self.status]
        return "\n".join(
            [
                "## 偏题检查",
                "",
                f"**判断**：{status_label}",
                "",
                f"**覆盖情况**：{self.coverage_summary}",
                "",
                "**缺失要点**：",
                *_render_bullets(self.missing_points),
                "",
                "**风险点**：",
                *_render_bullets(self.risk_points),
                "",
                "**修改方案**：",
                *_render_bullets(self.revision_plan),
                "",
                f"**下一步建议**：{self.next_step_suggestion}",
            ]
        ).strip()


class WritingCoachPolishOutput(BaseModel):
    original_text: str = Field(alias="originalText")
    polished_text: str = Field(alias="polishedText")
    changes: list[str] = Field(default_factory=list)
    preserve_meaning_notes: list[str] = Field(default_factory=list, alias="preserveMeaningNotes")
    next_step_suggestion: str = Field(alias="nextStepSuggestion")

    model_config = {"populate_by_name": True}

    def to_markdown(self) -> str:
        return "\n".join(
            [
                "## 润色建议",
                "",
                "**润色后**：",
                "",
                "```essay-draft",
                self.polished_text.strip(),
                "```",
                "",
                "**修改点**：",
                *_render_bullets(self.changes),
                "",
                "**保留原意说明**：",
                *_render_bullets(self.preserve_meaning_notes),
                "",
                f"**下一步建议**：{self.next_step_suggestion}",
            ]
        ).strip()


class WritingCoachFinalDraftOutput(BaseModel):
    draft_text: str = Field(alias="draftText")
    coverage_check: list[str] = Field(default_factory=list, alias="coverageCheck")
    word_count_guidance: str = Field(alias="wordCountGuidance")
    final_check_notes: list[str] = Field(default_factory=list, alias="finalCheckNotes")
    next_step_suggestion: str = Field(alias="nextStepSuggestion")

    model_config = {"populate_by_name": True}

    def to_markdown(self) -> str:
        return "\n".join(
            [
                "## 终稿草稿",
                "",
                "```essay-draft",
                self.draft_text.strip(),
                "```",
                "",
                "**题目覆盖检查**：",
                *_render_bullets(self.coverage_check),
                "",
                f"**字数建议**：{self.word_count_guidance}",
                "",
                "**终稿提醒**：",
                *_render_bullets(self.final_check_notes),
                "",
                f"**下一步建议**：{self.next_step_suggestion}",
            ]
        ).strip()


def _render_bullets(values: list[str]) -> list[str]:
    normalized = [value.strip() for value in values if value and value.strip()]
    if not normalized:
        return ["- 暂无"]
    return [f"- {value}" for value in normalized]


def _render_indented_bullets(values: list[str], *, indent: str) -> list[str]:
    normalized = [value.strip() for value in values if value and value.strip()]
    if not normalized:
        return [f"{indent}- 暂无"]
    return [f"{indent}- {value}" for value in normalized]
