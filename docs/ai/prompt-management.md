---
title: Prompt 管理与远程发布
status: active
owner: ai
last_updated: 2026-05-16
review_cycle: on-change
related_code:
  - python/ai_orchestrator/prompts/
  - python/ai_orchestrator/agents/
  - python/ai_orchestrator/services/prompt_sheet_workflow.py
related_docs:
  - docs/ai/openai-agents-request-architecture.md
  - docs/runbooks/environment-variables.md
---

# Prompt 管理与远程发布

本文定义 Python `ai_orchestrator` 的 Prompt 资产、OpenAI 远程 Prompt 发布方式和回退规则。

## 当前结论

Prompt 的权威源仍然是仓库内 Markdown 文件。OpenAI 远程 Prompt 只作为 OpenAI 平台运行时的发布版本，不替代 Git 评审、测试和文档。

默认运行模式是 `local`，行为与原有实现一致。需要验证远程 Prompt 时使用 `hybrid`，只给目标 agent 配置 Prompt ID；生产完全依赖远程 Prompt 前，再评估是否切到 `remote`。

## 适用范围

适用：

- `python/ai_orchestrator/prompts/agent_instructions/*.md`
- `python/ai_orchestrator/prompts/shared/*.md`
- Agents SDK `Agent.prompt`
- 学习助手 router、route decision、capability agent、prompt sheet agent 的系统指令

不适用：

- 用户作文正文
- 用户画像原文
- 当前选中文本
- 会话历史
- provider 兼容逻辑
- tool、handoff、schema 和 workflow 的 Python 定义

## 运行模式

| 模式 | 行为 | 建议用途 |
| --- | --- | --- |
| `local` | 始终使用仓库内 Markdown 组装 `instructions`。 | 默认、本地开发、非 OpenAI-compatible provider。 |
| `hybrid` | 有 Prompt ID 且 base URL 指向 `api.openai.com` 时使用远程 Prompt，否则回退本地。 | 灰度验证 router 或 route decision。 |
| `remote` | 每个被创建的 agent 必须有远程 Prompt，否则报错。 | 生产强约束发布，需完整配置后使用。 |

`AI_ASSISTANT_REMOTE_PROMPT_STRICT=true` 会让 `hybrid` 在缺少远程配置或 base URL 非 OpenAI 平台时直接失败，适合发布前验收。

## 环境变量

远程 Prompt 通过 agent key 映射环境变量：

| Agent key | Prompt ID | Version | Variables |
| --- | --- | --- | --- |
| `router` | `AI_PROMPT_ROUTER_ID` | `AI_PROMPT_ROUTER_VERSION` | `AI_PROMPT_ROUTER_VARIABLES_JSON` |
| `route_decision` | `AI_PROMPT_ROUTE_DECISION_ID` | `AI_PROMPT_ROUTE_DECISION_VERSION` | `AI_PROMPT_ROUTE_DECISION_VARIABLES_JSON` |
| `prompt_sheet_canvas` | `AI_PROMPT_PROMPT_SHEET_CANVAS_ID` | `AI_PROMPT_PROMPT_SHEET_CANVAS_VERSION` | `AI_PROMPT_PROMPT_SHEET_CANVAS_VARIABLES_JSON` |

其他 agent key 也按同样规则转换：转大写，非字母数字字符改成 `_`。

## 发布流程

1. 先修改仓库内 Prompt 文件。
2. 补充或更新最小回归测试。
3. 在 OpenAI Dashboard 创建或更新对应 Prompt。
4. 把远程 Prompt 版本固定到环境变量。
5. 本地用 `AI_ASSISTANT_PROMPT_SOURCE=hybrid` 验证目标链路。
6. 发布前确认 trace、结构化输出和回退策略。

生产建议固定 `AI_PROMPT_<AGENT_KEY>_VERSION`，不要依赖远程最新版本自动漂移。

## 回退规则

`hybrid` 模式下，如果没有 Prompt ID，或者 `OPENAI_BASE_URL` / `AI_PROVIDER_OPENAI_BASE_URL` 不是 OpenAI 平台地址，系统使用本地 Prompt。

这能保证 Kimi、Qwen 或其他 OpenAI-compatible base URL 不会误用 OpenAI 远程 Prompt。

## 验收要求

Prompt 管理相关改动至少验证：

- local 模式仍返回 `instructions`。
- hybrid 模式配置 Prompt ID 后返回 Agents SDK `prompt`。
- hybrid 模式缺少 Prompt ID 时回退本地。
- remote 或 strict 模式缺配置时明确失败。
- `route_decision` 的 `RoutingDecision` 结构化输出契约不变。

## 官方依据

- OpenAI Prompt 支持长期保存、版本化、变量和 API 引用。
- OpenAI Agents SDK 的 `Agent.prompt` 支持引用 OpenAI Prompt，但仅适用于 OpenAI models 与 Responses API 路径。

参考：

- https://developers.openai.com/api/docs/guides/prompting
- https://openai.github.io/openai-agents-python/agents/#prompt-templates
