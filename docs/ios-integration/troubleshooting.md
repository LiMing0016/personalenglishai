---
title: iPadOS 联调排障
status: draft
owner: backend
last_updated: 2026-06-18
review_cycle: on-change
related_code:
  - docker-compose.local.yml
  - backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java
  - backend/src/main/java/com/personalenglishai/backend/service/assistant/AssistantConversationService.java
related_docs:
  - docs/ios-integration/local-dev-and-docker.md
  - docs/ios-integration/ai-assistant-api-contract.md
  - docs/ios-integration/auth-api-contract.md
---

# iPadOS 联调排障

## 快速判断

先确认是哪一层失败：

| 现象 | 优先检查 |
| --- | --- |
| iPad App 完全连不上 | Base URL、ATS、本机端口、防火墙 |
| HTTP 401 | access token、refresh cookie、登录态清理 |
| HTTP 400 | 请求字段、Content-Type、文件限制 |
| HTTP 503 | assistant-orchestrator、OpenAI Key、上游模型 |
| SSE 无增量 | Accept/Header、URLSession 流读取、后端日志 |
| 上传失败 | 文件大小、MIME、字段名 `files` |

## 网络连接失败

### Simulator

Simulator 访问 Mac 宿主机：

```text
http://127.0.0.1:18080
```

排查：

```bash
curl -i http://127.0.0.1:18080/health
```

如果 Mac 上 curl 成功但 Simulator 失败，检查 App Transport Security 是否允许本地 HTTP。

### 真机 iPad

真机不能使用 `127.0.0.1` 访问 Mac。需要使用 Mac 局域网 IP：

```text
http://<mac-lan-ip>:18080
```

排查：

```bash
ipconfig getifaddr en0
```

然后在 iPad Safari 访问：

```text
http://<mac-lan-ip>:18080/health
```

如果 Safari 都无法访问，检查 Mac 防火墙、Docker 端口映射和网络是否同一 Wi-Fi。

## Docker 服务异常

查看服务：

```bash
docker compose -f docker-compose.local.yml ps
```

查看后端日志：

```bash
docker compose -f docker-compose.local.yml logs -f backend
```

查看 assistant-orchestrator 日志：

```bash
docker compose -f docker-compose.local.yml logs -f assistant-orchestrator
```

常见原因：

- MySQL 端口 `3306` 被本机已有 MySQL 占用。
- Redis 端口 `6379` 被本机已有 Redis 占用。
- Maven 首次下载依赖耗时较长。
- `OPENAI_API_KEY` 为空导致真实 AI 调用失败。

## 认证问题

### 登录返回 `400040`

含义：验证码无效或过期。

处理：

- 重新调用 `GET /api/v1/auth/captcha`。
- 重新完成 `POST /api/v1/auth/captcha/verify`。
- 使用新的 `captchaToken` 登录。

### 登录返回 `403020`

含义：邮箱未验证。

处理：

- 引导用户完成邮箱验证。
- 本地联调可使用已验证测试账号或 dev-login。

### 请求助手接口返回 401

处理：

1. 检查请求是否带 `Authorization: Bearer <access_token>`。
2. 调用 `POST /api/v1/auth/refresh`。
3. 刷新成功后重放原请求。
4. 刷新失败则清理 token 和 Cookie，回登录页。

## SSE 流式问题

### 建连后没有增量

检查请求头：

```http
Accept: text/event-stream
Content-Type: application/json
Authorization: Bearer <access_token>
```

检查请求体必须包含：

```json
{
  "clientMessageId": "ipad-msg-uuid",
  "mode": "daily_explain",
  "intent": "free_chat",
  "message": {
    "text": "hello"
  }
}
```

后端正常事件包含：

```text
run.started -> message.created -> message.delta -> message.completed -> run.completed
```

### 收到 `run.failed`

示例：

```json
{
  "type": "run.failed",
  "error": {
    "code": "OPENAI_RUN_FAILED",
    "message": "学习助手暂时不可用"
  }
}
```

处理：

- iPad 端结束 loading，保留用户输入和已生成的临时内容。
- 记录 `runId`、`traceId`、`error.code`。
- 后端排查 assistant-orchestrator 日志和 OpenAI Key。

### 用户主动离开页面导致后端 broken pipe

后端会把客户端断开视为可忽略场景。iPad 端离开页面时应取消流任务，并把该消息标记为已取消或草稿态。

## 附件上传问题

字段名必须是：

```text
files
```

限制：

- 最多 5 个文件。
- 单文件最大 10MB。
- 支持 PNG、JPG、WebP、PDF、TXT、DOC、DOCX。

常见错误：

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `400001` 且提示文件类型 | MIME 或扩展名不支持 | iPad 端上传前过滤 |
| `400001` 且提示 10MB | 文件过大 | 上传前压缩或提示 |
| `400001` 且提示至少提供一个 | `message` 为空且无 `files` | 禁用发送按钮 |

## 503 上游不可用

`503020` 表示学习助手上游不可用。排查顺序：

1. `assistant-orchestrator` 容器是否运行。
2. `OPENAI_API_KEY` 是否配置。
3. `AI_ASSISTANT_MODEL` 是否可用。
4. 后端环境变量 `ASSISTANT_ORCHESTRATOR_BASE_URL` 是否指向 `http://assistant-orchestrator:8002`。
5. assistant-orchestrator 日志是否有模型或网络错误。

## 需要升级给后端的信息

iPad 端提交问题时，请附上：

- 发生时间。
- 环境：Simulator 或真机。
- Base URL。
- 接口 method/path。
- HTTP 状态码和响应体。
- `traceId`、`runId`。
- 请求体脱敏版本。
- 后端日志片段或 assistant-orchestrator 日志片段。
