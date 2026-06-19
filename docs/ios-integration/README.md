---
title: iPadOS 联调文档
status: draft
owner: backend
last_updated: 2026-06-18
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java
  - backend/src/main/java/com/personalenglishai/backend/controller/auth/v1/AuthControllerV1.java
  - docker-compose.local.yml
related_docs:
  - docs/ios-integration/ai-assistant-api-contract.md
  - docs/ios-integration/auth-api-contract.md
  - docs/ios-integration/local-dev-and-docker.md
  - docs/ios-integration/integration-checklist.md
  - docs/ios-integration/troubleshooting.md
  - docs/ios-integration/changelog.md
---

# iPadOS 联调文档

## 当前结论

`docs/ios-integration/` 是 iPad 端和后端联调的接口事实来源。iPad 端接入、后端接口补齐、联调验收和问题排查，优先以本目录文档为准。

当前重点是 AI 助手联调：

- 当前已实现：会话、流式输出、附件上传、文件夹、置顶、归档、移动、分享。
- 待后端补齐：模型列表、停止生成、重新生成、附件元数据/预览、Mermaid/graph-json 输出协议。
- 本地联调默认后端地址：`http://127.0.0.1:18080`。
- iPad Simulator 访问宿主机后端同样使用：`http://127.0.0.1:18080`。

## 文档地图

| 文档 | 用途 |
| --- | --- |
| [AI 助手 API 契约](./ai-assistant-api-contract.md) | AI 助手会话、消息、流式、附件、文件夹、归档、分享，以及待补齐接口的契约 |
| [认证 API 契约](./auth-api-contract.md) | 登录、注册、刷新、退出、验证码、改密等 iPad 端鉴权接口 |
| [本地开发与 Docker](./local-dev-and-docker.md) | Docker 启动、端口、iPad Simulator 连接、冒烟命令 |
| [联调验收清单](./integration-checklist.md) | iPad 端和后端逐项验收标准 |
| [排障指南](./troubleshooting.md) | 常见网络、鉴权、SSE、上传、Docker 问题 |
| [变更记录](./changelog.md) | iPadOS 联调契约变更记录 |

## 统一约定

### Base URL

本地 Docker 后端：

```text
http://127.0.0.1:18080
```

生产、beta 或局域网联调地址由环境配置提供，iPad 端不得在业务代码中硬编码。

### 统一响应体

除 SSE 流和少数文本导出接口外，后端使用统一 JSON 响应：

```json
{
  "code": "0",
  "message": "OK",
  "data": {},
  "traceId": "trace-id"
}
```

`code = "0"` 表示业务成功。HTTP 非 2xx 或 `code != "0"` 时，iPad 端应进入错误处理分支，并在调试日志里保留 `traceId`。

### 鉴权

受保护接口统一使用：

```http
Authorization: Bearer <access_token>
```

`access_token` 来自登录或刷新接口返回的 `data.token`。`refresh_token` 由后端写入 httpOnly Cookie，iPad 端如果使用 `URLSession`，需要保证同一会话配置可以携带 Cookie。

### 当前实现状态标记

| 状态 | 含义 |
| --- | --- |
| 当前已实现 | 后端已有 Controller/Service 路径，iPad 端可以直接联调 |
| 目标契约 | iPad 端需要，后端尚需补齐或确认实现 |
| 协议约定 | 先固定数据结构，后续实现不得破坏字段语义 |

## 维护规则

- 新增、删除或修改 AI 助手/Auth 接口时，必须同步更新本目录对应契约。
- 只新增兼容字段时，更新 `last_updated` 和 [变更记录](./changelog.md)。
- 改动路径、必填字段、响应字段语义、错误码时，需要先和 iPad 端确认迁移窗口。
- 本目录文档更新后，运行 `cd docs && npm run build` 验证 VitePress 链接和 Markdown。
