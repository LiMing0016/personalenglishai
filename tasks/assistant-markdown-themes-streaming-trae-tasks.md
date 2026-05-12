# 学习助手 Markdown 主题、流式输出与英语格式任务拆分

本文把 `docs/assistant/assistant-markdown-themes-and-output-format.md` 的方案拆成适合 Trae 分步实现的题目。

## 总体目标

把学习助手回复从普通 Agent 文本升级成更像英语学习助手的输出体验：

- 支持 `MarkText 风格` 和 `Milkdown 风格` 两套 Markdown 视觉主题。
- 前端 Markdown renderer 必须支持 GFM table。
- 对比类、润色类、翻译类、讲解类回复稳定输出 Markdown 结构。
- 学习助手支持流式输出，长回复可以边生成边阅读。
- 流式完成后，表格、引用、代码块等 Markdown 元素渲染稳定。

## 全局约束

- 不引入完整 MarkText 应用。
- 不引入完整 Milkdown 编辑器。
- 不做 Markdown 编辑器。
- 不做富文本所见即所得输入框。
- 不改变 AI 回复内容的数据库格式。
- 优先复用现有 Markdown renderer。
- 第一版可以只做文本、表格、引用、代码块、列表的稳定流式渲染，不要求复杂 Mermaid/数学公式流式渲染。
- 如果引入开源依赖，需要优先选择轻量的 Markdown 渲染或样式依赖，不引入大型编辑器框架。

---

## 题目 1：确认并补齐 GFM table 渲染能力

### 目标

确保学习助手前端能把 AI 返回的 GFM Markdown table 渲染为真实 HTML table，而不是普通文本。

### 建议修改范围

- `web/src/components/assistant/AssistantChatView.vue`
- 当前 Markdown renderer 所在文件
- 可新增测试文件，例如：
  - `web/src/components/assistant/assistantMarkdownRenderer.test.ts`

### Prompt

```text
请为学习助手 Markdown 回复渲染补齐 GFM table 支持。

背景：
- 学习助手回复渲染在 web/src/components/assistant/AssistantChatView.vue 或其相关 Markdown helper 中。
- 用户希望对比类英语问题可以显示真正的表格。
- 示例 Markdown：
  | 词 | 核心含义 | 重点 |
  |---|---|---|
  | important | 有价值、需要重视 | 主观/实际重要性 |
  | significant | 程度大、意义明显 | 客观影响/统计意义 |

要求：
1. 确认当前 Markdown renderer 支持 GFM table。
2. 如果当前 renderer 是 marked，启用 gfm: true。
3. 如果当前 renderer 是 markdown-it，确认 table rule 没有被禁用。
4. 如果当前 renderer 不支持 GFM table，补齐支持能力。
5. AI 返回的 Markdown table 必须渲染成真实 <table>。
6. 表头、表格行、单元格语义正确，至少要出现 th/tr/td。
7. 不引入 MarkText / Milkdown 编辑器框架。
8. 不改变 AI 返回内容，只改变前端渲染能力。

请补充最小测试，验证 GFM table 能渲染为 table。
```

### 验收方案

- 输入 GFM table Markdown 后，渲染结果包含 `<table>`。
- 表头包含 `<th>`。
- 表格行包含 `<tr>`。
- 单元格包含 `<td>`。
- 表格不以纯文本方式显示。
- 普通段落、列表、加粗、引用仍正常渲染。
- `npm run build` 通过。

---

## 题目 2：实现 MarkText / Milkdown 两套 Markdown 输出主题

### 目标

在学习助手回复区支持两套视觉主题：

- `MarkText 风格`：清爽阅读，适合长解释。
- `Milkdown 风格`：学习卡片，适合知识点和对比。

### 建议修改范围

- `web/src/components/assistant/AssistantChatView.vue`
- `web/src/pages/app/AssistantPage.vue`
- 可新增：
  - `web/src/pages/app/assistantMarkdownTheme.ts`
  - `web/src/components/assistant/assistantMarkdownTheme.css`

### Prompt

```text
请为学习助手 Markdown 回复实现 MarkText 风格和 Milkdown 风格两套视觉主题。

背景：
- 学习助手回复内容是 Markdown。
- 用户希望先保留 MarkText / Milkdown 这两个名称，后续再产品化改名。
- 不能引入完整 MarkText 或 Milkdown 编辑器。

要求：
1. 定义类型：
   type AssistantMarkdownTheme = 'marktext' | 'milkdown'
2. 默认主题为 marktext。
3. 使用 localStorage 保存用户选择：
   peai:assistant:markdown-theme
4. 页面提供切换入口，文案为：
   - MarkText 风格
   - Milkdown 风格
5. 切换后，历史消息和新消息的 Markdown 样式立即变化。
6. MarkText 风格要求：
   - 阅读感强
   - 标题清晰
   - 段落舒适
   - 表格简洁
   - 引用块轻量
7. Milkdown 风格要求：
   - blockquote 像原句卡片
   - table 像对比卡片
   - pre/code 像最终答案卡片
   - ul/ol 更像知识点拆解
8. 不改变 AI 返回内容。
9. 不新增大型编辑器依赖。

请保持现有页面风格，不要重做整个助手页面。
```

### 验收方案

- 页面能切换 `MarkText 风格` / `Milkdown 风格`。
- 刷新页面后保留上次选择。
- 同一条历史回复切换主题后样式立即变化。
- `h2/h3/p/strong/ul/ol/blockquote/table/pre/code/hr/a` 都有基础样式。
- 两套主题视觉差异明显。
- 移动端没有文字重叠或内容溢出。
- `npm run build` 通过。

---

## 题目 3：优化 Markdown table 样式与移动端横向滚动

### 目标

让英语对比表、润色对比表、批改表在两套主题下都可读、好看，并在移动端不溢出。

### 建议修改范围

- `web/src/components/assistant/AssistantChatView.vue`
- Markdown CSS 主题文件

### Prompt

```text
请优化学习助手 Markdown table 的视觉样式和移动端适配。

背景：
- 学习助手会大量使用 Markdown table 展示词汇对比、润色修改说明、作文批改。
- 表格需要同时支持 MarkText 风格和 Milkdown 风格。

要求：
1. 表格宽度不超过回复容器。
2. 小屏幕下表格可以横向滚动。
3. 表头视觉上明显。
4. 单元格 padding、行高适合中英混排。
5. 长英文短语不撑破布局。
6. MarkText 风格表格更像干净文档表格。
7. Milkdown 风格表格更像学习对比卡片。
8. 表格内 strong、code、链接样式正常。
9. 不改变 Markdown 内容。

请用 CSS 实现，不要为了表格引入大型 UI 表格组件。
```

### 验收方案

- 对比 `important/significant` 的 Markdown table 显示正常。
- 桌面端表格列宽自然，阅读舒适。
- 移动端表格可横向滚动，不挤压到不可读。
- MarkText 风格和 Milkdown 风格下表格样式不同。
- 表格内中英文混排不溢出。
- `npm run build` 通过。

---

## 题目 4：学习助手 Prompt 输出规范：对比、讲解、翻译、润色、批改

### 目标

让 AI 不只是“能渲染 Markdown”，而是稳定生成适合英语学习的 Markdown 结构。

### 建议修改范围

- `python/ai_orchestrator/prompts`
- `python/ai_orchestrator/agents`
- `python/ai_orchestrator/tests`
- 可能涉及：
  - specialist agent instructions
  - router / assistant prompt
  - attachment agent prompt

### Prompt

```text
请升级学习助手 Agent 的输出格式规范，让回复更像英语学习助手。

背景：
- 前端将支持 GFM table 和两套 Markdown 主题。
- 但如果 AI 不输出 Markdown table，前端无法凭空生成表格。
- 用户希望像 “important 和 significant 区别” 这类问题优先展示对比表。

要求：
1. 通用规则：
   - 优先使用清晰 Markdown 结构。
   - 不输出大段无标题长文本。
   - 英语表达、语法结构、关键词用 **加粗** 或 inline code 标记。
   - 原句或识别内容用 blockquote。
   - 最终可复制版本可用 text code block。
2. 对比类问题：
   - 触发词包括：对比、区别、差异、compare、difference、vs。
   - 回复开头优先给 Markdown table。
   - 表格后再分点解释和给例句。
3. 讲解类问题：
   - 包含：这句话的意思、重点表达、例句、你来练一下。
4. 翻译类问题：
   - 包含：推荐译文、关键词、更自然的表达。
   - 图片翻译必须先说明识别到的内容，再翻译。
5. 润色类问题：
   - 包含：修改后、修改说明表格、可复用表达。
6. 批改/评分类问题：
   - 包含：总体反馈、主要问题表格、优化版本、下一步练习。
7. 不要让格式模板变成空洞固定话术，内容仍要根据用户输入具体生成。
8. 补充 prompt 或 agent 指令相关测试，至少验证关键指令存在。

请优先改 prompt/instruction，不要改前端。
```

### 验收方案

- 问 `对比一下 important 和 significant 的区别`，回复开头包含 Markdown table。
- 表格至少有两行：`important`、`significant`。
- 表格后有分点解释和例句。
- 讲解类问题包含原句、重点表达、例句、小练习。
- 翻译类问题包含推荐译文和关键词。
- 润色类问题包含修改说明表格。
- 图片翻译先输出识别内容，再给翻译。
- Python 相关测试通过。

---

## 题目 5：定义并接入学习助手流式事件 API

### 目标

让学习助手后端和 Python orchestrator 支持标准流式事件，前端可以边生成边显示。

### 建议修改范围

- `python/ai_orchestrator/app.py`
- `python/ai_orchestrator/assistant_service.py`
- `python/ai_orchestrator/schemas/chat.py`
- `backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java`
- `backend/src/main/java/com/personalenglishai/backend/service/assistant/PythonAssistantClient.java`
- `web/src/api/assistant.ts`

### Prompt

```text
请为学习助手接入 P1 流式事件 API。

背景：
- P0 已定义流式事件协议骨架：
  run.started
  handoff
  message.created
  message.delta
  message.completed
  run.completed
  run.failed
- 当前页面仍以非流式返回为主。

要求：
1. Python 提供流式 run 能力，输出标准事件。
2. Java 后端透传或转换 Python 流式事件。
3. 前端 API 层提供 stream 方法。
4. 第一版至少支持：
   - run.started
   - message.created
   - message.delta
   - message.completed
   - run.completed
   - run.failed
5. handoff 可以先保留为调试事件，不一定展示给用户。
6. 如果流式不可用，保留非流式 fallback。
7. 错误事件必须结束前端 loading。
8. 不影响现有非流式 /messages/run。

请补充必要的事件序列化测试。
```

### 验收方案

- 发送消息后，Network 能看到流式响应。
- 前端能收到 `message.delta`。
- `run.failed` 时页面停止 loading 并显示错误。
- 非流式接口仍可用。
- 后端和 Python 相关测试通过。

---

## 题目 6：前端流式 Markdown 渲染与消息状态

### 目标

让学习助手页面在收到 `message.delta` 时持续更新同一条 assistant message，并保持最终 Markdown 渲染正确。

### 建议修改范围

- `web/src/api/assistant.ts`
- `web/src/pages/app/assistantState.ts`
- `web/src/components/assistant/AssistantChatView.vue`
- 可新增：
  - `web/src/pages/app/assistantStream.ts`
  - `web/src/pages/app/assistantStream.test.ts`

### Prompt

```text
请为学习助手前端实现流式 Markdown 回复渲染。

背景：
- 后端会发送 run.started、message.delta、message.completed 等事件。
- 前端当前是等完整回复后一次性展示。
- 回复内容是 Markdown，可能包含表格、引用、代码块和列表。

要求：
1. 发送消息后创建一条 assistant loading message。
2. 收到 message.delta 时，把 delta 追加到同一条 assistant message.content。
3. 页面实时渲染累计 Markdown。
4. 收到 message.completed 时，用 completed content 做最终覆盖。
5. 收到 run.completed 时保存 run metadata。
6. 收到 run.failed 时结束 loading 并显示错误。
7. 流式过程中复制按钮可以隐藏或禁用，完成后可用。
8. 重试按钮完成后仍可用。
9. GFM table 在最终完成后必须渲染为真实 table。
10. 如果流式请求失败，fallback 到非流式请求或显示明确错误。

请保持现有消息结构兼容。
```

### 验收方案

- 普通文本问题回复会逐步出现。
- 同一条 assistant message 被持续更新，不产生多条碎片消息。
- 完成后 Markdown 标题、列表、引用、代码块渲染正确。
- 对比类问题最终表格渲染为 table。
- 流式失败时不留下无限 loading。
- 复制按钮复制最终完整内容。
- 重试按钮仍能重试上一条用户消息。
- `npm run build` 通过。

---

## 题目 7：端到端验收与文档更新

### 目标

验证 Markdown 主题、GFM table、Prompt 输出规范、流式输出能一起工作，并把最终边界写入文档。

### 建议修改范围

- `docs/assistant/assistant-markdown-themes-and-output-format.md`
- `docs/assistant/openai-agents-p0-acceptance-report.md`
- 可选新增验收记录文件

### Prompt

```text
请对学习助手 Markdown 主题、GFM table、英语输出规范和流式输出做端到端验收，并更新文档。

要求：
1. 验收 MarkText / Milkdown 主题切换。
2. 验收 GFM table 渲染。
3. 验收对比类 prompt 是否稳定输出表格。
4. 验收流式输出是否逐步更新同一条助手消息。
5. 验收流式完成后的 Markdown 最终渲染。
6. 验收失败和 fallback。
7. 更新文档记录实际结果和未完成边界。

请不要引入无关重构。
```

### 验收方案

- 自动化测试通过：
  - 前端 Markdown/GFM table 测试
  - 前端流式状态测试
  - Python prompt/instruction 测试
  - 后端流式事件测试
- 浏览器手工通过：
  - `important/significant` 对比显示表格
  - 切换 MarkText / Milkdown 后表格视觉变化
  - 长回复逐步输出
  - 流式完成后复制完整内容
  - 流式失败时显示错误
- 文档记录实际验证结果。

---

## 推荐执行顺序

1. 题目 1：确认并补齐 GFM table 渲染能力
2. 题目 2：实现 MarkText / Milkdown 两套 Markdown 输出主题
3. 题目 3：优化 Markdown table 样式与移动端横向滚动
4. 题目 4：学习助手 Prompt 输出规范
5. 题目 5：定义并接入学习助手流式事件 API
6. 题目 6：前端流式 Markdown 渲染与消息状态
7. 题目 7：端到端验收与文档更新

## 分支建议

建议使用独立分支：

```text
codex/assistant-markdown-streaming
```

如果拆 PR：

- PR 1：GFM table + Markdown 主题
- PR 2：Prompt 输出规范
- PR 3：流式事件 API
- PR 4：前端流式渲染和端到端验收
