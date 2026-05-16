import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const pageSource = readFileSync(new URL('../src/pages/admin/AdminUsersPage.vue', import.meta.url), 'utf8')
const detailSource = readFileSync(new URL('../src/pages/admin/AdminUserDetailPage.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../src/api/admin.ts', import.meta.url), 'utf8')

for (const expectedField of [
  'planCode',
  'planName',
  'subscriptionStatus',
  'quotaPeriod',
  'tokenLimit',
  'tokenUsed',
  'tokenRemaining',
  'overLimit',
  'currentPeriodStart',
  'currentPeriodEnd',
  'AdminUserOverview',
  'getUserOverview',
  '/admin/users/${userId}/overview',
]) {
  assert.ok(apiSource.includes(expectedField), `Admin user API types should include ${expectedField}`)
}

for (const expectedText of [
  '全部学段',
  '全部账号角色',
  '全部管理员角色',
  '全部套餐',
  '全部订阅状态',
  '已超额',
  '注册开始日期',
  '活跃开始日期',
  '请输入用户ID',
  '用户摘要抽屉',
  '完整详情',
  '查看作文',
  '查看订阅',
  '查看 AI',
  '查看审计',
  '订阅',
  '额度',
  '注册时间',
  '最近活跃',
]) {
  assert.ok(pageSource.includes(expectedText), `Admin users page should expose ${expectedText}`)
}

assert.ok(pageSource.includes('buildParams'), 'Admin users page should normalize filter params before querying')
assert.ok(pageSource.includes('createdFrom'), 'Admin users page should pass registration date filters')
assert.ok(pageSource.includes('lastActiveFrom'), 'Admin users page should pass active date filters')
assert.ok(pageSource.includes('formatQuota'), 'Admin users page should format quota numbers')

for (const expectedText of [
  '订阅与额度',
  '当前套餐',
  '订阅状态',
  '额度周期',
  '已用 / 上限',
  '剩余额度',
  '是否超额',
]) {
  assert.ok(detailSource.includes(expectedText), `Admin user detail page should expose ${expectedText}`)
}
