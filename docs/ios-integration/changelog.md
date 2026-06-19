---
title: iPadOS 联调变更记录
status: draft
owner: backend
last_updated: 2026-06-18
review_cycle: on-change
related_code: []
related_docs:
  - docs/ios-integration/README.md
  - docs/ios-integration/ai-assistant-api-contract.md
  - docs/ios-integration/auth-api-contract.md
---

# iPadOS 联调变更记录

## 2026-06-18

### 新增

- 新增 `docs/ios-integration/` 作为 iPad 端和后端联调的接口事实来源。
- 新增 AI 助手 API 契约，覆盖当前已实现的会话、流式输出、附件上传、文件夹、置顶、归档、移动、分享。
- 新增目标契约：模型列表、停止生成、重新生成、附件元数据、附件预览、Mermaid 输出、graph-json 输出。
- 新增认证 API 契约，覆盖邮箱注册/登录、验证码、refresh/logout、密码重置、短信登录注册、修改密码。
- 新增 Docker 本地启动和 iPad Simulator 连接说明。
- 新增联调验收清单和排障指南。

### 兼容性说明

- 本次只新增文档，不修改后端接口实现。
- 标注为“目标契约”的接口尚未作为当前已实现能力承诺，后端补齐时应按契约保持字段语义稳定。
