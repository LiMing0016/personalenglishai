---
title: 文档模板
status: active
owner: project
last_updated: 2026-05-13
review_cycle: quarterly
related_code:
  - docs/templates/
related_docs:
  - /contributing
---

# 文档模板

本目录存放新增主文档时优先复用的模板。

## 模板清单

- `product.md`：产品规则、业务流程、用户权益。
- `architecture.md`：系统架构、模块边界、跨服务调用。
- `api.md`：接口契约、鉴权、请求响应和错误码。
- `data.md`：数据库表、迁移、索引、数据口径。
- `ai.md`：Prompt、模型、评分、agent、AI 输出格式。
- `runbook.md`：部署、回滚、排障、运维操作。
- `testing.md`：测试矩阵、冒烟、回归、压测、验收。
- `adr.md`：长期技术决策记录。

## 使用规则

复制模板到对应目录后，必须更新 frontmatter、一级标题、相关代码、相关文档和验收方式。
