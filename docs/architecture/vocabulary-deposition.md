---
title: 单词沉淀架构
status: active
owner: backend
last_updated: 2026-07-11
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/service/vocabulary/
  - backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql
  - web/src/views/VocabularyView.vue
related_docs:
  - docs/architecture/dictionary-oxford.md
---

# 单词沉淀架构

## 当前阶段范围

- 当前阶段仅支持 `manual` 和 `dictionary` 两种单词沉淀来源：分别对应手动录入和词典收藏。
- PDF、AI 对话、笔记和错题尚未接入，当前不会自动沉淀到单词卡中心；这些来源属于后续接入范围。

## 资产边界

- `dictionary_*` 是共享的词典内容，提供只读查词和补全来源；不保存某个用户的收藏、查询次数或卡片内容。
- `user_dictionary_word_state` 是用户维度的词典行为状态，保存查询次数和收藏开关；它不是单词卡，也不拥有卡片版本。
- `vocabulary_card`、`vocabulary_card_source` 和 `vocabulary_card_revision` 是用户拥有的单词卡、捕获来源和不可变版本。卡片按用户、语言和规范化词形保持唯一，删除是软删除。
- `vocabulary_generation_job` 是可恢复的异步生成队列。它只负责生成卡片候选内容和任务状态，不替代词典内容或用户词典状态。

生成器向词典查询时只使用共享词典信息；不会把 `favorite` 或 `lookupCount` 写入生成内容。词典收藏和单词卡是两个独立的持久化边界。

## 数据库部署

新建数据库只执行初始迁移，创建单词卡、来源、版本、偏好和生成任务表：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql
```

只有历史旧表中的 `vocabulary_generation_job` 尚未具备租约字段时，才执行已有库租约迁移：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_add_vocabulary_generation_job_leases.sql
```

已有表随后执行精确身份迁移，将 `normalized_term` 改为 `utf8mb4_bin`。该列仍保存应用层规范化结果，但 MySQL 唯一键会区分重音不同的词形：

```powershell
mysql -u <user> -p <database> < backend/src/main/resources/db/migrate_make_vocabulary_identity_exact.sql
```

初始迁移已包含新库所需的 `lease_token`、`lease_expires_at` 和索引。新库只执行初始迁移，不得再执行租约迁移；租约迁移只用于历史旧表，成功执行后不得重复执行。迁移完成后再启动后端，避免调度器在不完整表结构上领取任务。

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
  "templateKey": "exam"
}
```

`templateKey` 只允许 `basic`、`exam` 或 `reading`，选中的 template key/version 固化到 generation job。为兼容旧客户端，无 body 时继续使用卡片当前模板。非法客户端输入统一返回稳定的 HTTP 400/`400001`；数据库、存储 JSON 和其他基础设施错误不得映射为 400。

### 列表查询

`GET /api/vocabulary/cards` 保留 `keyword`、`status`、`sourceType`、`page` 和 `size`，并支持 `sort`：`sort=recent` 按最近沉淀排序，`sort=az` 按规范词形 A-Z 排序。搜索范围包括 display/normalized/original term 和 active revision 的 `definitions`。

列表 summary 返回 `phonetic`、`coreDefinition`、`sourceCount`、`updatedAt`、`generationStatus` 和 `generationError`。来源类型与数量、最新 generation job 都按当前页批量加载。

## 前端路由

词典和卡片工作台入口是 `/app/vocabulary`，卡片详情工作区路由为 `/app/vocabulary/cards/:cardUid`。卡片列表可按来源类型筛选并切换最近/A-Z 排序，详情显示来源、最新任务状态、活跃版本和候选冲突状态。列表与详情仅在最新 job 为 `pending`/`running`（或兼容旧 `generating` 状态）时轮询，终态后停止。Inspector 只为合法 `http`/`https` 来源渲染外链；删除是软删除，再次收藏或录入可恢复。

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

cd ..
git diff --check
```

数据库迁移和端到端 API/UI 验证需要连接一个可丢弃的 MySQL 实例后执行；仅运行上述构建和测试不会证明迁移已经在本地数据库实际执行。
