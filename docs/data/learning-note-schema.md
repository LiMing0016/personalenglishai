---
title: Learning Note 数据结构
status: active
owner: data
last_updated: 2026-06-24
review_cycle: on-change
related_code:
  - backend/src/main/resources/db/migrate_create_learning_note_tables.sql
  - backend/src/main/resources/mapper/LearningNoteMapper.xml
  - backend/src/main/java/com/personalenglishai/backend/entity/learning/LearningNote.java
related_docs:
  - /api/learning-notes
---

# Learning Note 数据结构

## 当前结论

`learning_note` 是学习资产画布的通用持久化表。当前前端可保存 `vocabulary`、`grammar`、`sentence` 和 `expression` 四类资产，后续新增学习资产类型仍复用同一张表。

Markdown 正文是主数据。`structured_payload` 只作为未来增强字段，不作为首版展示和编辑的唯一来源。

## 背景

学习助手输出内容中，用户可以选中单词、短语或句子并创建学习资产。用户在右侧学习资产画布中编辑 Markdown，前端自动保存后进入对应学习资产列表。

后续如果出现语法树、句子整理、表达整理等画布，应复用同一表和同一套 API，通过 `type` 区分。

## 数据模型

| 表名 | 用途 | 关键关系 |
| --- | --- | --- |
| `learning_note` | 保存用户学习资产画布产物 | 按 `user_id` 归属用户，可关联来源助手会话和消息 |

## 字段说明

### `learning_note`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | bigint | 是 | 自增 | 主键。 |
| `note_uid` | varchar(64) | 是 | 无 | 对外稳定 ID。 |
| `user_id` | bigint | 是 | 无 | 笔记所属用户。 |
| `type` | varchar(32) | 是 | 无 | 学习资产类型，当前前端使用 `vocabulary`、`grammar`、`sentence`、`expression`。 |
| `title` | varchar(255) | 是 | 无 | 标题，单词卡中是单词或短语，其他类型为笔记标题。 |
| `content_markdown` | mediumtext | 是 | 无 | 用户可编辑 Markdown 正文。 |
| `structured_payload` | json | 否 | `NULL` | 预留结构化内容。 |
| `source_conversation_uid` | varchar(64) | 否 | `NULL` | 来源助手会话 ID。 |
| `source_message_uid` | varchar(64) | 否 | `NULL` | 来源助手消息 ID。 |
| `source_text` | text | 否 | `NULL` | 来源上下文文本。 |
| `status` | varchar(32) | 是 | `active` | 当前状态。 |
| `created_at` | datetime | 是 | `CURRENT_TIMESTAMP` | 创建时间。 |
| `updated_at` | datetime | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间。 |
| `deleted_at` | datetime | 否 | `NULL` | 软删除时间。 |

## 索引

| 索引 | 字段 | 唯一 | 用途 |
| --- | --- | --- | --- |
| `uk_learning_note_uid` | `note_uid` | 是 | 对外 ID 唯一查询。 |
| `idx_learning_note_user_type` | `user_id,type,deleted_at,updated_at` | 否 | 用户按类型查看学习资产列表。 |
| `idx_learning_note_source_conversation` | `source_conversation_uid` | 否 | 从助手会话追溯资产来源。 |

## 迁移步骤

1. 执行 `backend/src/main/resources/db/migrate_create_learning_note_tables.sql`。
2. 确认 `learning_note` 表和索引创建成功。
3. 发布后端 `LearningNoteMapper`、`LearningNoteService` 和 `LearningNoteController`。

```sql
CREATE TABLE IF NOT EXISTS learning_note (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_uid VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_markdown MEDIUMTEXT NOT NULL,
    structured_payload JSON NULL,
    source_conversation_uid VARCHAR(64) NULL,
    source_message_uid VARCHAR(64) NULL,
    source_text TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_learning_note_uid (note_uid),
    KEY idx_learning_note_user_type (user_id, type, deleted_at, updated_at),
    KEY idx_learning_note_source_conversation (source_conversation_uid)
);
```

## 数据生命周期

- 创建时机：用户新建学习资产后，前端按自动保存节奏调用创建接口。
- 更新时机：用户重命名或编辑已有 `noteUid` 的画布内容后，前端按自动保存节奏调用更新接口。
- 删除策略：调用删除接口后写入 `deleted_at`，列表默认过滤已删除记录。
- 保留时间：当前不做自动清理。

## 兼容性和回滚

- 向前兼容：新增学习资产类型时只新增 `type` 值和前端模板。
- 向后兼容：旧记录继续以 Markdown 正文展示和编辑。
- 回滚方式：功能回滚时保留表结构；如需彻底回滚，先确认没有线上用户数据。

## 验收方式

```sql
SHOW CREATE TABLE learning_note;
SELECT note_uid, type, title, deleted_at
FROM learning_note
WHERE user_id = ?
  AND type = 'vocabulary'
  AND deleted_at IS NULL
ORDER BY updated_at DESC;
```

通过标准：

- 表结构字段与迁移文件一致。
- 用户列表查询命中 `idx_learning_note_user_type`。
- 删除记录不会出现在默认列表中。
