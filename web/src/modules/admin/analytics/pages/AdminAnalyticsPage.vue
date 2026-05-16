<template>
  <section class="admin-section analytics-page">
    <div class="admin-card analytics-hero">
      <div>
        <div class="analytics-breadcrumb">数据分析 / {{ page.label }}</div>
        <h1>{{ page.label }}</h1>
        <p>{{ page.description }}</p>
      </div>
      <AnalyticsSourceBadge :source="dataset.source" />
    </div>

    <div class="admin-card">
      <AnalyticsFilterBar :filters="filters" @apply="applyFilters" />
    </div>

    <div class="analytics-notice" :class="{ 'analytics-notice--loading': loading }">
      <strong>{{ loading ? '正在刷新数据' : dataSourceLabel(dataset.source) }}</strong>
      <span>{{ dataset.notice }}</span>
      <small>生成时间：{{ dataset.generatedAt }}</small>
    </div>

    <div class="analytics-kpi-grid">
      <AnalyticsKpiCard v-for="kpi in dataset.kpis" :key="kpi.label" :kpi="kpi" />
    </div>

    <div class="analytics-chart-grid">
      <AnalyticsChartPanel v-for="chart in dataset.charts" :key="chart.title" :chart="chart" />
    </div>

    <div class="analytics-table-grid">
      <AnalyticsDataTable v-for="table in dataset.tables" :key="table.title" :table="table" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { dataSourceLabel, findAnalyticsPage, type AnalyticsPageKey } from '../analyticsCatalog.ts'
import { defaultAnalyticsFilters, getAnalyticsDataset } from '../api/adminAnalyticsApi.ts'
import { mockAnalyticsDatasets } from '../mocks/analyticsMock.ts'
import type { AnalyticsDataset, AnalyticsFilters } from '../types/index.ts'
import AnalyticsChartPanel from '../components/AnalyticsChartPanel.vue'
import AnalyticsDataTable from '../components/AnalyticsDataTable.vue'
import AnalyticsFilterBar from '../components/AnalyticsFilterBar.vue'
import AnalyticsKpiCard from '../components/AnalyticsKpiCard.vue'
import AnalyticsSourceBadge from '../components/AnalyticsSourceBadge.vue'

const props = defineProps<{
  pageKey: AnalyticsPageKey
}>()

const filters = ref<AnalyticsFilters>(defaultAnalyticsFilters())
const dataset = ref<AnalyticsDataset>(mockAnalyticsDatasets[props.pageKey])
const loading = ref(false)
const page = computed(() => findAnalyticsPage(props.pageKey))

async function load() {
  loading.value = true
  try {
    dataset.value = await getAnalyticsDataset(props.pageKey, filters.value)
  } finally {
    loading.value = false
  }
}

function applyFilters(next: AnalyticsFilters) {
  filters.value = next
  void load()
}

watch(
  () => props.pageKey,
  () => {
    dataset.value = mockAnalyticsDatasets[props.pageKey]
    void load()
  },
  { immediate: true },
)
</script>

<style scoped>
.analytics-page {
  display: grid;
  gap: 16px;
}

.analytics-hero {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.analytics-breadcrumb {
  margin-bottom: 8px;
  color: var(--admin-muted);
  font-size: 13px;
  font-weight: 700;
}

.analytics-hero h1 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  line-height: 1.2;
}

.analytics-hero p {
  max-width: 760px;
  margin: 8px 0 0;
  color: var(--admin-muted);
  font-size: 14px;
  line-height: 1.6;
}

.analytics-notice {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 13px;
}

.analytics-notice--loading {
  opacity: 0.72;
}

.analytics-notice span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analytics-notice small {
  color: #475569;
}

.analytics-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.analytics-chart-grid,
.analytics-table-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 1180px) {
  .analytics-kpi-grid,
  .analytics-chart-grid,
  .analytics-table-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .analytics-hero,
  .analytics-notice {
    grid-template-columns: 1fr;
  }

  .analytics-kpi-grid,
  .analytics-chart-grid,
  .analytics-table-grid {
    grid-template-columns: 1fr;
  }
}
</style>
