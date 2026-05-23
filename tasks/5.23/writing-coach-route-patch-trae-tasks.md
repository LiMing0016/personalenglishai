# 写作教练路由与 Patch 应用 Trae 实现题目

## 背景

目标是把写作教练继续升级成更接近 Codex / Cursor 的协作体验：

```text
左侧聊天提出需求
  ↓
写作路由器判断：普通回答 / 进入写作阶段 / 是否需要正文修改
  ↓
写作阶段 Agent 输出结构化回复和 patches
  ↓
前端展示确认卡片
  ↓
用户确认后由 Patch 应用器修改右侧作文
```

当前项目已经具备一些基础能力：

- `web/src/components/writing/writingPatchApplicator.ts`：前端 Patch 应用器。
- `web/src/components/writing/writingCoachEditActions.ts`：从写作教练回复中提取可应用动作。
- `web/src/components/writing/EditorShell.vue`：写作页主编排，负责应用写作教练动作。
- `python/ai_orchestrator/schemas/writing_coach.py`：写作教练 schema。
- `python/ai_orchestrator/agents/writing_coach.py`：写作阶段 Agent 创建逻辑。
- `python/ai_orchestrator/agents/writing_coach_route.py`：写作教练内部路由 Agent。
- `python/ai_orchestrator/services/writing_coach_route_runner.py`：写作路由 Runner。
- `docs/agent/写作路由与Patch方案.md`：当前总体方案。

这次 Trae 任务重点不是重做已有能力，而是把“写作路由 + 结构化 patch + 前端确认应用”做完整。

## 总体要求

1. 普通问题只回复，不出现“应用到正文”卡片。
2. 修改正文类请求必须生成结构化 patch，不再依赖模型自由描述。
3. 前端只允许用户确认后应用 patch。
4. Patch 应用失败时必须明确提示，不能静默失败。
5. 不要引入新的 agent 框架，不要绕过现有 OpenAI Agents SDK。
6. 不要让模型直接修改 `draftStore.draftText`，正文修改只能由前端 Patch 应用器完成。

---

## 题 1：完善写作路由器的输入输出契约

### 小题 A：补全写作路由 Schema 和测试

#### 题目 Prompt

请完善写作教练内部路由 schema，让它能稳定判断用户当前请求是否需要进入写作阶段，以及是否需要生成可应用 patch。

要求：

1. 检查并完善 `python/ai_orchestrator/schemas/writing_coach.py` 中的 `WritingCoachRouteDecision`。
2. `routeType` 至少支持：
   - `run_stage`
   - `answer_direct`
   - `ask_clarification`
3. `targetAction` 至少支持：
   - `analyze`
   - `outline`
   - `next`
   - `topic`
   - `polish`
   - `draft`
4. `editIntent` 至少支持：
   - `none`
   - `replace_selection`
   - `insert_after_selection`
   - `append_paragraph`
   - `replace_document`
5. `contextPolicy` 需要表达第二阶段是否需要：
   - 题目
   - rubric
   - 选区
   - 正文全文
   - 近期对话
6. 使用严格 Pydantic 配置，禁止额外字段。
7. 补充单元测试，覆盖：
   - `run_stage` 必须有 `targetAction`
   - `answer_direct` 不允许带 `targetAction`
   - `ask_clarification` 必须有 `missingInputs`
   - 非法 `editIntent` 会被拒绝

#### 题目难度

中等

#### 验收标准

- `WritingCoachRouteDecision` 能 parse 合法样例。
- 非法字段、非法枚举、缺失必填约束都会报错。
- 单元测试覆盖成功路径和失败路径。
- 不影响已有 `WritingCoachTopicAnalysisOutput`、`WritingCoachOutlineOutput` 等阶段输出 schema。

### 小题 B：完善写作路由 Prompt

#### 题目 Prompt

请完善 `python/ai_orchestrator/prompts/agent_instructions/writing_coach_route.md`，让写作路由 Agent 能更稳定区分普通问答和正文修改请求。

要求：

1. 明确写作路由 Agent 只做路由，不生成正文。
2. 增加典型判断规则：
   - “这个词是什么意思 / 为什么这样写” → `answer_direct`
   - “帮我审题 / 必答点是什么” → `run_stage + analyze`
   - “搭提纲 / 每段写什么” → `run_stage + outline`
   - “下一段怎么写 / 接着写” → `run_stage + next`
   - “有没有偏题 / 漏点” → `run_stage + topic`
   - “润色这句 / 替换这句” → `run_stage + polish`
   - “生成终稿 / 完整作文” → `run_stage + draft`
3. 明确 `editIntent` 规则：
   - 有选区且要求润色/替换 → `replace_selection`
   - 有选区且要求续写/补一句 → `insert_after_selection`
   - 没有选区但要求新增段落 → `append_paragraph`
   - 只有明确“替换全文”才允许 `replace_document`
4. 不确定是否修改正文时，默认 `answer_direct` 或 `editIntent=none`。

#### 题目难度

简单

#### 验收标准

- Prompt 中包含路由边界、阶段规则、editIntent 规则。
- Prompt 明确禁止生成正文。
- Prompt 明确普通问答不输出 patch。
- 有 prompt 结构测试，确认关键规则存在。

---

## 题 2：让服务层按写作路由结果编排

### 小题 A：显式阶段按钮跳过写作路由

#### 题目 Prompt

请确保用户点击写作流程按钮时，服务层直接进入对应写作阶段 Agent，不再重复调用写作路由器。

要求：

1. 检查 `python/ai_orchestrator/assistant_service.py` 中写作教练请求链路。
2. 当 `writingCoachContext.action` 是：
   - `analyze`
   - `outline`
   - `next`
   - `topic`
   - `polish`
   - `draft`
   时，直接使用对应 `WritingCoachStageAgent`。
3. 不调用 `WritingCoachRouteRunner`。
4. 保持全局 `RouteDecisionRunner` 的原有行为不被破坏。

#### 题目难度

中等

#### 验收标准

- 单元测试证明显式 `action=outline` 不会调用 `WritingCoachRouteRunner`。
- 显式阶段请求最终进入对应阶段 Agent。
- `agentName` 和 trace metadata 显示的是对应阶段 Agent。
- 原有 `first_draft_coach` 路由行为不回退。

### 小题 B：普通 coach 请求先走写作路由

#### 题目 Prompt

请让普通写作教练消息先经过 `WritingCoachRouteRunner`，再根据路由结果决定是否进入阶段 Agent。

要求：

1. 当 `writingCoachContext.action` 是 `coach` 或为空，且 `intent=first_draft_coach` 时，调用 `WritingCoachRouteRunner`。
2. 如果路由结果是 `run_stage`，进入 `targetAction` 对应阶段 Agent。
3. 如果路由结果是 `answer_direct`，走普通回答路径，不生成应用卡片。
4. 如果路由结果是 `ask_clarification`，返回追问或普通说明，不要强行进入阶段 Agent。
5. 路由失败时要降级到普通写作教练回答，并记录日志。

#### 题目难度

困难

#### 验收标准

- 单元测试覆盖：
  - `coach + 润色选区` → 写作路由返回 `polish`，最终进入润色阶段 Agent。
  - `coach + 普通概念问题` → 不进入阶段 Agent。
  - 写作路由异常 → 不导致接口整体失败。
- 日志包含 `WRITING_COACH_ROUTE_DONE` 或等价可定位信息。
- 不影响 stream 和非 stream 两条路径。

---

## 题 3：设计结构化 WritingCoachResponse

### 小题 A：后端新增 reply + patches 输出 Schema

#### 题目 Prompt

请新增写作教练最终响应 schema，让阶段 Agent 能返回普通回复和可应用 patches。

要求：

1. 在 `python/ai_orchestrator/schemas/writing_coach.py` 中新增 `WritingCoachResponse`。
2. 字段至少包含：
   - `intent`: `answer_only | propose_edit`
   - `reply`: 展示给用户看的中文说明
   - `patches`: `WritingPatch[]`
3. 新增 `WritingPatch` schema，支持：
   - `replace_selection`
   - `search_replace`
   - `insert_after_anchor`
   - `append_paragraph`
   - `replace_document`
4. patch 字段必须和前端 `WritingPatch` 类型对齐。
5. 普通问答必须允许 `patches=[]`。
6. 结构化 schema 使用 Pydantic 严格模式。

#### 题目难度

困难

#### 验收标准

- Pydantic 测试覆盖 5 种 patch。
- 普通问题样例能返回 `intent=answer_only` 和空 patches。
- 修改问题样例能返回 `intent=propose_edit` 和至少一个 patch。
- 非法 patch op 被拒绝。
- `replace_document` 有明显 reason 字段，方便前端二次确认。

### 小题 B：阶段 Agent 输出适配

#### 题目 Prompt

请调整写作阶段 Agent，让它在需要修改正文时输出 `WritingCoachResponse`，而不是只输出 Markdown。

要求：

1. 检查 `python/ai_orchestrator/agents/writing_coach.py`。
2. 对普通问答或审题/提纲类阶段，可以输出 `intent=answer_only`，`patches=[]`。
3. 对 `polish`、`next`、`draft` 等可能修改正文的阶段，输出 `intent=propose_edit` 和 patches。
4. 如果当前 action 已有专用阶段 schema，不能破坏已有审题/提纲结构化输出。
5. 如果第一版难以统一所有阶段，优先只对 `polish` 和 `next` 使用 `WritingCoachResponse`，并在文档中写清边界。

#### 题目难度

困难

#### 验收标准

- `polish` 有选区时能返回 `replace_selection` patch。
- `next` 有选区时能返回 `insert_after_anchor` patch。
- `next` 无选区时能返回 `append_paragraph` patch。
- `analyze` 和 `outline` 原有结构化输出不被破坏。
- 单元测试覆盖阶段 Agent output_type 选择。

---

## 题 4：前端消费结构化 patches

### 小题 A：接口层和类型层对齐

#### 题目 Prompt

请让前端 assistant 接口能消费后端返回的结构化 `WritingCoachResponse`。

要求：

1. 检查 `web/src/api/assistant.ts` 和 `web/src/types/assistantRequest.ts`。
2. 定义或复用 `WritingPatch`、`WritingCoachResponse` 类型。
3. 保持旧字段 `reply`、`lastChatResult.displayText` 兼容。
4. 如果后端暂时只返回纯文本，前端仍能正常展示。
5. 如果后端返回 patches，前端能保存到写作教练面板状态。

#### 题目难度

中等

#### 验收标准

- TypeScript 类型不使用 `any`。
- `npm run build` 通过。
- 纯文本回复仍正常展示。
- 带 patches 的回复能被前端识别。
- 不破坏现有 `assistantChatStream`。

### 小题 B：替换 Markdown 猜测式 action 提取

#### 题目 Prompt

请逐步减少前端从 Markdown 代码块猜 action 的逻辑，优先使用后端返回的结构化 patches。

要求：

1. 检查 `web/src/components/writing/writingCoachEditActions.ts`。
2. 如果响应里有结构化 patches，直接转成 `WritingCoachEditAction`。
3. 如果没有结构化 patches，再走旧的 Markdown 代码块提取逻辑作为兼容。
4. 不要删除旧逻辑，避免影响当前模型输出。
5. 给转换逻辑补纯函数测试。

#### 题目难度

中等

#### 验收标准

- 有 patches 时不再依赖 Markdown 代码块。
- 无 patches 时旧逻辑仍可工作。
- 5 种 patch 都能转成 UI 可展示 action。
- 单元测试覆盖结构化 patches 优先级。

---

## 题 5：Patch 确认卡片和失败反馈

### 小题 A：升级确认卡片展示

#### 题目 Prompt

请升级写作教练面板里的“应用到正文”卡片，让用户能清楚看到 patch 会做什么。

要求：

1. 检查 `web/src/components/writing/panels/WritingCoachPanel.vue`。
2. 根据 patch op 展示不同文案：
   - `replace_selection`：替换选区
   - `search_replace`：替换匹配文本
   - `insert_after_anchor`：插入到指定句后
   - `append_paragraph`：追加新段落
   - `replace_document`：替换全文
3. 卡片展示：
   - 修改原因
   - 原文或锚点
   - 新文
   - 应用按钮
   - 取消按钮
   - 复制按钮
4. `replace_document` 必须用更明显的风险提示。
5. UI 保持浅色风格，不要使用大面积黑色代码块。

#### 题目难度

中等

#### 验收标准

- 用户能看懂每个 patch 要修改哪里。
- `replace_document` 有明确覆盖全文提示。
- 长文本不会撑破面板。
- 复制按钮能复制新文。
- 没有 patch 时不显示卡片。

### 小题 B：完善 Patch 应用失败提示

#### 题目 Prompt

请完善前端 Patch 应用失败时的用户反馈。

要求：

1. 检查 `web/src/components/writing/writingPatchApplicator.ts` 和 `EditorShell.vue`。
2. `not_found` 时提示：无法定位原文，请重新选择目标句子。
3. `ambiguous` 时提示：原文出现多次，请先选择要修改的位置。
4. `duplicate` 时提示：相似内容已经存在，未重复插入。
5. 失败时不要关闭确认卡片，方便用户调整选区后再应用。
6. 成功后才清除确认卡片。

#### 题目难度

中等

#### 验收标准

- 三类失败状态都有明确 toast 或卡片提示。
- 失败时正文不变。
- 失败时 action card 仍然保留。
- 成功时正文更新，卡片消失。
- 单元测试覆盖 `not_found`、`ambiguous`、`duplicate`。

---

## 题 6：上下文按需加载

### 小题 A：根据 contextPolicy 组装写作上下文

#### 题目 Prompt

请根据 `WritingCoachRouteDecision.contextPolicy` 精简第二阶段写作 Agent 的输入上下文。

要求：

1. 检查 `web/src/components/writing/EditorShell.vue` 中 `buildWritingCoachContext` 和 `buildWritingCoachPrompt`。
2. 或检查 Python 侧 `build_assistant_input_items` 的写作上下文构建逻辑。
3. 根据 `contextPolicy` 决定是否传入：
   - 题目
   - rubric
   - 选区
   - 作文全文
   - 近期对话
4. 不要每次都传入全文作文。
5. 如果缺少目标阶段必需上下文，应该追问用户，而不是让模型猜。

#### 题目难度

困难

#### 验收标准

- 润色选区时不默认传全文。
- 偏题检查和终稿阶段会传全文。
- 审题和提纲阶段会传题目和 rubric。
- 缺题目时审题阶段能提示补充题目。
- 有测试或日志能看出最终上下文字段。

### 小题 B：保护 token 和隐私边界

#### 题目 Prompt

请为写作教练上下文添加长度控制和敏感信息保护。

要求：

1. 对作文全文、近期对话、rubric 文本做长度上限。
2. 保留现有 `truncateForAssistant` 或等价能力。
3. trace metadata 不要记录完整作文全文。
4. 日志只记录布尔状态和长度，不记录完整正文。
5. 如果上下文被截断，需要在输入中注明“已截断”。

#### 题目难度

中等

#### 验收标准

- 长作文不会无限传给模型。
- 日志里看不到完整作文正文。
- trace metadata 只含 `hasDraftText`、`draftLength` 等摘要字段。
- 截断后模型仍知道输入不是完整文本。
- 不影响短文本正常使用。

---

## 题 7：端到端验收与回归

### 小题 A：自动化测试

#### 题目 Prompt

请为写作路由与 Patch 应用补自动化测试。

要求：

1. Python 测试覆盖：
   - `WritingCoachRouteDecision`
   - `WritingCoachRouteRunner`
   - 显式阶段跳过写作路由
   - 普通 coach 请求进入写作路由
2. 前端测试覆盖：
   - `replace_selection`
   - `search_replace`
   - `insert_after_anchor`
   - `append_paragraph`
   - `replace_document`
   - `not_found`
   - `ambiguous`
   - `duplicate`
3. 不能只依赖手工测试。

#### 题目难度

困难

#### 验收标准

- Python 定向测试通过。
- 前端 Patch 纯函数测试通过。
- `npm run build` 通过。
- 测试覆盖成功路径和失败路径。
- 新测试命名能直接看出业务场景。

### 小题 B：手工验收清单和文档同步

#### 题目 Prompt

请补充文档并完成手工验收，确认写作教练的 Codex/Cursor 式编辑体验可用。

要求：

1. 更新 `docs/agent/写作路由与Patch方案.md`。
2. 文档说明：
   - 写作路由器职责
   - 阶段 Agent 职责
   - Patch 应用器职责
   - 普通回答和正文修改的区别
   - 失败状态如何处理
3. 手工验收至少覆盖：
   - 问普通问题，不出现应用卡片。
   - 选中一句，让模型润色并替换。
   - 选中一句，让模型在后面补一句。
   - 不选中，让模型追加新段落。
   - 原文出现多次时不自动修改。
   - 替换全文时出现二次确认。

#### 题目难度

中等

#### 验收标准

- 文档和代码字段一致。
- 手工验收清单全部记录结果。
- 失败路径不会改正文。
- 用户确认前不会自动写入作文。
- 交付说明包含已运行命令和未覆盖风险。

## 推荐验证命令

```bash
python -m unittest discover python/ai_orchestrator/tests -p "test_writing_coach*.py"
```

```bash
python -m unittest python.ai_orchestrator.tests.test_assistant_service.AssistantAgentServiceTest.test_run_assistant_request_prefers_structured_writing_coach_stage_agent python.ai_orchestrator.tests.test_assistant_service.AssistantAgentServiceTest.test_run_assistant_request_uses_writing_coach_route_for_generic_coach_action python.ai_orchestrator.tests.test_assistant_service.AssistantAgentServiceTest.test_run_assistant_request_skips_writing_coach_route_for_explicit_stage_action
```

```bash
cd web
npx tsx --test src/components/writing/writingPatchApplicator.test.ts src/components/writing/writingCoachEditActions.test.ts
npm run build
```

## 最终交付要求

完成后请给出：

1. 修改文件列表。
2. 写作路由 schema 示例。
3. `WritingCoachResponse` 和 `WritingPatch` 示例。
4. 前端 action card 截图或说明。
5. 已运行测试命令和结果。
6. 尚未覆盖的风险，例如结构化输出尚未完全替代 Markdown 提取。
