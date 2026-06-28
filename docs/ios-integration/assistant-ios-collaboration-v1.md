---
title: AI 对话助手 iOS 协同开发契约 v1
status: draft
owner: backend
last_updated: 2026-06-23
review_cycle: on-change
related_code:
  - web/src/pages/app/AssistantPage.vue
  - web/src/pages/app/assistantState.ts
  - web/src/api/assistant.ts
  - backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java
  - backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java
  - python/ai_orchestrator/assistant_service.py
related_docs:
  - docs/ios-integration/ai-assistant-api-contract.md
  - docs/ios-integration/integration-checklist.md
  - docs/agent/Agent能力清单.md
  - docs/agent/学习助手Agent编排架构.md
---

# AI 对话助手 iOS 协同开发契约 v1

## 当前结论

本文只定义独立 AI 对话助手的 iOS 协同开发方式，对应 Web 当前页面 `/app/assistant`。本契约不包含写作页 Copilot、翻译页、作文编辑器内联修改、其他业务页的 Agent 能力。

iOS 端不直接连接 Python Orchestrator 或 OpenAI。正式链路固定为：

```text
iOS Assistant
  -> Java Backend /api/assistant/**
  -> Python ai_orchestrator
  -> OpenAI Agents / Responses
```

第一阶段目标不是复制一个通用 GPT 聊天框，而是把 `/app/assistant` 做成 PEAI 的英语学习对话助手。iOS 端需要通过结构化 `AssistantRequest` 明确学习任务、上下文和客户端来源；后端负责会话、鉴权、持久化、SSE、错误码和 Agent 调度。

## 范围边界

### 本文覆盖

- AI 助手首页和对话页。
- 会话创建、列表、详情、重命名、删除、置顶、归档、文件夹移动和分享。
- 文本消息的结构化 Agent run。
- SSE 流式输出。
- 英语学习任务入口：解释、翻译、润色、总结、作文评分、例句、题目分析。
- iOS 与 Java / Python 的字段协同、错误处理和验收标准。

### 本文不覆盖

- 写作页右侧 Writing Coach Composer。
- 作文编辑器选区替换、插入下一段、Safe Apply。
- 翻译页文档翻译工作台。
- ChatKit / Agent Builder 实验链路。
- 直接从 iOS 调 OpenAI。

## 产品原则

独立 AI 对话助手允许自由输入，但不能被设计成通用问答工具。iOS 端和后端都应遵守以下边界：

1. 默认服务英语学习任务：词汇、语法、句子结构、翻译、润色、写作、评分、练习、学习规划和能力画像。
2. 对明显非英语学习请求，助手应简短收口，并引导用户改成英语学习问题。
3. iOS 首屏应提供学习任务入口，而不是只给一个空输入框。
4. iOS 发送请求时应优先带明确 `intent`，减少无约束 `free_chat`。
5. 后端仍需要保留范围外兜底，因为 iOS 客户端提示不能替代服务端边界。

## P0 开发闭环

P0 目标是 iOS 可以完整打开 AI 助手、发送英语学习问题、实时看到回复，并恢复历史会话。

| 能力 | iOS 工作 | 后端工作 | 验收 |
| --- | --- | --- | --- |
| 创建会话 | 首次进入或点击新建时调用创建接口 | Java 创建 `assistant_conversation` | 返回 `ConversationDetail`，iOS 进入空对话 |
| 会话列表 | 拉取未归档会话和归档会话 | Java 返回当前用户会话 | 列表顺序、置顶、归档状态正确 |
| 会话详情 | 进入会话时拉取消息 | Java 返回 `messages` | 历史消息可恢复 |
| 文本 Agent run | 构造 `AssistantRequest` | Java 代理到 Python | 返回 user/assistant 消息 |
| 流式输出 | 消费 SSE 事件并拼接 delta | Java 透传 Python stream event | 可边生成边阅读 |
| 失败重试 | 保留输入和最后一次请求 | Java 返回统一错误或 `run.failed` | 用户可重试 |
| 英语学习边界 | 用任务入口和 intent 引导 | Python/Route Agent 范围外收口 | 非英语学习请求不输出通用百科答案 |

## P1 能力增强

P1 目标是让用户明显感到这是 PEAI 学习助手，而不是一个普通聊天壳。

| 能力 | iOS 入口 | 推荐 intent | 说明 |
| --- | --- | --- | --- |
| 解释 | “解释”按钮 / chip | `explain` | 解释英文句子、短语、概念或用户粘贴文本 |
| 翻译 | “翻译”按钮 / chip | `translate` | 支持中英互译，并说明关键表达 |
| 润色 | “润色”按钮 / chip | `polish` | 改善英文表达，保留原意 |
| 总结 | “总结”按钮 / chip | `summarize` | 总结英文材料或上传内容 |
| 作文评分 | “作文评分”按钮 / chip | `grade_writing` | 独立助手内的轻量评分，不替代写作页完整评分工作台 |
| 生成例句 | “例句”按钮 / chip | `generate_examples` | 围绕单词、短语或句型生成例句 |
| 题目分析 | “题目分析”按钮 / chip | `analyze_question` | 分析英文写作题或阅读题要求 |

P1 中，iOS 可以保留自由输入，但发送前应根据用户选择的任务 chip 写入明确 intent。未选择任务时才使用 `free_chat`，且后端仍按英语学习范围处理。

## P2 目标契约

P2 适合在 P0/P1 稳定后推进：

- 停止生成：iOS 可取消正在进行的 SSE run。
- 重新生成：对最后一条 assistant 消息重新生成，并明确版本策略。
- 附件统一协议：从 multipart 文件上传逐步收敛到 `AttachmentRef` 元数据和预览接口。
- 模型列表：设置页展示可用模型和默认模型。
- 结构化学习输出：对词汇、句构、翻译、评分等回复增加可被 iOS 原生渲染的 block metadata。
- 质量反馈：iOS 支持对回复点赞、点踩或标记“不像英语学习助手”。

## iOS 页面模块建议

只针对独立助手，建议 iOS 拆成以下模块：

| 模块 | 职责 | 对接接口 |
| --- | --- | --- |
| `AssistantHomeView` | 空状态、任务入口、最近会话 | `GET /api/assistant/conversations` |
| `AssistantConversationView` | 消息列表、流式状态、错误重试 | `GET /api/assistant/conversations/{id}` |
| `AssistantComposer` | 文本输入、任务 chip、附件入口、发送按钮 | `POST /messages/run/stream` |
| `AssistantConversationStore` | 会话列表、当前会话、loading/error 状态 | Java Assistant API |
| `AssistantSSEClient` | SSE 连接、事件解析、取消和断线处理 | `text/event-stream` |
| `AssistantTaskRouter` | UI 任务入口到 `intent/scope/mode` 的映射 | 本地纯逻辑 |

## Intent 映射

iOS 不应把所有请求都发成 `free_chat`。推荐映射如下：

| iOS 任务入口 | `mode` | `intent` | `scope` | 适用输入 |
| --- | --- | --- | --- | --- |
| 自由提问 | `daily_explain` | `free_chat` | `message_only` | 英语学习相关自然语言问题 |
| 解释 | `daily_explain` | `explain` | `message_only` 或 `selection` | 英文词句、语法点、粘贴内容 |
| 翻译 | `daily_explain` | `translate` | `message_only` | 中英文本 |
| 润色 | `daily_explain` 或 `exam_boost` | `polish` | `message_only` | 英文句子、段落 |
| 总结 | `daily_explain` | `summarize` | `message_only` | 英文材料或附件文本 |
| 作文评分 | `exam_boost` | `grade_writing` | `message_only` | 作文正文，建议带学段 |
| 例句 | `daily_explain` | `generate_examples` | `message_only` | 单词、短语、句型 |
| 题目分析 | `exam_boost` | `analyze_question` | `message_only` | 写作题、阅读题、考试题干 |

## P0 请求示例

### 创建会话

```http
POST /api/assistant/conversations
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "title": "新对话",
  "projectId": null
}
```

### 流式发送解释请求

```http
POST /api/assistant/conversations/{conversationUid}/messages/run/stream
Authorization: Bearer <access_token>
Accept: text/event-stream
Content-Type: application/json
```

```json
{
  "clientMessageId": "ios-msg-uuid",
  "idempotencyKey": "ios-msg-uuid",
  "mode": "daily_explain",
  "intent": "explain",
  "scope": "message_only",
  "message": {
    "text": "请解释 take it for granted 的用法"
  },
  "studyContext": {
    "studyStage": "postgrad",
    "targetExam": "postgrad",
    "locale": "zh-CN",
    "responseLanguage": "zh-CN"
  },
  "clientMeta": {
    "sourcePage": "assistant",
    "timezone": "Asia/Shanghai",
    "userAgent": "PersonalEnglishAI-iOS/1.0"
  }
}
```

### 流式事件处理

iOS 必须按 `runId` 和 `messageId` 合并事件。

```text
run.started
message.created
message.delta*
message.completed
run.completed
```

处理规则：

- 收到 `run.started`：进入生成中状态。
- 收到 `message.created`：创建本地 assistant 占位消息。
- 收到 `message.delta`：追加文本。
- 收到 `message.completed`：用完整 `content` 覆盖临时文本。
- 收到 `run.completed`：结束 loading，并保存 run metadata。
- 收到 `run.failed`：结束 loading，展示错误，保留用户输入或提供重试。

## 附件策略

当前建议分阶段处理附件。

### P0

只要求文本流式能力稳定。iOS 可以先隐藏附件入口或标记为后续支持。

### P1

使用当前 multipart 接口发送图片、PDF、TXT、DOC、DOCX：

```http
POST /api/assistant/conversations/{conversationUid}/messages
Content-Type: multipart/form-data
```

限制：

- 最多 5 个文件。
- 单文件最大 10MB。
- 当前 multipart 走兼容聊天链路，不是完整结构化 `AssistantRequest` 流式链路。

### P2

补齐附件上传、元数据和预览接口后，iOS 改用 `AssistantRequest.attachments` 的 `AttachmentRef` 协议，让附件进入正式 Agent run。

## iOS 与后端分工

| 事项 | iOS | Java 后端 | Python Orchestrator |
| --- | --- | --- | --- |
| 登录态 | 保存 token、处理刷新失败回登录 | 校验 JWT、统一错误码 | 不处理 |
| 会话状态 | 展示与本地乐观更新 | MySQL 真源 | 不保存会话管理状态 |
| SSE | 连接、解析、取消、重试 UI | 透传事件、持久化最终消息 | 生成事件 |
| intent | 根据任务入口设置 | 校验和透传 | 路由到目标 Agent |
| 学习上下文 | 传 `studyContext` | 透传并记录 run metadata | 注入 prompt / trace |
| 范围外请求 | UI 引导 | 可返回统一错误或正常透传 | 做英语学习边界收口 |
| 附件 | 选择文件、展示上传状态 | 校验大小、类型、代理上传 | 多模态或文本抽取处理 |
| 调试 | 记录 `traceId/runId` | 写 Agent Debug 记录 | 产生 run metadata |

## 当前三端一致性检查

| 项目 | 当前状态 | 影响 | 建议 |
| --- | --- | --- | --- |
| Web 默认 intent | 普通输入默认 `free_chat` | 容易表现成通用聊天 | iOS P1 用任务 chip 明确 intent |
| Java 文本 run | 已支持 `/messages/run` 和 `/messages/run/stream` | iOS P0 可直接接入 | P0 优先用流式 run |
| 附件流式 | Web 有附件时回退非流式 multipart | 附件体验和结构化 run 不一致 | P1 先兼容，P2 统一 AttachmentRef |
| 学习边界 | Router prompt 有边界，但自由请求仍可能泛化 | 可能回答非英语学习问题 | 后端/Python 需要加强范围外回归样例 |
| iOS 契约 | 已有 API 契约，但缺少产品化 intent 使用指南 | iOS 容易只实现通用 chat | 以本文作为 iOS 任务入口事实来源 |

## 验收用例

P0 必过用例：

| 用例 | 请求 | 预期 |
| --- | --- | --- |
| 新建会话 | 创建空会话 | iOS 进入空对话，后端返回 conversation id |
| 英语解释 | `intent=explain` | 返回中文解释、例句或用法说明 |
| 翻译 | `intent=translate` | 返回译文和关键表达说明 |
| 润色 | `intent=polish` | 返回润色结果和修改点 |
| 流式输出 | SSE run | iOS 可实时追加 delta，completed 后覆盖最终文本 |
| 断流失败 | 中断 SSE 或上游失败 | iOS 停止 loading，展示重试 |
| 非英语学习请求 | 例如“做一个毕业生知识树” | 助手收口到英语学习范围，不输出泛化知识树 |
| 历史恢复 | 退出再进会话 | 消息列表、标题、摘要恢复 |

P1 验收用例：

| 用例 | 请求 | 预期 |
| --- | --- | --- |
| 考试模式作文评分 | `mode=exam_boost`、`intent=grade_writing` | 输出按考试目标组织的评价 |
| 例句生成 | `intent=generate_examples` | 输出符合学段的例句和简短解释 |
| 题目分析 | `intent=analyze_question` | 输出题目要求、作答方向和风险提醒 |
| 附件消息 | multipart 上传文件 | 文件限制、生效回复、错误提示都正确 |

## 开发顺序建议

1. iOS 先实现会话列表、创建会话、详情加载。
2. iOS 接入 `/messages/run/stream`，完成文本流式闭环。
3. iOS 增加任务 chip，并按本文映射 `intent/mode/scope`。
4. 后端和 Python 增加范围外请求回归样例，避免通用百科式输出。
5. iOS 补齐错误态、重试、空输入、token 失效处理。
6. P1 再接入附件兼容链路、文件夹、归档、分享等管理能力。
7. P2 再补停止生成、重新生成、附件统一协议和原生结构化 block 渲染。

## 合入评估

本文档是协同契约和验收标准，不修改运行时代码。作为文档级小改动，适合在 iOS 和后端共同确认后直接合入 `main`。如果后续根据本文改 Java/Python/Web 运行时代码，应另起独立实现任务，并按对应目录 `AGENTS.md` 运行验证。
