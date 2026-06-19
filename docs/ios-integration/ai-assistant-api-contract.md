---
title: AI 助手 API 契约
status: draft
owner: backend
last_updated: 2026-06-18
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java
  - backend/src/main/java/com/personalenglishai/backend/controller/PublicAssistantShareController.java
  - backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java
  - python/ai_orchestrator/assistant_service.py
related_docs:
  - docs/ios-integration/README.md
  - docs/ios-integration/integration-checklist.md
  - docs/ios-integration/troubleshooting.md
---

# AI 助手 API 契约

## 当前结论

本文件是 iPad 端接入 AI 助手的接口事实来源。当前后端已实现会话、流式输出、附件上传、文件夹、置顶、归档、移动和分享；模型列表、停止生成、重新生成、附件元数据/预览、Mermaid/graph-json 输出协议为目标契约，后端补齐时必须保持本文字段语义。

## 通用约定

### 鉴权

除公开分享读取外，所有 `/api/assistant/**` 接口都需要：

```http
Authorization: Bearer <access_token>
```

### JSON 包装

非流式成功响应：

```json
{
  "code": "0",
  "message": "OK",
  "data": {},
  "traceId": "trace-id"
}
```

### 通用错误码

| HTTP 状态 | 错误码 | 场景 | iPad 端处理 |
| --- | --- | --- | --- |
| 400 | `400001` | 参数验证失败、上传文件不合法、消息为空 | 停留当前页面，提示用户修正输入 |
| 401 | `401002` | token 失效或未登录 | 刷新 token；刷新失败则回登录页 |
| 404 | `404020` | 助手对话不存在 | 从本地列表移除该会话或提示已删除 |
| 404 | `404021` | 文件夹不存在 | 刷新文件夹列表后重试 |
| 404 | `404022` | 分享不存在或已失效 | 展示分享失效页 |
| 429 | `429010` | AI token 额度已用完 | 展示额度耗尽提示 |
| 503 | `503020` | 学习助手上游不可用 | 允许用户重试，保留 traceId |

## 数据模型

### ConversationSummary

```json
{
  "id": "conv-uuid",
  "projectId": 1,
  "title": "对话标题",
  "summary": "摘要",
  "pinned": false,
  "archived": false,
  "createdAt": "2026-06-18T10:00:00",
  "updatedAt": "2026-06-18T10:10:00"
}
```

### ConversationDetail

```json
{
  "id": "conv-uuid",
  "projectId": 1,
  "title": "对话标题",
  "summary": "摘要",
  "pinned": false,
  "archived": false,
  "createdAt": "2026-06-18T10:00:00",
  "updatedAt": "2026-06-18T10:10:00",
  "messages": [
    {
      "id": "msg-uuid",
      "role": "user",
      "content": "请解释这段话",
      "status": "done",
      "createdAt": "2026-06-18T10:00:10"
    }
  ]
}
```

### AssistantRequest

用于 Agent run 和流式 run。

```json
{
  "appConversationId": "conv-uuid",
  "clientMessageId": "ipad-msg-uuid",
  "idempotencyKey": "optional-idempotency-key",
  "mode": "daily_explain",
  "intent": "free_chat",
  "scope": "message_only",
  "message": {
    "text": "请解释这句话"
  },
  "selection": {
    "text": "selected text",
    "source": "document",
    "sourceId": "source-id",
    "messageId": "msg-uuid",
    "documentId": "doc-id",
    "range": {
      "start": 0,
      "end": 12
    }
  },
  "attachments": [],
  "studyContext": {
    "studyStage": "postgrad",
    "cefrLevel": "B2",
    "targetExam": "postgrad_en1",
    "locale": "zh-CN",
    "responseLanguage": "zh-CN"
  },
  "clientMeta": {
    "sourcePage": "assistant",
    "timezone": "Asia/Shanghai",
    "userAgent": "PersonalEnglishAI-iPad/1.0"
  }
}
```

约束：

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| `clientMessageId` | 是 | 最长 128 |
| `idempotencyKey` | 否 | 最长 128 |
| `mode` | 是 | 最长 32 |
| `intent` | 是 | 最长 32 |
| `scope` | 否 | 最长 64 |
| `message.text` | 否 | 最长 8000 |
| `selection.text` | 否 | 最长 8000 |
| `attachments` | 否 | 最多 5 个 |

### AttachmentRef

目标协议字段如下，当前 JSON run 可传入该结构；独立元数据和预览接口仍待后端补齐。

```json
{
  "attachmentId": "att-uuid",
  "provider": "local",
  "openaiFileId": "file_xxx",
  "storageKey": "assistant/att-uuid.pdf",
  "url": "https://example.com/file.pdf",
  "name": "paper.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 102400,
  "kind": "pdf",
  "processing": {
    "status": "ready",
    "errorCode": null,
    "extractedTextAvailable": true,
    "extractedText": "preview text",
    "pageCount": 3,
    "checksum": "sha256"
  },
  "modelInput": {
    "preferredPart": "file",
    "imageDetail": "auto"
  }
}
```

## 当前已实现接口

### 列出文件夹

```http
GET /api/assistant/projects
```

Request：无。

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": [
    {
      "id": 1,
      "name": "考研英语",
      "description": "作文练习",
      "createdAt": "2026-06-18T10:00:00",
      "updatedAt": "2026-06-18T10:00:00"
    }
  ]
}
```

错误码：`401002`、`500000`。

iPad 验收：登录后进入助手首页，可以拉取文件夹；空数组时展示默认未分组入口。

### 创建文件夹

```http
POST /api/assistant/projects
```

Request：

```json
{
  "name": "考研英语",
  "description": "作文练习"
}
```

Response：`data` 为 Project。错误码：`400001`、`401002`。

iPad 验收：创建后列表立即出现新文件夹，`name` 为空或超过 120 字符时展示校验错误。

### 更新文件夹

```http
PATCH /api/assistant/projects/{projectId}
```

Request：

```json
{
  "name": "新的文件夹名",
  "description": "新的说明"
}
```

Response：`data` 为 Project。错误码：`400001`、`401002`、`404021`。

iPad 验收：重命名后会话列表中的文件夹名称同步更新。

### 删除文件夹

```http
DELETE /api/assistant/projects/{projectId}
```

Response：

```json
{
  "code": "0",
  "message": "OK"
}
```

错误码：`401002`、`404021`。

iPad 验收：删除后文件夹从列表消失；关联会话的后端归属行为以后端实际实现为准，iPad 端删除后应刷新会话列表。

### 列出会话

```http
GET /api/assistant/conversations?archived=false&projectId=1
```

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `archived` | boolean | 否 | `true` 只看归档，`false` 只看未归档，不传由后端决定默认范围 |
| `projectId` | number | 否 | 文件夹 ID |

Response：`data` 为 ConversationSummary 数组。

错误码：`401002`。

iPad 验收：普通列表、归档列表、文件夹筛选均能刷新；置顶会话应由 iPad 端按 `pinned` 做视觉标识。

### 创建会话

```http
POST /api/assistant/conversations
```

Request：

```json
{
  "title": "新对话",
  "projectId": 1
}
```

Response：`data` 为 ConversationDetail。

错误码：`400001`、`401002`、`404021`。

iPad 验收：创建后进入空会话详情；未传 title 时允许后端生成默认标题。

### 获取会话详情

```http
GET /api/assistant/conversations/{conversationUid}
```

Response：`data` 为 ConversationDetail。

错误码：`401002`、`404020`。

iPad 验收：可恢复历史消息；`role` 至少支持 `user` 和 `assistant`。

### 更新会话标题/摘要

```http
PATCH /api/assistant/conversations/{conversationUid}
```

Request：

```json
{
  "title": "新的标题",
  "summary": "新的摘要"
}
```

Response：`data` 为 ConversationDetail。

错误码：`400001`、`401002`、`404020`。

iPad 验收：标题编辑保存后，返回列表时标题保持一致。

### 发送普通消息

```http
POST /api/assistant/conversations/{conversationUid}/messages
Content-Type: application/json
```

Request：

```json
{
  "message": "请解释 present perfect",
  "studyStage": "postgrad",
  "assistantMode": "coach"
}
```

Response：`data` 为 ConversationDetail，包含新增 user/assistant 消息。

错误码：`400001`、`401002`、`404020`、`429010`、`503020`。

iPad 验收：发送后消息列表出现用户消息和助手回复；失败时保留输入框内容并允许重试。

### 发送 Agent 消息

```http
POST /api/assistant/conversations/{conversationUid}/messages/run
Content-Type: application/json
```

Request：AssistantRequest。

Response：`data` 为 ConversationDetail。

错误码：`400001`、`401002`、`404020`、`429010`、`503020`。

iPad 验收：适用于带 `mode`、`intent`、`selection`、`studyContext` 的结构化助手请求；后端会持久化 user/assistant 消息。

### 发送 Agent 流式消息

```http
POST /api/assistant/conversations/{conversationUid}/messages/run/stream
Accept: text/event-stream
Content-Type: application/json
```

Request：AssistantRequest。

Response：SSE，每个事件为一行 `data: <json>`。

事件顺序：

```text
data: {"type":"run.started","runId":"run_x","traceId":"trace_x","agentName":"Router Agent","model":"test-model"}

data: {"type":"message.created","runId":"run_x","messageId":"msg_x","role":"assistant"}

data: {"type":"message.delta","runId":"run_x","messageId":"msg_x","delta":"Hello"}

data: {"type":"message.completed","runId":"run_x","messageId":"msg_x","content":"Hello world"}

data: {"type":"run.completed","runId":"run_x","run":{"runId":"run_x","traceId":"trace_x","agentName":"Router Agent","model":"test-model"}}
```

失败事件：

```json
{
  "type": "run.failed",
  "error": {
    "code": "OPENAI_RUN_FAILED",
    "message": "学习助手暂时不可用"
  }
}
```

错误码：连接建立前可能返回 `400001`、`401002`、`404020`、`429010`、`503020`；连接建立后失败通过 `run.failed` 事件表达。

iPad 验收：能实时拼接 `message.delta`；收到 `message.completed` 后以完整 `content` 覆盖临时文本；收到 `run.completed` 后结束加载态；断流或 `run.failed` 时展示重试。

### 附件上传并发送消息

```http
POST /api/assistant/conversations/{conversationUid}/messages
Content-Type: multipart/form-data
```

Form fields：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `message` | string | 否 | 文本消息；没有文件时必填 |
| `studyStage` | string | 否 | 学段 |
| `assistantMode` | string | 否 | 助手模式 |
| `files` | file[] | 否 | 最多 5 个文件 |

限制：

- 单文件最大 `10MB`。
- 支持 `PNG`、`JPG`、`WebP`、`PDF`、`TXT`、`DOC`、`DOCX`。
- `message` 和 `files` 至少提供一个。

Response：`data` 为 ConversationDetail。

错误码：`400001`、`401002`、`404020`、`429010`、`503020`。

iPad 验收：上传图片/PDF/TXT 后能收到助手回复；超过大小、超过数量、非法类型时无需重试上传，直接提示用户。

### 归档会话

```http
POST /api/assistant/conversations/{conversationUid}/archive
```

Response：`data` 为 ConversationSummary，`archived = true`。

错误码：`401002`、`404020`。

iPad 验收：归档后从未归档列表消失，在归档列表出现。

### 恢复会话

```http
POST /api/assistant/conversations/{conversationUid}/restore
```

Response：`data` 为 ConversationSummary，`archived = false`。

错误码：`401002`、`404020`。

iPad 验收：恢复后回到普通会话列表。

### 获取归档设置

```http
GET /api/assistant/archive/settings
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "archiveDir": "/path/to/archive",
    "defaultArchiveDir": "/default/path",
    "custom": false
  }
}
```

错误码：`401002`。

iPad 验收：设置页可以展示当前归档目录；iPad 端不应假设目录一定可写。

### 更新归档设置

```http
PATCH /api/assistant/archive/settings
```

Request：

```json
{
  "archiveDir": "/path/to/archive"
}
```

Response：`data` 为归档设置。

错误码：`400001`、`401002`。

iPad 验收：保存后重新进入设置页，目录保持一致。

### 置顶/取消置顶

```http
POST /api/assistant/conversations/{conversationUid}/pin
```

Request：

```json
{
  "pinned": true
}
```

Response：`data` 为 ConversationSummary。

错误码：`401002`、`404020`。

iPad 验收：置顶状态立即反映到列表；取消置顶后恢复普通排序。

### 移动会话

```http
POST /api/assistant/conversations/{conversationUid}/move
```

Request：

```json
{
  "projectId": 1
}
```

`projectId = null` 表示移出文件夹。

Response：`data` 为 ConversationSummary。

错误码：`400001`、`401002`、`404020`、`404021`。

iPad 验收：移动后源文件夹列表移除，目标文件夹列表出现；移出文件夹后在未分组列表出现。

### 删除会话

```http
DELETE /api/assistant/conversations/{conversationUid}
```

Response：

```json
{
  "code": "0",
  "message": "OK"
}
```

错误码：`401002`、`404020`。

iPad 验收：删除后详情页返回列表；再次进入该会话返回 404 时本地清理缓存。

### 创建分享

```http
POST /api/assistant/conversations/{conversationUid}/share
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "shareToken": "token",
    "sharePath": "/assistant/share/token",
    "createdAt": "2026-06-18T10:00:00"
  }
}
```

错误码：`401002`、`404020`。

iPad 验收：重复分享同一会话应返回同一有效分享；iPad 可复制或系统分享 `sharePath` 拼接后的完整 URL。

### 撤销分享

```http
DELETE /api/assistant/shares/{shareToken}
```

Response：

```json
{
  "code": "0",
  "message": "OK"
}
```

错误码：`401002`。

iPad 验收：撤销后公开分享读取接口返回 `404022`。

### 读取公开分享

```http
GET /api/public/assistant/shares/{shareToken}
```

鉴权：公开接口，不需要 Authorization。

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "title": "分享标题",
    "messages": [],
    "createdAt": "2026-06-18T10:00:00"
  }
}
```

错误码：`404022`。

iPad 验收：未登录状态可打开分享页；失效 token 展示失效状态。

### 创建 ChatKit 写作教练会话

```http
POST /api/assistant/chatkit/writing-coach/session
```

Request：

```json
{
  "workflowId": "wf_xxx",
  "conversationId": "conv-uuid",
  "writingContext": {},
  "stateVariables": {}
}
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "clientSecret": "secret",
    "sessionId": "session-id",
    "expiresAt": 1780000000
  }
}
```

错误码：`401002`、`503030`。

iPad 验收：若 iPad 端采用 ChatKit，必须确认 `clientSecret` 未写入持久日志；未配置 workflow 时展示配置缺失。

## 待补齐目标契约

### 模型列表

状态：目标契约。

```http
GET /api/assistant/models
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": [
    {
      "id": "gpt-5.4-mini",
      "label": "GPT-5.4 Mini",
      "provider": "openai",
      "default": true,
      "supportsStreaming": true,
      "supportsAttachments": true,
      "supportsVision": true,
      "maxInputTokens": 128000,
      "status": "available"
    }
  ]
}
```

错误码：`401002`、`503020`。

iPad 验收：设置页能展示模型列表；默认模型可自动选中；接口失败时回退到后端默认模型，不阻塞普通发送。

### 停止生成

状态：目标契约。

```http
POST /api/assistant/conversations/{conversationUid}/runs/{runId}/cancel
```

Request：

```json
{
  "clientMessageId": "ipad-msg-uuid"
}
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "runId": "run_x",
    "status": "cancelled",
    "cancelledAt": "2026-06-18T10:01:00"
  }
}
```

错误码：`400001`、`401002`、`404020`、`404030`、`409020`。

iPad 验收：流式生成时点击停止，SSE 结束或收到 `run.cancelled`；已完成 run 再取消应展示“已完成”而不是报错崩溃。

### 重新生成

状态：目标契约。

```http
POST /api/assistant/conversations/{conversationUid}/messages/{messageId}/regenerate
```

Request：

```json
{
  "clientMessageId": "ipad-regenerate-uuid",
  "mode": "daily_explain",
  "intent": "free_chat",
  "stream": true
}
```

Response：

非流式时 `data` 为 ConversationDetail；流式版本建议补充：

```http
POST /api/assistant/conversations/{conversationUid}/messages/{messageId}/regenerate/stream
```

并沿用现有 SSE 事件。

错误码：`400001`、`401002`、`404020`、`404031`、`409021`、`503020`。

iPad 验收：对最后一条 assistant 消息点击重新生成，原消息可保留为旧版本或被替换，但后端必须返回明确版本策略。

### 附件元数据

状态：目标契约。

```http
GET /api/assistant/attachments/{attachmentId}
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "attachmentId": "att-uuid",
    "name": "paper.pdf",
    "mimeType": "application/pdf",
    "sizeBytes": 102400,
    "kind": "pdf",
    "processing": {
      "status": "ready",
      "pageCount": 3,
      "checksum": "sha256"
    }
  }
}
```

错误码：`401002`、`404040`、`503020`。

iPad 验收：附件上传后可进入详情页查看文件名、大小、处理状态；`processing.status = failed` 时展示失败原因。

### 附件预览

状态：目标契约。

```http
GET /api/assistant/attachments/{attachmentId}/preview
```

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | number | 否 | PDF 页码，从 1 开始 |
| `format` | string | 否 | `json`、`text`、`image` |

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "attachmentId": "att-uuid",
    "format": "text",
    "text": "extracted preview",
    "thumbnailUrl": "https://example.com/preview.png",
    "page": 1,
    "pageCount": 3
  }
}
```

错误码：`400001`、`401002`、`404040`、`409040`。

iPad 验收：PDF 可预览第一页或文本摘要；图片可展示缩略图；处理未完成时展示排队或处理中。

## Mermaid 和 graph-json 输出协议

状态：协议约定，后端需在 AI 输出或消息元数据里补齐稳定载体。iPad 端不得从自然语言里猜测图表结构。

### 推荐载体

助手消息允许携带结构化 parts：

```json
{
  "id": "msg-uuid",
  "role": "assistant",
  "content": "下面是知识结构图。",
  "status": "done",
  "parts": [
    {
      "type": "markdown",
      "text": "下面是知识结构图。"
    },
    {
      "type": "mermaid",
      "language": "mermaid",
      "code": "flowchart TD\nA[Topic] --> B[Detail]",
      "title": "知识结构图"
    },
    {
      "type": "graph-json",
      "version": "1.0",
      "graph": {
        "nodes": [
          {"id": "topic", "label": "Topic", "type": "concept"}
        ],
        "edges": []
      }
    }
  ]
}
```

### SSE 增量事件

目标事件：

```json
{
  "type": "message.part",
  "runId": "run_x",
  "messageId": "msg_x",
  "part": {
    "type": "mermaid",
    "code": "flowchart TD\nA --> B"
  }
}
```

### graph-json 约束

```json
{
  "version": "1.0",
  "graph": {
    "directed": true,
    "nodes": [
      {
        "id": "n1",
        "label": "主语从句",
        "type": "concept",
        "metadata": {}
      }
    ],
    "edges": [
      {
        "id": "e1",
        "source": "n1",
        "target": "n2",
        "label": "包含",
        "type": "contains",
        "metadata": {}
      }
    ]
  }
}
```

iPad 验收：

- Mermaid 渲染失败时展示源码折叠区，不丢失消息。
- graph-json 至少能渲染节点和边；未知 `type` 使用默认样式。
- 自然语言 `content` 和结构化 `parts` 可以同时存在。
