---
title: Admin 订阅与额度 API
status: active
owner: backend
last_updated: 2026-05-15
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/admin/AdminSubscriptionController.java
  - backend/src/main/java/com/personalenglishai/backend/service/admin/AdminSubscriptionService.java
  - web/src/api/admin.ts
related_docs:
  - docs/product/subscription/subscription-token-quota.md
  - docs/admin/index.md
---

# Admin 订阅与额度 API

## 当前结论

Admin 订阅模块用于查看每日新增用户、普通用户、订阅用户、订阅等级分布、token 消耗和套餐额度规则。第一版不支持单用户特殊额度，也不支持管理员直接改已用 token。

权限分为：

- `admin.subscription.read`：读取订阅用户列表和额度规则。
- `admin.subscription.write`：生成兑换码、修改额度规则。

## GET /api/admin/subscriptions

查询订阅用户列表。返回有效付费用户、未订阅用户和过期回落 Free 的用户。

### Request

Query 参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 搜索邮箱、手机号、昵称 |
| `planCode` | string | `free` / `basic` / `pro` / `premium` |
| `subscriptionStatus` | string | `active` / `free` / `expired` |
| `overLimit` | boolean | 只看已超额用户 |
| `expiresFrom` | string | 到期时间下界，`YYYY-MM-DD` |
| `expiresTo` | string | 到期时间上界，`YYYY-MM-DD` |
| `page` | number | 默认 1 |
| `size` | number | 默认 20，最大 100 |

### Response

```json
{
  "items": [
    {
      "userId": 1,
      "email": "user@example.com",
      "phone": null,
      "nickname": "Tom",
      "userStatus": "active",
      "planCode": "free",
      "planName": "Free",
      "subscriptionStatus": "free",
      "quotaPeriod": "daily",
      "tokenLimit": 10000,
      "tokenUsed": 1200,
      "tokenRemaining": 8800,
      "overLimit": false,
      "currentPeriodStart": null,
      "currentPeriodEnd": null,
      "usageMonth": "2026-05",
      "usageDate": "2026-05-14"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 20
}
```

字段口径：

- `quotaPeriod=daily`：Free、未订阅、过期订阅。
- `quotaPeriod=monthly`：当前有效的 Basic / Pro / Premium。
- `tokenLimit` 是当前额度周期的有效上限。
- `tokenUsed` 来自当前自然日或自然月聚合。

## GET /api/admin/subscriptions/overview

查询订阅与用户运营概览，用于 `/admin/subscriptions` 首屏指标。

### Response

```json
{
  "totalUsers": 120,
  "ordinaryUsers": 90,
  "subscribedUsers": 30,
  "todayNewUsers": 8,
  "todayNewSubscriptions": 3,
  "todayFreeTokenUsed": 1000,
  "todayPaidTokenUsed": 2000,
  "overLimitUsers": 2,
  "sevenDaySubscriptionRate": 37.5,
  "planDistribution": [
    {
      "planCode": "free",
      "planName": "Free",
      "userCount": 90,
      "ratio": 75.0,
      "sortOrder": 0
    },
    {
      "planCode": "basic",
      "planName": "Basic",
      "userCount": 15,
      "ratio": 12.5,
      "sortOrder": 1
    }
  ]
}
```

字段口径：

- `planDistribution` 按当前有效套餐统计用户数量。无订阅、Free、过期付费订阅均归为 `free`。
- `ratio` 是该套餐用户数占 `totalUsers` 的百分比。
- 套餐顺序使用 `subscription_plan.sort_order`。

## GET /api/admin/subscriptions/daily-stats

查询每日用户数据。默认返回最近 14 天。

### Request

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `dateFrom` | string | 起始日期，`YYYY-MM-DD` |
| `dateTo` | string | 结束日期，`YYYY-MM-DD` |

### Response

```json
[
  {
    "statDate": "2026-05-14",
    "newUsers": 8,
    "newSubscriptions": 3,
    "ordinaryUsers": 90,
    "subscribedUsers": 30,
    "freeTokenUsed": 1000,
    "paidTokenUsed": 2000,
    "subscriptionRate": 37.5
  }
]
```

## GET /api/admin/subscription/quota-rules

查询套餐额度规则。

### Response

```json
[
  {
    "planCode": "free",
    "planName": "Free",
    "quotaPeriod": "daily",
    "dailyTokenLimit": 10000,
    "monthlyTokenLimit": 100000,
    "active": true,
    "sortOrder": 0
  }
]
```

## PUT /api/admin/subscription/quota-rules/{planCode}

修改套餐额度规则。修改会立即影响后续 quota check，并写入管理员审计日志。

### Request

Free：

```json
{
  "dailyTokenLimit": 10000
}
```

付费套餐：

```json
{
  "monthlyTokenLimit": 1000000
}
```

### Response

返回更新后的 quota rule。

## POST /api/admin/subscription/redeem-codes

生成兑换码接口保持不变，继续要求 `admin.subscription.write`。

## 错误码

| 场景 | 行为 |
| --- | --- |
| 无订阅读权限 | 403 |
| 无订阅写权限 | 403 |
| 无效套餐 | `COMMON_VALIDATION_ERROR` |
| 额度为空或小于等于 0 | `COMMON_VALIDATION_ERROR` |

## 兼容性

- 用户端 `/api/subscription/me` 保留 `monthlyTokenLimit` 字段，并新增 `quotaPeriod`、`dailyTokenLimit`、`tokenLimit`、`usageDate`。
- AI 请求超额错误码仍为 `SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED`。
- `ai_token_usage_event` 仍是 usage 明细真源，日/月聚合只用于快速判断额度。
