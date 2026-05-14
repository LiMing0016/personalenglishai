import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const writingPageSource = readFileSync(new URL('../src/pages/app/WritingPage.vue', import.meta.url), 'utf8')
const mockSource = readFileSync(new URL('../src/pages/app/writingDashboardMock.ts', import.meta.url), 'utf8')
const overviewCardSource = readFileSync(new URL('../src/components/writing/dashboard/WritingOverviewCard.vue', import.meta.url), 'utf8')
const appLayoutSource = readFileSync(new URL('../src/layouts/AppLayout.vue', import.meta.url), 'utf8')
const routerSource = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const writingApiSource = readFileSync(new URL('../src/api/writing.ts', import.meta.url), 'utf8')

for (const expectedCopy of [
  'PEAI Writing / Practice',
  'Dashboard',
  '每日推荐作文',
  '成长 / 激励',
  '单篇得分趋势',
  '得分分布',
  '作文落点',
  '练习进度',
  '本月目标',
  '写作能力',
  '能力成长曲线',
  '写作主题和风格',
  '历史作文',
]) {
  assert.ok(writingPageSource.includes(expectedCopy), `writing dashboard should include ${expectedCopy}`)
}

for (const expectedClass of [
  'dashboard-hero',
  'new-doc-btn--academy',
  'writing-home-layout',
  'daily-recommendations',
  'WritingOverviewCard',
  'score-trend-card',
  'score-distribution-card',
  'score-scatter-card',
  'practice-progress-section',
  'ability-trend-card',
  'topic-layout',
  'dashboard-chart--score',
  'dashboard-chart--distribution',
  'dashboard-chart--scatter',
  'dashboard-chart--ability',
]) {
  assert.ok(writingPageSource.includes(expectedClass), `writing dashboard should render ${expectedClass}`)
}

assert.ok(routerSource.includes("path: 'writing/dashboard'"), 'writing dashboard should have an explicit route')
assert.ok(writingPageSource.includes("phase === 'doc-list' || phase === 'dashboard'"), 'home and dashboard should share the writing page shell')
assert.ok(writingPageSource.includes('phase === \'dashboard\''), 'analytics modules should render only in dashboard phase')
assert.ok(writingPageSource.includes('phase === \'doc-list\''), 'home page should render the document list phase')
assert.ok(writingApiSource.includes('getWritingDashboard'), 'writing API should expose the real dashboard endpoint')
assert.ok(writingApiSource.includes("'/writing/dashboard'"), 'writing API should request the real writing dashboard endpoint')
assert.ok(writingPageSource.includes('dashboardLoading'), 'writing dashboard should expose loading state')
assert.ok(writingPageSource.includes('dashboardError'), 'writing dashboard should expose error state')
assert.ok(writingPageSource.includes('loadWritingDashboard'), 'writing dashboard should load real dashboard data')
assert.ok(!writingPageSource.includes(':overview="mockWritingOverview"'), 'overview card should not receive mock overview data')
assert.ok(!writingPageSource.includes('mockGrowthDashboard.essayScoreTrend'), 'growth score trend should not render from mock growth data')
assert.ok(!writingPageSource.includes('mockGrowthDashboard.scoreDistribution'), 'score distribution should not render from mock growth data')
assert.ok(writingPageSource.includes('完成评分后展示'), 'dashboard should provide an empty state for missing score data')
assert.ok(!writingPageSource.includes('dashboard-filters'), 'dashboard should not keep a separate outer filter row')
assert.ok(!writingPageSource.includes('dashboardScopeText'), 'dashboard scope copy should live in the overview card')
assert.ok(writingPageSource.includes('const PAGE_SIZE = 9'), 'home page should paginate history essays in groups of nine')
assert.ok(writingPageSource.includes('v-for="doc in displayDocs"'), 'history grid should render only the current page docs')
assert.ok(writingPageSource.includes('showHistoryPagination'), 'history page should expose pagination controls when more than nine essays exist')
assert.ok(writingPageSource.includes('historyPlaceholderCount'), 'short last pages should keep a stable 3x3 grid height')
assert.ok(writingPageSource.includes('repeat(3, minmax(0, 1fr))'), 'wide home page should support a 3x3 essay grid')
assert.ok(mockSource.includes('mockAbilityDashboard'), 'dashboard mock should centralize ability data')
assert.ok(mockSource.includes('mockWritingOverview'), 'dashboard mock should centralize overview data')
assert.ok(mockSource.includes('mockDailyWritingPrompts'), 'home page should use daily writing prompt mock data')
assert.ok(mockSource.includes('CEFR 参考等级'), 'ability mock should use CEFR reference wording')
assert.ok(overviewCardSource.includes('写作总览'), 'overview card should render the overview title')
assert.ok(overviewCardSource.includes('overview-menu-row'), 'overview card should host range and mode menus')
assert.ok(overviewCardSource.includes('overview-date-popover'), 'overview range filter should use a date range popover')
assert.ok(overviewCardSource.includes('type="date"'), 'overview range filter should let users choose start and end dates')
assert.ok(overviewCardSource.includes('customRange'), 'overview card should accept a custom date range')
assert.ok(overviewCardSource.includes('<select'), 'overview mode filter should use a menu-style select')
assert.ok(overviewCardSource.includes('update:range'), 'overview card should emit range changes')
assert.ok(overviewCardSource.includes('update:mode'), 'overview card should emit mode changes')
assert.ok(overviewCardSource.includes('update:customRange'), 'overview card should emit custom range changes')
assert.ok(!overviewCardSource.includes('overview-scope-btn'), 'overview card should not keep the old read-only scope button')
assert.ok(overviewCardSource.includes('AI建议'), 'overview card should render AI advice copy')
assert.ok(overviewCardSource.includes('displayTrend'), 'overview chart should derive displayed points from the selected range')
assert.ok(overviewCardSource.includes('resolveTrendWindow'), 'overview chart should resolve a chart window from range and custom dates')
assert.ok(overviewCardSource.includes("props.range === 'year'"), 'overview chart should support a one-year monthly axis')
assert.ok(overviewCardSource.includes('props.customRange'), 'overview chart should use custom start and end dates for the axis')
assert.ok(overviewCardSource.includes("unit: 'week'"), 'overview chart should aggregate near-30-day and medium custom ranges by week')
assert.ok(!overviewCardSource.includes('buildSyntheticTrendPoint'), 'overview chart should not synthesize mock trend points')
assert.ok(overviewCardSource.includes('WritingDashboardOverview'), 'overview chart should consume the real dashboard API DTO')
assert.ok(overviewCardSource.includes('TARGET_SCORE'), 'overview chart should render an explicit learning target line')
assert.ok(overviewCardSource.includes('MarkLineComponent'), 'overview chart should use ECharts markLine for the target score')
assert.ok(!overviewCardSource.includes("name: '评分次数'"), 'overview chart should not render rating count as a second bar series')
assert.ok(overviewCardSource.includes('item.sourceLabel || item.date'), 'overview chart should render backend-provided labels')
assert.ok(overviewCardSource.includes('BarChart'), 'overview card should render ECharts bars')
assert.ok(overviewCardSource.includes('LineChart'), 'overview card should render ECharts line')
assert.ok(!overviewCardSource.includes('MarkPointComponent'), 'overview card should not emphasize highest score with a heavy mark point')
assert.ok(!writingPageSource.includes('HeatmapChart'), 'growth dashboard should replace heatmap with score distribution')
assert.ok(writingPageSource.includes('PieChart'), 'growth dashboard should use ECharts donut chart for score distribution')
assert.ok(writingPageSource.includes('ScatterChart'), 'growth dashboard should use ECharts scatter chart for essay score landing points')
assert.ok(writingPageSource.includes('scoreBands'), 'single-essay score trend should render score band backgrounds')
for (const expectedBand of ['需要补基础', '基础建立', '稳定提升', '良好', '优秀']) {
  assert.ok(mockSource.includes(expectedBand), `score trend mock should include band ${expectedBand}`)
}
assert.ok(writingPageSource.includes('essayScoreTrend'), 'growth dashboard should use single essay score trend data')
assert.ok(writingPageSource.includes('scoreDistribution'), 'growth dashboard should use score distribution data')
assert.ok(writingPageSource.includes('scoreScatterChartRef'), 'growth dashboard should render a monthly essay landing scatter chart')
assert.ok(writingPageSource.includes('dashboardGrowth.value.scoreScatter'), 'essay landing scatter chart should use backend scatter data')
assert.ok(writingPageSource.includes('score-distribution-card') && writingPageSource.includes('score-scatter-card'), 'right growth column should combine distribution and monthly landing charts')
assert.ok(!writingPageSource.includes('目标 80'), 'single essay score trend should not render a target line')
assert.ok(writingPageSource.includes('MarkAreaComponent'), 'ability dashboard should render CEFR bands with ECharts')
assert.ok(writingPageSource.includes('renderDashboardCharts'), 'dashboard should render ECharts instances')
assert.ok(appLayoutSource.includes('app-layout--writing'), 'writing route should use the warm writing surface')
assert.ok(appLayoutSource.includes("route.path.startsWith('/app/writing')"), 'warm writing surface should be scoped to writing routes')
assert.ok(appLayoutSource.includes('overflow-y: auto'), 'writing route should scroll inside the main content area')
assert.ok(appLayoutSource.includes('overscroll-behavior: contain'), 'writing route scrolling should not drag the app rail')
assert.ok(appLayoutSource.includes(":deep(.app-rail)"), 'writing route should warm the left rail background')
assert.ok(appLayoutSource.includes('--app-sidebar-border: #e4dfd3'), 'writing route should use the warm sidebar border')
assert.ok(!writingPageSource.includes('class="stats-grid"'), 'dashboard should replace the old four-card stats grid')
assert.ok(!writingPageSource.includes('表达升级建议'), 'dashboard should not include expression-upgrade module')
assert.ok(!mockSource.includes('important'), 'dashboard mock should not include word replacement examples')

console.log('writing-dashboard-prototype-ok')
