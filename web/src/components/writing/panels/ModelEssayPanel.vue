<template>
  <div class="model-essay-panel">
    <div class="hero-card">
      <div class="hero-copy">
        <p class="hero-eyebrow">范文</p>
        <h3 class="hero-title">按当前题目与评分标准直接生成参考范文</h3>
        <p class="hero-desc">
          同时提供“优秀作文”和“满分作文”，并解释它们为什么得高分，以及你当前作文还差什么。
        </p>
      </div>
      <button
        type="button"
        class="hero-action"
        :disabled="loading || !canGenerate"
        @click="generate"
      >{{ loading ? '生成中...' : hasResult ? '重新生成' : '生成范文' }}</button>
    </div>

    <section class="context-card">
      <div class="context-header">
        <span class="context-badge">{{ writingMode === 'exam' ? '考试模式' : '自由写作' }}</span>
        <span v-if="studyStage" class="context-badge">学段：{{ studyStage }}</span>
        <span v-if="taskType" class="context-badge">题型：{{ taskType }}</span>
      </div>
      <div class="context-grid">
        <div class="context-item">
          <h4>题目内容</h4>
          <p>{{ effectiveTopicContent || '将根据当前作文自动提炼主题。' }}</p>
        </div>
        <div class="context-item">
          <h4>写作要求</h4>
          <p>{{ taskPrompt?.trim() || '未提供额外写作要求。' }}</p>
        </div>
      </div>
    </section>

    <div v-if="!canGenerate" class="empty-state">
      <p class="empty-title">当前内容还不够生成范文</p>
      <p class="empty-text">先写一段较完整的作文，再生成对照范文更有学习价值。</p>
    </div>

    <div v-else-if="loading" class="loading-state">
      <div class="skeleton card"></div>
      <div class="skeleton card"></div>
    </div>

    <div v-else-if="error" class="error-state">
      <p class="error-text">{{ error }}</p>
      <button type="button" class="secondary-btn" @click="generate">重试</button>
    </div>

    <template v-else-if="result">
      <article
        v-for="card in cards"
        :key="card.label"
        class="essay-card"
      >
        <div class="essay-card-header">
          <div>
            <p class="essay-card-label">{{ card.label }}</p>
            <p v-if="card.summary" class="essay-card-summary">{{ card.summary }}</p>
          </div>
          <div class="essay-card-actions">
            <button type="button" class="ghost-btn" @click="copyText(card.essay)">复制</button>
            <button
              type="button"
              class="primary-btn"
              :disabled="!card.essay"
              @click="replaceWholeEssay(card)"
            >替换整篇</button>
          </div>
        </div>

        <div class="essay-content">{{ card.essay }}</div>

        <section class="analysis-block">
          <h4>高分原因</h4>
          <ul>
            <li v-for="reason in card.highScoreReasons" :key="reason">{{ reason }}</li>
          </ul>
        </section>

        <section class="analysis-block guidance">
          <h4>你该怎么做</h4>
          <ul>
            <li v-for="guide in card.improvementGuidance" :key="guide">{{ guide }}</li>
          </ul>
        </section>
      </article>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { generateModelEssay, type ModelEssayCard, type WritingModelEssayResponse } from '@/api/writing'
import type { PolishTier } from '@/api/writing'
import { showToast } from '@/utils/toast'
import {
  loadModelEssayResult,
  saveModelEssayResult,
} from '@/components/writing/editorShellStorage'

const props = withDefaults(defineProps<{
  fullEssay?: string
  docId?: string | null
  topicContent?: string | null
  taskPrompt?: string | null
  studyStage?: string | null
  writingMode?: 'free' | 'exam'
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
}>(), {
  fullEssay: '',
  docId: null,
  topicContent: null,
  taskPrompt: null,
  studyStage: null,
  writingMode: 'free',
  taskType: null,
  minWords: null,
  recommendedMaxWords: null,
})

const emit = defineEmits<{
  'replace-sentence': [payload: { start: number; end: number; original: string; replacement: string; tier: PolishTier }]
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const result = ref<WritingModelEssayResponse | null>(null)

const effectiveTopicContent = computed(() => props.topicContent?.trim() || '')
const canGenerate = computed(() => props.fullEssay.trim().length >= 20)
const hasResult = computed(() => Boolean(result.value?.excellentEssay?.essay || result.value?.perfectEssay?.essay))
const scopeKey = computed(() => props.docId?.trim() || null)
const cards = computed<ModelEssayCard[]>(() => {
  if (!result.value) return []
  return [result.value.excellentEssay, result.value.perfectEssay]
})

function currentSnapshot() {
  return {
    essaySnapshot: normalizeSnapshotText(props.fullEssay),
    topicContentSnapshot: normalizeSnapshotText(effectiveTopicContent.value),
    taskPromptSnapshot: normalizeSnapshotText(props.taskPrompt),
    studyStageSnapshot: normalizeSnapshotText(props.studyStage),
    writingModeSnapshot: props.writingMode,
    taskTypeSnapshot: normalizeSnapshotText(props.taskType),
  }
}

function loadCachedResult() {
  return loadModelEssayResult(scopeKey.value) ?? (scopeKey.value ? loadModelEssayResult(null) : null)
}

function restoreCache() {
  const cached = loadCachedResult()
  if (!cached) return false
  const snapshot = currentSnapshot()
  if (
    cached.essaySnapshot !== snapshot.essaySnapshot
    || cached.topicContentSnapshot !== snapshot.topicContentSnapshot
    || cached.taskPromptSnapshot !== snapshot.taskPromptSnapshot
    || cached.studyStageSnapshot !== snapshot.studyStageSnapshot
    || cached.writingModeSnapshot !== snapshot.writingModeSnapshot
    || cached.taskTypeSnapshot !== snapshot.taskTypeSnapshot
  ) {
    return false
  }
  result.value = {
    rubricKey: cached.rubricKey ?? null,
    mode: cached.mode === 'exam' ? 'exam' : 'free',
    stage: cached.stage ?? null,
    topicContent: cached.topicContent ?? null,
    taskPrompt: cached.taskPrompt ?? null,
    excellentEssay: cached.excellentEssay as ModelEssayCard,
    perfectEssay: cached.perfectEssay as ModelEssayCard,
  }
  return true
}

function persistCache(data: WritingModelEssayResponse) {
  const snapshot = currentSnapshot()
  saveModelEssayResult({
    rubricKey: data.rubricKey ?? null,
    mode: data.mode ?? props.writingMode,
    stage: data.stage ?? props.studyStage ?? null,
    topicContent: data.topicContent ?? effectiveTopicContent.value,
    taskPrompt: data.taskPrompt ?? props.taskPrompt ?? null,
    excellentEssay: data.excellentEssay,
    perfectEssay: data.perfectEssay,
    ...snapshot,
  }, scopeKey.value)
}

onMounted(() => {
  restoreCache()
})

onActivated(() => {
  if (!result.value) {
    restoreCache()
  }
})

watch(scopeKey, (nextScope, prevScope) => {
  if (nextScope && nextScope !== prevScope && result.value) {
    persistCache(result.value)
  }
})

watch(
  () => [
    props.fullEssay,
    effectiveTopicContent.value,
    props.taskPrompt,
    props.studyStage,
    props.writingMode,
    props.taskType,
    scopeKey.value,
  ],
  () => {
    error.value = null
    const cached = loadCachedResult()
    const snapshot = currentSnapshot()
    if (
      cached
      && cached.essaySnapshot === snapshot.essaySnapshot
      && cached.topicContentSnapshot === snapshot.topicContentSnapshot
      && cached.taskPromptSnapshot === snapshot.taskPromptSnapshot
      && cached.studyStageSnapshot === snapshot.studyStageSnapshot
      && cached.writingModeSnapshot === snapshot.writingModeSnapshot
      && cached.taskTypeSnapshot === snapshot.taskTypeSnapshot
    ) {
      if (!result.value) {
        restoreCache()
      }
      return
    }
    result.value = null
  },
)

async function generate() {
  if (!canGenerate.value || loading.value) return
  loading.value = true
  error.value = null
  try {
    const response = await generateModelEssay({
      essay: props.fullEssay,
      studyStage: props.studyStage,
      writingMode: props.writingMode,
      taskType: props.taskType,
      topicContent: effectiveTopicContent.value || undefined,
      taskPrompt: props.taskPrompt?.trim() || undefined,
      minWords: props.minWords,
      recommendedMaxWords: props.recommendedMaxWords,
    })
    result.value = response
    persistCache(response)
  } catch (err: any) {
    error.value = err?.message ?? '范文生成失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    showToast('已复制范文', 'success')
  } catch {
    showToast('复制失败，请手动选择文本', 'error')
  }
}

function replaceWholeEssay(card: ModelEssayCard) {
  if (!card.essay) return
  emit('replace-sentence', {
    start: 0,
    end: props.fullEssay.length,
    original: props.fullEssay,
    replacement: card.essay,
    tier: card.label === '满分作文' ? 'perfect' : 'advanced',
  })
}

function normalizeSnapshotText(value?: string | null) {
  return (value ?? '').replace(/\s+/g, ' ').trim()
}
</script>

<style scoped>
.model-essay-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f0fdf4 0%, #eff6ff 100%);
  border: 1px solid #d1fae5;
}

.hero-eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #047857;
}

.hero-title {
  margin: 0 0 8px;
  font-size: 18px;
  line-height: 1.25;
  color: #0f172a;
}

.hero-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
  max-width: 520px;
}

.hero-action,
.primary-btn,
.secondary-btn,
.ghost-btn {
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.hero-action,
.primary-btn {
  background: #047857;
  color: #fff;
  font-weight: 700;
}

.hero-action {
  flex-shrink: 0;
  padding: 10px 16px;
  font-size: 13px;
}

.hero-action:disabled,
.primary-btn:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.context-card,
.empty-state,
.loading-state,
.error-state,
.essay-card {
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.context-card,
.empty-state,
.loading-state,
.error-state,
.essay-card {
  padding: 14px;
}

.context-header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.context-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.context-item h4,
.analysis-block h4 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.context-item p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
  white-space: pre-wrap;
}

.empty-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 700;
  color: #334155;
}

.empty-text,
.error-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}

.loading-state {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 12px;
}

.skeleton.card {
  height: 220px;
}

.essay-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.essay-card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.essay-card-label {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.essay-card-summary {
  margin: 0;
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
}

.essay-card-actions {
  display: flex;
  gap: 8px;
}

.ghost-btn {
  background: #ecfeff;
  color: #0f766e;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
}

.primary-btn,
.secondary-btn {
  padding: 8px 12px;
  font-size: 12px;
}

.secondary-btn {
  background: #e2e8f0;
  color: #334155;
  font-weight: 700;
}

.essay-content {
  padding: 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  font-size: 15px;
  line-height: 1.95;
  color: #111827;
  white-space: pre-wrap;
}

.analysis-block {
  padding: 12px 14px;
  border-radius: 12px;
  background: #f0fdf4;
  border: 1px solid #d1fae5;
}

.analysis-block.guidance {
  background: #eff6ff;
  border-color: #dbeafe;
}

.analysis-block ul {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.analysis-block li {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 900px) {
  .context-grid {
    grid-template-columns: 1fr;
  }

  .essay-card-header,
  .hero-card {
    flex-direction: column;
  }

  .essay-card-actions {
    width: 100%;
  }
}
</style>
