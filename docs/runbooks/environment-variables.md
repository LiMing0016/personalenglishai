# 环境变量说明

本文说明 Personal English AI 当前使用的环境变量。配置时不要靠记忆，应以本文、`.env.example`、`backend/.env.example`、`web/.env.example` 和部署平台 Secret 配置为准。

## 配置来源

| 场景 | 配置文件 / 来源 | 说明 |
| --- | --- | --- |
| 本地后端 | `backend/.env` | Spring Boot 会通过 `application.yml` 自动导入。不要提交真实文件。 |
| 本地前端 | `web/.env` | Vite 开发服务器读取。不要提交真实文件。 |
| 本地端口 | `local-ports.env` | `start-local.bat` 读取，用于覆盖本地端口。 |
| Docker Compose | 根目录 `.env` + `docker-compose.yml` | Compose 会读取根目录 `.env` 并传给容器。 |
| 生产环境 | 服务器环境变量 / CI Secret / 容器平台 Secret | 不应把真实密钥写入仓库或文档。 |

## 上线必填项

| 变量 | 示例 | 说明 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://.../personal_english_ai?...` | 后端 MySQL JDBC 地址。生产不能指向 `127.0.0.1`，除非数据库在同容器网络内并明确可达。 |
| `SPRING_DATASOURCE_USERNAME` | `app_user` | MySQL 用户名。生产建议使用最小权限账号。 |
| `SPRING_DATASOURCE_PASSWORD` | Secret | MySQL 密码。必须放 Secret。 |
| `JWT_SECRET` | Secret | JWT 签名密钥，至少 32 字节，生产必须随机生成。 |
| `APP_BASE_URL` | `https://www.personalenglishai.com` | 前端公开访问地址，用于生成邮箱验证和重置密码链接。 |
| `COOKIE_SECURE` | `true` | 生产 HTTPS 必须为 `true`，本地 HTTP 才用 `false`。 |
| `MAIL_ENABLED` | `true` | 生产邮箱注册验证应启用真实 SMTP。 |
| `MAIL_HOST` | `smtpdm.aliyun.com` | 当前推荐使用阿里云邮件推送 DirectMail SMTP。 |
| `MAIL_PORT` | `465` | SMTP SSL 端口。 |
| `MAIL_USERNAME` | Secret | DirectMail SMTP 账号。 |
| `MAIL_PASSWORD` | Secret | DirectMail SMTP 密码。 |
| `MAIL_FROM` | `noreply@personalenglishai.com` | 已验证的发信地址。必须与邮件服务商后台配置一致。 |

## 本地端口

`start-local.bat` 使用 `local-ports.env.example` + `local-ports.env` 控制本地端口，并会把前端地址注入后端 `APP_BASE_URL`。脚本本身不写死端口号，默认端口由“基准端口 + `PORT_OFFSET`”推导。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT_OFFSET` | `0` | 当前本地项目的端口偏移量。多 agent/worktree 并行时改这个值。 |
| `BACKEND_BASE_PORT` | `18080` | 本地后端基准端口。未显式设置 `BACKEND_PORT` 时，脚本会推导 `BACKEND_PORT=BACKEND_BASE_PORT+PORT_OFFSET`。 |
| `WEB_BASE_PORT` | `3300` | 本地前端基准端口。未显式设置 `WEB_PORT` 时，脚本会推导 `WEB_PORT=WEB_BASE_PORT+PORT_OFFSET`。 |
| `PYTHON_BASE_PORT` | `8011` | Python orchestrator 基准端口。未显式设置 `PYTHON_PORT` 时，脚本会推导 `PYTHON_PORT=PYTHON_BASE_PORT+PORT_OFFSET`。 |
| `NGINX_BASE_PORT` | `8080` | 本地 Nginx 基准端口。未显式设置 `NGINX_PORT` 时，脚本会推导 `NGINX_PORT=NGINX_BASE_PORT+PORT_OFFSET`。 |
| `BACKEND_PORT` | 可选 | 显式指定本地后端端口。脚本会传入 `SERVER_PORT`。 |
| `WEB_PORT` | 可选 | 显式指定本地前端端口。脚本会传入 Vite `--port`，也会生成本地 `APP_BASE_URL`。 |
| `PYTHON_HOST` | `127.0.0.1` | Python orchestrator 监听地址。 |
| `PYTHON_PORT` | 可选 | 显式指定 Python orchestrator 端口。 |
| `NGINX_DIR` | 本机路径 | 本地 Nginx 路径。 |
| `NGINX_PORT` | 可选 | 显式指定本地 Nginx 端口。 |
| `PAUSE_AT_END` | `0` | 本地脚本是否暂停。 |

手动启动 Vite 时默认端口是 `3000`；使用 `start-local.bat` 时默认端口是 `3300`。因此本地手动启动与脚本启动时，`APP_BASE_URL` 可能不同。

## 后端运行时

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` / `prod` | Spring profile。生产建议 `prod`。 |
| `SERVER_PORT` | `18080` | 后端监听端口。`start-local.bat` 会覆盖为 `BACKEND_PORT`。 |
| `COOKIE_SECURE` | `false` | refresh cookie 是否仅 HTTPS 发送。生产必须为 `true`。 |

## 数据库

后端实际读取 Spring 标准变量：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | 是 | JDBC 地址。 |
| `SPRING_DATASOURCE_USERNAME` | 是 | 数据库用户名。 |
| `SPRING_DATASOURCE_PASSWORD` | 是 | 数据库密码。 |

以下变量主要用于 Docker Compose 或兼容脚本：

| 变量 | 说明 |
| --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 数据库主机、端口、库名。 |
| `DB_USER` / `DB_PASSWORD` | 数据库账号密码。 |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` | 使用 Compose 内置 MySQL 容器时的初始化变量。 |

## JWT

| 变量 | 推荐值 | 说明 |
| --- | --- | --- |
| `JWT_SECRET` | 随机 32 字节以上 | 生产必须是强随机值。可用 `openssl rand -hex 32` 生成。 |
| `JWT_ACCESS_TOKEN_SECONDS` | `1800` | Access token 有效期，默认 30 分钟。 |
| `JWT_REFRESH_TOKEN_SECONDS` | `259200` | Refresh token 有效期，默认 3 天。 |

不要再新增或依赖 `JWT_EXPIRE_SECONDS`。当前后端实际读取的是 `JWT_ACCESS_TOKEN_SECONDS` 和 `JWT_REFRESH_TOKEN_SECONDS`。

## 邮箱注册与密码重置

邮箱验证和重置密码链接由后端根据 `APP_BASE_URL` 生成。

### 当前推荐：阿里云邮件推送 DirectMail

| 变量 | 示例 | 说明 |
| --- | --- | --- |
| `APP_BASE_URL` | `https://www.personalenglishai.com` | 验证邮件中的前端域名。 |
| `MAIL_ENABLED` | `true` | 生产启用真实发信。本地可设为 `false` 输出日志邮件。 |
| `MAIL_HOST` | `smtpdm.aliyun.com` | DirectMail SMTP 地址。 |
| `MAIL_PORT` | `465` | SSL SMTP 端口。阿里云 ECS 不建议使用 25 端口。 |
| `MAIL_USERNAME` | Secret | DirectMail SMTP 账号，不是网页登录账号。 |
| `MAIL_PASSWORD` | Secret | DirectMail SMTP 密码。 |
| `MAIL_FROM` | `noreply@personalenglishai.com` | DirectMail 后台已验证的发信地址。 |
| `MAIL_SSL_ENABLE` | `true` | 使用 465 时启用。 |
| `MAIL_STARTTLS_ENABLE` | `false` | 使用 465 SSL 时通常关闭。 |

### 备选：阿里邮箱 / 企业邮箱 SMTP

如果不是 DirectMail，而是普通阿里邮箱账号发信：

| 变量 | 示例 |
| --- | --- |
| `MAIL_HOST` | `smtp.qiye.aliyun.com` |
| `MAIL_PORT` | `465` |
| `MAIL_USERNAME` | `noreply@personalenglishai.com` |
| `MAIL_PASSWORD` | 三方客户端安全密码或授权码 |
| `MAIL_FROM` | `noreply@personalenglishai.com` |
| `MAIL_SSL_ENABLE` | `true` |
| `MAIL_STARTTLS_ENABLE` | `false` |

DirectMail 和阿里邮箱的账号体系不要混用。

## Redis

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址。Docker 中应为 `redis`。 |
| `REDIS_PORT` | `6379` | Redis 端口。 |
| `REDIS_DATABASE` | `0` | Redis database。 |
| `REDIS_TIMEOUT` | `3000` | Redis 超时毫秒数。 |

邮箱验证限流、异步任务和缓存类功能应优先使用 Redis，避免多实例部署时内存状态不一致。

当前邮箱验证链路使用 Redis Lua 原子限流：

| 场景 | 规则 |
| --- | --- |
| 重新发送验证邮件 | 同邮箱 60 秒冷却。 |
| 重新发送验证邮件 | 同邮箱 1 小时最多 5 次。 |
| 重新发送验证邮件 | 同 IP 1 小时最多 20 次。 |
| 注册首次发送验证邮件 | 同 IP 1 小时最多 20 次。 |

限流命中时后端返回 HTTP **429**，`code=429003`。邮箱未验证登录时返回 HTTP **403**，`code=403020`。

## 前端

| 变量 | 示例 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | 由脚本注入 | Vite 开发代理目标。`start-local.bat` 会按当前 `BACKEND_PORT` 注入，生产构建后通常由 Nginx 反代 `/api`。 |
| `VITE_ASSISTANT_API_BASE_URL` | `http://127.0.0.1:8011` | 前端访问 Python orchestrator 的地址。 |

前端运行时代码默认请求相对路径 `/api`。生产环境应通过 Nginx 将 `/api` 转发到后端。

## AI Provider

| 变量 | 说明 |
| --- | --- |
| `AI_PROVIDER_ACTIVE` | 默认 AI provider，可选 `openai` / `kimi` / `qwen`。 |
| `AI_PROVIDER_OPENAI_API_KEY` / `OPENAI_API_KEY` | OpenAI 或兼容服务 API key。 |
| `AI_PROVIDER_OPENAI_BASE_URL` / `OPENAI_BASE_URL` | OpenAI-compatible base URL。 |
| `AI_PROVIDER_OPENAI_MODEL` / `AI_MODEL` | 文本模型。 |
| `AI_PROVIDER_OPENAI_IMAGE_MODEL` | 图片模型。 |
| `AI_PROVIDER_KIMI_API_KEY` / `AI_PROVIDER_KIMI_BASE_URL` / `AI_PROVIDER_KIMI_MODEL` | Kimi 配置。 |
| `AI_PROVIDER_QWEN_API_KEY` / `QWEN_API_KEY` | 千问 API key。 |
| `AI_PROVIDER_QWEN_BASE_URL` / `QWEN_BASE_URL` | 千问 OpenAI-compatible base URL。 |
| `AI_PROVIDER_QWEN_MODEL` / `QWEN_MODEL` | 千问模型。 |
| `AI_PROVIDER_QWEN_IMAGE_MODEL` | 千问图片模型。 |

## OpenAI 客户端与代理

| 变量 | 说明 |
| --- | --- |
| `OPENAI_CONNECT_TIMEOUT_MS` | 连接超时。 |
| `OPENAI_RESPONSE_TIMEOUT_MS` | 单次响应超时。 |
| `OPENAI_OVERALL_TIMEOUT_MS` | 总体超时。 |
| `OPENAI_MAX_RETRIES` | 最大重试次数。 |
| `OPENAI_INITIAL_BACKOFF_MS` / `OPENAI_MAX_BACKOFF_MS` | 重试退避。 |
| `OPENAI_MAX_IN_MEMORY_SIZE` | WebClient 最大内存。 |
| `OPENAI_PROXY_ENABLED` | 是否启用代理。 |
| `OPENAI_PROXY_URL` / `OPENAI_PROXY_HOST` / `OPENAI_PROXY_PORT` | 代理地址。 |
| `OPENAI_CB_FAILURE_THRESHOLD` / `OPENAI_CB_WINDOW_MS` / `OPENAI_CB_RECOVERY_MS` | 熔断参数。 |

## 学习助手与 Python 服务

| 变量 | 说明 |
| --- | --- |
| `ASSISTANT_ORCHESTRATOR_BASE_URL` | 后端调用 Python orchestrator 的地址。 |
| `ASSISTANT_ORCHESTRATOR_TIMEOUT_MS` | 后端调用 Python orchestrator 超时。 |
| `AI_ASSISTANT_MODEL` | Python orchestrator 使用的助手模型。 |
| `AI_ASSISTANT_SESSION_DB_PATH` | Python orchestrator 会话数据库路径。 |
| `AI_ASSISTANT_PROMPT_SOURCE` | Python Agents SDK prompt 来源：`local` / `hybrid` / `remote`。默认 `local`。 |
| `AI_ASSISTANT_REMOTE_PROMPT_STRICT` | 远程 Prompt 配置缺失或 base URL 非 OpenAI 平台时是否直接报错，默认 `false`。 |
| `AI_PROMPT_<AGENT_KEY>_ID` | OpenAI 远程 Prompt ID，例如 `AI_PROMPT_ROUTER_ID`、`AI_PROMPT_ROUTE_DECISION_ID`。 |
| `AI_PROMPT_<AGENT_KEY>_VERSION` | OpenAI 远程 Prompt 固定版本。生产建议填写，避免远程 Prompt 静默升级。 |
| `AI_PROMPT_<AGENT_KEY>_VARIABLES_JSON` | 可选 Prompt 变量 JSON 对象，例如 `{"release":"smoke"}`。 |
| `LANGFUSE_ENABLED` | 是否启用 Python OpenAI Agents SDK 到 Langfuse 的外部追踪导出，默认 `false`。 |
| `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` | Langfuse 项目 API key。 |
| `LANGFUSE_BASE_URL` | Langfuse Cloud 区域地址或自托管地址。兼容旧变量 `LANGFUSE_HOST`。 |
| `AI_CONTEXT_CONVERSATION_PROCESSOR` | 对话上下文处理方式：`rule` / `python` / `hybrid`。 |
| `AI_CONTEXT_CONVERSATION_PYTHON_ENABLED` | 是否启用 Python 上下文处理。 |
| `AI_CONTEXT_CONVERSATION_PYTHON_BASE_URL` | Python context sidecar 地址。 |
| `AI_CONTEXT_CONVERSATION_PYTHON_TIMEOUT_MS` | Python context sidecar 超时。 |
| `AI_CONTEXT_CONVERSATION_PYTHON_FALLBACK_ENABLED` | Python 处理失败时是否降级。 |

## Prompt 与 AI 调试

| 变量 | 说明 |
| --- | --- |
| `AI_PROMPT_DEBUG` | 是否启用 Prompt 调试日志。生产慎开。 |
| `AI_PROMPT_LOG_RAW_ENABLED` | 是否记录原始 Prompt。生产通常关闭，避免隐私风险。 |
| `AI_PROMPT_LOG_RAW_MAX_CHARS` | 原始 Prompt 最大日志长度。 |
| `AI_PROMPT_CONTEXT_SELECTED_TEXT_MAX` | 选中文本上下文最大长度。 |
| `AI_PROMPT_CONTEXT_RECENT_EACH_MAX` | 最近对话单条最大长度。 |
| `AI_PROMPT_CONTEXT_RECENT_TURNS` | 最近对话轮数。 |
| `AI_PROMPT_CONTEXT_DRAFT_MAX` | 草稿上下文最大长度。 |
| `AI_REPAIR_ENABLED` | 是否启用 AI 输出修复。 |

## 语法、词典与外部服务

| 变量 | 说明 |
| --- | --- |
| `LANGUAGETOOL_ENABLED` | 是否启用 LanguageTool。 |
| `SAPLING_ENABLED` / `SAPLING_API_KEY` / `SAPLING_BASE_URL` / `SAPLING_TIMEOUT_MS` | Sapling 配置。 |
| `TEXTGEARS_ENABLED` / `TEXTGEARS_API_KEY` / `TEXTGEARS_BASE_URL` / `TEXTGEARS_TIMEOUT_MS` / `TEXTGEARS_LANGUAGE` / `TEXTGEARS_AI` | TextGears 配置。 |
| `TRINKA_ENABLED` / `TRINKA_API_KEY` / `TRINKA_BASE_URL` / `TRINKA_TIMEOUT_MS` / `TRINKA_LANGUAGE` / `TRINKA_PIPELINE` | Trinka 配置。 |
| `OXFORD_APP_ID` / `OXFORD_APP_KEY` / `OXFORD_BASE_URL` / `OXFORD_LANGUAGE` / `OXFORD_TIMEOUT_MS` | Oxford Dictionaries 配置。 |
| `QWEN_VL_MODEL` / `QWEN_TIMEOUT_MS` / `QWEN_PROMPT_DEBUG` / `QWEN_PROMPT_LOG_RAW_ENABLED` / `QWEN_PROMPT_LOG_RAW_MAX_CHARS` | 旧 Qwen 客户端配置。 |

## Docker 与镜像

| 变量 | 说明 |
| --- | --- |
| `ACR_REGISTRY` | 镜像仓库地址。 |
| `ACR_NAMESPACE` | 镜像仓库命名空间。 |
| `RESEND_API_KEY` | 预留变量，当前邮箱发送走 SMTP，不依赖它。 |
| `CONTEXT_SIDECAR_REDIS_URL` | Python context sidecar 的 Redis URL，Compose 中固定为 `redis://redis:6379/0`。 |
| `CONTEXT_SIDECAR_REDIS_TTL_SECONDS` | context sidecar Redis TTL。 |
| `CONTEXT_SIDECAR_REDIS_MAX_MESSAGES` | context sidecar 最大消息数。 |
| `CONTEXT_SIDECAR_DEFAULT_RECENT_TURNS` | context sidecar 默认最近轮数。 |

## 生产上线检查清单

- `APP_BASE_URL=https://www.personalenglishai.com`。
- `COOKIE_SECURE=true`。
- `MAIL_ENABLED=true`。
- `MAIL_HOST=smtpdm.aliyun.com`，除非确认使用的是阿里邮箱 SMTP。
- `MAIL_FROM` 是 DirectMail 已验证发信地址。
- `MAIL_USERNAME` / `MAIL_PASSWORD` 来自 DirectMail SMTP 配置，不是网页登录密码。
- `JWT_SECRET` 不是模板值，长度足够。
- `JWT_ACCESS_TOKEN_SECONDS` 和 `JWT_REFRESH_TOKEN_SECONDS` 已按安全策略设置。
- `SPRING_DATASOURCE_URL` 指向生产数据库。
- `SPRING_DATASOURCE_USERNAME` 使用生产最小权限账号。
- `REDIS_HOST` 指向生产 Redis 或 Compose 服务名。
- Redis 可用性已验证；邮箱验证重发和注册首次发信依赖 Redis 限流。
- `AI_PROMPT_LOG_RAW_ENABLED=false`，除非明确处于受控排障窗口。
- 前端域名 HTTPS 证书有效。
- Nginx 或网关已把 `/api` 转发到后端。
- 邮箱验证链路人工验收：注册、收信、未验证前登录返回 `403020`、点击验证、验证后登录成功、过期链接、重复点击、重发邮件限流。

## 密钥管理要求

- 不要提交真实 `.env`。
- 不要在文档、Issue、PR 描述、聊天记录里粘贴真实密钥。
- 生产密钥应放在 CI/CD Secret、服务器环境变量或云平台 Secret Manager。
- 轮换 `JWT_SECRET` 会导致已有 token 失效，需要安排维护窗口。
- 轮换 SMTP 密码后必须验证注册邮件和密码重置邮件。
