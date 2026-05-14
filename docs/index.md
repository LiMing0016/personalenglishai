---
status: active
owner: project
last_updated: 2026-05-10
related_code:
  - web/
  - backend/
  - python/ai_orchestrator/
---

# Personal English AI 文档中心

这里是 Personal English AI 的统一文档入口。

文档站用于查找当前有效的产品规则、系统架构、接口契约、数据说明、AI 行为、运行手册、测试说明和技术决策记录。

## 从这里开始

- [产品](./product/)：当前产品范围、用户规则和业务说明。
- [架构](./architecture/)：前端、后端、Python agent 服务和支撑系统如何协作。
- [接口](./api/)：当前前后端接口契约。
- [数据](./data/)：数据库、迁移和持久化规则。
- [AI](./ai/)：评分、语法、Prompt、助手路由和 agent 工作流。
- [Admin](./admin/)：管理员端的信息架构、运营治理后台和项目资产管理规则。
- [运行手册](./runbooks/)：本地开发、部署和排障。
- [测试](./testing/)：冒烟测试和回归检查。
- [ADR](./adr/)：重要技术决策记录。

## 文档可信规则

主导航只放当前仍然指导开发和运行的文档。任务拆解放在仓库根目录 `tasks/`；历史计划、mockup、报告和资料提取中间产物放在 `archive/`，除非被当前维护文档明确引用，否则不作为当前依据。

维护规则见 [文档治理规则](./contributing.md)。
