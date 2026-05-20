# 管理员端模型用量 Token 前后端实现 Trae 题目

## 背景

当前管理员端“模型用量 / Model Usage”页面已经先用现有 Agent Debug 接口做了初步接入：

- `GET /api/ops/agent/runs`：可读取 Agent Run 列表。
- `GET /api/ops/agent/runs/{runId}`：可读取单次 Run 的 usage 明细。
- 前端可以展示 tokens、模型、workflow、agent、用户、状态、延迟等基础信息。

但这还不是正式的模型用量统计能力。当前仍存在这些限制：

- 没有专用的管理员模型用量聚合 API。
- provider 主要依赖前端按 model 名称推断。
- cost 成本字段没有真实后端来源。
- 趋势图、多维聚合、Provider 分布主要由前端基于已加载数据计算。
- CSV 导出只覆盖当前前端已加载数据。

本组题目目标是把“模型用量 Token 统计”从当前临时接入推进到正式的前后端能力。题目按推荐开发顺序排列。每组分 A / B 两题：A 为中等难度，B 为难题。

---

## 题目 1A：梳理现有 Token 数据来源

难度：中等

### Prompt

请在当前项目中梳理管理员端“模型用量 / Model Usage”页面可用的后端数据来源。重点检查 Agent Run、usage、token、cost、model、workflow、agent、user、status 相关表、实体、Mapper、Service、Controller。

要求：

1. 不改业务代码。
2. 输出当前已实现接口清单。
3. 标出哪些字段可以直接得到，哪些字段只能推断，哪些字段缺失。
4. 给出后续实现模型用量聚合接口的建议路径。

### 验收标准

- 能明确列出 `/api/ops/agent/runs`、`/api/ops/agent/runs/{runId}` 等现有可用接口。
- 能说明 `tokens`、`model`、`workflow`、`agent`、`latency`、`status` 当前来源。
- 能明确指出 `provider` 需要推断或补字段，`cost` 当前缺失或未聚合。
- 没有修改代码。

---

## 题目 1B：设计正式后端 API 契约

难度：难

### Prompt

请为管理员端“模型用量 / Model Usage”设计正式后端 API 契约，并在 docs 或任务文档中落地。

需要覆盖：

1. 概览 KPI：总 tokens、输入 tokens、输出 tokens、请求数、成本、失败率、平均延迟。
2. 趋势图：按小时/天/周/月聚合。
3. 多维表格：按 model/provider/workflow/agent/user 聚合。
4. Provider 分布。
5. 明细事件列表。
6. 筛选条件：时间范围、粒度、provider、model、workflow、agent、user、status。

要求：

- 给出 endpoint、query params、response schema。
- 标注哪些字段来自现有表，哪些需要新增或计算。
- 保持和当前 Spring Boot 后端风格一致。

### 验收标准

- 文档包含至少 4 个正式接口设计。
- 每个接口有请求参数和响应 JSON 示例。
- 明确成本字段、provider 字段、聚合粒度的实现方式。
- 能作为后续开发依据。

---

## 题目 2A：补齐 Token Usage DTO 和查询对象

难度：中等

### Prompt

请在后端新增管理员模型用量相关 DTO / Query 对象，为后续 API 做准备。

需要包含：

1. `ModelUsageQuery`：`startTime`、`endTime`、`granularity`、`provider`、`model`、`workflow`、`agent`、`userId`、`status`。
2. `ModelUsageOverviewResponse`：`totalTokens`、`inputTokens`、`outputTokens`、`requestCount`、`costUsd`、`failureRate`、`avgLatencyMs`。
3. `ModelUsageTrendPoint`。
4. `ModelUsageDimensionRow`。
5. `ModelUsageEventRow`。
6. `ModelUsageProviderShare`。

要求：

- 放在 admin 或 ops 合适包下。
- 命名清晰。
- 不破坏现有接口。

### 验收标准

- 后端能编译通过。
- DTO 字段覆盖前端页面需要的数据。
- Query 支持所有筛选项。
- 没有影响现有 Agent Debug API。

---

## 题目 2B：建立统一 Token Usage 查询 Service

难度：难

### Prompt

请实现管理员模型用量查询 Service，基于当前已有 Agent Run / usage 数据生成统一的 Token Usage 视图。

要求：

1. 从现有运行记录读取 `model`、`workflow`、`agent`、`user`、`status`、`latency`、`createdAt`。
2. 从 usage JSON 或现有 usage 字段读取 `inputTokens`、`outputTokens`、`totalTokens`、`requestCount`。
3. provider 先按 model 名称推断：`gpt/openai -> OpenAI`，`claude -> Anthropic`，`gemini -> Google`，`deepseek -> DeepSeek`，其他 -> `其他 / Local`。
4. `costUsd` 如果当前没有真实价格来源，先返回 `null`，并在代码中保持字段可扩展。
5. 支持按 Query 条件过滤。

注意：

- 不要硬编码假数据。
- 不要改动现有 Agent Debug 行为。
- 增加必要单元测试。

### 验收标准

- Service 能返回真实后端数据聚合前的标准化 rows。
- provider 推断逻辑有测试。
- usage 缺失时不会报错，tokens 默认为 0 或合理空值。
- `mvn test` 或相关后端测试通过。

---

## 题目 3A：实现概览和明细 API

难度：中等

### Prompt

请实现管理员端模型用量后端接口：

1. `GET /api/admin/model-usage/overview`
2. `GET /api/admin/model-usage/events`

要求：

- 支持 `ModelUsageQuery` 中的筛选参数。
- overview 返回 KPI 聚合。
- events 返回分页明细列表。
- 复用上一题的 Model Usage Service。
- Controller、Service、Mapper 分层符合当前项目风格。
- 增加后端测试。

### 验收标准

- 接口能返回真实数据，不是 mock。
- 支持时间范围、model、workflow、agent、userId、status 过滤。
- overview 的 `totalTokens`、`requestCount`、`failureRate`、`avgLatencyMs` 计算正确。
- events 支持分页。
- 后端测试通过。

---

## 题目 3B：实现趋势、多维聚合和 Provider 分布 API

难度：难

### Prompt

请继续实现管理员端模型用量聚合接口：

1. `GET /api/admin/model-usage/trends`
2. `GET /api/admin/model-usage/dimensions`
3. `GET /api/admin/model-usage/providers`

要求：

- trends 支持 `granularity=hour/day/week/month`。
- dimensions 支持 `groupBy=model/provider/workflow/agent/user`。
- providers 返回 provider token 占比。
- 所有接口支持统一筛选条件。
- 聚合逻辑尽量下推数据库；如果当前数据结构不方便，可以先 Service 层聚合，但要控制分页/时间范围。
- 增加测试覆盖聚合正确性。

### 验收标准

- 趋势图数据按时间升序返回。
- `groupBy` 不同维度时返回正确聚合结果。
- provider share 占比合计接近 100%。
- 空数据返回空数组或 0 指标，不报错。
- 后端测试通过。

---

## 题目 4A：新增 modelUsageApi

难度：中等

### Prompt

请在前端新增管理员模型用量 API client，接入后端正式接口。

要求：

1. 新增或完善 `web/src/api/adminModelUsage.ts`。
2. 封装 overview、events、trends、dimensions、providers 请求。
3. 定义 TypeScript 类型。
4. 请求参数和后端 `ModelUsageQuery` 对齐。
5. 不再让页面直接依赖 `/api/ops/agent/runs` 作为主数据源。

注意：

- 保持当前前端 api 封装风格。
- 不删除已有 Agent Debug API。

### 验收标准

- 有完整 TS 类型。
- API 方法覆盖全部后端接口。
- 页面可通过统一 client 获取模型用量数据。
- `npm run build` 通过。

---

## 题目 4B：前端页面改为正式后端数据驱动

难度：难

### Prompt

请把管理员端“模型用量 / Model Usage”页面改成正式后端接口驱动。

要求：

1. KPI 卡片使用 `/overview`。
2. 趋势图使用 `/trends`。
3. 多维表格使用 `/dimensions`。
4. Provider 环图使用 `/providers`。
5. 明细事件使用 `/events`。
6. 筛选条件变化时自动重新请求。
7. loading、error、empty 状态完整。
8. 保留“接口实现状态”区块，但根据正式接口接入情况更新为已完成/部分完成/未完成。

注意：

- 不要使用静态 mock 数据。
- 不要在前端自行补造成本数据。
- `costUsd` 为 `null` 时显示 `—`。

### 验收标准

- 页面数据来自正式 admin model usage API。
- 筛选条件能影响所有图表和表格。
- 后端空数据时页面不崩溃。
- API 失败时有错误提示和重试入口。
- `npm run build` 通过。

---

## 题目 5A：Provider 字段后端标准化

难度：中等

### Prompt

请把 Model Usage 的 provider 逻辑从前端推断迁移到后端标准化输出。

要求：

1. 后端所有 model usage 接口都返回 provider。
2. provider 推断逻辑集中在一个 helper/service 中。
3. 前端不再重复 provider 推断。
4. 增加测试覆盖常见模型：gpt、claude、gemini、deepseek、qwen、kimi、unknown。

### 验收标准

- 前端收到的每条数据都有 provider。
- provider 推断逻辑只有后端一份。
- 测试覆盖主要模型名。
- 前后端构建/测试通过。

---

## 题目 5B：实现成本计算基础能力

难度：难

### Prompt

请为管理员模型用量实现成本计算基础能力。

要求：

1. 设计模型价格配置结构，支持 provider、model、inputTokenPrice、outputTokenPrice、currency、effectiveFrom。
2. 后端聚合接口根据 inputTokens/outputTokens 计算 costUsd。
3. 如果找不到价格配置，costUsd 返回 null，并在响应中保留 unknownCostCount 或类似字段。
4. 前端成本卡片、表格、趋势展示真实成本或 `—`。
5. 增加测试覆盖价格命中、价格缺失、输入输出 token 分别计价。

注意：

- 不要把价格硬编码散落在业务代码里。
- 价格配置可以先用数据库表或集中配置文件，按当前项目风格选择。

### 验收标准

- 成本不再全部显示 `—`。
- 缺少价格配置时不会计算错误成本。
- overview、dimensions、events 都能体现 `costUsd`。
- 测试覆盖成本计算核心逻辑。

---

## 题目 6A：补齐前端静态测试和页面验收

难度：中等

### Prompt

请为管理员端“模型用量 / Model Usage”页面补齐前端测试。

要求：

1. 检查页面是否使用 `adminModelUsageApi`。
2. 检查页面包含 KPI、趋势、多维表格、Provider 分布、明细事件、接口实现状态。
3. 检查已完成/部分完成/未完成状态文案存在。
4. 保持测试风格和当前 `web/tests` 一致。
5. 运行 node --test 对应测试和 `npm run build`。

### 验收标准

- 新增或更新测试通过。
- `npm run build` 通过。
- 页面关键模块不会被误删。
- 测试不依赖真实后端服务。

---

## 题目 6B：端到端联调和文档更新

难度：难

### Prompt

请完成管理员端模型用量功能的前后端联调和文档更新。

要求：

1. 启动后端和前端。
2. 使用浏览器或 Playwright 验证 `/admin/model-usage` 页面能成功请求后端接口。
3. 验证筛选、刷新、CSV 导出、跳转 Agent Run detail。
4. 更新相关 docs，说明接口、字段来源、已知限制、成本配置方式。
5. 给出最终验证记录。

注意：

- 不要掩盖未完成项。
- 如果某些数据依赖真实运行记录，请说明如何造数或使用现有数据验证。

### 验收标准

- 页面在真实后端下可打开并展示数据。
- Network 中能看到正式 admin model usage API 请求。
- 筛选条件有效。
- CSV 导出内容和当前筛选数据一致。
- 文档包含接口说明和已知限制。
- 最终说明列出已运行的验证命令。

---

## 题目 7A：按 Figma 复刻页面结构和视觉层级

难度：中等

### Prompt

请基于当前管理员端“模型用量 / Model Usage”页面和给定 Figma 截图，逐项复刻页面结构和视觉层级。

重点覆盖：

1. 顶部标题、面包屑、环境选择、通知、设置、用户入口。
2. 多维筛选区：时间范围、粒度、Provider、模型、Workflow、Agent、用户、状态、刷新、导出。
3. KPI 指标卡片：总 Tokens、输入 Tokens、输出 Tokens、请求数、总成本、失败率、平均延迟。
4. 主趋势图区域。
5. 下方多维表格和 Provider 分布图。
6. 明细事件、操作入口、关联联动、数据来源、支持能力区域。
7. 右侧说明卡片：多维筛选、核心指标、趋势图、多维拆解、明细与联动。

要求：

- 保持当前 Vue 3 / TypeScript / CSS 写法。
- 不引入新的 UI 框架。
- 页面文案中英文结构尽量贴近设计。
- 不使用静态假数据替代已接入的真实接口数据。

### 验收标准

- 页面模块和 Figma 图中的主要区域一一对应。
- 筛选、KPI、趋势、表格、环图、明细、底部说明区域都存在。
- 视觉层级清晰，卡片间距、边框、背景、状态色符合当前管理员端风格。
- `npm run build` 通过。

---

## 题目 7B：补齐响应式和视觉回归验收

难度：难

### Prompt

请为管理员端“模型用量 / Model Usage”页面补齐响应式布局和视觉回归验收。

要求：

1. 桌面端宽屏下接近 Figma 的信息密度和布局。
2. 中等宽度下右侧说明卡片能合理下移或折叠，不遮挡主内容。
3. 移动端下筛选区、KPI、图表、表格、明细按可读顺序纵向排列。
4. 表格在小屏下支持横向滚动或卡片化，不出现文字重叠。
5. 图表容器有稳定高度，loading、empty、error 不造成布局跳动。
6. 使用 Playwright 或等效方式截取桌面和移动端页面截图进行验收。

### 验收标准

- 1440px、1024px、390px 三种宽度下页面可读且无明显重叠。
- 所有按钮和筛选控件在移动端可点击。
- 表格、图表、右侧说明不会溢出页面主体。
- 视觉验收截图保存在临时或约定位置，并在最终说明中列出。
- `npm run build` 通过。

---

## 题目 8A：完善管理员导航、菜单状态和路由入口

难度：中等

### Prompt

请完善管理员端“模型用量 / Model Usage”的导航、菜单状态和路由入口。

要求：

1. `/admin/model-usage` 路由可稳定访问。
2. 管理员侧边栏中“模型用量”菜单状态为已实现。
3. 当前页面激活状态正确。
4. 从用户详情页、Agent 调试页等相关入口跳转到模型用量页时，可以带上 `userId`、`model`、`workflow`、`agent` 等查询参数。
5. 页面能读取 query 参数并初始化筛选条件。

### 验收标准

- 管理员导航中不再把“模型用量”显示为占位或待接入。
- 访问 `/admin/model-usage?userId=xxx` 后用户筛选自动生效。
- 从相关页面跳转不会丢失上下文。
- 路由和菜单相关静态测试通过。

---

## 题目 8B：补齐管理员权限和路由守卫验收

难度：难

### Prompt

请补齐管理员端“模型用量 / Model Usage”的权限和路由守卫验收。

要求：

1. 只有管理员或具备对应权限的用户可以访问 `/admin/model-usage`。
2. 未登录用户跳转登录或显示当前项目统一的未登录处理。
3. 普通用户访问时返回 403 或展示统一无权限页面。
4. 后端 model usage API 也必须校验管理员权限，不能只依赖前端路由。
5. 前端对 401 / 403 做出明确提示或跳转。
6. 增加必要的前后端测试。

### 验收标准

- 未登录、普通用户、管理员三种身份行为明确。
- 后端接口没有权限绕过风险。
- 前端路由守卫和后端鉴权行为一致。
- 测试覆盖 401、403、管理员正常访问。

---

## 题目 9A：实现图表 tooltip、分组切换和空态

难度：中等

### Prompt

请完善“模型用量 / Model Usage”页面图表组件的真实交互。

要求：

1. 趋势图支持指标切换：Total Tokens、Input Tokens、Output Tokens、Requests、Cost、Failure Rate、Latency。
2. 趋势图支持分组切换：Provider、Model、Workflow、Agent、User。
3. tooltip 展示当前时间点、分组值、tokens、请求数、成本、失败率、延迟。
4. Provider 分布图 tooltip 展示占比和 token 数。
5. 空数据时展示明确空态，不显示错误图形或 NaN。

### 验收标准

- 切换指标和分组会重新请求或重新渲染对应数据。
- tooltip 数据和图表展示一致。
- 空数据返回时图表区域有清晰空态。
- `npm run build` 通过。

---

## 题目 9B：实现时间 brush、多图联动和局部失败降级

难度：难

### Prompt

请增强“模型用量 / Model Usage”页面图表交互，补齐时间 brush、多图联动和局部失败降级。

要求：

1. 趋势图支持时间 brush 或等效时间窗口选择。
2. 选择时间窗口后，多维表格、Provider 分布、明细事件同步使用该时间范围。
3. 点击趋势图中的 Provider / Model / Workflow / Agent 分组时，可以联动筛选表格和明细。
4. 某一个图表接口失败时，只降级该模块，不影响其他模块展示。
5. 每个图表模块有独立 loading、error、empty 状态。

### 验收标准

- 时间 brush 改变后相关模块数据同步变化。
- 点击图表分组后筛选条件可见且可清除。
- 单个接口 500 时页面整体不崩溃。
- Playwright 或组件测试覆盖至少一个联动场景和一个局部失败场景。

---

## 题目 10A：补齐分页、排序和查询参数约束

难度：中等

### Prompt

请为模型用量后端 API 和前端页面补齐分页、排序和查询参数约束。

要求：

1. events 明细接口支持 `page`、`size`、`sortBy`、`sortOrder`。
2. dimensions 多维聚合接口支持 `limit`、`sortBy`、`sortOrder`。
3. 后端限制最大 `size` 和最大时间范围，避免一次查询过大。
4. 前端表格支持分页、排序、每页条数选择。
5. 参数非法时返回统一错误结构。

### 验收标准

- 明细表可以翻页和排序。
- 多维表格可以按 tokens、cost、requests、failureRate、avgLatency 排序。
- 超过最大查询范围时后端返回明确错误。
- 前端能展示参数错误并引导用户调整筛选。

---

## 题目 10B：优化大数据量 SQL 聚合和索引

难度：难

### Prompt

请针对模型用量统计的大数据量场景优化后端查询性能。

要求：

1. 分析当前 Agent Run / usage / token usage 相关表结构。
2. 为时间范围、model、workflow、agent、user、status、provider 查询设计合适索引。
3. 对 overview、trends、dimensions、providers、events 分别评估 SQL 查询计划。
4. 优先让聚合在数据库层完成，避免无上限拉取到 Service 层聚合。
5. 准备至少 10 万条级别的测试数据或说明等效压测方案。
6. 给出性能基线和优化后结果。

### 验收标准

- 大时间范围查询不会无界扫描全部数据。
- 常用筛选组合命中索引或有明确原因说明。
- 10 万条级别数据下主要接口响应时间有记录。
- 优化没有改变接口响应 schema。
- 后端测试和必要的迁移校验通过。

---

## 题目 11A：提供真实造数方案

难度：中等

### Prompt

请为模型用量功能提供真实可重复的本地造数方案。

要求：

1. 造数覆盖多个 provider、model、workflow、agent、user、status。
2. 覆盖最近 24 小时、7 天、30 天、跨月数据。
3. 覆盖成功、失败、超时、usage 缺失、成本缺失等边界场景。
4. 造数方式可以是 SQL、测试 fixture、后端测试 helper 或本地脚本，需符合当前项目习惯。
5. 文档说明如何执行和如何清理。

### 验收标准

- 本地执行后页面能看到趋势、分布、表格、明细都有数据。
- 边界数据能触发空成本、失败率、延迟等展示。
- 造数脚本可重复执行，不会破坏生产数据。
- 文档包含执行命令和清理方式。

---

## 题目 11B：提供一键本地联调脚本

难度：难

### Prompt

请提供模型用量功能的一键本地联调脚本或清晰的本地联调命令集合。

要求：

1. 启动后端服务。
2. 启动前端服务。
3. 可选执行造数。
4. 输出可访问的 `/admin/model-usage` URL。
5. 检查关键环境变量、端口占用、数据库连接。
6. 失败时输出明确诊断信息。

### 验收标准

- 新人可以按文档或脚本启动完整联调环境。
- 端口占用、数据库未启动、缺少环境变量时有明确提示。
- 页面可以访问真实后端接口。
- 不引入会影响现有本地开发流程的破坏性命令。

---

## 题目 12A：完善当前筛选范围 CSV 导出

难度：中等

### Prompt

请完善“模型用量 / Model Usage”页面 CSV 导出能力，确保导出内容和当前筛选条件一致。

要求：

1. 导出当前筛选条件下的 events 明细。
2. CSV 包含时间、用户、Provider、模型、Workflow、Agent、输入 Tokens、输出 Tokens、总 Tokens、成本、延迟、状态。
3. 文件名包含导出时间和筛选范围。
4. cost 为 null 时导出空值或 `—`，不要导出错误数字。
5. 前端导出时展示 loading 和失败提示。

### 验收标准

- CSV 内容和页面筛选结果一致。
- 中文 Excel 打开不乱码。
- 空数据时不会下载错误文件，并给出提示。
- 导出失败时页面有错误反馈。

---

## 题目 12B：实现后端化大数据量导出

难度：难

### Prompt

请实现模型用量大数据量 CSV 导出的后端能力。

要求：

1. 新增后端导出接口，接收和页面一致的筛选条件。
2. 支持流式下载或异步导出任务，按当前项目能力选择。
3. 大数据量导出不能让前端一次性拉取全部明细。
4. 导出过程需要权限校验。
5. 导出失败、超时、无数据时返回明确状态。
6. 前端导出按钮改为调用后端导出接口。

### 验收标准

- 大数据量导出不会造成浏览器卡死。
- 后端导出结果字段完整且顺序稳定。
- 权限不足不能导出。
- 前端能展示导出中、成功、失败状态。
- 有测试覆盖导出参数、权限和空数据场景。

---

## 题目 13A：把接口实现状态改为集中配置

难度：中等

### Prompt

请把“模型用量 / Model Usage”页面中的“已完成 / 部分完成 / 未完成”接口实现状态改为集中配置。

要求：

1. 状态项集中维护，不散落在模板中。
2. 每一项包含名称、状态、说明、关联接口或模块。
3. 覆盖 overview、trends、dimensions、providers、events、export、cost、provider 标准化、权限、联动。
4. 前端页面根据配置渲染状态卡片。
5. 当正式后端接口上线后，可以只改配置或少量逻辑完成状态收敛。

### 验收标准

- 页面状态项完整覆盖关键接口和前端功能。
- 状态文案和真实实现一致。
- 新增或修改状态不需要改大量模板。
- 静态测试检查三类状态文案存在。

---

## 题目 13B：实现接口状态随正式能力上线收敛

难度：难

### Prompt

请设计并实现“模型用量 / Model Usage”页面接口实现状态的收敛机制。

要求：

1. 当前临时 Agent Debug 接入阶段标注为部分完成。
2. 正式 admin model usage API 上线后，对应项自动或半自动更新为已完成。
3. 成本、后端导出、权限、图表联动等能力分别有独立状态。
4. 如果后端提供 capability endpoint，则前端可根据 capability 返回值渲染状态；如果暂不提供，则用集中配置并保持文档同步。
5. 不允许页面显示已完成但实际仍依赖临时接口。

### 验收标准

- 状态卡片能准确反映当前实现。
- 切换到正式 API 后，临时接入说明被移除或降级为历史说明。
- capability 或配置变更有测试覆盖。
- 文档说明每个状态项的判定依据。

---

## 题目 14A：统一错误码和前端降级体验

难度：中等

### Prompt

请补齐模型用量功能的错误码处理和前端降级体验。

要求：

1. 后端对参数错误、未登录、无权限、数据不存在、查询范围过大、服务异常返回统一错误结构。
2. 前端分别处理 400、401、403、404、408、429、500。
3. 页面顶部显示全局错误，单模块接口失败显示模块级错误。
4. 支持重试按钮。
5. 超时或限流时提示用户缩小时间范围或稍后重试。

### 验收标准

- 用户能区分无权限、参数错误、服务异常。
- 某个模块失败不会导致整页白屏。
- 重试按钮可重新请求失败模块。
- 测试覆盖至少 400、401、403、500 四类错误。

---

## 题目 14B：补齐超时、限流和部分失败的稳定性测试

难度：难

### Prompt

请为模型用量功能补齐超时、限流和部分失败的稳定性测试。

要求：

1. 模拟 overview 成功、trends 失败、events 成功的部分失败场景。
2. 模拟后端查询超时。
3. 模拟查询范围过大。
4. 模拟用户会话过期。
5. 验证前端 loading 不会永久停留。
6. 验证错误恢复后页面能重新展示数据。

### 验收标准

- 部分失败时成功模块仍正常展示。
- 超时和限流有明确提示。
- 会话过期按项目统一登录流程处理。
- 错误恢复后不需要刷新整个浏览器页面。
- 前后端相关测试通过。
