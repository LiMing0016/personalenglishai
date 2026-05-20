# Agent 文档

本目录记录 PEAI 学习助手 Agent 架构、运行链路、prompt 注入规则、路由策略和后续演进计划。

当前文档：

- [学习助手Agent编排架构.md](../agent/学习助手Agent编排架构.md)：学习助手 8 Agent 架构、handoff/tool-agent 边界、路由策略和验证方式。
- [active-task-state-design.md](./active-task-state-design.md)：Active Task State、续问判定、状态生命周期和 classifier 设计。
- [grammar-skills-profile-impact.md](./grammar-skills-profile-impact.md)：grammar-check / grammar-explain Skill 边界、事件字段、统计口径和用户画像影响。
- [grammar-learning-events-persistence.md](./grammar-learning-events-persistence.md)：语法学习事件从 Python Orchestrator 到 Backend API、MySQL 和画像聚合的持久化方案。
- [writing-task-metadata.md](../architecture/writing-task-metadata.md)：写作助教工作流的题目任务元数据层，说明作文中心任务、必答点、写作重点和推荐结构如何保存。
- [CEFR](../product/cefr/)：CEFR 在 PEAI 中作为能力坐标系的使用方式，包含项目指南、产品化应用和 RAG 抽取方案。

维护规则：

- Agent 数量、职责、handoff/tool 边界发生变化时，必须同步更新架构文档。
- 新增能力 Agent 时，应补充职责、路由规则、prompt 资产和最小回归样例。
- 接入用户画像、学习计划持久化或服务端历史时，应补充数据流和接口契约。
