# Scripts

本目录放本地手工执行的辅助脚本，不属于后端运行时代码，也不属于单元/集成测试。

## 现有脚本

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
