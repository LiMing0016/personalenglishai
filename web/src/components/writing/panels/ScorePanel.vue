<template>
  <div class="score-panel">
    <template v-if="evaluateResult">
      <!-- 总分 -->
      <div class="score-overview">
        <span class="score-caption">总分</span>
        <div class="score-line">
          <span class="score-value">{{ displayScore }}</span>
          <span class="score-max">/ {{ displayMaxScore }}</span>
          <span v-if="gaokaoband" class="score-band">{{ gaokaoband }}</span>
          <span
            v-if="improvement"
            :class="['improvement-badge', improvement.delta >= 0 ? 'improved' : 'declined']"
          >{{ improvement.delta >= 0 ? '\u2191' : '\u2193' }}{{ Math.abs(improvement.delta) }}</span>
        </div>
        <span v-if="evaluateResult.gaokao_score" class="score-hint">高考预估得分</span>
        <span v-else class="score-hint">综合评分</span>
        <p v-if="improvement" class="improvement-message">{{ improvement.message }}</p>
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
          <span v-if="row.score != null" class="dim-score">{{ row.score }}</span>
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
          <button type="button" class="btn-view-details" @click="emit('view-error-details')">查看详情</button>
        </div>
      </div>

      <div v-if="evaluateResult && !evaluateResult.errors?.length" class="no-errors-hint">
        未发现典型错误，继续保持！
      </div>

      <!-- 讲评 -->
      <div v-if="evaluateResult.summary" class="report-summary">
        <span class="summary-label">讲评</span>
        <p class="summary-text">{{ evaluateResult.summary }}</p>
      </div>

      <div class="actions">
        <button type="button" class="btn btn-primary" @click="emit('start-fix')">开始订正</button>
        <button type="button" class="btn btn-secondary" @click="emit('close')">暂不订正</button>
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
  }>(),
  { evaluateResult: null, activeErrorId: null, submitting: false, evaluateError: null, dimensionLabels: null },
)

const emit = defineEmits<{
  'start-fix': []
  'error-click': [errorId: string]
  'view-error-details': []
  retry: []
  close: []
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
  const gs = props.evaluateResult?.gaokao_score
  if (typeof gs?.score === 'number') return gs.score
  return props.evaluateResult?.score?.overall ?? 0
})

const displayMaxScore = computed(() => {
  const gs = props.evaluateResult?.gaokao_score
  if (typeof gs?.max_score === 'number') return gs.max_score
  return 100
})

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
    const dimScores = props.evaluateResult?.dimensionScores
    const score = dimScores?.[key] ?? null
    return {
      key,
      label: labelOf(key),
      grade,
      stars: '\u2B50'.repeat(GRADE_STARS[grade]),
      score,
    }
  }),
)

// ── 优点 / 缺点 ──

interface InsightItem {
  label: string
  quote?: string
  text: string
}

function labelOf(key: DimensionKey): string {
  return props.dimensionLabels?.[key] ?? DEFAULT_LABELS[key] ?? key
}

const priorityFocus = computed<DimensionKey[]>(() => {
  const all = props.evaluateResult?.priority_focus ?? []
  const valid = new Set<string>(dimensionOrder.value)
  return all.filter((k): k is DimensionKey => valid.has(k))
})

const strengths = computed<InsightItem[]>(() => {
  const items: InsightItem[] = []
  const weakSet = new Set(priorityFocus.value)
  const analysisMap = props.evaluateResult?.analysis ?? {}

  for (const key of dimensionOrder.value) {
    const grade = gradeMap.value[key]
    if (weakSet.has(key) || (grade !== 'A' && grade !== 'B')) continue
    const a = analysisMap[key]
    const text = a?.strength?.trim()
      || `${labelOf(key)}表现稳定（${grade}）。`
    const quote = a?.strength_quote?.trim() || a?.quote?.trim() || undefined
    items.push({ label: labelOf(key), quote, text })
    if (items.length >= 3) break
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
  const seen = new Set<DimensionKey>()
  const analysisMap = props.evaluateResult?.analysis ?? {}

  for (const key of priorityFocus.value) {
    const a = analysisMap[key]
    const text = (a?.suggestion ?? a?.weakness)?.trim()
      || `建议优先改进${labelOf(key)}。`
    const quote = a?.weakness_quote?.trim() || a?.quote?.trim() || undefined
    items.push({ label: labelOf(key), quote, text })
    seen.add(key)
  }

  for (const key of Object.keys(analysisMap) as DimensionKey[]) {
    if (seen.has(key)) continue
    const a = analysisMap[key]
    const text = (a?.suggestion ?? a?.weakness)?.trim()
    if (!text) continue
    const quote = a?.weakness_quote?.trim() || a?.quote?.trim() || undefined
    items.push({ label: labelOf(key), quote, text })
    seen.add(key)
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

function renderPieChart() {
  if (!pieChartRef.value || errorTypePieData.value.length === 0) return
  if (!chartInstance.value) {
    chartInstance.value = echarts.init(pieChartRef.value)
  }
  chartInstance.value.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, left: 'center', textStyle: { fontSize: 11 } },
    color: PIE_COLORS,
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold' } },
      data: errorTypePieData.value,
    }],
  })
}

watch(errorTypePieData, () => nextTick(renderPieChart), { deep: true })
watch(() => props.evaluateResult, () => nextTick(renderPieChart))

onMounted(() => nextTick(renderPieChart))
onBeforeUnmount(() => { chartInstance.value?.dispose() })

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
  grid-template-columns: 92px 1fr auto auto;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #f3f4f6;
}
.dim-row:first-child { border-top: none; }

.dim-label { font-size: 13px; color: #374151; }
.dim-stars { font-size: 14px; letter-spacing: 1px; white-space: nowrap; }
.dim-score { font-size: 12px; font-weight: 600; color: #374151; }
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
  height: 220px;
}

.error-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.error-summary-text {
  font-size: 13px;
  color: #6b7280;
}

.btn-view-details {
  padding: 4px 14px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #d97706;
  border-radius: 8px;
  background: #fffbeb;
  color: #92400e;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}
.btn-view-details:hover { background: #fef3c7; }

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

/* ── 操作按钮 ── */
.actions { display: flex; gap: 10px; }
.btn { padding: 10px 20px; font-size: 14px; font-weight: 500; border: none; border-radius: 12px; cursor: pointer; transition: background 0.2s; }
.btn-primary { background: #047857; color: #fff; }
.btn-primary:hover { background: #065f46; }
.btn-secondary { background: #f3f4f6; color: #374151; }
.btn-secondary:hover { background: #e5e7eb; }

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
