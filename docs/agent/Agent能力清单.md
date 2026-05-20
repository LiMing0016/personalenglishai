---
title: Agent 能力清单
status: active
owner: ai
last_updated: 2026-05-20
review_cycle: on-change
related_code:
  - python/ai_orchestrator/agents/
  - python/ai_orchestrator/services/
  - python/ai_orchestrator/prompts/
related_docs:
  - docs/agent/Agent产品现状与路线图.md
  - docs/agent/路由Agent设计.md
  - docs/agent/Agent可观测性与调试中心.md
  - docs/agent/写作教练Agent设计.md
---

# Agent 能力清单

## 用途

本文是 PEAI Agent 的能力台账，用来区分：

- 已经可用的 Agent 能力。
- 已经设计但仍需补实现的能力。
- 当前不做或暂缓的能力。
- 每个能力的入口、输出、缺口和验证方式。

状态口径：

| 状态 | 含义 |
| --- | --- |
| 已实现 | 主链路已有代码承载，可在产品流程中调用。 |
| 初步实现 | 有基础代码或页面，但能力不完整。 |
| 设计中 | 有设计文档，尚未形成稳定实现。 |
| 待接入 | 明确需要，但还没有接入主链路。 |
| 暂不做 | 当前阶段不进入实现范围。 |

## 请求入口

| 入口 | 状态 | 职责 | 缺口 |
| --- | --- | --- | --- |
| `run_assistant_request` | 已实现 | 非流式正式 Agent 请求入口。 | 需要继续统一旧链路能力。 |
| `stream_assistant_request` | 已实现 | 流式正式 Agent 请求入口。 | 需要补齐错误事件和调试事件口径。 |
| `chat` | 已实现 | 历史聊天兼容入口，承载多轮会话、附件、active task continuation。 | 需要逐步收敛为兼容层。 |

## Agent 清单

| Agent | 状态 | 职责 | 输入 | 输出 | 当前缺口 |
| --- | --- | --- | --- | --- | --- |
| Router Agent | 已实现 | 识别用户意图，选择目标 Agent 或 workflow。 | 用户消息、上下文、可用 specialist。 | handoff / tool result / 直接回答。 | 与 Route Agent / 规则路由职责需收敛。 |
| Route Agent | 已实现 | 输出结构化 `RoutingDecision`。 | `RouteRequest`。 | intent、route_type、workflow、target_agent、missing_inputs。 | 需要扩大 eval case 覆盖。 |
| Polish Agent | 已实现 | 润色、改写、表达升级。 | 原句或选中文本、目标风格。 | 润色结果和解释。 | 输出结构需要进一步标准化。 |
| Sentence Structure Agent | 已实现 | 讲解句子结构、语法结构、长难句。 | 句子或段落。 | 结构拆解和讲解。 | 需要按学段控制解释深度。 |
| Vocab Agent | 已实现 | 单词、短语、搭配、辨析。 | 词汇或表达。 | 释义、例句、用法差异。 | 需要接入个人词库或学习记录。 |
| Translation Agent | 已实现 | 中英互译、译文解释。 | 原文、方向、学习目标。 | 译文和解释。 | 需要更稳定地区分翻译与讲解。 |
| Scoring Agent | 已实现 | 作文评分、诊断、建议。 | 作文、题目、考试类型。 | 分数、诊断、改进建议。 | rubric 按需加载和评分一致性不足。 |
| Prompt Design Agent | 已实现 | 练习设计、题目设计、题单生成和训练任务设计。 | 学习目标、考试类型、题型。 | 练习题、题目或训练要求。 | 与 Writing Coach Agent 的已有题目陪写边界需保持清晰。 |
| Writing Coach Agent | 设计中 | 从零到一陪用户完成作文，按学段和考试标准推进审题、构思、提纲、分段陪写、草稿合成和偏题检查。 | 题目、学段、考试模式、当前正文、WritingTaskMetadata、rubric。 | 提纲、段落建议、完整草稿、偏题风险提醒。 | 尚未接入独立 Agent、工具化偏题检查和前端应用正文按钮。 |
| Ability Profile Agent | 已实现 | 解释用户能力画像。 | 用户画像摘要、历史表现。 | 能力解释和建议。 | 长期画像数据不足。 |
| Learning Planner Agent | 已实现 | 学习计划、路径规划。 | 目标、时间、能力状态。 | 学习计划。 | 需要真实学习进度和复习记录支撑。 |

## Workflow 清单

| Workflow | 状态 | 触发条件 | 执行链路 | 缺口 |
| --- | --- | --- | --- | --- |
| 自由问答 | 已实现 | 用户提出一般英语学习问题。 | Router / Route Agent -> 目标 Agent 或直接回答。 | 需要范围外和闲聊边界更稳定。 |
| 翻译 | 已实现 | intent 为 translation 或用户明确要求翻译。 | Route -> Translation Agent。 | 多轮继续解释需要更稳定。 |
| 润色 | 已实现 | intent 为 polish 或有选中文本改写诉求。 | Route -> Polish Agent。 | 风格、考试场景和用户水平需结构化传入。 |
| 写作评分 | 已实现 | 用户提交作文或要求评分。 | Route -> Scoring Agent。 | rubric、题型、考试目标需工具化。 |
| 审题 / 题目分析 | 已实现 | 用户要求分析题目本身或设计训练题。 | Route -> Prompt Design Agent。 | 如果题目分析服务于已有作文陪写，应转入 Writing Coach Agent。 |
| 作文从零到一陪写 | 设计中 | 用户要求开头、提纲、下一段、从零写作文。 | Route -> Writing Coach Agent -> 多轮审题/构思/提纲/分段/合成。 | 需要接入写作上下文、WritingTaskMetadata、偏题检查和草稿应用交互。 |
| 学习计划 | 初步实现 | 用户要求规划学习路径。 | Route -> Learning Planner Agent。 | 缺少长期学习状态和目标拆解数据。 |
| 能力画像解释 | 初步实现 | 用户询问自己水平或弱项。 | Route -> Ability Profile Agent。 | 缺少稳定画像数据来源。 |
| Model Sandbox | 设计中 | 开发者选择历史 run 或 eval case 重跑。 | Debug Run -> Sandbox -> 候选模型 / prompt 对比。 | 数据闭环和 UI 仍需实现。 |
| Eval 回归 | 待接入 | prompt、模型、路由规则调整后。 | Eval Case -> Runner -> Grader -> 报告。 | 缺少标准 eval case 和 grader。 |

## 工具与外部能力

| 能力 | 状态 | 用途 | 缺口 |
| --- | --- | --- | --- |
| `@function_tool` | 已使用 | 将 Python 函数暴露为 Agents SDK tool。 | 当前工具数量少，业务上下文仍多靠 prompt。 |
| Agents as tools | 已使用 | Router 可把 specialist 当工具调用。 | 多 Agent 汇总的结构化输出需加强。 |
| Handoffs | 已使用 | Router 可把任务移交给 specialist。 | 需要明确哪些场景用 handoff，哪些用 tool。 |
| SQLiteSession | 已使用 | 旧 chat 多轮会话状态。 | 与新 `AssistantRequest` 链路的状态口径需统一。 |
| OpenAI Tracing | 已接入 | 官方 trace / span 排查。 | metadata 字段需持续保持短小、可索引。 |
| Langfuse | 可选接入 | 外部 tracing、prompt、实验分析。 | 需要和本地 debug run 形成稳定关联。 |

## 验证要求

| 类型 | 当前要求 |
| --- | --- |
| 路由 | 覆盖常见 intent、缺输入、范围外、多轮继续。 |
| 输出 | Specialist 输出应符合面向学习者的中文解释要求。 |
| 观测 | 每次正式请求应能关联 run id、trace id、目标 Agent 和模型。 |
| 回归 | 修改 prompt、路由 schema、target agent 时应补 eval case 或单测。 |

## 维护规则

- 新增 Agent 时，必须补充 Agent 清单、Workflow 清单和验证要求。
- 修改 Agent 职责时，必须同步更新 [Agent 产品现状与路线图](./Agent产品现状与路线图.md)。
- 删除或废弃能力时，不要直接从表格移除，先标记为暂不做或已废弃，并说明原因。
