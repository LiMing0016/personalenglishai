---
title: 单词沉淀架构
status: active
owner: backend
last_updated: 2026-07-13
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/
  - backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql
  - backend/src/main/resources/db/migrate_add_vocabulary_generation_job_leases.sql
  - backend/src/main/resources/db/migrate_add_vocabulary_themes_and_markdown_cards.sql
  - backend/src/main/resources/db/migrate_add_vocabulary_review_semantics.sql
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyThemeService.java
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/VocabularyCardGenerator.java
  - web/src/views/VocabularyView.vue
related_docs:
  - docs/architecture/dictionary-oxford.md
  - docs/ai/vocabulary-theme-prompts.md
---

# 单词沉淀架构

## 当前结论

单词卡使用“统一核心 JSON + 主题扩展 Markdown”的版本化模型。词典事实、卡片身份、主题定义和生成任务各自保持独立边界；主题编辑只追加版本，不改写历史卡片，Markdown 失败也不会丢弃已经验证的核心词典数据。

## 当前阶段范围

- 当前阶段仅支持 `manual` 和 `dictionary` 两种单词沉淀来源：分别对应手动录入和词典收藏。
- PDF、AI 对话、笔记和错题尚未接入，当前不会自动沉淀到单词卡中心；这些来源属于后续接入范围。

## 资产边界

- `dictionary_*` 是共享的词典内容，提供只读查词和补全来源；不保存某个用户的收藏、查询次数或卡片内容。
- `user_dictionary_word_state` 是用户维度的词典行为状态，保存查询次数和收藏开关；它不是单词卡，也不拥有卡片版本。
- `vocabulary_card`、`vocabulary_card_source` 和 `vocabulary_card_revision` 是用户拥有的单词卡、捕获来源和不可变版本。卡片按用户、语言和规范化词形保持唯一，删除是软删除。
- `vocabulary_generation_job` 是可恢复的异步生成队列。它只负责生成卡片候选内容和任务状态，不替代词典内容或用户词典状态。
- `vocabulary_theme` 保存系统或用户主题的当前元数据，`vocabulary_theme_revision` 保存不可变用途和 Prompt 策略快照，`user_vocabulary_theme_recent` 只保存用户最近使用关系。
- `vocabulary_card_revision.core_json` 保存可查询、可验证的核心词典数据，`content_markdown` 保存主题扩展内容，`content_format_version` 决定响应适配方式。兼容字段 `content_json` 在迁移期继续保留。

生成器向词典查询时只使用共享词典信息；不会把 `favorite` 或 `lookupCount` 写入生成内容。词典收藏和单词卡是两个独立的持久化边界。

## 数据库部署

新建数据库先执行初始迁移，创建单词卡、来源、版本、偏好和生成任务表：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql
```

新库随后执行主题与 Markdown 卡片迁移，不执行历史库专用的 review-semantics 增量。初始迁移已经包含 `conflict_candidate_revision_uid`、`generation_outcome` 和 `warning`：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_add_vocabulary_themes_and_markdown_cards.sql
```

历史旧表升级或租约迁移中断后，执行已有库租约迁移：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_add_vocabulary_generation_job_leases.sql
```

已有表随后执行精确身份迁移，将 `normalized_term` 改为 `utf8mb4_bin`。该列仍保存应用层规范化结果，但 MySQL 唯一键会区分重音不同的词形：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_make_vocabulary_identity_exact.sql
```

基础表存在后执行主题与 Markdown 卡片迁移。该迁移创建三张主题表，写入 Basic、Exam、Reading 系统主题，并为卡片、revision、任务和用户偏好补充主题字段：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_add_vocabulary_themes_and_markdown_cards.sql
```

历史库最后执行审核语义增量，为已有表补充显式冲突候选和生成结果字段：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_add_vocabulary_review_semantics.sql
```

新库部署先执行 `migrate_create_vocabulary_deposition_tables.sql`，再执行 `migrate_add_vocabulary_themes_and_markdown_cards.sql`；历史库升级顺序是租约迁移、精确身份迁移、主题迁移、审核语义增量。新库不得执行 `migrate_add_vocabulary_review_semantics.sql`，历史库不得省略该增量。

迁移后必须从当前 `DATABASE()` 验证：`vocabulary_theme`、`vocabulary_theme_revision`、`user_vocabulary_theme_recent` 共 3 张表；`vocabulary_card_revision` 必须同时具备 `theme_uid`、`theme_version`、`core_json`、`content_markdown`、`content_format_version` 共 5 列。验证只能在明确创建的 disposable schema 中执行，清理前再次精确核对 schema 名称，不得连接开发业务库后执行 `DROP DATABASE`。

初始迁移已包含新库所需的 `lease_token`、`lease_expires_at` 和索引，因此新库无需额外执行租约迁移；租约迁移只用于历史旧表或恢复中断升级。该脚本通过 `information_schema` 独立检查两列和 `idx_vocabulary_job_lease`，可重复执行；已存在的结构不会重复创建，缺失动作会继续完成，最后对租约到期时间为空的 `running` 任务执行幂等回填。新库仍需继续执行后续主题迁移。迁移完成后再启动后端，避免调度器在不完整表结构上领取任务。

## 主题所有权与版本

- `vocabulary_theme.owner_type` 区分 `system` 和 `user`。系统主题只读，由后端注册表维护，UID 固定为 `theme_system_basic`、`theme_system_exam`、`theme_system_reading`；用户只能复制，不能编辑或删除系统主题。
- 用户只能管理自己的主题；用户主题属于单一 `user_id`。创建、复制和编辑都会写入新的 `vocabulary_theme_revision`；编辑推进 `current_version`，不会原地覆盖旧 revision。
- 捕获和 generation job 固化 `theme_uid + theme_version`。旧卡继续冻结在旧主题版本；只有用户确认“使用最新主题版本”后，重新生成任务才引用当前版本。
- 默认主题存于 `user_vocabulary_preference.default_theme_uid`，最近使用主题存于 `user_vocabulary_theme_recent`。停用或删除主题不会删除历史卡片的主题快照。

## 核心 JSON 与 Markdown

核心 JSON 固定 `schemaVersion`、`term`、`phonetics` 和 `senses`，保存核心事实，用于列表摘要、搜索和后续结构化学习。生成器先读取共享词典，不读取 `favorite`、`lookupCount` 等用户状态；词典缺少音标和释义时才调用结构化 AI fallback。无论来源如何，最终 core 都必须保持卡片规范词形并通过 schema 校验。

主题 Markdown 只承载扩展内容，包括例句、学习提示、考试侧重点或语境解释，不承担单词身份和核心释义。输出为空、超过 20,000 字符或包含原始 HTML 时视为失败；保存和缓存前均执行相同校验。详细 Prompt、安全分隔符、词典优先级和日志字段见[主题化单词卡 Prompt](../ai/vocabulary-theme-prompts.md)。

当 core 有效而 Markdown 生成失败时，仍追加包含 `core_json` 的 revision，卡片状态为 `needs_review`，所以 core_json 仍可见；前端显示核心内容和“主题内容待完善”，并允许重新生成。技术异常原文不直接展示给用户。

Legacy 模板到系统主题的固定映射是：

- `basic` -> `theme_system_basic`
- `exam` -> `theme_system_exam`
- `reading` -> `theme_system_reading`

## 生成任务与调度器

后端的 `VocabularyGenerationScheduler` 在每轮开始时处理过期的 `running` 租约，再领取 `pending` 任务。正常任务会按 `pending`、`running`、`succeeded` 或可重试/终态 `failed` 流转；租约令牌用于隔离过期工作者的迟到写入。

达到最大尝试次数的过期租约使用同一条 MySQL 多表更新：job 原子进入 `failed`，同时将没有 active revision 且仍为 `generating` 的 card 置为 `failed`。已有 active revision 的卡片保留可用内容，最新 job 状态单独暴露给列表和详情轮询。

默认配置在 `backend/src/main/resources/application.yml`：

```yaml
vocabulary:
  generation:
    scheduler:
      enabled: ${VOCABULARY_GENERATION_SCHEDULER_ENABLED:true}
      fixed-delay-ms: ${VOCABULARY_GENERATION_SCHEDULER_FIXED_DELAY_MS:5000}
      batch-size: ${VOCABULARY_GENERATION_SCHEDULER_BATCH_SIZE:5}
      lease-ms: ${VOCABULARY_GENERATION_SCHEDULER_LEASE_MS:300000}
```

本地启用时设置 `vocabulary.generation.scheduler.enabled=true`（或环境变量 `VOCABULARY_GENERATION_SCHEDULER_ENABLED=true`）。`lease-ms` 必须大于一次外部生成的最大端到端时间；需要暂停领取任务时仅将该开关设为 `false`，不要删除队列数据。

## 写入与版本契约

### 捕获与来源合并

`POST /api/vocabulary/captures` 接收用户捕获。调用方为同一次重试复用同一个 `clientRequestId`，以保证幂等；新请求会按规范化词形定位已有卡片，并把新来源写入 `vocabulary_card_source`。同一词形不会生成第二张卡，响应动作为 `source_merged`。

批量捕获只把已识别的逐词校验拒绝转换成 item `rejected`。数据库和基础设施异常必须向上抛出，由请求返回 5xx；前端在 HTTP 失败或响应包含 `rejected` 时保留输入草稿和原 `clientRequestId`，供用户修正后重试。

词典详情中的收藏仍先写入 `user_dictionary_word_state`。收藏会额外以 `dictionary` 来源捕获到卡片；取消收藏只更新词典收藏状态，不删除已经存在的单词卡。对已软删除的同一词再次捕获会恢复原卡片和原 `cardUid`，而不是创建新卡。

### 编辑与冲突

编辑使用 `PUT /api/vocabulary/cards/{cardUid}`，请求必须携带当前 `baseRevisionUid`。成功编辑会创建新的用户版本并推进活跃版本；过期的 `baseRevisionUid` 返回版本冲突错误码 `409030`。

过期用户编辑仍是 append-only candidate revision。事务内先写 revision、尝试 guarded activation 并标记冲突候选；该事务正常提交后，外层再读取最新 card/revisions 构造 `409030`，因此构造冲突响应不会回滚用户候选版本。

生成候选或过期用户编辑与当前版本发生冲突时，使用 `POST /api/vocabulary/cards/{cardUid}/conflicts/{revisionUid}/resolve` 解决。只接受 revisions history 推导出的当前候选。`keep_current`、`use_ai` 和 `merge_fields` 都会追加新的 `system_merge` revision，再以当前 active revision 做 guard 激活；`term` 始终由 card 的规范词形覆盖，不能通过候选内容或合并字段修改。

### 重新生成

`POST /api/vocabulary/cards/{cardUid}/regenerate` 接受可选 JSON body：

```json
{
  "themeUid": "theme_system_exam",
  "useLatestThemeVersion": true
}
```

主题化客户端发送 `themeUid`，并在用户确认后发送 `useLatestThemeVersion: true`。后端解析可见且启用的主题当前版本，并把主题 UID/version 与兼容 template key/version 一起固化到 generation job。为兼容旧客户端，仍接受 `templateKey` 为 `basic`、`exam` 或 `reading`；无 body 时继续使用卡片当前主题或 legacy 模板。非法客户端输入统一返回稳定的 HTTP 400/`400001`；数据库、存储 JSON 和其他基础设施错误不得映射为 400。

### 列表查询

`GET /api/vocabulary/cards` 保留 `keyword`、`status`、`sourceType`、`page` 和 `size`，并支持 `sort`：`sort=recent` 按最近沉淀排序，`sort=az` 按规范词形 A-Z 排序。搜索范围包括 display/normalized/original term 和 active revision 的 `definitions`。

列表 summary 返回 `phonetic`、`coreDefinition`、`sourceCount`、`updatedAt`、`generationStatus` 和 `generationError`。来源类型与数量、最新 generation job 都按当前页批量加载。

## 前端路由

词典和卡片工作台入口是 `/app/vocabulary`，卡片详情工作区路由为 `/app/vocabulary/cards/:cardUid`。卡片列表可按来源类型筛选并切换最近/A-Z 排序，详情显示来源、最新任务状态、活跃版本和候选冲突状态。列表与详情仅在最新 job 为 `pending`/`running`（或兼容旧 `generating` 状态）时轮询，终态后停止。Inspector 只为合法 `http`/`https` 来源渲染外链；删除是软删除，再次收藏或录入可恢复。

主题库入口是 `/app/vocabulary/themes`。捕获区的主题 shelf 最多展示默认主题和最近使用主题；批量捕获显式发送本次选择的 `themeUid`。详情优先渲染新格式 core + Markdown；legacy `basic`、`exam`、`reading` 通过兼容适配器投影 core，仍可读取，并可选择主题重新生成成新格式。

## 失败模式与恢复

| 故障 | 持久化结果 | 用户表现 | 恢复方式 |
| --- | --- | --- | --- |
| 词典查询异常 | job 保留失败码，不写无效 revision | 生成未完成 | 按 retryable 和最大 3 次规则重试 |
| 词典内容不足且 core fallback 失败 | 不激活新 revision | 生成未完成 | 修复模型/词典后重试 |
| core 有效、Markdown 失败 | 保存 partial revision，状态 `needs_review` | 核心内容可见，主题内容待完善 | 重新生成或人工编辑 Markdown |
| base revision 已变化 | AI revision 保留为候选 | 显示版本冲突 | `keep_current`、`use_ai` 或 `merge_fields` |
| worker lease 丢失 | 迟到结果不激活 | 当前卡不受影响 | 由持有新 lease 的 worker 完成 |

## 发布与回滚

发布时先迁移数据库，再发布后端，最后发布主题库、主题 shelf 和新详情 UI。迁移完成但前端尚未发布时，旧客户端仍通过 `content_json` 和 legacy template 字段读取兼容内容。

回滚不删除主题表或新格式 revision，也不批量覆盖 `theme_uid`、`core_json` 或 `content_markdown`。安全回滚顺序是：暂停 generation scheduler，回退 Web；确认旧 API 兼容投影可读后再回退 Backend。新格式内容在旧 UI 无法编辑时保持只读，不得丢弃。恢复新版后重新启用 scheduler，并抽查 ready、needs_review、legacy 三类卡片。

## 验证

文档契约测试从后端模块运行：

```powershell
cd backend
.\mvnw.cmd -q -Dtest=VocabularyDepositionDocsTest test
```

提交前的代码级验证：

```powershell
cd backend
.\mvnw.cmd -q test

cd ..\web
npx tsx --test tests/vocabularyCaptureTerms.test.ts tests/vocabularyApiContract.test.ts tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyCardInspector.test.ts
npm run build
$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:5177'
$env:E2E_MOCK_AUTH='1'
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium

cd ..
git diff --check
```

数据库迁移和端到端 API/UI 验证需要连接一个可丢弃的 MySQL 实例后执行；仅运行上述构建和测试不会证明迁移已经在本地数据库实际执行。
