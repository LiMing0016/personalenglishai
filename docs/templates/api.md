---
title: API 文档标题
status: draft
owner: backend
last_updated: YYYY-MM-DD
review_cycle: on-change
related_code: []
related_docs: []
---

# API 文档标题

## 当前结论

说明这个接口用于什么场景，由谁调用，是否为当前推荐接口。

## Endpoint

```http
METHOD /api/path
```

## 鉴权和权限

- 鉴权方式：JWT / 公开接口 / 管理员权限。
- 权限要求：
- 用户身份来源：

## Request

### Headers

| Header | 必填 | 说明 |
| --- | --- | --- |
| Authorization | 是 | `Bearer <access_token>` |

### Query Parameters

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| page | number | 否 | 页码，从 0 开始 | `0` |

### Body

```json
{
  "field": "value"
}
```

| 字段 | 类型 | 必填 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| field | string | 是 | 1-100 字符 | 字段说明 |

## Response

### 成功响应

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| data | object | 响应数据 |

### 错误响应

| HTTP 状态 | 错误码 | 场景 | 用户提示 |
| --- | --- | --- | --- |
| 400 | `400000` | 参数错误 | 检查输入 |
| 401 | `401001` | 未登录 | 重新登录 |

## 兼容性约束

- 不能删除的字段：
- 不能改变语义的字段：
- 可新增的字段：

## 安全和限流

- 是否需要限流：
- 是否涉及敏感数据：
- 是否写审计日志：

## 验收方式

```powershell
curl -i http://localhost:18080/api/path
```

通过标准：

- 状态码符合预期。
- 响应字段符合契约。
- 错误场景返回明确错误码。

## 相关资料

- Controller：
- Service：
- 前端调用：
- 测试：
