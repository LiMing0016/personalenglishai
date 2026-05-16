---
title: Admin 用户 API
status: active
owner: backend
last_updated: 2026-05-15
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/admin/AdminUserController.java
  - backend/src/main/java/com/personalenglishai/backend/service/admin/AdminUserService.java
  - backend/src/main/resources/mapper/AdminUserQueryMapper.xml
  - web/src/pages/admin/AdminUsersPage.vue
  - web/src/pages/admin/AdminUserDetailPage.vue
related_docs:
  - docs/admin/index.md
  - docs/api/admin-subscription.md
---

# Admin 用户 API

## 当前结论

Admin 用户模块用于管理员端常规用户查询和单用户排查。用户列表应覆盖账号、状态、学段、角色、订阅、额度、最近活跃和注册时间等用户资产字段，避免运营和客服依赖直接 SQL 查询生产数据。

接口只返回运营排查必要信息，不返回密码、密码 hash、JWT、refresh token 或数据库连接信息。

## GET /api/admin/users

查询用户列表。需要 `admin.users.read` 权限。

### Request

Query 参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | number | 用户 ID 精确筛选 |
| `keyword` | string | 搜索邮箱、手机号、昵称 |
| `status` | string | `active` / `disabled` |
| `role` | string | 账号角色，`user` / `admin` |
| `registerSource` | string | 注册来源 |
| `adminRole` | string | `super_admin` / `support_admin` / `content_admin` |
| `studyStage` | string | 学段，例如 `ielts`、`postgrad` |
| `planCode` | string | `free` / `basic` / `pro` / `premium` |
| `subscriptionStatus` | string | `free` / `active` / `expired` |
| `overLimit` | boolean | 只看已超额用户 |
| `createdFrom` | string | 注册时间下界 |
| `createdTo` | string | 注册时间上界 |
| `lastActiveFrom` | string | 最近活跃时间下界 |
| `lastActiveTo` | string | 最近活跃时间上界 |
| `page` | number | 默认 1 |
| `size` | number | 默认 20，最大 100 |

### Response

```json
{
  "items": [
    {
      "id": 31,
      "email": "admin03@admin.com",
      "phone": null,
      "nickname": "Admin 03",
      "status": "active",
      "registerSource": "seed",
      "role": "admin",
      "adminRoles": ["super_admin"],
      "studyStage": "ielts",
      "createdAt": "2026-05-15T13:57:20",
      "lastActiveAt": "2026-05-15T13:57:20",
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
      "usageDate": "2026-05-15"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 20
}
```

字段口径：

- `adminRoles` 来自 `admin_user_role`，普通用户为空数组。
- 当前有效套餐口径与 `/api/admin/subscriptions` 保持一致：有效付费订阅归入 Basic / Pro / Premium，未订阅、Free 和过期订阅归入 Free。
- `quotaPeriod=daily` 表示 Free 日额度，`quotaPeriod=monthly` 表示付费套餐月额度。
- `tokenUsed`、`tokenRemaining` 使用当前自然日或自然月聚合。

## GET /api/admin/users/{userId}

查询单个用户详情。需要 `admin.users.read` 权限。

### Response

响应包含账号基础信息、学习画像、作文统计、最近评测和 `subscription` 订阅额度摘要：

```json
{
  "id": 31,
  "email": "admin03@admin.com",
  "phone": null,
  "nickname": "Admin 03",
  "status": "active",
  "registerSource": "seed",
  "createdAt": "2026-05-15T13:57:20",
  "lastActiveAt": "2026-05-15T13:57:20",
  "role": "admin",
  "adminRoles": ["super_admin"],
  "studyStage": "ielts",
  "aiMode": null,
  "subscription": {
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
    "usageDate": "2026-05-15"
  },
  "ability": {},
  "stats": {},
  "recentEvaluations": []
}
```

## GET /api/admin/users/{userId}/overview

查询单个用户的摘要概览。需要 `admin.users.read` 权限。

该接口用于 `/admin/users` 右侧用户摘要抽屉和后续用户 360 详情页首屏。它只返回高频摘要，不返回完整作文正文、完整 prompt、密码 hash、token 或验证码。

### Response

```json
{
  "account": {
    "id": 31,
    "email": "admin03@admin.com",
    "phoneMasked": "138****0021",
    "nickname": "Admin 03",
    "status": "active",
    "studyStage": "ielts",
    "role": "admin",
    "adminRoles": ["super_admin"],
    "lastActiveAt": "2026-05-15T13:57:20"
  },
  "subscription": {
    "planCode": "free",
    "planName": "Free",
    "subscriptionStatus": "free",
    "quotaPeriod": "daily",
    "tokenLimit": 10000,
    "tokenUsed": 1200,
    "tokenRemaining": 8800,
    "overLimit": false
  },
  "writing": {
    "recentEvaluations": [],
    "stats": {
      "totalEssays": 0,
      "averageScore": null,
      "bestScore": null,
      "studyDays": 0
    }
  },
  "aiUsage": {
    "todayTokens": 1200,
    "monthTokens": 1200,
    "recentFailedRequests": 0
  },
  "audit": {
    "recentLogs": []
  },
  "quickLinks": {
    "detail": "/admin/users/31",
    "essays": "/admin/essays?userId=31",
    "subscriptions": "/admin/subscriptions?userId=31",
    "aiUsage": "/admin/model-usage?userId=31",
    "auditLogs": "/admin/audit-logs?targetUserId=31"
  }
}
```

当前版本说明：

- `phoneMasked` 默认脱敏。
- `writing.recentEvaluations` 复用用户详情中的最近评测摘要并限制为最近 3 条。
- `audit.recentLogs` 首版返回空数组，后续接入审计查询。
- `aiUsage` 首版使用当前额度用量作为最小摘要，后续接入模型用量聚合。

## PATCH /api/admin/users/{userId}/status

更新用户状态。需要 `admin.users.write` 权限，并写入管理员审计日志。

## PUT /api/admin/users/{userId}/roles

更新用户管理员角色。需要 `super_admin` 角色，并写入管理员审计日志。

## 兼容性

- 用户列表和详情只新增字段，不删除原字段。
- 订阅和额度字段复用订阅模块口径，不新增第二套套餐真源。
- 生产环境中常规用户查询应优先使用管理员端或 BI，直接 SQL 仅用于工程排障并需要审计。
