import type {
  AiUsageActivity,
  AiUsageDayBucket,
  AiUsageProductKey,
} from '@/api/user'

const PRODUCT_KEYS: AiUsageProductKey[] = [
  'assistant',
  'writing',
  'translation',
  'vocabulary',
  'other',
]

const PRODUCT_LABELS: Record<AiUsageProductKey, string> = {
  assistant: '学习助手',
  writing: '写作',
  translation: '翻译',
  vocabulary: '词汇',
  other: '其他',
}

export interface UsageCalendarDay {
  date: string
  total: number
  byProduct: AiUsageDayBucket['byProduct']
  level: 0 | 1 | 2 | 3 | 4
  inRange: boolean
  isToday: boolean
}

export interface UsageCalendarMonthLabel {
  label: string
  column: number
}

export interface UsageCalendar {
  days: UsageCalendarDay[]
  monthLabels: UsageCalendarMonthLabel[]
}

export interface UsagePeriod {
  key: string
  label: string
  start: string
  end: string
  total: number
  byProduct: AiUsageDayBucket['byProduct']
}

export interface UsageProductBreakdown {
  key: AiUsageProductKey
  label: string
  total: number
  percent: number
}

export function buildUsageCalendar(
  activity: AiUsageActivity,
  today: string,
): UsageCalendar {
  const byDate = new Map(activity.buckets.map((bucket) => [bucket.date, bucket]))
  const gridEnd = addDays(activity.to, 6 - mondayIndex(activity.to))
  const gridStart = addDays(gridEnd, -(53 * 7 - 1))
  const nonZeroTotals = activity.buckets
    .map((bucket) => nonNegative(bucket.total))
    .filter((total) => total > 0)
    .sort((left, right) => left - right)
  const thresholds = [
    quantileThreshold(nonZeroTotals, 0.25),
    quantileThreshold(nonZeroTotals, 0.5),
    quantileThreshold(nonZeroTotals, 0.75),
  ]
  const days: UsageCalendarDay[] = []

  for (let offset = 0; offset < 53 * 7; offset += 1) {
    const date = addDays(gridStart, offset)
    const inRange = date >= activity.from && date <= activity.to
    const bucket = inRange ? byDate.get(date) : undefined
    const total = nonNegative(bucket?.total)
    days.push({
      date,
      total,
      byProduct: normalizeProducts(bucket?.byProduct),
      level: usageLevel(total, thresholds),
      inRange,
      isToday: date === today,
    })
  }

  const monthLabels: UsageCalendarMonthLabel[] = []
  let previousMonth = ''
  for (let column = 0; column < 53; column += 1) {
    const weekStart = addDays(gridStart, column * 7)
    const visibleDate = weekStart < activity.from ? activity.from : weekStart
    const month = visibleDate.slice(0, 7)
    if (visibleDate <= activity.to && month !== previousMonth) {
      monthLabels.push({
        label: `${Number(month.slice(5, 7))}月`,
        column,
      })
      previousMonth = month
    }
  }
  return { days, monthLabels }
}

export function buildWeeklyUsage(activity: AiUsageActivity): UsagePeriod[] {
  const end = addDays(activity.to, 6 - mondayIndex(activity.to))
  const start = addDays(end, -(52 * 7 - 1))
  return Array.from({ length: 52 }, (_, index) => {
    const weekStart = addDays(start, index * 7)
    const weekEnd = addDays(weekStart, 6)
    return aggregatePeriod(
      `week-${weekStart}`,
      `${shortDate(weekStart)}–${shortDate(weekEnd)}`,
      weekStart,
      weekEnd,
      activity.buckets,
    )
  })
}

export function buildMonthlyUsage(activity: AiUsageActivity): UsagePeriod[] {
  const to = parseDate(activity.to)
  const endYear = to.getUTCFullYear()
  const endMonth = to.getUTCMonth()
  return Array.from({ length: 12 }, (_, index) => {
    const monthOffset = index - 11
    const startDate = new Date(Date.UTC(endYear, endMonth + monthOffset, 1))
    const endDate = new Date(Date.UTC(
      startDate.getUTCFullYear(),
      startDate.getUTCMonth() + 1,
      0,
    ))
    const start = formatDate(startDate)
    const end = formatDate(endDate)
    return aggregatePeriod(
      `month-${start.slice(0, 7)}`,
      `${startDate.getUTCFullYear()}年${startDate.getUTCMonth() + 1}月`,
      start,
      end,
      activity.buckets,
    )
  })
}

export function buildProductBreakdown(
  activity: AiUsageActivity,
): UsageProductBreakdown[] {
  const totals = normalizeProducts()
  for (const bucket of activity.buckets) {
    for (const key of PRODUCT_KEYS) {
      totals[key] += nonNegative(bucket.byProduct?.[key])
    }
  }
  const grandTotal = PRODUCT_KEYS.reduce((sum, key) => sum + totals[key], 0)
  return PRODUCT_KEYS
    .filter((key) => totals[key] > 0)
    .map((key) => ({
      key,
      label: PRODUCT_LABELS[key],
      total: totals[key],
      percent: grandTotal > 0 ? Math.round((totals[key] / grandTotal) * 100) : 0,
    }))
}

function aggregatePeriod(
  key: string,
  label: string,
  start: string,
  end: string,
  buckets: AiUsageDayBucket[],
): UsagePeriod {
  const byProduct = normalizeProducts()
  let total = 0
  for (const bucket of buckets) {
    if (bucket.date < start || bucket.date > end) continue
    total += nonNegative(bucket.total)
    for (const product of PRODUCT_KEYS) {
      byProduct[product] += nonNegative(bucket.byProduct?.[product])
    }
  }
  return { key, label, start, end, total, byProduct }
}

function normalizeProducts(
  source?: Partial<AiUsageDayBucket['byProduct']>,
): AiUsageDayBucket['byProduct'] {
  return {
    assistant: nonNegative(source?.assistant),
    writing: nonNegative(source?.writing),
    translation: nonNegative(source?.translation),
    vocabulary: nonNegative(source?.vocabulary),
    other: nonNegative(source?.other),
  }
}

function quantileThreshold(sorted: number[], quantile: number): number {
  if (sorted.length === 0) return 0
  const index = Math.max(0, Math.ceil(sorted.length * quantile) - 1)
  return sorted[index] ?? sorted[sorted.length - 1] ?? 0
}

function usageLevel(
  total: number,
  thresholds: number[],
): UsageCalendarDay['level'] {
  if (total <= 0) return 0
  if (total <= (thresholds[0] ?? 0)) return 1
  if (total <= (thresholds[1] ?? 0)) return 2
  if (total <= (thresholds[2] ?? 0)) return 3
  return 4
}

function mondayIndex(value: string): number {
  return (parseDate(value).getUTCDay() + 6) % 7
}

function addDays(value: string, days: number): string {
  const date = parseDate(value)
  date.setUTCDate(date.getUTCDate() + days)
  return formatDate(date)
}

function parseDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1))
}

function formatDate(date: Date): string {
  return [
    date.getUTCFullYear(),
    String(date.getUTCMonth() + 1).padStart(2, '0'),
    String(date.getUTCDate()).padStart(2, '0'),
  ].join('-')
}

function shortDate(value: string): string {
  return `${Number(value.slice(5, 7))}/${Number(value.slice(8, 10))}`
}

function nonNegative(value: number | null | undefined): number {
  return Number.isFinite(value) ? Math.max(0, Number(value)) : 0
}
