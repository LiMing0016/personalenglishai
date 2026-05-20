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
  - docs/agent/Agent产品现状与路线图.md
  - docs/agent/Agent能力清单.md
  - docs/agent/写作教练Agent设计.md
  - docs/agent/agent-builder/index.md
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
| 3 | [Agent 能力清单](./Agent能力清单.md) | 看每个 Agent、workflow、工具和观测能力的状态。 |
| 4 | [写作教练 Agent 设计](./写作教练Agent设计.md) | 看从零到一作文陪写、学段/考试标准、偏题检查和多轮循环方案。 |
| 5 | [路由 Agent 设计](./路由Agent设计.md) | 看路由 Agent 的输入、输出、模型配置边界和工作流决策 schema。 |
| 6 | [Agent 可观测性与调试中心](./Agent可观测性与调试中心.md) | 看 Agent Debug Recorder、Eval Dataset Builder、DeepEval 和 Langfuse 接入方案。 |
| 7 | [AI 调试端设计](./AI调试端设计.md) | 看 `/ops/agent/*` 的页面结构、使用边界和数据接入顺序。 |
| 8 | [OpenAI Agents SDK 中文学习笔记](./OpenAI Agents SDK中文学习笔记.md) | 查官方 SDK 概念、术语翻译和 PEAI 代码用法映射。 |
| 9 | [Agent Builder 学习资料](./agent-builder/) | 学习 OpenAI Platform 可视化工作流、节点、变量和 PEAI 映射方式。 |
| 10 | [对话词句采集清洗方案](./数据清洗/对话词句采集清洗方案.md) | 看对话词句如何从 Agent 回复中采集、清洗、评分并落库。 |

## 文档分层

| 类型 | 负责回答 | 当前文档 |
| --- | --- | --- |
| 产品总账 | 当前做到哪，欠什么，下一期做什么 | [Agent 产品现状与路线图](./Agent产品现状与路线图.md) |
| 能力台账 | 每个 Agent / workflow / tool 是否可用 | [Agent 能力清单](./Agent能力清单.md) |
| 技术设计 | 模块边界、数据流、schema、运行链路 | [学习助手 Agent 编排架构](./学习助手Agent编排架构.md)、[路由 Agent 设计](./路由Agent设计.md)、[写作教练 Agent 设计](./写作教练Agent设计.md) |
| 调试与观测 | 如何记录、回放、评估、排查 Agent run | [Agent 可观测性与调试中心](./Agent可观测性与调试中心.md)、[AI 调试端设计](./AI调试端设计.md) |
| 数据清洗 | 如何把 Agent 回复中的词句转成可学习资产 | [对话词句采集清洗方案](./数据清洗/对话词句采集清洗方案.md) |
| 学习资料 | 外部 SDK 和 Agent Builder 概念如何映射到 PEAI | [OpenAI Agents SDK 中文学习笔记](./OpenAI Agents SDK中文学习笔记.md)、[Agent Builder 学习资料](./agent-builder/) |

维护规则：

- Agent 路由 intent、workflow、target agent 发生变化时，先更新 [Agent 能力清单](./Agent能力清单.md)，再更新对应设计文档。
- 新增 workflow 时，需要说明入口 intent、必需输入、缺失输入处理、目标 Agent 和验证样例。
- Agent 能力进入可用、废弃或重构状态时，需要同步更新 [Agent 产品现状与路线图](./Agent产品现状与路线图.md)。
- Route Agent 只负责决策，不承载评分、诊断、润色、教学内容生成。
