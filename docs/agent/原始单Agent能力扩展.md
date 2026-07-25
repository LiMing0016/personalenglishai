---
title: 原始单 Agent 能力扩展
status: implemented
owner: ai
last_updated: 2026-07-24
related_code:
  - python/ai_orchestrator/agents/raw_single.py
  - python/ai_orchestrator/raw_assistant_service.py
  - python/ai_orchestrator/services/agent_session_runner.py
  - web/src/pages/app/AssistantPage.vue
related_docs:
  - docs/superpowers/specs/2026-07-24-single-agent-raw-ablation-design.md
---

# 原始单 Agent 能力扩展

## 目标

在不修改现有多 Agent 编排的前提下，把原始模型作为一条可持续增加能力的单 Agent 路线：

- 始终只有一个原始 Agent。
- 不注入 PEAI 应用级 Prompt。
- 不使用路由、handoff 或业务工作流。
- 已接入 OpenAI Agents SDK 的托管网页搜索工具与本地 MCP Fetch 网页抓取能力。
- 继续使用 SDK Session 管理同一对话的上下文。

后续增加单词卡片、口语练习等能力时，继续向这个 Agent 添加工具，不新增平行运行模式。

## 两种运行模式

| 模式代码 | 前端名称 | Prompt | 工具 | 路由与 handoff |
| --- | --- | --- | --- | --- |
| `multi_agent` | 多 Agent | 现有业务 Prompt | 现有业务工具 | 有 |
| `single_agent_raw` | 原始模型 | 无 PEAI Prompt | `WebSearchTool`、MCP Fetch，后续继续扩展 | 无 |

前端切换模式时新建对话，避免不同实验组共享上下文。

## 运行链路

```text
Assistant API
    |
    v
AssistantRuntime
    |
    +-- multi_agent --------> AssistantAgentService
    |
    +-- single_agent_raw ---> RawSingleAgentService
                                  |
                                  v
                  Raw Agent + WebSearchTool + MCP Fetch
                                  |
                                  v
                         OpenAI Responses API
```

原始模型继续使用原来的 Session 命名空间：

```text
single-raw:{conversationId}
```

后端不再重复拼接数据库中的会话历史，历史由 Agents SDK Session 提供。

## 搜索、抓取与来源

Web Search 与 MCP Fetch 都属于同一个 Raw Single Agent 的工具集，不是第三个 Agent 模式；前端仍只提供“多 Agent”和“原始模型”两个选项。

| 能力 | 实现 | 用途 |
| --- | --- | --- |
| 网页搜索 | `WebSearchTool` | 搜索最新信息和发现页面 |
| 网页抓取 | `mcp-server-fetch` | 读取用户指定 URL 的网页正文 |

模型自行判断当前问题是否需要搜索：

- 实时天气、近期事件、最新文档等问题可以调用 `web_search`。
- Web Search 用于搜索和发现页面；对于用户已指定的 URL，MCP Fetch 用于读取网页正文。
- 算术、常识或纯上下文问题不要求调用工具。
- 工具调用写入回复元数据中的 `tool_calls` 步骤，便于调试和后续评测。
- SDK 返回 URL citation annotation 时，回答保留模型生成的行内引用；如果正文没有包含对应链接，服务会补充“来源”列表。
- 搜索提供方没有返回 URL annotation 时，不生成伪造来源。

## MCP Fetch 开关与安全边界

MCP Fetch 默认关闭。本地实验时可设置：

```text
AI_ASSISTANT_RAW_FETCH_MCP_ENABLED=true
```

开启后，Raw Single Agent 才会连接本地 `mcp-server-fetch`。创建、连接或工具发现失败时，服务会回退到普通 Raw Agent，普通对话不会因此被阻断。已开始的工具调用如果失败，由 SDK 作为工具错误交给模型处理；系统不自动重放整轮请求，避免会话重复写入。

> 安全警告：官方 Fetch Server 可访问本地和内网 IP，当前直连方式仅适用于本地实验，不适合公网发布。生产前至少需要限制 URL 协议、校验 DNS 解析 IP、校验每一跳重定向、限制响应大小、设置超时和并发限制，以防范 SSRF 与资源耗尽。

## 能力边界

原始模型当前已经具备：

- 实时网页搜索。
- 用户指定 URL 的网页正文抓取（本地实验开关开启时）。
- 同一对话内的上下文延续。
- 搜索调用轨迹。
- 可点击的来源链接。
- 搜索失败时沿用现有错误协议。

当前模式暂不具备：

- 天气、词典等专用业务工具。
- 单词卡片、练习卡片等结构化学习产物。
- 跨对话、跨设备长期记忆。
- 来源可信度评级和多来源事实核验。
- 生产环境的搜索额度、费用和速率治理。

## 验证记录

2026-07-24 使用本地已有 OpenAI 配置和 `gpt-5.4-mini` 验证：

1. 查询安顺市当天实时天气，运行轨迹包含一次 `web_search`。
2. 查询 OpenAI Agents SDK 的 `WebSearchTool`，回答返回 OpenAI 官方文档链接。
3. 使用 `fetch` 读取 IANA 的 Example Domains 页面，运行轨迹记录
   `toolNames=["fetch"]`，并正确概括页面标题和正文。
4. 在同一会话中追问“刚才页面的标题”，模型能够继续引用上一轮网址。
5. 询问 `2 + 2`，没有触发网页搜索或抓取。
6. 模拟系统找不到 `uvx` 时，Fetch 初始化失败会回退普通 Raw Agent，
   对话仍正常返回结果。
7. 页面只保留“多 Agent”和“原始模型”两个选项，没有单独的“联网 Agent”。

密钥只加载到本地进程环境，没有写入仓库、日志或测试输出。
