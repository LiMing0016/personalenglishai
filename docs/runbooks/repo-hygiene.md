---
title: 仓库目录卫生治理
status: active
owner: project
last_updated: 2026-05-22
review_cycle: monthly
related_code:
  - .gitignore
  - pyproject.toml
  - AGENTS.md
  - docs/AGENTS.md
related_docs:
  - docs/runbooks/local-dev.md
  - docs/runbooks/local-scripts.md
---

# 仓库目录卫生治理

## 适用场景

当仓库根目录出现 pytest 缓存、临时虚拟环境、运行日志、工具探针目录或多条功能线混杂时，按本手册治理。

目标是让根目录只保留长期模块和项目入口，避免运行产物污染 git 状态、影响检索和 code review。

## 根目录边界

根目录长期保留的一级目录：

```text
backend/
web/
python/
docs/
tasks/
scripts/
deploy/
.github/
```

根目录长期保留的项目入口文件：

```text
README.md
AGENTS.md
CLAUDE.md
docker-compose.yml
docker-compose.nginx.yml
Dockerfile
.env.example
local-ports.env.example
setup-local.ps1
start-local.bat
start-nginx.bat
stop.nginx.bat
```

## 不应长期留在根目录的内容

以下内容属于运行产物或本地缓存，不应提交：

```text
.pytest_cache/
pytest-cache-files-*/
.cache/
.tmp_pip/
.tmp-*/
.tmp-backend/
.tmp-sidecar/
.tmp-promptfoo-cache/
.tmp-venv-probe*/
.tmp_ctx_*.json
tmp[0-9a-zA-Z_]*/
*.log
```

说明：

- pytest 缓存通过根目录 `pyproject.toml` 固定到 `.cache/pytest/`，避免在根目录生成多条 `pytest-cache-files-*` 随机目录。
- `tmp/` 不再作为长期资产目录；历史 PDF 资料迁入 `docs/archive/source-assets/pdfs/`。
- `tmp1n4_a0rs/` 这类带随机后缀的目录按临时产物处理。
- `.tmp_ctx_*.json` 是本地测试上下文样例，不应提交。
- `backend/target/`、`node_modules/`、`dist/`、`.venv/` 等由各模块规则忽略。

## 根目录脚本

以下脚本当前是正式开发入口，保留在根目录作为兼容包装：

```text
setup-local.ps1
start-local.bat
start-nginx.bat
stop.nginx.bat
```

原因：

- `README.md`、`docs/runbooks/local-dev.md` 和 `docs/runbooks/local-scripts.md` 已明确引用它们。
- Windows 用户可以直接在根目录双击或执行。
- 脚本内部以仓库根目录为上下文读取 `local-ports.env`、`backend/`、`web/` 和 `python/`。

真实实现已迁入 `scripts/dev/`：

```text
setup-local.ps1              # root wrapper
start-local.bat              # root wrapper
scripts/dev/setup-local.ps1  # implementation
scripts/dev/start-local.bat  # implementation
scripts/dev/start-nginx.bat  # implementation
scripts/dev/stop.nginx.bat   # implementation
```

这样既能保持旧命令兼容，又能让脚本实现归档到 `scripts/`。

## 清理步骤

1. 先查看根目录：

```powershell
Get-ChildItem -Force
```

2. 确认临时目录是否被 git 跟踪：

```powershell
git ls-files pytest-cache-files-* tmp1n4_a0rs tmp3wft7pdw tmpl8i0l2ab .tmp_pip
```

3. 查看忽略规则是否生效：

```powershell
git check-ignore -v pytest-cache-files-5pwcfzvk tmp1n4_a0rs .tmp_pip
```

4. 清理前确认目标路径都在仓库根目录下：

```powershell
$root = (Resolve-Path .).Path
$targets = @(
  "pytest-cache-files-*",
  ".tmp_pip",
  ".tmp-*",
  "tmp1n4_a0rs",
  "tmp3wft7pdw",
  "tmpl8i0l2ab"
)

$targets |
  ForEach-Object { Get-ChildItem -Force -Directory -Path $_ -ErrorAction SilentlyContinue } |
  ForEach-Object {
    $path = $_.FullName
    if (-not $path.StartsWith($root)) {
      throw "Refuse to remove path outside repo: $path"
    }
    $path
  }
```

5. 确认后再删除：

```powershell
$root = (Resolve-Path .).Path
$targets = @(
  "pytest-cache-files-*",
  ".tmp_pip",
  ".tmp-*",
  "tmp1n4_a0rs",
  "tmp3wft7pdw",
  "tmpl8i0l2ab"
)

$targets |
  ForEach-Object { Get-ChildItem -Force -Directory -Path $_ -ErrorAction SilentlyContinue } |
  ForEach-Object {
    $path = $_.FullName
    if (-not $path.StartsWith($root)) {
      throw "Refuse to remove path outside repo: $path"
    }
    Remove-Item -LiteralPath $path -Recurse -Force
  }
```

如果 Windows 返回 `Access denied`，通常是 pip、Python 或调试进程仍持有目录句柄。先关闭相关终端和本地服务，再重新执行清理命令。

## 功能线治理

当 `git status` 同时出现多条功能线时，应拆分提交或分支：

```text
codex/admin-user-center
codex/admin-subscription-quota
codex/admin-seed-accounts
codex/agent-debug-console
codex/docs-admin-design
```

建议：

- 一个需求一个分支。
- 一个 PR 只解决一个业务主题。
- 任务题单放 `tasks/`，不要放 `docs/`。
- 当前有效文档放 `docs/`，临时执行记录不要加入主文档。
- 不要把缓存、日志和构建产物放入 review。

## 验收方式

清理后运行：

```powershell
git status --short
git diff --check
.\scripts\maintenance\check-repo-hygiene.ps1
```

如果修改了文档，还需运行：

```powershell
cd docs
npm run build
```

验收标准：

- 根目录不再出现 pytest 随机缓存目录。
- `git status` 不再因为临时目录报 `Permission denied`。
- `.gitignore` 覆盖新增临时产物模式。
- 文档构建通过。
