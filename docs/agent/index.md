---
title: Agent 设计
status: draft
owner: ai
last_updated: 2026-05-20
review_cycle: monthly
related_code:
  - python/ai_orchestrator/
related_docs:
  - docs/agent/学习助手Agent编排架构.md
  - docs/agent/原始单Agent能力扩展.md
  - docs/agent/Agent产品现状与路线图.md
  - docs/agent/Agent能力清单.md
  - docs/agent/英语班主任.md
  - docs/agent/写作教练Agent设计.md
  - docs/agent/写作教练Schema设计.md
  - docs/agent/写作路由与Patch方案.md
  - docs/agent/agent-builder/index.md
  - docs/agent/FunctionCall学习笔记.md
  - docs/agent/StructuredOutput学习笔记.md
---

# Agent 设计

本目录是 PEAI Agent 的产品与技术设计中心，用来回答三类问题：

- 当前 Agent 已经实现了什么，哪些还只是设计或规划。
- 每个 Agent、workflow、prompt、调试能力的职责边界是什么。
- 后续迭代应该优先补哪些能力，怎么验证质量没有退化。

## 阅读顺序

| 顺序 | 文档 | 用途 |
| --- | --- | --- |
| 1 | [Agent 产品现状与路线图](./Agent产品现状与路线图.md) | 看当前阶段、已实现能力、缺口和后续规划。 |
| 2 | [学习助手 Agent 编排架构](./学习助手Agent编排架构.md) | 看当前 Python Agent 编排、字段字典、路由链路和流程图。 |
| 3 | [原始单 Agent 能力扩展](./原始单Agent能力扩展.md) | 看原始模型如何在同一个 Agent 中逐步增加搜索和后续工具能力。 |
| 4 | [Agent 能力清单](./Agent能力清单.md) | 看每个 Agent、workflow、工具和观测能力的状态。 |
| 5 | [Hermes 长期英语学习班主任接入方案](./英语班主任.md) | 看 Hermes 如何作为长期学习画像、每日任务和周复盘 Provider 接入 PEAI。 |
| 6 | [写作教练 Agent 设计](./写作教练Agent设计.md) | 看从零到一作文陪写、学段/考试标准、偏题检查和多轮循环方案。 |
| 7 | [写作教练 Schema 设计](./写作教练Schema设计.md) | 看写作教练 input context、审题 output、提纲 output 和字段字典。 |
| 8 | [写作路由与 Patch 方案](./写作路由与Patch方案.md) | 看 WritingIntentPlanner、WritingPatch 和 Patch 应用器如何协作。 |
| 9 | [路由 Agent 设计](./路由Agent设计.md) | 看路由 Agent 的输入、输出、模型配置边界和工作流决策 schema。 |
| 10 | [Agent 可观测性与调试中心](./Agent可观测性与调试中心.md) | 看 Agent Debug Recorder、Eval Dataset Builder、DeepEval 和 Langfuse 接入方案。 |
| 11 | [AI 调试端设计](./AI调试端设计.md) | 看 `/ops/agent/*` 的页面结构、使用边界和数据接入顺序。 |
| 12 | [OpenAI Agents SDK 中文学习笔记](./OpenAI Agents SDK中文学习笔记.md) | 查官方 SDK 概念、术语翻译和 PEAI 代码用法映射。 |
| 13 | [Function Call 学习笔记](./FunctionCall学习笔记.md) | 学习 function/tool calling 的调用链、schema、工具边界和 PEAI 落地方式。 |
| 14 | [Structured Output 学习笔记](./StructuredOutput学习笔记.md) | 学习结构化输出、schema 约束、Agents SDK output_type 和 PEAI 输出契约设计。 |
| 15 | [Agent Builder 学习资料](./agent-builder/) | 学习 OpenAI Platform 可视化工作流、节点、变量和 PEAI 映射方式。 |
| 16 | [对话词句采集清洗方案](./数据清洗/对话词句采集清洗方案.md) | 看对话词句如何从 Agent 回复中采集、清洗、评分并落库。 |

## 文档分层

| 类型 | 负责回答 | 当前文档 |
| --- | --- | --- |
| 产品总账 | 当前做到哪，欠什么，下一期做什么 | [Agent 产品现状与路线图](./Agent产品现状与路线图.md) |
| 能力台账 | 每个 Agent / workflow / tool 是否可用 | [Agent 能力清单](./Agent能力清单.md) |
| 技术设计 | 模块边界、数据流、schema、运行链路 | [学习助手 Agent 编排架构](./学习助手Agent编排架构.md)、[原始单 Agent 能力扩展](./原始单Agent能力扩展.md)、[Hermes 长期英语学习班主任接入方案](./英语班主任.md)、[路由 Agent 设计](./路由Agent设计.md)、[写作教练 Agent 设计](./写作教练Agent设计.md)、[写作教练 Schema 设计](./写作教练Schema设计.md)、[写作路由与 Patch 方案](./写作路由与Patch方案.md) |
| 调试与观测 | 如何记录、回放、评估、排查 Agent run | [Agent 可观测性与调试中心](./Agent可观测性与调试中心.md)、[AI 调试端设计](./AI调试端设计.md) |
| 数据清洗 | 如何把 Agent 回复中的词句转成可学习资产 | [对话词句采集清洗方案](./数据清洗/对话词句采集清洗方案.md) |
| 学习资料 | 外部 SDK、Function Call、Structured Output 和 Agent Builder 概念如何映射到 PEAI | [OpenAI Agents SDK 中文学习笔记](./OpenAI Agents SDK中文学习笔记.md)、[Function Call 学习笔记](./FunctionCall学习笔记.md)、[Structured Output 学习笔记](./StructuredOutput学习笔记.md)、[Agent Builder 学习资料](./agent-builder/) |

维护规则：

- Agent 路由 intent、workflow、target agent 发生变化时，先更新 [Agent 能力清单](./Agent能力清单.md)，再更新对应设计文档。
- 新增 workflow 时，需要说明入口 intent、必需输入、缺失输入处理、目标 Agent 和验证样例。
- Agent 能力进入可用、废弃或重构状态时，需要同步更新 [Agent 产品现状与路线图](./Agent产品现状与路线图.md)。
- Route Agent 只负责决策，不承载评分、诊断、润色、教学内容生成。
