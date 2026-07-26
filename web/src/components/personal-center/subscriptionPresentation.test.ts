import assert from 'node:assert/strict'
import test from 'node:test'

import type { SubscriptionStatus } from '@/api/user'
import { buildSubscriptionPresentation } from './subscriptionPresentation.ts'

test('Free 档使用注册时间作为生效时间并长期有效', () => {
  assert.deepEqual(
    buildSubscriptionPresentation(
      subscriptionStatus({
        planCode: 'free',
        planName: 'Free',
        currentPeriodStart: null,
        currentPeriodEnd: null,
      }),
      '2026-05-15T09:30:00+08:00',
    ),
    {
      planName: 'Free',
      periodText: '2026-05-15 — 长期有效',
      ariaLabel: '当前档位 Free，有效期 2026-05-15 — 长期有效',
    },
  )
})

test('正式档位优先展示后端返回的订阅周期', () => {
  assert.deepEqual(
    buildSubscriptionPresentation(subscriptionStatus({
      planCode: 'pro',
      planName: 'Pro',
      currentPeriodStart: '2026-07-20T00:00:00+08:00',
      currentPeriodEnd: '2026-08-20T00:00:00+08:00',
    })),
    {
      planName: 'Pro',
      periodText: '2026-07-20 — 2026-08-20',
      ariaLabel: '当前档位 Pro，有效期 2026-07-20 — 2026-08-20',
    },
  )
})

test('订阅开始时间缺失时明确提示待同步', () => {
  assert.deepEqual(
    buildSubscriptionPresentation(subscriptionStatus({
      planCode: 'basic',
      planName: 'Basic',
      currentPeriodStart: null,
      currentPeriodEnd: '2026-08-20',
    })),
    {
      planName: 'Basic',
      periodText: '生效时间待同步 — 2026-08-20',
      ariaLabel: '当前档位 Basic，有效期 生效时间待同步 — 2026-08-20',
    },
  )
})

function subscriptionStatus(
  overrides: Partial<SubscriptionStatus> = {},
): SubscriptionStatus {
  return {
    planCode: 'free',
    planName: 'Free',
    currentPeriodStart: null,
    currentPeriodEnd: null,
    quotaPeriod: 'daily',
    usageDate: '2026-07-27',
    usageMonth: '2026-07',
    dailyTokenLimit: 10_000,
    monthlyTokenLimit: 10_000,
    tokenLimit: 10_000,
    tokenUsed: 0,
    tokenRemaining: 10_000,
    overLimit: false,
    ...overrides,
  }
}
