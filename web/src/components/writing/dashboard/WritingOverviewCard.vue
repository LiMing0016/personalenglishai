<template>
  <article class="writing-overview-card" aria-labelledby="writing-overview-title">
    <header class="overview-header">
      <div class="overview-title-block">
        <span class="overview-kicker">Overview</span>
        <h3 id="writing-overview-title">写作总览</h3>
        <p>{{ overviewScopeText }}</p>
        <div class="overview-menu-row" aria-label="写作总览筛选">
          <div ref="rangeMenuRef" class="overview-range-picker">
            <button
              type="button"
              class="overview-range-trigger"
              aria-haspopup="dialog"
              :aria-expanded="rangeMenuOpen"
              @click="rangeMenuOpen = !rangeMenuOpen"
            >
              <span>时间范围</span>
              <strong>{{ rangeButtonLabel }}</strong>
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="m5 7 5 5 5-5" />
              </svg>
            </button>
            <div v-if="rangeMenuOpen" class="overview-date-popover" role="dialog" aria-label="选择统计时间范围">
              <div class="overview-date-presets" aria-label="快捷时间范围">
                <button
                  v-for="option in rangeOptions"
                  :key="option.value"
                  type="button"
                  :class="{ active: range === option.value }"
                  @click="applyRangePreset(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
              <div class="overview-date-custom">
                <span class="overview-date-kicker">自定义范围</span>
                <label>
                  <span>开始日期</span>
                  <input :value="customRange.start" type="date" @change="selectCustomStart" />
                </label>
                <label>
                  <span>结束日期</span>
                  <input :value="customRange.end" type="date" @change="selectCustomEnd" />
                </label>
                <button type="button" class="overview-date-apply" @click="applyCustomRange">
                  应用日期范围
                </button>
              </div>
            </div>
          </div>
          <label class="overview-filter-menu">
            <span>写作模式</span>
            <select :value="mode" aria-label="写作模式" @change="selectMode">
              <option v-for="option in modeOptions" :key="option.value" :value="option.value">
                {{ modeOptionLabel(option.label) }}
              </option>
            </select>
            <svg viewBox="0 0 20 20" aria-hidden="true">
              <path d="m5 7 5 5 5-5" />
            </svg>
          </label>
        </div>
      </div>
    </header>

    <div class="overview-metrics" aria-label="写作总览摘要">
      <div v-for="metric in metrics" :key="metric.label" class="overview-metric">
        <span class="metric-icon" :class="`metric-icon--${metric.tone}`">
          <svg v-if="metric.icon === 'doc'" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M14 2H7a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7Z" />
            <path d="M14 2v5h5M9 13h6M9 17h4" />
          </svg>
          <svg v-else-if="metric.icon === 'pulse'" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 12h4l3-7 4 14 3-7h4" />
          </svg>
          <svg v-else-if="metric.icon === 'bar'" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 20V10M12 20V4M19 20v-7" />
          </svg>
          <svg v-else viewBox="0 0 24 24" aria-hidden="true">
            <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z" />
          </svg>
        </span>
        <span class="metric-copy">
          <strong>{{ metric.value }}</strong>
          <em>{{ metric.unit }}</em>
          <small>{{ metric.label }}</small>
        </span>
      </div>
    </div>

    <div ref="chartRef" class="overview-chart" aria-label="写作总览组合图"></div>

    <div class="overview-insight">
      <strong>AI建议</strong>
      <span>{{ overview.insight }}</span>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useEventListener } from '@vueuse/core'

import type {
  WritingDashboardMode,
  WritingDashboardRange,
  WritingDashboardCustomRange,
  WritingOverviewData,
  WritingOverviewTrendPoint,
} from '@/pages/app/writingDashboardMock'

echarts.use([
  BarChart,
  LineChart,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
  CanvasRenderer,
])

const props = defineProps<{
  overview: WritingOverviewData
  range: WritingDashboardRange
  mode: WritingDashboardMode
  customRange: WritingDashboardCustomRange
  rangeOptions: Array<{ value: WritingDashboardRange; label: string }>
  modeOptions: Array<{ value: WritingDashboardMode; label: string }>
}>()

const emit = defineEmits<{
  'update:range': [value: WritingDashboardRange]
  'update:mode': [value: WritingDashboardMode]
  'update:customRange': [value: WritingDashboardCustomRange]
}>()

const chartRef = ref<HTMLElement | null>(null)
const rangeMenuRef = ref<HTMLElement | null>(null)
const rangeMenuOpen = ref(false)
let chartInstance: echarts.ECharts | null = null
const DAY_MS = 24 * 60 * 60 * 1000
const TARGET_SCORE = 80

interface DisplayTrendPoint extends WritingOverviewTrendPoint {
  sourceLabel: string
}

interface TrendWindow {
  start: Date
  end: Date
  unit: 'day' | 'week' | 'month'
}

const overviewScopeText = computed(() => {
  const rangeLabel = props.range === 'custom'
    ? customRangeText.value
    : props.rangeOptions.find(option => option.value === props.range)?.label ?? '近30天'
  const modeLabel = props.modeOptions.find(option => option.value === props.mode)?.label ?? '全部'
  return `${rangeLabel} · ${modeLabel}模式 · 按每篇最新评分`
})

const rangeButtonLabel = computed(() => {
  if (props.range === 'custom') return customRangeText.value
  return props.rangeOptions.find(option => option.value === props.range)?.label ?? '近30天'
})

const customRangeText = computed(() => {
  if (!props.customRange.start || !props.customRange.end) return '自定义日期'
  return `${formatDateLabel(props.customRange.start)} - ${formatDateLabel(props.customRange.end)}`
})

const metrics = computed(() => [
  {
    label: '累计作文',
    value: props.overview.summary.totalEssays,
    unit: '篇',
    icon: 'doc',
    tone: 'dark',
  },
  {
    label: '评分次数',
    value: props.overview.summary.totalSubmissions,
    unit: '次',
    icon: 'pulse',
    tone: 'dark',
  },
  {
    label: '平均分',
    value: props.overview.summary.averageScore,
    unit: '分',
    icon: 'bar',
    tone: 'blue',
  },
  {
    label: '最高分',
    value: props.overview.summary.bestScore,
    unit: '分',
    icon: 'star',
    tone: 'amber',
  },
])

const displayTrend = computed<DisplayTrendPoint[]>(() => buildDisplayTrend())

watch([() => props.overview, () => props.range, () => props.mode, () => props.customRange], async () => {
  await nextTick()
  renderChart()
}, { deep: true })

onMounted(async () => {
  await nextTick()
  renderChart()
})

useEventListener(window, 'resize', () => {
  chartInstance?.resize()
})

useEventListener(document, 'click', (event) => {
  const target = event.target as Node | null
  if (target && rangeMenuRef.value?.contains(target)) return
  rangeMenuOpen.value = false
})

onBeforeUnmount(() => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})

function renderChart() {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const trend = displayTrend.value
  const trendWindow = props.range === 'all' ? null : resolveTrendWindow()
  const bucketUnit = trendWindow?.unit ?? 'day'
  const dates = trend.map(item => item.date)
  const maxCount = Math.max(1, ...trend.map(item => item.essayCount))
  const labelInterval = dates.length <= 14 ? 0 : Math.max(0, Math.ceil(dates.length / 8) - 1)
  const barWidth = bucketUnit === 'week' ? 34 : bucketUnit === 'month' ? 22 : dates.length > 20 ? 10 : 14

  chartInstance.setOption({
    animationDuration: 650,
    animationEasing: 'cubicOut',
    color: ['#5fbf9b', '#2f6f64'],
    legend: {
      top: 4,
      right: 0,
      icon: 'roundRect',
      itemWidth: 16,
      itemHeight: 8,
      itemGap: 18,
      textStyle: { color: '#6f6a60', fontSize: 12, fontWeight: 700 },
    },
    grid: { top: 52, right: 46, bottom: 38, left: 44 },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        lineStyle: { color: '#cfc6b8', type: 'dashed' },
        crossStyle: { color: '#cfc6b8' },
      },
      backgroundColor: '#fffefa',
      borderColor: '#e4dfd3',
      borderWidth: 1,
      textStyle: { color: '#191919', fontSize: 12 },
      formatter: (params: any) => {
        const date = params[0]?.axisValue
        const point = trend.find(item => item.date === date)
        if (!point) return ''
        return [
          `<strong>${point.sourceLabel}</strong>`,
          `完成作文：${point.essayCount} 篇`,
          `评分次数：${point.submissionCount} 次`,
          `平均分：${point.averageScore} 分`,
          `目标分：${TARGET_SCORE} 分`,
        ].join('<br/>')
      },
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#ded9ce' } },
      axisLabel: { color: '#8b8579', fontSize: 11, interval: labelInterval },
    },
    yAxis: [
      {
        type: 'value',
        min: 0,
        max: Math.max(3, maxCount + 1),
        minInterval: 1,
        name: '篇',
        nameTextStyle: { color: '#8b8579', fontSize: 11 },
        axisLabel: { color: '#8b8579', fontSize: 11 },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: '#ebe5da', type: 'dashed' } },
      },
      {
        type: 'value',
        min: 50,
        max: 100,
        interval: 10,
        name: '分',
        nameTextStyle: { color: '#8b8579', fontSize: 11 },
        axisLabel: { color: '#8b8579', fontSize: 11 },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '完成作文',
        type: 'bar',
        barWidth,
        barCategoryGap: '58%',
        yAxisIndex: 0,
        data: trend.map(item => item.essayCount),
        itemStyle: {
          borderRadius: [10, 10, 0, 0],
          opacity: 0.92,
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#1f9f78' },
            { offset: 0.55, color: '#6ed0ae' },
            { offset: 1, color: 'rgba(110, 208, 174, 0.28)' },
          ]),
        },
      },
      {
        name: '平均分',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: trend.map(item => item.averageScore),
        lineStyle: {
          width: 3,
          color: '#2f6f64',
          shadowColor: 'rgba(47, 111, 100, 0.18)',
          shadowBlur: 9,
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(47, 111, 100, 0.14)' },
            { offset: 1, color: 'rgba(47, 111, 100, 0)' },
          ]),
        },
        itemStyle: { color: '#fffefa', borderColor: '#2f6f64', borderWidth: 2.5 },
        markLine: {
          symbol: 'none',
          silent: true,
          lineStyle: { color: '#d89b35', type: 'dashed', width: 1.5 },
          label: {
            formatter: `目标 ${TARGET_SCORE}分`,
            color: '#9a5b00',
            fontSize: 11,
            fontWeight: 700,
            backgroundColor: '#fff7e6',
            borderColor: '#efc983',
            borderWidth: 1,
            borderRadius: 999,
            padding: [4, 8],
          },
          data: [
            {
              yAxis: TARGET_SCORE,
              label: {
                position: 'end',
              },
            },
          ],
        },
      },
    ],
  }, true)
}

function buildDisplayTrend(): DisplayTrendPoint[] {
  if (props.range === 'all') {
    return props.overview.trend.map(item => ({
      ...item,
      sourceLabel: item.date,
    }))
  }

  const window = resolveTrendWindow()
  const labels = window.unit === 'month'
    ? buildMonthLabels(window.start, window.end)
    : window.unit === 'week'
      ? buildWeekLabels(window.start, window.end)
      : buildDayLabels(window.start, window.end)

  return labels.map((label, index) => buildSyntheticTrendPoint(label, index, labels.length, window.unit))
}

function resolveTrendWindow(): TrendWindow {
  const today = startOfDay(new Date())

  if (props.range === 'year') {
    return {
      start: startOfMonth(addMonths(today, -11)),
      end: today,
      unit: 'month',
    }
  }

  if (props.range === 'custom') {
    const start = parseDateInput(props.customRange.start)
    const end = parseDateInput(props.customRange.end)
    if (start && end && start.getTime() <= end.getTime()) {
      const spanDays = Math.round((end.getTime() - start.getTime()) / DAY_MS) + 1
      return {
        start,
        end,
        unit: spanDays > 90 ? 'month' : spanDays > 14 ? 'week' : 'day',
      }
    }
  }

  if (props.range === '30d') {
    return {
      start: addDays(today, -29),
      end: today,
      unit: 'week',
    }
  }

  const days = props.range === '7d' ? 7 : 14

  return {
    start: addDays(today, -(days - 1)),
    end: today,
    unit: 'day',
  }
}

function buildDayLabels(start: Date, end: Date) {
  const total = Math.max(1, Math.round((end.getTime() - start.getTime()) / DAY_MS) + 1)
  return Array.from({ length: total }, (_, index) => {
    const date = addDays(start, index)
    return {
      key: formatDayLabel(date),
      sourceLabel: formatFullDateLabel(date),
    }
  })
}

function buildWeekLabels(start: Date, end: Date) {
  const labels: Array<{ key: string; sourceLabel: string }> = []
  let cursor = start
  let weekIndex = 1

  while (cursor.getTime() <= end.getTime()) {
    const weekStart = cursor
    const weekEnd = new Date(Math.min(addDays(cursor, 6).getTime(), end.getTime()))
    labels.push({
      key: `第${weekIndex}周`,
      sourceLabel: `${formatDateLabel(formatDateInput(weekStart))} - ${formatDateLabel(formatDateInput(weekEnd))}`,
    })
    cursor = addDays(cursor, 7)
    weekIndex += 1
  }

  return labels
}

function buildMonthLabels(start: Date, end: Date) {
  const labels: Array<{ key: string; sourceLabel: string }> = []
  let cursor = startOfMonth(start)
  const last = startOfMonth(end)

  while (cursor.getTime() <= last.getTime()) {
    labels.push({
      key: formatMonthLabel(cursor),
      sourceLabel: `${cursor.getFullYear()}年${String(cursor.getMonth() + 1).padStart(2, '0')}月`,
    })
    cursor = addMonths(cursor, 1)
  }

  return labels.length ? labels : [{ key: formatMonthLabel(start), sourceLabel: formatFullDateLabel(start) }]
}

function buildSyntheticTrendPoint(
  label: { key: string; sourceLabel: string },
  index: number,
  total: number,
  unit: TrendWindow['unit'],
): DisplayTrendPoint {
  const seed = props.overview.trend[index % Math.max(1, props.overview.trend.length)]
  const progress = total <= 1 ? 1 : index / (total - 1)
  const startScore = props.overview.trend[0]?.averageScore ?? Math.max(60, props.overview.summary.averageScore - 10)
  const averageScore = clampScore(Math.round(startScore + (props.overview.summary.averageScore - startScore) * progress))
  const bestScore = clampScore(Math.max(averageScore, Math.round(averageScore + (props.overview.summary.bestScore - averageScore) * 0.72)))
  const shouldShowSparseCount = unit === 'month' ? index % 3 === 1 : unit === 'week' ? true : index % 5 === 0

  return {
    date: label.key,
    sourceLabel: label.sourceLabel,
    essayCount: unit === 'week'
      ? Math.max(1, seed?.essayCount ?? 0, index % 2 === 0 ? 2 : 1)
      : seed?.essayCount || (shouldShowSparseCount ? 1 : 0),
    submissionCount: unit === 'week'
      ? Math.max(1, seed?.submissionCount ?? 0, index % 2 === 0 ? 2 : 1)
      : seed?.submissionCount || (unit === 'month' && index % 4 === 2 ? 1 : 0),
    averageScore,
    bestScore,
  }
}

function addDays(date: Date, days: number) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function addMonths(date: Date, months: number) {
  const next = new Date(date)
  next.setMonth(next.getMonth() + months)
  return next
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function parseDateInput(value: string) {
  if (!value) return null
  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day) return null
  return new Date(year, month - 1, day)
}

function formatDayLabel(date: Date) {
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function formatMonthLabel(date: Date) {
  return `${String(date.getFullYear()).slice(2)}/${String(date.getMonth() + 1).padStart(2, '0')}`
}

function formatFullDateLabel(date: Date) {
  return `${date.getFullYear()}-${formatDayLabel(date)}`
}

function formatDateInput(date: Date) {
  return `${date.getFullYear()}-${formatDayLabel(date)}`
}

function clampScore(value: number) {
  return Math.min(100, Math.max(50, value))
}

function selectMode(event: Event) {
  emit('update:mode', (event.target as HTMLSelectElement).value as WritingDashboardMode)
}

function modeOptionLabel(label: string) {
  return `${label}模式`
}

function applyRangePreset(value: WritingDashboardRange) {
  emit('update:range', value)
  rangeMenuOpen.value = false
}

function selectCustomStart(event: Event) {
  emit('update:customRange', {
    ...props.customRange,
    start: (event.target as HTMLInputElement).value,
  })
}

function selectCustomEnd(event: Event) {
  emit('update:customRange', {
    ...props.customRange,
    end: (event.target as HTMLInputElement).value,
  })
}

function applyCustomRange() {
  emit('update:range', 'custom')
  rangeMenuOpen.value = false
}

function formatDateLabel(value: string) {
  const [, month, day] = value.split('-')
  return month && day ? `${month}/${day}` : value
}
</script>

<style scoped>
.writing-overview-card {
  display: grid;
  gap: 18px;
  margin-bottom: 30px;
  padding: 22px;
  border: 1px solid #e4dfd3;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 44px rgba(31, 28, 21, 0.05);
}

.overview-header {
  display: block;
}

.overview-title-block {
  min-width: 0;
}

.overview-kicker {
  display: block;
  margin-bottom: 7px;
  color: #6f6a60;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.overview-header h3 {
  margin: 0;
  color: #191919;
  font-size: 22px;
  font-weight: 850;
  line-height: 1.15;
}

.overview-header p {
  margin: 8px 0 0;
  color: #6f6a60;
  font-size: 13px;
}

.overview-menu-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
}

.overview-range-picker {
  position: relative;
}

.overview-range-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 0 36px 0 12px;
  border: 1px solid #ded9ce;
  border-radius: 999px;
  background: #fbfaf7;
  color: #191919;
  box-shadow: 0 8px 18px rgba(31, 28, 21, 0.04);
  cursor: pointer;
}

.overview-range-trigger span {
  color: #8b8579;
  font-size: 12px;
  font-weight: 700;
}

.overview-range-trigger strong {
  font-size: 13px;
  font-weight: 850;
  white-space: nowrap;
}

.overview-range-trigger svg,
.overview-filter-menu svg {
  position: absolute;
  right: 12px;
  width: 16px;
  height: 16px;
  fill: none;
  stroke: #6f6a60;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
  pointer-events: none;
}

.overview-range-trigger:focus-visible {
  outline: none;
  border-color: #9ed8bd;
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.12);
}

.overview-date-popover {
  position: absolute;
  z-index: 20;
  top: calc(100% + 10px);
  left: 0;
  display: grid;
  grid-template-columns: 150px minmax(240px, 1fr);
  min-width: 430px;
  overflow: hidden;
  border: 1px solid #d8d1c4;
  border-radius: 16px;
  background: #fffefa;
  box-shadow: 0 24px 70px rgba(31, 28, 21, 0.18);
}

.overview-date-presets {
  display: grid;
  align-content: start;
  gap: 2px;
  padding: 12px;
  border-right: 1px solid #e4dfd3;
  background: #f4f0e8;
}

.overview-date-presets button {
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #514c43;
  font-size: 13px;
  font-weight: 800;
  text-align: left;
  cursor: pointer;
}

.overview-date-presets button:hover,
.overview-date-presets button.active {
  color: #047857;
  background: #e8f6ef;
}

.overview-date-custom {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.overview-date-kicker {
  color: #6f6a60;
  font-size: 12px;
  font-weight: 850;
  text-transform: uppercase;
}

.overview-date-custom label {
  display: grid;
  gap: 6px;
}

.overview-date-custom label span {
  color: #7a746a;
  font-size: 12px;
  font-weight: 800;
}

.overview-date-custom input {
  width: 100%;
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid #ded9ce;
  border-radius: 10px;
  background: #fbfaf7;
  color: #191919;
  font-size: 13px;
  font-weight: 700;
}

.overview-date-custom input:focus {
  outline: none;
  border-color: #9ed8bd;
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.12);
}

.overview-date-apply {
  min-height: 38px;
  border: 0;
  border-radius: 10px;
  background: #111111;
  color: #ffffff;
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
}

.overview-date-apply:hover {
  background: #000000;
}

.overview-filter-menu {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 0 36px 0 12px;
  border: 1px solid #ded9ce;
  border-radius: 999px;
  background: #fbfaf7;
  color: #6f6a60;
  box-shadow: 0 8px 18px rgba(31, 28, 21, 0.04);
}

.overview-filter-menu span {
  color: #8b8579;
  font-size: 12px;
  font-weight: 700;
}

.overview-filter-menu select {
  min-width: 82px;
  appearance: none;
  border: 0;
  outline: 0;
  background: transparent;
  color: #191919;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.overview-filter-menu:focus-within {
  border-color: #9ed8bd;
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.12);
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-metric {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #eee9df;
  border-radius: 12px;
  background: #fbfaf7;
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 1px solid #dfd8ca;
  border-radius: 11px;
  background: #ffffff;
  color: #191919;
}

.metric-icon--blue {
  color: #2563eb;
}

.metric-icon--amber {
  color: #d97706;
}

.metric-icon svg {
  width: 21px;
  height: 21px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.9;
}

.metric-copy {
  display: block;
  min-width: 0;
}

.metric-copy strong {
  color: #191919;
  font-size: 26px;
  line-height: 1;
}

.metric-copy em {
  margin-left: 4px;
  color: #514c43;
  font-size: 13px;
  font-style: normal;
  font-weight: 800;
}

.metric-copy small {
  display: block;
  margin-top: 5px;
  color: #7a746a;
  font-size: 12px;
  font-weight: 700;
}

.overview-chart {
  width: 100%;
  height: 330px;
  min-width: 0;
  padding: 8px 4px 0;
  border: 1px solid #eee9df;
  border-radius: 13px;
  background: linear-gradient(180deg, #fffefa 0%, #fbfaf7 100%);
}

.overview-insight {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #eee9df;
  border-radius: 12px;
  background: #fbfaf7;
}

.overview-insight strong {
  flex: 0 0 auto;
  color: #047857;
  font-size: 13px;
  font-weight: 850;
}

.overview-insight span {
  color: #514c43;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .overview-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .writing-overview-card {
    padding: 18px;
  }
  .overview-date-popover {
    grid-template-columns: 1fr;
    min-width: min(86vw, 360px);
  }
  .overview-date-presets {
    border-right: 0;
    border-bottom: 1px solid #e4dfd3;
  }
  .overview-metrics {
    grid-template-columns: 1fr;
  }
  .overview-chart {
    height: 280px;
  }
  .overview-insight {
    flex-direction: column;
    gap: 6px;
  }
}
</style>
