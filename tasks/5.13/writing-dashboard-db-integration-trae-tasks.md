# 写作 Dashboard 总览与成长激励接入数据库 Trae 实现题目

## 背景

写作 Dashboard 目前已经完成了页面视觉与交互雏形，但“写作总览”和“成长 / 激励”仍主要依赖前端 mock 数据。

当前项目已经具备一部分真实数据基础：

- `documents`
  - 保存作文标题、题目、状态、创建和更新时间。
- `essay_evaluation`
  - 每次评分一条记录，包含总分、维度分、词数、句数、错误数和评分时间。
- `document_score_summary`
  - 每篇作文一条评分摘要，包含首次评分、最新评分、最佳评分和最新错误统计。
- 后端已有 `/api/writing/dashboard/assets`
  - 当前只返回写作资产类统计，尚不能覆盖页面所需的评分总览、趋势和分布。

本次目标是把以下两个模块接入真实数据库：

1. `写作总览`
   - 累计作文
   - 评分次数
   - 平均分
   - 最高分
   - 按时间聚合的完成作文柱状图与平均分折线
   - AI建议
2. `成长 / 激励`
   - 单篇得分趋势
   - 得分分布
   - 80+ 高分占比
   - 作文落点
   - 本月目标与连续写作

第一版原则：

- 优先使用真实数据库数据。
- 不接 LLM 生成建议。
- `AI建议` 先用规则生成，保证稳定、便宜、可测试。
- 不修改评分算法。
- 不修改作文编辑器核心链路。
- 不新增数据库表，优先复用现有表。
- 不新增图表库，继续使用项目已有 ECharts。
- 前端不要继续展示看起来像真实成绩的 mock 数据。

---

## 题目 1：确认 Dashboard 数据口径与响应结构

### Prompt

为写作 Dashboard 设计一套真实数据库响应结构，覆盖 `overview` 和 `growth` 两块数据。

要求：

1. 新增或整理后端响应 DTO / record。
2. 前端新增对应 TypeScript 类型。
3. 统计口径默认值：
   - `range=30d`
   - `mode=all`
   - `scorePolicy=latest`
4. 支持查询参数：
   - `range=7d|14d|30d|year|all|custom`
   - `mode=all|free|exam`
   - `start=yyyy-MM-dd`
   - `end=yyyy-MM-dd`
5. `mode` 判定规则：
   - 有有效 `task_prompt` 视为 `exam`
   - 无有效 `task_prompt` 视为 `free`
6. 平均分默认按“每篇作文最新评分”计算。
7. 评分次数按 `essay_evaluation` 真实记录数计算，允许同一篇作文多次评分。
8. 空数据时字段结构稳定，不返回 null 断层。

### 验收标准

- 后端响应至少包含：
  - `overview`
  - `growth`
  - `scope`
- `overview.summary` 包含：
  - `totalEssays`
  - `totalSubmissions`
  - `averageScore`
  - `bestScore`
- `overview.trend` 包含：
  - `date`
  - `sourceLabel`
  - `essayCount`
  - `submissionCount`
  - `averageScore`
  - `bestScore`
- `growth` 包含：
  - `essayScoreTrend`
  - `scoreDistribution`
  - `scoreBands`
  - `monthlyGoal`
  - `streak`
- 前后端字段命名一致。
- 空数据返回空数组和 0，不让前端报错。

---

## 题目 2：实现后端 Dashboard 聚合接口

### Prompt

实现真实数据库版写作 Dashboard 聚合接口。

建议接口：

```http
GET /api/writing/dashboard
```

要求：

1. Controller 只做鉴权、参数接收和调用 service。
2. 业务逻辑放在 `WritingDashboardService`。
3. 复用现有 `DocumentScoreSummaryMapper` / `EssayEvaluationMapper`，必要时新增查询方法。
4. 只统计当前登录用户的数据。
5. 排除已删除作文：
   - `documents.deleted_at IS NULL`
6. 排除无有效分数记录：
   - `overall_score IS NOT NULL`
7. 支持 `range`、`mode`、`start`、`end` 过滤。
8. 非法参数使用安全默认值，不让接口 500。

### 验收标准

- `GET /api/writing/dashboard` 可返回真实数据库聚合数据。
- 未登录返回 401。
- Controller 不直接拼复杂 JSON。
- Service 中有清晰的参数归一化逻辑。
- Mapper 查询不跨用户读取数据。
- 不影响已有 `/api/writing/dashboard/assets`。

---

## 题目 3：实现写作总览真实统计

### Prompt

把 `写作总览` 所需数据改为后端真实统计。

统计规则：

1. `累计作文`
   - 统计当前范围内有最新评分摘要的作文数。
2. `评分次数`
   - 统计当前范围内的 `essay_evaluation` 记录数。
3. `平均分`
   - 默认按每篇作文最新评分平均。
4. `最高分`
   - 默认取每篇作文最新评分中的最高分。
5. 趋势图：
   - `7d` / `14d` 按天聚合。
   - `30d` / 自定义短范围按周聚合。
   - `year` 按月聚合。
   - `all` 根据数据跨度自动按月或年聚合。

### 验收标准

- 顶部四张卡不再使用 `mockWritingOverview`。
- 时间范围切换后数据重新请求。
- 写作模式切换后数据重新请求。
- 趋势图中的柱子来自真实完成作文数。
- 趋势图中的折线来自真实平均分。
- 数据不足时展示稳定空态，不展示伪造趋势。

---

## 题目 4：实现规则版 AI建议

### Prompt

为 `写作总览` 和 `成长 / 激励` 实现第一版规则建议，不接 LLM。

建议规则：

1. 样本少：
   - 评分次数少于 3 次时提示继续完成评分后再观察趋势。
2. 最近上升：
   - 最近 3 篇平均分高于前 3 篇时提示保持练习节奏。
3. 最近下降：
   - 最近 3 篇平均分低于前 3 篇时提示复盘最近作文问题。
4. 高分占比低：
   - 80 分以上占比低于 30% 时提示优先稳定基础表达。
5. 无数据：
   - 提示先完成一篇作文评分。

要求：

1. 规则建议放在后端 service 中集中生成。
2. 不在前端硬编码复杂判断。
3. 文案保持克制，不夸大能力判断。
4. 不出现“官方认证”“已达到某等级”等绝对化表达。

### 验收标准

- `overview.insight` 来自接口。
- `growth.insight` 来自接口。
- 无数据、少数据、上升、下降都有可测试分支。
- 不调用 OpenAI API。
- 不新增 prompt。

---

## 题目 5：实现成长 / 激励真实数据

### Prompt

把 `成长 / 激励` 模块改为真实数据库数据。

要求：

1. `essayScoreTrend`
   - 每个点代表一篇作文的最新评分。
   - 按最新评分时间升序排列。
   - 包含作文标题、模式、分数、评分时间、较上一篇变化。
2. `scoreDistribution`
   - 按每篇作文最新评分分桶。
   - 分桶固定为：
     - `<60`
     - `60-70`
     - `70-80`
     - `80-90`
     - `90-100`
3. `highScorePercent`
   - 统计 80 分以上作文占全部有评分作文的比例。
4. `scoreScatter`
   - 按月份展示每篇作文落在哪个分数区间。
5. `monthlyGoal`
   - 第一版目标值可固定为 3 篇 / 月。
   - 完成数来自当月有评分的作文数。
6. `streak`
   - 当前连续写作天数和最长连续天数可基于评分日期计算。
   - 如果实现成本较高，第一版允许只返回当前月活跃天数，但字段要稳定。

### 验收标准

- `单篇得分趋势` 不再使用 `mockGrowthDashboard.essayScoreTrend`。
- `得分分布` 不再使用 `mockGrowthDashboard.scoreDistribution`。
- 80+ 占比来自真实分桶计算。
- 作文标题、模式、评分时间来自数据库。
- 少于 2 篇时趋势图显示空态。
- 没有评分时分布图显示空态。

---

## 题目 6：前端接入 Dashboard API

### Prompt

前端接入真实 Dashboard API，并替换页面中的 mock 数据引用。

要求：

1. 在 `web/src/api/writing.ts` 新增 API 方法。
2. 在前端新增或整理 Dashboard 类型。
3. `WritingPage.vue` 或 dashboard composable 中维护：
   - loading
   - error
   - data
4. 筛选条件变化后重新请求：
   - `dashboardRange`
   - `dashboardMode`
   - `dashboardCustomRange`
5. 请求失败时不展示 mock 成绩。
6. 请求失败可以展示错误提示或空态。
7. 保留现有 ECharts 渲染效果。

### 验收标准

- 页面打开 Dashboard 后会请求 `/api/writing/dashboard`。
- 切换时间范围会重新请求。
- 切换写作模式会重新请求。
- `WritingOverviewCard` 消费接口返回的 `overview`。
- 成长区图表消费接口返回的 `growth`。
- 不再把 `mockWritingOverview` 作为真实页面数据传入。
- 不再把 `mockGrowthDashboard` 作为真实页面数据传入。

---

## 题目 7：处理空数据、少数据与加载状态

### Prompt

完善 Dashboard 的真实数据加载体验，避免新用户看到假成绩或报错。

要求：

1. 加载中显示轻量 loading。
2. 无作文时：
   - 总览卡显示 0 或 `--`
   - 图表显示引导文案
   - 建议提示先完成一篇作文评分
3. 有作文但无评分时：
   - 作文数可以显示
   - 评分相关数据显示 `--` 或 0
   - 成长图表显示“完成评分后展示”
4. 少于 2 篇评分时：
   - 单篇得分趋势显示空态。
5. 请求失败时：
   - 页面不崩溃。
   - 不展示 mock 分数。

### 验收标准

- 新用户 Dashboard 不出现 78、92 这类 mock 分数。
- 接口失败时页面仍可进入写作练习和历史作文。
- ECharts 不在空 DOM 或空数据上报错。
- 图表实例卸载时正常 dispose。

---

## 题目 8：后端测试

### Prompt

为真实 Dashboard 聚合接口补后端测试。

要求：

1. 覆盖 controller 鉴权。
2. 覆盖默认参数：
   - `range=30d`
   - `mode=all`
3. 覆盖模式筛选：
   - `free`
   - `exam`
4. 覆盖空数据。
5. 覆盖少数据。
6. 覆盖得分分布分桶。
7. 覆盖规则建议至少 2 个分支。
8. 保留已有 `/dashboard/assets` 测试。

### 验收标准

后端至少运行：

```bash
.\mvnw.cmd -q "-Dtest=WritingControllerTest" test
```

如果新增 service 单测，也需要运行对应测试。

---

## 题目 9：前端测试与构建

### Prompt

为 Dashboard 真实数据接入补前端测试和构建验证。

要求：

1. 测试 API 方法存在。
2. 测试页面不再直接传入 `mockWritingOverview`。
3. 测试页面不再直接使用 `mockGrowthDashboard` 渲染真实 Dashboard。
4. 测试关键文案：
   - 写作总览
   - 成长 / 激励
   - 单篇得分趋势
   - 分布分析
5. 测试空态文案。
6. 构建必须通过。

### 验收标准

建议运行：

```bash
npm run build
```

如项目已有对应测试脚本，也一起运行：

```bash
node tests\writingDashboardPrototype.test.ts
```

---

## 题目 10：文档与交付说明

### Prompt

更新 Dashboard 数据说明，记录真实数据库接入后的统计口径和边界。

要求：

1. 说明默认统计口径：
   - 近 30 天
   - 全部模式
   - 每篇最新评分
2. 说明真实数据来源：
   - `documents`
   - `essay_evaluation`
   - `document_score_summary`
3. 说明哪些字段第一版仍是规则或固定值：
   - AI建议是规则生成
   - 月目标默认 3 篇
   - 连续写作如未完整实现，需要说明当前口径
4. 说明不接 LLM 的原因：
   - 稳定
   - 可测试
   - 成本低
5. 说明后续可扩展方向：
   - 接入画像记忆
   - 接入 LLM 生成更个性化建议
   - 按学段 / 题型 / 体裁拆分统计

### 验收标准

- 文档能帮助后续开发理解统计口径。
- 文档不把规则建议描述成真正 AI 深度分析。
- 文档不承诺未实现的个性化能力。

---

## 推荐执行顺序

1. 先定义后端响应结构和前端类型。
2. 实现 `/api/writing/dashboard` 聚合接口。
3. 补后端测试，确认统计口径。
4. 前端接入 overview。
5. 前端接入 growth。
6. 处理空态、loading、error。
7. 跑前后端验证。
8. 更新数据说明文档。

## 暂不做内容

- 不接 OpenAI API 生成 Dashboard 建议。
- 不做用户画像和长期记忆。
- 不修改评分模型和评分 prompt。
- 不新增数据库表。
- 不新增图表库。
- 不改作文编辑器主流程。
- 不改历史作文列表的打开、重命名、删除逻辑。
