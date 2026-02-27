# 商用级 Document API — 验收 6 用例

## 1. 建表

执行 `src/main/resources/db/schema.sql` 中的 `documents`、`document_revisions`、`document_pins`。

## 2. 鉴权

文档接口需 JWT。请求头：`Authorization: Bearer <token>`（先调用登录接口取 token）。

## 3. 验收用例（Postman/curl）

| # | 操作 | 预期 |
|---|------|------|
| 1 | **POST /api/docs** Body: `{"title":"My Draft","content":"This is my first draft."}` | 200，返回 `docId`、`latestRevision: 1` |
| 2 | **GET /api/docs/{docId}** | 200，返回 `title`、`latestRevision`、`content` |
| 3 | **POST /api/docs/{docId}/revisions** Body: `{"expectedLatestRevision":1,"content":"updated content..."}` | 200，返回 `latestRevision: 2` |
| 4 | 再次 **POST /api/docs/{docId}/revisions** Body: `{"expectedLatestRevision":1,"content":"..."}` | **409** 冲突（乐观锁） |
| 5 | **DELETE /api/docs/{docId}**，再 **GET /api/docs/{docId}** | DELETE 204；GET **404**（已删除/找不到） |
| 6 | **POST /api/ai/command** Body: `intent=rewrite`，`contextRefs.docId`= 上面创建的 docId | 200，`status=succeeded`，content 含 `[ORCH:rewrite]`；日志 `found=true` |

## 4. 与 AI Orchestrator 集成

- ContextBuilder 按 `contextRefs.docId`（及可选 `revision`）调 DocumentService 取内容。
- 日志：`buildContext traceId=... tenantId=... workspaceId=... docId=... revision=... found=...`
- 响应带 `traceId`，便于计费/审计/风控。
