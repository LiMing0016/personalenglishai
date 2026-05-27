---
title: 写作 Agent 工具
status: draft
owner: ai
last_updated: 2026-05-21
related_docs:
  - docs/agent/写作教练Agent设计.md
---

# 写作 Agent 工具

本文档保存写作教练 Agent 使用的 Function tools。每个 JSON 代码块都是 **Agent Builder 的 Function Definition 可直接复制版本**。

注意：

- 复制时复制完整 JSON，不要只复制 `parameters`。
- Agent Builder 的 Function 弹窗需要顶层包含 `name`、`description`、`parameters`、`strict`。
- 这些工具由 `writing_coach_agent` 编排，不代表要拆成多个 Agent。

## 1. analyze_writing_task

用途：审题理解，生成中心任务、必答点、偏题风险、推荐结构和评分关注点。

```json
{
  "name": "analyze_writing_task",
  "description": "分析英语作文题目，生成中心任务、必答点、偏题风险、推荐结构和评分关注点。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "inputAsText": { "type": "string", "description": "用户本轮原始输入，例如“帮我审题”。" },
      "studyStage": { "type": "string", "description": "用户学段，例如小学、初中、高中、大学四六级、雅思、托福。" },
      "writingMode": { "type": "string", "enum": ["free", "exam"], "description": "写作模式。" },
      "taskPrompt": { "type": "string", "description": "聚合后的作文题目与写作要求。" },
      "essayQuestion": { "type": "string", "description": "考试题目原文。自由写作或无原题时为空字符串。" },
      "questionMaterials": { "type": "string", "description": "题目材料、提示语、图表描述、续写材料或附加要求。" },
      "taskType": { "type": "string", "enum": ["unknown", "advantages_disadvantages", "opinion", "discussion", "problem_solution", "cause_effect", "compare_contrast", "letter_request", "letter_advice", "letter_apology", "chart_summary", "picture_description", "story_continuation", "application", "custom"], "description": "作文任务类型。" },
      "examType": { "type": "string", "enum": ["none", "primary", "middle_school", "high_school", "zhongkao", "gaokao", "cet4", "cet6", "postgraduate_exam", "ielts", "toefl", "custom"], "description": "考试类型。" },
      "genre": { "type": "string", "enum": ["unknown", "argumentative", "expository", "narrative", "descriptive", "letter", "email", "notice", "announcement", "speech", "proposal", "report", "chart_description", "picture_description", "continuation_writing", "application", "review", "custom"], "description": "文体或任务形式。" },
      "minWords": { "type": "integer", "description": "最小字数要求。没有时为 0。" },
      "maxWords": { "type": "integer", "description": "最大字数或推荐上限。没有时为 0。" },
      "rubricKey": { "type": "string", "description": "评分标准标识。没有时为空字符串。" },
      "draftText": { "type": "string", "description": "当前作文正文。刚开始写作时为空字符串。" }
    },
    "required": ["inputAsText", "studyStage", "writingMode", "taskPrompt", "essayQuestion", "questionMaterials", "taskType", "examType", "genre", "minWords", "maxWords", "rubricKey", "draftText"]
  },
  "strict": true
}
```

## 2. generate_writing_ideas

用途：立意构思，生成 2 到 3 个切题思路。

```json
{
  "name": "generate_writing_ideas",
  "description": "基于题目中心任务生成 2 到 3 个切题立意，并说明优缺点和适用场景。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "taskPrompt": { "type": "string", "description": "作文题目原文或写作要求。" },
      "centralTask": { "type": "string", "description": "题目中心任务。" },
      "mustAnswerPoints": { "type": "array", "items": { "type": "string" }, "description": "必须覆盖的要点。" },
      "offTopicRisks": { "type": "array", "items": { "type": "string" }, "description": "已知偏题风险。" },
      "studyStage": { "type": "string", "description": "用户学段。" },
      "writingMode": { "type": "string", "enum": ["free", "exam"], "description": "写作模式。" },
      "genre": { "type": "string", "enum": ["unknown", "argumentative", "expository", "narrative", "descriptive", "letter", "email", "notice", "announcement", "speech", "proposal", "report", "chart_description", "picture_description", "continuation_writing", "application", "review", "custom"], "description": "文体或任务形式。" },
      "ideaPreference": { "type": "string", "description": "用户对立意的偏好。没有时为空字符串。" }
    },
    "required": ["taskPrompt", "centralTask", "mustAnswerPoints", "offTopicRisks", "studyStage", "writingMode", "genre", "ideaPreference"]
  },
  "strict": true
}
```

## 3. activate_writing_materials

用途：素材激活，生成理由、例子、表达素材和可用句式。

```json
{
  "name": "activate_writing_materials",
  "description": "根据题目、立意和学段生成切题理由、例子、表达素材和可用句式。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "centralTask": { "type": "string", "description": "题目中心任务。" },
      "mustAnswerPoints": { "type": "array", "items": { "type": "string" }, "description": "必须覆盖的要点。" },
      "selectedIdea": { "type": "string", "description": "用户已选择或当前倾向的立意。没有时为空字符串。" },
      "studyStage": { "type": "string", "description": "用户学段。" },
      "genre": { "type": "string", "enum": ["unknown", "argumentative", "expository", "narrative", "descriptive", "letter", "email", "notice", "announcement", "speech", "proposal", "report", "chart_description", "picture_description", "continuation_writing", "application", "review", "custom"], "description": "文体或任务形式。" },
      "materialNeed": { "type": "string", "description": "素材需求，例如理由、例子、连接词、论证素材。没有时为空字符串。" }
    },
    "required": ["centralTask", "mustAnswerPoints", "selectedIdea", "studyStage", "genre", "materialNeed"]
  },
  "strict": true
}
```

## 4. build_writing_outline

用途：组织提纲，明确每段目标和要点覆盖。

```json
{
  "name": "build_writing_outline",
  "description": "根据题目中心任务、必答点、立意和文体生成作文提纲，明确每段目标和要点覆盖。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "taskPrompt": { "type": "string", "description": "作文题目原文或写作要求。" },
      "centralTask": { "type": "string", "description": "题目中心任务。" },
      "mustAnswerPoints": { "type": "array", "items": { "type": "string" }, "description": "必须覆盖的要点。" },
      "selectedIdea": { "type": "string", "description": "用户确认或当前推荐的立意。没有时为空字符串。" },
      "genre": { "type": "string", "enum": ["unknown", "argumentative", "expository", "narrative", "descriptive", "letter", "email", "notice", "announcement", "speech", "proposal", "report", "chart_description", "picture_description", "continuation_writing", "application", "review", "custom"], "description": "文体或任务形式。" },
      "minWords": { "type": "integer", "description": "最小字数。" },
      "maxWords": { "type": "integer", "description": "最大字数或推荐上限。" },
      "studyStage": { "type": "string", "description": "用户学段。" }
    },
    "required": ["taskPrompt", "centralTask", "mustAnswerPoints", "selectedIdea", "genre", "minWords", "maxWords", "studyStage"]
  },
  "strict": true
}
```

## 5. draft_writing_section

用途：起草开头、主体段、结尾、下一段或完整草稿片段。

```json
{
  "name": "draft_writing_section",
  "description": "根据题目、提纲和当前正文起草指定段落或完整草稿片段，并返回需要用户确认的写入建议。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "taskPrompt": { "type": "string", "description": "作文题目原文或写作要求。" },
      "centralTask": { "type": "string", "description": "题目中心任务。" },
      "mustAnswerPoints": { "type": "array", "items": { "type": "string" }, "description": "必须覆盖的要点。" },
      "outline": { "type": "array", "items": { "type": "string" }, "description": "当前提纲。没有时为空数组。" },
      "draftText": { "type": "string", "description": "当前作文正文。" },
      "sectionType": { "type": "string", "enum": ["opening", "body", "conclusion", "next_paragraph", "full_draft"], "description": "本次起草范围。" },
      "currentParagraphIndex": { "type": "integer", "description": "当前段落序号。未知时为 0。" },
      "studyStage": { "type": "string", "description": "用户学段。" },
      "genre": { "type": "string", "enum": ["unknown", "argumentative", "expository", "narrative", "descriptive", "letter", "email", "notice", "announcement", "speech", "proposal", "report", "chart_description", "picture_description", "continuation_writing", "application", "review", "custom"], "description": "文体或任务形式。" }
    },
    "required": ["taskPrompt", "centralTask", "mustAnswerPoints", "outline", "draftText", "sectionType", "currentParagraphIndex", "studyStage", "genre"]
  },
  "strict": true
}
```

## 6. revise_writing_structure

用途：修改结构、调整逻辑、补要点或删除无关内容。

```json
{
  "name": "revise_writing_structure",
  "description": "检查并重构作文内容、段落逻辑和要点覆盖，输出结构修改建议和可确认的正文编辑方案。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "taskPrompt": { "type": "string", "description": "作文题目原文或写作要求。" },
      "centralTask": { "type": "string", "description": "题目中心任务。" },
      "mustAnswerPoints": { "type": "array", "items": { "type": "string" }, "description": "必须覆盖的要点。" },
      "offTopicRisks": { "type": "array", "items": { "type": "string" }, "description": "偏题风险。" },
      "draftText": { "type": "string", "description": "当前作文正文。" },
      "selectedText": { "type": "string", "description": "用户选中的正文片段。没有选区时为空字符串。" },
      "revisionGoal": { "type": "string", "description": "修改目标，例如补要点、调结构、删无关内容、加强逻辑。没有时为空字符串。" },
      "studyStage": { "type": "string", "description": "用户学段。" },
      "genre": { "type": "string", "enum": ["unknown", "argumentative", "expository", "narrative", "descriptive", "letter", "email", "notice", "announcement", "speech", "proposal", "report", "chart_description", "picture_description", "continuation_writing", "application", "review", "custom"], "description": "文体或任务形式。" }
    },
    "required": ["taskPrompt", "centralTask", "mustAnswerPoints", "offTopicRisks", "draftText", "selectedText", "revisionGoal", "studyStage", "genre"]
  },
  "strict": true
}
```

## 7. polish_writing_language

用途：润色选中表达，不改变原意和题目方向。

```json
{
  "name": "polish_writing_language",
  "description": "在不改变原意和题目方向的前提下，润色用户选中的英文表达，并返回可确认的替换建议。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "textToPolish": { "type": "string", "description": "需要润色的文本。通常来自 selectedText。" },
      "taskPrompt": { "type": "string", "description": "作文题目原文或写作要求。" },
      "centralTask": { "type": "string", "description": "题目中心任务。" },
      "mustAnswerPoints": { "type": "array", "items": { "type": "string" }, "description": "必须覆盖的要点。" },
      "studyStage": { "type": "string", "description": "用户学段。" },
      "genre": { "type": "string", "enum": ["unknown", "argumentative", "expository", "narrative", "descriptive", "letter", "email", "notice", "announcement", "speech", "proposal", "report", "chart_description", "picture_description", "continuation_writing", "application", "review", "custom"], "description": "文体或任务形式。" },
      "polishLevel": { "type": "string", "enum": ["light", "standard", "advanced"], "description": "润色强度。" },
      "preserveMeaning": { "type": "boolean", "description": "是否必须保持原意。应为 true。" }
    },
    "required": ["textToPolish", "taskPrompt", "centralTask", "mustAnswerPoints", "studyStage", "genre", "polishLevel", "preserveMeaning"]
  },
  "strict": true
}
```

## 8. check_final_draft

用途：终稿自查，检查切题、要点覆盖、结构完整性、字数和 rubric 风险。

```json
{
  "name": "check_final_draft",
  "description": "对完整作文进行终稿自查，检查切题、必答点覆盖、结构完整性、字数和 rubric 风险，但不产生正式评分。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "taskPrompt": { "type": "string", "description": "作文题目原文或写作要求。" },
      "centralTask": { "type": "string", "description": "题目中心任务。" },
      "mustAnswerPoints": { "type": "array", "items": { "type": "string" }, "description": "必须覆盖的要点。" },
      "offTopicRisks": { "type": "array", "items": { "type": "string" }, "description": "偏题风险。" },
      "rubricFocus": { "type": "array", "items": { "type": "string" }, "description": "评分关注点。" },
      "draftText": { "type": "string", "description": "完整作文正文。" },
      "minWords": { "type": "integer", "description": "最小字数。" },
      "maxWords": { "type": "integer", "description": "最大字数或推荐上限。" },
      "studyStage": { "type": "string", "description": "用户学段。" },
      "writingMode": { "type": "string", "enum": ["free", "exam"], "description": "写作模式。" },
      "rubricKey": { "type": "string", "description": "评分标准标识。" }
    },
    "required": ["taskPrompt", "centralTask", "mustAnswerPoints", "offTopicRisks", "rubricFocus", "draftText", "minWords", "maxWords", "studyStage", "writingMode", "rubricKey"]
  },
  "strict": true
}
```

## 9. check_topic_relevance

用途：偏题检查，判断立意、提纲、段落或完整草稿是否紧扣题目。

```json
{
  "name": "check_topic_relevance",
  "description": "检查用户的立意、提纲、段落或完整草稿是否紧扣作文题目，返回偏题状态、缺失要点和修改建议。",
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "taskPrompt": { "type": "string", "description": "作文题目原文或写作要求。" },
      "centralTask": { "type": "string", "description": "题目中心任务。" },
      "mustAnswerPoints": { "type": "array", "items": { "type": "string" }, "description": "必须覆盖的要点。" },
      "offTopicRisks": { "type": "array", "items": { "type": "string" }, "description": "已知偏题风险。" },
      "textToCheck": { "type": "string", "description": "需要检查的文本，可以是立意、提纲、段落或完整草稿。" },
      "checkScope": { "type": "string", "enum": ["idea", "outline", "paragraph", "full_draft"], "description": "检查范围。" },
      "studyStage": { "type": "string", "description": "用户学段。" },
      "writingMode": { "type": "string", "enum": ["free", "exam"], "description": "写作模式。" },
      "rubricKey": { "type": "string", "description": "评分标准标识。没有时为空字符串。" }
    },
    "required": ["taskPrompt", "centralTask", "mustAnswerPoints", "offTopicRisks", "textToCheck", "checkScope", "studyStage", "writingMode", "rubricKey"]
  },
  "strict": true
}
```
