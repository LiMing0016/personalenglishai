# Docs Agent Rules

本文件约束 `docs/` 下的文档维护。修改文档时，除仓库根目录 `AGENTS.md` 外，还必须遵守本文件。

## 基本原则

- `docs/` 是 VitePress 文档站目录，只放当前有效文档、归档资料和文档站自身配置。
- 新增或修改文档前，先判断内容应放在哪里；不要把任务拆解、临时计划或 Trae 题单塞进主文档目录。
- 优先维护已有权威文档，避免为同一主题创建多篇相互竞争的说明。
- 文档语言默认使用中文，除非内容本身是代码、接口字段、命令或外部专有名词。
- 修改文档后，检查是否需要更新 VitePress 导航、侧边栏、索引页或相关交叉链接。

## Frontmatter 规则

`docs/` 下当前有效的主文档必须包含 YAML frontmatter。新增主文档使用以下字段：

```yaml
---
title: 文档标题
status: draft
owner: project
last_updated: YYYY-MM-DD
review_cycle: monthly
related_code: []
related_docs: []
---
```

字段要求：

- `title`：文档标题，应与一级标题语义一致。
- `status`：只能使用 `active`、`draft`、`deprecated`、`archived`。
- `owner`：负责维护的模块或角色，例如 `project`、`frontend`、`backend`、`ai`、`ops`、`data`。
- `last_updated`：实际修改日期，格式为 `YYYY-MM-DD`。
- `review_cycle`：建议使用 `monthly`、`quarterly`、`on-change` 或 `none`。
- `related_code`：相关代码路径；没有直接代码关系时使用空数组。
- `related_docs`：相关文档路径；没有相关文档时使用空数组。

修改已有主文档时：

- 如果文档已有 frontmatter，保持并补齐缺失字段。
- 如果内容发生实质变化，更新 `last_updated`。
- 如果文档迁入 `docs/archive/`，将 `status` 改为 `archived`。
- 如果文档不再是当前依据但仍需保留，将 `status` 改为 `deprecated`，并说明替代文档。

## 目录归属

按内容归档到合适目录：

- `docs/product/`：产品规则、业务说明、用户流程、订阅、路线图、CEFR 等产品层内容。
- `docs/architecture/`：系统架构、模块边界、跨服务数据流、技术方案和长期结构说明。
- `docs/api/`：接口契约、请求响应字段、错误码、鉴权要求、兼容性约束。
- `docs/data/`：数据库表、迁移、索引、持久化规则、数据生命周期和数据口径。
- `docs/ai/`：Prompt、评分、语法检查、agent 路由、模型调用、AI 输出格式和 AI 行为边界。
- `docs/runbooks/`：本地启动、部署、回滚、排障、环境变量、日志和运维操作步骤。
- `docs/testing/`：测试矩阵、冒烟测试、回归测试、压测、验收标准。
- `docs/adr/`：长期技术决策记录。影响架构、部署、数据模型、依赖或 AI 工作流的决策应新增 ADR。
- `docs/archive/`：历史资料、旧计划、旧报告、废弃设计、mockup、OCR 中间产物和不再作为当前依据的材料。
- `docs/.vitepress/`：VitePress 配置和构建相关文件。

不要放入 `docs/` 主导航的内容：

- Trae 题单、一次性任务拆解、临时执行清单：放到仓库根目录 `tasks/`。
- 大段实验过程、历史执行日志、旧验收报告：放到 `docs/archive/`。
- 构建产物和依赖目录：不要提交 `docs/node_modules/` 或 `docs/.vitepress/dist/`。

## 导航和索引

- 新增当前有效主文档后，检查是否需要更新 `docs/.vitepress/config.ts` 的 `nav` 或 `sidebar`。
- 每个一级分区应有 `index.md`，用于说明该分区收录什么、如何阅读、哪些文档最重要。
- 只把当前仍指导开发、上线或运维的文档加入主导航。
- 归档文档默认不加入主导航，除非当前文档明确引用它作为历史背景。

## 文档类型模板

新增主文档时，优先复制 `docs/templates/` 下的对应模板，再改标题、frontmatter 和正文。模板目录：

- `docs/templates/product.md`：产品规则、业务流程、用户权益。
- `docs/templates/architecture.md`：系统架构、模块边界、跨服务调用。
- `docs/templates/api.md`：接口契约、鉴权、请求响应和错误码。
- `docs/templates/data.md`：数据库表、迁移、索引、数据口径。
- `docs/templates/ai.md`：Prompt、模型、评分、agent、AI 输出格式。
- `docs/templates/runbook.md`：部署、回滚、排障、运维操作。
- `docs/templates/testing.md`：测试矩阵、冒烟、回归、压测、验收。
- `docs/templates/adr.md`：长期技术决策记录。

API 文档至少包含：

- 接口用途。
- Endpoint。
- 鉴权和权限。
- Request。
- Response。
- 错误码。
- 兼容性约束。
- 验收方式。

架构文档至少包含：

- 当前结论。
- 范围和非范围。
- 组件职责。
- 数据流或调用链。
- 失败模式。
- 设计取舍。
- 相关代码和 ADR。

Runbook 至少包含：

- 适用场景。
- 快速判断。
- 操作步骤。
- 回滚或恢复方案。
- 验收方式。
- 升级处理条件。

ADR 优先使用 `docs/templates/adr.md` 或 `docs/adr/template.md`，并保持编号递增。

## 需要同步检查文档的代码改动

如果改动涉及以下内容，必须检查并同步更新对应文档：

- 架构、模块边界或服务调用链。
- API 路径、请求字段、响应字段、错误码或鉴权逻辑。
- 数据库 schema、迁移、索引、数据口径或持久化行为。
- 部署方式、环境变量、端口、健康检查、回滚或排障流程。
- AI Prompt、评分规则、agent 路由、模型配置或结构化输出。
- 测试命令、冒烟流程、验收矩阵或发布流程。

## 验证

文档改动后至少运行：

```powershell
cd docs
npm run build
```

如果只修改归档文档且没有链接变更，也优先运行构建；若无法运行，最终说明原因。

构建失败时，不要通过设置 `ignoreDeadLinks: true` 规避问题。应修复死链、导航或 Markdown 问题。
