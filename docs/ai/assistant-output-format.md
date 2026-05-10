# 学习助手 Markdown 主题与英语输出规范方案

## 1. 背景

当前学习助手已经支持 Markdown 渲染，但输出仍偏普通 Agent 文本。用户希望 AI 回复更像英语学习助手，而不是普通聊天机器人。

典型问题：

- 对比类问题没有稳定输出表格。
- 原句、解释、例句、练习之间视觉层级不够清晰。
- AI 回复内容可以阅读，但不够像“英语老师整理好的讲义”。
- 回复需要等完整生成后才稳定呈现，缺少 ChatGPT 类似的边生成边阅读体验。
- 前端必须支持 GFM table，否则 AI 即使输出 Markdown 表格，也无法渲染成真正表格。
- 需要支持两种 Markdown 视觉风格，由用户自行选择：
  - `MarkText 风格`
  - `Milkdown 风格`

本方案不引入 MarkText / Milkdown 框架本身，只借鉴它们的视觉方向，通过前端 CSS 主题和后端 Prompt 输出规范实现。

## 2. 核心结论

图一没有表格，不是 Markdown 渲染器的问题，而是 Agent 输出格式没有被约束。

需要分两层解决：

1. **Prompt 层**：让 AI 在合适场景稳定输出 Markdown 表格、引用、列表和代码块。
2. **前端样式层**：让这些 Markdown 元素显示得更像英语学习内容。
3. **流式渲染层**：让 AI 输出可以边生成边稳定渲染 Markdown，避免表格、代码块、列表在流式过程中抖动或破版。

也就是说：

```text
没有表格 = Prompt / Agent 输出格式问题
表格不好看 = Markdown CSS 主题问题
流式显示抖动 = Streaming Markdown 渲染问题
```

## 3. 目标

### 3.1 产品目标

让学习助手回复从“普通 AI 回答”升级为“英语学习讲义”。

用户应该能明显感受到：

- 内容结构更稳定。
- 对比类答案优先表格。
- 讲解类答案有清晰模块。
- 润色/批改类答案更像英语老师反馈。
- 输出视觉更适合学习和复习。
- 回复可以边生成边阅读，长答案不需要等全部完成。
- 流式过程中 Markdown 表格、代码块、引用块尽量稳定，不出现明显闪烁或错乱。

### 3.2 技术目标

- 不引入完整 MarkText / Milkdown 编辑器。
- 不更换现有 Markdown parser。
- 通过 CSS class 支持两套主题。
- 通过 localStorage 保存用户选择。
- 通过 Prompt 约束 Agent 输出 Markdown 结构。
- Markdown renderer 必须支持 GitHub Flavored Markdown table。
- 支持 P0/P1 流式事件协议，前端可消费 `message.delta` 增量。
- 流式渲染需要兼容不完整 Markdown，例如未闭合代码块、未完成表格行。

## 4. 两种 Markdown 视觉风格

## 4.1 MarkText 风格

定位：

```text
清爽阅读模式
```

适合：

- 长解释
- 翻译说明
- 作文反馈
- 语法讲解
- 长段落阅读

视觉特点：

- 段落留白舒适。
- 标题层级清晰但不过度装饰。
- 引用块轻量，像原文摘录。
- 表格干净、文档感强。
- 代码块适合作为最终可复制文本。

可对应 UI label：

```text
MarkText 风格
```

后续可改为：

```text
阅读模式
```

## 4.2 Milkdown 风格

定位：

```text
学习卡片模式
```

适合：

- 词汇辨析
- 句子改错
- 练习拆解
- 图片内容讲解
- 原句/修改后/原因对比

视觉特点：

- Markdown block 更像学习卡片。
- `blockquote` 像原句卡片。
- `table` 像对比卡片。
- `ul/ol` 像知识点拆解卡片。
- `code block` 像最终答案卡片。
- 模块之间边界更明确。

可对应 UI label：

```text
Milkdown 风格
```

后续可改为：

```text
卡片模式
```

## 5. 前端设计

### 5.1 类型定义

建议新增类型：

```ts
export type AssistantMarkdownTheme = 'marktext' | 'milkdown'
```

默认值：

```ts
'marktext'
```

原因：

- 默认阅读负担低。
- 长文本回复更稳。
- 不会一开始让页面显得过度卡片化。

### 5.2 本地持久化

localStorage key：

```text
peai:assistant:markdown-theme
```

保存值：

```text
marktext
milkdown
```

### 5.3 UI 入口

建议放在学习助手页面设置里，例如：

```text
自定义 / 设置
- 输出样式：MarkText 风格 / Milkdown 风格
```

或者在助手页面右上角放轻量 segmented control：

```text
MarkText | Milkdown
```

第一版建议放在已有“自定义”入口中，避免主界面增加视觉负担。

### 5.4 渲染 class

助手 Markdown 回复容器增加主题 class：

```html
<div class="assistant-markdown assistant-markdown--marktext">
  ...
</div>
```

或：

```html
<div class="assistant-markdown assistant-markdown--milkdown">
  ...
</div>
```

### 5.5 样式重点

需要覆盖以下 Markdown 元素：

```text
h2 / h3
p
strong
ul / ol
blockquote
table
thead / th / td
code
pre
hr
a
```

### 5.6 GFM table 支持

前端 Markdown renderer 必须支持 GFM table。

示例输入：

```md
| 词 | 核心含义 | 重点 |
|---|---|---|
| important | 有价值、需要重视 | 主观/实际重要性 |
| significant | 程度大、意义明显 | 客观影响/统计意义 |
```

预期渲染：

```text
真实 HTML table，而不是普通文本。
```

如果当前使用 `marked`：

```ts
marked.setOptions({
  gfm: true,
  breaks: true,
})
```

如果当前使用 `markdown-it`：

```ts
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})
```

并确认 table rule 没有被禁用。

如果未来切换到 remark 体系：

```text
必须启用 remark-gfm。
```

验收：

- Markdown table 渲染为 `<table>`。
- 表头渲染为 `<thead>` 或至少有 `<th>`。
- 表格行渲染为 `<tr>`。
- 单元格渲染为 `<td>`。
- 不允许整段表格以纯文本方式显示。

### 5.7 表格移动端处理

表格必须支持横向滚动：

```css
.assistant-markdown table {
  display: block;
  overflow-x: auto;
  max-width: 100%;
}
```

不能在小屏幕压缩到不可读。

## 6. 流式输出设计

### 6.1 为什么要加流式输出

英语助手经常会生成较长内容，例如：

- 词汇对比表格。
- 作文批改。
- 图片内容识别和翻译。
- 语法讲解。
- 练习题和答案解析。

如果等完整回复生成后再显示，用户会感觉卡顿。流式输出可以让用户边看边等，体验更接近 ChatGPT。

### 6.2 流式输出要解决的问题

普通文本流式比较简单，但 Markdown 流式会有额外问题：

```text
表格可能只生成了一半
代码块可能还没闭合
列表项可能正在生成中
blockquote 可能跨多行
```

所以不能只把 delta 字符串直接拼到 DOM。需要：

- 维护完整的累计 Markdown 文本。
- 每次 delta 到达后重新安全渲染或增量渲染。
- 对不完整 Markdown 做容错。
- 对代码块、表格、数学公式、Mermaid 等复杂块延迟或安全渲染。

### 6.3 事件协议

沿用 P0 已定义的事件类型：

```text
run.started
handoff
message.created
message.delta
message.completed
run.completed
run.failed
```

前端第一版至少需要消费：

```text
run.started
message.created
message.delta
message.completed
run.completed
run.failed
```

`handoff` 可以先作为调试信息，不一定显示给用户。

### 6.4 推荐响应流程

```text
用户发送消息
-> 前端创建用户消息和 assistant loading message
-> 后端建立 run
-> Python 发送 run.started
-> Python 发送 message.created
-> Python 持续发送 message.delta
-> 前端累计 delta 并渲染 Markdown
-> Python 发送 message.completed
-> 前端用 completed content 做最终渲染
-> Python 发送 run.completed
-> 前端保存 run metadata
```

### 6.5 前端渲染策略

建议第一版采用：

```text
累计全文 markdown
每次 delta 更新当前 assistant message.content
通过现有 Markdown renderer 重新渲染
```

原因：

- 实现简单。
- 和现有消息结构兼容。
- 不需要一开始引入复杂增量 AST。

需要注意：

- 对流式中未闭合的代码块，渲染器需要容错。
- 表格行未完成时可以先按普通文本显示，完成后再变成表格。
- GFM table 在 `message.completed` 后必须渲染为真实 table。
- `message.completed` 到达后做一次最终渲染。

### 6.6 开源方案评估

可参考的开源方案：

| 方案 | 作用 | 建议 |
|---|---|---|
| `github-markdown-css` | 基础 Markdown 阅读样式 | 适合作为 MarkText 风格基础 |
| `streamdown-vue` | Vue/Nuxt 流式 Markdown 渲染 | 值得评估，适合 AI 输出场景 |
| `vue-markdown-render` | Vue 3 AI streaming Markdown，支持 Mermaid/KaTeX/Shiki | 值得评估，功能较完整 |
| `vue-markdown-shiki` | Vue 3 + markdown-it + Shiki | 适合代码高亮和复制能力 |

第一版建议：

```text
先不引入大型编辑器。
先用现有 Markdown renderer + CSS 主题完成视觉升级。
流式输出如果现有 renderer 抖动明显，再评估 streamdown-vue 或 vue-markdown-render。
```

### 6.7 流式输出验收标准

- 发送普通文本问题后，助手回复逐步出现，而不是等待完整生成。
- `message.delta` 到达时，页面持续更新同一条 assistant message。
- `message.completed` 到达后，最终内容和非流式返回一致。
- Markdown 标题、列表、引用、代码块最终渲染正确。
- 对比类问题生成表格时，最终表格渲染正确。
- 流式生成 GFM table 时，完成后必须渲染为真实 `<table>`。
- 流式过程中页面不大幅跳动。
- 用户可以在生成中看到停止或 loading 状态。
- 失败时显示 `run.failed` 对应错误，不留下无限 loading。
- 复制按钮复制最终完整内容。
- 重试按钮仍能重试上一条用户消息。

## 7. Prompt 输出规范

前端主题只能优化“已经生成的 Markdown”。要让图一变成图二，必须让 Agent 稳定输出 Markdown 表格。

### 7.1 通用规则

所有英语助手回复应遵守：

- 优先使用清晰 Markdown 结构。
- 不输出大段无标题长文本。
- 英语表达、语法结构、关键词用 `**加粗**` 或 ``inline code`` 标记。
- 原句或识别内容用 `>` 引用块。
- 最终可复制版本可用 `text` 代码块。
- 对比、批改、翻译差异优先使用表格。
- 结尾可以给一个小练习或下一步建议。

### 7.2 对比类问题

触发条件：

```text
对比
区别
差异
compare
difference
vs
A 和 B 有什么不同
```

输出结构：

```md
### 快速对比

| 词 | 核心含义 | 重点 | 常见中文 |
|---|---|---|---|
| important | ... | ... | ... |
| significant | ... | ... | ... |

### 1. important

...

### 2. significant

...

### 怎么选

- ...
```

验收示例：

用户输入：

```text
对比一下 important 和 significant 的区别
```

预期：

- 回复开头必须有 Markdown 表格。
- 表格至少包含两行：`important`、`significant`。
- 表格后有分点解释和例句。

### 7.3 讲解类问题

适用于：

```text
explain
语法解释
词汇解释
选中文本询问 AI
```

输出结构：

```md
### 这句话的意思

> 原句

中文解释。

### 重点表达

- **表达 1**：解释
- `结构`：用法

### 例句

> Example sentence.

中文解释。

### 你来练一下

...
```

### 7.4 翻译类问题

输出结构：

```md
### 推荐译文

```text
...
```

### 关键词

| 英文 | 含义 | 翻译处理 |
|---|---|---|
| ... | ... | ... |

### 更自然的表达

- ...
```

如果是图片翻译：

```md
### 我识别到的内容

> ...

### 翻译

...
```

### 7.5 润色类问题

输出结构：

```md
### 修改后

```text
...
```

### 修改说明

| 原表达 | 修改后 | 原因 |
|---|---|---|
| ... | ... | ... |

### 可复用表达

- ...
```

### 7.6 批改/评分类问题

输出结构：

```md
### 总体反馈

...

### 主要问题

| 问题 | 例子 | 修改建议 |
|---|---|---|
| ... | ... | ... |

### 优化版本

```text
...
```

### 下一步练习

...
```

## 8. 推荐实施拆分

### 题目 1：学习助手 Markdown 输出主题切换

目标：

实现 `MarkText 风格 / Milkdown 风格` 两套前端 Markdown CSS 主题。

建议修改：

```text
web/src/components/assistant/AssistantChatView.vue
web/src/pages/app/AssistantPage.vue
web/src/pages/app/assistantMarkdownTheme.ts
```

验收：

- 用户可以切换 MarkText / Milkdown 风格。
- 切换后历史回复和新回复立即变化。
- 刷新后保留选择。
- 前端 Markdown renderer 支持 GFM table。
- 表格、引用、代码块、列表、标题都有明显样式差异。
- 移动端表格不溢出。
- 不引入 MarkText / Milkdown 依赖。

### 题目 2：学习助手流式输出接入

目标：

让学习助手回复支持边生成边显示，并保持 Markdown 最终渲染正确。

建议修改：

```text
python/ai_orchestrator/app.py
python/ai_orchestrator/assistant_service.py
backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java
backend/src/main/java/com/personalenglishai/backend/service/assistant/PythonAssistantClient.java
web/src/api/assistant.ts
web/src/pages/app/assistantState.ts
web/src/components/assistant/AssistantChatView.vue
```

验收：

- 后端或 Python 能输出标准事件：`run.started`、`message.delta`、`message.completed`、`run.completed`、`run.failed`。
- 前端发送后，同一条 assistant message 逐步追加内容。
- 最终 Markdown 表格、引用、代码块渲染正确。
- 流式失败时显示错误并结束 loading。
- 复制和重试在流式完成后仍可用。
- 如果浏览器或服务不支持流式，保留非流式 fallback。

### 题目 3：英语助手 Prompt 输出规范

目标：

让 Agent 按 intent 和用户问题类型稳定输出英语学习结构。

建议修改：

```text
python/ai_orchestrator/prompts
python/ai_orchestrator/agents
python/ai_orchestrator/tests
```

验收：

- 对比类问题优先输出 Markdown 表格。
- 讲解类问题包含原句、重点表达、例句、小练习。
- 翻译类问题包含推荐译文、关键词、自然表达。
- 润色类问题包含修改后、修改说明表格、可复用表达。
- 图片翻译先说明识别到的内容，再翻译。

## 9. 不做事项

第一版不做：

- 不引入完整 MarkText 应用。
- 不引入完整 Milkdown 编辑器。
- 不做 Markdown 编辑器。
- 不做富文本所见即所得输入框。
- 不改变后端消息存储结构。
- 不改变 AI 返回内容的数据库格式。
- 不要求第一版支持复杂流式 Mermaid 渲染。
- 不要求第一版支持复杂流式数学公式渲染。
- 不要求第一版实现可编辑 Markdown block。

## 10. 最终效果

完成后：

- 图一这种纯段落输出，可以通过 Prompt 变成带表格的英语学习讲解。
- 图二这种表格输出，可以通过主题 CSS 显示得更像学习产品。
- 长回复可以边生成边阅读。
- 流式完成后，表格、代码块、引用块呈现为最终稳定样式。
- 用户可以自行选择：
  - `MarkText 风格`：像清爽英语讲义。
  - `Milkdown 风格`：像英语学习卡片。

核心收益：

```text
Prompt 负责让内容结构正确。
Markdown 主题负责让内容视觉专业。
Streaming 负责让等待过程更自然。
```

## 11. 当前实现状态

已在 `codex/assistant-markdown-streaming` 分支完成第一轮实现：

- 前端 Markdown renderer 支持 GFM table，并补充表格单测。
- 学习助手页面支持 `MarkText` / `Milkdown` 输出风格切换，使用 `peai:assistant:markdown-theme` 持久化。
- Python orchestrator 新增 `/assistant/run/stream` SSE 入口，基于 OpenAI Agents SDK `Runner.run_streamed()` 输出 `message.delta`。
- Java 后端新增 `/api/assistant/conversations/{conversationUid}/messages/run/stream` 代理入口，负责透传 SSE，并在 `message.completed` 后保存助手完整回复。
- 前端 API 与 `assistantState` 支持消费流式事件，持续更新同一条 assistant loading message。
- Agent 指令统一追加 Markdown 学习讲义输出规范，约束对比、讲解、翻译、润色、批改类回复结构。

已验证：

- 前端 Markdown / stream parser / theme helper 单测通过。
- 前端 `npm run build` 通过。
- Python streaming endpoint、service、runner、prompt 相关单测通过。
- 后端 `AssistantControllerTest` 通过。

已知边界：

- 附件上传消息第一版仍走非流式 fallback。
- `handoff` 事件暂不在前端展示。
- 复杂 Mermaid、数学公式等流式 Markdown 渲染不在第一版范围内。
