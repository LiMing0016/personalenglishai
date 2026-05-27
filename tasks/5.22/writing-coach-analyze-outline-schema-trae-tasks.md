# 写作教练审题与提纲稳定 Schema 输出 Trae 实现题目

## 背景

当前写作教练已经有 6 个阶段入口：

- `审题`
- `提纲`
- `下一段`
- `偏题检查`
- `润色`
- `终稿`

本轮先只做 `审题` 和 `提纲` 两个阶段，目标是让模型输出稳定结构，而不是每次返回风格不同的自然语言。

核心思路：

- 前端点击 `审题` 时，后端使用审题专用 schema 输出。
- 前端点击 `提纲` 时，后端使用提纲专用 schema 输出。
- 审题结果里的必答点、中心任务、题型判断，要能被提纲阶段复用。
- 用户题目、题目材料、图片描述、当前学段 rubric，都要作为输入上下文传给模型。

关联文档：

- `docs/agent/写作教练Schema设计.md`
- `docs/agent/写作教练Agent设计.md`
- `docs/agent/StructuredOutput学习笔记.md`
- `docs/agent/FunctionCall学习笔记.md`

关联代码：

- `python/ai_orchestrator/schemas/writing_coach.py`
- `python/ai_orchestrator/schemas/assistant_request.py`
- `python/ai_orchestrator/adapters/openai_input_items.py`
- `python/ai_orchestrator/prompts/agent_instructions/writing_coach_stage.md`
- `web/src/components/writing/EditorShell.vue`
- `web/src/components/writing/panels/WritingCoachPanel.vue`
- `web/src/types/assistantRequest.ts`

## 目标

把写作教练的 `审题` 和 `提纲` 做成可稳定验证的结构化工作流：

1. `审题` 输出固定 schema，包含中心任务、必答点、约束、偏题风险、评分关注点。
2. `提纲` 输出固定 schema，包含中心论点、段落计划、覆盖检查、衔接计划、评分对齐。
3. `提纲` 必须复用 `审题` 的结构化结果，尤其是 `mustAnswerPointIds`。
4. 前端按钮点击后不再只靠 prompt 文本区分阶段，而是让请求上下文和后端 schema 明确区分阶段。
5. 出错时前端要能显示可理解错误，不能只出现 `出错了。请重试。`。

## 非目标

本题单不做以下内容：

- 不实现 `下一段`、`偏题检查`、`润色`、`终稿` 的完整 schema。
- 不改造整个 Agent Builder。
- 不做云端持久化工作流。
- 不新增图表库、富文本编辑器或大范围 UI 重构。

## 推荐执行顺序

```text
题 1：确认审题和提纲 schema
  ↓
题 2：补齐写作教练 input context
  ↓
题 3：让审题阶段稳定输出结构化结果
  ↓
题 4：让提纲阶段复用审题结果
  ↓
题 5：前端按钮接入与错误态优化
  ↓
题 6：测试、文档和真实场景验收
```

## 难度说明

| 难度 | 含义 |
| --- | --- |
| 简单 | 只改类型、文档或局部单测，影响范围小 |
| 中等 | 涉及前后端或 prompt/schema 联动，需要补测试 |
| 困难 | 涉及真实模型链路、状态复用、端到端验证，容易出现边界问题 |

---

## 题 1：确认审题和提纲 Pydantic Schema

### 题目难度

中等（完成）

### 题目标准

这道题的重点不是“字段越多越好”，而是让 `审题` 和 `提纲` 两个阶段的输出结构稳定、可验证、可被后续阶段复用。schema 要适合 OpenAI Structured Outputs：字段明确、类型明确、必填项清楚、枚举值稳定，并且禁止模型随意输出额外字段。

### 给 Trae 的 Prompt

请检查并完善写作教练 `审题` 和 `提纲` 的 Pydantic 输出 schema。

要求：

1. schema 文件优先放在 `python/ai_orchestrator/schemas/writing_coach.py`。
2. 每个输出 schema 都要继承统一的严格基类，禁止模型输出未定义字段。
3. 审题输出至少包含：
   - `schemaVersion`
   - `stage`
   - `topicBrief`
   - `centralTask`
   - `taskType`
   - `genre`
   - `stanceRequirement`
   - `mustAnswerPoints`
   - `taskConstraints`
   - `offTopicRisks`
   - `recommendedStructure`
   - `rubricFocus`
   - `missingInfo`
   - `confidence`
   - `nextStepSuggestion`
4. 提纲输出至少包含：
   - `schemaVersion`
   - `stage`
   - `basedOnAnalysis`
   - `controllingIdea`
   - `outlineMode`
   - `paragraphPlan`
   - `coverageCheck`
   - `transitionPlan`
   - `rubricAlignment`
   - `writingTips`
   - `nextStepSuggestion`
5. 提纲里的段落计划必须能引用审题必答点，例如 `mustAnswerPointIds: ["P1", "P2"]`。

### 验收标准

- Pydantic schema 能 parse 正常样例。
- 多余字段会被拒绝。
- `stage` 使用固定值，例如 `analyze` / `outline`。
- `schemaVersion` 使用固定值，便于后续升级。
- 单元测试覆盖正常解析和额外字段拒绝。

### 建议测试

```bash
python -m pytest python/ai_orchestrator/tests/test_writing_coach_schemas.py -q
```

---

## 题 2：补齐写作教练 Input Schema（完成）

### 题目难度

中等

### 题目标准

这道题的重点是把“模型需要知道什么”设计清楚。审题不能只收到用户一句话，还要收到题目、题目材料、图片描述、考试模式、题型、字数、rubric、当前草稿和选中文本。输入结构要前后端一致，不能前端传一套字段、后端解析另一套字段。

### 给 Trae 的 Prompt

请为写作教练请求补齐输入上下文 schema，让模型审题时能拿到完整材料。

写作教练 input context 至少包含：

- `schemaVersion`：输入协议版本。
- `stage`：当前阶段，例如 `analyze` 或 `outline`。
- `examMode`：考试模式，例如 IELTS、TOEFL、校内写作。
- `taskType`：题型，例如 task1、task2、discussion、argument。
- `wordLimit`：目标字数。
- `studyStage`：当前学段或学习阶段。
- `topicText`：题目文本。
- `sourceMaterials`：题目材料，例如阅读材料、图表说明、背景文本。
- `imageDescriptions`：图片或图表的文字描述。
- `draftText`：当前草稿。
- `selectedText`：用户当前选中的文本。
- `rubric`：当前学段评分标准。
- `previousStageOutput`：上一阶段结构化输出。

要求：

1. 前端 TypeScript 类型和后端 Pydantic 类型字段要对齐。
2. 图片暂时可以先传 `imageDescriptions`，不要求本题实现 OCR。
3. rubric 允许为空，但字段结构必须稳定。
4. 空字段不要传成混乱的自然语言，要用数组、对象或空字符串表达。

### 验收标准

- `web/src/types/assistantRequest.ts` 有清晰类型定义。
- `python/ai_orchestrator/schemas/assistant_request.py` 有对应 schema。
- `openai_input_items` 能把 input context 转成模型可读的结构化上下文。
- 单测能验证题目、图片描述、rubric 被写入模型输入。

### 建议测试

```bash
python -m pytest python/ai_orchestrator/tests/test_assistant_request_input_items.py -q
```

---

## 题 3：实现审题阶段稳定输出（完成）

### 题目难度

困难

### 题目标准

这道题的重点是让 `审题` 成为一个稳定阶段，而不是普通聊天回复。点击 `审题` 后，模型必须按审题 schema 输出；输出内容要能明确告诉学生：这道题到底问什么、必须回答哪些点、有哪些限制、哪里容易跑题、评分标准重点看什么。

### 给 Trae 的 Prompt

请实现写作教练 `审题` 阶段的稳定结构化输出。

要求：

1. 用户点击 `审题` 后，请求中必须明确携带 `stage=analyze`。
2. 后端运行写作教练时，审题阶段必须使用 `WritingCoachTopicAnalysisOutput` 作为输出 schema。
3. prompt 要明确要求模型基于：
   - 题目文本
   - 题目材料
   - 图片描述
   - 考试模式
   - 题型
   - 字数要求
   - 当前学段 rubric
4. 审题输出里的 `mustAnswerPoints` 要有稳定 `pointId`，例如 `P1`、`P2`。
5. 审题结果要能渲染成用户可读的中文说明。

### 验收标准

- 点击 `审题` 后，返回内容包含中心任务、必答点、约束、偏题风险。
- 模型原始结构能通过 Pydantic parse。
- markdown 渲染结果里能看到 `P1`、`P2` 等必答点编号。
- 没有题目时，返回明确错误或缺失信息提示，不要伪造题目。
- 前端不出现空白回复或通用错误 toast。

### 建议测试

```bash
python -m pytest python/ai_orchestrator/tests/test_writing_coach_stage_agent.py -q
```

---

## 题 4：实现提纲阶段复用审题结果

### 题目难度（完成）

困难

### 题目标准

这道题的重点是“阶段之间有状态”。提纲不是重新审题，而是基于上一阶段审题结果继续规划文章。尤其要保证审题里的 `P1/P2/P3` 等必答点能被提纲段落引用，后续才可以继续做下一段、偏题检查和终稿检查。

### 给 Trae 的 Prompt

请实现写作教练 `提纲` 阶段，让它复用上一轮审题结构化结果。

要求：

1. 用户点击 `提纲` 后，请求中必须明确携带 `stage=outline`。
2. 如果当前会话已有审题结果，提纲阶段要把审题结果作为 `previousStageOutput` 传给模型。
3. 提纲输出必须引用审题的 `mustAnswerPointIds`。
4. 提纲不能重新发散审题，应该围绕审题结果组织段落。
5. 如果没有审题结果，提纲阶段可以先基于题目生成，但要在 `basedOnAnalysis` 中标明没有可复用审题结果。

### 验收标准

- 先点 `审题` 再点 `提纲`，提纲段落能引用 `P1`、`P2`。
- `coverageCheck` 能说明每个必答点是否被覆盖。
- `paragraphPlan` 至少包含每段目标、主题句、关键内容、例子建议、衔接方式。
- 提纲输出能通过 Pydantic parse。
- 如果审题结果不存在，提纲不崩溃，并明确说明降级行为。

### 建议测试

```bash
python -m pytest python/ai_orchestrator/tests/test_writing_coach_schemas.py python/ai_orchestrator/tests/test_writing_coach_stage_agent.py -q
```

---

## 题 5：前端按钮接入与错误态优化

### 题目难度（完成）

中等

### 题目标准

这道题的重点是用户真实点击可用。前端按钮不能只是换一段自然语言 prompt，而要明确告诉后端当前阶段。请求失败时，要能定位是缺少题目、rubric 降级、schema 解析失败还是模型调用失败，不能只弹一个笼统错误。

### 给 Trae 的 Prompt

请检查写作教练前端按钮与后端阶段 schema 的接入。

要求：

1. `审题` 按钮点击后发送 `stage=analyze`。
2. `提纲` 按钮点击后发送 `stage=outline`。
3. 请求中带上当前题目、草稿、选中文本、图片描述、rubric。
4. 前端保存最近一次审题结构化结果，供提纲阶段复用。
5. 后端错误要尽量显示具体原因，例如：
   - 缺少题目
   - rubric 加载失败但已降级
   - schema parse 失败
   - 模型调用失败
6. 不要让用户只看到 `出错了。请重试。`。

### 验收标准

- 点击 `审题` 可以拿到回复。
- 点击 `提纲` 可以拿到回复。
- 浏览器控制台没有明显请求构造错误。
- 网络请求 payload 中能看到写作教练 context。
- 前端错误提示对用户和开发者都有定位价值。

### 建议测试

```bash
cd web
npm run build
```

手工验收：

1. 打开写作页面。
2. 输入一条 IELTS Task 2 题目。
3. 点击 `审题`。
4. 确认输出包含中心任务和必答点。
5. 点击 `提纲`。
6. 确认提纲引用审题必答点编号。

---

## 题 6：端到端验收和文档同步

### 题目难度

困难

### 题目标准

这道题的重点是确认“代码真的跑通”。不仅要跑单元测试和 build，还要用真实 IELTS 题目从前端点击 `审题`、再点击 `提纲`，检查请求 payload、模型输出、结构化解析、前端渲染和错误态是否完整闭环。

### 给 Trae 的 Prompt

请完成审题和提纲两个阶段的端到端验收，并同步必要文档。

要求：

1. 使用至少 1 个 IELTS Task 2 题目做真实模型 smoke test。
2. 如果项目已有本地后端和前端启动方式，按项目标准启动。
3. 记录实际请求是否带上：
   - 题目
   - 题目材料
   - 图片描述
   - rubric
   - 上一阶段审题输出
4. 如果实现字段和 `docs/agent/写作教练Schema设计.md` 不一致，要同步文档。
5. 不要删除或覆盖无关改动。

### 验收标准

- Python 相关测试通过。
- 前端 build 通过。
- 手工点击 `审题` 和 `提纲` 成功。
- 文档和代码字段一致。
- 说明是否还缺少图片 OCR、rubric 自动匹配、持久化状态等后续能力。

### 建议验证命令

```bash
python -m pytest python/ai_orchestrator/tests/test_writing_coach_schemas.py python/ai_orchestrator/tests/test_writing_coach_stage_agent.py python/ai_orchestrator/tests/test_assistant_request_input_items.py -q
```

```bash
cd web
npm run build
```

## 最终交付

完成后请给出：

- 修改文件列表。
- 审题输出样例。
- 提纲输出样例。
- 已运行的测试命令和结果。
- 尚未覆盖的风险点。
