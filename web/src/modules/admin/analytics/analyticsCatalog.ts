export type AnalyticsPageKey = 'overview' | 'users' | 'subscriptions' | 'writing' | 'ai-usage' | 'funnel'
export type AnalyticsDataSource = 'realtime' | 'mixed' | 'mock' | 'todo'

export interface AnalyticsPageDefinition {
  key: AnalyticsPageKey
  label: string
  path: string
  dataSource: AnalyticsDataSource
  description: string
}

export const analyticsPages: AnalyticsPageDefinition[] = [
  {
    key: 'overview',
    label: 'BI 总览',
    path: '/admin/analytics',
    dataSource: 'mixed',
    description: '汇总增长、订阅、写作和 AI 用量，已优先复用订阅概览数据。',
  },
  {
    key: 'users',
    label: '用户分析',
    path: '/admin/analytics/users',
    dataSource: 'todo',
    description: '用户增长、活跃、留存和学段分布需要后续聚合接口。',
  },
  {
    key: 'subscriptions',
    label: '订阅分析',
    path: '/admin/analytics/subscriptions',
    dataSource: 'mixed',
    description: '订阅用户、套餐分布和每日趋势优先复用现有订阅接口。',
  },
  {
    key: 'writing',
    label: '写作分析',
    path: '/admin/analytics/writing',
    dataSource: 'todo',
    description: '作文提交、评分完成率和分数分布等待写作聚合接口。',
  },
  {
    key: 'ai-usage',
    label: 'AI 用量',
    path: '/admin/analytics/ai-usage',
    dataSource: 'todo',
    description: '模型、workflow、token、失败率和成本估算等待模型用量聚合接口。',
  },
  {
    key: 'funnel',
    label: '转化漏斗',
    path: '/admin/analytics/funnel',
    dataSource: 'mock',
    description: '先用 Mock 展示注册到订阅漏斗，后续接事件或日快照。',
  },
]

export function findAnalyticsPage(key: AnalyticsPageKey) {
  return analyticsPages.find((page) => page.key === key) ?? analyticsPages[0]
}

export function dataSourceLabel(source: AnalyticsDataSource) {
  const labels: Record<AnalyticsDataSource, string> = {
    realtime: '真实数据',
    mixed: '部分接入',
    mock: 'Mock 数据',
    todo: '待实现',
  }
  return labels[source]
}
