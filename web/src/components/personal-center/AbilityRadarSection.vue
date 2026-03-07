<template>
  <div class="radar-section">
    <h2 class="section-title">能力雷达</h2>

    <!-- Empty State -->
    <div v-if="!loading && !hasData" class="empty-state">
      完成首次作文评估后，这里将展示你的能力雷达
    </div>

    <!-- Chart -->
    <div v-show="hasData" ref="chartRef" class="radar-chart"></div>

    <!-- Dimension Bars -->
    <div v-if="hasData" class="dim-bars">
      <div class="dim-bar-row" v-for="dim in dimensions" :key="dim.key">
        <span class="dim-label">{{ dim.label }}</span>
        <div class="dim-bar-track">
          <div
            class="dim-bar-fill"
            :style="{ width: ((dim.value / 10) * 100) + '%' }"
          ></div>
        </div>
        <span class="dim-value">{{ dim.value?.toFixed(1) ?? '--' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, onUnmounted } from 'vue'
import { useEventListener } from '@vueuse/core'
import * as echarts from 'echarts'
import { userApi, type AbilityProfile } from '@/api/user'

interface DimensionDisplay {
  key: string
  label: string
  value: number
}

const loading = ref(true)
const profile = ref<AbilityProfile | null>(null)
const chartRef = ref<HTMLElement | null>(null)
const chartInstance = shallowRef<echarts.ECharts | null>(null)

const dimensionMap: { key: keyof AbilityProfile; label: string }[] = [
  { key: 'taskScore', label: '任务完成' },
  { key: 'coherenceScore', label: '连贯衔接' },
  { key: 'grammarScore', label: '语法准确' },
  { key: 'vocabularyScore', label: '词汇丰富' },
  { key: 'structureScore', label: '篇章结构' },
  { key: 'varietyScore', label: '表达多样' },
]

const hasData = computed(() => {
  if (!profile.value) return false
  return dimensionMap.some((d) => (profile.value as any)[d.key] != null)
})

const dimensions = computed<DimensionDisplay[]>(() => {
  if (!profile.value) return []
  return dimensionMap.map((d) => ({
    key: d.key,
    label: d.label,
    value: (profile.value as any)[d.key] ?? 0,
  }))
})

function renderChart() {
  if (!chartRef.value || !hasData.value) return

  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value)
  }

  const indicators = dimensionMap.map((d) => ({
    name: d.label,
    max: 10,
  }))

  const values = dimensionMap.map((d) => (profile.value as any)?.[d.key] ?? 0)

  chartInstance.value.setOption({
    radar: {
      shape: 'polygon',
      indicator: indicators,
      axisName: {
        color: '#475569',
        fontSize: 13,
      },
      splitArea: {
        areaStyle: {
          color: ['#fff', '#f8fafc'],
        },
      },
      splitLine: {
        lineStyle: {
          color: '#e2e8f0',
        },
      },
      axisLine: {
        lineStyle: {
          color: '#e2e8f0',
        },
      },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: values,
            name: '能力分布',
            areaStyle: {
              color: 'rgba(4, 120, 87, 0.15)',
            },
            lineStyle: {
              color: '#047857',
              width: 2,
            },
            itemStyle: {
              color: '#047857',
            },
          },
        ],
      },
    ],
    tooltip: {
      trigger: 'item',
    },
  })
}

function handleResize() {
  chartInstance.value?.resize()
}

onMounted(async () => {
  try {
    const res = await userApi.getAbilityProfile()
    profile.value = res.data ?? null
  } catch {
    // silent
  } finally {
    loading.value = false
  }

  if (hasData.value) {
    renderChart()
  }

})

useEventListener(window, 'resize', handleResize)

onUnmounted(() => {
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
  }
})
</script>

<style scoped>
.radar-section {
  max-width: 800px;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 24px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #94a3b8;
  font-size: 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
}

.radar-chart {
  width: 100%;
  height: 380px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  margin-bottom: 24px;
}

.dim-bars {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dim-bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dim-label {
  width: 72px;
  font-size: 13px;
  color: #475569;
  flex-shrink: 0;
}

.dim-bar-track {
  flex: 1;
  height: 8px;
  background: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
}

.dim-bar-fill {
  height: 100%;
  background: #047857;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.dim-value {
  width: 36px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: #047857;
  flex-shrink: 0;
}
</style>
