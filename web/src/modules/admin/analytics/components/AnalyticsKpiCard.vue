<template>
  <article class="analytics-kpi" :class="`analytics-kpi--${kpi.tone || 'neutral'}`">
    <div class="analytics-kpi__head">
      <span>{{ kpi.label }}</span>
      <AnalyticsSourceBadge :source="kpi.source" />
    </div>
    <strong>{{ kpi.value }}</strong>
    <small>{{ kpi.delta || '当前周期' }}</small>
  </article>
</template>

<script setup lang="ts">
import type { AnalyticsKpi } from '../types/index.ts'
import AnalyticsSourceBadge from './AnalyticsSourceBadge.vue'

defineProps<{
  kpi: AnalyticsKpi
}>()
</script>

<style scoped>
.analytics-kpi {
  display: grid;
  gap: 10px;
  min-height: 118px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.analytics-kpi__head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  color: var(--admin-muted);
  font-size: 13px;
  font-weight: 700;
}

.analytics-kpi strong {
  color: #111827;
  font-size: 26px;
  line-height: 1;
}

.analytics-kpi small {
  color: var(--admin-muted);
  font-size: 12px;
}

.analytics-kpi--good {
  border-color: #bbf7d0;
}

.analytics-kpi--warning {
  border-color: #fde68a;
}

.analytics-kpi--danger {
  border-color: #fecaca;
}
</style>
