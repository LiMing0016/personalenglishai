---
title: PEAI Agent 工作流产品现状报告
status: active
owner: ai
last_updated: 2026-05-17
review_cycle: on-change
related_code:
  - python/ai_orchestrator/assistant_service.py
  - python/ai_orchestrator/agents/
  - python/ai_orchestrator/prompts/
  - python/ai_orchestrator/services/
related_docs:
  - docs/agent/学习助手Agent编排架构.md
  - docs/ai/prompt-management.md
  - docs/ai/openai-agents-request-architecture.md
---

# PEAI Agent 工作流产品现状报告

## 一句话结论

当前项目已经从“单一聊天助手”进入 **多 Agent 编排雏形阶段**。

核心链路能跑，Router、Specialist Agents、Dynamic Instructions、Prompt Resolver、基础 tracing 都已接入；但还没到“稳定生产级智能学习系统”，主要缺口是 **评分标准按需加载、检索、工具化上下文、eval 验证、Prompt 版本治理和实验体系**。

## 当前阶段判断

| 维度 | 当前状态 | 产品阶段 |
|---|---|---|
| 多 Agent 架构 | 已搭建 | 可用原型 |
| 用户请求入口 | 已有新旧两套入口 | 迁移中 |
| 路由判断 | Route Decision + Router Agent 并存 | 需要收敛 |
| 专职 Agent | 8 个能力 Agent 已定义 | 可用 |
| 动态上下文 | 学段、考试模式已接入 | 初版 |
| 多轮记忆 | 正式 `AssistantRequest` 链路使用后端历史窗口，旧 `chat` 链路保留 SDK session | 初版 |
| Prompt 管理 | local / hybrid / remote resolver 已有 | 基础治理 |
| 可观测性 | run / trace / route metadata 已有 | 初步可排查 |
| 稳定性验证 | 单测有，但 eval harness 不完整 | 不足 |

产品上可以定义为：

```text
当前阶段：P1 可用编排原型 / 内测可用
还不是：P2 稳定运营级学习 Agent 系统
```

## 1. 用户请求如何进入 `AssistantAgentService`

当前有两条入口。

### 新入口：`run_assistant_request` / `stream_assistant_request`

这是更接近正式产品链路的入口，处理结构化 `AssistantRequest`：

```text
AssistantRequest
-> validate_assistant_request
-> route_assistant_agent
-> RouteDecisionRunner
-> 选择目标 Agent
-> build_assistant_input_items
-> run_agent / stream_agent
-> AssistantReply
```

它支持：

- `run_id`
- `trace_id`
- 后端注入的最近对话历史窗口
- structured metadata
- route request
- routing decision
- streaming events
- target agent metadata

这是未来主链路。

当前实现中，Java 后端会在调用 `run_assistant_request` / `stream_assistant_request` 前，从当前 `conversationUid` 读取最近已完成的 user / assistant 消息，写入 `AssistantRequest.conversationHistory`。Python 再把这些历史消息转成 Responses API input items，并把当前用户消息追加到最后。

该策略是滑动窗口，不是无限长记忆。超出窗口的长链路学习对话，后续应通过会话摘要、active task 或学习资产摘要补足。

### 旧入口：`chat`

旧入口更像历史聊天兼容层：

```text
message + conversation_id + attachments
-> Router Agent 或 Attachment Agent
-> build_contextual_user_message
-> run_agent_session
```

它仍然承担多轮会话、附件、active task continuation 等能力。

产品判断：**当前处于新旧入口并存阶段**。后续应该逐步让正式学习助手统一走 `AssistantRequest` 链路。

## 2. `AssistantRunContext` 保存了哪些上下文

当前 `AssistantRunContext` 很轻：

```python
conversation_id: str
study_stage: str | None
assistant_mode: str | None
```

它的作用不是直接发给模型，而是作为 Agents SDK 的本地运行上下文，供 dynamic instructions 读取。

当前已覆盖：

- 会话 ID
- 学段
- 模式，例如考试模式

当前未覆盖但产品上后续需要：

- `examType`，例如考研、雅思、四六级
- `taskType`，例如 writing、vocab、translation
- `promptType`，例如 chart、letter、argument
- user ability profile
- 是否询问评分标准
- 当前页面业务上下文
- 当前作文、题目、选中文本摘要

产品判断：**上下文框架有了，但上下文字段还比较薄**。

## 3. Router / Route Decision 如何判断目标任务

当前有两层路由。

### 第一层：规则路由 `route_assistant_agent`

它根据 `request.intent` 和 `request.mode` 做粗路由：

```text
translate -> Translation Agent
polish -> Polish Agent
grade_writing -> Scoring Agent
analyze_question -> Prompt Design Agent
exam_boost -> Scoring Agent
默认 -> Router Agent
```

这是确定性兜底路由。

### 第二层：模型路由 `RouteDecisionRunner`

它把 `AssistantRequest` 转成 `RouteRequest`，交给 `RouteAgent` 输出结构化 `RoutingDecision`：

```text
intent
route_type
workflow
target_agent
confidence
required_inputs
missing_inputs
normalized_inputs
reason
```

它可以表达：

- 直接运行 workflow
- 追问缺失信息
- 直接回答
- 拒绝非英语学习任务

产品判断：**路由能力已经进入结构化阶段，但规则路由和模型路由职责还重叠**。后续需要明确谁是主路由，谁是兜底。

## 4. Specialist Agents 如何承接具体学习任务

当前定义了 8 个专职 Agent：

| Agent | 负责能力 |
|---|---|
| Polish Agent | 润色、改写、表达升级 |
| Sentence Structure Agent | 句子结构、语法结构、长难句 |
| Vocab Agent | 单词、短语、搭配、辨析 |
| Translation Agent | 中英互译、译文解释 |
| Scoring Agent | 作文评分、诊断、建议 |
| Prompt Design Agent | 练习设计、题目设计 |
| Ability Profile Agent | 能力画像解释 |
| Learning Planner Agent | 学习计划、路径规划 |

Router Agent 同时把这些 Specialist 注册为：

```text
handoffs
tools
```

也就是说它既可以把对话转交给某个 Agent，也可以在多意图场景下把 Agent 当工具调用。

产品判断：**专职能力拆分已经合理，下一步要补结构化输出和质量评估**，否则多 Agent 汇总时稳定性不够。

## 5. `resolve_agent_prompt_kwargs` 如何决定 Prompt

这个函数是当前 Prompt 管理的核心适配层。

它根据：

```text
AI_ASSISTANT_PROMPT_SOURCE=local | hybrid | remote
```

决定 Agent 创建时使用什么配置。

### local

读取本地 Markdown prompt：

```text
agent_instructions/router.md
agent_instructions/polish.md
...
```

返回：

```python
{"instructions": "..."}
```

### dynamic local

如果 `dynamic=True`，返回 callable instructions：

```python
{"instructions": load_dynamic_agent_instructions(agent_key)}
```

运行时会读取 `AssistantRunContext`，拼入 Runtime Learning Context。

### remote

读取环境变量中的 OpenAI Platform Prompt：

```text
AI_PROMPT_ROUTER_ID
AI_PROMPT_ROUTER_VERSION
AI_PROMPT_ROUTER_VARIABLES_JSON
```

返回：

```python
{"prompt": {"id": "...", "version": "..."}}
```

### hybrid

优先 remote，没配置则回退 local。

产品判断：**Prompt Resolver 基础能力已经实现**。

但它还不是完整 Prompt Platform，暂时没有 A/B Test、灰度分流、自动回滚、运行时热切策略。

## 6. Runtime Learning Context 如何进入模型

当前有两条路径。

### 路径 A：进入 user message

旧 `chat` 链路会调用：

```text
build_contextual_user_message
```

把学段、考试模式拼到用户消息前面。

### 路径 B：进入 dynamic instructions

本地 prompt 模式下，Agent 创建时使用：

```text
resolve_agent_prompt_kwargs(..., dynamic=True)
```

然后 dynamic instructions 会读取：

```text
AssistantRunContext.study_stage
AssistantRunContext.assistant_mode
```

并生成：

```text
# Runtime Learning Context
[用户画像上下文]
- 学段: ...
[学段输出标准]
...
[对话模式上下文]
- 当前模式: 考试模式
```

产品判断：**动态上下文已经接入，但存在重复注入风险**。同一类上下文可能同时出现在 user message 和 instructions 中，后续应该统一到一个 RuntimeContextBuilder。

## 当前产品成熟度

建议把当前阶段定义为：

```text
Agent Orchestration P1：可用编排骨架
```

已经完成：

- 多 Agent 拆分
- Router Agent
- Route Decision 结构化输出
- Specialist Agents
- Dynamic Instructions 初版
- 正式 `AssistantRequest` 链路的后端历史窗口多轮记忆
- local / remote / hybrid Prompt Resolver
- 基础 trace metadata
- streaming 输出
- active task continuation 初版

尚未完成：

- Function tools 按需读取考试标准、rubric、用户画像
- File Search / Retrieval 检索标准片段
- 附件链路的安全 session 策略
- 路由 eval harness
- Specialist 结构化输出 schema
- Prompt A/B Test
- Prompt 发布、回滚、灰度机制
- RouteDecision 与 Router Agent 职责收敛
- 上下文注入去重

## 产品建议

短期不要先做 A/B Test 或复杂热加载。

优先级应该是：

```text
P0：收敛路由链路，明确 RouteDecision 是主路由还是辅助路由
P0：统一 Runtime Learning Context 注入路径
P1：接入 Function Tools 读取 rubric / 用户画像 / 考试标准
P1：建立 eval harness，验证路由和输出格式稳定性
P2：做 Prompt 版本发布、回滚和远程同步
P3：做 A/B Test 和实验平台
```

整体判断：项目方向是对的，已经不是简单 prompt 拼接，但现在仍属于 **“工程骨架已成型，产品稳定性还需要补验证和上下文工具化”** 的阶段。
