import type { AnalyticsDataSource, AnalyticsPageKey } from '../analyticsCatalog.ts'

export interface AnalyticsFilters {
  dateFrom: string
  dateTo: string
  studyStage: string
  planCode: string
  channel: string
}

export interface AnalyticsKpi {
  label: string
  value: string
  delta?: string
  tone?: 'neutral' | 'good' | 'warning' | 'danger'
  source: AnalyticsDataSource
}

export interface AnalyticsChartPoint {
  label: string
  value: number
  secondaryValue?: number
}

export interface AnalyticsChart {
  title: string
  subtitle: string
  source: AnalyticsDataSource
  points: AnalyticsChartPoint[]
}

export interface AnalyticsTable {
  title: string
  source: AnalyticsDataSource
  columns: string[]
  rows: Array<Array<string | number>>
}

export interface AnalyticsDataset {
  pageKey: AnalyticsPageKey
  source: AnalyticsDataSource
  generatedAt: string
  notice: string
  kpis: AnalyticsKpi[]
  charts: AnalyticsChart[]
  tables: AnalyticsTable[]
}
