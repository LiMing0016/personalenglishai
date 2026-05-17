import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { adminNavGroups } from '../src/layouts/adminNav.ts'

const routerSource = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const adminLayoutSource = readFileSync(new URL('../src/layouts/AdminLayout.vue', import.meta.url), 'utf8')
const taskSource = readFileSync(new URL('../../tasks/5.14/admin-backoffice-trae-tasks.md', import.meta.url), 'utf8')

for (const expectedGroup of [
  '总览',
  '用户运营',
  '订阅与权益',
  '作文与评测',
  '内容资产',
  'AI 与 Agent',
  '数据分析',
  '审计与系统',
]) {
  assert.ok(
    adminNavGroups.some((group) => group.label === expectedGroup),
    `admin navigation should include grouped navigation: ${expectedGroup}`,
  )
}

for (const expectedRoute of [
  "path: 'docs'",
  "path: 'subscriptions'",
  "path: 'subscription/redeem-codes'",
  "path: 'subscription/quota-ledger'",
  "path: 'prompt-assets'",
  "path: 'materials'",
  "path: 'scoring-config'",
  "path: 'agent-debug/runs'",
  "path: 'agent-debug/runs/:id'",
  "path: 'data-catalog'",
  "path: 'data-catalog/:tableName'",
]) {
  assert.ok(routerSource.includes(expectedRoute), `admin router should include ${expectedRoute}`)
}

assert.ok(adminLayoutSource.includes('/admin/docs'), 'AdminLayout should link the documentation home inside /admin')
assert.ok(adminLayoutSource.includes('/admin/agent-debug/runs'), 'AdminLayout should link Agent Debug inside /admin')
assert.ok(!adminLayoutSource.includes('/ops/agent/runs'), 'AdminLayout should not send admins to the separate /ops Agent shell')

for (const taskText of [
  '/admin/subscriptions',
  '/admin/subscription/redeem-codes',
  '/admin/subscription/quota-ledger',
  '/admin/prompt-assets',
  '/admin/materials',
  '/admin/scoring-config',
  '/admin/agent-debug/runs',
]) {
  assert.ok(taskSource.includes(taskText), `Trae task should cover ${taskText}`)
}
