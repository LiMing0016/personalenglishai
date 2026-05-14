# 写作首页 Dashboard 改造 Trae 任务拆分

## 背景

写作首页需要从“作文列表页”升级成“英语写作学习 Dashboard”。第一版允许部分模块使用 mock 数据，但前后端结构必须完整，后续可以逐步替换成真实聚合接口。

本次 Dashboard 默认统计口径：

- 时间范围：近 30 天
- 写作模式：全部
- 分数口径：按每篇最新评分

页面模块：

1. 顶部总览
2. 成长 / 激励
3. 写作能力
4. 写作主题和风格
5. 历史作文

统一查询参数建议：

```http
range=7d|30d|month|all
mode=all|free|exam
scorePolicy=latest|initial
```

统一原则：

- 前端可视化优先使用项目已有 ECharts，不新增图表库。
- heatmap、进度条、徽章、标签云优先用 Vue + CSS 实现。
- mock 数据必须集中在 mock provider / mock factory，不要散落在模板里。
- CEFR / Cambridge 文案只能写“参考等级”或“估算等级”，不能写官方认证。

---

# 题目 1：Dashboard 数据契约与 Mock Provider

## 目标

先把写作 Dashboard 的前后端数据结构定下来。第一版可以返回 mock，但字段要稳定，后续真实接口替换时页面组件不用大改。

## 第 1 轮：定义总数据结构

### Prompt

先给写作 Dashboard 建一套统一数据结构。后端补 dashboard response DTO，前端补 TypeScript 类型。先不要做完整 UI，重点是字段清楚、模块边界清楚。

### 验收标准

- 后端有 dashboard response DTO 或 record
- 前端有 `writingDashboardTypes.ts`
- 类型覆盖 overview、growth、ability、topicStyle、history
- 字段命名保持前后端一致
- 不把 mock 数据写进 Vue 模板

## 第 2 轮：实现后端 Mock Provider

### Prompt

后端先实现 Dashboard mock provider。controller 调 service，service 从 mock provider 拿数据，不要在 controller 里直接拼 JSON。

### 验收标准

- 有 dashboard controller 或 service
- mock 数据集中管理
- 支持 `range`、`mode`、`scorePolicy` 参数
- 参数缺省时使用默认口径
- 空数据结构稳定，不返回 null 断层

## 第 3 轮：实现前端 Mock Adapter

### Prompt

前端实现 dashboard 数据 adapter。页面未来只消费 adapter 返回的数据。接口还没完整可用时，可以 fallback 到 mock。

### 验收标准

- 有 `writingDashboardMock.ts`
- 有 `useWritingDashboardData` 或等价 composable
- 前端请求失败时能使用 mock fallback
- mock fallback 有 TODO 注释，说明后续替换真实接口
- 页面不用关心数据来自接口还是 mock

## 第 4 轮：基础测试和文档

### Prompt

给 Dashboard 数据契约补基础测试和说明文档。说明当前哪些字段真实、哪些字段 mock、默认统计口径是什么。

### 验收标准

- 后端测试覆盖 dashboard 基础响应
- 前端测试覆盖默认口径文案
- 文档说明 mock 字段和后续替换路径
- 前端构建通过
- 后端测试通过

---

# 题目 2：页面框架、全局筛选与口径说明

## 目标

把写作首页主体布局搭出来，支持全局时间范围和写作模式筛选，并清楚展示当前统计口径。

## 第 1 轮：后端支持全局筛选参数

### Prompt

后端 Dashboard 接口先支持 `range`、`mode`、`scorePolicy` 三个参数。第一版可以不完全影响所有 mock 数据，但参数解析和默认值要做好。

### 验收标准

- 支持 `range=7d|30d|month|all`
- 支持 `mode=all|free|exam`
- 支持 `scorePolicy=latest|initial`
- 非法参数有合理默认或错误处理
- 测试覆盖默认参数

## 第 2 轮：前端实现 Dashboard 页面骨架

### Prompt

在写作首页 doc-list 阶段搭出 Dashboard 页面骨架。保留现有进入编辑器流程，先把模块区域占位出来。

### 验收标准

- 页面包含标题、副标题、新建作文按钮
- 页面包含全局筛选区域
- 有模块占位：总览、成长、能力、主题风格、历史作文
- 不影响 `phase === 'editor'`
- 不影响新建作文流程

## 第 3 轮：实现筛选状态联动

### Prompt

把全局筛选状态接入 Dashboard adapter。用户切换时间范围或模式后，Dashboard 数据重新加载。

### 验收标准

- 默认选中近 30 天、全部模式
- 切换筛选会重新请求或重新取 adapter 数据
- 页面展示 `当前统计：近30天 · 全部模式 · 按每篇最新评分`
- loading 状态不闪烁、不阻塞历史作文基本操作

## 第 4 轮：响应式布局

### Prompt

把 Dashboard 主体布局做成桌面端两列/多列，窄屏自动单列。不要出现横向滚动和卡片重叠。

### 验收标准

- 桌面端接近 dashboard 原型
- 小屏幕单列展示
- 卡片文字不溢出
- `npm run build` 通过

---

# 题目 3：顶部总览统计卡

## 目标

实现顶部四张统计卡：累计作文、评分次数、平均分、最高分。已有真实数据优先使用真实，趋势线和对比文案第一版可以 mock。

## 第 1 轮：后端 Overview 数据

### Prompt

实现 Dashboard overview 数据结构。后端尽量用真实作文数据计算累计作文、评分次数、平均分、最高分；算不了的较上周变化和小趋势先 mock。

### 验收标准

- 返回 totalEssays、totalSubmissions、averageScore、bestScore
- 返回每张卡的小趋势数据
- 平均分默认按每篇最新评分
- 最高分默认取单篇最新评分最高
- 支持 range 和 mode

## 第 2 轮：前端统计卡 UI

### Prompt

实现顶部四张统计卡 UI。每张卡包含图标、主数字、单位、说明文案和小趋势线。

### 验收标准

- 展示累计作文、评分次数、平均分、最高分
- 每张卡视觉统一
- 主数字醒目，说明文案清楚
- 趋势线根据数组渲染，不是图片
- 空数据时显示 `--`

## 第 3 轮：统计口径文案

### Prompt

给统计卡补口径说明。尤其是平均分和最高分，用户要知道是按最新评分统计。

### 验收标准

- 平均分说明包含“按每篇最新评分”
- 最高分说明包含“单篇最新评分最高”
- 评分次数说明包含“含重复评分”
- 累计作文可显示自由 / 考试拆分

## 第 4 轮：测试

### Prompt

为 overview 补前后端基础测试。重点测默认口径和空数据。

### 验收标准

- 后端测试覆盖 overview 字段
- 前端测试覆盖四张卡关键文案
- 空数据不报错
- `npm run build` 通过

---

# 题目 4：成长 / 激励板块

## 目标

实现成长 / 激励板块，包括得分趋势、写作活跃度、连续写作、本月目标。得分趋势优先真实，其他第一版可 mock。

## 第 1 轮：后端 Growth 数据结构

### Prompt

后端补 growth 数据结构，包含 scoreTrend、activityHeatmap、streak、monthlyGoal。第一版得分趋势尽量真实，其他可以 mock。

### 验收标准

- 返回 scoreTrend points
- 返回 activityHeatmap days
- 返回 streak 当前连续天数和最长连续天数
- 返回 monthlyGoal 目标和完成数
- 支持 range 和 mode

## 第 2 轮：得分趋势图

### Prompt

前端实现“得分趋势”图。使用 ECharts 折线面积图，数据不足时展示引导文案。

### 验收标准

- 使用 ECharts LineChart
- 横轴是日期，纵轴是分数
- 绿色折线 + 浅绿色面积
- tooltip 展示日期和得分
- 少于 3 个点时显示“完成 3 次以上评分后展示趋势图”

## 第 3 轮：写作活跃度 Heatmap

### Prompt

实现写作活跃度 heatmap。用 CSS grid 渲染绿色方格，不要用静态图片。

### 验收标准

- 显示最近若干周活动格子
- 色阶至少 4 档
- 有低到高图例
- hover 或 title 能看到日期和次数
- 数据来自 adapter

## 第 4 轮：连续写作和本月目标

### Prompt

实现连续写作卡和本月目标卡。目标卡要有进度条，连续写作要显示当前和最长记录。

### 验收标准

- 展示连续写作天数
- 展示最长连续记录
- 展示本月目标完成数
- 进度条百分比正确
- 空数据时展示鼓励文案

## 第 5 轮：测试和文档

### Prompt

补 growth 模块测试和 mock 说明。说明哪些是真实、哪些暂时 mock。

### 验收标准

- 后端测试覆盖 growth 响应
- 前端测试覆盖趋势图空状态和 heatmap 存在
- 文档说明 mock 边界
- `npm run build` 通过

---

# 题目 5：写作能力等级与 CEFR 成长曲线

## 目标

实现写作能力核心模块，让用户看到当前写作参考等级、目标等级、最近成长点，以及带 CEFR 背景分层的能力成长曲线。

## 第 1 轮：后端 Ability 接口和 DTO

### Prompt

实现写作能力 dashboard 接口。第一版可以 mock，但接口结构要完整，包含 level、growth、trend 三部分。

建议接口：

```http
GET /api/writing/dashboard/ability
```

### 验收标准

- 支持 `range` 和 `mode`
- 返回 currentLevel、targetLevel、progressToNext
- 返回最近成长点 items
- 返回 cefrBands 和 trend points
- controller 不直接硬编码 JSON

## 第 2 轮：前端 API、类型和 mock

### Prompt

前端新增 ability dashboard 类型、API 方法和 mock 数据。后续组件只消费这个统一结构。

### 验收标准

- 有 `WritingAbilityDashboardResponse` 类型
- 有 `getWritingDashboardAbility` API 方法
- 有 `mockWritingAbilityDashboard`
- 请求失败可 fallback 到 mock
- mock 数据包含 A2/B1/B2/C1/C2 分层

## 第 3 轮：当前等级和最近成长点 UI

### Prompt

实现写作能力上半部分 UI：当前写作水平和最近成长点。文案要强调“参考等级 / 估算”。

### 验收标准

- 展示当前等级，例如 `B1+`
- 展示目标等级，例如 `B2`
- 展示进度条
- 展示“基于最近 5 次评分估算”
- 展示成长点 delta，正数绿色，负数红色
- 不出现“官方认证等级”

## 第 4 轮：能力成长曲线 ECharts

### Prompt

实现能力成长曲线。用 ECharts LineChart，背景要体现 CEFR 等级区间。

### 验收标准

- 容器高度约 320px
- Y 轴固定 40 到 100
- 背景分层显示 A2 / B1 / B2 / C1 / C2
- 折线包含综合能力、词汇丰富度、语法准确性、结构连贯、句式复杂度
- legend 可点击开关
- tooltip 显示日期、各项分数和参考等级

## 第 5 轮：空状态、resize 和体验细节

### Prompt

补能力曲线的空状态和 resize。没有数据时不要初始化空图表。

### 验收标准

- points 为空时显示“完成 3 次以上评分后展示能力成长曲线”
- 窗口尺寸变化后图表 resize 正常
- 组件卸载时 dispose 图表
- loading / error 状态合理

## 第 6 轮：测试和文案验收

### Prompt

给能力模块补前后端测试，重点检查 CEFR 文案和曲线数据结构。

### 验收标准

- 后端测试覆盖 level、growth、trend
- 前端测试覆盖 B1+、B2、CEFR 参考等级文案
- 测试确保不出现“官方认证”
- `npm run build` 通过

---

# 题目 6：写作能力诊断：高频错误与词汇句式

## 目标

在写作能力板块补诊断区：高频错误排行、词汇与句式指标。第一版可混合真实统计和 mock。

## 第 1 轮：后端 Diagnostics 数据

### Prompt

后端补 ability diagnostics 数据。已有真实错误统计就用真实；没有的错误类型和词汇句式指标先 mock。

### 验收标准

- 返回 errorRanking
- 返回 lexicalSyntaxMetrics
- errorRanking 至少支持 label、count、severity
- metrics 至少包含高级词占比、重复词比例、平均句长
- mock 字段集中管理

## 第 2 轮：高频错误 UI

### Prompt

前端实现高频错误排行。用横向条形进度展示，不一定用 ECharts。

### 验收标准

- 展示冠词错误、时态错误、主谓一致、介词搭配
- 每项有次数和横向条
- 颜色有轻重区分
- 空数据时显示“完成评分后展示高频错误”

## 第 3 轮：词汇与句式指标 UI

### Prompt

实现词汇与句式指标卡，展示高级词占比、重复词比例、平均句长。

### 验收标准

- 三个指标清晰展示
- 百分比使用进度条
- 平均句长显示单位“词”
- 指标说明简短，不占太多空间

## 第 4 轮：测试

### Prompt

补诊断模块测试。重点是数据结构、空状态和关键文案。

### 验收标准

- 后端测试覆盖 diagnostics 字段
- 前端测试覆盖高频错误和词汇句式文案
- `npm run build` 通过

---

# 题目 7：写作主题和风格板块

## 目标

实现写作主题和风格板块，包括常练主题、体裁分布、推荐下一篇。不做“表达升级建议”。

## 第 1 轮：后端 Topic Style 数据

### Prompt

后端补 topicStyle 数据结构。第一版主题、体裁分布、推荐下一篇可以 mock，但要支持后续真实统计替换。

### 验收标准

- 返回 topicTags
- 返回 genreDistribution
- 返回 recommendedNextPrompt
- 支持 range 和 mode
- 不返回表达升级建议字段

## 第 2 轮：常练主题标签

### Prompt

前端实现常练主题标签卡。标签用不同浅色背景，但不要太花。

### 验收标准

- 展示 education、environment、technology、campus life 等标签
- 标签大小可以按权重变化
- 数据来自 topicTags
- 空数据时显示推荐练习主题

## 第 3 轮：体裁分布图

### Prompt

实现体裁分布。可以用 ECharts 环形图，也可以用横向条。优先保证清晰和稳定。

### 验收标准

- 展示议论文、应用文、图表作文、书信
- 百分比合计合理
- tooltip 或文案能看出数量/比例
- 图表 resize 正常

## 第 4 轮：推荐下一篇

### Prompt

实现推荐下一篇卡片。展示推荐题目、推荐原因、难度和开始练习入口。

### 验收标准

- 展示推荐标题
- 展示推荐原因
- 展示难度
- 按钮可以进入新建作文流程或先 toast
- 不出现 `important → essential` 这类表达升级内容

## 第 5 轮：测试

### Prompt

补主题风格模块测试，尤其检查不出现表达升级建议。

### 验收标准

- 后端测试覆盖 topicStyle 响应
- 前端测试覆盖常练主题、体裁分布、推荐下一篇
- 测试确保不出现“表达升级建议”
- `npm run build` 通过

---

# 题目 8：历史作文卡片增强

## 目标

把历史作文从普通文件列表升级成学习记录卡片。继续使用真实作文列表，保留搜索、筛选、排序、打开、重命名、删除。

## 第 1 轮：后端历史作文字段检查

### Prompt

检查当前历史作文列表接口字段是否足够支持增强卡片。缺字段先兼容现有字段，必要时补轻量字段，但不要大改编辑器流程。

### 验收标准

- 明确现有字段：title、taskPrompt、latestScore、initialScore、submitCount、updatedAt、status
- 如新增字段，保持向后兼容
- 不影响打开作文接口
- 后端测试通过

## 第 2 轮：卡片信息结构

### Prompt

前端先整理历史作文卡片需要的派生字段，比如状态、题目摘要、分数变化、下一步建议。

### 验收标准

- 有清晰 helper 或 computed
- 支持待评分、已评分、题目草稿
- 支持较初评变化
- 支持自由写作默认摘要
- 不把复杂判断写满模板

## 第 3 轮：增强卡片 UI

### Prompt

实现增强后的历史作文卡片 UI。卡片要像学习记录，不只是文件列表。

### 验收标准

- 展示自由 / 考试标签
- 展示状态标签
- 展示标题和题目摘要
- 展示最新分、评分次数、较初评、最近修改
- 展示下一步建议和继续写作
- 文字不溢出

## 第 4 轮：保留原有操作

### Prompt

确认增强卡片后，原来的打开、重命名、删除、筛选、搜索、排序都不回退。

### 验收标准

- 点击卡片仍能进入编辑器
- 重命名可用
- 删除可用
- 搜索可用
- 全部 / 自由 / 考试筛选可用
- 最近修改排序可用

## 第 5 轮：测试

### Prompt

给历史作文卡片补测试，重点覆盖关键文案和原有入口不回退。

### 验收标准

- 前端测试覆盖状态、评分次数、较初评、继续写作
- 原有 app writing 页面测试通过
- `npm run build` 通过

---

# 题目 9：Dashboard 后端聚合接口整合

## 目标

在各模块接口稳定后，提供一个聚合接口，方便写作首页一次获取完整 Dashboard 数据。

## 第 1 轮：设计聚合响应

### Prompt

设计并实现 `GET /api/writing/dashboard` 聚合接口，响应包含 overview、growth、ability、diagnostics、topicStyle。

### 验收标准

- 聚合接口结构完整
- 支持 range、mode、scorePolicy
- 内部复用各模块 service
- 不在 controller 里重复拼装复杂逻辑

## 第 2 轮：前端接入聚合接口

### Prompt

前端 adapter 优先请求聚合接口。聚合接口失败时再 fallback 到分模块接口或 mock。

### 验收标准

- 页面只通过统一 adapter 获取 Dashboard 数据
- fallback 逻辑集中管理
- 页面组件不关心接口拆分
- 请求失败不影响历史作文基本展示

## 第 3 轮：空数据用户体验

### Prompt

处理新用户或无数据用户。所有模块要有合理空状态，不要展示一堆假的真实成绩。

### 验收标准

- 总览卡显示 0 或 `--`
- 趋势图显示引导文案
- 能力曲线显示评分次数不足文案
- 历史作文显示开始写作引导
- 页面不报错

## 第 4 轮：聚合接口测试

### Prompt

补聚合接口测试，覆盖默认参数、模式筛选、空数据和基础字段。

### 验收标准

- 后端测试覆盖 `GET /api/writing/dashboard`
- 前端测试覆盖 adapter fallback
- 构建和测试通过

---

# 题目 10：Dashboard 组件拆分、验收测试与说明文档

## 目标

收尾整理 Dashboard 前端组件，补测试和说明文档，确保后续接真实数据时不需要推翻结构。

## 第 1 轮：组件拆分

### Prompt

把写作首页 Dashboard 拆成独立组件，避免 `WritingPage.vue` 继续膨胀。

建议组件：

- `WritingDashboardHeader`
- `WritingOverviewCards`
- `WritingGrowthPanel`
- `WritingAbilityPanel`
- `WritingTopicStylePanel`
- `WritingHistorySection`

### 验收标准

- `WritingPage.vue` 只负责阶段、数据和编排
- 每个组件 props 类型清晰
- 组件之间没有互相直接改状态
- 不影响编辑器页面

## 第 2 轮：前端验收测试

### Prompt

补 Dashboard 前端验收测试。覆盖模块存在、默认口径、关键文案和不该出现的模块。

### 验收标准

- 测试覆盖顶部总览
- 测试覆盖成长 / 激励
- 测试覆盖写作能力
- 测试覆盖主题和风格
- 测试覆盖历史作文
- 测试确保不出现“表达升级建议”

## 第 3 轮：后端验收测试

### Prompt

整理 Dashboard 后端测试，保证模块接口和聚合接口都有基础覆盖。

### 验收标准

- overview 测试通过
- growth 测试通过
- ability 测试通过
- topicStyle 测试通过
- dashboard 聚合测试通过

## 第 4 轮：文档说明

### Prompt

写一份 Dashboard 数据说明文档。说明默认口径、真实字段、mock 字段、后续替换计划和 CEFR 文案边界。

### 验收标准

- 文档说明默认统计口径
- 文档列出 mock 模块
- 文档说明后端真实接口替换路线
- 文档强调 CEFR 是参考等级，不是官方认证

## 第 5 轮：最终验证

### Prompt

做最终验证，确保写作首页 Dashboard、历史作文、编辑器入口都正常。

### 验收标准

- `npm run build` 通过
- 后端测试通过
- 写作首页能正常加载
- 新建作文能进入流程
- 打开历史作文能进入编辑器
- 没有明显布局重叠和横向溢出

