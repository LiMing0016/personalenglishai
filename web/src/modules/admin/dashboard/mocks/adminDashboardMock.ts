import type { AdminDashboardFilter, AdminDashboardPayload, BreakdownItem, DateValuePoint } from '../types'

const PLAN_LABELS = [
  { key: 'monthly', label: '月卡' },
  { key: 'quarterly', label: '季卡' },
  { key: 'yearly', label: '年卡' },
  { key: 'other', label: '其他' },
]

const MODEL_PRICING: Record<string, { input: number; output: number }> = {
  'gpt-4o': { input: 0.000005, output: 0.000015 },
  'qwen-plus': { input: 0.0000022, output: 0.0000066 },
  'qwen-vl-plus': { input: 0.000004, output: 0.000012 },
  'gpt-4.1-mini': { input: 0.0000008, output: 0.0000032 },
}

const STAGE_LABELS = [
  { key: 'highschool', label: '高中' },
  { key: 'cet4', label: '四级' },
  { key: 'cet6', label: '六级' },
  { key: 'postgrad', label: '考研' },
]

function hashSeed(input: string) {
  let hash = 2166136261
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 16777619)
  }
  return Math.abs(hash >>> 0)
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function toDateKey(date: Date) {
  return date.toISOString().slice(0, 10)
}

function parseDate(input?: string) {
  if (!input) return null
  const date = new Date(`${input}T00:00:00+08:00`)
  return Number.isNaN(date.getTime()) ? null : date
}

function normalizeFilter(filter: AdminDashboardFilter) {
  const today = new Date('2026-03-14T00:00:00+08:00')
  let start = new Date(today)
  let end = new Date(today)

  if (filter.preset === 'yesterday') {
    start.setDate(start.getDate() - 1)
    end = new Date(start)
  } else if (filter.preset === 'thisWeek') {
    const day = start.getDay() === 0 ? 7 : start.getDay()
    start.setDate(start.getDate() - day + 1)
  } else if (filter.preset === 'thisMonth') {
    start = new Date(today.getFullYear(), today.getMonth(), 1)
  } else if (filter.preset === 'custom') {
    start = parseDate(filter.startDate) ?? new Date(today.getFullYear(), today.getMonth(), 1)
    end = parseDate(filter.endDate) ?? new Date(today)
  }

  const invalid = start.getTime() > end.getTime()
  const days = invalid ? 0 : Math.floor((end.getTime() - start.getTime()) / 86400000) + 1
  return { start, end, days, invalid, today }
}

function buildDateRange(start: Date, days: number) {
  return Array.from({ length: days }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    return date
  })
}

function buildTrend(seedBase: number, dates: Date[], floor: number, spread: number, slope: number) {
  return dates.map((date, index) => {
    const seed = hashSeed(`${seedBase}:${toDateKey(date)}:${index}`)
    const seasonal = Math.round(Math.sin((index + 1) / 2.6) * spread * 0.22)
    const drift = Math.round(index * slope)
    const noise = seed % spread
    return {
      date: toDateKey(date),
      value: Math.max(0, floor + drift + seasonal + noise),
    }
  })
}

function toBreakdown(items: Array<{ key: string; label: string; value: number }>): BreakdownItem[] {
  const total = items.reduce((sum, item) => sum + item.value, 0)
  return items.map((item) => ({
    ...item,
    ratio: total > 0 ? Number((item.value / total).toFixed(4)) : 0,
  }))
}

function roundCurrency(value: number) {
  return Number(value.toFixed(2))
}

function buildSubscription(filter: AdminDashboardFilter) {
  const normalized = normalizeFilter(filter)
  if (normalized.invalid) {
    return {
      dailyNew: 0,
      yesterdayNew: 0,
      monthlyNew: 0,
      totalSubscribedUsers: 0,
      activeSubscribers: 0,
      trend: [] as DateValuePoint[],
      planBreakdown: [] as BreakdownItem[],
    }
  }

  const monthlyStart = new Date(normalized.today.getFullYear(), normalized.today.getMonth(), 1)
  const monthlyDates = buildDateRange(monthlyStart, normalized.today.getDate())
  const monthlyTrend = buildTrend(hashSeed(`subscription:month:${JSON.stringify(filter)}`), monthlyDates, 9, 7, 0.4)
  const filteredDates = buildDateRange(normalized.start, normalized.days)
  const trend = buildTrend(hashSeed(`subscription:${JSON.stringify(filter)}`), filteredDates, 4, 6, normalized.days > 10 ? 0.35 : 0)
  const dailyNew = trend[trend.length - 1]?.value ?? 0
  const yesterdayNew = monthlyTrend[monthlyTrend.length - 2]?.value ?? Math.max(0, dailyNew - 2)
  const monthlyNew = monthlyTrend.reduce((sum, item) => sum + item.value, 0)
  const totalSubscribedUsers = 4820 + hashSeed(`subscribers:${JSON.stringify(filter)}`) % 420
  const activeSubscribers = clamp(Math.round(totalSubscribedUsers * 0.62), 1, totalSubscribedUsers)
  const weights = PLAN_LABELS.map((plan, index) => ({
    ...plan,
    value: 20 + (hashSeed(`${plan.key}:${JSON.stringify(filter)}`) % (40 - index * 4)),
  }))

  return {
    dailyNew,
    yesterdayNew,
    monthlyNew,
    totalSubscribedUsers,
    activeSubscribers,
    trend,
    planBreakdown: toBreakdown(weights),
  }
}

function buildModelUsage(filter: AdminDashboardFilter) {
  const normalized = normalizeFilter(filter)
  const factor = normalized.invalid ? 0 : Math.max(1, normalized.days)
  const rawModels = [
    { key: 'gpt-4o', label: 'GPT-4o', requests: 210 + factor * 4 },
    { key: 'qwen-plus', label: 'Qwen Plus', requests: 320 + factor * 6 },
    { key: 'qwen-vl-plus', label: 'Qwen VL Plus', requests: 64 + factor * 2 },
    { key: 'gpt-4.1-mini', label: 'GPT-4.1 mini', requests: 430 + factor * 9 },
  ].map((item, index) => {
    const seed = hashSeed(`${item.key}:${JSON.stringify(filter)}`)
    const inputTokens = item.requests * (680 + (seed % 220) + index * 40)
    const outputTokens = item.requests * (190 + (seed % 90) + index * 18)
    const totalTokens = inputTokens + outputTokens
    const pricing = MODEL_PRICING[item.key]
    const estimatedCost = roundCurrency(inputTokens * pricing.input + outputTokens * pricing.output)
    return {
      modelKey: item.key,
      modelName: item.label,
      requestCount: item.requests,
      inputTokens,
      outputTokens,
      totalTokens,
      estimatedCost,
    }
  })

  const totalTokens = rawModels.reduce((sum, row) => sum + row.totalTokens, 0)
  const totalInputTokens = rawModels.reduce((sum, row) => sum + row.inputTokens, 0)
  const totalOutputTokens = rawModels.reduce((sum, row) => sum + row.outputTokens, 0)
  const estimatedCost = roundCurrency(rawModels.reduce((sum, row) => sum + row.estimatedCost, 0))

  return {
    totalInputTokens,
    totalOutputTokens,
    totalTokens,
    estimatedCost,
    chart: rawModels.map((row) => ({
      key: row.modelKey,
      label: row.modelName,
      totalTokens: row.totalTokens,
      inputTokens: row.inputTokens,
      outputTokens: row.outputTokens,
    })),
    table: rawModels.map((row) => ({
      ...row,
      ratio: totalTokens > 0 ? Number((row.totalTokens / totalTokens).toFixed(4)) : 0,
    })),
  }
}

function buildUsers(filter: AdminDashboardFilter) {
  const normalized = normalizeFilter(filter)
  if (normalized.invalid) {
    return { trend: [], stageBreakdown: [] }
  }
  const dates = buildDateRange(normalized.start, normalized.days)
  return {
    trend: buildTrend(hashSeed(`users:${JSON.stringify(filter)}`), dates, 12, 10, normalized.days > 14 ? 0.25 : 0),
    stageBreakdown: toBreakdown(STAGE_LABELS.map((stage, index) => ({
      ...stage,
      value: 120 + index * 40 + (hashSeed(`${stage.key}:users:${JSON.stringify(filter)}`) % 70),
    }))),
  }
}

function buildWriting(filter: AdminDashboardFilter) {
  const normalized = normalizeFilter(filter)
  if (normalized.invalid) {
    return { submissionTrend: [], scoreBreakdown: [] }
  }
  const dates = buildDateRange(normalized.start, normalized.days)
  return {
    submissionTrend: buildTrend(hashSeed(`writing:${JSON.stringify(filter)}`), dates, 18, 14, normalized.days > 10 ? 0.3 : 0),
    scoreBreakdown: toBreakdown([
      { key: '90+', label: '90分以上', value: 52 },
      { key: '80-89', label: '80-89分', value: 86 },
      { key: '70-79', label: '70-79分', value: 134 },
      { key: 'below70', label: '70分以下', value: 73 },
    ].map((item) => ({ ...item, value: item.value + (hashSeed(`${item.key}:writing:${JSON.stringify(filter)}`) % 24) }))),
  }
}

function buildContent(filter: AdminDashboardFilter) {
  const seed = hashSeed(`content:${JSON.stringify(filter)}`)
  return {
    totalPrompts: 1260 + (seed % 60),
    activePrompts: 1110 + (seed % 42),
    totalRubrics: 38 + (seed % 5),
    activeRubrics: 12 + (seed % 3),
  }
}

function buildAudit(filter: AdminDashboardFilter) {
  const normalized = normalizeFilter(filter)
  if (normalized.invalid) {
    return { actionBreakdown: [], dailyActions: [] }
  }
  const dates = buildDateRange(normalized.start, normalized.days)
  return {
    dailyActions: buildTrend(hashSeed(`audit:${JSON.stringify(filter)}`), dates, 6, 9, 0.18),
    actionBreakdown: toBreakdown([
      { key: 'user', label: '用户治理', value: 62 },
      { key: 'prompt', label: '题库变更', value: 28 },
      { key: 'rubric', label: 'Rubric 操作', value: 16 },
      { key: 'essay', label: '作文查看', value: 94 },
    ].map((item) => ({ ...item, value: item.value + (hashSeed(`${item.key}:audit:${JSON.stringify(filter)}`) % 18) }))),
  }
}

function buildSummary(payload: Omit<AdminDashboardPayload, 'summary' | 'meta'>) {
  const latestUsers = payload.users.trend[payload.users.trend.length - 1]?.value ?? 0
  const latestWriting = payload.writing.submissionTrend[payload.writing.submissionTrend.length - 1]?.value ?? 0
  const averageScoreSource = payload.writing.scoreBreakdown.reduce((sum, item) => {
    if (item.key === '90+') return sum + item.value * 92
    if (item.key === '80-89') return sum + item.value * 84
    if (item.key === '70-79') return sum + item.value * 75
    return sum + item.value * 66
  }, 0)
  const scoreCount = payload.writing.scoreBreakdown.reduce((sum, item) => sum + item.value, 0)
  return {
    totalUsers: payload.users.stageBreakdown.reduce((sum, item) => sum + item.value, 4200),
    dailyNewUsers: latestUsers,
    activeUsers: Math.round((payload.users.stageBreakdown.reduce((sum, item) => sum + item.value, 0) || 1) * 0.58),
    writingSubmissions: latestWriting,
    averageScore: scoreCount > 0 ? Number((averageScoreSource / scoreCount).toFixed(1)) : 0,
    pendingTasks: payload.audit.actionBreakdown.find((item) => item.key === 'essay')?.value ?? 0,
  }
}

export async function getDashboardMock(filter: AdminDashboardFilter): Promise<AdminDashboardPayload> {
  const subscription = buildSubscription(filter)
  const modelUsage = buildModelUsage(filter)
  const users = buildUsers(filter)
  const writing = buildWriting(filter)
  const content = buildContent(filter)
  const audit = buildAudit(filter)
  const payloadWithoutSummary = {
    subscription,
    modelUsage,
    users,
    writing,
    content,
    audit,
  }

  return {
    meta: {
      dataSource: 'mock',
      generatedAt: '2026-03-14T09:30:00+08:00',
      filters: filter,
    },
    summary: buildSummary(payloadWithoutSummary),
    ...payloadWithoutSummary,
  }
}

