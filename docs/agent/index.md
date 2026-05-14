---
title: Agent 设计
status: draft
owner: ai
last_updated: 2026-05-14
review_cycle: monthly
related_code:
  - python/ai_orchestrator/
related_docs:
  - docs/ai/agent-overview.md
  - docs/ai/learning-assistant-architecture.md
---

# Agent 设计

本目录记录 PEAI Agent 工作流设计，包括路由 Agent、写作工作流、能力 Agent、结构化输出和后续评测方案。

当前文档：

- [路由 Agent](./路由agent.md)：Route Agent 的输入、输出、模型配置边界和工作流决策 schema。
- [OpenAI Agents SDK 中文学习笔记](./openai-agents-sdk-study-notes.md)：官方文档中文导读、术语翻译和 PEAI 代码用法映射。
- [Agent 可观测性与调试中心](./agent-observability-center.md)：Agent Debug Recorder、管理员调试中心、Eval Dataset Builder、DeepEval 和 Langfuse 接入方案。
- [AI 调试端设计](./ai-debug-console.md)：`/ops/agent/*` 的页面结构、使用边界、首期空壳和后续数据接入顺序。

维护规则：

- Agent 路由 intent、workflow、target agent 发生变化时，需要同步更新路由文档。
- 新增 workflow 时，需要说明入口 intent、必需输入、缺失输入处理和验证样例。
- Route Agent 只负责决策，不承载评分、诊断、润色、教学内容生成。
