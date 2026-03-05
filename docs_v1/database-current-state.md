# 数据库现状文档（Database Current State）

本文档根据 `backend/src/main/resources/db/*.sql` 与 Mapper 使用情况整理。

## 1. 主 schema（`schema.sql`）

### 1.1 users

关键字段：

- `id`
- `email` / `email_verified`
- `phone` / `phone_verified`
- `password_hash`
- `nickname`
- `avatar_url`
- `role`（默认 `user`）
- `status`（默认 `active`）
- `register_source`
- `token_version`
- `last_active_at`
- `created_at` / `updated_at`

关键点：`role/status` 已存在，但当前尚未形成管理员权限体系。

### 1.2 user_profile（1:1）

- `user_id`（PK，FK -> users.id）
- `study_stage`
- `ai_mode`

### 1.3 documents / document_revisions / document_pins

- 支持文档主记录 + revision 历史 + pin 结构
- `documents` 按 `tenant_id/workspace_id/public_id` 唯一
- 删除为软删（`deleted_at`）

### 1.4 user_ability_profile

- 用户能力画像（多维度分数、confidence、sample_count、版本字段）

### 1.5 essay_evaluation / essay_favorite

- 写作评测历史
- 收藏关系（`user_id + evaluation_id` 唯一）

### 1.6 evaluate_task

- 异步评测任务：`request_id`、`status`、`error`、`result_json`、时间戳

## 2. 增量 SQL 文件

### 2.1 `create_rubric_tables.sql`

- `rubric_version`
- `rubric_dimension`
- `rubric_level`
- 含 highschool-v1 的初始化数据

### 2.2 `create_email_verification.sql`

- `email_verification_token`
- 对 `users.email_verified` 的兼容性变更

### 2.3 `create_sms_verification.sql`

- `sms_verification_code`
- 对 users 手机字段相关兼容变更

### 2.4 `create_ability_tables.sql`

- `user_ability_profile`（另一版定义）
- `writing_assessment`

注：该文件与 `schema.sql` 在能力画像部分存在“定义口径差异”，执行时应统一迁移策略，避免重复或冲突。

## 3. 与当前业务直接对应关系

- 认证：`users`、`email_verification_token`、`sms_verification_code`
- 个人中心：`users` + `user_profile` + `user_ability_profile`
- 写作评测：`essay_evaluation`、`essay_favorite`、`evaluate_task`
- 文档：`documents`、`document_revisions`、`document_pins`
- Rubric：`rubric_version`、`rubric_dimension`、`rubric_level`

## 4. 当前缺失（管理员端视角）

当前数据库未见下列管理员治理表：

- 管理员操作审计日志表（例如 `admin_audit_log`）
- 管理员角色绑定/权限策略表（如果后续需要更细 RBAC）

这也是管理员端建设的核心增量点之一。
