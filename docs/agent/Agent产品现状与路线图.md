---
title: Agent 产品现状与路线图
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
  - docs/agent/Agent能力清单.md
  - docs/agent/路由Agent设计.md
  - docs/agent/Agent可观测性与调试中心.md
  - docs/agent/学习助手Agent编排架构.md
  - docs/ai/prompt-management.md
---

# Agent 产品现状与路线图

## 当前阶段结论

PEAI Agent 当前处于 **P1 可用编排原型 / 内测可用阶段**。

核心链路已经从单一聊天助手升级为多 Agent 编排雏形：Router、Specialist Agents、动态 instructions、Prompt Resolver、OpenAI tracing 和基础 Langfuse 接入已经存在。但距离稳定运营级学习 Agent 系统还有缺口，主要集中在评分标准按需加载、检索增强、工具化上下文、eval 回归、Prompt 版本治理和实验体系。

```text
当前阶段：P1 可用编排原型 / 内测可用
目标阶段：P2 稳定运营级学习 Agent 系统
```

## 当前状态总览

| 维度 | 当前状态 | 产品判断 |
| --- | --- | --- |
| 多 Agent 架构 | 已搭建 Router + Specialist Agents | 可用原型 |
| 用户请求入口 | 新 `AssistantRequest` 链路与旧 `chat` 链路并存 | 迁移中 |
| 路由判断 | 规则路由与模型路由并存 | 需要收敛职责 |
| 专职 Agent | 8 个能力 Agent 已定义 | 可用但需补质量评估 |
| 动态上下文 | 学段、模式已接入 | 字段偏薄 |
| Prompt 管理 | local / hybrid / remote resolver 已有 | 基础治理 |
| 可观测性 | OpenAI trace、run metadata、Langfuse 可选接入 | 初步可排查 |
| Eval 验证 | 单测较多，但业务 eval harness 不完整 | 不足 |

## 已实现能力

| 能力 | 状态 | 说明 | 主要文档 |
| --- | --- | --- | --- |
| Route Agent | 已实现 | 将用户输入转成结构化路由决策。 | [路由 Agent 设计](./路由Agent设计.md) |
| Specialist Agents | 已实现 | 覆盖润色、句子结构、词汇、翻译、评分、题目设计、能力画像、学习计划。 | [Agent 能力清单](./Agent能力清单.md) |
| AssistantRequest 新入口 | 已实现 | 支持 run id、trace id、structured metadata、streaming events。 | [Agent 能力清单](./Agent能力清单.md) |
| 旧 chat 兼容入口 | 保留中 | 仍承载多轮会话、附件、active task continuation 等能力。 | [Agent 能力清单](./Agent能力清单.md) |
| Prompt Resolver | 初步实现 | 支持 local / hybrid / remote prompt 来源。 | [Prompt 管理](../ai/prompt-management.md) |
| OpenAI Platform Traces | 已接入 | 用于查看 Agents SDK trace / span。 | [Agent 可观测性与调试中心](./Agent可观测性与调试中心.md) |
| Langfuse tracing | 可选接入 | 用于外部 tracing、prompt、实验分析。 | [Agent 可观测性与调试中心](./Agent可观测性与调试中心.md) |
| AI 调试端 | P0 数据闭环中 | `/ops/agent/*` 用于查看 run、step、prompt snapshot、usage。 | [AI 调试端设计](./AI调试端设计.md) |

## 主要缺口

| 缺口 | 影响 | 优先级 | 建议解决方式 |
| --- | --- | --- | --- |
| 新旧入口职责重叠 | 路由、上下文和 tracing 口径不完全统一 | P0 | 明确 `AssistantRequest` 为正式主链路，旧 `chat` 作为兼容层逐步收敛。 |
| Route Agent 与规则路由边界不清 | 可能出现两套路由判断互相覆盖 | P0 | 让模型路由负责语义判断，规则路由只做兜底和强约束。 |
| 上下文字段偏薄 | 模型难以稳定理解考试类型、任务类型、页面状态 | P0 | 扩展 `AssistantRunContext` 和 RouteRequest 的业务上下文。 |
| 评分标准未工具化 | 写作评分难以按考试类型动态加载 rubric | P1 | 将 rubric、考试标准、题型规则变成可检索工具或受控上下文。 |
| Eval 回归不足 | 改 Prompt 或换模型后难判断是否退化 | P1 | 建立 route_eval、scoring_eval、feedback_eval 三类最小回归集。 |
| Prompt 版本治理不足 | 难以追踪 prompt 变更与输出质量关系 | P1 | 为 prompt 增加版本、来源、发布状态、回滚记录。 |
| 外部观测平台未形成闭环 | Langfuse / OpenAI trace 与本地 debug run 关联还需加强 | P1 | 使用统一 run_id / trace_id 打通本地与外部观测。 |

## 路线图

### P0：稳定当前主链路

- 明确正式入口：`AssistantRequest` 为主，旧 `chat` 为兼容层。
- 收敛路由职责：Route Agent 输出结构化决策，规则层只做兜底。
- 完善 route metadata：保证 OpenAI Trace、Langfuse、本地 run 能互相关联。
- 补齐核心错误路径：缺输入、范围外、模型失败、空输出、结构化解析失败。

### P1：进入可评估阶段

- 建立 Agent Eval Case 数据集。
- 支持从真实 run 保存 eval case。
- 建立模型 / prompt sandbox，对同一输入重跑并对比结果。
- 将写作 rubric、考试标准、评分维度工具化。
- 为 Prompt Resolver 增加版本、发布、回滚和实验记录。

### P2：运营级 Agent 系统

- 引入更完整的用户能力画像和长期学习状态。
- 支持多 Agent workflow 的结构化中间结果沉淀。
- 建立自动化质量看板，包括路由准确率、评分稳定性、用户满意度、失败率。
- 将 Langfuse / DeepEval / OpenAI Platform Trace 纳入日常运营排查流程。

## 技术债

- `Router Agent`、`RouteDecisionRunner`、规则路由之间需要进一步收敛。
- Specialist Agents 的输出结构不够统一，多 Agent 汇总时质量不可控。
- Debug Recorder、OpenAI Trace、Langfuse 三套观测口径需要统一 run id。
- Prompt 文档、代码 prompt、远程 prompt 之间需要明确唯一事实来源。

## 维护规则

- 每次新增或废弃 Agent / workflow，必须更新 [Agent 能力清单](./Agent能力清单.md)。
- 每次 Agent 产品阶段变化，必须更新本文的“当前阶段结论”和“路线图”。
- 每次引入新的观测、eval、prompt 治理机制，需要补充到对应设计文档。

## 相关文档

- [Agent 能力清单](./Agent能力清单.md)
- [路由 Agent 设计](./路由Agent设计.md)
- [Agent 可观测性与调试中心](./Agent可观测性与调试中心.md)
- [AI 调试端设计](./AI调试端设计.md)
- [Prompt 管理](../ai/prompt-management.md)
