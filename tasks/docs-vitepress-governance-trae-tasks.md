# VitePress 文档治理 Trae 题单

## 目标

把当前混乱的项目文档治理成一个面向中国用户、可长期维护的 VitePress 文档站。

核心目标：

- `docs/` 只放当前有效、可被文档站展示的长期文档。
- `tasks/` 放 Trae 题单、阶段性任务拆解和中间执行清单，不进入 VitePress 导航。
- `docs/archive/` 放历史材料、旧状态报告、mockup、OCR 中间产物和不再作为当前依据的资料。
- 文档站使用中文导航、中文搜索文案和清晰的信息架构。
- 每个题目都可单独提交，且提交后能通过 VitePress 构建验证。

## 统一约束

- 用户和维护者主要是中文用户，新增文档和导航默认使用中文。
- 不改业务代码，不改前端功能，不改后端接口。
- 不删除历史资料，除非明确是重复空目录或构建产物。
- 不把临时任务拆解放进 `docs/`。
- 每题提交前至少运行一次对应验证命令。
- 提交信息使用 Conventional Commits，例如：`docs(project): 引入 VitePress 文档站`。

## 题目 1：引入 VitePress 文档站骨架

### Prompt

请在当前仓库中为 `docs/` 引入 VitePress 文档站骨架，不迁移历史文档内容。

目标：

1. 在 `docs/` 下新增 VitePress 所需 npm 配置：
   - `docs/package.json`
   - `docs/package-lock.json`
   - `docs/.vitepress/config.ts`
2. 配置中文文档站：
   - `lang: zh-CN`
   - 中文站点标题
   - 中文搜索按钮和搜索弹窗文案
   - 首页、产品、架构、接口、数据、AI、运行手册、测试、ADR 的顶层导航
3. 新增最小可构建的首页：
   - `docs/index.md`
4. 确保构建产物不进入 Git：
   - `docs/.vitepress/dist`
   - `docs/node_modules`

建议改动范围：

- `docs/package.json`
- `docs/package-lock.json`
- `docs/.vitepress/config.ts`
- `docs/index.md`
- `.gitignore`
- `README.md`

不要做：

- 不迁移旧文档。
- 不创建复杂主题。
- 不改业务代码。

### 验收标准

- `cd docs && npm install` 能成功安装依赖。
- `cd docs && npm run build` 能成功构建。
- `docs/.vitepress/config.ts` 中存在中文导航和中文搜索文案。
- `docs/index.md` 能作为文档站首页渲染。
- `git status` 不包含 `docs/node_modules` 或 `docs/.vitepress/dist`。

## 题目 2：设计文档信息架构和生命周期规则

### Prompt

请为项目设计文档治理规则，明确哪些内容进入 VitePress，哪些内容归档，哪些内容放到 `tasks/`。

目标：

1. 新增文档治理说明：
   - `docs/contributing.md`
2. 定义文档状态：
   - `active`
   - `draft`
   - `deprecated`
   - `archived`
3. 定义目录边界：
   - `docs/product/`：产品规则和业务说明
   - `docs/architecture/`：系统架构和模块边界
   - `docs/api/`：接口契约
   - `docs/data/`：数据库和持久化
   - `docs/ai/`：Prompt、评分、agent、AI 行为
   - `docs/runbooks/`：本地开发、部署、排障
   - `docs/testing/`：测试和验收
   - `docs/adr/`：重要技术决策
   - `docs/archive/`：历史资料
   - `tasks/`：Trae 题单和任务拆解
4. 在 `README.md` 中说明文档入口和治理规则。

不要做：

- 不移动大量文档。
- 不把 `tasks/` 加入 VitePress 导航。
- 不为每个旧文档补 frontmatter。

### 验收标准

- `docs/contributing.md` 清楚说明文档状态和目录边界。
- `README.md` 能引导开发者进入 `docs/` 文档站和 `tasks/` 任务目录。
- `tasks/` 被明确描述为任务拆解目录，不属于文档站主导航。
- `cd docs && npm run build` 通过。

## 题目 3：建立 VitePress 分区首页和侧边栏

### Prompt

请为 VitePress 文档站建立分区首页和侧边栏，让维护者能从导航快速找到当前有效文档。

目标：

1. 为以下分区创建首页：
   - `docs/product/index.md`
   - `docs/architecture/index.md`
   - `docs/api/index.md`
   - `docs/data/index.md`
   - `docs/ai/index.md`
   - `docs/runbooks/index.md`
   - `docs/testing/index.md`
   - `docs/adr/index.md`
2. 在 `docs/.vitepress/config.ts` 中配置对应 sidebar。
3. 每个分区首页说明：
   - 这个分区放什么
   - 当前有哪些文档
   - 什么内容不应该放进来
4. 新增 ADR 模板：
   - `docs/adr/template.md`

不要做：

- 不迁移旧文档正文。
- 不做复杂样式。
- 不加入英文导航。

### 验收标准

- 每个顶层导航点击后都有有效页面。
- 每个分区都有侧边栏。
- `docs/adr/template.md` 包含背景、决策、影响、替代方案等部分。
- `cd docs && npm run build` 通过，且没有死链错误。

## 题目 4：迁移当前有效文档到新结构

### Prompt

请把当前仍然有效的项目文档迁移到 VitePress 新结构中，并保持内容可追溯。

目标：

1. 识别当前有效文档，并迁移到合适分区：
   - 产品规则迁移到 `docs/product/`
   - 架构说明迁移到 `docs/architecture/`
   - 接口说明迁移到 `docs/api/`
   - 数据库和持久化说明迁移到 `docs/data/`
   - AI、评分、Prompt、agent 说明迁移到 `docs/ai/`
   - 启动、部署、排障说明迁移到 `docs/runbooks/`
   - 测试矩阵和验收说明迁移到 `docs/testing/`
2. 更新对应分区首页和侧边栏链接。
3. 保留原文档内容，不做大段重写。
4. 优先使用 Git rename，让历史可追溯。

不要做：

- 不迁移 Trae 题单到 `docs/`。
- 不删除不确定是否有价值的文档。
- 不改业务代码。

### 验收标准

- 当前有效文档不再散落在 `docs/` 根目录。
- VitePress 侧边栏能访问迁移后的主要文档。
- `git diff --name-status` 中主要体现为 rename，而不是大规模删除重建。
- `cd docs && npm run build` 通过。
- 随机打开 5 个迁移后的链接，路径和标题都正确。

## 题目 5：归档历史材料并迁出任务拆解

### Prompt

请整理历史材料和任务拆解，避免它们污染 VitePress 当前文档导航。

目标：

1. 把历史材料迁移到 `docs/archive/`：
   - 旧状态报告
   - mockup
   - OCR 或 CSV 中间产物
   - 已完成实施计划
   - 验收报告
   - 废弃设计
2. 把 Trae 题单、阶段性任务拆解、临时执行清单迁移到根目录 `tasks/`。
3. 删除或忽略空目录。
4. 更新 `docs/index.md`、`docs/contributing.md`、`README.md` 中关于归档和 `tasks/` 的说明。

不要做：

- 不把 `tasks/` 加入 VitePress 导航。
- 不把归档材料当成当前依据。
- 不删除历史内容。

### 验收标准

- `tasks/` 下集中放置任务拆解类 Markdown。
- `docs/archive/` 下集中放置历史材料。
- `docs/题目` 不再包含文件。
- `docs/archive/tasks` 不再作为任务拆解的最终位置。
- `cd docs && npm run build` 通过。
- `rg -n "docs/题目|docs/archive/tasks" README.md docs tasks` 不应出现新的当前规则引用。

## 题目 6：补齐运行手册、环境变量和构建验证

### Prompt

请补齐 VitePress 文档站的运行手册，让后续开发者能稳定启动、构建和维护文档站。

目标：

1. 在 `docs/runbooks/` 下补齐：
   - 本地开发说明
   - 部署说明
   - 环境变量说明
   - 启动环境检查说明
2. 更新 VitePress sidebar，确保运行手册都能从导航进入。
3. 更新 `README.md`，说明文档站常用命令：
   - `cd docs && npm install`
   - `cd docs && npm run dev`
   - `cd docs && npm run build`
4. 确保环境变量文档路径使用 VitePress 新结构，例如：
   - `docs/runbooks/environment-variables.md`

不要做：

- 不把真实密钥写入文档。
- 不引入新的部署平台。
- 不修改应用启动脚本行为。

### 验收标准

- `docs/runbooks/index.md` 能链接到环境变量、本地开发、部署、启动检查等文档。
- `docs/.vitepress/config.ts` 的运行手册 sidebar 包含环境变量文档。
- `README.md` 中的环境变量文档链接指向 `docs/runbooks/environment-variables.md`。
- `cd docs && npm run build` 通过。
- 搜索 `docs/deploy/environment-variables.md` 不应再有当前链接引用。

## 题目 7：建立文档质量门禁和人工验收清单

### Prompt

请为 VitePress 文档治理补充质量门禁，确保后续文档变更不会再次变得混乱。

目标：

1. 在 `docs/contributing.md` 或 `docs/testing/index.md` 中补充文档变更验收清单。
2. 清单至少包含：
   - 是否属于当前有效文档
   - 是否应该进入 `tasks/`
   - 是否应该进入 `docs/archive/`
   - 是否更新了 sidebar
   - 是否运行了 VitePress build
   - 是否避免真实密钥和敏感信息
3. 如项目已有 CI，可补充文档构建命令说明；没有 CI 时只写本地验证，不新增 CI。
4. 补充一个 ADR，说明为什么选择 VitePress 作为文档治理方案：
   - `docs/adr/0001-use-vitepress-for-docs.md`

不要做：

- 不新增复杂自动化。
- 不强行要求所有历史文档补齐 frontmatter。
- 不把文档治理规则写成难以执行的大流程。

### 验收标准

- 有明确的文档变更验收清单。
- 有 VitePress 选型 ADR。
- ADR 说明背景、决策、影响和替代方案。
- `cd docs && npm run build` 通过。
- 后续开发者能根据清单判断一份文档应该放到 `docs/`、`docs/archive/` 还是 `tasks/`。

## 推荐执行顺序

1. 题目 1：先让 VitePress 骨架可构建。
2. 题目 2：先定规则，避免迁移时继续混乱。
3. 题目 3：建立导航和分区入口。
4. 题目 4：迁移当前有效文档。
5. 题目 5：归档历史材料，并把任务拆解迁出 `docs/`。
6. 题目 6：补齐运行手册和环境变量导航。
7. 题目 7：补质量门禁和 ADR。

## 最终总体验收

- `docs/` 是一个可独立运行的 VitePress 文档站。
- `tasks/` 存放任务拆解，不进入文档站导航。
- `docs/archive/` 只作为历史资料库，不作为当前开发依据。
- `README.md` 能说明文档站入口、常用命令和目录边界。
- `cd docs && npm run build` 成功。
- 除文档目录治理外，没有业务代码改动。
