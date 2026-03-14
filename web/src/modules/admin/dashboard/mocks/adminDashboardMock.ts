import type {
  AdminDashboardFilter,
  AdminDashboardPayload,
  AdminDashboardPreset,
  AdminModelUsageMetrics,
  AdminSubscriptionMetrics,
  BreakdownItem,
  DateValuePoint,
} from '../types'

const BASE_NOW = new Date('2026-03-14T10:00:00+08:00')

const MODEL_PRICING: Record<string, { input: number; output: number }> = {
  'gpt-4o': { input: 0.00002, output: 0.00008 },
  'qwen-plus': { input: 0.000004, output: 0.000012 },
  'qwen-vl-plus': { input: 0.000006, output: 0.000018 },
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function cloneDate(date: Date) {
  return new Date(date.getTime())
}

function startOfDay(date: Date) {
  const next = cloneDate(date)
  next.setHours(0, 0, 0, 0)
  return next
}

function addDays(date: Date, days: number) {
  const next = cloneDate(date)
  next.setDate(next.getDate() + days)
  return next
}

function startOfWeek(date: Date) {
  const next = startOfDay(date)
  const day = next.getDay() || 7
  next.setDate(next.getDate() - day + 1)
  return next
}

function startOfMonth(date: Date) {
  const next = startOfDay(date)
  next.setDate(1)
  return next
}

function daysBetween(start: Date, end: Date) {
  const diff = startOfDay(end).getTime() - startOfDay(start).getTime()
  return Math.max(0, Math.floor(diff / 86400000)) + 1
}

function parseDate(value: string | undefined, fallback: Date) {
  if (!value) return cloneDate(fallback)
  return startOfDay(new Date(`${value}T00:00:00+08:00`))
}

function normalizeFilter(filter: AdminDashboardFilter) {
  const preset: AdminDashboardPreset = filter.preset ?? 'thisMonth'
  const now = cloneDate(BASE_NOW)

  if (preset === 'today') {
    const start = startOfDay(now)
    return { preset, start, end: start }
  }

  if (preset === 'yesterday') {
    const start = addDays(startOfDay(now), -1)
    return { preset, start, end: start }
  }

  if (preset === 'thisWeek') {
    return { preset, start: startOfWeek(now), end: startOfDay(now) }
  }

  if (preset === 'custom') {
    const start = parseDate(filter.startDate, addDays(startOfDay(now), -6))
    const end = parseDate(filter.endDate, startOfDay(now))
    return start.getTime() <= end.getTime()
      ? { preset, start, end }
      : { preset, start: end, end: start }
  }

  return { preset: 'thisMonth' as const, start: startOfMonth(now), end: startOfDay(now) }
}

function hashSeed(input: string) {
  let hash = 0
  for (let index = 0; index < input.length; index += 1) {
    hash = (hash * 31 + input.charCodeAt(index)) % 2147483647
  }
  return hash
}

function seeded(seed: number, offset = 0) {
  const value = Math.sin(seed + offset * 97.13) * 10000
  return value - Math.floor(value)
}

function buildTrend(start: Date, end: Date, seedKey: string, min: number, max: number): DateValuePoint[] {
  const length = daysBetween(start, end)
  return Array.from({ length }, (_, index) => {
    const current = addDays(start, index)
    const seed = hashSeed(`${seedKey}:${formatDate(current)}`)
    const ratio = seeded(seed, index)
    const wave = Math.sin((index / Math.max(length, 1)) * Math.PI)
    const value = Math.round(min + (max - min) * (0.45 * ratio + 0.55 * Math.max(wave, 0.15)))
    return {
      date: formatDate(current),
      value,
    }
  })
}

function buildBreakdown(items: Array<{ key: string; label: string; value: number }>): BreakdownItem[] {
  const total = items.reduce((sum, item) => sum + item.value, 0) || 1
  return items.map((item) => ({
    ...item,
    ratio: Number((item.value / total).toFixed(4)),
  }))
}

function sumTrend(points: DateValuePoint[]) {
  return points.reduce((sum, item) => sum + item.value, 0)
}

function buildSubscription(start: Date, end: Date): AdminSubscriptionMetrics {
  const trend = buildTrend(start, end, 'subscription', 6, 36)
  const monthlyStart = startOfMonth(end)
  const monthlyTrend = buildTrend(monthlyStart, end, 'subscription-month', 6, 36)
  const yesterday = buildTrend(addDays(end, -1), addDays(end, -1), 'subscription-yesterday', 5, 18)[0]?.value ?? 0
  const totalSubscribedUsers = 4280 + hashSeed(`${formatDate(start)}:${formatDate(end)}:sub-total`) % 720
  const activeSubscribers = Math.round(totalSubscribedUsers * 0.62)

  return {
    dailyNew: trend[trend.length - 1]?.value ?? 0,
    yesterdayNew: yesterday,
    monthlyNew: sumTrend(monthlyTrend),
    totalSubscribedUsers,
    activeSubscribers,
    trend,
    planBreakdown: buildBreakdown([
      { key: 'monthly', label: '月卡', value: 520 + hashSeed('plan-monthly') % 120 },
      { key: 'quarterly', label: '季卡', value: 260 + hashSeed('plan-quarterly') % 80 },
      { key: 'yearly', label: '年卡', value: 180 + hashSeed('plan-yearly') % 60 },
      { key: 'other', label: '其他', value: 70 + hashSeed('plan-other') % 20 },
    ]),
  }
}

function buildModelUsage(start: Date, end: Date): AdminModelUsageMetrics {
  const rangeDays = daysBetween(start, end)
  const models = [
    { modelKey: 'gpt-4o', modelName: 'gpt-4o', baseRequests: 120 },
    { modelKey: 'qwen-plus', modelName: 'qwen-plus', baseRequests: 240 },
    { modelKey: 'qwen-vl-plus', modelName: 'qwen-vl-plus', baseRequests: 64 },
  ]

  const table = models.map((model, index) => {
    const requestCount = model.baseRequests + rangeDays * (index + 5) + (hashSeed(`${model.modelKey}:req:${rangeDays}`) % 40)
    const inputTokens = requestCount * (420 + index * 80)
    const outputTokens = requestCount * (210 + index * 45)
    const totalTokens = inputTokens + outputTokens
    const price = MODEL_PRICING[model.modelKey]
    const estimatedCost = Number(((inputTokens * price.input) + (outputTokens * price.output)).toFixed(2))
    return {
      modelKey: model.modelKey,
      modelName: model.modelName,
      requestCount,
      inputTokens,
      outputTokens,
      totalTokens,
      ratio: 0,
      estimatedCost,
    }
  })

  const totalTokens = table.reduce((sum, row) => sum + row.totalTokens, 0) || 1
  const normalized = table.map((row) => ({
    ...row,
    ratio: Number((row.totalTokens / totalTokens).toFixed(4)),
  }))

  return {
    totalInputTokens: normalized.reduce((sum, row) => sum + row.inputTokens, 0),
    totalOutputTokens: normalized.reduce((sum, row) => sum + row.outputTokens, 0),
    totalTokens: normalized.reduce((sum, row) => sum + row.totalTokens, 0),
    estimatedCost: Number(normalized.reduce((sum, row) => sum + row.estimatedCost, 0).toFixed(2)),
    chart: normalized.map((row) => ({
      modelKey: row.modelKey,
      label: row.modelName,
      inputTokens: row.inputTokens,
      outputTokens: row.outputTokens,
      totalTokens: row.totalTokens,
    })),
    table: normalized,
  }
}

export function getDashboardMock(filter: AdminDashboardFilter): AdminDashboardPayload {
  const normalized = normalizeFilter(filter)
  const usersTrend = buildTrend(normalized.start, normalized.end, 'users', 10, 48)
  const writingTrend = buildTrend(normalized.start, normalized.end, 'writing', 16, 70)
  const subscription = buildSubscription(normalized.start, normalized.end)
  const modelUsage = buildModelUsage(normalized.start, normalized.end)

  return {
    meta: {
      dataSource: 'mock',
      generatedAt: BASE_NOW.toISOString(),
      filters: {
        preset: normalized.preset,
        startDate: formatDate(normalized.start),
        endDate: formatDate(normalized.end),
        timezone: filter.timezone ?? 'Asia/Shanghai',
      },
    },
    summary: {
      totalUsers: 18320 + hashSeed('summary-total-users') % 900,
      dailyNewUsers: usersTrend[usersTrend.length - 1]?.value ?? 0,
      activeUsers: 2160 + hashSeed('summary-active-users') % 260,
      writingSubmissions: sumTrend(writingTrend),
      averageScore: Number((77 + seeded(hashSeed('summary-score')) * 9).toFixed(1)),
      pendingTasks: 18 + hashSeed('summary-pending') % 16,
    },
    subscription,
    modelUsage,
    users: {
      trend: usersTrend,
      stageBreakdown: buildBreakdown([
        { key: 'primary', label: '小学', value: 420 },
        { key: 'junior', label: '初中', value: 860 },
        { key: 'senior', label: '高中', value: 1180 },
        { key: 'college', label: '大学', value: 560 },
      ]),
    },
    writing: {
      submissionTrend: writingTrend,
      scoreBreakdown: buildBreakdown([
        { key: '90+', label: '90分以上', value: 180 },
        { key: '80-89', label: '80-89分', value: 420 },
        { key: '70-79', label: '70-79分', value: 360 },
        { key: 'under-70', label: '70分以下', value: 160 },
      ]),
    },
    content: {
      totalPrompts: 860,
      activePrompts: 712,
      totalRubrics: 84,
      activeRubrics: 21,
    },
    audit: {
      dailyActions: buildTrend(normalized.start, normalized.end, 'audit', 12, 56),
      actionBreakdown: buildBreakdown([
        { key: 'update', label: '更新', value: 320 },
        { key: 'create', label: '创建', value: 180 },
        { key: 'publish', label: '发布', value: 74 },
        { key: 'disable', label: '停用', value: 48 },
      ]),
    },
  }
}

