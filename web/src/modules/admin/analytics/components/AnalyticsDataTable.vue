<template>
  <section class="analytics-panel">
    <div class="analytics-panel__head">
      <h3>{{ table.title }}</h3>
      <AnalyticsSourceBadge :source="table.source" />
    </div>
    <div v-if="table.rows.length" class="admin-table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th v-for="column in table.columns" :key="column">{{ column }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in table.rows" :key="rowIndex">
            <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="analytics-empty">待实现数据接入后展示表格</div>
  </section>
</template>

<script setup lang="ts">
import type { AnalyticsTable } from '../types/index.ts'
import AnalyticsSourceBadge from './AnalyticsSourceBadge.vue'

defineProps<{
  table: AnalyticsTable
}>()
</script>

<style scoped>
.analytics-panel {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.analytics-panel__head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.analytics-panel h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
}

.analytics-empty {
  display: grid;
  place-items: center;
  min-height: 120px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: var(--admin-muted);
  font-size: 13px;
}
</style>
