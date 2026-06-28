---
status: active
owner: project
last_updated: 2026-06-12
related_code:
  - docker-compose.local.yml
  - Dockerfile
  - python/ai_orchestrator/Dockerfile
  - python/context_sidecar/Dockerfile
---

# Docker 本地开发

这份手册用于在新电脑上用 Docker 启动完整本地环境，减少 Java、Node、Python、MySQL、Redis 版本不一致带来的问题。

## 适用场景

适合：

- 新电脑首次运行项目。
- 不想分别安装 MySQL、Redis、Node、Maven 和 Python 依赖。
- 想快速验证前端、后端、Python agent、文档站能否一起启动。

不适合：

- 需要频繁单步调试 Java 或 Python 代码。
- 需要直接使用本机 IDE 的热调试器。
- 生产部署。生产部署仍使用 `docker-compose.yml` 和 `docker-compose.nginx.yml`。

## 服务组成

`docker-compose.local.yml` 会启动：

| 服务 | 容器 | 宿主机默认地址 |
| --- | --- | --- |
| Web 前端 | `peai-local-web` | `http://127.0.0.1:3300` |
| Backend | `peai-local-backend` | `http://127.0.0.1:18080` |
| MySQL | `peai-local-mysql` | `127.0.0.1:3306` |
| Redis | `peai-local-redis` | `127.0.0.1:6379` |
| Python orchestrator | `peai-local-assistant-orchestrator` | `http://127.0.0.1:8011` |
| Context sidecar | `peai-local-context-sidecar` | `http://127.0.0.1:8001` |
| 文档站 | `peai-local-docs` | `http://127.0.0.1:5174` |

## 首次启动

确保已安装 Docker Desktop，然后在仓库根目录执行：

```powershell
docker compose -f docker-compose.local.yml up --build
```

后台启动：

```powershell
docker compose -f docker-compose.local.yml up -d --build
```

查看服务状态：

```powershell
docker compose -f docker-compose.local.yml ps
```

查看日志：

```powershell
docker compose -f docker-compose.local.yml logs -f backend
docker compose -f docker-compose.local.yml logs -f web
docker compose -f docker-compose.local.yml logs -f assistant-orchestrator
```

停止服务：

```powershell
docker compose -f docker-compose.local.yml down
```

停止并删除本地数据卷：

```powershell
docker compose -f docker-compose.local.yml down -v
```

`down -v` 会删除 MySQL、Redis、Maven、Node modules 和 Python session 数据。只有在需要重置环境时使用。

## 环境变量

Compose 会自动读取根目录 `.env`。如果没有 `.env`，会使用 `docker-compose.local.yml` 里的默认值。

常用变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `WEB_PORT` | `3300` | 前端宿主机端口 |
| `BACKEND_PORT` | `18080` | 后端宿主机端口 |
| `DOCS_PORT` | `5174` | 文档站宿主机端口 |
| `ASSISTANT_ORCHESTRATOR_PORT` | `8011` | Python orchestrator 宿主机端口 |
| `CONTEXT_SIDECAR_PORT` | `8001` | Context sidecar 宿主机端口 |
| `MYSQL_PORT` | `3306` | MySQL 宿主机端口 |
| `REDIS_PORT` | `6379` | Redis 宿主机端口 |
| `MYSQL_ROOT_PASSWORD` | `peai_root_password` | 本地 MySQL root 密码 |
| `OPENAI_API_KEY` | 空 | 需要真实 AI 调用时填写 |
| `AI_ASSISTANT_MODEL` | `gpt-5.4-mini` | Python orchestrator 默认模型 |

如果本机端口被占用，可以在根目录 `.env` 中覆盖：

```properties
WEB_PORT=3310
BACKEND_PORT=18090
DOCS_PORT=5184
ASSISTANT_ORCHESTRATOR_PORT=8021
MYSQL_PORT=3307
REDIS_PORT=6380
```

## 数据库初始化

MySQL 容器首次创建数据卷时，会执行：

```text
backend/src/main/resources/db/schema.sql
```

如果后续新增了迁移 SQL，旧数据卷不会自动重新执行初始化脚本。可以选择：

1. 手动进入 MySQL 执行新增 SQL。
2. 开发环境直接重置数据卷：

```powershell
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up -d --build
```

## 和本机启动脚本的区别

| 方式 | 优点 | 代价 |
| --- | --- | --- |
| `start-local.bat` | 更适合本机 IDE 调试，端口检查更细 | 需要本机装好 Java、Node、Python、MySQL、Redis |
| `start-local.sh` | macOS/Linux 一键启动 Docker 本地环境，默认 rebuild | 依赖 Docker Desktop，IDE 单步调试不如本机方便 |
| `docker-compose.local.yml` | 新电脑更容易跑起来，依赖版本统一 | 首次拉镜像和装依赖较慢，IDE 单步调试不如本机方便 |

建议：

```text
macOS/Linux 新电脑 / 演示 / 验证环境：优先 start-local.sh。
Windows 日常深度开发 / 调试：优先 start-local.bat。
生产部署：使用 docker-compose.yml + docker-compose.nginx.yml。
```

## 常见问题

### 端口被占用

报错类似：

```text
Ports are not available
```

处理方式：

1. 关闭占用该端口的本机服务。
2. 或在 `.env` 中修改对应端口。

### 前端能打开但接口失败

检查后端是否启动完成：

```powershell
docker compose -f docker-compose.local.yml logs -f backend
```

后端第一次启动会下载 Maven 依赖，可能比前端慢。

### 数据库连接失败

检查 MySQL 健康状态：

```powershell
docker compose -f docker-compose.local.yml ps mysql
```

如果是首次启动，等待 MySQL 初始化完成后后端会自动重试。

### AI 回复失败

检查是否配置了：

```properties
OPENAI_API_KEY=...
```

没有 key 时，页面和服务可以启动，但真实模型调用会失败。
