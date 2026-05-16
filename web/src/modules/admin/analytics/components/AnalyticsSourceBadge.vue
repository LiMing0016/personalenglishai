<template>
  <span class="analytics-source" :class="`analytics-source--${source}`">{{ dataSourceLabel(source) }}</span>
</template>

<script setup lang="ts">
import { dataSourceLabel, type AnalyticsDataSource } from '../analyticsCatalog.ts'

defineProps<{
  source: AnalyticsDataSource
}>()
</script>

<style scoped>
.analytics-source {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.analytics-source--realtime {
  background: #dcfce7;
  color: #166534;
}

.analytics-source--mixed {
  background: #dbeafe;
  color: #1d4ed8;
}

.analytics-source--mock {
  background: #fef3c7;
  color: #92400e;
}

.analytics-source--todo {
  background: #fee2e2;
  color: #991b1b;
}
</style>
