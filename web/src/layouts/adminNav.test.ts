import test from 'node:test'
import assert from 'node:assert/strict'

import { adminNavGroups } from './adminNav.ts'

test('admin navigation links Agent Debug to the standalone AI ops console', () => {
  const aiGroup = adminNavGroups.find((group) => group.label === 'AI 与 Agent')
  const agentDebug = aiGroup?.items.find((item) => item.label === 'Agent Debug')

  assert.equal(agentDebug?.to, '/ops/agent/runs')
  assert.equal(agentDebug?.status, 'implemented')
})

test('admin navigation exposes BI analytics pages with explicit placeholder states', () => {
  const analyticsGroup = adminNavGroups.find((group) => group.label === '数据分析')

  assert.deepEqual(
    analyticsGroup?.items.map((item) => [item.label, item.to, item.status]),
    [
      ['BI 总览', '/admin/analytics', 'implemented'],
      ['用户分析', '/admin/analytics/users', 'placeholder'],
      ['订阅分析', '/admin/analytics/subscriptions', 'implemented'],
      ['写作分析', '/admin/analytics/writing', 'placeholder'],
      ['AI 用量', '/admin/analytics/ai-usage', 'placeholder'],
      ['转化漏斗', '/admin/analytics/funnel', 'placeholder'],
    ],
  )
})
