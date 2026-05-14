import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const pageSource = readFileSync(new URL('../src/pages/admin/AdminSubscriptionsPage.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../src/api/admin.ts', import.meta.url), 'utf8')
const layoutSource = readFileSync(new URL('../src/layouts/AdminLayout.vue', import.meta.url), 'utf8')

for (const expectedApi of [
  'listSubscriptions',
  '/admin/subscriptions',
  'listSubscriptionQuotaRules',
  '/admin/subscription/quota-rules',
  'updateSubscriptionQuotaRule',
  'getSubscriptionOverview',
  '/admin/subscriptions/overview',
  'listSubscriptionDailyStats',
  '/admin/subscriptions/daily-stats',
]) {
  assert.ok(apiSource.includes(expectedApi), `admin API should expose ${expectedApi}`)
}

for (const expectedText of [
  '今日新增用户',
  '今日新增订阅',
  '普通用户',
  '订阅用户',
  '订阅等级分布',
  '每日用户数据',
  '已用额度',
  '剩余额度',
]) {
  assert.ok(pageSource.includes(expectedText), `subscriptions page should render ${expectedText}`)
}

assert.ok(!pageSource.includes('disabled placeholder="搜索用户'), 'subscriptions page should not keep disabled skeleton filters')
assert.ok(!pageSource.includes('接口待接入'), 'subscriptions page should not show pending API badge')
assert.ok(pageSource.includes("segmentTabs"), 'subscriptions page should use user segment tabs')
assert.ok(pageSource.includes("subscriptionStatus: tab.status"), 'tab changes should pass subscription segment filter')
assert.ok(pageSource.includes('planDistribution'), 'subscriptions page should consume plan distribution metrics')
assert.ok(pageSource.includes('PieChart'), 'subscriptions page should render a plan distribution pie chart')
assert.ok(pageSource.includes('useSubscriptionDistributionChart'), 'subscriptions page should isolate chart setup')
assert.ok(pageSource.includes('adminApi.listSubscriptions'), 'subscriptions page should load real subscription list API')
assert.ok(pageSource.includes('adminApi.getSubscriptionOverview'), 'subscriptions page should load overview metrics')
assert.ok(pageSource.includes('adminApi.listSubscriptionDailyStats'), 'subscriptions page should load daily user stats')
assert.ok(pageSource.includes('adminApi.listSubscriptionQuotaRules'), 'subscriptions page should load quota rule API')
assert.ok(pageSource.includes('adminApi.updateSubscriptionQuotaRule'), 'subscriptions page should save quota rule edits')
assert.ok(layoutSource.includes('admin.subscription.read'), 'subscriptions navigation should use read permission')
