---
title: Agent Debug API
status: draft
owner: ai
last_updated: 2026-05-16
review_cycle: monthly
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/ops/AgentDebugController.java
  - backend/src/main/java/com/personalenglishai/backend/service/ops/AgentDebugService.java
  - backend/src/main/resources/mapper/AgentDebugMapper.xml
related_docs:
  - docs/agent/AI调试端设计.md
---

# Agent Debug API

## 接口用途

Agent Debug API 为 `/ops/agent/*` AI 调试端提供只读数据查询，用于查看真实 Agent run、RouteRequest、RoutingDecision、steps、prompt snapshots 和 usage。

## 权限

首期复用现有管理员登录校验：

- 未登录：返回 401 或走现有登录拦截。
- 非管理员：返回 403 或走现有管理员身份拦截。
- 管理员：允许只读查询。

暂不新增细粒度权限。

## Endpoints

### GET `/api/ops/agent/runs`

查询 Agent run 列表。

Request query：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| status | string | `completed`、`failed`、`partial` |
| intent | string | 路由意图 |
| targetAgent | string | 目标 agent |
| model | string | 实际模型 |
| userId | number | 用户 ID |
| conversationId | string | 助手会话 ID |
| createdFrom | string | 开始时间 |
| createdTo | string | 结束时间 |
| page | number | 页码，默认 1 |
| size | number | 每页数量，默认 20，最大 100 |

Response：

```json
{
  "items": [
    {
      "runId": "run_xxx",
      "traceId": "trace_xxx",
      "userId": 1,
      "conversationId": "conv-1",
      "rawUserMessage": "帮我润色这句话：I very like English.",
      "intent": "polish",
      "routeType": "run_workflow",
      "workflow": "specialist_single_turn",
      "targetAgent": "polish",
      "agentName": "Polish Agent",
      "model": "gpt-5.4-mini",
      "status": "completed",
      "latencyMs": 1234,
      "totalTokens": 130,
      "createdAt": "2026-05-15T12:00:00"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 20
}
```

### GET `/api/ops/agent/runs/{runId}`

查询单次 run 详情。

Response 包含：

| 字段 | 说明 |
| --- | --- |
| routeRequest | 后端传给 RouteAgent 的结构化输入 |
| routingDecision | RouteAgent 的结构化输出 |
| usage | token 与请求用量 |
| output | 最终输出摘要 |
| steps | RouteAgent、目标 Agent 等执行步骤 |
| prompts | prompt snapshots |

### GET `/api/ops/agent/runs/{runId}/steps`

查询 run 的执行步骤，按 `stepOrder` 升序返回。

### GET `/api/ops/agent/runs/{runId}/prompts`

查询 run 关联的 prompt snapshots。

### GET `/api/ops/agent/prompts`

查询 prompt snapshot 列表。

Request query：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| promptKey | string | Prompt key |
| promptHash | string | Prompt hash |
| agentName | string | Agent 名称 |
| model | string | 模型 |
| createdFrom | string | 开始时间 |
| createdTo | string | 结束时间 |
| page | number | 页码 |
| size | number | 每页数量 |

### POST `/api/ops/agent/eval-cases`（P1 草案）

从真实 run 保存 eval case。首期由人工确认 expected，系统只做预填。

Request：

```json
{
  "sourceRunId": "run_xxx",
  "caseType": "route_eval",
  "inputSnapshot": {},
  "expected": {
    "intent": "polish",
    "routeType": "run_workflow",
    "targetAgent": "PolishAgent"
  },
  "tags": ["polish", "route"],
  "note": "典型润色请求"
}
```

### POST `/api/ops/agent/model-sandbox/runs`（P1 草案）

基于历史 run 创建一次模型试跑。试跑不影响真实用户会话，不写入正式 assistant message，不修改用户画像或学习记录。

Request：

```json
{
  "sourceRunId": "run_xxx",
  "scope": "route_agent",
  "model": "gpt-5.4-mini"
}
```

Response：

```json
{
  "experimentRunId": "run_exp_xxx",
  "sourceRunId": "run_xxx",
  "scope": "route_agent",
  "model": "gpt-5.4-mini",
  "status": "completed",
  "routingDecision": {},
  "usage": {},
  "latencyMs": 900
}
```

第一版只要求支持 `scope=route_agent`，后续再扩展 `target_agent` 和 `full_workflow`。

## 错误响应

AI 调试端需要把 API 错误展示为可诊断状态，而不是只显示 HTTP 状态码。

建议错误响应保留：

```json
{
  "code": "AGENT_DEBUG_QUERY_FAILED",
  "message": "Agent debug run query failed",
  "requestId": "req_xxx",
  "details": {
    "endpoint": "GET /api/ops/agent/runs",
    "reason": "debug table missing or SQL schema mismatch"
  }
}
```

常见错误：

| HTTP | code | 说明 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 管理员登录失效 |
| 403 | `FORBIDDEN` | 当前账号不是管理员 |
| 500 | `AGENT_DEBUG_QUERY_FAILED` | 查询 debug run、step 或 prompt 失败 |
| 500 | `AGENT_DEBUG_SCHEMA_MISMATCH` | 本地数据库缺表或字段不一致 |

## 数据来源

学习助手调用 Python Agent 后，Python 返回 `run` metadata。后端 `AssistantConversationService` 将 metadata 写入：

- `agent_debug_run`
- `agent_debug_step`
- `agent_prompt_snapshot`

Debug 记录失败时不应影响学习助手正常回复。

## 脱敏

后端返回前会脱敏常见敏感字段：

- `Authorization`
- `Cookie`
- `token`
- `access_token`
- `refresh_token`
- `secret`
- `apiKey`
- `password`

Token 用量字段如 `inputTokens`、`cachedInputTokens`、`totalTokens` 不属于敏感凭证，不会被脱敏。
