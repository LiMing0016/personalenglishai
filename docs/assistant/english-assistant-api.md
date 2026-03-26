# English Assistant API

总览文档见：[英语助手总览](english-assistant-overview.md)

## Overview

站内英语助手一期采用独立接口，不复用通用 `/api/ai/command`。

目标边界：

- 只服务站内页面
- 只回答英语学习和英语写作相关问题
- 政治、色情和其他敏感高风险话题拒答
- 写作场景按需引用当前作文上下文
- 也允许继续操作上一轮助手刚生成的内容

## Runtime Stack

当前英语助手的运行时由这些部分组成：

- 后端：`Spring Boot + Redis + WebClient`
- 前端：`Vue 3 + SSE`
- 模型调用：OpenAI `Responses API`
- 会话连续性：`store + previous_response_id + Redis`
- 分类输出：`Structured Outputs`
- 成本控制：`prompt_cache_key + input_tokens 预检 + truncation: auto`

这意味着当前系统并不是前端直连模型，也不是纯 Prompt 文本拼接，而是由后端统一编排。

## Runtime Logic

一条请求的实际运行逻辑如下：

1. 前端把用户消息和当前可用上下文发送到后端
2. 后端先查 Redis 会话状态
3. Router 用 `Responses API` 做结构化分类
4. Service 根据分类结果决定：
   - 是否拒答
   - 是否使用 draft
   - 是否使用 assistant output
   - 是否注入 rubric
5. `EnglishAssistantContextAssembler` 做上下文组装、局部片段提取和预算裁剪
6. Answer Service 再调用 `Responses API` 输出最终回答
7. Redis 回写最近 response、artifact、recent turns、summary
8. 前端消费结构化响应或 SSE 事件

## Endpoints

### `POST /api/english-assistant/chat`

非流式问答接口。

请求体：

```json
{
  "conversationId": "c_123",
  "message": "帮我解释这句话为什么别扭",
  "useDraftContext": true,
  "studyStage": "postgrad",
  "writingMode": "exam",
  "assignmentText": "Write an essay about school life.",
  "selectedText": "Therefore, universities should continue...",
  "draftText": "The survey on students' main gains ...",
  "preferredAction": "explain"
}
```

响应体：

```json
{
  "conversationId": "c_123",
  "responseId": "resp_abc",
  "scope": "current_draft",
  "taskType": "explain",
  "refused": false,
  "refusalReason": null,
  "usedDraftContext": true,
  "message": "这句话别扭，主要是逻辑连接不自然。",
  "actions": []
}
```

### `POST /api/english-assistant/chat/stream`

流式 SSE 接口。后端先完成 Router 分类，再对主回答做流式转发。

事件格式：

- `meta`
- `delta`
- `done`
- `error`

示例：

```text
event: meta
data: {"conversationId":"c_123","scope":"current_draft","taskType":"explain","usedDraftContext":true}

event: delta
data: {"text":"这句话的问题主要在于"}

event: done
data: {"conversationId":"c_123","responseId":"resp_abc","scope":"current_draft","taskType":"explain","usedDraftContext":true,"response":{...}}
```

## Router

Router 使用 OpenAI Responses API + Structured Outputs，返回：

- `scope`
  - `english_general`
  - `current_draft`
  - `assistant_output`
  - `session_meta`
  - `sensitive_refuse`
  - `off_topic`
- `taskType`
  - `ask`
  - `explain`
  - `rewrite`
  - `polish`
  - `translate`
  - `evaluate`
  - `generate`
- `needsDraftContext`
- `refusalReason`

Router 模型固定为 `gpt-4o`，`store=false`，`prompt_cache_key=english-router-v1`。

Router 额外特性：

- 当存在可复用的会话链时，后端会把对应链路的 `previous_response_id` 一并传给 Router，帮助它理解“续问”而不是把每轮都当成孤立问题
- Router 的 `hasAssistantOutput` 不再只看 `previous_response_id`，而是基于后端状态里是否存在真实可复用 artifact 判断
- 仅保留极少量兜底规则处理明显误判场景，例如：开启作文上下文时，`这篇作文 / 这句 / 这段 / 字数` 等强指代消息会优先回到 `current_draft`
- 当用户明显在引用上一轮助手生成的内容，例如“翻译一下最后一段”“改写刚才那篇”，后端会把它识别为 `assistant_output`
- `session_meta` 用于处理“你能记住上下文吗”“是否引用作文”等会话元问题
- `sensitive_refuse` 用于政治、色情、暴力、违法、极端等敏感高风险话题拒答

## Answer Service

主回答器同样使用 Responses API，模型固定为 `gpt-4o`。

规则：

- `english_general` 不注入作文上下文
- `current_draft` 仅在 `useDraftContext=true` 时注入 `assignmentText / selectedText / draftText`
- `assistant_output` 会注入上一轮助手输出的 `assistantOutputText`，用于继续翻译、解释、改写刚生成内容
- `current_draft` 且传入 `studyStage + writingMode` 时，后端会按当前 active rubric 动态加载 assistant 专用 rubric 摘要
- 若当前 `studyStage + writingMode` 没有 active rubric，则跳过 rubric 注入，不回退到其他学段
- 有 `selectedText` 时优先围绕选中内容回答
- 后端会先经过 `EnglishAssistantContextAssembler` 做上下文排序、局部裁剪与 deterministic trimming
- 输入段顺序固定为：
  - task metadata
  - `rubric`
  - `assignment`
  - `selected_text`
  - `draft_excerpt`
  - `assistant_output_excerpt`
  - `recent_turns`
  - `summary`
  - `user_message`
- `rewrite / polish / translate` 直接输出可应用文本
- 其他任务尽量保持自然对话风格

缓存 key：

- `english-answer-general-v1`
- `english-answer-draft-v1`

## Conversation Store

Redis 使用单文档保存会话状态：

- `generalLastResponseId`
- `draftLastResponseId`
- `lastDraftHash`
- `generalLastAssistantOutput`
- `draftLastAssistantOutput`
- `lastArtifactChain`
- `lastArtifactResponseId`
- `lastArtifactText`
- `lastArtifactTaskType`
- `generalRecentTurns`
- `draftRecentTurns`
- `generalSummary`
- `draftSummary`
- `generalTurnCount`
- `draftTurnCount`
- `generalSoftOverflowCount`
- `draftSoftOverflowCount`

Key 设计：

- `peai:english-assistant:state:{conversationId}`

TTL：

- 24 小时

作用：

- 普通英语问答与作文问答分两条会话链
- 草稿 hash 变化后重开 draft 链，避免旧稿污染新稿
- 聊天里刚生成的范文、改写或解释也会被保存，供下一轮“最后一段/刚才那篇”继续引用
- 最近可复用产物会单独保存成 artifact 指针，避免 `assistant_output` 错引用到更早的普通问答结果，也避免在链头已被后续解释覆盖时丢失真正要继续处理的产物
- 当 draft 链因为换稿被清空时，属于旧稿的 draft artifact 指针也会一起失效，避免新稿继续引用旧稿生成内容
- recent turns 只保留小窗口，避免把全历史塞回模型
- summary 只在长会话或连续超预算时晚触发，不是默认每轮生成

## Context Strategy

一期采用“成本优先”的上下文策略：

- Router 不带原始 recent turns，只带轻量摘要字段
- `english_general / session_meta` 最多消费最近 4 个真实 user turns
- `assistant_output` 最多消费最近 2 个真实 user turns
- `current_draft` 最多消费最近 1 个真实 user turn
- 有 `selectedText` 时，默认不再注入整篇 `draft`
- `assistant_output` 优先注入局部片段而不是整篇刚生成内容

超长时的处理顺序：

1. 动态上下文偏大，或 `draft + rubric + recent_turns` 这类重上下文组合出现时，就会优先调用 `/v1/responses/input_tokens`
2. 软上限约 2800 input tokens，先裁 recent turns，再缩 rubric / assignment / excerpt
3. 硬上限约 3600 input tokens，退化到最小回答包

主回答请求额外带 `truncation: auto`，作为预算误判时的最后兜底，避免直接超窗报错。

Summary 的触发条件：

- 同一链累计超过 8 个真实 user turns
- 或连续 2 次组装后超过软上限

Summary 仅用于 `general / draft` 两条链，`assistant_output` 不走 summary。

## Frontend Notes

写作页 AI Chat 已切换到新接口：

- 普通英语问答不再带通用 AI command payload
- 流式事件在前端转换为：
  - 思考中
  - 回复中
  - 完成
  - 失败
- 文本流式展示与最终结构化响应分离

动作仅依赖 `actions[]`，不再从自然语言正文猜测。
