<template>
  <section class="analytics-panel">
    <div class="analytics-panel__head">
      <div>
        <h3>{{ chart.title }}</h3>
        <p>{{ chart.subtitle }}</p>
      </div>
      <AnalyticsSourceBadge :source="chart.source" />
    </div>
    <div v-if="chart.points.length" class="analytics-bars">
      <div v-for="point in chart.points" :key="point.label" class="analytics-bars__row">
        <span>{{ point.label }}</span>
        <div class="analytics-bars__track">
          <i :style="{ width: `${barWidth(point.value)}%` }"></i>
        </div>
        <strong>{{ formatValue(point.value) }}</strong>
        <small v-if="point.secondaryValue !== undefined">{{ formatValue(point.secondaryValue) }}</small>
      </div>
    </div>
    <div v-else class="analytics-empty">待实现数据接入后展示图表</div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AnalyticsChart } from '../types/index.ts'
import AnalyticsSourceBadge from './AnalyticsSourceBadge.vue'

const props = defineProps<{
  chart: AnalyticsChart
}>()

const maxValue = computed(() => Math.max(...props.chart.points.map((point) => point.value), 1))

function barWidth(value: number) {
  return Math.max(4, Math.round((value / maxValue.value) * 100))
}

function formatValue(value: number) {
  return Number(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.analytics-panel {
  display: grid;
  gap: 16px;
  min-height: 260px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.analytics-panel__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.analytics-panel h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
}

.analytics-panel p {
  margin: 6px 0 0;
  color: var(--admin-muted);
  font-size: 13px;
}

.analytics-bars {
  display: grid;
  gap: 12px;
  align-content: start;
}

.analytics-bars__row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) 72px 56px;
  gap: 10px;
  align-items: center;
  min-height: 28px;
  font-size: 13px;
}

.analytics-bars__row span {
  overflow: hidden;
  color: #374151;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analytics-bars__track {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef2ff;
}

.analytics-bars__track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #2563eb;
}

.analytics-bars__row strong,
.analytics-bars__row small {
  color: #111827;
  text-align: right;
}

.analytics-empty {
  display: grid;
  place-items: center;
  min-height: 160px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: var(--admin-muted);
  font-size: 13px;
}
</style>
