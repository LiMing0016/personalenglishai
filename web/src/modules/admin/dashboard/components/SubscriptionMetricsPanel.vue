<template>
  <DashboardSection
    title="订阅增长"
    description="订阅相关指标当前使用前端演示数据。后端接入后将替换为真实订单与会员统计。"
    :status="status"
    empty-message="当前筛选条件下暂无订阅示例数据"
  >
    <template #badge>
      <DashboardBadge label="Mock" tone="warn" />
    </template>

    <div class="admin-grid-two admin-dashboard-kpi-grid admin-dashboard-kpi-grid--wide">
      <DashboardKpiCard label="今日新增订阅" :value="formatNumber(metrics.dailyNew)" hint="按自然日统计" />
      <DashboardKpiCard label="昨日新增订阅" :value="formatNumber(metrics.yesterdayNew)" hint="用于对比波动" />
      <DashboardKpiCard label="本月新增订阅" :value="formatNumber(metrics.monthlyNew)" hint="自然月累计" />
      <DashboardKpiCard label="累计订阅用户" :value="formatNumber(metrics.totalSubscribedUsers)" hint="历史累计去重用户" />
      <DashboardKpiCard label="当前有效订阅" :value="formatNumber(metrics.activeSubscribers)" hint="当前仍在有效期内" />
    </div>

    <div class="admin-grid-two admin-dashboard-chart-grid">
      <div>
        <div ref="trendEl" class="admin-dashboard-chart"></div>
      </div>
      <div>
        <div ref="breakdownEl" class="admin-dashboard-chart"></div>
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
import type { AdminDashboardStatus, AdminSubscriptionMetrics } from '../types'

const props = defineProps<{
  metrics: AdminSubscriptionMetrics
  status: AdminDashboardStatus
}>()

const trendEl = ref<HTMLElement | null>(null)
const breakdownEl = ref<HTMLElement | null>(null)
const trendChart = useAdminChart()
const breakdownChart = useAdminChart()

const hasData = computed(() => props.metrics.trend.length > 0 && props.metrics.planBreakdown.length > 0)

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function clearCharts() {
  trendChart.dispose()
  breakdownChart.dispose()
}

function handleResize() {
  trendChart.resize()
  breakdownChart.resize()
}

async function renderCharts() {
  if (!hasData.value || !trendEl.value || !breakdownEl.value) {
    clearCharts()
    return
  }

  await nextTick()

  trendChart.mount(trendEl.value)
  trendChart.setOption({
    color: ['#136f4b'],
    tooltip: { trigger: 'axis' },
    grid: { left: 32, right: 16, top: 28, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.metrics.trend.map((item) => item.date.slice(5)),
      axisLine: { lineStyle: { color: '#b5c4ae' } },
      axisLabel: { color: '#607164' },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#607164' },
      splitLine: { lineStyle: { color: '#e5ece1' } },
    },
    series: [{ type: 'line', smooth: true, areaStyle: { opacity: 0.16 }, data: props.metrics.trend.map((item) => item.value) }],
  })

  breakdownChart.mount(breakdownEl.value)
  breakdownChart.setOption({
    color: ['#136f4b', '#6b9f74', '#f2b45a', '#8f99b2'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#607164' } },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      itemStyle: { borderColor: '#fffdf7', borderWidth: 3 },
      label: { color: '#18261a', formatter: '{b}\n{d}%' },
      data: props.metrics.planBreakdown.map((item) => ({ name: item.label, value: item.value })),
    }],
  })
}

watch(() => [props.metrics, props.status], async () => {
  if (props.status === 'ready') {
    await renderCharts()
    return
  }

  clearCharts()
}, { deep: true, immediate: true })

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  clearCharts()
})
</script>
