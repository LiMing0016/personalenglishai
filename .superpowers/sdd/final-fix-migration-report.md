# 租约迁移最终审查修复报告

## 修复范围

- `migrate_add_vocabulary_generation_job_leases.sql`
- 租约迁移静态契约与 MySQL 8 集成测试
- README 与单词沉淀架构中的迁移重试说明

未修改业务代码、任务状态机或 mapper SQL。

## 根因

旧脚本使用 `ADD COLUMN IF NOT EXISTS`，该语法在验证使用的 MySQL 8.0.46 上直接报 1064。恢复索引则使用无条件 `ADD KEY`，即使列语法可执行，迁移完成后或索引已落盘的中断状态再次执行也会因重复索引失败。将多个 DDL 合在一条 `ALTER TABLE` 中还无法按已落盘状态逐项恢复。

## 修复

迁移现在分别从当前 `DATABASE()` 的 `information_schema.COLUMNS` 和 `information_schema.STATISTICS` 检查：

1. `lease_token`
2. `lease_expires_at`
3. `idx_vocabulary_job_lease`

每个缺失动作通过 MySQL 8 动态 SQL 单独执行；已存在时执行无副作用的 `SELECT 1`。DDL 全部完成后，仍使用 `lease_expires_at IS NULL` 条件幂等回填 `running` 历史任务。

## TDD 证据

RED 使用 disposable `mysql:8.0` 容器（服务版本 8.0.46）运行 `VocabularyLeaseMigrationMySqlTest`：

- 4 个测试全部在原迁移首条语句失败。
- MySQL 返回 `SQLSyntaxErrorException` / 1064，失败位置为 `ADD COLUMN IF NOT EXISTS`。

GREEN 对相同测试和镜像重新运行：

- `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

其他验证：

- 聚焦 schema/docs：`Tests run: 14, Failures: 0, Errors: 0, Skipped: 4`；4 个跳过项是未提供 MySQL 环境变量时的集成测试。
- 完整后端：`mvn test`，`Tests run: 598, Failures: 0, Errors: 0, Skipped: 5`，`BUILD SUCCESS`。
- 文档站：`npm run build`，VitePress `build complete`。

四个真实数据库场景为：

| 场景 | 初始状态 | 验证 |
| --- | --- | --- |
| 无租约列 | 两列和索引均缺失 | 两列创建、索引一份、回填成功 |
| 已有列无索引 | 两列存在，目标索引缺失 | 不重复列、索引一份、回填成功 |
| 列与索引均存在 | 完整租约结构存在 | 重跑无错、索引仍一份、回填成功 |
| DDL 中断后重跑 | 仅 `lease_token` 已落盘 | 补齐其余结构后再次执行无错、索引一份、回填成功 |

测试为每个场景创建随机前缀 schema，并在清理前校验 schema 前缀；容器在命令的 `finally` 中删除。

## 提交

提交信息：`fix(vocabulary): 保证租约迁移可重复执行`
