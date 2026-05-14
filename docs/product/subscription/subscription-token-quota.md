---
title: 订阅 Token 额度
status: active
owner: backend
last_updated: 2026-05-15
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/service/subscription/SubscriptionService.java
  - backend/src/main/java/com/personalenglishai/backend/service/admin/AdminSubscriptionService.java
  - backend/src/main/resources/db/migrate_create_subscription_tables.sql
related_docs:
  - docs/api/admin-subscription.md
  - docs/admin/index.md
---

# 会员订阅基础层：AI Token 日/月额度

## 1. 当前定位

第一版会员系统只做三件事：

1. Free、未订阅、过期订阅用户按每日 AI token 总额度限制 AI 请求。
2. Basic / Pro / Premium 有效订阅用户按每月 AI token 总额度限制 AI 请求。
3. 通过“兑换会员码”和 Admin 额度规则维护订阅权益。

不做真实支付、订单、退款、自动续费、模型单价、人民币成本估算，也不做储值礼品卡或功能次数限制。

```mermaid
flowchart TD
  User["用户"] --> FE["前端个人中心 / AI 功能"]
  FE --> API["Java 后端 API"]
  API --> Redeem["兑换会员码"]
  Redeem --> Sub["用户订阅"]
  API --> Quota["订阅额度校验"]
  Quota --> DB["订阅与 token 用量表"]
  API --> AI["OpenAI / Qwen / Python Agents"]
  AI --> Usage["Provider usage"]
  Usage --> Record["统一 token 记录服务"]
  Record --> DB
```

## 2. 会员档位

| planCode | 名称 | 额度周期 | token 额度 |
| --- | --- | --- | ---: |
| `free` | Free | 每日 | 10,000 |
| `basic` | Basic | 每月 | 1,000,000 |
| `pro` | Pro | 每月 | 5,000,000 |
| `premium` | Premium | 每月 | 20,000,000 |

新用户默认没有 `user_subscription` 记录，但查询时会被解析为 `Free`。过期付费订阅同样回落到 Free 每日额度。

## 3. 数据模型

```mermaid
erDiagram
  subscription_plan {
    string plan_code PK
    string name
    bigint monthly_token_limit
    bigint daily_token_limit
    int sort_order
    boolean active
  }

  user_subscription {
    bigint user_id PK
    string plan_code
    string status
    datetime current_period_start
    datetime current_period_end
  }

  ai_token_usage_event {
    string usage_event_id PK
    bigint user_id
    string feature_key
    string provider
    string model
    bigint input_tokens
    bigint cached_input_tokens
    bigint output_tokens
    bigint reasoning_tokens
    bigint total_tokens
    string trace_id
    datetime occurred_at
  }

  user_ai_token_usage_monthly {
    bigint user_id PK
    string usage_month PK
    bigint token_used
  }

  user_ai_token_usage_daily {
    bigint user_id PK
    date usage_date PK
    bigint token_used
  }

  subscription_plan ||--o{ user_subscription : "plan_code"
  ai_token_usage_event }o--|| user_ai_token_usage_monthly : "aggregate"
  ai_token_usage_event }o--|| user_ai_token_usage_daily : "aggregate"
```

核心表：

- `subscription_plan`：会员档位配置。
- `user_subscription`：用户当前会员档位和有效期。
- `subscription_redeem_code`：兑换码表，只保存 HMAC hash，不保存明文码。
- `subscription_redeem_event`：兑换流水表，记录用户、IP、兑换前后订阅状态。
- `ai_token_usage_event`：单次 AI 调用 token 事件，使用 `usage_event_id` 幂等去重。
- `user_ai_token_usage_monthly`：付费用户自然月聚合 token 用量，用于快速额度判断。
- `user_ai_token_usage_daily`：Free、未订阅、过期订阅用户自然日聚合 token 用量，用于快速额度判断。

数据库初始化脚本位于：

- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/resources/db/migrate_create_subscription_tables.sql`

## 4. 请求拦截逻辑

核心策略是：请求前只判断用户是否已经超额，不预扣 token。

```mermaid
sequenceDiagram
  participant FE as 前端
  participant API as AI 接口
  participant Sub as SubscriptionService
  participant AI as AI Provider
  participant Usage as AiUsageRecorder
  participant DB as DB

  FE->>API: 发起 AI 请求
  API->>Sub: assertAiTokenQuotaAvailable(userId)
  Sub->>DB: 查询当前档位 + 当前日/月 used
  alt used >= limit
    Sub-->>API: 抛出 SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED
    API-->>FE: HTTP 429
  else 未超额
    API->>AI: 调用 OpenAI/Qwen/Python Agent
    AI-->>API: 返回结果 + usage
    API->>Usage: 记录 input/output/reasoning tokens
    Usage->>DB: 写 usage event
    Usage->>DB: 聚合到 monthly usage
    API-->>FE: 返回业务结果
  end
```

## 5. 超额规则

本次调用导致超额时，不回滚、不阻止本次结果返回。

## 6. 兑换码规则

- 管理员通过 `POST /api/admin/subscription/redeem-codes` 生成兑换码。
- 用户通过 `POST /api/subscription/redeem` 兑换会员码。
- 兑换码携带 `planCode + durationDays`，例如 Pro 30 天。
- 同档位兑换从当前有效期末尾续期；不同档位兑换从当前时间切换到新档位。
- 明文码只在生成接口响应中返回一次，数据库只保存 HMAC-SHA256 hash。
- 兑换过程使用事务和条件更新保证单个码只能成功兑换一次。

```mermaid
flowchart LR
  A["请求前 used=99,000<br/>limit=100,000"] --> B["允许本次 AI 调用"]
  B --> C["本次消耗 5,000"]
  C --> D["调用后 used=104,000"]
  D --> E["本次正常返回"]
  E --> F["下一次 AI 请求返回 429"]
```

已超过当前额度周期后，后续 AI 请求返回 HTTP `429`。Free 用户在服务器自然日切换后恢复可用额度；付费用户在自然月切换后恢复可用额度。

业务语义错误码：

```text
SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED
```

当前后端统一错误响应仍使用数字 code，映射为：

```text
429010
```

前端同时兼容这两个 code，并提示用户本月 AI token 额度已用完。

## 6. Token 计算口径

统一扣量口径：

```text
totalTokens = inputTokens + outputTokens + reasoningTokens
```

`cachedInputTokens` 会保存，但当前不从总扣量里单独折算成本。

只有当 provider 没有拆分字段时，才退回使用 provider 的 `total_tokens`。

## 7. 幂等机制

每次 AI 调用生成一个 `usageEventId`。

```mermaid
flowchart TD
  A["AI usage 上报"] --> B{"usage_event_id 已存在?"}
  B -- 是 --> C["忽略，不重复累计"]
  B -- 否 --> D["插入 ai_token_usage_event"]
  D --> E["累加 user_ai_token_usage_monthly.token_used"]
```

这保证了同一个 AI 响应重复上报时，不会重复扣 token。

## 8. 订阅购买机制

模拟购买只支持 `basic`、`pro`、`premium`。

```mermaid
flowchart TD
  A["POST /api/subscription/mock-purchase"] --> B{"当前是否同档且未过期?"}
  B -- 是 --> C["从 current_period_end 延长 30 天"]
  B -- 否 --> D["立即切换档位<br/>从当前时间起 30 天"]
  C --> E["upsert user_subscription"]
  D --> E
  E --> F["返回最新 /me 状态"]
```

`Free` 不需要购买。过期 paid subscription 查询时会回落为 `Free`。

## 9. API

### 查询档位

`GET /api/subscription/plans`

返回 4 个档位及其 token 上限。Free 使用每日额度，付费套餐使用月度额度。

### 查询当前会员

`GET /api/subscription/me`

返回当前档位、有效期、额度周期、当前周期 token limit、used、remaining。

新增兼容字段：

- `quotaPeriod`：`daily` 或 `monthly`。
- `dailyTokenLimit`：Free 每日额度。
- `tokenLimit`：当前周期生效额度。
- `usageDate`：Free 当前自然日。

### 模拟购买

`POST /api/subscription/mock-purchase`

请求体：

```json
{
  "planCode": "basic"
}
```

规则：

- 同档续购：从当前有效期结束时间继续延长 30 天。
- 换档购买：立即切换，新有效期从当前时间起 30 天。

## 10. Admin 订阅与额度管理

管理员端 `/admin/subscriptions` 接入真实数据后用于管理订阅用户和额度规则：

- `GET /api/admin/subscriptions/overview`：查询今日新增用户、今日新增订阅、普通/订阅用户规模、Free / Basic / Pro / Premium 等级分布、token 消耗和转化率。
- `GET /api/admin/subscriptions`：查询已订阅、未订阅、过期回落 Free 的用户列表。
- `GET /api/admin/subscriptions/daily-stats`：查询每日用户、订阅和 token 消耗统计。
- `GET /api/admin/subscription/quota-rules`：查看 Free / Basic / Pro / Premium 额度规则。
- `PUT /api/admin/subscription/quota-rules/{planCode}`：修改 Free 每日额度或付费套餐月度额度。

权限：

- `admin.subscription.read`：订阅列表和额度规则只读。
- `admin.subscription.write`：生成兑换码、修改额度规则。

额度规则修改会写入审计日志，包含套餐、修改前额度和修改后额度。

## 11. 后端状态管理

后端有三层状态：

```mermaid
flowchart TD
  DB["持久状态<br/>subscription / monthly usage"] --> Service["业务状态解析<br/>SubscriptionService"]
  Service --> Runtime["运行时上下文<br/>AiUsageContextHolder ThreadLocal"]
  Runtime --> Recorder["AI usage 记录<br/>AiUsageRecorder"]
```

- 持久状态：DB 是唯一真源。
- 当前请求上下文：用 `AiUsageContextHolder` 传递 `userId`、`featureKey`、`traceId`。
- 额度判断：每次 AI 请求前实时查当前自然日或自然月聚合。
- 前端不保存额度真源，只展示 `/api/subscription/me` 返回值。

## 12. 前端状态管理

前端订阅状态目前只在个人中心组件内维护，没有新增 Pinia store。

```mermaid
flowchart LR
  Page["PersonalCenterPage"] --> Section["SubscriptionSection"]
  Section --> API["userApi"]
  API --> Plans["GET /subscription/plans"]
  API --> Me["GET /subscription/me"]
  Section --> Purchase["POST /subscription/mock-purchase"]
  Purchase --> Refresh["更新 status"]
```

AI 请求遇到 `429010` 或 `SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED` 时：

- 展示 AI token 额度已用完提示。
- 不触发登录跳转。
- 分发 `subscription-quota-exceeded` 事件，后续可用于自动跳转会员区。

## 13. Python Agents SDK usage 透传

Python 保持原业务 DTO 兼容，只额外带 `_usage` 给 Java 后端消费。

```mermaid
sequenceDiagram
  participant Py as Python Agent Workflow
  participant Java as Java Service
  participant DTO as Java DTO
  participant Rec as AiUsageRecorder

  Py->>Py: extract_usage(result)
  Py-->>Java: 返回业务结果 + _usage
  Java->>DTO: 反序列化 _usage
  Java->>Rec: recordCurrentContext(...)
  DTO-->>FE: 不输出 _usage 给前端
```

`_usage` 在 Java DTO 中是 write-only，前端看不到，不破坏现有业务响应结构。

## 14. 当前覆盖范围

已接入：

- OpenAI Java 客户端
- Qwen Java 客户端
- AI command
- 写作评分、润色、翻译、模板、素材、范文、题单、助手等 AI 入口
- Python Agents SDK 题单/助手链路 usage 透传

未限制：

- 普通文档
- 历史记录
- 个人资料
- 能力雷达
- 语法工具链
- 无 provider usage 的调用不做字符估算扣费

## 15. 一句话总结

当前实现是一个“订阅状态 + 日/月 token 聚合 + AI 请求前拦截 + AI 响应后记账 + Admin 额度规则管理”的基础层。DB 负责长期状态，ThreadLocal 负责单次 AI 调用上下文，前端只展示和触发模拟升级，管理员端维护规则和排查用户额度状态。
