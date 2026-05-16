# Scripts

本目录放本地手工执行的辅助脚本，不属于后端运行时代码，也不属于单元/集成测试。

## 现有脚本

### `dev/`

本地开发入口脚本的真实实现目录。根目录保留同名 wrapper，便于继续使用旧命令：

- `setup-local.ps1`
- `start-local.bat`
- `start-nginx.bat`
- `stop.nginx.bat`
- `admin-acceptance-login.mjs`

### `dev/admin-acceptance-login.mjs`

用途：

- 本地管理员端验收时，用种子管理员账号换取真实 access token。
- 调用 `/api/admin/auth/me` 校验 token 确实具备管理员身份。
- 输出可写入浏览器 `localStorage.auth_token` 的 token 和目标地址。

前提：

- 后端已启动，并显式开启 `APP_DEV_ADMIN_LOGIN_ENABLED=true`。
- 请求必须来自本机 loopback 地址；非本机请求会得到 `404`。
- 使用 `scripts/dev/start-local.bat` 启动时会自动开启该本地验收开关。
- 数据库已执行 `backend/src/main/resources/db/seed_admin_accounts.sql`。

默认账号：

- `admin01@admin.com`
- `Kiss497.*`

最简单用法：

```powershell
node scripts/dev/admin-acceptance-login.mjs
```

指定本地端口或账号：

```powershell
node scripts/dev/admin-acceptance-login.mjs `
  --web-origin http://127.0.0.1:5173 `
  --api-base http://127.0.0.1:5173/api `
  --email admin01@admin.com `
  --password 'Kiss497.*' `
  --target-path /admin/users
```

输出中的 `token` 只用于本地验收，不要提交到文档、代码或聊天记录中。

### `maintenance/`

仓库维护和目录卫生检查脚本：

- `check-repo-hygiene.ps1`：检查根目录是否残留 pytest 缓存、`.tmp-*`、`.tmp_pip`、运行日志和构建/依赖目录。

### `score-cache-diagnose.ps1`

用途：

- 连续提交同一篇作文评分请求
- 轮询异步评分结果
- 输出 Prompt Cache 命中诊断表格

脚本路径：

- [score-cache-diagnose.ps1](/F:/personalenglishai/scripts/score-cache-diagnose.ps1)

前提：

- 本地后端已启动在 `http://127.0.0.1:18080`
- `backend/.env` 中存在有效 `JWT_SECRET`
- 当前评分接口可正常返回结果

最简单用法：

```powershell
$essay = @"
这里放整篇作文
"@

& 'F:\personalenglishai\scripts\score-cache-diagnose.ps1' -EssayText $essay -Runs 10
```

从文件读取作文：

```powershell
& 'F:\personalenglishai\scripts\score-cache-diagnose.ps1' `
  -EssayFile 'F:\path\to\essay.txt' `
  -Runs 10
```

验证带文档上下文的评分链路：

```powershell
& 'F:\personalenglishai\scripts\score-cache-diagnose.ps1' `
  -EssayFile 'F:\path\to\essay.txt' `
  -DocumentId 'doc_xxx' `
  -Runs 10
```

主要输出列：

- `run`
- `status`
- `input_tokens`
- `cached_tokens`
- `cache_hit_rate`
- `prompt_cache_key`
- `instructions_hash_12`
- `cached_prefix_hash_12`
- `essay_hash_12`

结果判断：

- `prompt_cache_key`、`instructions_hash_12`、`cached_prefix_hash_12` 一致：
  说明稳定前缀没有变化
- `essay_hash_12` 一致：
  说明作文正文也没有变化
- `cached_tokens > 0`：
  说明本次命中缓存
- `cached_tokens = 0`：
  说明本次为 miss，不代表脚本或前缀一定有问题

补充说明：

- `cache_hit_rate` 不是“全文重复率”
- 当前评分场景里，`60% - 75%` 一般已经说明固定前缀命中健康
