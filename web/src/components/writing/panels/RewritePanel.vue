<template>
  <section class="rewrite-panel">
    <div class="rewrite-panel__header">
      <div>
        <h3 class="rewrite-panel__title">自动润色</h3>
        <p class="rewrite-panel__subtitle">
          按当前题目与评分标准生成一版候选稿，并在同一 rubric 下做一次安全复评。
        </p>
      </div>
    </div>

    <div v-if="locked" class="rewrite-panel__notice rewrite-panel__notice--warn">
      当前为考试首写锁定状态，暂不支持自动润色。
    </div>

    <template v-else>
      <div class="rewrite-panel__card">
        <div class="rewrite-panel__meta">
          <span class="rewrite-panel__badge">{{ writingModeLabel }}</span>
          <span v-if="studyStageLabel" class="rewrite-panel__badge">学段：{{ studyStageLabel }}</span>
          <span v-if="taskTypeLabel" class="rewrite-panel__badge">题型：{{ taskTypeLabel }}</span>
        </div>

        <div class="rewrite-panel__section">
          <div class="rewrite-panel__section-title">润色档次</div>
          <div class="rewrite-panel__tiers">
            <button
              v-for="item in tierOptions"
              :key="item.value"
              type="button"
              class="rewrite-panel__tier"
              :class="{ 'is-active': polishTier === item.value }"
              @click="selectTier(item.value)"
            >
              <span class="rewrite-panel__tier-label">{{ item.label }}</span>
              <span class="rewrite-panel__tier-desc">{{ item.desc }}</span>
            </button>
          </div>
        </div>

        <div class="rewrite-panel__section rewrite-panel__context">
          <div>
            <div class="rewrite-panel__section-title">题目内容</div>
            <p class="rewrite-panel__context-text">{{ displayedTopicContent }}</p>
          </div>
          <div>
            <div class="rewrite-panel__section-title">写作要求</div>
            <p class="rewrite-panel__context-text">{{ displayedTaskPrompt }}</p>
          </div>
        </div>

        <div class="rewrite-panel__actions">
          <button
            type="button"
            class="rewrite-panel__primary"
            :disabled="polishingAll || !polishTier || !fullEssay.trim()"
            @click="doPolishAll"
          >
            {{ polishingAll ? '润色中...' : '生成候选稿' }}
          </button>
        </div>
      </div>

      <div v-if="polishError" class="rewrite-panel__notice rewrite-panel__notice--error">
        <div>{{ polishError }}</div>
        <button type="button" class="rewrite-panel__link" @click="retryCurrentFlow">重试</button>
      </div>

      <div
        v-if="hasSummaryCard"
        class="rewrite-panel__card rewrite-panel__summary-card"
      >
        <div class="rewrite-panel__summary-head">
          <div>
            <div class="rewrite-panel__section-title">本次润色结果</div>
            <p class="rewrite-panel__summary-text">
              当前档位目标：{{ targetBandLabel }}
              <span v-if="processingModeLabel"> · {{ processingModeLabel }}</span>
            </p>
          </div>
          <button type="button" class="rewrite-panel__link" @click="summaryCollapsed = !summaryCollapsed">
            {{ summaryCollapsed ? '展开' : '收起' }}
          </button>
        </div>

        <div class="rewrite-panel__meta rewrite-panel__meta--wrap">
          <span v-if="alignmentLabel" class="rewrite-panel__badge" :class="alignmentBadgeClass">{{ alignmentLabel }}</span>
          <span v-if="routeLabel" class="rewrite-panel__badge">{{ routeLabel }}</span>
          <span v-if="baselineBandLabel" class="rewrite-panel__badge">原文档位：{{ baselineBandLabel }}</span>
          <span v-if="finalBandLabel" class="rewrite-panel__badge">候选稿档位：{{ finalBandLabel }}</span>
          <span
            v-if="accepted !== null"
            class="rewrite-panel__badge"
            :class="accepted ? 'rewrite-panel__badge--success' : 'rewrite-panel__badge--danger'"
          >
            {{ accepted ? '已通过安全复评' : '未通过安全复评' }}
          </span>
        </div>

        <div v-if="!summaryCollapsed" class="rewrite-panel__summary-body">
          <div class="rewrite-panel__summary-grid">
            <div>
              <div class="rewrite-panel__section-title">分数对比</div>
              <p class="rewrite-panel__summary-text">{{ scoreSummary }}</p>
            </div>
            <div v-if="bindingReasonLabel">
              <div class="rewrite-panel__section-title">当前约束</div>
              <p class="rewrite-panel__summary-text">{{ bindingReasonLabel }}</p>
            </div>
          </div>

          <div v-if="polishSummary?.strengths?.length" class="rewrite-panel__summary-list">
            <div class="rewrite-panel__section-title">做得好的地方</div>
            <ul>
              <li v-for="item in polishSummary.strengths" :key="`strength-${item}`">{{ item }}</li>
            </ul>
          </div>

          <div v-if="polishSummary?.improvements?.length" class="rewrite-panel__summary-list">
            <div class="rewrite-panel__section-title">仍需改进</div>
            <ul>
              <li v-for="item in polishSummary.improvements" :key="`improvement-${item}`">{{ item }}</li>
            </ul>
          </div>

          <div v-if="unmetCoreDimensionsLabel.length" class="rewrite-panel__summary-list">
            <div class="rewrite-panel__section-title">未达标核心维度</div>
            <ul>
              <li v-for="item in unmetCoreDimensionsLabel" :key="`dimension-${item}`">{{ item }}</li>
            </ul>
          </div>

          <div v-if="targetGap" class="rewrite-panel__notice" :class="accepted ? 'rewrite-panel__notice--info' : 'rewrite-panel__notice--warn'">
            {{ targetGap }}
          </div>
        </div>
      </div>

      <div
        v-if="polishedEssay"
        class="rewrite-panel__card rewrite-panel__result-card"
        :class="{ 'rewrite-panel__result-card--disabled': accepted === false }"
      >
        <div class="rewrite-panel__result-head">
          <div>
            <div class="rewrite-panel__section-title">整篇候选稿</div>
            <p class="rewrite-panel__summary-text">
              {{ accepted ? '已通过安全复评，可替换正文。' : '候选稿未通过安全复评，仅供参考。' }}
            </p>
          </div>
          <div class="rewrite-panel__actions rewrite-panel__actions--inline">
            <button
              type="button"
              class="rewrite-panel__secondary"
              :disabled="accepted === false"
              @click="applyWholeEssay"
            >
              整篇替换
            </button>
            <button type="button" class="rewrite-panel__link" @click="dismissWholeEssay">忽略</button>
          </div>
        </div>

        <article class="rewrite-panel__essay">
          <p v-for="(paragraph, index) in polishedParagraphs" :key="`paragraph-${index}`">
            {{ paragraph }}
          </p>
        </article>
      </div>

      <div v-else-if="sentences.length" class="rewrite-panel__card">
        <div class="rewrite-panel__section-title">句子级建议</div>
        <div class="rewrite-panel__sentence-list">
          <article
            v-for="(item, idx) in sentences"
            :key="`${idx}-${item.original}`"
            class="rewrite-panel__sentence-item"
          >
            <button
              type="button"
              class="rewrite-panel__sentence-toggle"
              @click="toggleSentence(idx, item.start, item.end)"
            >
              <span>原句 {{ idx + 1 }}</span>
              <span>{{ expandedIdx === idx ? '收起' : '展开' }}</span>
            </button>
            <p class="rewrite-panel__sentence-original">{{ item.original }}</p>
            <div v-if="expandedIdx === idx" class="rewrite-panel__sentence-detail">
              <p class="rewrite-panel__sentence-polished" v-html="renderDiff(item.original, item.polished)"></p>
              <p v-if="item.explanation" class="rewrite-panel__sentence-expl">{{ item.explanation }}</p>
              <button
                type="button"
                class="rewrite-panel__secondary"
                :disabled="replacedSet.has(idx)"
                @click="replaceSentence(item, idx)"
              >
                {{ replacedSet.has(idx) ? '已替换' : '应用此句' }}
              </button>
            </div>
          </article>
        </div>
      </div>

      <div v-if="!polishedEssay && !sentences.length && !polishingAll && !polishError" class="rewrite-panel__card rewrite-panel__empty">
        选择一个档位后生成候选稿。系统只会在候选稿通过同 rubric 安全复评时允许替换正文。
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  type PolishDirectionSnapshot,
  type PolishEssayResponse,
  type PolishRewriteMode,
  type PolishTier,
  type PolishTopicAlignmentStatus,
  polishEssay,
} from '@/api/writing'
import { loadPolishResult, savePolishResult } from '../editorShellStorage'

type SentenceSuggestion = {
  original: string
  polished: string
  explanation?: string
  start: number
  end: number
}

const props = defineProps<{
  docId?: string | null
  fullEssay: string
  locked?: boolean
  topicContent?: string
  taskPrompt?: string
  studyStage?: string | null
  writingMode?: 'free' | 'exam'
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
}>()

const emit = defineEmits<{
  'replace-sentence': [payload: { start: number; end: number; original: string; replacement: string; tier: PolishTier }]
  'sentence-focus': [range: { start: number; end: number } | null]
}>()

const tierOptions: Array<{ value: PolishTier; label: string; desc: string }> = [
  { value: 'basic', label: '基础改进', desc: '目标接近 Band 3' },
  { value: 'steady', label: '稳步提升', desc: '目标接近 Band 4' },
  { value: 'advanced', label: '进阶表达', desc: '目标接近高位 Band 4 / Band 5' },
  { value: 'perfect', label: '满分润色', desc: '目标对齐 Band 5' },
]

const polishTier = ref<PolishTier>('steady')
const polishingAll = ref(false)
const polishError = ref<string | null>(null)
const expandedIdx = ref<number | null>(null)
const summaryCollapsed = ref(false)
const replacedSet = ref(new Set<number>())
const polishSummary = ref<PolishEssayResponse['summary'] | null>(null)
const rubricKey = ref<string | null>(null)
const polishRubricKey = ref<string | null>(null)
const policyKey = ref<string | null>(null)
const route = ref<PolishRewriteMode | null>(null)
const topicAlignmentStatus = ref<PolishTopicAlignmentStatus | null>(null)
const rewriteMode = ref<PolishRewriteMode | null>(null)
const processingModeLabel = ref<string | null>(null)
const baselineBand = ref<string | null>(null)
const baselineScore = ref<number | null>(null)
const finalBand = ref<string | null>(null)
const finalScore = ref<number | null>(null)
const sourceBandRank = ref<number | null>(null)
const targetBandRank = ref<number | null>(null)
const accepted = ref<boolean | null>(null)
const guardTriggered = ref<boolean | null>(null)
const fallbackToOriginal = ref<boolean | null>(null)
const targetMet = ref<boolean | null>(null)
const attemptCount = ref<number | null>(null)
const targetTier = ref<PolishTier | null>(null)
const targetGap = ref<string | null>(null)
const bestEffort = ref<boolean | null>(null)
const baselineDirection = ref<PolishDirectionSnapshot | null>(null)
const finalDirection = ref<PolishDirectionSnapshot | null>(null)
const bindingReason = ref<string | null>(null)
const unmetCoreDimensions = ref<string[]>([])
const polishedEssay = ref<string | null>(null)
const sentences = ref<SentenceSuggestion[]>([])
let ignoreNextEssayChange = false
let polishAbortToken = 0

const writingModeLabel = computed(() => props.writingMode === 'exam' ? '考试模式' : '自由写作')
const studyStageLabel = computed(() => normalizeText(props.studyStage))
const taskTypeLabel = computed(() => normalizeText(props.taskType))
const displayedTopicContent = computed(() => normalizeText(props.topicContent) || '未提供题目内容。')
const displayedTaskPrompt = computed(() => normalizeText(props.taskPrompt) || '未提供写作要求。')
const polishedParagraphs = computed(() => splitParagraphs(polishedEssay.value))
const baselineBandLabel = computed(() => normalizeText(baselineBand.value))
const finalBandLabel = computed(() => normalizeText(finalBand.value))
const targetBandLabel = computed(() => targetBandRank.value ? `第 ${targetBandRank.value} 档` : '未指定')
const hasSummaryCard = computed(() => {
  return Boolean(
    processingModeLabel.value
    || baselineBand.value
    || finalBand.value
    || polishSummary.value
    || targetGap.value
    || bindingReason.value
    || unmetCoreDimensions.value.length
  )
})

const scoreSummary = computed(() => {
  const parts: string[] = []
  if (baselineScore.value != null || baselineBand.value) {
    parts.push(`原文 ${baselineScore.value ?? '-'} 分${baselineBand.value ? `（${baselineBand.value}）` : ''}`)
  }
  if (finalScore.value != null || finalBand.value) {
    parts.push(`候选稿 ${finalScore.value ?? '-'} 分${finalBand.value ? `（${finalBand.value}）` : ''}`)
  }
  return parts.join(' -> ') || '暂无复评分数。'
})

const alignmentLabel = computed(() => {
  switch (topicAlignmentStatus.value) {
    case 'aligned': return '审题结果：符合题意'
    case 'partial': return '审题结果：部分偏题'
    case 'off_topic': return '审题结果：明显偏题'
    default: return null
  }
})

const alignmentBadgeClass = computed(() => {
  switch (topicAlignmentStatus.value) {
    case 'aligned': return 'rewrite-panel__badge--success'
    case 'partial': return 'rewrite-panel__badge--warn'
    case 'off_topic': return 'rewrite-panel__badge--danger'
    default: return ''
  }
})

const routeLabel = computed(() => {
  switch (route.value) {
    case 'rubric_polish': return '处理路线：正常润色'
    case 'topic_correction_then_polish': return '处理路线：先纠偏再润色'
    case 'corrected_rewrite': return '处理路线：按题目要求重写'
    case 'fallback_polish': return '处理路线：兜底润色'
    default: return null
  }
})

const bindingReasonLabel = computed(() => {
  switch (bindingReason.value) {
    case 'topic_cap': return '当前主要受题意约束影响'
    case 'task_completion_cap': return '当前主要受任务完成度约束影响'
    case 'coverage_cap': return '当前主要受要点覆盖约束影响'
    case 'word_count_cap': return '当前主要受字数要求约束影响'
    default: return normalizeText(bindingReason.value)
  }
})

const unmetCoreDimensionsLabel = computed(() => unmetCoreDimensions.value.map(toDimensionLabel))

watch(
  () => [props.docId, props.fullEssay, props.topicContent, props.taskPrompt, props.studyStage, props.writingMode, props.taskType],
  () => {
    if (ignoreNextEssayChange) {
      ignoreNextEssayChange = false
      return
    }
    if (!tryRestoreCachedState()) {
      resetPolishState()
    }
  },
  { immediate: true },
)

function selectTier(tier: PolishTier) {
  polishTier.value = tier
}

async function doPolishAll() {
  if (!props.fullEssay.trim() || polishingAll.value) return
  polishError.value = null
  polishingAll.value = true
  const token = ++polishAbortToken
  try {
    const response = await polishEssay({
      text: props.fullEssay,
      tier: polishTier.value,
      studyStage: props.studyStage ?? null,
      writingMode: props.writingMode ?? 'free',
      topicContent: props.topicContent,
      taskPrompt: props.taskPrompt,
      taskType: props.taskType ?? null,
      minWords: props.minWords ?? null,
      recommendedMaxWords: props.recommendedMaxWords ?? null,
    })
    if (token !== polishAbortToken) return
    applyPolishResponse(response)
  } catch (error: any) {
    if (token !== polishAbortToken) return
    polishError.value = error?.response?.data?.message || error?.message || '润色失败，请稍后再试。'
  } finally {
    if (token === polishAbortToken) polishingAll.value = false
  }
}

function applyPolishResponse(response: PolishEssayResponse) {
  polishSummary.value = response.summary ?? null
  rubricKey.value = normalizeText(response.rubricKey)
  policyKey.value = normalizeText(response.policyKey)
  polishRubricKey.value = normalizeText(response.polishRubricKey)
  route.value = response.route ?? null
  topicAlignmentStatus.value = response.topicAlignmentStatus ?? null
  rewriteMode.value = response.rewriteMode ?? null
  processingModeLabel.value = normalizeText(response.processingModeLabel)
  baselineBand.value = normalizeText(response.baselineBand)
  baselineScore.value = response.baselineScore ?? null
  finalBand.value = normalizeText(response.finalBand)
  finalScore.value = response.finalScore ?? null
  sourceBandRank.value = response.sourceBandRank ?? null
  targetBandRank.value = response.targetBandRank ?? null
  accepted.value = response.accepted ?? null
  guardTriggered.value = response.guardTriggered ?? null
  fallbackToOriginal.value = response.fallbackToOriginal ?? null
  targetMet.value = response.targetMet ?? null
  attemptCount.value = response.attemptCount ?? null
  targetTier.value = response.targetTier ?? null
  targetGap.value = normalizeText(response.targetGap)
  bestEffort.value = response.bestEffort ?? null
  baselineDirection.value = response.baselineDirection ?? null
  finalDirection.value = response.finalDirection ?? null
  bindingReason.value = normalizeText(response.bindingReason)
  unmetCoreDimensions.value = Array.isArray(response.unmetCoreDimensions) ? response.unmetCoreDimensions : []
  polishedEssay.value = normalizeText(response.polishedEssay)
  sentences.value = (response.sentences ?? []).map((item) => {
    const match = locateSentence(item.original)
    return {
      original: item.original,
      polished: item.polished,
      explanation: item.explanation,
      start: match?.start ?? -1,
      end: match?.end ?? -1,
    }
  })
  replacedSet.value = new Set<number>()
  expandedIdx.value = null
  summaryCollapsed.value = false
  persistState()
}

function retryCurrentFlow() {
  void doPolishAll()
}

function applyWholeEssay() {
  if (!polishedEssay.value || accepted.value === false) return
  ignoreNextEssayChange = true
  emit('replace-sentence', {
    start: 0,
    end: props.fullEssay.length,
    original: props.fullEssay,
    replacement: polishedEssay.value,
    tier: polishTier.value,
  })
}

function dismissWholeEssay() {
  polishedEssay.value = null
  accepted.value = null
  guardTriggered.value = null
  fallbackToOriginal.value = null
  persistState()
}

function toggleSentence(index: number, start: number, end: number) {
  expandedIdx.value = expandedIdx.value === index ? null : index
  emit('sentence-focus', expandedIdx.value === index ? null : { start, end })
}

function replaceSentence(item: SentenceSuggestion, index: number) {
  if (item.start < 0 || item.end < 0) return
  replacedSet.value = new Set(replacedSet.value).add(index)
  ignoreNextEssayChange = true
  emit('replace-sentence', {
    start: item.start,
    end: item.end,
    original: item.original,
    replacement: item.polished,
    tier: polishTier.value,
  })
  persistState()
}

function persistState() {
  savePolishResult({
    tier: polishTier.value,
    summary: polishSummary.value,
    processingModeLabel: processingModeLabel.value,
    rubricKey: rubricKey.value,
    policyKey: policyKey.value,
    polishRubricKey: polishRubricKey.value,
    route: route.value,
    topicAlignmentStatus: topicAlignmentStatus.value,
    rewriteMode: rewriteMode.value,
    baselineBand: baselineBand.value,
    baselineScore: baselineScore.value,
    baselineGrades: null,
    finalBand: finalBand.value,
    finalScore: finalScore.value,
    finalGrades: null,
    sourceBandRank: sourceBandRank.value,
    targetBandRank: targetBandRank.value,
    accepted: accepted.value,
    guardTriggered: guardTriggered.value,
    fallbackToOriginal: fallbackToOriginal.value,
    targetMet: targetMet.value,
    attemptCount: attemptCount.value,
    targetTier: targetTier.value,
    targetGap: targetGap.value,
    bestEffort: bestEffort.value,
    baselineDirection: baselineDirection.value,
    finalDirection: finalDirection.value,
    bindingReason: bindingReason.value,
    unmetCoreDimensions: unmetCoreDimensions.value,
    polishedEssay: polishedEssay.value,
    sentences: sentences.value,
    replacedIndices: [...replacedSet.value],
    essaySnapshot: normalizeEssaySnapshot(props.fullEssay),
  }, cacheScope())
}

function tryRestoreCachedState(): boolean {
  const cached = loadPolishResult(cacheScope())
  if (!cached) return false
  if (cached.essaySnapshot !== normalizeEssaySnapshot(props.fullEssay)) return false
  polishTier.value = normalizeTier(cached.tier)
  polishSummary.value = (cached.summary ?? null) as PolishEssayResponse['summary'] | null
  processingModeLabel.value = normalizeText(cached.processingModeLabel)
  rubricKey.value = normalizeText(cached.rubricKey)
  policyKey.value = normalizeText(cached.policyKey)
  polishRubricKey.value = normalizeText(cached.polishRubricKey)
  route.value = (cached.route ?? null) as PolishRewriteMode | null
  topicAlignmentStatus.value = (cached.topicAlignmentStatus ?? null) as PolishTopicAlignmentStatus | null
  rewriteMode.value = (cached.rewriteMode ?? null) as PolishRewriteMode | null
  baselineBand.value = normalizeText(cached.baselineBand)
  baselineScore.value = cached.baselineScore ?? null
  finalBand.value = normalizeText(cached.finalBand)
  finalScore.value = cached.finalScore ?? null
  sourceBandRank.value = cached.sourceBandRank ?? null
  targetBandRank.value = cached.targetBandRank ?? null
  accepted.value = cached.accepted ?? null
  guardTriggered.value = cached.guardTriggered ?? null
  fallbackToOriginal.value = cached.fallbackToOriginal ?? null
  targetMet.value = cached.targetMet ?? null
  attemptCount.value = cached.attemptCount ?? null
  targetTier.value = normalizeTier(cached.targetTier ?? undefined)
  targetGap.value = normalizeText(cached.targetGap)
  bestEffort.value = cached.bestEffort ?? null
  baselineDirection.value = (cached.baselineDirection ?? null) as PolishDirectionSnapshot | null
  finalDirection.value = (cached.finalDirection ?? null) as PolishDirectionSnapshot | null
  bindingReason.value = normalizeText(cached.bindingReason)
  unmetCoreDimensions.value = Array.isArray(cached.unmetCoreDimensions) ? cached.unmetCoreDimensions : []
  polishedEssay.value = normalizeText(cached.polishedEssay)
  sentences.value = Array.isArray(cached.sentences) ? (cached.sentences as SentenceSuggestion[]) : []
  replacedSet.value = new Set(Array.isArray(cached.replacedIndices) ? cached.replacedIndices : [])
  expandedIdx.value = null
  polishError.value = null
  return true
}

function resetPolishState() {
  polishError.value = null
  polishSummary.value = null
  rubricKey.value = null
  polishRubricKey.value = null
  policyKey.value = null
  route.value = null
  topicAlignmentStatus.value = null
  rewriteMode.value = null
  processingModeLabel.value = null
  baselineBand.value = null
  baselineScore.value = null
  finalBand.value = null
  finalScore.value = null
  sourceBandRank.value = null
  targetBandRank.value = null
  accepted.value = null
  guardTriggered.value = null
  fallbackToOriginal.value = null
  targetMet.value = null
  attemptCount.value = null
  targetTier.value = null
  targetGap.value = null
  bestEffort.value = null
  baselineDirection.value = null
  finalDirection.value = null
  bindingReason.value = null
  unmetCoreDimensions.value = []
  polishedEssay.value = null
  sentences.value = []
  replacedSet.value = new Set<number>()
  expandedIdx.value = null
  summaryCollapsed.value = false
}

function cacheScope() {
  return props.docId?.trim() || null
}

function normalizeTier(value?: string | null): PolishTier {
  if (value === 'basic' || value === 'steady' || value === 'advanced' || value === 'perfect') {
    return value
  }
  return 'steady'
}

function locateSentence(original: string) {
  const haystack = props.fullEssay
  const needle = normalizeText(original)
  if (!haystack || !needle) return null
  const start = haystack.indexOf(needle)
  if (start < 0) return null
  return { start, end: start + needle.length }
}

function normalizeEssaySnapshot(text: string) {
  return normalizeText(text).replace(/\s+/g, ' ')
}

function normalizeText(value?: string | null) {
  return value?.trim() || ''
}

function splitParagraphs(text?: string | null) {
  const normalized = normalizeText(text)
  return normalized ? normalized.split(/\n{2,}/).map((item) => item.trim()).filter(Boolean) : []
}

function toDimensionLabel(key: string) {
  const labels: Record<string, string> = {
    task_achievement: '任务完成',
    content_quality: '内容质量',
    structure: '结构',
    vocabulary: '词汇',
    grammar: '语法',
    expression: '表达',
  }
  return labels[key] ?? key
}

function renderDiff(original: string, polished: string) {
  const before = escapeHtml(truncate(original, 260))
  const after = escapeHtml(truncate(polished, 260))
  if (before === after) return after
  return `<span class="rewrite-panel__diff-old">${before}</span><span class="rewrite-panel__diff-arrow"> → </span><span class="rewrite-panel__diff-new">${after}</span>`
}

function truncate(text: string, maxLength: number) {
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

function escapeHtml(text: string) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>

<style scoped>
.rewrite-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rewrite-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.rewrite-panel__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #17324d;
}

.rewrite-panel__subtitle,
.rewrite-panel__summary-text,
.rewrite-panel__context-text {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #5f6b7a;
}

.rewrite-panel__card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
  border: 1px solid #dce6ee;
  border-radius: 18px;
  background: #fff;
}

.rewrite-panel__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rewrite-panel__badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #eef5fb;
  color: #35506a;
  font-size: 12px;
  font-weight: 600;
}

.rewrite-panel__badge--success {
  background: #e8f7ee;
  color: #18794e;
}

.rewrite-panel__badge--warn {
  background: #fff4df;
  color: #9a6700;
}

.rewrite-panel__badge--danger {
  background: #fdecec;
  color: #b42318;
}

.rewrite-panel__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rewrite-panel__section-title {
  font-size: 13px;
  font-weight: 700;
  color: #17324d;
}

.rewrite-panel__tiers {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.rewrite-panel__tier {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid #d6e3ee;
  border-radius: 14px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color .2s ease, background .2s ease, transform .2s ease;
}

.rewrite-panel__tier:hover {
  border-color: #4b8fdf;
  transform: translateY(-1px);
}

.rewrite-panel__tier.is-active {
  border-color: #198754;
  background: #edf8f1;
}

.rewrite-panel__tier-label {
  font-size: 14px;
  font-weight: 700;
  color: #17324d;
}

.rewrite-panel__tier-desc {
  font-size: 12px;
  color: #5f6b7a;
  line-height: 1.5;
}

.rewrite-panel__context {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.rewrite-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.rewrite-panel__actions--inline {
  justify-content: flex-end;
}

.rewrite-panel__primary,
.rewrite-panel__secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.rewrite-panel__primary {
  background: #198754;
  color: #fff;
}

.rewrite-panel__primary:disabled,
.rewrite-panel__secondary:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.rewrite-panel__secondary {
  background: #fff;
  border-color: #cfe0ec;
  color: #17324d;
}

.rewrite-panel__link {
  border: none;
  background: transparent;
  color: #1e63b5;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}

.rewrite-panel__notice {
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.6;
}

.rewrite-panel__notice--warn {
  background: #fff6e8;
  color: #9a6700;
}

.rewrite-panel__notice--error {
  background: #fdecec;
  color: #b42318;
}

.rewrite-panel__notice--info {
  background: #eef5fb;
  color: #35506a;
}

.rewrite-panel__summary-head,
.rewrite-panel__result-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.rewrite-panel__summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.rewrite-panel__summary-list ul {
  margin: 8px 0 0;
  padding-left: 18px;
  color: #35506a;
  font-size: 13px;
  line-height: 1.7;
}

.rewrite-panel__result-card--disabled {
  border-color: #f1d0d0;
  background: #fffafa;
}

.rewrite-panel__essay {
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: #17324d;
  font-size: 14px;
  line-height: 1.8;
}

.rewrite-panel__essay p {
  margin: 0;
}

.rewrite-panel__sentence-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rewrite-panel__sentence-item {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #dce6ee;
  background: #fafcff;
}

.rewrite-panel__sentence-toggle {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 0 8px;
  border: none;
  background: transparent;
  color: #17324d;
  font-weight: 700;
  cursor: pointer;
  padding: 0;
}

.rewrite-panel__sentence-original,
.rewrite-panel__sentence-polished,
.rewrite-panel__sentence-expl {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #35506a;
}

.rewrite-panel__sentence-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rewrite-panel__diff-old {
  color: #8b95a1;
  text-decoration: line-through;
}

.rewrite-panel__diff-arrow {
  color: #5f6b7a;
}

.rewrite-panel__diff-new {
  color: #198754;
  font-weight: 600;
}

.rewrite-panel__empty {
  color: #5f6b7a;
  line-height: 1.7;
}

@media (max-width: 768px) {
  .rewrite-panel__tiers,
  .rewrite-panel__context,
  .rewrite-panel__summary-grid {
    grid-template-columns: 1fr;
  }

  .rewrite-panel__summary-head,
  .rewrite-panel__result-head {
    flex-direction: column;
  }
}
</style>
