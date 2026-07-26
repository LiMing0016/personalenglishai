import assert from 'node:assert/strict'
import test from 'node:test'

import type { AiUsageActivity, AiUsageDayBucket } from '@/api/user'
import {
  buildMonthlyUsage,
  buildProductBreakdown,
  buildUsageCalendar,
  buildWeeklyUsage,
} from './usageActivity.ts'

test('每日活动构造固定 53 × 7 日历并按非零日分位数分级', () => {
  const model = buildUsageCalendar(activity([
    day('2026-07-24', 10, { writing: 10 }),
    day('2026-07-25', 20, { assistant: 20 }),
    day('2026-07-26', 10_000, { vocabulary: 10_000 }),
  ]), '2026-07-26')

  assert.equal(model.days.length, 53 * 7)
  assert.equal(model.days.filter((item) => item.inRange && item.total > 0).length, 3)
  assert.ok(model.days.find((item) => item.date === '2026-07-24')!.level > 0)
  assert.equal(model.days.find((item) => item.date === '2026-07-26')!.isToday, true)
  assert.equal(model.days.find((item) => item.date === '2026-07-23')!.level, 0)
})

test('单个极值不会把其他所有非零日期压成空白级别', () => {
  const model = buildUsageCalendar(activity([
    day('2026-07-22', 1),
    day('2026-07-23', 2),
    day('2026-07-24', 3),
    day('2026-07-25', 4),
    day('2026-07-26', 1_000_000),
  ]), '2026-07-26')

  const levels = model.days
    .filter((item) => item.inRange && item.total > 0)
    .map((item) => item.level)
  assert.ok(levels.every((level) => level >= 1))
  assert.ok(new Set(levels).size >= 3)
})

test('最近 52 周和 12 个月聚合与范围内日总量守恒', () => {
  const source = activity([
    day('2026-06-30', 30, { writing: 20, translation: 10 }),
    day('2026-07-01', 70, { assistant: 70 }),
  ])

  assert.equal(sum(buildWeeklyUsage(source).map((item) => item.total)), 100)
  assert.equal(sum(buildMonthlyUsage(source).map((item) => item.total)), 100)
})

test('产品构成稳定排序并把未知或缺失分类收进其他', () => {
  const source = activity([
    day('2026-07-26', 100, {
      assistant: 40,
      writing: 30,
      translation: 20,
      vocabulary: 5,
      other: 5,
    }),
  ])

  const breakdown = buildProductBreakdown(source)

  assert.deepEqual(breakdown.map((item) => item.key), [
    'assistant',
    'writing',
    'translation',
    'vocabulary',
    'other',
  ])
  assert.equal(breakdown.find((item) => item.key === 'other')!.percent, 5)
})

function activity(buckets: AiUsageDayBucket[]): AiUsageActivity {
  return {
    metric: 'ai_tokens',
    unit: 'token',
    timezone: 'Asia/Shanghai',
    from: '2025-07-27',
    to: '2026-07-26',
    total: sum(buckets.map((item) => item.total)),
    buckets,
  }
}

function day(
  date: string,
  total: number,
  byProduct: Partial<AiUsageDayBucket['byProduct']> = {},
): AiUsageDayBucket {
  return {
    date,
    total,
    byProduct: {
      assistant: 0,
      writing: 0,
      translation: 0,
      vocabulary: 0,
      other: 0,
      ...byProduct,
    },
  }
}

function sum(values: number[]) {
  return values.reduce((total, value) => total + value, 0)
}
