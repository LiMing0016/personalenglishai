---
title: iPadOS 本地开发与 Docker
status: draft
owner: backend
last_updated: 2026-06-18
review_cycle: on-change
related_code:
  - docker-compose.local.yml
  - backend/.env.example
  - backend/src/main/resources/application-local.yml
related_docs:
  - docs/runbooks/docker-local.md
  - docs/runbooks/local-dev.md
  - docs/ios-integration/README.md
---

# iPadOS 本地开发与 Docker

## 当前结论

iPad Simulator 联调后端时，推荐使用仓库根目录的 `docker-compose.local.yml` 启动完整本地依赖。默认后端地址为 `http://127.0.0.1:18080`，iPad Simulator 可以直接访问这个地址。

## 服务和端口

| 服务 | 容器 | 宿主机端口 | 容器端口 | 用途 |
| --- | --- | --- | --- | --- |
| Backend | `peai-local-backend` | `18080` | `18080` | Spring Boot API |
| MySQL | `peai-local-mysql` | `3306` | `3306` | 本地数据库 |
| Redis | `peai-local-redis` | `6379` | `6379` | 缓存和上下文 |
| Assistant Orchestrator | `peai-local-assistant-orchestrator` | `8011` | `8002` | Python AI 编排服务 |
| Context Sidecar | `peai-local-context-sidecar` | `8001` | `8001` | 会话上下文 sidecar |
| Paddle OCR | `peai-local-paddle-ocr` | `8090` | `8090` | OCR 服务 |
| Web | `peai-local-web` | `3300` | `3000` | Web 前端 |
| Docs | `peai-local-docs` | `5174` | `5174` | VitePress 文档站 |

## 启动

在仓库根目录执行：

```bash
docker compose -f docker-compose.local.yml up -d mysql redis assistant-orchestrator context-sidecar paddle-ocr backend
```

首次启动会下载镜像、构建 Python 服务并初始化 MySQL，时间会比较长。

查看状态：

```bash
docker compose -f docker-compose.local.yml ps
```

查看后端日志：

```bash
docker compose -f docker-compose.local.yml logs -f backend
```

停止：

```bash
docker compose -f docker-compose.local.yml down
```

如果需要同时启动 Web 和文档站：

```bash
docker compose -f docker-compose.local.yml up -d
```

## 环境变量

常用本地变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BACKEND_PORT` | `18080` | 后端宿主机端口 |
| `MYSQL_PORT` | `3306` | MySQL 宿主机端口 |
| `REDIS_PORT` | `6379` | Redis 宿主机端口 |
| `ASSISTANT_ORCHESTRATOR_PORT` | `8011` | Python 编排服务宿主机端口 |
| `OPENAI_API_KEY` | 空 | AI 助手真实调用需要配置 |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI 兼容 base URL |
| `AI_ASSISTANT_MODEL` | `gpt-5.4-mini` | assistant-orchestrator 使用的模型 |
| `COOKIE_SECURE` | `false` | 本地 Cookie 不要求 HTTPS |
| `MAIL_ENABLED` | `false` | 本地默认关闭邮件发送 |

本地可在 `.env` 写入：

```dotenv
OPENAI_API_KEY=sk-...
BACKEND_PORT=18080
ASSISTANT_ORCHESTRATOR_PORT=8011
```

## 健康检查

后端健康检查：

```bash
curl -i http://127.0.0.1:18080/health
```

API ping：

```bash
curl -i http://127.0.0.1:18080/api/ping
```

Assistant orchestrator 端口检查：

```bash
curl -i http://127.0.0.1:8011/docs
```

## iPad Simulator 连接

iPad Simulator 运行在 Mac 上时，可以直接访问宿主机 loopback：

```text
http://127.0.0.1:18080
```

iPad App 本地配置建议：

```swift
let apiBaseURL = URL(string: "http://127.0.0.1:18080")!
```

注意：

- Simulator 使用 `127.0.0.1` 指向 Mac 宿主机。
- 真机 iPad 不可以使用 `127.0.0.1` 访问 Mac，需要改成 Mac 的局域网 IP，例如 `http://192.168.1.20:18080`。
- 如果使用真机，Mac 防火墙需要允许 Docker 或 Java 进程入站连接。

## iPad App 网络配置

本地 HTTP 非 HTTPS 时，iPad App 需要 App Transport Security 例外。调试配置示例：

```xml
<key>NSAppTransportSecurity</key>
<dict>
  <key>NSAllowsArbitraryLoads</key>
  <true/>
</dict>
```

更收敛的做法是只给本地联调域名或 IP 放行。生产环境必须使用 HTTPS。

## 登录后冒烟

1. 获取验证码：

```bash
curl -s http://127.0.0.1:18080/api/v1/auth/captcha
```

2. 正常登录需要先完成验证码。若本地启用了 `app.dev-admin-login.enabled=true`，可用 dev-login：

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Passw0rd123"}' \
  http://127.0.0.1:18080/api/v1/auth/dev-login
```

3. 使用返回的 token 调助手接口：

```bash
curl -i \
  -H 'Authorization: Bearer <access_token>' \
  http://127.0.0.1:18080/api/assistant/projects
```

## SSE 冒烟

```bash
curl -N \
  -H 'Authorization: Bearer <access_token>' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "clientMessageId": "ipad-smoke-1",
    "mode": "daily_explain",
    "intent": "free_chat",
    "scope": "message_only",
    "message": {"text": "Say hello in one short sentence."}
  }' \
  http://127.0.0.1:18080/api/assistant/conversations/<conversationUid>/messages/run/stream
```

通过标准：

- 响应 Header 包含 `text/event-stream`。
- 至少收到 `run.started`、`message.created`、`message.delta` 或 `message.completed`、`run.completed`。
- 后端日志无 `ASSISTANT_UPSTREAM_UNAVAILABLE`。

## 附件上传冒烟

```bash
curl -i \
  -H 'Authorization: Bearer <access_token>' \
  -F 'message=请总结这个文件' \
  -F 'files=@/path/to/sample.txt;type=text/plain' \
  http://127.0.0.1:18080/api/assistant/conversations/<conversationUid>/messages
```

通过标准：

- 合法文件返回 `code = "0"`。
- 超过 10MB 或非法类型返回 `400001`。
- iPad 端错误提示不进入无限重试。
