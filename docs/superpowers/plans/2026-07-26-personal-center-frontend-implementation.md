# 个人中心前端重设计实施计划

> 本计划在 `codex/personal-center-1237` 独立分支和
> `F:\personalenglishai\.worktrees\personal-center-1237` 工作树中执行。

**目标：** 用用户选定的动态学习时间线重建个人中心前端，同时移除重复导航和邀请激励入口，保留现有真实数据与路由。

**范围：** 仅前端。账号安全、注销和兑换码后端能力另行实施。

## Task 1：建立可测试的个人中心导航模型

**Files**

- Create: `web/src/pages/app/personalCenterModel.ts`
- Create: `web/src/pages/app/personalCenterModel.test.ts`
- Modify: `web/src/pages/app/PersonalCenterPage.vue`

**Steps**

1. 先写失败测试，覆盖六个页签、旧 `referral` 回落和有效查询参数解析。
2. 运行 `node --test --experimental-strip-types src/pages/app/personalCenterModel.test.ts`，确认因模块不存在而失败。
3. 实现最小导航模型并运行测试至通过。
4. 将页面导航改为模型驱动，删除邀请激励导入和渲染分支。

## Task 2：实现动态学习时间线的数据模型

**Files**

- Create: `web/src/components/personal-center/learningContinuity.ts`
- Create: `web/src/components/personal-center/learningContinuity.test.ts`
- Create: `web/src/components/personal-center/LearningContinuityPanel.vue`

**Steps**

1. 先写失败测试，覆盖有最近记录、无记录、学习天数边界三种行为。
2. 运行测试确认失败。
3. 实现纯函数模型，确保所有展示内容来自真实输入或明确空状态。
4. 实现选定时间线组件，使用 Lucide 图标与真实路由事件。
5. 再次运行模型测试。

## Task 3：重构个人中心页面框架

**Files**

- Modify: `web/src/pages/app/PersonalCenterPage.vue`

**Steps**

1. 将原二级侧栏重构为顶部用户信息区与横向页签。
2. 保留昵称、学习阶段编辑及查询参数同步。
3. 增加加载、邮箱验证和响应式状态。
4. 检查桌面端只保留 AppRail 全局导航。

## Task 4：升级学习概览与行动型空状态

**Files**

- Modify: `web/src/components/personal-center/OverviewSection.vue`
- Modify: `web/src/components/personal-center/MyEssaysSection.vue`
- Modify: `web/src/components/personal-center/AbilityRadarSection.vue`
- Modify: `web/src/components/personal-center/SubscriptionSection.vue`

**Steps**

1. 在概览顶部接入动态学习时间线。
2. 保留真实统计和最近写作记录，加入写作、翻译、词汇真实入口。
3. 将无记录状态改为带按钮的行动型空状态。
4. 将栏目标题改为新的跨产品信息架构，并明确能力画像的数据来源。
5. 不接入模拟购买；订阅页保留现有查询，并为后续兑换码流程预留文案。

## Task 5：验证与设计 QA

**Files**

- Create: `design-qa.md`

**Steps**

1. 运行两个新增模型测试。
2. 运行 `npm run build`。
3. 启动本地 Vite 预览，并在 Codex 内置浏览器打开个人中心。
4. 分别检查桌面宽度和移动宽度，验证页签、时间线与跳转。
5. 对照选定稿记录设计 QA，修复全部 P0/P1/P2。
6. 保持预览服务和浏览器页面打开，向用户提供可点击本地地址。

## Task 6：完成分支交付检查

1. 检查文档是否与实现一致。
2. 检查工作树没有无关改动。
3. 评估是否可合并到 `main`；本轮视觉改造需要用户预览确认后再合并。
4. 以 Conventional Commits 中文格式提交实现。
