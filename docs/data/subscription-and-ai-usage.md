---
title: 订阅与 AI 用量数据口径
status: active
owner: data
last_updated: 2026-07-26
review_cycle: on-change
related_code:
  - backend/src/main/resources/db/schema.sql
  - backend/src/main/java/com/personalenglishai/backend/service/subscription/
  - backend/src/main/resources/mapper/AiTokenUsageMapper.xml
related_docs:
  - /api/user-ai-usage
  - /api/admin-subscription
---

# 订阅与 AI 用量数据口径

## 当前结论

`ai_token_usage_event` 是 AI Token 历史事实源；`user_ai_token_usage_daily` 和 `user_ai_token_usage_monthly` 是当前额度判断聚合，不能替代事件账本生成跨套餐历史。个人中心历史活动读取原始事件，订阅页当前权益继续读取聚合表。

本轮没有新增表或执行数据回填。

## 背景

免费用户按日额度、兑换码会员按月额度，两类用户会在历史中切换套餐。如果个人中心只读取日表或月表，会在切换边界产生缺口。因此历史展示统一查询原始事件，再根据展示时区和产品维度聚合。

## 数据模型

| 表名 | 用途 | 关键关系 |
| --- | --- | --- |
| `subscription_plan` | 套餐与日/月 Token 上限 | 由 `plan_code` 关联订阅 |
| `user_subscription` | 用户当前套餐和有效期 | 每用户一条当前状态 |
| `ai_token_usage_event` | 不可变 AI Token 事件账本 | `user_id` 关联用户 |
| `user_ai_token_usage_daily` | 免费用户每日额度聚合 | `(user_id, usage_date)` |
| `user_ai_token_usage_monthly` | 会员用户每月额度聚合 | `(user_id, usage_month)` |
| `subscription_redeem_code` | 兑换码资产 | HMAC hash，不保存明文 |
| `subscription_redeem_event` | 兑换历史 | 关联用户、兑换码和前后套餐 |

## 字段说明

### `ai_token_usage_event`

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| usage_event_id | varchar(96) | 是 | 无 | 稳定幂等主键 |
| user_id | bigint | 是 | 无 | 用量归属用户 |
| feature_key | varchar(96) | 是 | 无 | 细粒度功能键 |
| provider | varchar(64) | 否 | null | 模型供应商 |
| model | varchar(128) | 否 | null | 模型标识 |
| input_tokens | bigint | 是 | 0 | 输入 Token |
| cached_input_tokens | bigint | 是 | 0 | 输入中的缓存命中明细，不重复加总 |
| output_tokens | bigint | 是 | 0 | 输出 Token |
| reasoning_tokens | bigint | 是 | 0 | 推理 Token 明细，不在总量中重复加总 |
| total_tokens | bigint | 是 | 0 | 供应商总量优先，缺失时回退为输入 + 输出 |
| trace_id | varchar(96) | 否 | null | 跨服务追踪标识 |
| occurred_at | datetime | 是 | current timestamp | 按 UTC 时刻写入和解释 |

### 聚合表

| 表 | 周期字段 | 使用者 | 说明 |
| --- | --- | --- | --- |
| `user_ai_token_usage_daily` | `usage_date` | Free | 当前服务业务时区中的日额度 |
| `user_ai_token_usage_monthly` | `usage_month` | Basic / Pro / Premium | 当前服务业务时区中的自然月额度 |

聚合表只在事件首次插入成功后递增。重复 `usage_event_id` 被 `INSERT IGNORE` 拒绝，也不会重复增加额度。

单次事件写入和对应额度聚合使用独立事务，保证两者一起提交或回滚；计量数据库异常不会把已经完成的学习、写作或词汇业务事务一并回滚。正式计费前仍需用 outbox 或等价机制补上失败后的持久化重试。

## 产品分类

产品分类不写回事件表，由 `AiUsageProductClassifier` 在查询时集中映射：

| 产品键 | 功能键规则 |
| --- | --- |
| assistant | `assistant.*`、`ai.command.*` |
| translation | `writing.translate`、`translation.*` |
| writing | 其余 `writing.*` |
| vocabulary | `vocabulary.*` |
| other | 其它或空功能键 |

前端不得自行解析 `feature_key`。

## 写入链路

### 通用 Java AI 客户端

`AiUsageContextHolder` 提供用户、功能键和 trace 上下文，`AiUsageRecorder` 从模型响应读取 Token 并调用 `SubscriptionService.recordUsage`。

### 学习助手

- 同步和流式响应都读取 Python run metadata。
- `usage_event_id = assistant:<runId>`。
- `feature_key = assistant.conversation`。
- 调用前执行额度检查；同一 run 重复回调由事件主键去重。

### 词汇卡异步生成

- Python workflow 汇总 Core 与 Card Blocks 两次 Agents SDK 调用的 usage。
- Java worker 在成功完成且仍持有 lease 后写入。
- `usage_event_id = vocabulary-card:<jobUid>`。
- `feature_key = vocabulary.card-generation`。
- usage 缺失时跳过，不估算。

### 已有词汇链路

词汇图片识别和导入分析继续通过已有上下文记录：

- `vocabulary.image_recognition`
- `vocabulary.import_analysis`

## 索引

| 索引 | 字段 | 唯一 | 用途 |
| --- | --- | --- | --- |
| PRIMARY | usage_event_id | 是 | 幂等写入 |
| idx_ai_token_usage_event_user_time | user_id, occurred_at | 否 | 个人历史范围查询 |
| idx_ai_token_usage_event_feature | feature_key | 否 | 功能审计 |
| idx_ai_token_usage_event_trace | trace_id | 否 | 调用链排查 |

## 数据生命周期

- 创建时机：模型服务返回可信 Token usage 后。
- 更新时机：原始事件不更新；额度聚合仅递增。
- 删除或归档策略：当前随用户外键删除。正式计费前应结合注销去标识化和法务保留要求重新确认。
- 保留时间：当前未设置自动清理。
- 历史回填：不根据作文、会话或词汇卡记录推测 Token。

## 兼容性和回滚

- 向前兼容：未知功能键进入 `other`，总量不丢失。
- 向后兼容：Python vocabulary usage 字段可为 null；Java 仍接受没有 usage 的旧响应。
- 回滚方式：可以回滚查询接口和前端组件；已写入的幂等原始事件无需删除，避免破坏额度和审计。
- 时区：事件按 UTC；现有额度聚合继续沿用服务业务时区，不能用历史 API 替代实时额度检查。

## 正式计费前必须补齐

1. 并发请求的额度预占、真实结算和释放。
2. outbox 或等价持久化重试，避免记录失败永久丢失。
3. 调整事件和供应商对账，不直接修改原始事实。
4. 托管 ChatKit 获得可信服务端用量后再纳入。
5. 为音频秒数、图片次数等建立独立 metric 和 unit。

## 验收方式

```sql
SELECT usage_event_id, user_id, feature_key, total_tokens, occurred_at
FROM ai_token_usage_event
WHERE user_id = ?
  AND occurred_at >= ?
  AND occurred_at < ?
ORDER BY occurred_at, usage_event_id;
```

通过标准：

- 同一 `usage_event_id` 只存在一次。
- `total_tokens` 不重复加入缓存或推理明细。
- 学习助手 run ID 和词汇 job UID 可追踪到对应事件。
- 个人历史总量来自原始事件，额度判断继续使用聚合表。

## 相关资料

- Schema：`backend/src/main/resources/db/schema.sql`
- Mapper：`backend/src/main/resources/mapper/AiTokenUsageMapper.xml`
- Service：`backend/src/main/java/com/personalenglishai/backend/service/subscription/SubscriptionService.java`
- API：`docs/api/user-ai-usage.md`
- 测试：`SubscriptionServiceTest`、`AssistantUsageServiceTest`、`VocabularyGenerationWorkerTest`
