---
title: iPadOS 联调验收清单
status: draft
owner: backend
last_updated: 2026-06-18
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/AssistantController.java
  - backend/src/main/java/com/personalenglishai/backend/controller/auth/v1/AuthControllerV1.java
related_docs:
  - docs/ios-integration/ai-assistant-api-contract.md
  - docs/ios-integration/auth-api-contract.md
  - docs/ios-integration/local-dev-and-docker.md
---

# iPadOS 联调验收清单

## 当前结论

本清单用于 iPad 端和后端联调前、联调中、合入前逐项确认。AI 助手相关验收优先级最高。

## 环境

| 项目 | 验收标准 | 状态 |
| --- | --- | --- |
| Docker 服务启动 | `backend`、`mysql`、`redis`、`assistant-orchestrator` 均为 running | 待验收 |
| 后端健康检查 | `GET /health` 返回 2xx | 待验收 |
| iPad Simulator 网络 | App 可访问 `http://127.0.0.1:18080` | 待验收 |
| 真机网络 | 真机使用 Mac 局域网 IP，防火墙已放行 | 如适用 |
| AI Key | 需要真实 AI 回复时已配置 `OPENAI_API_KEY` | 如适用 |

## Auth

| 项目 | 验收标准 | 状态 |
| --- | --- | --- |
| 获取验证码 | `GET /api/v1/auth/captcha` 返回 `captchaId`、`bgImage`、`pieceImage` | 待验收 |
| 验证码校验 | `POST /api/v1/auth/captcha/verify` 成功返回 `captchaToken` | 待验收 |
| 邮箱登录 | 登录成功返回 `token`、`tokenType`、`expiresIn` | 待验收 |
| refresh cookie | 登录响应写入 `refresh_token` httpOnly Cookie | 待验收 |
| 刷新 token | `POST /api/v1/auth/refresh` 可返回新 access token | 待验收 |
| 退出登录 | `POST /api/v1/auth/logout` 后本地 token 和 Cookie 清理 | 待验收 |
| 401 处理 | access token 失效时先刷新，刷新失败回登录页 | 待验收 |

## AI 助手会话

| 项目 | 验收标准 | 状态 |
| --- | --- | --- |
| 创建会话 | `POST /api/assistant/conversations` 返回 ConversationDetail | 待验收 |
| 会话列表 | `GET /api/assistant/conversations` 返回 ConversationSummary 数组 | 待验收 |
| 会话详情 | `GET /api/assistant/conversations/{id}` 返回历史 messages | 待验收 |
| 更新标题 | `PATCH /api/assistant/conversations/{id}` 后列表标题同步 | 待验收 |
| 删除会话 | `DELETE /api/assistant/conversations/{id}` 后列表移除 | 待验收 |

## AI 助手消息

| 项目 | 验收标准 | 状态 |
| --- | --- | --- |
| 普通消息 | JSON 消息发送后返回 user/assistant 新消息 | 待验收 |
| Agent run | 结构化 AssistantRequest 可得到回复 | 待验收 |
| 流式输出 | SSE 可实时展示 delta，并在 completed 后结束加载 | 待验收 |
| 流式失败 | `run.failed` 能展示错误并允许重试 | 待验收 |
| 空输入保护 | 空 message 且无文件时返回 `400001`，iPad 不发送无效请求 | 待验收 |
| token 额度 | `429010` 展示额度耗尽，不进入重复请求 | 待验收 |

## 附件

| 项目 | 验收标准 | 状态 |
| --- | --- | --- |
| TXT 上传 | `multipart/form-data` 上传 TXT 后得到助手回复 | 待验收 |
| 图片上传 | PNG/JPG/WebP 上传后得到助手回复 | 待验收 |
| PDF 上传 | PDF 上传后得到助手回复 | 待验收 |
| 文件数量限制 | 超过 5 个文件返回 `400001` | 待验收 |
| 文件大小限制 | 单文件超过 10MB 返回 `400001` | 待验收 |
| 非法类型 | 不支持类型返回 `400001` | 待验收 |
| 元数据/预览 | 目标契约接口补齐后验收 | 待实现 |

## 文件夹、置顶、归档、移动

| 项目 | 验收标准 | 状态 |
| --- | --- | --- |
| 创建文件夹 | 文件夹列表出现新项目 | 待验收 |
| 重命名文件夹 | 文件夹名称更新 | 待验收 |
| 删除文件夹 | 删除后刷新列表无该文件夹 | 待验收 |
| 移动会话 | 会话在目标文件夹出现 | 待验收 |
| 移出文件夹 | `projectId = null` 后进入未分组 | 待验收 |
| 置顶 | `pinned = true` 后列表有置顶标识 | 待验收 |
| 取消置顶 | `pinned = false` 后恢复普通状态 | 待验收 |
| 归档 | `archived = true` 后普通列表移除 | 待验收 |
| 恢复 | 恢复后普通列表出现 | 待验收 |

## 分享

| 项目 | 验收标准 | 状态 |
| --- | --- | --- |
| 创建分享 | 返回 `shareToken` 和 `sharePath` | 待验收 |
| 重复分享 | 同一会话重复分享返回可用分享 | 待验收 |
| 公开读取 | 未登录访问公开分享成功 | 待验收 |
| 撤销分享 | 撤销后公开读取返回 `404022` | 待验收 |

## 待补齐接口

| 接口 | iPad 端验收标准 | 状态 |
| --- | --- | --- |
| 模型列表 | 设置页展示模型，默认模型自动选中 | 待实现 |
| 停止生成 | 流式生成可取消，UI 结束 loading | 待实现 |
| 重新生成 | 对 assistant 消息重新生成并处理版本策略 | 待实现 |
| 附件元数据 | 附件详情展示处理状态和文件信息 | 待实现 |
| 附件预览 | PDF/图片/TXT 可预览 | 待实现 |
| Mermaid 输出 | 渲染失败可回退源码 | 待实现 |
| graph-json 输出 | 节点和边可稳定渲染 | 待实现 |

## 合入前检查

| 项目 | 验收标准 | 状态 |
| --- | --- | --- |
| 文档同步 | 接口变更已更新 `docs/ios-integration/` | 待检查 |
| 错误码同步 | 新错误码已写入契约和 iPad 端处理 | 待检查 |
| 构建验证 | `cd docs && npm run build` 通过 | 待检查 |
| 后端测试 | 如改后端代码，`cd backend && mvn test` 通过 | 如适用 |
| main 合并评估 | 小文档改动可直接合入；高风险接口实现走独立分支/PR | 待评估 |
