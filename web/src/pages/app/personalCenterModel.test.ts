import assert from 'node:assert/strict'
import test from 'node:test'

import {
  PERSONAL_CENTER_TABS,
  parsePersonalCenterSection,
} from './personalCenterModel.ts'

test('个人中心只暴露六个跨产品页签', () => {
  assert.deepEqual(
    PERSONAL_CENTER_TABS.map(({ key, label }) => ({ key, label })),
    [
      { key: 'overview', label: '学习概览' },
      { key: 'records', label: '学习记录' },
      { key: 'assets', label: '学习资产' },
      { key: 'profile', label: '能力画像' },
      { key: 'subscription', label: '订阅与用量' },
      { key: 'security', label: '账号安全' },
    ],
  )
})

test('有效查询参数恢复对应页签', () => {
  assert.equal(parsePersonalCenterSection('subscription'), 'subscription')
  assert.equal(parsePersonalCenterSection(['security']), 'security')
})

test('旧邀请入口和未知查询参数回落到学习概览', () => {
  assert.equal(parsePersonalCenterSection('referral'), 'overview')
  assert.equal(parsePersonalCenterSection('unknown'), 'overview')
  assert.equal(parsePersonalCenterSection(undefined), 'overview')
})

test('旧个人中心书签迁移到新的页签命名', () => {
  assert.equal(parsePersonalCenterSection('essays'), 'records')
  assert.equal(parsePersonalCenterSection('radar'), 'profile')
  assert.equal(parsePersonalCenterSection('settings'), 'security')
})
