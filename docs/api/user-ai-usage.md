---
title: 用户 AI 用量活动 API
status: active
owner: backend
last_updated: 2026-07-26
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/UserController.java
  - backend/src/main/java/com/personalenglishai/backend/service/subscription/AiUsageActivityService.java
  - backend/src/main/java/com/personalenglishai/backend/mapper/subscription/AiTokenUsageMapper.java
  - web/src/api/user.ts
related_docs:
  - /data/subscription-and-ai-usage
---

# 用户 AI 用量活动 API

## 当前结论

该接口是个人中心查询历史 AI Token 活动的当前推荐接口。它读取 `ai_token_usage_event` 原始事件账本，按用户请求的展示时区聚合为自然日 bucket；当前套餐、额度和剩余额度继续由 `/api/subscription/me` 提供。

## Endpoint

```http
GET /api/users/me/usage
```

## 鉴权和权限

- 鉴权方式：JWT。
- 权限要求：已登录用户。
- 用户身份来源：认证过滤器写入的 `userId` request attribute。
- 用户只能查询自己的用量，不接受 `userId` 查询参数。

## Request

### Headers

| Header | 必填 | 说明 |
| --- | --- | --- |
| Authorization | 是 | `Bearer <access_token>` |

### Query Parameters

| 字段 | 类型 | 必填 | 默认值 | 约束 | 示例 |
| --- | --- | --- | --- | --- | --- |
| metric | string | 否 | `ai_tokens` | 当前只支持 `ai_tokens` | `ai_tokens` |
| granularity | string | 否 | `day` | 当前只支持 `day` | `day` |
| from | date | 否 | `to - 365 天` | 展示时区中的自然日，包含当天 | `2025-07-27` |
| to | date | 否 | 展示时区中的今天 | 不早于 `from`，包含当天 | `2026-07-26` |
| timezone | string | 否 | `Asia/Shanghai` | 有效 IANA 时区名称 | `Asia/Shanghai` |

`from` 到 `to` 最多包含 366 个自然日。

## Response

### 成功响应

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "metric": "ai_tokens",
    "unit": "token",
    "timezone": "Asia/Shanghai",
    "from": "2025-07-27",
    "to": "2026-07-26",
    "total": 328640,
    "buckets": [
      {
        "date": "2026-07-26",
        "total": 12680,
        "byProduct": {
          "assistant": 5200,
          "writing": 4600,
          "translation": 1880,
          "vocabulary": 1000,
          "other": 0
        }
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| metric | string | 当前固定为 `ai_tokens` |
| unit | string | 当前固定为 `token` |
| timezone | string | 实际采用的展示时区 |
| from / to | date | 实际查询的首尾自然日 |
| total | number | 所有返回 bucket 的 Token 总量 |
| buckets | array | 只包含有正数用量的日期，按日期升序 |
| buckets[].date | date | 展示时区中的自然日 |
| buckets[].byProduct | object | 稳定产品维度；未知功能进入 `other` |

产品维度：

- `assistant`：学习助手和 `ai.command.*`。
- `writing`：除翻译外的 `writing.*`。
- `translation`：`writing.translate` 和 `translation.*`。
- `vocabulary`：`vocabulary.*`。
- `other`：未登记功能。

### 错误响应

| HTTP 状态 | 错误码 | 场景 | 用户提示 |
| --- | --- | --- | --- |
| 400 | `400001` | metric 或 granularity 不支持 | 使用当前支持的查询值 |
| 400 | `400001` | 日期倒置或超过 366 天 | 缩小查询范围 |
| 400 | `400001` | timezone 不是有效 IANA 时区 | 使用 `Asia/Shanghai` 等合法时区 |
| 401 | 认证错误码 | 未登录或令牌失效 | 重新登录 |

## 时间与统计口径

1. 原始事件的 `occurred_at` 按 UTC 时刻解释。
2. 服务端把 `[from 00:00, to + 1 天 00:00)` 从请求时区转换成 UTC 半开区间。
3. SQL 只按用户和 UTC 区间取数。
4. Java 服务再把每条事件转换回请求时区的自然日。
5. 响应是稀疏 bucket；无用量日期由前端日历模型补零。

## 兼容性约束

- 不把历史 bucket 加入 `/api/subscription/me`。
- `metric`、`unit`、`timezone`、`from`、`to`、`total` 和 `buckets` 不能删除或改变语义。
- `byProduct` 的既有键保持稳定；新增产品维度前必须保留 `other` 兼容。
- 周和月视图由前端从相同日 bucket 派生，避免多套统计口径。
- 历史只展示账本已有事实，不根据业务记录反推 Token。

## 当前排除范围

- 托管 ChatKit 写作教练。
- 本地 PDFBox、Paddle/Tesseract OCR。
- 没有调用 LLM 的本地检索和文档处理。
- 尚未上线的听力、口语 AI 能力。
- 音频秒数、图片次数等非 Token 单位。

## 安全和限流

- 接口不返回 prompt、用户文本或供应商请求原文。
- 查询使用 `(user_id, occurred_at)` 索引。
- 单次范围限制为 366 天。
- 当前没有独立接口限流；如果事件量显著增加，应优先增加服务端聚合读模型，不扩大无界查询。

## 验收方式

```powershell
curl.exe -H "Authorization: Bearer <access_token>" `
  "http://localhost:8080/api/users/me/usage?metric=ai_tokens&granularity=day&from=2025-07-27&to=2026-07-26&timezone=Asia%2FShanghai"
```

通过标准：

- 只返回当前用户事件。
- 跨 UTC 日界线的事件落入正确上海自然日。
- `total` 等于所有 bucket 的 `total` 之和。
- 未识别 `feature_key` 进入 `other`。
- 非法时区和超长范围返回 `400001`。

## 相关资料

- Controller：`backend/src/main/java/com/personalenglishai/backend/controller/UserController.java`
- Service：`backend/src/main/java/com/personalenglishai/backend/service/subscription/AiUsageActivityService.java`
- Mapper：`backend/src/main/resources/mapper/AiTokenUsageMapper.xml`
- 前端调用：`web/src/api/user.ts`
- 测试：`AiUsageActivityServiceTest`、`UserUsageControllerTest`
