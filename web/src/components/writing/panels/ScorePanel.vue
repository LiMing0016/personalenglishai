<template>
  <div class="score-panel">
    <template v-if="evaluateResult">
      <!-- 总分 -->
      <div class="score-overview">
        <span class="score-caption">总分</span>
        <div class="score-line">
          <span class="score-value">{{ displayScore }}</span>
          <span class="score-max">/ {{ displayMaxScore }}</span>
          <span v-if="gaokaoband && !(props.examMaxScore && props.examMaxScore !== 100)" class="score-band">{{ gaokaoband }}</span>
          <span
            v-if="improvement"
            :class="['improvement-badge', improvement.delta >= 0 ? 'improved' : 'declined']"
          >{{ improvement.delta >= 0 ? '\u2191' : '\u2193' }}{{ Math.abs(improvement.delta) }}</span>
        </div>
        <span v-if="evaluateResult.gaokao_score && !(props.examMaxScore && props.examMaxScore !== 100)" class="score-hint">高考预估得分</span>
        <span v-else class="score-hint">综合评分</span>
        <p v-if="improvement" class="improvement-message">{{ improvement.message }}</p>
        <p v-if="gptErrorCount > 0" class="error-count-hint">
          检测到 <strong>{{ gptErrorCount }}</strong> 个语法/表达问题
        </p>
      </div>

      <div v-if="isFallback" class="fallback-banner">
        AI 评分暂不可用，当前展示默认评估结果，不代表真实水平。
        <button type="button" class="fallback-retry-btn" @click="emit('retry')">重新评分</button>
      </div>

      <!-- 维度等级（星星 + 字母） -->
      <div class="dimensions">
        <div v-for="row in dimensionRows" :key="row.key" class="dim-row">
          <span class="dim-label">{{ row.label }}</span>
          <span class="dim-stars">{{ row.stars }}</span>
          <span class="dim-grade">({{ row.grade }})</span>
        </div>
      </div>

      <!-- 优点 -->
      <div v-if="strengths.length" class="insight-block strengths-block">
        <span class="insight-title">优点 Strengths</span>
        <ul class="insight-list">
          <li v-for="(item, idx) in strengths" :key="`s-${idx}`" class="insight-item">
            <span class="insight-dim">{{ item.label }}</span>
            <blockquote v-if="item.quote" class="essay-quote">{{ item.quote }}</blockquote>
            <span class="insight-text">{{ item.text }}</span>
          </li>
        </ul>
      </div>

      <!-- 缺点 -->
      <div v-if="weaknesses.length" class="insight-block weaknesses-block">
        <span class="insight-title">缺点 Weaknesses</span>
        <ul class="insight-list">
          <li v-for="(item, idx) in weaknesses" :key="`w-${idx}`" class="insight-item">
            <span class="insight-dim">{{ item.label }}</span>
            <blockquote v-if="item.quote" class="essay-quote">{{ item.quote }}</blockquote>
            <span class="insight-text">{{ item.text }}</span>
          </li>
        </ul>
      </div>

      <!-- 行动建议 -->
      <div v-if="focusDetail" class="priority-focus-card">
        <span class="pf-title">重点改进</span>
        <span class="pf-dimension">{{ focusDimensionLabel }}</span>
        <p class="pf-reason">{{ focusDetail.reason }}</p>
        <p class="pf-action">{{ focusDetail.action_item }}</p>
      </div>

      <!-- 典型错误统计饼图 -->
      <div v-if="errorTypeErrors.length" class="insight-block errors-block">
        <span class="insight-title">典型错误 Error Details</span>
        <div ref="pieChartRef" class="error-pie-chart" />
        <div class="error-summary-row">
          <span class="error-summary-text">共 {{ errorTypeErrors.length }} 个错误</span>
          <span v-if="topErrorType" class="error-summary-top">
            最高占比：{{ topErrorType.name }} {{ formatPercent(topErrorType.value, errorTypeTotal) }}
          </span>
        </div>
      </div>

      <div v-if="evaluateResult && !evaluateResult.errors?.length" class="no-errors-hint">
        &#127881; 未检测到明显语法错误，写得很棒！继续保持，尝试在词汇和表达上更进一步吧！
      </div>

      <!-- 讲评 -->
      <div v-if="evaluateResult.summary" class="report-summary">
        <span class="summary-label">讲评</span>
        <p class="summary-text">{{ evaluateResult.summary }}</p>
      </div>

      <!-- 引导语法修正 -->
      <div v-if="errorTypeErrors.length" class="grammar-cta-card">
        <p class="grammar-cta-text">评分完成！建议逐条修正语法错误，提升作文质量。</p>
        <button type="button" class="grammar-cta-btn" @click="emit('start-grammar-check')">
          开始语法修正
        </button>
      </div>

    </template>

    <!-- 加载中骨架屏 -->
    <template v-else-if="submitting">
      <div class="score-loading">
        <div class="skeleton-block skeleton-score"></div>
        <div class="skeleton-block skeleton-dims"></div>
        <div class="skeleton-block skeleton-insight"></div>
        <div class="skeleton-block skeleton-insight short"></div>
        <p class="loading-hint">AI 正在评分中，请稍候...</p>
      </div>
    </template>

    <!-- 评分失败 + 重试 -->
    <template v-else-if="evaluateError">
      <div class="score-error">
        <p class="error-message">{{ evaluateError }}</p>
        <button type="button" class="btn btn-primary" @click="emit('retry')">重新评分</button>
      </div>
    </template>

    <!-- 空状态 -->
    <template v-else>
      <div class="score-empty">
        <p>提交作文后显示评分报告。</p>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, nextTick, ref, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { WritingEvaluateResponse, DimensionKey, GradeLetter } from '@/api/writing'

echarts.use([PieChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = withDefaults(
  defineProps<{
    evaluateResult?: WritingEvaluateResponse | null
    activeErrorId?: string | null
    submitting?: boolean
    evaluateError?: string | null
    dimensionLabels?: Record<string, string> | null
    examMaxScore?: number | null
  }>(),
  { evaluateResult: null, activeErrorId: null, submitting: false, evaluateError: null, dimensionLabels: null, examMaxScore: null },
)

const emit = defineEmits<{
  'error-click': [errorId: string]
  retry: []
  close: []
  'start-grammar-check': []
}>()

// ── 维度配置 ──

const DEFAULT_LABELS: Record<string, string> = {
  content_quality: '内容质量',
  task_achievement: '任务完成',
  structure: '结构',
  vocabulary: '词汇',
  grammar: '语法',
  expression: '表达',
}

const FREE_DIMENSIONS: DimensionKey[] = ['content_quality', 'structure', 'vocabulary', 'grammar', 'expression']
const EXAM_DIMENSIONS: DimensionKey[] = ['content_quality', 'task_achievement', 'structure', 'vocabulary', 'grammar', 'expression']

const GRADE_STARS: Record<GradeLetter, number> = { A: 5, B: 4, C: 3, D: 2, E: 1 }

const ERROR_TYPE_LABELS: Record<string, string> = {
  spelling: '拼写',
  morphology: '词形',
  subject_verb: '主谓一致',
  tense: '时态',
  article: '冠词',
  preposition: '介词',
  collocation: '搭配',
  syntax: '句法',
  word_choice: '用词',
  part_of_speech: '词性',
  punctuation: '标点',
  logic: '逻辑',
  grammar: '语法',
  expression: '表达',
  coherence: '连贯',
  format: '格式',
}

// ── 基础计算 ──

const effectiveMode = computed<'free' | 'exam'>(() =>
  props.evaluateResult?.mode === 'exam' ? 'exam' : 'free',
)

const dimensionOrder = computed(() =>
  effectiveMode.value === 'exam' ? EXAM_DIMENSIONS : FREE_DIMENSIONS,
)

const isFallback = computed(() => props.evaluateResult?.source === 'fallback')

const displayScore = computed(() => {
  const overall = props.evaluateResult?.score?.overall ?? 0
  // 考试模式：examMaxScore 优先，按满分换算
  if (props.examMaxScore && props.examMaxScore !== 100) {
    return Math.round(overall * props.examMaxScore / 100)
  }
  const gs = props.evaluateResult?.gaokao_score
  if (typeof gs?.score === 'number') return gs.score
  return overall
})

const displayMaxScore = computed(() => {
  // 考试模式：examMaxScore 优先
  if (props.examMaxScore && props.examMaxScore !== 100) return props.examMaxScore
  const gs = props.evaluateResult?.gaokao_score
  if (typeof gs?.max_score === 'number') return gs.max_score
  return props.examMaxScore ?? 100
})

const gptErrorCount = computed(() => props.evaluateResult?.error_count ?? props.evaluateResult?.errors?.length ?? 0)
const gaokaoband = computed(() => props.evaluateResult?.gaokao_score?.band ?? null)
const improvement = computed(() => props.evaluateResult?.improvement ?? null)

// ── 等级计算 ──

function normalizeGrade(value: unknown): GradeLetter {
  const upper = String(value ?? '').trim().toUpperCase()
  if (upper === 'A' || upper === 'B' || upper === 'C' || upper === 'D' || upper === 'E') return upper
  return 'C'
}

function scoreToGrade(value: number | undefined): GradeLetter {
  const n = Number(value ?? 60)
  if (n >= 90) return 'A'
  if (n >= 75) return 'B'
  if (n >= 60) return 'C'
  if (n >= 42) return 'D'
  return 'E'
}

const gradeMap = computed<Record<DimensionKey, GradeLetter>>(() => {
  const grades = props.evaluateResult?.grades
  if (grades && Object.keys(grades).length > 0) {
    const result = {} as Record<DimensionKey, GradeLetter>
    for (const key of dimensionOrder.value) {
      result[key] = normalizeGrade(grades[key])
    }
    return result
  }
  const dimScores = props.evaluateResult?.dimensionScores
  if (dimScores && Object.keys(dimScores).length > 0) {
    const result = {} as Record<DimensionKey, GradeLetter>
    for (const key of dimensionOrder.value) {
      result[key] = scoreToGrade(dimScores[key] as number | undefined)
    }
    return result
  }
  const result = {} as Record<DimensionKey, GradeLetter>
  for (const key of dimensionOrder.value) result[key] = 'C'
  return result
})

const dimensionRows = computed(() =>
  dimensionOrder.value.map((key) => {
    const grade = gradeMap.value[key]
    return {
      key,
      label: labelOf(key),
      grade,
      stars: '\u2B50'.repeat(GRADE_STARS[grade]),
    }
  }),
)

// ── 优点 / 缺点 ──

interface InsightItem {
  label: string
  quote?: string
  text: string
}

function labelOf(key: string): string {
  return props.dimensionLabels?.[key] ?? DEFAULT_LABELS[key] ?? key
}

const priorityFocus = computed<DimensionKey[]>(() => {
  const all = props.evaluateResult?.priority_focus ?? []
  const valid = new Set<string>(dimensionOrder.value)
  return all.filter((k): k is DimensionKey => valid.has(k))
})

const orderedAnalysisKeys = computed<string[]>(() => {
  const analysisMap = props.evaluateResult?.analysis ?? {}
  const keys: string[] = []
  const seen = new Set<string>()

  for (const key of dimensionOrder.value) {
    const rec = analysisMap[key]
    if (!rec) continue
    keys.push(key)
    seen.add(key)
  }
  for (const key of Object.keys(analysisMap)) {
    if (seen.has(key)) continue
    keys.push(key)
    seen.add(key)
  }
  return keys
})

const strengths = computed<InsightItem[]>(() => {
  const items: InsightItem[] = []
  const analysisMap = props.evaluateResult?.analysis ?? {}

  // 展示后端 analysis 中的全部 strength，避免遗漏模型返回内容
  for (const key of orderedAnalysisKeys.value) {
    const a = analysisMap[key as DimensionKey]
    const text = a?.strength?.trim()
    if (!text) continue
    const quote = a?.strength_quote?.trim() || a?.quote?.trim() || undefined
    items.push({ label: labelOf(key), quote, text })
  }

  if (!items.length) {
    const sorted = [...dimensionRows.value].sort(
      (a, b) => GRADE_STARS[b.grade] - GRADE_STARS[a.grade],
    )
    for (const row of sorted.slice(0, 2)) {
      const a = analysisMap[row.key as DimensionKey]
      const text = a?.strength?.trim() || `${row.label}相对较好（${row.grade}）。`
      const quote = a?.strength_quote?.trim() || a?.quote?.trim() || undefined
      items.push({ label: row.label, quote, text })
    }
  }

  return items
})

const weaknesses = computed<InsightItem[]>(() => {
  const items: InsightItem[] = []
  const analysisMap = props.evaluateResult?.analysis ?? {}
  const prioritySet = new Set<string>(priorityFocus.value)
  const keyOrder = [
    ...priorityFocus.value,
    ...orderedAnalysisKeys.value.filter((k) => !prioritySet.has(k)),
  ]

  // 每个维度同时展示 weakness + suggestion（若都有），确保信息完整
  for (const key of keyOrder) {
    const a = analysisMap[key as DimensionKey]
    if (!a) continue
    const weaknessText = a.weakness?.trim() ?? ''
    const suggestionText = a.suggestion?.trim() ?? ''
    const parts: string[] = []
    if (weaknessText) parts.push(`问题：${weaknessText}`)
    if (suggestionText && suggestionText !== weaknessText) parts.push(`建议：${suggestionText}`)
    const text = parts.join(' ') || (prioritySet.has(key) ? `建议优先改进${labelOf(key)}。` : '')
    if (!text) continue
    const quote = a?.weakness_quote?.trim() || a?.quote?.trim() || undefined
    items.push({ label: labelOf(key), quote, text })
  }

  if (!items.length) {
    items.push({ label: '建议', text: '建议优先改进语法与词汇，提升表达准确性。' })
  }

  return items
})
// ── 行动建议 ──

const focusDetail = computed(() => props.evaluateResult?.priority_focus_detail ?? null)
const focusDimensionLabel = computed(() => {
  const dim = focusDetail.value?.dimension
  return dim ? (DEFAULT_LABELS[dim] ?? dim) : ''
})

// ── 错误统计 ──

const errorTypeErrors = computed(() =>
  (props.evaluateResult?.errors ?? []).filter((e) => e.category !== 'suggestion'),
)

// ── ECharts 饼图 ──

const pieChartRef = ref<HTMLElement | null>(null)
const chartInstance = shallowRef<echarts.ECharts | null>(null)

const PIE_COLORS = ['#f59e0b', '#ef4444', '#6366f1', '#0ea5e9', '#10b981', '#8b5cf6', '#ec4899', '#f97316']

const errorTypePieData = computed(() => {
  const countMap = new Map<string, number>()
  for (const err of errorTypeErrors.value) {
    const label = ERROR_TYPE_LABELS[err.type] ?? err.type
    countMap.set(label, (countMap.get(label) ?? 0) + 1)
  }
  return Array.from(countMap.entries())
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
})

const errorTypeTotal = computed(() =>
  errorTypePieData.value.reduce((sum, item) => sum + item.value, 0),
)

const topErrorType = computed(() => errorTypePieData.value[0] ?? null)

function formatPercent(value: number, total: number): string {
  if (total <= 0) return '0%'
  const percent = (value / total) * 100
  return `${percent >= 10 ? percent.toFixed(0) : percent.toFixed(1)}%`
}

function renderPieChart() {
  if (!pieChartRef.value || errorTypePieData.value.length === 0) return
  if (!chartInstance.value) {
    chartInstance.value = echarts.init(pieChartRef.value)
  }

  const pieData = errorTypePieData.value
  const total = errorTypeTotal.value
  const valueMap = new Map(pieData.map((item) => [item.name, item.value]))
  const isNarrow = pieChartRef.value.clientWidth < 640

  chartInstance.value.setOption({
    title: [
      {
        text: String(total),
        left: isNarrow ? '50%' : '24%',
        top: isNarrow ? '30%' : '36%',
        textAlign: 'center',
        textStyle: {
          fontSize: 30,
          fontWeight: 700,
          color: '#1f2937',
        },
      },
      {
        text: '错误总数',
        left: isNarrow ? '50%' : '24%',
        top: isNarrow ? '46%' : '52%',
        textAlign: 'center',
        textStyle: {
          fontSize: 12,
          fontWeight: 500,
          color: '#64748b',
        },
      },
    ],
    tooltip: {
      trigger: 'item',
      formatter: (params: { name: string; value: number }) =>
        `${params.name}<br/>数量：${params.value} 个<br/>占比：${formatPercent(params.value, total)}`,
    },
    legend: isNarrow
      ? {
          bottom: 0,
          left: 'center',
          textStyle: { fontSize: 11, color: '#475569' },
          formatter: (name: string) => {
            const value = valueMap.get(name) ?? 0
            return `${name} ${value}个 ${formatPercent(value, total)}`
          },
        }
      : {
          orient: 'vertical',
          right: 0,
          top: 'middle',
          itemGap: 12,
          icon: 'roundRect',
          itemWidth: 10,
          itemHeight: 10,
          textStyle: {
            rich: {
              n: { width: 70, fontSize: 12, color: '#334155' },
              v: { width: 44, align: 'right', fontSize: 12, color: '#0f172a', fontWeight: 600 },
              p: { width: 42, align: 'right', fontSize: 12, color: '#64748b' },
            },
          },
          formatter: (name: string) => {
            const value = valueMap.get(name) ?? 0
            return `{n|${name}} {v|${value}个} {p|${formatPercent(value, total)}}`
          },
        },
    color: PIE_COLORS,
    series: [{
      type: 'pie',
      radius: isNarrow ? ['38%', '62%'] : ['45%', '70%'],
      center: isNarrow ? ['50%', '42%'] : ['24%', '45%'],
      avoidLabelOverlap: true,
      minAngle: 5,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 },
      label: {
        show: true,
        color: '#475569',
        fontSize: 11,
        formatter: (params: { percent: number }) => `${params.percent}%`,
      },
      labelLine: { show: true, length: 10, length2: 8 },
      emphasis: {
        scale: true,
        scaleSize: 6,
      },
      data: pieData,
    }],
  })
}

function handleChartResize() {
  chartInstance.value?.resize()
}

watch(errorTypePieData, () => nextTick(renderPieChart), { deep: true })
watch(() => props.evaluateResult, () => nextTick(renderPieChart))

onMounted(() => {
  window.addEventListener('resize', handleChartResize)
  nextTick(renderPieChart)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleChartResize)
  chartInstance.value?.dispose()
})
// ── activeErrorId 联动 ──

watch(
  () => props.activeErrorId,
  (id) => {
    if (!id) return
    nextTick(() => {
      const el = document.querySelector(`[data-error-id="${CSS.escape(id)}"]`)
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
  },
)
</script>

<style scoped>
.score-panel {
  padding: 16px;
}

/* ── 总分 ── */
.score-overview {
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
}

.score-caption {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.score-line {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.score-value {
  font-size: 34px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.score-max {
  font-size: 16px;
  color: #6b7280;
}

.score-band {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 20px;
  background: #d1fae5;
  color: #065f46;
  vertical-align: middle;
}

.score-hint {
  display: block;
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}

.error-count-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #dc2626;
  line-height: 1.4;
}



.improvement-badge {
  display: inline-flex;
  align-items: center;
  margin-left: 8px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 700;
  border-radius: 20px;
  vertical-align: middle;
}
.improvement-badge.improved { background: #d1fae5; color: #065f46; }
.improvement-badge.declined { background: #fee2e2; color: #991b1b; }

.improvement-message {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}

.fallback-banner {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  line-height: 1.5;
}

/* ── 维度 ── */
.dimensions {
  margin-bottom: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}

.dim-row {
  display: grid;
  grid-template-columns: 92px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #f3f4f6;
}
.dim-row:first-child { border-top: none; }

.dim-label { font-size: 13px; color: #374151; }
.dim-stars { font-size: 14px; letter-spacing: 1px; white-space: nowrap; }
.dim-grade { font-size: 12px; color: #6b7280; }

/* ── 优点 / 缺点 / 错误 通用 ── */
.insight-block {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.insight-title {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.insight-list {
  margin: 0;
  padding-left: 18px;
  color: #374151;
  font-size: 13px;
  line-height: 1.6;
}

.insight-item { margin-bottom: 10px; }
.insight-item:last-child { margin-bottom: 0; }

.insight-dim { font-size: 12px; font-weight: 600; color: #374151; }

.essay-quote {
  margin: 4px 0;
  padding: 4px 10px;
  border-left: 3px solid #d1d5db;
  background: #f9fafb;
  border-radius: 0 6px 6px 0;
  font-size: 12px;
  font-style: italic;
  color: #6b7280;
  white-space: pre-wrap;
  word-break: break-all;
}

.insight-text {
  display: block;
  font-size: 13px;
  color: #374151;
  line-height: 1.6;
  margin-top: 2px;
}

.strengths-block { background: #f0fdf4; border-color: #dcfce7; }
.weaknesses-block { background: #fff7ed; border-color: #fed7aa; }
.errors-block { background: #fefce8; border-color: #fde68a; }

.error-pie-chart {
  width: 100%;
  height: 260px;
}

.error-summary-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 8px;
  gap: 12px;
  flex-wrap: wrap;
}

.error-summary-text {
  font-size: 13px;
  color: #334155;
}

.error-summary-top {
  font-size: 12px;
  color: #64748b;
}

@media (max-width: 640px) {
  .error-pie-chart {
    height: 320px;
  }
}

/* ── 行动建议卡片 ── */
.priority-focus-card {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #c7d2fe;
  border-left: 4px solid #6366f1;
  background: #eef2ff;
}

.pf-title { display: block; font-size: 12px; font-weight: 600; color: #4338ca; margin-bottom: 4px; }
.pf-dimension { display: inline-block; padding: 1px 8px; font-size: 12px; font-weight: 600; border-radius: 20px; background: #c7d2fe; color: #3730a3; margin-bottom: 6px; }
.pf-reason { margin: 0 0 4px; font-size: 13px; color: #374151; line-height: 1.5; }
.pf-action { margin: 0; font-size: 13px; font-weight: 500; color: #4338ca; line-height: 1.5; }

/* (error card styles removed — details now in grammar panel) */

.no-errors-hint {
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f0fdf4;
  border: 1px solid #dcfce7;
  color: #166534;
  font-size: 13px;
  line-height: 1.5;
}

/* ── 讲评 ── */
.report-summary { margin-bottom: 12px; }
.summary-label { display: block; font-size: 12px; font-weight: 600; color: #6b7280; margin-bottom: 6px; }
.summary-text { margin: 0; font-size: 13px; line-height: 1.6; color: #374151; }

/* ── 语法修正引导 ── */
.grammar-cta-card {
  margin-bottom: 12px;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid #a7f3d0;
  background: #ecfdf5;
  text-align: center;
}
.grammar-cta-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: #065f46;
  line-height: 1.5;
}
.grammar-cta-btn {
  display: inline-block;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 10px;
  background: #047857;
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}
.grammar-cta-btn:hover { background: #065f46; }

.btn { padding: 10px 20px; font-size: 14px; font-weight: 500; border: none; border-radius: 12px; cursor: pointer; transition: background 0.2s; }
.btn-primary { background: #047857; color: #fff; }
.btn-primary:hover { background: #065f46; }

/* ── 加载骨架屏 ── */
.score-loading { padding: 16px 0; display: flex; flex-direction: column; gap: 12px; }
.skeleton-block { border-radius: 10px; background: linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-score { height: 80px; }
.skeleton-dims { height: 120px; }
.skeleton-insight { height: 60px; }
.skeleton-insight.short { height: 40px; width: 70%; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.loading-hint { text-align: center; font-size: 13px; color: #6b7280; margin: 4px 0 0; }

/* ── 评分失败 ── */
.score-error { padding: 32px 16px; text-align: center; }
.score-error .error-message { font-size: 14px; color: #991b1b; margin: 0 0 16px; line-height: 1.5; }

.fallback-retry-btn { display: inline-block; margin-top: 8px; padding: 4px 12px; font-size: 12px; font-weight: 500; border: 1px solid #d97706; border-radius: 8px; background: #fff; color: #92400e; cursor: pointer; transition: background 0.15s; }
.fallback-retry-btn:hover { background: #fef3c7; }

/* ── 空状态 ── */
.score-empty { padding: 32px 0; text-align: center; font-size: 14px; color: #9ca3af; }
</style>


