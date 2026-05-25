---
title: 作文资产归档清单
status: active
owner: data
last_updated: 2026-05-24
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/service/writing/WritingDocumentAssetService.java
  - backend/src/main/java/com/personalenglishai/backend/dto/writing/WritingDocumentAssetResponse.java
  - backend/src/main/resources/mapper/WritingDocumentAssetMapper.xml
  - backend/src/main/resources/db/migrate_create_writing_document_asset_tables.sql
  - backend/src/main/resources/db/migrate_create_writing_learning_asset_preview_tables.sql
  - web/src/components/personal-center/WritingAssetsSection.vue
related_docs:
  - ./writing-dashboard-data.md
---

# 作文资产归档清单

## 当前结论

作文归档到个人中心后，会进入“作文资产”。当前第一版资产以数据库快照为准，归档时生成一份 Markdown 档案和一份 JSON 快照。

资产归档的目标是让用户在个人中心完整复盘一篇作文，而不是只看到一条写作历史记录。归档不是删除、隐藏或冻结；已归档作文仍可继续编辑、继续评分和继续使用写作教练。

## 资产入口

用户中心“作文资产”列表展示已归档作文。列表层只展示摘要信息，用于快速识别和进入详情。

| 资产项 | 来源 | 说明 |
| --- | --- | --- |
| 作文标题 | `documents.title` | 未命名作文显示默认标题 |
| 题目摘要 | `documents.task_prompt` / 写作元数据 | 自由写作显示自由写作说明 |
| 最新分数 | `documents.latest_score` | 没有评分时显示未评分 |
| 提交次数 | `documents.submit_count` | 用于反映评分或提交次数 |
| 更新时间 | `documents.updated_at` | 用于排序和识别最近修改 |
| 归档状态 | `documents.status = 2` | 列表只展示当前仍处于归档状态的作文 |

列表提供三个主要操作：

- 查看档案：打开资产详情。
- 编辑：进入 `/app/writing/editor` 并打开对应作文。
- 取消归档：把 `documents.status` 从 `2` 恢复为 `1`，不删除历史快照。

## 资产详情

资产详情在个人中心内部展示，不新增独立一级页面。详情由后端资产接口返回，包含可直接展示的数据和后端生成的 Markdown 档案。

| 资产分区 | 包含内容 | 数据来源 |
| --- | --- | --- |
| 概览 | 作文 ID、标题、最新版本、最新分数、提交次数、归档状态 | `documents` |
| 题目信息 | 写作模式、学段、题目文本 | `documents.task_prompt`、`writing_metadata` |
| 作文正文 | 当前最新版本正文 | `document_revisions` |
| 评分记录 | 总分、等级、结构分、词汇分、语法分、表达分、错误数、评估时间 | `essay_evaluation` |
| 写作教练对话 | 已关联会话、会话标题、消息数、更新时间、用户和教练消息 | `writing_document_conversation_link`、`assistant_conversation`、`assistant_message` |
| DeepSeek 学习资产预览 | 单词、短语、句子、语法点、写作策略、用户价值说明和复用提示 | `writing_learning_asset_preview_run`、`writing_learning_asset_preview_item` |
| Markdown 档案 | 将概览、题目、正文、评分、教练对话和档案元信息整理成可读文档 | `writing_document_asset_snapshot.markdown_content` |
| 档案状态 | 生成时间、是否过期 `stale` | 快照表和当前源数据对比 |

## Markdown 档案结构

Markdown 档案使用固定章节，便于用户阅读、复制和下载。

```markdown
# 作文标题

## 作文概览

## 题目信息

## 作文正文

## 评分记录

## 写作教练对话

## 档案元信息
```

### 作文概览

包含：

- 作文 ID。
- 最新版本。
- 最新分数。
- 提交次数。
- 归档状态。

### 题目信息

包含：

- 写作模式。
- 学段。
- 题目或写作要求。

如果是自由写作且没有固定题目，显示“自由写作，无固定题目”。

### 作文正文

作文正文来自当前最新 `document_revisions` 内容。归档后继续编辑作文不会自动取消归档；再次打开资产详情时，如果正文已更新，资产会被标记为可刷新。

### 评分记录

Markdown 中展示评分摘要，避免档案正文过长。

每条评分记录包含：

- 总分。
- 等级。
- 结构分。
- 词汇分。
- 语法分。
- 错误数。
- 评估时间。

完整评分结果保存在 JSON 快照的 `resultJson` 中。

### 写作教练对话

写作教练对话优先收录已经和作文显式关联的会话。若历史会话发生在关联能力上线前，资产服务会按当前用户、`[写作教练 Copilot 请求]` 标记、作文标题和题目信息尝试找回匹配会话，并补写关联关系。

关联关系由 `writing_document_conversation_link` 保存，一篇作文可以关联多个写作教练会话。Markdown 中按会话展示用户和写作教练消息。

个人中心资产详情会在“写作教练对话”分区展示每个历史会话的下载按钮。点击按钮下载的是单段写作教练历史对话 Markdown，用户可以用 Typora、Obsidian、VS Code、Notion 等外部软件打开。这个按钮不替代作文核心档案；作文正文、题目信息、评分记录和完整 Markdown 档案仍保留在资产详情中。

如果用户消息是内部 `[写作教练 Copilot 请求]` 格式，Markdown 中只提取 `[用户本轮问题]` 之后的可读内容；完整原始消息保存在 JSON 快照中。

如果没有关联教练会话，Markdown 显示“暂无写作教练对话”。

### 档案元信息

包含：

- 快照生成时间。
- 评分记录数。
- 教练会话数。

## JSON 快照

JSON 快照保存在 `writing_document_asset_snapshot.snapshot_json`。它是面向系统的结构化档案，保留比 Markdown 更完整的数据。

| 字段 | 内容 |
| --- | --- |
| `generatedAt` | 快照生成时间 |
| `document` | 作文 ID、标题、题目、最新版本、最新分数、提交次数、状态 |
| `content` | 最新作文正文 |
| `metadata` | 写作模式、学段、主题标题、题目文本、体裁、来源类型 |
| `evaluations` | 评分记录摘要和完整 `resultJson` |
| `coachConversations` | 关联教练会话、消息角色、消息原文和发送时间 |

JSON 快照不替代权限校验。读取资产详情、刷新资产、下载 Markdown 时仍必须校验当前用户是作文 owner。

## DeepSeek 学习资产预览

作文资产详情支持手动触发“DeepSeek 学习资产预览”。它不会直接写入单词页或正式复习队列，第一版只用于让用户和产品侧观察：系统能否从一篇作文、评分记录和写作教练完整对话中提取出真正有复盘价值的内容。

预览运行记录保存在 `writing_learning_asset_preview_run`：

| 字段 | 说明 |
| --- | --- |
| `run_uid` | 本次提取运行 ID |
| `document_id` / `user_id` | 归属作文和用户 |
| `status` | `completed` 或 `failed` |
| `model` | 使用的 DeepSeek 模型，默认 `deepseek-chat` |
| `summary` | 本次学习价值总结 |
| `result_json` | DeepSeek 原始 JSON 输出 |
| `error_message` | 失败原因 |
| `input_token_count` / `output_token_count` | 模型 token 用量 |
| `item_count` | 本次保留的资产条数 |
| `generated_at` | 生成时间 |

预览条目保存在 `writing_learning_asset_preview_item`：

| 字段 | 说明 |
| --- | --- |
| `asset_type` | `word`、`phrase`、`sentence`、`grammar`、`writing_strategy` |
| `source_type` | `user_focus`、`coach_feedback`、`system_discovered` |
| `display_text` | 前端主展示文本 |
| `original_text` | 用户原文或原问题里的表达 |
| `recommended_text` | 推荐表达、改写句或知识点 |
| `meaning_zh` | 中文含义 |
| `explanation` | 为什么这样更好 |
| `value_reason_for_user` | 为什么这条内容对该用户有价值 |
| `how_to_reuse` | 下次写作如何复用 |
| `review_prompt` | 复习提示 |
| `source_question` | 对应的用户提问 |
| `source_excerpt` | 证据片段 |
| `confidence` | 模型置信度 |
| `learning_value_score` | 学习价值分 |
| `promotion_status` | 当前为 `preview`，后续可晋级为正式单词库/句子库资产 |

后端会过滤掉缺少 `value_reason_for_user` 的条目，避免把泛泛的高级词堆进资产库。后续如果接入单词库、句子库或短语库，建议从 `promotion_status=preview` 的条目中由用户确认或由规则晋级，而不是把 DeepSeek 输出直接全量写入正式学习库。

## 过期状态

资产详情返回 `stale`，用于提示用户档案是否需要刷新。

只要快照生成后出现以下任一变化，`stale=true`：

- 作文最新版本变化。
- 作文更新时间晚于快照生成时间。
- 评分记录数量变化。
- 已关联写作教练消息数量变化。

用户点击“刷新档案”后，后端重新生成 Markdown 和 JSON 快照，并更新同一份最新快照记录。

## 当前不归档的内容

当前第一版只归档后端已经持久化的数据。以下内容暂不进入作文资产档案：

- 浏览器临时状态中的素材。
- 浏览器临时状态中的范文结果。
- 浏览器临时状态中的翻译结果。
- 浏览器临时状态中的润色结果。
- 未落库的临时面板状态。
- OpenAI ChatKit 远端 transcript。

后续如果这些内容需要成为用户核心资产，必须先建立后端持久化和作文归属关系，再纳入资产快照。

## 数据生命周期

- 创建时机：作文归档时同步生成资产快照。
- 更新时机：再次归档或用户点击“刷新档案”时更新同一份快照。
- 取消归档：只把 `documents.status` 从 `2` 恢复为 `1`，不删除历史快照。
- 列表可见性：资产列表只展示当前 `documents.status=2` 的作文。
- 详情可读性：按当前用户 ownership 校验后读取资产详情。

## 相关接口

| 接口 | 用途 |
| --- | --- |
| `PATCH /api/docs/{docId}/archive` | 归档作文并生成或刷新资产快照 |
| `PATCH /api/docs/{docId}/unarchive` | 取消归档，不删除快照 |
| `GET /api/writing/documents?archived=true` | 查询当前用户已归档作文列表 |
| `POST /api/writing/documents/{docId}/coach-conversations` | 关联作文和写作教练会话 |
| `GET /api/writing/documents/{docId}/coach-conversations/{conversationId}/markdown` | 下载单段写作教练历史对话 Markdown |
| `GET /api/writing/documents/{docId}/asset` | 获取作文资产详情 |
| `POST /api/writing/documents/{docId}/asset/refresh` | 手动刷新作文资产快照 |
| `POST /api/writing/documents/{docId}/asset/learning-preview/refresh` | 手动提取 DeepSeek 学习资产预览 |
| `GET /api/writing/documents/{docId}/asset/markdown` | 下载 Markdown 档案 |

## 验收方式

后端验证：

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=DocumentServiceTest,WritingDocumentAssetServiceTest" test
.\mvnw.cmd -q test
```

前端验证：

```powershell
cd web
npm run build
```

手动验收：

1. 在写作编辑器使用写作教练。
2. 归档当前作文。
3. 打开 `/app/me?tab=assets`。
4. 点击“查看档案”。
5. 确认档案中能看到作文正文、题目信息、评分记录、写作教练对话和 Markdown。
6. 继续编辑作文或继续使用写作教练后，确认详情提示可刷新。
7. 点击“刷新档案”，确认 Markdown 内容更新。
