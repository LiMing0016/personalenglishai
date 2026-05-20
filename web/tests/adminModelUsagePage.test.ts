import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { adminNavGroups } from '../src/layouts/adminNav.ts'

const pageSource = readFileSync(new URL('../src/pages/admin/AdminModelUsagePage.vue', import.meta.url), 'utf8')
const opsAgentApiSource = readFileSync(new URL('../src/api/opsAgent.ts', import.meta.url), 'utf8')

for (const expectedText of [
  'Model Usage',
  '项目总览',
  '用户用量',
  '时间范围',
  '时间粒度',
  'Provider',
  'Workflow',
  'Agent',
  '总 Tokens',
  '请求数',
  '总成本 (USD)',
  '失败率',
  '平均延迟',
  'Tokens 使用趋势',
  'Tokens 分布 (按 Provider)',
  '明细抽屉 (Events)',
  '关联联动',
  '数据来源',
  '支持能力',
  '接口实现状态',
  '模型用量页面区块导航',
  '核心指标',
  'Tokens 趋势',
  '多维拆解',
  '明细事件',
  '操作联动',
  '已完成',
  '部分完成',
  '未完成',
  'Agent run 列表',
  'Run detail usage',
  'Provider 由 model 推断',
  '成本字段',
  '后端聚合 API',
]) {
  assert.ok(pageSource.includes(expectedText), `Admin model usage page should expose ${expectedText}`)
}

for (const expectedClass of [
  'model-usage-page',
  'model-usage-filter-grid',
  'model-usage-kpi-grid',
  'model-usage-chart-card',
  'model-usage-stacked-bars',
  'model-usage-provider-table',
  'model-usage-donut',
  'model-usage-events-card',
  'model-usage-anchor-nav',
  'model-usage-anchor-tab',
  'model-usage-events-table-wrap',
]) {
  assert.ok(pageSource.includes(expectedClass), `Admin model usage page should include ${expectedClass}`)
}

const modelUsageNav = adminNavGroups
  .flatMap((group) => group.items)
  .find((item) => item.to === '/admin/model-usage')

assert.equal(modelUsageNav?.status, 'implemented', 'Model usage nav should not appear as a placeholder')
assert.ok(!pageSource.includes('接口待接入'), 'Admin model usage page should no longer render the placeholder badge')

for (const expectedApiUsage of [
  "import { opsAgentApi",
  'opsAgentApi.listRuns',
  'opsAgentApi.getRun',
  'buildRunQuery',
  'loadUsage',
  'detailsByRunId',
]) {
  assert.ok(pageSource.includes(expectedApiUsage), `Admin model usage page should use backend Agent Debug API via ${expectedApiUsage}`)
}

for (const expectedEndpoint of [
  "'/ops/agent/runs'",
  '`/ops/agent/runs/${encodeURIComponent(runId)}`',
]) {
  assert.ok(opsAgentApiSource.includes(expectedEndpoint), `opsAgentApi should call implemented backend endpoint ${expectedEndpoint}`)
}
