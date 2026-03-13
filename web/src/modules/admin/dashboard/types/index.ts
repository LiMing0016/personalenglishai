export type AdminDashboardPreset = 'today' | 'yesterday' | 'thisWeek' | 'thisMonth' | 'custom'

export type AdminDashboardStatus = 'loading' | 'empty' | 'error' | 'ready'

export interface AdminDashboardFilter {
  preset: AdminDashboardPreset
  startDate?: string
  endDate?: string
  timezone?: string
}

export interface AdminDashboardMeta {
  dataSource: 'mock' | 'api'
  generatedAt: string
  filters: AdminDashboardFilter
}

export interface DateValuePoint {
  date: string
  value: number
}

export interface BreakdownItem {
  key: string
  label: string
  value: number
  ratio: number
}

export interface AdminDashboardSummary {
  totalUsers: number
  dailyNewUsers: number
  activeUsers: number
  writingSubmissions: number
  averageScore: number
  pendingTasks: number
}

export interface AdminSubscriptionMetrics {
  dailyNew: number
  yesterdayNew: number
  monthlyNew: number
  totalSubscribedUsers: number
  activeSubscribers: number
  trend: DateValuePoint[]
  planBreakdown: BreakdownItem[]
}

export interface AdminModelUsageChartPoint {
  key: string
  label: string
  totalTokens: number
  inputTokens: number
  outputTokens: number
}

export interface AdminModelUsageRow {
  modelKey: string
  modelName: string
  requestCount: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  ratio: number
  estimatedCost: number
}

export interface AdminModelUsageMetrics {
  totalInputTokens: number
  totalOutputTokens: number
  totalTokens: number
  estimatedCost: number
  chart: AdminModelUsageChartPoint[]
  table: AdminModelUsageRow[]
}

export interface AdminUserMetrics {
  trend: DateValuePoint[]
  stageBreakdown: BreakdownItem[]
}

export interface AdminWritingMetrics {
  submissionTrend: DateValuePoint[]
  scoreBreakdown: BreakdownItem[]
}

export interface AdminContentMetrics {
  totalPrompts: number
  activePrompts: number
  totalRubrics: number
  activeRubrics: number
}

export interface AdminAuditMetrics {
  actionBreakdown: BreakdownItem[]
  dailyActions: DateValuePoint[]
}

export interface AdminDashboardPayload {
  meta: AdminDashboardMeta
  summary: AdminDashboardSummary
  subscription: AdminSubscriptionMetrics
  modelUsage: AdminModelUsageMetrics
  users: AdminUserMetrics
  writing: AdminWritingMetrics
  content: AdminContentMetrics
  audit: AdminAuditMetrics
}
