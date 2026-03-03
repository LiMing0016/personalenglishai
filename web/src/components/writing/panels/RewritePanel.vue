<template>
  <div class="polish-panel">
    <template v-if="suggestions.length">
      <ul class="suggestion-list">
        <li
          v-for="err in suggestions"
          :key="err.id"
          class="suggestion-item"
          :class="{ 'suggestion-item--fixed': isFixed(err.id) }"
        >
          <div class="suggestion-header">
            <span class="suggestion-type">{{ errorTypeLabel(err.type) }} · {{ err.severity === 'major' ? '严重' : '轻微' }}</span>
            <span v-if="isFixed(err.id)" class="badge-fixed">已润色</span>
            <button
              v-else
              type="button"
              class="btn-polish"
              @click="enterPolish(err)"
            >润色</button>
          </div>
          <div v-if="err.original" class="suggestion-original">
            <span class="original-text">{{ err.original }}</span>
          </div>
          <p v-if="err.reason" class="suggestion-reason">{{ err.reason }}</p>
        </li>
      </ul>

      <!-- 润色详情视图 -->
      <div v-if="polishTarget" class="polish-detail">
        <div class="polish-detail-header">
          <span class="polish-detail-label">润色目标</span>
          <button type="button" class="polish-close-btn" @click="exitPolish">&times;</button>
        </div>
        <p class="polish-target-text">{{ polishTarget.original }}</p>
        <div class="polish-tiers">
          <button
            v-for="t in tierOptions"
            :key="t.value"
            type="button"
            class="tier-chip"
            :class="{ 'tier-chip--active': polishTier === t.value }"
            @click="selectTier(t.value)"
          >{{ t.label }}</button>
        </div>
        <div v-if="polishLoading" class="polish-loading">
          <div class="skeleton-block skeleton-polish"></div>
          <p class="loading-hint">AI 润色中...</p>
        </div>
        <div v-else-if="polishResult" class="polish-result">
          <div v-if="polishResult.polished" class="polish-result-text">
            <span class="result-label">润色结果</span>
            <p class="polished-text">{{ polishResult.polished }}</p>
          </div>
          <p v-if="polishResult.explanation" class="polish-explanation">{{ polishResult.explanation }}</p>
          <button
            v-if="polishResult.polished"
            type="button"
            class="btn btn-primary btn-sm"
            @click="applyPolish"
          >替换原文</button>
          <p v-else class="polish-error-hint">未能生成润色结果，请重试或切换档次。</p>
        </div>
      </div>

      <div class="polish-footer">
        <button type="button" class="btn btn-secondary" @click="emit('exit-correction')">完成</button>
      </div>
    </template>

    <template v-else>
      <div class="polish-empty">
        <p>没有需要润色的建议项。</p>
        <button type="button" class="btn btn-secondary" @click="emit('exit-correction')">返回</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { WritingEvaluateResponse, PolishTier, PolishResponse } from '@/api/writing'
import { polishSuggestion } from '@/api/writing'

type ErrorItem = WritingEvaluateResponse['errors'][number]

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

const tierOptions: { value: PolishTier; label: string }[] = [
  { value: 'basic', label: '基础改进' },
  { value: 'steady', label: '稳步提升' },
  { value: 'advanced', label: '进阶表达' },
  { value: 'perfect', label: '满分冲刺' },
]

const props = defineProps<{
  suggestions: ErrorItem[]
  fixedErrorIds: Set<string>
  fullEssay: string
}>()

const emit = defineEmits<{
  'apply-polish': [payload: { errorId: string; polished: string }]
  'exit-correction': []
}>()

const polishTarget = ref<ErrorItem | null>(null)
const polishTier = ref<PolishTier>('steady')
const polishLoading = ref(false)
const polishResult = ref<PolishResponse | null>(null)
let polishAbortToken = 0

function errorTypeLabel(type: string): string {
  return ERROR_TYPE_LABELS[type] ?? type
}

function isFixed(errorId: string): boolean {
  return props.fixedErrorIds.has(errorId)
}

function extractContext(original: string): string {
  const essay = props.fullEssay
  if (!essay || !original) return ''
  const idx = essay.indexOf(original)
  if (idx === -1) return ''
  const before = essay.slice(Math.max(0, idx - 50), idx)
  const after = essay.slice(idx + original.length, idx + original.length + 50)
  return (before + original + after).trim()
}

function enterPolish(err: ErrorItem) {
  polishTarget.value = err
  polishTier.value = 'steady'
  polishResult.value = null
  doPolish()
}

function exitPolish() {
  polishTarget.value = null
  polishResult.value = null
  polishLoading.value = false
  polishAbortToken++
}

function selectTier(tier: PolishTier) {
  if (tier === polishTier.value && polishResult.value) return
  polishTier.value = tier
  doPolish()
}

async function doPolish() {
  const target = polishTarget.value
  if (!target?.original) return
  const token = ++polishAbortToken
  polishLoading.value = true
  polishResult.value = null
  try {
    const res = await polishSuggestion({
      original: target.original,
      context: extractContext(target.original),
      reason: target.reason || undefined,
      tier: polishTier.value,
    })
    if (token !== polishAbortToken) return
    polishResult.value = res
  } catch {
    if (token !== polishAbortToken) return
    polishResult.value = { polished: null, explanation: '润色请求失败，请重试。' }
  } finally {
    if (token === polishAbortToken) {
      polishLoading.value = false
    }
  }
}

function applyPolish() {
  if (!polishTarget.value || !polishResult.value?.polished) return
  emit('apply-polish', {
    errorId: polishTarget.value.id,
    polished: polishResult.value.polished,
  })
  exitPolish()
}
</script>

<style scoped>
.polish-panel {
  padding: 16px;
}

.suggestion-list {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.suggestion-item {
  padding: 10px;
  border-radius: 10px;
  background: #fefce8;
  border: 1px solid #fde68a;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.suggestion-item--fixed { opacity: 0.5; }

.suggestion-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.suggestion-header .suggestion-type { flex: 1; }

.suggestion-type {
  font-size: 12px;
  color: #6b7280;
}

.badge-fixed {
  display: inline-block;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 20px;
  background: #d1fae5;
  color: #065f46;
  white-space: nowrap;
}

.btn-polish {
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid #059669;
  border-radius: 6px;
  background: #ecfdf5;
  color: #047857;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}
.btn-polish:hover { background: #d1fae5; }

.suggestion-original {
  display: flex;
  gap: 4px;
}

.original-text {
  background: #fef3c7;
  color: #78350f;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: monospace;
  font-size: 12px;
}

.suggestion-reason {
  margin: 2px 0 0;
  font-size: 12px;
  color: #78350f;
  line-height: 1.4;
}

/* ── 润色详情 ── */
.polish-detail {
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 12px;
  background: #f0fdf4;
  border: 1px solid #dcfce7;
}

.polish-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.polish-detail-label {
  font-size: 12px;
  font-weight: 600;
  color: #065f46;
}

.polish-close-btn {
  padding: 0 6px;
  font-size: 18px;
  line-height: 1;
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
}
.polish-close-btn:hover { color: #374151; }

.polish-target-text {
  margin: 0 0 10px;
  font-size: 13px;
  font-family: monospace;
  color: #374151;
  padding: 6px 8px;
  background: #fef3c7;
  border-radius: 6px;
}

.polish-tiers {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.tier-chip {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #d1d5db;
  border-radius: 20px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.tier-chip:hover { border-color: #059669; color: #059669; }
.tier-chip--active { background: #059669; border-color: #059669; color: #fff; }
.tier-chip--active:hover { background: #047857; border-color: #047857; color: #fff; }

.polish-loading { padding: 8px 0; }
.skeleton-block { border-radius: 10px; background: linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-polish { height: 60px; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.loading-hint { text-align: center; font-size: 13px; color: #6b7280; margin: 4px 0 0; }

.polish-result { margin-top: 8px; }
.polish-result-text { margin-bottom: 8px; }
.result-label { display: block; font-size: 11px; font-weight: 600; color: #065f46; margin-bottom: 4px; }
.polished-text { margin: 0; font-size: 14px; color: #065f46; line-height: 1.6; font-weight: 500; }
.polish-explanation { margin: 0 0 8px; font-size: 12px; color: #374151; line-height: 1.5; }
.polish-error-hint { margin: 0; font-size: 13px; color: #991b1b; }

.polish-footer { display: flex; gap: 10px; }
.polish-empty { padding: 32px 16px; text-align: center; font-size: 14px; color: #9ca3af; }
.polish-empty .btn { margin-top: 12px; }

.btn { padding: 10px 20px; font-size: 14px; font-weight: 500; border: none; border-radius: 12px; cursor: pointer; transition: background 0.2s; }
.btn-primary { background: #047857; color: #fff; }
.btn-primary:hover { background: #065f46; }
.btn-secondary { background: #f3f4f6; color: #374151; }
.btn-secondary:hover { background: #e5e7eb; }
.btn-sm { padding: 6px 16px; font-size: 13px; }
</style>
