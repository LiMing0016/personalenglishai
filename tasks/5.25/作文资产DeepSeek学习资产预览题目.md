# 作文资产 DeepSeek 学习资产预览 Trae 实现题目

## 背景

当前“作文资产”已经能在个人中心展示已归档作文的核心档案，包括作文正文、题目信息、评分记录、写作教练对话和 Markdown 档案。

下一步要把作文资产变成用户可复盘的学习资产：用户点击“提取学习资产”后，后端把这篇作文、评分记录和写作教练完整对话传给 DeepSeek，让 DeepSeek 提取对该用户真正有学习价值的内容，并在个人中心作文资产详情中预览。

第一版只做“预览”，不直接写入单词页、句子页或正式复习队列。等效果验证后，再设计晋级到正式词库/句库的流程。

## 总体要求

1. 在作文资产详情中新增“DeepSeek 学习资产预览”能力。
2. 提取来源包括：作文正文、题目信息、评分摘要、写作教练完整对话。
3. 提取内容包括：单词、短语、句子、语法点、写作策略。
4. 每条学习资产必须说明“为什么这条对当前用户有价值”。
5. 只允许作文 owner 查看和刷新学习资产预览。
6. 不修改生产登录逻辑，不新增任何鉴权绕过。
7. 第一版不把预览项写入正式单词库/句子库。

## 非目标

- 不做单词页或句子页入口。
- 不做复习队列。
- 不做用户手动编辑学习资产。
- 不做学习资产收藏、掌握、忽略状态。
- 不把 DeepSeek 输出无条件写入正式学习库。
- 不依赖浏览器缓存或前端临时状态作为资产来源。

---

## 题 1：后端学习资产预览数据链路

### 发给 Trae 的 Prompt

请为作文资产新增 DeepSeek 学习资产预览后端能力。要求新增数据库表保存提取运行和预览条目，新增刷新接口，接口会校验当前用户是作文 owner，然后把作文正文、评分记录、写作教练对话组装为输入调用 DeepSeek，解析 JSON 后保存并返回最新资产详情。第一版只保存 preview，不写入正式单词库或句子库。

### 小题 A：新增学习资产预览表与 Mapper

#### 题目 Prompt

请新增作文资产学习资产预览所需数据库表、实体和 Mapper。

要求：

1. 新增 `writing_learning_asset_preview_run`，保存每次 DeepSeek 提取运行。
2. 新增 `writing_learning_asset_preview_item`，保存本次提取出来的预览条目。
3. 表字段至少包含：
   - run：`run_uid`、`document_id`、`user_id`、`status`、`model`、`summary`、`result_json`、`error_message`、`input_token_count`、`output_token_count`、`item_count`、`generated_at`
   - item：`item_uid`、`run_uid`、`document_id`、`user_id`、`asset_type`、`source_type`、`display_text`、`original_text`、`recommended_text`、`meaning_zh`、`explanation`、`value_reason_for_user`、`how_to_reuse`、`review_prompt`、`source_question`、`source_excerpt`、`confidence`、`learning_value_score`、`promotion_status`
4. `asset_type` 支持：`word`、`phrase`、`sentence`、`grammar`、`writing_strategy`。
5. `source_type` 支持：`user_focus`、`coach_feedback`、`system_discovered`。
6. `promotion_status` 第一版固定为 `preview`，为后续晋级正式词库/句库预留。
7. 同步更新 `schema.sql` 和独立 migration SQL。
8. 不改已有作文归档、评分、写作教练主流程。

#### 题目难度

中等

#### 验收标准

- 本地数据库执行 migration 后不存在找不到表的问题。
- Mapper 能查询某篇作文最新一次预览运行。
- Mapper 能保存本次预览条目。
- 表通过 `document_id`、`user_id` 建立作文和用户归属。
- 不影响已有 `writing_document_asset_snapshot` 和 `writing_document_conversation_link`。

### 小题 B：新增刷新接口并调用 DeepSeek

#### 题目 Prompt

请新增刷新作文学习资产预览的后端接口。

要求：

1. 新增接口：`POST /api/writing/documents/{docId}/asset/learning-preview/refresh`。
2. 必须校验当前登录用户是该作文 owner。
3. 组装 DeepSeek 输入时包含：
   - 作文标题
   - 作文题目
   - 作文最新正文
   - 评分摘要
   - 写作教练完整对话
4. DeepSeek 输出必须要求严格 JSON，不要 Markdown。
5. 后端解析 JSON 后只保留有明确 `valueReasonForUser` 的条目。
6. 对条目按 `assetType + recommendedText/displayText` 做去重。
7. 失败时保存 `status=failed` 和 `error_message`，前端资产详情仍可打开。
8. `GET /api/writing/documents/{docId}/asset` 返回最新 `learningAssetPreview`。

#### 题目难度

偏难

#### 验收标准

- owner 可以刷新并获取学习资产预览。
- 非 owner 不能刷新或查看该作文学习资产预览。
- DeepSeek 返回单词、短语、句子、语法点、写作策略时能正确入库和返回。
- 缺少 `valueReasonForUser` 的条目不会进入预览。
- DeepSeek 调用失败时接口不破坏作文资产详情页，只展示失败状态。
- 后端测试覆盖成功提取、权限校验、失败状态。

---

## 题 2：个人中心作文资产详情展示学习资产预览

### 发给 Trae 的 Prompt

请在个人中心“作文资产”详情中新增“DeepSeek 学习资产预览”分区。用户点击按钮后调用后端刷新接口，展示 DeepSeek 从这篇作文和写作教练对话中提取出的高价值学习内容。UI 要强调“这是用户自己作文里的问题和可复用表达”，而不是普通词表。

### 小题 A：扩展前端 API 类型和刷新调用

#### 题目 Prompt

请扩展前端写作 API，支持作文学习资产预览。

要求：

1. 在 `web/src/api/writing.ts` 中新增 `WritingLearningAssetPreview` 和 `WritingLearningAssetPreviewItem` 类型。
2. 扩展 `WritingDocumentAssetResponse`，增加 `learningAssetPreview` 字段。
3. 新增 API 方法：`refreshWritingDocumentLearningAssetPreview(docId)`。
4. 请求路径使用：`/writing/documents/{docId}/asset/learning-preview/refresh`。
5. 不改登录、路由守卫或生产鉴权逻辑。

#### 题目难度

简单

#### 验收标准

- TypeScript 类型检查通过。
- `getWritingDocumentAsset` 能接收并展示 `learningAssetPreview`。
- `refreshWritingDocumentLearningAssetPreview` 调用路径正确。
- `npm run build` 通过。

### 小题 B：资产详情新增学习资产预览 UI

#### 题目 Prompt

请在 `web/src/components/personal-center/WritingAssetsSection.vue` 中新增“DeepSeek 学习资产预览”分区。

要求：

1. 在作文正文、评分记录、写作教练对话之后增加学习资产预览区块。
2. 区块包含“提取学习资产”按钮。
3. 无预览时显示空状态，引导用户点击提取。
4. 提取中按钮显示 loading 状态。
5. 提取失败时展示错误信息。
6. 提取成功后按卡片展示每条学习资产：
   - 类型：单词 / 短语 / 句子 / 语法点 / 写作策略
   - 来源：用户提问 / 教练反馈 / 系统发现
   - 推荐表达或展示文本
   - 用户原文
   - 中文含义
   - 为什么对用户有价值
   - 下次如何复用
   - 复习提示
   - 对应用户提问
   - 学习价值分和可信度
7. 右侧“资产包含”清单增加“学习资产预览”。
8. 移动端不应出现文本溢出或卡片横向撑破。

#### 题目难度

中等

#### 验收标准

- 打开 `/app/me?tab=assets` 后进入任一作文资产详情，可以看到“DeepSeek 学习资产预览”分区。
- 点击“提取学习资产”会调用后端刷新接口。
- 成功返回后能展示提取出来的资产卡片。
- 失败时不会导致整个资产详情页崩溃。
- `npm run build` 通过。
- 普通作文资产详情中的作文正文、评分记录、写作教练对话和 Markdown 档案仍正常展示。

---

## 测试计划

### 后端

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=WritingDocumentAssetServiceTest" test
.\mvnw.cmd -q test
```

### 前端

```powershell
cd web
npm run build
```

### 文档

```powershell
cd docs
npm run build
```

### 手动验收

1. 登录测试账号。
2. 进入 `/app/me?tab=assets`。
3. 打开一篇已归档作文资产详情。
4. 点击“提取学习资产”。
5. 确认页面展示 DeepSeek 提取出的单词、短语、句子、语法点或写作策略。
6. 确认每条资产能看到“为什么对用户有价值”和“下次如何复用”。
7. 确认该功能没有把预览项写入正式单词页。

## 关键文件参考

- `backend/src/main/java/com/personalenglishai/backend/service/writing/WritingDocumentAssetService.java`
- `backend/src/main/java/com/personalenglishai/backend/dto/writing/WritingDocumentAssetResponse.java`
- `backend/src/main/resources/mapper/WritingDocumentAssetMapper.xml`
- `backend/src/main/resources/db/migrate_create_writing_learning_asset_preview_tables.sql`
- `web/src/api/writing.ts`
- `web/src/components/personal-center/WritingAssetsSection.vue`
- `docs/data/writing-document-assets.md`
- `docs/product/翻译页面/作文资产DeepSeek学习价值提取预览方案.md`
