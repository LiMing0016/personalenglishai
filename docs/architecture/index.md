---
status: active
owner: project
last_updated: 2026-05-16
related_code:
  - web/
  - backend/
  - python/
---

# 架构

架构文档说明当前系统结构、模块边界和关键数据流。

## 系统形态

Personal English AI 是一个全栈英语学习应用：

- `web/`：Vue 3 前端应用。
- `backend/`：Spring Boot API、持久化、评分、语法和 AI 服务编排。
- `python/ai_orchestrator/`：基于 OpenAI Agents SDK 的学习助手服务。
- MySQL：存储长期业务数据。
- Redis：存储运行时状态、缓存和兼容 sidecar 状态。

## 当前文档

- [鉴权](./auth.md)
- [助手会话管理](./assistant-conversation-management.md)
- [Oxford 词典集成](./dictionary-oxford.md)
- [文档知识提取管线设计](./文档知识提取管线设计.md)
- [仓库结构规范](./repository-structure.md)
- [写作任务元数据](./writing-task-metadata.md)
