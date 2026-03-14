<template>
  <DashboardSection
    title="模型 Token 消耗"
    description="成本和 token 使用量均由 facade 层汇总计算。页面只消费标准化结果。"
    :status="status"
    empty-message="当前筛选条件下暂无模型用量示例数据"
  >
    <template #badge>
      <DashboardBadge label="Mock" tone="warn" />
    </template>
    <template #actions>
      <div class="admin-toolbar-right">
        <button
          v-for="item in modes"
          :key="item.value"
          type="button"
          class="admin-btn admin-btn--secondary"
          :class="{ 'admin-dashboard-filterbar__preset--active': viewMode === item.value }"
          @click="viewMode = item.value"
        >
          {{ item.label }}
        </button>
      </div>
    </template>

    <div class="admin-grid-two admin-dashboard-kpi-grid admin-dashboard-kpi-grid--wide">
      <DashboardKpiCard label="输入 Token" :value="formatNumber(metrics.totalInputTokens)" />
      <DashboardKpiCard label="输出 Token" :value="formatNumber(metrics.totalOutputTokens)" />
      <DashboardKpiCard label="总 Token" :value="formatNumber(metrics.totalTokens)" />
      <DashboardKpiCard label="估算成本" :value="formatCurrency(metrics.estimatedCost)" hint="按模型单独计费后聚合" />
    </div>

    <div class="admin-grid-two admin-dashboard-chart-grid">
      <div>
        <div ref="chartEl" class="admin-dashboard-chart"></div>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>模型</th>
              <th>请求数</th>
              <th>输入</th>
              <th>输出</th>
              <th>总量</th>
              <th>占比</th>
              <th>成本</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in metrics.table" :key="row.modelKey">
              <td>{{ row.modelName }}</td>
              <td>{{ formatNumber(row.requestCount) }}</td>
              <td>{{ formatNumber(row.inputTokens) }}</td>
              <td>{{ formatNumber(row.outputTokens) }}</td>
              <td>{{ formatNumber(row.totalTokens) }}</td>
              <td>{{ formatPercent(row.ratio) }}</td>
              <td>{{ formatCurrency(row.estimatedCost) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </DashboardSection>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import DashboardBadge from './DashboardBadge.vue'
import DashboardKpiCard from './DashboardKpiCard.vue'
import DashboardSection from './DashboardSection.vue'
import { useAdminChart } from '../composables/useAdminChart'
import type { AdminDashboardStatus, AdminModelUsageMetrics } from '../types'

const props = defineProps<{
  metrics: AdminModelUsageMetrics
  status: AdminDashboardStatus
}>()

const modes = [
  { value: 'total', label: '总量' },
  { value: 'input', label: '输入' },
  { value: 'output', label: '输出' },
] as const

const viewMode = ref<'total' | 'input' | 'output'>('total')
const chartEl = ref<HTMLElement | null>(null)
const chart = useAdminChart()

const hasData = computed(() => props.metrics.chart.length > 0)

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatCurrency(value: number) {
  return `¥${value.toFixed(2)}`
}

function formatPercent(value: number) {
  return `${(value * 100).toFixed(1)}%`
}

function clearChart() {
  chart.dispose()
}

function handleResize() {
  chart.resize()
}

function getChartValues() {
  return props.metrics.chart.map((row) => {
    if (viewMode.value === 'input') return row.inputTokens
    if (viewMode.value === 'output') return row.outputTokens
    return row.totalTokens
  })
}

async function renderChart() {
  if (!hasData.value || !chartEl.value) {
    clearChart()
    return
  }

  await nextTick()

  chart.mount(chartEl.value)
  chart.setOption({
    color: ['#136f4b'],
    tooltip: { trigger: 'axis' },
    grid: { left: 32, right: 20, top: 24, bottom: 34, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.metrics.chart.map((item) => item.label),
      axisLabel: { color: '#607164', interval: 0, rotate: 12 },
      axisLine: { lineStyle: { color: '#b5c4ae' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#607164' },
      splitLine: { lineStyle: { color: '#e5ece1' } },
    },
    series: [{ type: 'bar', barWidth: 34, borderRadius: [10, 10, 0, 0], data: getChartValues() }],
  })
}

watch(() => [props.metrics, props.status, viewMode.value], async () => {
  if (props.status === 'ready') {
    await renderChart()
    return
  }

  clearChart()
}, { deep: true, immediate: true })

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  clearChart()
})
</script>
