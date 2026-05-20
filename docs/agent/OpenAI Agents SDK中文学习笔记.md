---
title: OpenAI Agents SDK 中文学习笔记
status: draft
owner: ai
last_updated: 2026-05-17
review_cycle: monthly
related_code:
  - python/ai_orchestrator/agents/
  - python/ai_orchestrator/services/agent_session_runner.py
  - python/ai_orchestrator/services/route_decision_runner.py
  - python/ai_orchestrator/assistant_service.py
  - python/ai_orchestrator/services/prompt_sheet_workflow.py
related_docs:
  - docs/agent/路由Agent设计.md
  - docs/ai/openai-agents-request-architecture.md
---

# OpenAI Agents SDK 中文学习笔记

## 当前结论

本文不是 OpenAI 官方文档的逐字全文翻译，而是面向 PEAI 项目的中文学习笔记、术语翻译和实现对照。

官方文档应作为最终依据：

- [OpenAI Agents SDK Intro](https://openai.github.io/openai-agents-python/)
- [Agents](https://openai.github.io/openai-agents-python/agents/)
- [Running agents](https://openai.github.io/openai-agents-python/running_agents/)
- [Handoffs](https://openai.github.io/openai-agents-python/handoffs/)
- [Tracing](https://openai.github.io/openai-agents-python/tracing/)

## 一句话理解

OpenAI Agents SDK 是一个 Python 优先的 agent 应用运行层。它不要求你自己实现 agent loop、tool 调度、handoff、session、结构化输出和 trace，而是把这些能力作为少量核心原语提供出来。

对 PEAI 来说，它适合承载：

- 路由 Agent：把用户请求转成结构化 `RoutingDecision`。
- 能力 Agent：评分、润色、词汇解释、句子结构分析、翻译。
- 工作流：先路由，再调用目标 agent，后续再扩展写作评分链路。
- 可观测性：在 OpenAI Platform Traces 中看到 RouteAgent 和最终 Agent 的调用链。

## 核心术语翻译

| 英文 | 中文理解 | PEAI 中的例子 |
| --- | --- | --- |
| Agent | 带 instructions、model、tools 的 LLM 执行单元 | `RouteAgent`、`Polish Agent`、`Scoring Agent` |
| Runner | 执行 Agent 的运行器 | `Runner.run()`、`Runner.run_streamed()` |
| Tool | Agent 可调用的函数或外部能力 | prompt sheet 的 canvas tool |
| Function tool | 把 Python 函数包装成 tool | `@function_tool` |
| Agents as tools | 把一个 agent 暴露为另一个 agent 的 tool | 旧 Router 的 specialist tools |
| Handoff | 一个 agent 把任务移交给另一个 agent | 旧 Router handoff specialist |
| Guardrail | 输入/输出校验和安全检查 | 暂未作为主链路接入 |
| Session | 跨轮对话状态 | `SQLiteSession` |
| Structured output | 结构化输出 | `RoutingDecision`、`ContinuationDecision` |
| Trace | 一次端到端 workflow 记录 | `PEAI Assistant Workflow` |
| Span | trace 内的一段操作 | agent span、turn span、generation span |
| RunConfig | 单次 run 的配置 | RouteAgent trace metadata |

## 官方文档目录中文导读

| 官方章节 | 中文说明 | PEAI 是否已使用 |
| --- | --- | --- |
| Intro | SDK 定位、安装、Hello world、何时用 SDK 而不是直接用 Responses API | 已参考 |
| Quickstart | 第一个文本 agent 的最小示例 | 已落地为多 agent 结构 |
| Configuration | API key、模型、trace、全局配置 | 部分使用 |
| Agents | Agent 配置、instructions、context、output types、多 agent 模式 | 已使用 |
| Sandbox agents | 隔离工作区里的 agent，适合代码、文件、仓库任务 | 暂未使用 |
| Models | OpenAI 模型和第三方模型接入 | 使用 OpenAI 模型名配置 |
| Tools | function tools、hosted tools、agents as tools | 已部分使用 |
| Guardrails | 输入输出校验、安全边界 | 待接入 |
| Running agents | `Runner.run()`、session、上下文、多轮执行 | 已使用 |
| Streaming | 流式运行和事件处理 | 已使用 |
| Agent orchestration | 多 agent 编排模式，manager、handoff、agents as tools | 已使用部分模式 |
| Handoffs | agent 之间任务移交 | 旧链路已使用，正在收敛 |
| Results | run 结果、final output、run items、usage | 已使用 |
| Human-in-the-loop | 人工审批和中断恢复 | 暂未使用 |
| Sessions | 会话记忆层 | 旧 chat 使用 `SQLiteSession` |
| Context management | 控制传给模型的上下文 | 项目内自定义上下文装配 |
| Usage | token 和调用统计 | 已采集 usage |
| MCP | 连接 MCP server 作为工具来源 | 暂未使用 |
| Tracing | trace、span、flush、高层 workflow trace | 已使用 |
| Realtime agents | 低延迟实时 agent | 暂未使用 |
| Voice agents | 语音输入、agent、TTS 管线 | 暂未使用 |
| Agent visualization | 可视化 agent 结构 | 暂未使用 |
| REPL utility | 本地交互式调试 agent | 暂未使用 |
| Examples | 官方示例集合 | 可作为后续参考 |

## Intro 要点

SDK 的设计目标是：功能足够完整，但原语尽量少；默认可直接工作，同时允许你精确控制运行方式。

官方强调的主能力可以理解为：

- Agent loop：SDK 管理模型调用、tool 调用、结果回传和继续执行。
- Python-first：用普通 Python 代码组合 agent，不需要学习很重的新框架。
- Agents as tools / Handoffs：用于多 agent 协作和任务委派。
- Function tools：把 Python 函数变成模型可调用 tool，并自动生成 schema。
- Sessions：保存同一 agent loop 内的工作上下文。
- Tracing：可视化、调试和监控 agent workflow。
- Realtime / Voice：低延迟语音和实时交互场景。

## Responses API 还是 Agents SDK

如果你只需要一次模型回复，或者想完全自己管理 loop、tool 调度和状态，可以直接用 Responses API。

如果你需要 SDK 管理以下内容，更适合 Agents SDK：

- 多轮 agent loop。
- tool 执行。
- handoff。
- guardrail。
- session。
- 多 agent 协作。
- 结构化结果和 trace。

PEAI 的学习助手属于第二类，因为它需要路由、能力 agent、流式输出、trace 和后续 workflow 编排。

## Agent

Agent 可以理解为一个“被配置好的模型执行单元”。常见配置包括：

- `name`：agent 名称，会显示在 trace 中。
- `instructions`：agent 的系统指令。
- `model`：使用哪个模型。
- `tools`：可调用工具。
- `handoffs`：可移交的目标 agent。
- `output_type`：结构化输出类型。

PEAI 示例：

```python
Agent(
    name="RouteAgent",
    model=model,
    instructions=load_agent_instructions("route_decision"),
    output_type=RoutingDecision,
)
```

这说明 RouteAgent 不应该直接回答用户，而应该输出 `RoutingDecision`。

## Runner

`Runner` 是执行 agent 的入口。

常用方式：

- `Runner.run()`：异步非流式执行。
- `Runner.run_sync()`：同步执行。
- `Runner.run_streamed()`：流式执行。

PEAI 当前用法：

- RouteAgent 用 `Runner.run()` 生成结构化路由结果。
- 学习助手回复用 `Runner.run()` 或 `Runner.run_streamed()`。
- prompt sheet workflow 用 `Runner.run()` 生成题目和聊天响应。

## 结构化输出

`output_type` 可以让 Agent 输出 Pydantic model，而不是自由文本。

PEAI 里最重要的例子是：

```python
output_type=RoutingDecision
```

这会让 RouteAgent 输出类似：

```json
{
  "intent": "polish",
  "route_type": "run_workflow",
  "workflow": "specialist_single_turn",
  "target_agent": "polish",
  "confidence": 0.94,
  "required_inputs": [],
  "missing_inputs": [],
  "reason": "User asked for polishing."
}
```

结构化输出的价值：

- 后端可以稳定消费。
- 可以做 schema 校验。
- 可以写回归测试。
- 可以避免模型把内部路由决策混进用户可见文本。

## Tools

Tool 是 agent 可以调用的确定性能力。

官方常见 tool 类型包括：

- function tool：Python 函数。
- hosted tool：平台托管工具。
- agent as tool：把另一个 agent 包装成 tool。
- MCP tool：来自 MCP server 的工具。

PEAI 当前主要用到：

- `function_tool`：prompt sheet workflow 中把本地函数作为 tool。
- `as_tool()`：旧 Router 把 specialist agent 暴露为工具，用于多意图任务。

设计建议：

- 数据查询、保存、校验优先做成 tool。
- 开放式生成、评分、润色这类能力再做成 agent。
- tool 的输入输出要稳定，避免返回任意自由文本。

## Handoffs

Handoff 是一个 agent 把控制权交给另一个 agent。

PEAI 旧链路里，`Router Agent` 可以 handoff 到：

- `Polish Agent`
- `Sentence Structure Agent`
- `Vocab Agent`
- `Translation Agent`
- `Scoring Agent`
- `Prompt Design Agent`
- `Ability Profile Agent`
- `Learning Planner Agent`

新版路由设计正在减少对旧 handoff 的依赖：先由 `RouteAgent` 输出结构化 `RoutingDecision`，再由后端显式选择目标 agent。这样比让 Router 自由 handoff 更可控。

保留 handoff 时要注意：

- handoff 名称要是合法 tool name。
- agent name 中有空格时，要使用 `tool_name_override`。
- handoff 输入应有明确 schema，例如 `HandoffRoutingMetadata`。

## Agents as tools 和 Handoffs 的区别

| 模式 | 含义 | 适合场景 |
| --- | --- | --- |
| Agents as tools | 主 agent 调用其他 agent 后拿回结果，再自己汇总 | 多意图、多结果整合 |
| Handoffs | 主 agent 把控制权交给另一个 agent | 单一明确任务转交 |

PEAI 当前策略：

- 新主链路优先使用 `RouteAgent -> backend runner -> target agent`。
- 旧 Router 中的 handoff 和 agents as tools 作为兼容路径保留。
- 后续写作 workflow 成熟后，应由 workflow 显式编排多个能力，而不是让 agent 自由互调。

## Sessions

Session 是 SDK 的会话状态能力，用来在多轮 agent loop 中保存上下文。

PEAI 当前旧 chat 入口使用：

```python
SQLiteSession(conversation_id, session_db_path)
```

注意：

- Session 不是完整的用户画像系统。
- Session 更适合短期对话上下文。
- 长期学习画像应沉淀为业务数据，再按需注入 agent。

## Streaming

Streaming 用于边生成边返回。

PEAI 当前通过：

```python
Runner.run_streamed(agent, agent_input, ...)
```

再监听 SDK stream events，把文本 delta 转成前端 SSE：

- `run.started`
- `message.created`
- `message.delta`
- `message.completed`
- `run.completed`

这样前端可以边生成边显示回复。

## Results 和 Usage

SDK run 结果通常包含：

- `final_output`：最终输出。
- `last_agent`：最后执行的 agent。
- `new_items`：本轮新增事件项。
- `raw_responses`：底层模型响应。
- `usage`：token 使用情况。

PEAI 已经从结果中提取：

- `input_tokens`
- `cached_input_tokens`
- `output_tokens`
- `reasoning_tokens`
- `total_tokens`
- `tool_call_count`
- `handoff_count`
- `response_ids`
- `response_models`

这些字段用于日志、成本分析和 trace 验收。

## Tracing

Trace 是一次端到端 workflow 的记录。Span 是 trace 内部的一段操作。

官方默认会记录：

- `Runner.run()` 或 `Runner.run_streamed()` 的整体运行。
- agent 执行 span。
- LLM generation span。
- function tool span。
- guardrail span。
- handoff span。

PEAI 当前有两类 trace：

| 入口 | trace 名称 | 内容 |
| --- | --- | --- |
| `/assistant/route/debug` | `PEAI RouteAgent` | 只看 RouteAgent 输入和 `RoutingDecision` |
| `/assistant/run`、`/assistant/run/stream` | `PEAI Assistant Workflow` | 同一条 trace 中包含 RouteAgent 和最终 capability agent |

## Higher level traces

Higher level trace 的意思是：如果一个业务流程里有多次 `Runner.run()`，可以用外层 `trace()` 把它们包在同一条 trace 里。

PEAI 当前正式链路等价于：

```python
with trace("PEAI Assistant Workflow"):
    route_decision = await Runner.run(route_agent, route_input)
    result = await Runner.run(target_agent, user_input)
```

这样在 OpenAI Platform 里应该看到：

```text
PEAI Assistant Workflow
  RouteAgent
  Polish Agent / Scoring Agent / ...
```

## flush_traces

SDK 默认会批量异步上传 trace。FastAPI 这种长运行服务里，如果希望一次请求结束后马上在平台看到 trace，可以在外层 trace 结束后调用：

```python
flush_traces()
```

PEAI 当前策略：

- `/assistant/route/debug`：RouteAgent run 结束后 flush。
- `/assistant/run` 和 `/assistant/run/stream`：外层 `PEAI Assistant Workflow` trace 结束后统一 flush。
- 内层 RouteAgent 在正式链路里不单独 flush，避免把链路拆成两条 trace。

## Sensitive data

Trace 可能包含模型输入、输出和 tool 输入输出。对于作文正文、题目、用户选中文本，这些都可能是敏感数据。

当前开发验收阶段可以保留：

```python
trace_include_sensitive_data=True
```

生产环境需要评估是否改为：

```python
trace_include_sensitive_data=False
```

或者只在 metadata 中保留布尔标记，例如：

- `has_essay_text`
- `has_topic_prompt`
- `has_selected_text`

不要把完整作文正文重复塞进 trace metadata。

## PEAI 当前已使用的 SDK 能力

| SDK 能力 | 文件 | 用途 |
| --- | --- | --- |
| `Agent` | `agents/route_decision.py` | 定义 RouteAgent |
| `Agent` | `agents/specialists.py` | 定义 specialist agents |
| `output_type` | `agents/route_decision.py` | 结构化输出 `RoutingDecision` |
| `Runner.run()` | `services/route_decision_runner.py` | 执行 RouteAgent |
| `Runner.run()` | `services/agent_session_runner.py` | 非流式执行学习助手 |
| `Runner.run_streamed()` | `services/agent_session_runner.py` | 流式执行学习助手 |
| `RunConfig` | `services/route_decision_runner.py` | 配置 RouteAgent trace |
| `trace()` | `assistant_service.py` | 创建 `PEAI Assistant Workflow` |
| `flush_traces()` | `assistant_service.py`、`route_decision_runner.py` | 请求结束后主动上传 trace |
| `handoff()` | `agents/specialists.py` | 旧 Router handoff |
| `tool_name_override` | `agents/specialists.py` | 修复 handoff tool name |
| `as_tool()` | `agents/specialists.py` | specialist agents as tools |
| `SQLiteSession` | `services/agent_session_runner.py` | 旧 chat 会话上下文 |
| `function_tool` | `services/prompt_sheet_workflow.py` | prompt sheet 本地工具 |
| `RunContextWrapper` | `agents/specialists.py`、`prompt_sheet_workflow.py` | handoff/tool 上下文 |

## 推荐学习顺序

1. 先读 Intro，理解 SDK 和 Responses API 的边界。
2. 再读 Agents，理解 `Agent`、instructions、tools、output types。
3. 再读 Running agents，理解 `Runner.run()`、session、run result。
4. 再读 Tracing，理解 trace、span、flush、sensitive data。
5. 再读 Handoffs 和 Agent orchestration，判断什么时候用 handoff，什么时候由后端 workflow 显式编排。
6. 最后读 Tools 和 Guardrails，把业务查询、校验、安全边界沉淀出来。

## PEAI 后续建议

- 继续让 RouteAgent 只输出结构化路由，不直接回答用户。
- 写作总 workflow 成熟后，用 workflow 显式串联 Scoring、Diagnosis、Revision Plan、Polish、Practice。
- 对评分、润色、练习生成等关键 agent 逐步引入结构化输出。
- 把长期用户画像做成业务数据和检索注入，不要直接依赖 SDK session 作为长期记忆。
- 给关键 workflow 建 eval 或回归样例，验证路由、结构化输出、trace 和最终用户可见回复。
- 生产前评估 trace 敏感数据策略。

## 相关官方资料

- [OpenAI Agents SDK Intro](https://openai.github.io/openai-agents-python/)
- [Agents](https://openai.github.io/openai-agents-python/agents/)
- [Running agents](https://openai.github.io/openai-agents-python/running_agents/)
- [Streaming](https://openai.github.io/openai-agents-python/streaming/)
- [Agent orchestration](https://openai.github.io/openai-agents-python/multi_agent/)
- [Handoffs](https://openai.github.io/openai-agents-python/handoffs/)
- [Results](https://openai.github.io/openai-agents-python/results/)
- [Sessions](https://openai.github.io/openai-agents-python/sessions/)
- [Tracing](https://openai.github.io/openai-agents-python/tracing/)
