# 学习助手 OpenAI Agents P0 验收报告

## 1. 验收范围

本次验收覆盖学习助手 P0 请求链路、Agent run 接口、选中文本询问 AI、助手消息操作、流式事件协议骨架，以及相关文档整理。

已纳入验收的能力：

- OpenAI Agents 风格 P0 请求 DTO。
- 前端纯文本消息接入 `/messages/run`。
- Java 后端接入 Python `/assistant/run`。
- Python 侧请求校验、scope 推断、模型输入映射和确定性 Agent 路由。
- 选中文本后显示“询问 AI 助手”，点击后填入助手输入框但不自动发送。
- 选中文本发送时携带 `selection`、`intent=explain`、`scope=selection_and_message`。
- 助手输出内容支持复制和重试。
- P0 流式事件协议类型骨架。
- Trae 题单统一放入 `docs/题目/`。

当前边界：

- 图片和文件附件仍保留 multipart 兼容链路。
- 完整 P0 JSON 附件 refs 化需要后续补“附件上传后返回稳定 ref 或 OpenAI file id”的接口。

## 2. 当前复查结果

复查项：

- 工作区状态：干净。
- 最近 P0 提交：已存在。
- 设计文档位置：`docs/assistant/openai-agents-sdk-request-architecture.md`。
- 题单位置：`docs/题目/`。
- 关键实现可搜索到：
  - `/assistant/run`
  - `/messages/run`
  - `selection_and_message`
  - `<selected_text>`
  - `input_image`
  - `run.started`
  - `message.delta`
- 禁用模式无命中：
  - `ask_selection`
  - `message.intent`
  - `conversationId.*OpenAI`

## 3. 自动化验收

### 3.1 Python 验收

在项目根目录执行：

```powershell
python\ai_orchestrator\.venv\Scripts\python.exe -m unittest python.ai_orchestrator.tests.test_assistant_request_validator python.ai_orchestrator.tests.test_assistant_request_input_items python.ai_orchestrator.tests.test_assistant_routing python.ai_orchestrator.tests.test_assistant_run_endpoint python.ai_orchestrator.tests.test_assistant_stream_events python.ai_orchestrator.tests.test_openai_input_items_adapter python.ai_orchestrator.tests.test_routing_policy
```

预期结果：

- 全部测试通过。
- 允许出现现有 Pydantic alias warning。
- 不允许出现 test fail 或 error。

### 3.2 后端验收

执行：

```powershell
cd backend
.\mvnw.cmd -q test -Dtest=AssistantControllerTest
```

预期结果：

- `AssistantControllerTest` 通过。
- 允许出现现有 `org.json.JSONObject` 重复依赖 warning。

### 3.3 前端验收

执行：

```powershell
cd web
node --test src\pages\app\assistantMessageActions.test.ts
npm run build
```

预期结果：

- 消息操作测试通过。
- TypeScript 和 Vite build 通过。
- 允许出现现有 chunk size warning。

## 4. 静态搜索验收

### 4.1 禁用模式搜索

在项目根目录执行：

```powershell
Get-ChildItem -Path web\src,backend\src,python\ai_orchestrator -Recurse -Include *.ts,*.vue,*.java,*.py |
  Select-String -Pattern 'ask_selection|message\.intent|conversationId.*OpenAI'
```

预期结果：

- 无输出。

### 4.2 关键能力搜索

执行：

```powershell
Get-ChildItem -Path web\src,backend\src,python\ai_orchestrator -Recurse -Include *.ts,*.vue,*.java,*.py |
  Select-String -Pattern '/assistant/run|messages/run|selection_and_message|selected_text|input_image|run\.started|message\.delta'
```

预期结果：

- 能看到前端、后端、Python 中对应实现。

## 5. 浏览器手工验收

打开页面：

```text
http://localhost:3000/app/assistant
```

### 5.1 纯文本聊天

操作：

1. 新建或进入一个学习助手对话。
2. 输入：`帮我解释一下 present perfect tense`
3. 点击发送。
4. 打开浏览器 DevTools Network。

预期结果：

- 请求走 `POST /api/assistant/conversations/{id}/messages/run`。
- 请求体包含：
  - `appConversationId`
  - `clientMessageId`
  - `mode: daily_explain`
  - `intent: free_chat`
  - `scope: message_only`
  - `message.text`
- 请求体不包含旧的顶层 `input`。
- 请求体不包含 `message.intent`。
- 页面显示用户消息和助手回复。
- 刷新后对话仍在。

### 5.2 选中文本询问 AI

操作：

1. 在 `/app` 业务区任意页面选中一段文本。
2. 选区附近应出现浮层按钮：`询问 AI 助手`。
3. 点击按钮。
4. 跳转或打开学习助手。
5. 输入框自动填入：

```text
请帮我解释这段内容：

「选中的文本」
```

6. 不自动发送。
7. 用户可继续编辑问题。
8. 手动点击发送。
9. 查看 Network 请求。

预期结果：

- 点击浮层后不会自动发送。
- 请求走 `/messages/run`。
- 请求体包含：
  - `intent: explain`
  - `scope: selection_and_message`
  - `selection.text`
  - `selection.source: page_selection`
  - `message.text`
- Python input item 中选中文本被包在 `<selected_text>...</selected_text>` 边界内。

### 5.3 助手输出复制和重试

操作：

1. 等助手生成一条回复。
2. 查看回复下方操作区。
3. 点击复制。
4. 点击重试。

预期结果：

- 回复下方显示复制和重试操作。
- 复制后剪贴板内容等于助手回复文本。
- 页面提示复制成功。
- 重试会找到上一条用户消息重新发送。
- 重试不会拿助手回复本身作为输入。
- loading 和失败状态不破坏页面。

### 5.4 图片和附件

说明：

图片和文件附件当前仍走 multipart 兼容链路，这是 P0 的保守兼容策略。

操作：

1. 在输入框内 `Ctrl+V` 粘贴截图。
2. 或点击 `+` 添加图片、PDF、txt。
3. 输入：`翻译一下图片内容`
4. 点击发送。
5. 查看 Network 请求。

预期结果：

- 图片能在输入框内显示预览。
- 发送后用户消息中仍显示图片或文件卡片。
- 带附件请求走 multipart 兼容接口 `/messages`。
- Python 侧最终 input items 包含 `input_image` 或 `input_file`。
- 模型回复必须基于图片或文件内容，而不是只回复“请提供文本”。
- 刷新同一浏览器后，历史用户消息里的本地附件仍能显示。

### 5.5 模式与路由

操作：

1. 日常模式发送普通解释问题。
2. 考试模式发送评分或提分问题。
3. 发送翻译类问题。
4. 发送润色类问题。

预期结果：

- 日常默认走 daily explain/router 逻辑。
- 考试模式请求中 `mode=exam_boost`。
- 翻译 intent 路由到 translation agent。
- 润色 intent 路由到 writing coach agent。
- Python response metadata 中包含：
  - `run.agentName`
  - `run.intent`
  - `run.scope`
  - `run.mode`

## 6. 文档验收

检查以下文件是否存在且内容可读：

```text
docs/assistant/openai-agents-sdk-request-architecture.md
docs/题目/openai-agents-sdk-p0-trae-tasks.md
docs/题目/assistant-inline-upload-trae-tasks.md
docs/题目/assistant-selection-actions-tasks.md
```

预期结果：

- 设计文档说明 P0 request/run contract。
- 题单都在 `docs/题目/`。
- 新题单不再散落在 `docs/` 根目录。

## 7. 通过标准

本次验收通过需要同时满足：

- 自动化测试和 build 全部通过。
- 纯文本聊天走 `/messages/run`。
- 选中文本点击后只预填输入框，不自动发送。
- 选中文本手动发送时携带 `selection`。
- 助手回复具备复制和重试操作。
- 图片和附件仍能被模型实际读取。
- 刷新后历史对话和本地附件显示正常。
- 文档位置和内容符合预期。

## 8. 后续建议

P1 建议补齐：

- 附件上传接口，返回稳定 `attachmentId`、`openaiFileId` 或可读 URL。
- 前端带附件消息全量切换为 P0 JSON `attachments: AssistantAttachmentRef[]`。
- 后端持久化 run metadata。
- 流式 SSE 真实执行链路，而不仅是协议骨架。
- 浏览器手工验收后，把实际结果补回本文档。
