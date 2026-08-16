import assert from 'node:assert/strict'
import test from 'node:test'

import type { AiUsageActivity, AiUsageDayBucket } from '@/api/user'
import {
  buildMonthlyUsage,
  buildProductBreakdown,
  buildUsageQueryRange,
  buildUsageCalendar,
  buildUsageHeadline,
  buildWeeklySquareColumns,
  buildWeeklyUsage,
} from './usageActivity.ts'

test('个人中心查询含今天在内的最近 365 个自然日', () => {
  assert.deepEqual(buildUsageQueryRange('2026-07-27'), {
    from: '2025-07-28',
    to: '2026-07-27',
  })
})

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

test('周和自然月聚合包含首尾边界并与日总量守恒', () => {
  const source = activity([
    day('2025-07-27', 20, { vocabulary: 20 }),
    day('2026-06-30', 30, { writing: 20, translation: 10 }),
    day('2026-07-26', 50, { assistant: 50 }),
  ])

  assert.equal(buildWeeklyUsage(source).length, 53)
  assert.equal(buildMonthlyUsage(source).length, 13)
  assert.equal(sum(buildWeeklyUsage(source).map((item) => item.total)), 100)
  assert.equal(sum(buildMonthlyUsage(source).map((item) => item.total)), 100)
})

test('每周总量离散为从 0 到 7 的方块高度', () => {
  const periods = [
    weeklyPeriod('week-empty', 0),
    weeklyPeriod('week-small', 10),
    weeklyPeriod('week-middle', 50),
    weeklyPeriod('week-peak', 100),
  ]

  assert.deepEqual(
    buildWeeklySquareColumns(periods).map((item) => item.filledCells),
    [0, 1, 4, 7],
  )
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

test('每日摘要只展示今天的 Token', () => {
  const source = activity([
    day('2026-07-26', 10),
    day('2026-07-27', 20),
  ])

  assert.deepEqual(buildUsageHeadline('daily', source, '2026-07-27'), {
    total: 20,
    label: '今日 Token',
  })
})

test('每周摘要展示本周一到今天的 Token', () => {
  const source = activity([
    day('2026-07-19', 7),
    day('2026-07-20', 10),
    day('2026-07-26', 40),
  ])

  assert.deepEqual(buildUsageHeadline('weekly', source, '2026-07-26'), {
    total: 50,
    label: '本周 Token',
  })
})

test('累计摘要展示活动查询区间内的总 Token', () => {
  const source = activity([
    day('2026-07-20', 10),
    day('2026-07-27', 20),
  ])

  assert.deepEqual(buildUsageHeadline('cumulative', source, '2026-07-27'), {
    total: 30,
    label: '累计 Token',
  })
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

function weeklyPeriod(key: string, total: number) {
  return {
    key,
    label: '7/20–7/26',
    start: '2026-07-20',
    end: '2026-07-26',
    total,
    byProduct: {
      assistant: total,
      writing: 0,
      translation: 0,
      vocabulary: 0,
      other: 0,
    },
  }
}
