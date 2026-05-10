# 评分链路上下文与缓存

## 目标

- 评分请求优先依赖 `docId` 还原题目、学段、题型和字数要求。
- 评分 Prompt 拆成稳定前缀和动态尾部，尽量吃到 OpenAI Prompt Caching。
- 正式评分的动态尾部尽量只保留作文正文，避免 `aiHint`、文本统计等动态字段提前打断缓存前缀。
- `previous_response_id` 不参与正式评分，只保留给未来评分解释/追问能力。

## 当前实现

### 1. doc 级运行时状态

- Redis key：`peai:score:runtime:{docId}`
- TTL：24 小时
- 状态字段：
  - `model`
  - `promptVersion`
  - `rubricKey`
  - `studyStage`
  - `mode`
  - `taskType`
  - `taskPromptHash`
  - `renderedRubricHash`
  - `promptCacheKey`
  - `lastScoreResponseId`
  - `lastEssayHash`

### 2. Prompt 组装

- `systemPrompt` 只保留角色与最高优先级判卷原则。
- GPT 只负责：
  - `grades`
  - `analysis`
  - `priority_focus`
  - `summary`
- `errors` 不再由 GPT 生成，统一来自外部语法检查链路。
- 用户输入拆成两段：
  - 稳定前缀：评分上下文、Rubric、任务要求、文档元数据、输出 schema、few-shot
  - 动态尾部：仅作文正文
- 当前正式评分默认不把这些内容放进动态尾部：
  - `aiHint`
  - 文本统计
  - 语法错误清单
- 稳定前缀在服务内按以下 key 做本地缓存：
  - `model|promptVersion|rubricKey|studyStage|mode|taskType`

### 3. OpenAI Responses API

- 评分主链路改为 `OpenAiClient.createTextResponse(...)`
- 请求字段：
  - `instructions`
  - `input`
  - `prompt_cache_key`
  - `prompt_cache_retention`
  - `previous_response_id`
- `prompt_cache_key` 规则：
  - `score:{model}:{promptVersion}:{rubricKey}:{stage}:{mode}:{taskType}`
- `prompt_cache_retention`：
  - 已知高阶模型优先用 `24h`
  - 其他模型回退 `in_memory`

## previous_response_id 规则

- 正式评分链路默认不复用 `previous_response_id`。
- 重新评分应视为独立判卷，只复用稳定前缀的 Prompt Cache，不串上一次评分结果。
- Redis 中保留 `lastScoreResponseId` 仅用于未来的评分解释/追问能力，不参与正式评分请求。

## 观测字段

`WritingEvaluateResponse` 新增可选字段：

- `input_tokens`
- `cached_tokens`
- `payload_bytes`
- `prompt_cache_key`
- `cache_mode`

这些字段用于排查：

- 是否命中了业务侧 `docId` 上下文
- 是否命中了 OpenAI Prompt Caching
- 每次评分的请求体规模是否异常

日志额外输出：

- `cacheHitRate`
- `prefixChars`
- `essayChars`

用于快速判断：

- 缓存命中比例是否提升
- 稳定前缀是否被意外缩短或污染
- 动态尾部是否仍然只有作文正文

### 本地诊断脚本

为了验证同一篇作文重复提交时的 Prompt Cache 命中情况，仓库提供了本地诊断脚本：

- `scripts/score-cache-diagnose.ps1`

脚本行为：

- 读取 `backend/.env` 中的 `JWT_SECRET`
- 本地生成 access token
- 真实调用异步评分接口：
  - `POST /api/writing/evaluate/submit`
  - `GET /api/writing/evaluate/tasks/{requestId}`
- 连续提交多次评分请求
- 输出表格字段：
  - `run`
  - `status`
  - `input_tokens`
  - `cached_tokens`
  - `cache_hit_rate`
  - `prompt_cache_key`
  - `instructions_hash_12`
  - `cached_prefix_hash_12`
  - `essay_hash_12`

最简单用法：

```powershell
$essay = @"
这里放整篇作文
"@

& 'F:\personalenglishai\scripts\score-cache-diagnose.ps1' -EssayText $essay -Runs 10
```

也支持从文件读取：

```powershell
& 'F:\personalenglishai\scripts\score-cache-diagnose.ps1' `
  -EssayFile 'F:\path\to\essay.txt' `
  -Runs 10
```

如果要验证带文档上下文的真实页面链路，可以额外传 `documentId`：

```powershell
& 'F:\personalenglishai\scripts\score-cache-diagnose.ps1' `
  -EssayFile 'F:\path\to\essay.txt' `
  -DocumentId 'doc_xxx' `
  -Runs 10
```

结果判断：

- `prompt_cache_key` 一致
- `instructions_hash_12` 一致
- `cached_prefix_hash_12` 一致
- `essay_hash_12` 一致

以上 4 项都稳定，说明本地稳定前缀没有变化。

在此前提下：

- `cached_tokens > 0`：本次命中缓存
- `cached_tokens = 0`：本次为 miss，属于 provider 侧 best-effort 结果

对当前评分链路，`cache_hit_rate` 在 `60% - 75%` 通常已经说明固定前缀命中健康；它不是“全文重复率”，不应按 `100%` 预期理解。

## 约束

- 这套缓存只服务正式评分链路。
- 改稿后重新评分不会复用旧 `previous_response_id`，避免上一版作文污染新评分。
- 前端接口暂不破坏，`WritingEvaluateRequest` 中旧字段仍可保留作兜底；当 `docId` 存在时以后端文档元数据为准。
