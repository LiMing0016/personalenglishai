---
title: 仓库结构规范
status: active
owner: project
last_updated: 2026-05-22
review_cycle: monthly
related_code:
  - backend/
  - web/
  - python/
  - scripts/
  - docs/
  - tasks/
related_docs:
  - docs/runbooks/repo-hygiene.md
  - docs/runbooks/local-scripts.md
---

# 仓库结构规范

## 当前结论

仓库根目录只保留长期模块和项目入口文件。运行缓存、临时探针、一次性上下文、构建产物和依赖缓存不能长期留在根目录。

当前标准结构：

```text
personalenglishai/
  backend/
  web/
  python/
  docs/
  tasks/
  scripts/
  deploy/
  tools/
  .github/
```

## 根目录长期模块

| 路径 | 职责 |
| --- | --- |
| `backend/` | Spring Boot 后端、MyBatis mapper、数据库脚本、后端测试 |
| `web/` | Vue 3 前端、前端测试、Vite 构建配置 |
| `python/` | Python agent 和 orchestrator 服务 |
| `docs/` | 当前有效项目文档、架构、API、运行手册、归档资料 |
| `tasks/` | 一次性任务题单和 Trae 题目 |
| `scripts/` | 可复用开发、诊断、维护脚本 |
| `deploy/` | 部署配置和环境模板 |
| `tools/` | 项目级辅助工具源码，不放依赖缓存 |
| `.github/` | CI/CD 和 GitHub 工作流 |

## 根目录入口文件

允许保留：

```text
README.md
AGENTS.md
CLAUDE.md
Dockerfile
docker-compose.yml
docker-compose.nginx.yml
.env.example
local-ports.env.example
setup-local.ps1
start-local.bat
start-nginx.bat
stop.nginx.bat
```

说明：

- 根目录启动脚本是兼容入口，真实实现放在 `scripts/dev/`。
- `local-ports.env` 是本机私有配置，必须忽略，不提交。
- `README_DEPLOY.md` 当前保留为旧部署入口，后续可迁入 `docs/runbooks/deploy.md`。

## scripts 目录

```text
scripts/
  dev/
    setup-local.ps1
    start-local.bat
    start-nginx.bat
    stop.nginx.bat
  maintenance/
    check-repo-hygiene.ps1
  check-encoding.ps1
  score-cache-diagnose.ps1
```

规则：

- `scripts/dev/` 放本地启动和环境初始化脚本。
- `scripts/maintenance/` 放仓库卫生、检查、批处理维护脚本。
- 根目录 wrapper 只转发到 `scripts/dev/`，保持历史命令兼容。

## docs 目录

```text
docs/
  admin/
  api/
  architecture/
  product/
  runbooks/
  testing/
  archive/
```

规则：

- 当前有效规范和长期依据放 `docs/`。
- 历史资料、OCR 中间产物、旧 mockup、源资产放 `docs/archive/`。
- 一次性任务题单不放 `docs/`，放 `tasks/`。

## tasks 目录

建议新任务使用完整日期：

```text
tasks/
  2026-05-15/
  2026-05-16/
```

已有 `tasks/5.15` 可以保留，后续新建任务优先使用 `YYYY-MM-DD`。

## tools 目录

`tools/` 只放可复用工具源码或脚本，不放依赖目录：

```text
tools/
  bcrypt/
  pdf-extraction/
```

禁止提交：

- `tools/**/node_modules/`
- `tools/**/.venv/`
- 工具运行输出缓存

## 不允许长期留在根目录的内容

```text
pytest-cache-files-*/
.pytest_cache/
.cache/
.tmp_pip/
.tmp-*/
.tmp_ctx_*.json
tmp[0-9a-zA-Z_]*/
*.log
node_modules/
dist/
```

如果需要保留源资产，迁入 `docs/archive/source-assets/` 并更新引用。

## 变更流程

新增一级目录前必须回答：

1. 是否已有目录能承载？
2. 这是长期模块、工具源码、文档、任务，还是运行产物？
3. 是否需要加入 `.gitignore`？
4. 是否需要更新 `README.md`、VitePress 导航或 runbook？
5. 是否需要加入 `scripts/maintenance/check-repo-hygiene.ps1` 检查？

## 验收

结构治理后运行：

```powershell
.\scripts\maintenance\check-repo-hygiene.ps1
git diff --check
cd docs
npm run build
```

涉及脚本迁移时额外运行：

```powershell
.\start-local.bat --check
.\setup-local.ps1 -CheckOnly
```
