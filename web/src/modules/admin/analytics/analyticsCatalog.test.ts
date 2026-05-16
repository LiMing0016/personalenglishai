import test from 'node:test'
import assert from 'node:assert/strict'

import { analyticsPages, dataSourceLabel } from './analyticsCatalog.ts'

test('analyticsPages separates real, mock, and pending data sources', () => {
  assert.deepEqual(
    analyticsPages.map((page) => [page.key, page.path, page.dataSource]),
    [
      ['overview', '/admin/analytics', 'mixed'],
      ['users', '/admin/analytics/users', 'todo'],
      ['subscriptions', '/admin/analytics/subscriptions', 'mixed'],
      ['writing', '/admin/analytics/writing', 'todo'],
      ['ai-usage', '/admin/analytics/ai-usage', 'todo'],
      ['funnel', '/admin/analytics/funnel', 'mock'],
    ],
  )
})

test('dataSourceLabel keeps data provenance visible in the UI', () => {
  assert.equal(dataSourceLabel('realtime'), '真实数据')
  assert.equal(dataSourceLabel('mixed'), '部分接入')
  assert.equal(dataSourceLabel('mock'), 'Mock 数据')
  assert.equal(dataSourceLabel('todo'), '待实现')
})
