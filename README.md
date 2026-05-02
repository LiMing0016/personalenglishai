# Personal English AI

Personal English AI 是一个面向英语学习与写作训练的 Web 应用。当前项目已经从早期的登录/验证码 Demo 演进为前后端分离的学习工作台，核心包括写作编辑与评分、学习助手对话、个人中心、订阅与能力画像，以及 Python 侧 AI 编排服务。

## 当前能力

| 模块 | 说明 |
|------|------|
| 写作工作台 | 支持自由写作、考试写作、富文本编辑、题目输入、作文评分、语法检查、润色、改写、翻译和历史作文管理 |
| 学习助手 | `/app/assistant` 提供类 ChatGPT 的对话体验，支持新建对话、历史搜索、文件夹整理、置顶、归档、分享、删除和移动到文件夹 |
| 全局应用侧栏 | 应用页统一使用左侧窄栏导航，覆盖写作、学习助手、单词、听力、口语和个人中心入口 |
| 个人中心 | `/app/me` 提供综合能力、我的作文、能力雷达、订阅、邀请激励、账号设置和学段切换 |
| 鉴权与安全 | 支持 JWT 登录态、邮箱/手机号注册登录、401/403 边界处理、滑动拼图验证码 |
| AI 服务 | 后端负责评分、语法、Prompt 组装、上下文管理和 AI 调用；Python orchestrator 负责学习助手 agent 编排 |
| 持久化 | MySQL 存储用户、作文、评分、学习助手文件夹/对话/消息/分享；Redis 用于任务状态、缓存和 sidecar 状态 |

## 技术栈

| 层 | 技术 |
|----|------|
| Web | Vue 3, TypeScript, Vite, Vue Router, Pinia, TanStack Query, TipTap, Axios, ECharts |
| Backend | Spring Boot 3.2, Java 17, MyBatis, MySQL, Redis, JWT, Maven |
| Python AI | FastAPI, OpenAI Agents SDK, Uvicorn |
| Deploy | Docker, Docker Compose, Nginx |

## 仓库结构

```text
.
├── web/                         # Vue 前端应用
├── backend/                     # Spring Boot 后端服务
├── python/
│   ├── ai_orchestrator/         # 学习助手 agent 编排服务
│   └── context_sidecar/         # 遗留上下文 sidecar，主要做兼容
├── docs/                        # 产品、接口、评分、AI 与部署文档
├── deploy/                      # Nginx 等部署配置
├── scripts/                     # 辅助脚本
├── docker-compose.yml           # 后端、Redis、Python 服务编排
├── docker-compose.nginx.yml     # Nginx 反向代理编排
├── setup-local.ps1              # Windows 本地环境初始化
└── start-local.bat              # Windows 本地启动脚本
```

## 关键路由

| 路由 | 说明 |
|------|------|
| `/` | 公共首页 |
| `/login` | 登录 |
| `/register` | 注册 |
| `/app` | 应用首页 |
| `/app/writing` | 写作工作台 |
| `/app/assistant` | 学习助手 |
| `/app/vocabulary` | 单词 |
| `/app/listening` | 听力 |
| `/app/speaking` | 口语 |
| `/app/me` | 个人中心 |
| `/assistant/share/:shareToken` | 学习助手公开分享页 |
| `/admin/*` | 管理后台 |

## 学习助手数据模型

学习助手的文件夹与历史对话应以数据库为准，前端只做展示和乐观更新。

| 表 | 用途 |
|----|------|
| `assistant_project` | 学习助手文件夹。UI 文案使用“文件夹”，历史表名仍保留 project |
| `assistant_conversation` | 对话摘要、标题、置顶、归档、软删除和 `project_id` 归属 |
| `assistant_message` | 对话消息明细 |
| `assistant_share` | 分享快照 |

当前推荐规则：

- “最近”显示未归入文件夹、未归档、未删除的对话。
- “文件夹”显示当前用户所有文件夹，空文件夹也应该展示。
- 移动对话只更新 `assistant_conversation.project_id`，不复制消息。
- 删除文件夹不应默认删除对话，优先将对话移出文件夹或让用户二次确认。

## 本地开发

### 前置要求

- Java 17
- Node.js 18+
- npm 9+
- Python 3.12
- MySQL 8 或可用的远程 MySQL
- Redis 7（部分链路需要）

### 一键准备 Windows 本地环境

```powershell
.\setup-local.ps1
```

只检查环境、不安装依赖：

```powershell
.\setup-local.ps1 -CheckOnly
```

### 一键启动 Windows 本地服务

```bat
start-local.bat
```

默认端口：

| 服务 | 默认地址 |
|------|----------|
| Web | `http://localhost:3300` |
| Backend | `http://localhost:18081` |
| Python orchestrator | `http://127.0.0.1:8011` |

端口可通过 `local-ports.env` 调整。脚本会在端口被占用时提示更换端口、结束占用进程或取消启动。

### 手动启动

前端：

```bash
cd web
npm install
npm run dev
```

Vite 默认开发端口为 `3000`，接口代理目标来自 `VITE_API_BASE_URL`。

后端：

```bash
cd backend
./mvnw.cmd spring-boot:run
```

Python orchestrator：

```powershell
py -3.12 -m venv python\ai_orchestrator\.venv
python\ai_orchestrator\.venv\Scripts\python.exe -m pip install -r python\ai_orchestrator\requirements.txt
python\ai_orchestrator\.venv\Scripts\python.exe -m uvicorn python.ai_orchestrator.app:app --host 127.0.0.1 --port 8011 --ws none
```

## 环境变量

常用配置文件：

- 根目录 `.env`：Docker Compose 与本地公共配置
- `backend/.env`：后端本地配置
- `web/.env`：前端 Vite 配置
- `local-ports.env`：本地启动脚本端口配置

常用变量：

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE_URL` | 前端请求后端 API 的基础地址 |
| `VITE_ASSISTANT_API_BASE_URL` | 前端请求 Python 助手服务的基础地址 |
| `SPRING_DATASOURCE_URL` | 后端 MySQL JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | 数据库账号密码 |
| `JWT_SECRET` | JWT 密钥，生产环境必须使用足够长的随机值 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 |
| `OPENAI_API_KEY` | Python orchestrator 调用 OpenAI 时使用 |
| `AI_ASSISTANT_MODEL` | 学习助手模型，默认见 `docker-compose.yml` |

## 验证命令

前端：

```bash
cd web
npm run build
```

当前助手侧栏相关源码测试：

```bash
cd web
node tests/appRailChrome.test.ts
node tests/assistantSidebarCollapse.test.ts
node tests/assistantPageChrome.test.ts
node tests/assistantConversationMenu.test.ts
node tests/assistantRouting.test.ts
```

后端：

```bash
cd backend
.\mvnw.cmd test
```

本地启动前检查：

```bat
start-local.bat --check
```

## 部署

基础服务编排：

```bash
docker compose up -d
```

包含 Nginx 反向代理：

```bash
docker compose -f docker-compose.yml -f docker-compose.nginx.yml up -d
```

更多生产部署说明见 [README_DEPLOY.md](README_DEPLOY.md)。

## 重要设计约定

- 前端应用页统一使用左侧应用栏，不再依赖顶部导航作为主入口。
- 写作页状态复杂，修改时优先保持 Pinia / TanStack Query / TipTap 的既有边界。
- 学习助手文件夹是 `assistant_project` 的产品化呈现，短期内不为了文案重命名数据库表。
- 后端保持 `controller -> service -> mapper -> dto/entity/config` 分层，controller 不写业务编排。
- Python 新 agent 能力默认进入 `python/ai_orchestrator/`，不要继续向 `context_sidecar/` 扩展新功能。
- 涉及接口、数据库、部署、状态流或 agent 编排变化时，需要同步更新文档。

## 参考文档

- [web/AGENTS.md](web/AGENTS.md)：前端开发约束
- [backend/AGENTS.md](backend/AGENTS.md)：后端开发约束
- [python/AGENTS.md](python/AGENTS.md)：Python agent 开发约束
- [README_DEPLOY.md](README_DEPLOY.md)：部署说明
