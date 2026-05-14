# 写作首页与 Dashboard 拆分 Trae 实现题目

## 背景

写作模块当前把历史作文、推荐题目、写作统计、成长数据、能力曲线、主题风格等内容都放在一个页面里，首页信息密度过高。

新的设计方向：

- `写作首页` 只保留两类内容：历史作文、每日推荐作文。
- 统计和数据可视化内容统一迁入 `Dashboard`。
- 首页保留米白 academy 风格、黑色“新建作文”按钮和轻量顶部导航。
- 每日推荐先使用 mock 数据，后续再接真实推荐接口。

目标路由：

- `/app/writing`：写作首页。
- `/app/writing/dashboard`：写作 Dashboard。

## 题目 1：新增写作 Dashboard 路由与页面阶段

### Prompt

把写作模块拆出一个 Dashboard 路由。

目标：
让 `/app/writing` 继续作为写作首页，新增 `/app/writing/dashboard` 专门承载数据看板。

要求：

1. 在前端路由中新增 `writing/dashboard`。
2. 路由名称建议为 `WritingDashboard`。
3. 继续复用现有 `WritingPage.vue`，不要新建完全独立页面。
4. 给 `WritingPage` 增加 `dashboard` 阶段。
5. `navigateToPhase`、`resolveRoutePhase`、`routeNameForPhase` 都要支持 dashboard。
6. 编辑器、考试设置、真题选择等原有写作流程不能受影响。

### 验收标准

- 访问 `/app/writing` 仍进入写作首页。
- 访问 `/app/writing/dashboard` 能进入 Dashboard 阶段。
- `/app/writing/mode`、`/app/writing/setup`、`/app/writing/past-prompts`、`/app/writing/editor` 仍正常。
- 没有重复创建第二个写作页组件。
- `npm run build` 通过。

## 题目 2：实现写作页顶部导航

### Prompt

给写作模块增加一个轻量顶部导航，用于切换写作首页和 Dashboard。

目标：
用户在写作模块内部可以清楚地区分“写作练习”和“Dashboard”。

要求：

1. 在写作页顶部增加两个入口：
   - 写作练习
   - Dashboard
2. 点击“写作练习”跳转 `/app/writing`。
3. 点击“Dashboard”跳转 `/app/writing/dashboard`。
4. 当前页面入口要有 active 状态。
5. 风格参考 academy：文字轻、下划线清晰、不要做成重型 tab 卡片。
6. 移动端不能挤压或换行混乱。

### 验收标准

- 首页和 Dashboard 都能看到顶部导航。
- active 状态与当前路由一致。
- 导航不影响左侧 AppRail。
- 页面刷新后 active 状态仍正确。

## 题目 3：改造写作首页为“历史作文 + 每日推荐”

### Prompt

把 `/app/writing` 改造成新的写作首页，只展示历史作文和每日推荐作文。

目标：
首页不再展示统计图表和能力数据，让用户进来后能快速继续旧作文或开始推荐练习。

要求：

1. 首页保留 hero 区：
   - 标题：写作练习
   - 副标题：坚持每天写一点，英语写作自然进步。
   - 右侧黑色“+ 新建作文”按钮
   - 可保留当前线稿插画
2. 首页主内容使用双栏布局：
   - 左侧：历史作文
   - 右侧：每日推荐作文
3. 首页不展示以下模块：
   - 写作总览
   - 成长 / 激励
   - 写作能力
   - 能力成长曲线
   - 写作主题和风格
4. 历史作文继续使用真实 `docList` 数据。
5. 历史作文保留搜索、模式筛选、排序、分页、重命名、删除等原有能力。
6. 双栏在小屏幕下自动变成上下排列。

### 验收标准

- `/app/writing` 首屏核心内容是历史作文和每日推荐作文。
- 首页不再出现 ECharts 数据看板。
- 历史作文卡片点击、菜单、分页仍可用。
- 移动端历史作文和每日推荐不横向溢出。
- `node tests/writingDashboardPrototype.test.ts` 通过。

## 题目 4：实现每日推荐作文模块

### Prompt

实现首页右侧“每日推荐作文”模块。

目标：
先用 mock 数据做出产品形态，后续再替换成真实推荐接口。

要求：

1. 在写作 mock 数据文件中新增每日推荐数据。
2. 每条推荐包含：
   - `id`
   - 难度
   - 体裁
   - 标题
   - 简短说明
   - 推荐字数
   - 预计用时
3. 首页展示 3 条推荐。
4. 每条推荐有“开始练习”按钮。
5. 点击“开始练习”先跳转到现有新建作文流程，不直接创建作文。
6. “换一换”按钮先只做静态 UI，不接随机逻辑。

### 验收标准

- 首页右侧显示“每日推荐作文”。
- 每条推荐能看到难度、体裁、标题、说明、字数、预计用时。
- 点击“开始练习”进入现有新建作文入口。
- mock 数据集中管理，不直接散落在 template 里。
- 不新增后端接口。

## 题目 5：把统计和可视化模块迁入 Dashboard

### Prompt

把写作统计和数据可视化内容迁移到 `/app/writing/dashboard`。

目标：
Dashboard 作为写作数据页，集中展示用户的写作统计、成长、能力、主题风格。

要求：

1. Dashboard 页面保留以下模块：
   - 写作总览
   - 成长 / 激励
   - 写作活跃度
   - 本月目标
   - 写作能力
   - 能力成长曲线
   - 高频错误
   - 词汇与句式
   - 写作主题和风格
2. Dashboard 保留统计筛选：
   - 近7天
   - 近30天
   - 本月
   - 全部
   - 全部 / 自由 / 考试
3. Dashboard 使用现有 ECharts，不新增图表库。
4. ECharts 只在 Dashboard 阶段初始化。
5. 从 Dashboard 切回首页时，需要避免图表实例残留或重复初始化。

### 验收标准

- `/app/writing/dashboard` 能看到完整数据看板。
- `/app/writing` 看不到这些数据模块。
- 切换首页和 Dashboard 不报错。
- 图表 resize 正常。
- `npm run build` 通过。

## 题目 6：补充测试与验收检查

### Prompt

为写作首页和 Dashboard 拆分补充轻量测试。

目标：
保证后续改页面时不会把数据模块又放回首页，或误删 Dashboard 入口。

要求：

1. 更新现有写作 dashboard 原型测试。
2. 测试应覆盖：
   - 存在 `WritingDashboard` 路由
   - 存在 `/app/writing/dashboard`
   - 首页包含“每日推荐作文”
   - 首页包含历史作文结构
   - Dashboard 包含写作总览和数据可视化模块
   - 写作首页和 Dashboard 共享同一个写作页壳
3. 保留 AppRail 图标相关测试。
4. 构建前执行基础测试。

### 验收标准

- `node tests/writingDashboardPrototype.test.ts` 通过。
- `node tests/appRailChrome.test.ts` 通过。
- `npm run build` 通过。
- 没有 TypeScript 编译错误。
- 没有把每日推荐写死在多个文件里。

## 推荐执行顺序

1. 先新增路由和 `dashboard` 阶段。
2. 再做顶部导航。
3. 再把首页改成双栏：历史作文 + 每日推荐。
4. 再把数据模块迁到 Dashboard。
5. 最后补测试和构建验证。

## 暂不做内容

- 不接每日推荐真实后端接口。
- 不做“换一换”的真实随机推荐。
- 不改写作编辑器。
- 不改评分逻辑。
- 不改历史作文接口。
