# 写作教练 Markdown 渲染修复 Trae 实现题目

## 背景

写作教练返回内容里经常包含 Markdown，例如：

- 标题：`### 审题分析`、`#### 核心观点`
- 列表：`- 必答点`
- 表格：`| 项目 | 内容 |`
- 代码块：`fenced code block`
- 行内强调：`**task response**`
- 模型输出的安全换行：`<br>`

之前写作教练面板没有完整复用通用 Assistant 的 Markdown 渲染逻辑，导致部分内容直接以原始 Markdown 文本显示，例如：

- `#### 核心观点` 没有变成标题。
- `fenced code block` 直接显示反引号。
- 表格按纯文本显示。
- `<br>` 没有按换行处理。

## 目标

让写作教练的 AI 回复像 Codex / ChatGPT 一样正确渲染 Markdown，同时保持学习产品的视觉克制。

## 非目标

- 不引入新的 Markdown 第三方库。
- 不重写写作页主链路。
- 不改变后端返回结构。
- 不把写作教练代码块做成黑色开发者终端风格。

## 题目难度

中等

## 题目标准

这道题的标准是“用户看不到未解析的 Markdown 语法”。写作教练返回的标题、列表、表格、代码块、行内加粗、行内代码和安全 `<br>` 都应该被渲染成可读 UI。

渲染器必须继续做 HTML 转义，不能为了支持 `<br>` 直接信任模型输出的任意 HTML。

## 给 Trae 的 Prompt

请修复写作教练面板的 Markdown 渲染问题。

要求：

1. 写作教练面板复用项目已有的共享 Markdown 渲染器，不要再维护一套简陋本地 `renderMarkdown`。
2. 共享 Markdown 渲染器至少支持：
   - `#` 到 `######` 标题。
   - 无序列表。
   - 有序列表。
   - GFM 风格表格。
   - fenced code block，例如 `text` 语言代码块。
   - 行内粗体 `**text**`。
   - 行内代码 `` `code` ``。
   - 安全 `<br>` 换行。
3. fenced code block 要渲染成独立块，不要把三反引号显示给用户。
4. 写作教练里的代码块视觉要轻，适合学习场景：
   - 浅色背景。
   - 清晰边框。
   - 不使用大面积黑色背景。
   - 示例英文句子用普通可读字体，不强制等宽字体。
5. 通用 Assistant 如果也使用共享渲染器，需要避免新结构没有样式。
6. 补充单元测试覆盖：
   - 表格。
   - 非表格 `|` 文本。
   - `<br>`。
   - fenced code block。
   - h4-h6 标题。

## 验收标准

- 写作教练回复中的 `#### 核心观点` 渲染成标题，不显示 `####`。
- 写作教练回复中的 fenced code block 渲染成浅色代码块，不显示三反引号。
- 表格渲染成真实 table。
- `<br>` 渲染成换行。
- 模型输出的普通 `<script>` 或未知 HTML 不会被当成 HTML 执行。
- `npx tsx --test web/src/components/assistant/markdown.test.ts` 通过。
- `cd web && npm run build` 通过。

## 建议测试

```bash
npx tsx --test web/src/components/assistant/markdown.test.ts
```

```bash
cd web
npm run build
```

## 手工验收样例

把下面内容作为助手回复样例渲染：

````md
### 审题分析

#### 核心观点

College Chinese can help students:

- write more clearly
- express ideas more precisely

```text
I believe College Chinese should be made a compulsory course because it can improve students' language skills.
```

| 项目 | 内容 |
|---|---|
| 中心任务 | 判断 College Chinese 是否应该成为必修课 |
````

期望：

- `审题分析` 和 `核心观点` 都是标题。
- 列表正常缩进。
- 英文句子在浅色代码块里显示。
- 表格是边框表格。
