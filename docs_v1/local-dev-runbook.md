# 本地运行与配置手册（Local Dev Runbook）

本文档描述当前仓库可见的本地运行方式与配置入口。

## 1. 环境要求

### 1.1 后端

- JDK 17
- Maven（项目自带 `mvnw`）
- MySQL 8

### 1.2 前端

- Node.js >= 18
- npm >= 9

### 1.3 可选服务

- Redis（用于 context sidecar）
- Python sidecar（`python/context_sidecar`）

## 2. 配置文件

### 2.1 后端配置

主配置：`backend/src/main/resources/application.yml`

支持从以下位置导入 `.env`：

- `./backend/.env`
- `./.env`

最关键环境变量：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`（必须至少 32 字节）
- `SERVER_PORT`（默认 18080）

可选：

- `OPENAI_API_KEY`
- `MAIL_*`
- `SMS_ENABLED`
- `AI_CONTEXT_CONVERSATION_*`

### 2.2 前端配置

示例文件：`web/env.example`

- `VITE_API_BASE_URL=http://localhost:8080`

注意：前端代码中 axios 默认 `baseURL=/api`，生产环境通常通过反向代理转发。

## 3. 本地启动（常规）

### 3.1 启动后端

在 `backend/` 下执行：

```bash
./mvnw spring-boot:run
```

### 3.2 启动前端

在 `web/` 下执行：

```bash
npm install
npm run dev
```

## 4. Docker Compose 启动

主文件：`docker-compose.yml`

当前启用服务：

- `backend`
- `redis`
- `context-sidecar`

可叠加：`docker-compose.nginx.yml`（加入 nginx 反代）

示例：

```bash
docker-compose -f docker-compose.yml -f docker-compose.nginx.yml up -d
```

## 5. 健康检查与连通性

- `GET /health` -> 健康检查
- `GET /api/ping` -> 后端连通
- `GET /api/db/ping` -> 数据库连通（受 JWT 保护）

## 6. 常见问题排查

### 6.1 登录后立刻跳回登录页

优先检查：

- access token 是否写入 `auth_token`
- refresh cookie path 是否匹配 `/api/v1/auth/`
- 后端 401 是否来自 token 过期或签名错误

### 6.2 JWT 启动失败

- 检查 `JWT_SECRET` 长度是否 >= 32 字节

### 6.3 AI command 本地调试

- `dev/local` profile 下允许 `/api/ai/command` 匿名调试
- 生产环境需按正常 JWT 访问

## 7. 文档关联

- 接口字段：`api-field-matrix-implemented.md`
- 后端现状：`backend-current-state.md`
- 前端现状：`frontend-current-state.md`
- 数据库现状：`database-current-state.md`
