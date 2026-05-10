# POST /api/ai/command — 契约与商用 Document 集成

前端只传**结构化意图 + 上下文引用**（docId = 文档 public_id），不传最终 prompt。  
上下文内容从 **Document Service** 按 docId（及可选 revision）读取，traceId 串联可观测。

## aiProvider 契约

- `aiProvider`（可选）：当前会话希望命中的模型提供方
- 固定值：
  - `openai`
  - `kimi`
  - `qwen`
- 若请求未显式提供 `aiProvider`，后端回退到系统级 `AI_PROVIDER_ACTIVE`
- 写作页顶部模型选择器会按 `docId` 恢复上次选择，并将该值透传到 `/api/ai/command`

## contextRefs 契约

- **docId**（必填）：文档对外 ID（public_id），由 `POST /api/docs` 返回的 `docId`
- **revision**（可选）：不传则用最新版本
- selectionRange / pinnedIds：可选，预留

## 自测（无 JWT，mock 租户）

无 JWT 时使用 `tenantId=mock-tenant`，通常查不到库里的文档，会返回 `document not found`。

## 自测（带 JWT，验收读库）

1. 登录获取 token，调用 `POST /api/docs` 创建文档，得到 `docId`。
2. `POST /api/ai/command`，Header `Authorization: Bearer <token>`，Body：
   - `intent`: `"rewrite"`
   - `contextRefs.docId`: 上一步的 `docId`
   - `aiProvider`: `"openai" | "kimi" | "qwen"`（可选）
3. 预期：`status=succeeded`，`finalResult.content` 含 `[ORCH:rewrite]` 及 `draftContent=...`；日志有 `buildContext traceId=... docId=... found=true`。

## 鉴权说明

- `/api/ai/command` 仍在 JWT 白名单（临时），可不带 token 调用（此时为 mock 租户）。
- 带 token 时，tenantId/workspaceId 从 JWT 推导，可命中该用户创建的文档。
