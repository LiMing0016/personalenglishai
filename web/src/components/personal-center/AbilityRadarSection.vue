<template>
  <div class="radar-section">
    <div class="section-heading">
      <p class="section-eyebrow">能力画像</p>
      <h2 class="section-title">基于写作评测的能力画像</h2>
      <p class="section-description">当前画像来自你的写作评测数据，后续会逐步纳入阅读、词汇与表达训练。</p>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && !hasData" class="empty-state">
      <h3>完成首次写作评测，生成能力画像</h3>
      <p>我们会从任务完成、连贯衔接、语法、词汇、结构与表达多样性六个维度分析。</p>
      <RouterLink to="/app/writing">开始写作评测</RouterLink>
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

.section-heading {
  margin-bottom: 24px;
}

.section-eyebrow {
  margin: 0 0 5px;
  color: #7a8da2;
  font-size: 11px;
  font-weight: 760;
  letter-spacing: 0.12em;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.section-description {
  margin: 9px 0 0;
  color: #6f8297;
  font-size: 13px;
  line-height: 1.6;
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

.empty-state h3 {
  margin: 0;
  color: #1c334c;
  font-size: 18px;
}

.empty-state p {
  max-width: 560px;
  margin: 10px auto 20px;
  color: #73869b;
  line-height: 1.65;
}

.empty-state a {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  border-radius: 10px;
  padding: 0 15px;
  background: #087a59;
  color: #fff;
  font-weight: 700;
  text-decoration: none;
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
