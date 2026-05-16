import { adminApi } from '@/api/admin'
import { mockAnalyticsDatasets } from '../mocks/analyticsMock.ts'
import type { AnalyticsDataset, AnalyticsFilters } from '../types/index.ts'
import type { AnalyticsPageKey } from '../analyticsCatalog.ts'

function cloneDataset(pageKey: AnalyticsPageKey): AnalyticsDataset {
  return JSON.parse(JSON.stringify(mockAnalyticsDatasets[pageKey])) as AnalyticsDataset
}

function formatNumber(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function formatRatio(value: number | null | undefined) {
  return `${Number(value ?? 0).toFixed(1)}%`
}

export function defaultAnalyticsFilters(): AnalyticsFilters {
  const now = new Date()
  const start = new Date(now)
  start.setDate(now.getDate() - 29)
  return {
    dateFrom: start.toISOString().slice(0, 10),
    dateTo: now.toISOString().slice(0, 10),
    studyStage: '',
    planCode: '',
    channel: '',
  }
}

export async function getAnalyticsDataset(pageKey: AnalyticsPageKey, filters: AnalyticsFilters): Promise<AnalyticsDataset> {
  if (pageKey === 'overview') {
    return loadOverviewDataset(filters)
  }
  if (pageKey === 'subscriptions') {
    return loadSubscriptionDataset(filters)
  }
  return cloneDataset(pageKey)
}

async function loadOverviewDataset(_filters: AnalyticsFilters): Promise<AnalyticsDataset> {
  const dataset = cloneDataset('overview')
  try {
    const overview = await adminApi.getSubscriptionOverview()
    dataset.generatedAt = new Date().toLocaleString('zh-CN')
    dataset.kpis = [
      { label: '新增用户', value: formatNumber(overview.todayNewUsers), delta: '真实', tone: 'good', source: 'realtime' },
      { label: '活跃用户', value: formatNumber(overview.totalUsers), delta: '用总用户暂代', source: 'mixed' },
      { label: '订阅新增', value: formatNumber(overview.todayNewSubscriptions), delta: '真实', tone: 'good', source: 'realtime' },
      { label: '作文提交', value: '待实现', delta: '写作聚合', tone: 'warning', source: 'todo' },
      { label: 'AI Tokens', value: formatNumber(overview.todayFreeTokenUsed + overview.todayPaidTokenUsed), delta: '订阅用量', source: 'mixed' },
      { label: '失败率', value: '待实现', delta: '任务聚合', tone: 'warning', source: 'todo' },
    ]
  } catch {
    dataset.notice = '订阅概览接口不可用，当前显示 Mock 和待实现状态。'
  }
  return dataset
}

async function loadSubscriptionDataset(filters: AnalyticsFilters): Promise<AnalyticsDataset> {
  const dataset = cloneDataset('subscriptions')
  try {
    const [overview, dailyStats] = await Promise.all([
      adminApi.getSubscriptionOverview(),
      adminApi.listSubscriptionDailyStats({ dateFrom: filters.dateFrom, dateTo: filters.dateTo }),
    ])
    dataset.generatedAt = new Date().toLocaleString('zh-CN')
    dataset.kpis = [
      { label: '订阅用户', value: formatNumber(overview.subscribedUsers), source: 'realtime' },
      { label: '新增订阅', value: formatNumber(overview.todayNewSubscriptions), source: 'realtime' },
      { label: '订阅转化率', value: formatRatio(overview.sevenDaySubscriptionRate), source: 'realtime' },
      { label: '超额用户', value: formatNumber(overview.overLimitUsers), source: 'realtime', tone: overview.overLimitUsers > 0 ? 'warning' : 'neutral' },
    ]
    dataset.charts = [
      {
        title: '每日新增订阅',
        subtitle: '来自 /api/admin/subscriptions/daily-stats',
        source: 'realtime',
        points: dailyStats.map((item) => ({
          label: item.statDate.slice(5),
          value: item.newSubscriptions,
          secondaryValue: item.subscriptionRate,
        })),
      },
      {
        title: '套餐分布',
        subtitle: '来自 /api/admin/subscriptions/overview',
        source: 'realtime',
        points: overview.planDistribution.map((item) => ({
          label: item.planName || item.planCode,
          value: item.userCount,
          secondaryValue: item.ratio,
        })),
      },
    ]
    dataset.tables = [
      {
        title: '订阅分层',
        source: 'realtime',
        columns: ['分层', '用户数', '占比'],
        rows: overview.planDistribution.map((item) => [
          item.planName || item.planCode,
          formatNumber(item.userCount),
          formatRatio(item.ratio),
        ]),
      },
    ]
  } catch {
    dataset.notice = '订阅接口不可用，当前显示 Mock 结构和空状态。'
  }
  return dataset
}
