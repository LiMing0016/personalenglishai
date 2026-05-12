# 本地脚本说明

本文解释仓库里几个 `.bat` / `.ps1` 脚本分别做什么、什么时候用、运行前需要准备什么。

这些脚本本质上只是把你原本要在命令行里手动输入的一串命令保存下来，方便重复执行。它们不会改变业务代码逻辑。

## 0. 本地端口配置

如果同一台电脑里放了多个本项目副本，最容易冲突的是端口号。现在脚本支持在每个项目目录里单独放一个本地配置文件：

```text
local-ports.env
```

第一次使用时，复制模板：

```powershell
Copy-Item .\local-ports.env.example .\local-ports.env
```

然后按当前项目文件夹修改 `PORT_OFFSET`。脚本会用“基准端口 + 偏移量”推导各服务端口，例如第一个项目：

```env
PORT_OFFSET=0

BACKEND_BASE_PORT=18080
WEB_BASE_PORT=3300
PYTHON_BASE_PORT=8011
NGINX_BASE_PORT=8080

PYTHON_HOST=127.0.0.1

NGINX_DIR=D:\nginx-1.28.1
PAUSE_AT_END=0
```

第二个项目只需要改偏移量：

```env
PORT_OFFSET=100

BACKEND_BASE_PORT=18080
WEB_BASE_PORT=3300
PYTHON_BASE_PORT=8011
NGINX_BASE_PORT=8080

PYTHON_HOST=127.0.0.1

NGINX_DIR=D:\nginx-1.28.1
PAUSE_AT_END=0
```

第三个项目继续增加偏移量：

```env
PORT_OFFSET=200

BACKEND_BASE_PORT=18080
WEB_BASE_PORT=3300
PYTHON_BASE_PORT=8011
NGINX_BASE_PORT=8080

PYTHON_HOST=127.0.0.1

NGINX_DIR=D:\nginx-1.28.1
PAUSE_AT_END=0
```

注意：

- `local-ports.env` 是本机个人配置，已经被 `.gitignore` 忽略，不应该提交。
- 每行格式用 `KEY=value`，等号两边不要加空格。
- 如果没有 `local-ports.env`，脚本会先读取 `local-ports.env.example` 里的基准端口。
- `local-ports.env.example` 是模板，可以提交到 Git，方便其他电脑复制。

## 1. 脚本类型

项目里主要有两类脚本：

- `.bat`：Windows 批处理脚本，适合双击运行，常用于启动或停止本地服务。
- `.ps1`：PowerShell 脚本，适合做检查、诊断、自动化任务。

如果 Windows 提示不允许运行 `.ps1`，通常是 PowerShell 执行策略限制。可以在 PowerShell 中用下面方式临时绕过：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-encoding.ps1
```

## 2. `start-local.bat`

路径：

```text
start-local.bat
```

用途：本地开发时，一次性启动项目的主要服务。

它会打开多个新的 PowerShell 窗口，分别启动：

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 后端 Spring Boot | `http://localhost:${BACKEND_PORT}` | Java 后端 API |
| 前端 Vite | `http://localhost:${WEB_PORT}` | Vue 前端页面 |
| Python orchestrator | `http://${PYTHON_HOST}:${PYTHON_PORT}` | Python AI 编排服务 |

它启动后会提示前端访问地址：

```text
http://localhost:${WEB_PORT}
```

如果没有 `local-ports.env`，脚本会使用 `local-ports.env.example` 里的基准端口和 `PORT_OFFSET=0`：

```text
BACKEND_BASE_PORT=18080
WEB_BASE_PORT=3300
PYTHON_HOST=127.0.0.1
PYTHON_BASE_PORT=8011
```

启动前端时，脚本会把 `VITE_API_BASE_URL` 设置成当前后端地址。这样前端页面和 Vite `/api` 代理都会打到当前项目自己的后端端口。

启动后端和前端时，脚本也会把学习助手 Python 地址同步成当前 `PYTHON_HOST + PYTHON_PORT`：

```text
ASSISTANT_ORCHESTRATOR_BASE_URL=http://${PYTHON_HOST}:${PYTHON_PORT}
VITE_ASSISTANT_API_BASE_URL=http://${PYTHON_HOST}:${PYTHON_PORT}
AI_CONTEXT_CONVERSATION_PYTHON_BASE_URL=http://${PYTHON_HOST}:${PYTHON_PORT}
```

这样自动换 Python 端口后，学习助手不会继续请求旧的 `8002`、`8011` 或 `8091`。

### 它启动前会检查什么

脚本会检查：

- `backend/mvnw.cmd` 是否存在
- `web/package.json` 是否存在
- `web/node_modules/` 是否存在
- `python/ai_orchestrator/.venv/Scripts/python.exe` 是否存在
- `backend/.env` 是否存在

如果缺少 `web/node_modules/`，需要先运行：

```powershell
cd web
npm install
```

如果缺少 Python 虚拟环境，需要先创建 venv 并安装依赖。

### 它不会做什么

当前 `start-local.bat` 只负责启动服务，不负责首次安装依赖。

也就是说，它不会自动安装：

- Java
- Node.js
- Python
- npm 包
- Python requirements
- MySQL / Redis

所以它更适合“环境已经准备好之后的一键启动”。

### 检查模式

可以运行：

```powershell
.\start-local.bat --check
```

这只检查启动前提，不真正启动服务。

检查模式也会读取 `local-ports.env`，可以用来确认当前项目的配置文件是否能被脚本正常加载。

检查模式还会检查 `BACKEND_PORT`、`WEB_PORT`、`PYTHON_PORT` 是否已经被占用。如果某个端口已经被占用，会提示占用端口和 PID，并让你选择：

```text
C = Change to a free port and save local-ports.env
K = Kill the occupying process and keep this port
N = Cancel startup
```

建议：

- 如果你不确定 PID 是什么，优先选 `C`，让当前项目自动换端口。
- 如果你确定它是旧的项目服务窗口，才选 `K` 杀掉进程。
- 如果你想自己处理，选 `N` 取消启动。

Python orchestrator 启动时默认关闭 WebSocket 协议：

```text
--ws none
```

当前项目没有使用 WebSocket，这样可以避免 `uvicorn` 在本地加载不需要的 WebSocket 依赖。

## 3. `start-nginx.bat`

路径：

```text
start-nginx.bat
```

用途：启动或重载本机 Nginx。

当前脚本从 `local-ports.env.example` + `local-ports.env` 读取本机 Nginx 路径和端口：

如果 Nginx 不在模板里的目录，不需要改脚本，改当前项目的 `local-ports.env` 即可：

```env
NGINX_DIR=D:\nginx-1.28.1
NGINX_BASE_PORT=8080
```

它会做这些事：

1. 进入 Nginx 目录。
2. 执行 `nginx.exe -t` 检查 Nginx 配置是否正确。
3. 如果 Nginx 已经在运行，就执行 reload。
4. 如果 Nginx 没运行，就检查 `NGINX_PORT` 是否被占用。
5. 端口没被占用时启动 Nginx。
6. 启动后确认 `nginx.exe` 进程和端口监听状态。

适用场景：

- 本机用 Nginx 代理前后端。
- 本地模拟部署环境。
- 修改 Nginx 配置后重新加载。

不适用场景：

- 普通前后端本地开发。
- 没安装 Nginx 的新同事电脑。

普通开发优先用 `start-local.bat`，不用先管 Nginx。

## 4. `stop.nginx.bat`

路径：

```text
stop.nginx.bat
```

用途：停止本机 Nginx。

它同样会读取 `local-ports.env` 里的 Nginx 路径：

```env
NGINX_DIR=D:\nginx-1.28.1
```

它会先执行：

```bat
nginx.exe -s stop
```

如果 Nginx 进程仍然存在，再用 `taskkill` 强制结束。

适用场景：

- 本地 Nginx 占用了端口。
- 想关闭本地代理。
- 重启 Nginx 前先清理旧进程。

## 5. `scripts/check-encoding.ps1`

路径：

```text
scripts/check-encoding.ps1
```

用途：检查项目文本文件是否存在编码问题。

它主要检查两类问题：

- 文件是否带 UTF-8 BOM。
- 是否出现明显的中文乱码特征。

默认用法：

```powershell
.\scripts\check-encoding.ps1
```

指定检查目录：

```powershell
.\scripts\check-encoding.ps1 -Root .\backend
```

适用场景：

- 修改了大量中文文档或中文日志文案后。
- 发现控制台或网页出现中文乱码。
- 提交前做一次编码检查。

它不会自动修复文件，只会报告可疑文件。

## 6. `scripts/score-cache-diagnose.ps1`

路径：

```text
scripts/score-cache-diagnose.ps1
```

用途：诊断作文评分接口的 Prompt Cache 命中情况。

它会：

1. 读取 `backend/.env` 里的 `JWT_SECRET`。
2. 临时生成一个本地测试 JWT。
3. 连续多次调用评分提交接口。
4. 轮询评分任务结果。
5. 输出每次请求的 token 和 cache 命中情况。

最简单用法：

```powershell
$essay = @"
这里放整篇作文
"@

.\scripts\score-cache-diagnose.ps1 -EssayText $essay -Runs 10
```

从文件读取作文：

```powershell
.\scripts\score-cache-diagnose.ps1 `
  -EssayFile "F:\path\to\essay.txt" `
  -Runs 10
```

运行前提：

- 后端已经启动。
- `backend/.env` 中有有效 `JWT_SECRET`
- 评分接口可以正常调用

如果没有显式传 `-BaseUrl`，它会优先读取 `local-ports.env` 里的 `BACKEND_PORT`，自动使用：

```text
http://127.0.0.1:${BACKEND_PORT}
```

如果没有 `local-ports.env`，才会回退到旧默认值：

```text
http://127.0.0.1:18080
```

你仍然可以手动指定地址：

```powershell
.\scripts\score-cache-diagnose.ps1 `
  -BaseUrl "http://127.0.0.1:18181" `
  -EssayFile "F:\path\to\essay.txt"
```

常见输出字段：

| 字段 | 含义 |
| --- | --- |
| `input_tokens` | 本次输入 token |
| `cached_tokens` | 命中的缓存 token |
| `cache_hit_rate` | 缓存命中比例 |
| `prompt_cache_key` | Prompt cache key |
| `instructions_hash_12` | 指令部分 hash 前 12 位 |
| `cached_prefix_hash_12` | 缓存前缀 hash 前 12 位 |
| `essay_hash_12` | 作文正文 hash 前 12 位 |

适用场景：

- 调试评分接口的 prompt cache。
- 验证同一篇作文多次提交时固定前缀是否稳定。
- 分析为什么某次请求没有命中缓存。

普通用户或普通前端开发一般不需要运行它。

## 7. `backend/.env.example`

路径：

```text
backend/.env.example
```

这不是启动脚本，而是后端环境变量模板。

首次配置项目时，通常复制它：

```powershell
Copy-Item .\backend\.env.example .\backend\.env
```

然后在 `backend/.env` 里填写真实配置，例如：

- 数据库地址
- 数据库用户名和密码
- Redis 地址
- JWT_SECRET
- OpenAI / Kimi / Qwen API Key

`backend/.env` 不应该提交到 Git，因为里面可能有密钥。

## 8. `docker-compose.yml`

路径：

```text
docker-compose.yml
```

这也不是脚本，但它是一份 Docker 服务编排配置。

它定义了这些服务：

- `backend`
- `redis`
- `context-sidecar`
- `assistant-orchestrator`
- 可选 MySQL 配置目前在文件里注释掉了

当前这份更偏部署/容器环境，不是最适合新同事本地一键开发的配置。

如果要让别人“一键配置并启动本地开发环境”，建议后续新增一份：

```text
docker-compose.local.yml
```

只放本地依赖服务，例如 MySQL 和 Redis。后端、前端、Python 服务仍用本地命令启动，调试会更方便。

## 9. 推荐使用顺序

对于已经配置好环境的开发者：

```text
双击 start-local.bat
```

对于第一次拿到项目的新开发者，目前还需要手动准备：

1. 安装 Java 17。
2. 安装 Node.js。
3. 安装 Python。
4. 配置 MySQL 和 Redis。
5. 复制 `backend/.env.example` 为 `backend/.env` 并填写配置。
6. 复制 `local-ports.env.example` 为 `local-ports.env`，按本机端口占用情况修改。
7. 进入 `web` 执行 `npm install`。
8. 创建 Python venv 并安装 requirements。
9. 再运行 `start-local.bat`。

首次配置可以用 `setup-local.ps1` 自动完成第 5、6、7、8 步，并检查第 1、2、3、4 步。

## 10. `setup-local.ps1`

路径：

```text
setup-local.ps1
```

用途：首次配置当前项目文件夹，让后续可以直接用 `start-local.bat` 启动。

它会做这些事：

1. 检查本机是否能找到 Java、Node.js、npm、Python。
2. 如果缺少 `local-ports.env`，从 `local-ports.env.example` 复制一份。
3. 如果缺少 `backend/.env`，就从 `backend/.env.example` 复制一份。
4. 如果 `web/node_modules` 不存在，就在 `web` 目录执行 `npm install`。
5. 如果 Python venv 不完整，就创建 `python/ai_orchestrator/.venv`。
6. 安装 `python/ai_orchestrator/requirements.txt`。
7. 最后执行 `start-local.bat --check`，确认启动前提是否齐。

默认用法：

```powershell
.\setup-local.ps1
```

只检查、不安装：

```powershell
.\setup-local.ps1 -CheckOnly
```

指定 Python 版本：

```powershell
.\setup-local.ps1 -PythonVersion 3.12
```

跳过某些安装：

```powershell
.\setup-local.ps1 -SkipWebInstall
.\setup-local.ps1 -SkipPythonInstall
```

注意：

- 它不会安装 Java、Node.js、Python 本体，只检查这些命令是否已经在本机可用。
- 它不会覆盖已有 `backend/.env` 或 `local-ports.env`。
- Java 和 Python 本体是系统级安装，可以被多个项目文件夹共用。
- Python venv 是项目级目录，每个项目文件夹一份，避免不同项目依赖版本互相污染。
- 如果你在同一台电脑里跑 3 个项目，先分别修改各自的 `local-ports.env`，再启动。

## 11. 总结

| 文件 | 主要用途 | 面向谁 |
| --- | --- | --- |
| `local-ports.env.example` | 本地端口配置模板 | 首次配置 |
| `local-ports.env` | 本机私有端口配置，不提交 Git | 日常开发 |
| `setup-local.ps1` | 首次配置当前项目文件夹 | 首次配置 |
| `start-local.bat` | 启动本地后端、前端、Python 服务 | 日常开发 |
| `start-nginx.bat` | 启动或重载本机 Nginx | 部署/代理调试 |
| `stop.nginx.bat` | 停止本机 Nginx | 部署/代理调试 |
| `scripts/check-encoding.ps1` | 检查编码和中文乱码 | 提交前检查 |
| `scripts/score-cache-diagnose.ps1` | 诊断评分 cache 命中 | 后端/AI 调试 |
| `backend/.env.example` | 后端环境变量模板 | 首次配置 |
| `docker-compose.yml` | Docker 服务编排 | 部署/容器环境 |
