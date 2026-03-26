# POST /api/ai/command — 契约与商用 Document 集成

前端只传**结构化意图 + 上下文引用**（docId = 文档 public_id），不传最终 prompt。  
上下文内容从 **Document Service** 按 docId（及可选 revision）读取，traceId 串联可观测。

## contextRefs 契约

- **docId**（必填）：文档对外 ID（public_id），由 `POST /api/docs` 返回的 `docId`
- **revision**（可选）：不传则用最新版本
- selectionRange / pinnedIds：可选，预留

## constraints 契约

写作侧边助手当前会额外使用以下标准字段：

- `conversationId`：多轮对话 ID。前端按 `docId` 维度持久化，后端用它恢复最近一次 OpenAI `response.id`
- `selectedText`：当前高亮选中的英文文本
- `includeDraft`：是否把作文正文纳入本轮上下文
- `assistantMode`：预留字段，当前可不传

## 响应契约

`POST /api/ai/command` 保持兼容旧字段，同时新增结构化 assistant 字段：

- `message`：本轮用户可见回复
- `responseId`：OpenAI Responses API 的 `response.id`
- `actions[]`：结构化动作，例如 `replace_selection`
- `toolRuns[]`：本轮工具执行摘要
- `result.apply` / `finalResult.content`：兼容旧前端的文本视图

`actions[]` 当前用于显式驱动“替换选中内容”按钮，不再要求前端从文本里猜隐藏 JSON。

## 流式接口

新增 `POST /api/ai/command/stream`，当前仅支持 `intent=chat`。

- 返回类型：`text/event-stream`
- 中间事件：`assistant_event`
- 收尾事件：`final`

`assistant_event` 会携带：

- `status`：`thinking | tool_running | tool_completed | completed | failed`
- `toolRuns[]`：最新工具执行状态
- `message`：阶段提示文案

`final` 会携带完整的 `AICommandResponse`，结构与同步接口一致。

## 会话状态与 Redis

写作助手的 OpenAI 多轮状态不再只依赖前端 recent messages。

- Redis key：`peai:assistant:response:{conversationId}`
- value：最近一次 OpenAI `response.id`
- TTL：24 小时
- fallback：如果 Redis 不可用，则本轮请求仍可执行，但不会恢复上一轮 OpenAI state

## 自测（无 JWT，mock 租户）

无 JWT 时使用 `tenantId=mock-tenant`，通常查不到库里的文档，会返回 `document not found`。

## 自测（带 JWT，验收读库）

1. 登录获取 token，调用 `POST /api/docs` 创建文档，得到 `docId`。
2. `POST /api/ai/command`，Header `Authorization: Bearer <token>`，Body：
   - `intent`: `"rewrite"`
   - `contextRefs.docId`: 上一步的 `docId`
3. 预期：`status=succeeded`，`finalResult.content` 含 `[ORCH:rewrite]` 及 `draftContent=...`；日志有 `buildContext traceId=... docId=... found=true`。

## 鉴权说明

- `/api/ai/command` 仍在 JWT 白名单（临时），可不带 token 调用（此时为 mock 租户）。
- `/api/ai/command/stream` 复用同一套请求上下文解析逻辑。
- 带 token 时，tenantId/workspaceId 从 JWT 推导，可命中该用户创建的文档。
