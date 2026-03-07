<template>
  <div class="gc-panel">
    <!-- ── Grammarly 风格摘要区 ── -->
    <div class="gc-summary">
      <div class="gc-summary-row">
        <span class="gc-summary-title">语法检查</span>
        <span v-if="checking" class="gc-summary-status gc-summary-status--checking">检查中</span>
        <span v-else-if="errors.length === 0 && !error" class="gc-summary-status gc-summary-status--clean">全部正确</span>
        <span v-else-if="errors.length > 0" class="gc-summary-score">
          {{ errors.length }} <span class="gc-summary-unit">个问题</span>
        </span>
      </div>

      <!-- 进度条 -->
      <div class="gc-bar-track">
        <div
          v-if="checking"
          class="gc-bar-fill gc-bar-indeterminate"
        />
        <div
          v-else
          class="gc-bar-fill"
          :class="barColorClass"
          :style="{ width: barPercent + '%' }"
        />
      </div>

      <!-- 修正进度 / 操作 -->
      <div v-if="!checking && errors.length > 0" class="gc-action-row">
        <span class="gc-fix-progress">{{ fixedCount }}/{{ totalFixable }} 已修正</span>
        <button
          v-if="!allFixed && totalFixable > 0"
          type="button"
          class="btn-fix-all"
          @click="emit('fix-all')"
        >一键全部订正</button>
      </div>
    </div>

    <!-- ── 错误卡片列表 ── -->
    <ul v-if="errors.length > 0" class="gc-list">
      <li
        v-for="err in errors"
        :key="err.id"
        class="gc-item"
        :class="{
          'gc-item--active': err.id === activeErrorId,
          'gc-item--fixed': isFixed(err.id),
        }"
        :data-error-id="err.id"
        @click="emit('error-click', err.id)"
      >
        <div class="gc-item-header">
          <span class="gc-type">{{ errorTypeLabel(err.type) }} · {{ err.severity === 'major' ? '严重' : '轻微' }}</span>
          <span v-if="isFixed(err.id)" class="badge-fixed">已修改</span>
          <button
            v-else-if="canFix(err)"
            type="button"
            class="btn-fix-single"
            @click.stop="emit('fix-error', err.id)"
          >替换</button>
        </div>
        <div v-if="err.original && hasValidSuggestion(err)" class="gc-correction">
          <span class="gc-original">{{ err.original }}</span>
          <span class="gc-arrow">&rarr;</span>
          <span class="gc-suggestion">{{ err.suggestion }}</span>
        </div>
        <div v-else-if="err.original && isDeletion(err)" class="gc-correction">
          <span class="gc-original">{{ err.original }}</span>
          <span class="gc-arrow">&rarr;</span>
          <span class="gc-deletion">(删除)</span>
        </div>
        <div v-else-if="err.original" class="gc-correction">
          <span class="gc-original">{{ err.original }}</span>
        </div>
        <p v-if="err.reason" class="gc-reason">{{ err.reason }}</p>
      </li>
    </ul>

    <!-- 全部修正完成 -->
    <div v-if="allFixed && !checking && errors.length > 0" class="gc-done">
      <p class="gc-done-text">全部修正完成！继续输入，系统将自动重新检查。</p>
    </div>

    <!-- API 错误 -->
    <div v-if="error" class="gc-error">
      <p class="gc-error-title">语法检查请求失败</p>
      <p class="gc-error-detail">{{ error }}</p>
    </div>

    <!-- 无错误空状态 + 润色引导 -->
    <div v-if="!checking && errors.length === 0 && !error" class="gc-empty">
      <p class="gc-empty-text">未检测到语法错误</p>
      <p class="gc-empty-hint">继续输入，系统将自动重新检查。</p>
      <div class="gc-polish-guide">
        <p class="gc-polish-text">语法无误，试试润色提升表达？</p>
        <button type="button" class="gc-polish-btn" @click="emit('start-polish')">开始润色</button>
      </div>
    </div>

    <!-- AI 改进建议：语法检查完成且无错误时自动加载 -->
    <div v-if="showSuggestions" class="sg-block">
      <div class="sg-header">
        <div class="sg-header-left">
          <span class="sg-title">改进建议 Suggestions</span>
          <span class="sg-hint">AI 检测搭配不当、中式英语等隐含问题</span>
        </div>
        <div v-if="suggestionsLoaded" class="sg-header-actions">
          <span
            class="sg-toggle"
            :class="{ 'sg-toggle--disabled': !canReloadSuggestions }"
            :title="canReloadSuggestions ? '重新检测' : '修改作文后可重新检测'"
            @click="reloadSuggestions"
          >↻</span>
          <span class="sg-toggle" @click="suggestionsCollapsed = !suggestionsCollapsed">
            {{ suggestionsCollapsed ? '展开' : '收起' }}
          </span>
        </div>
      </div>
      <template v-if="!suggestionsCollapsed">
        <div v-if="suggestionsLoading" class="sg-loading">
          <span class="gc-spinner" />
          <span class="gc-loading-text">AI 正在分析中...</span>
        </div>
        <div v-else-if="suggestionsError" class="sg-error">
          <p>{{ suggestionsError }}</p>
          <button type="button" class="sg-retry-btn" @click="loadSuggestions">重试</button>
        </div>
        <div v-else-if="gptHardErrors.length === 0 && suggestions.length === 0 && suggestionsLoaded" class="sg-empty">
          未发现隐含问题，表达很自然！
        </div>
        <template v-else>
          <!-- GPT 复检硬性错误 -->
          <template v-if="gptHardErrors.length > 0">
            <div class="sg-section-label sg-section-label--error">
              <span class="sg-section-dot sg-section-dot--error">●</span>
              AI 复检错误 ({{ gptHardErrors.length }})
            </div>
            <ul class="sg-list">
              <li v-for="item in gptHardErrors" :key="item.id" class="sg-item sg-item--error">
                <div class="sg-item-header">
                  <span class="sg-type sg-type--error">{{ errorTypeLabel(item.type) }}</span>
                  <button
                    v-if="!isGptErrorApplied(item.id)"
                    type="button"
                    class="btn-fix-single"
                    @click="applyGptError(item)"
                  >替换</button>
                  <span v-else class="sg-applied-badge">已替换</span>
                </div>
                <div class="sg-correction">
                  <span class="gc-original">{{ item.original }}</span>
                  <span class="sg-arrow">&rarr;</span>
                  <span class="gc-suggestion">{{ item.suggestion }}</span>
                </div>
                <p class="sg-reason">{{ item.reason }}</p>
              </li>
            </ul>
          </template>

          <!-- 软性建议 -->
          <template v-if="suggestions.length > 0">
            <div class="sg-section-label">
              <span class="sg-section-dot">●</span>
              改进建议 ({{ suggestions.length }})
            </div>
            <div v-if="usableSuggestions.length > 0" class="sg-action-row">
              <span class="sg-fix-progress">{{ usableSuggestions.length }} 条可替换</span>
              <button
                type="button"
                class="sg-apply-all-btn"
                @click="applyAllSuggestions"
              >一键全部替换</button>
            </div>
            <ul class="sg-list">
              <li v-for="item in suggestions" :key="item.id" class="sg-item">
                <div class="sg-item-header">
                  <span class="sg-type">{{ suggestionTypeLabel(item.type) }}</span>
                  <button
                    v-if="!isSuggestionApplied(item.id)"
                    type="button"
                    class="sg-apply-btn"
                    @click="applySuggestion(item)"
                  >替换</button>
                  <span v-else class="sg-applied-badge">已替换</span>
                </div>
                <div class="sg-correction">
                  <span class="sg-original" :class="{ 'sg-original--applied': isSuggestionApplied(item.id) }">{{ item.original }}</span>
                  <span class="sg-arrow">&rarr;</span>
                  <span class="sg-fix">{{ item.suggestion }}</span>
                </div>
                <p class="sg-reason">{{ item.reason }}</p>
              </li>
            </ul>
          </template>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, nextTick, ref, onMounted, onBeforeUnmount } from 'vue'
import type { WritingEvaluateResponse, SuggestionItem, SuggestionErrorItem } from '@/api/writing'
import { fetchWritingSuggestions } from '@/api/writing'
import { loadPolishSuggestions, savePolishSuggestions } from '../editorShellStorage'

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

const props = defineProps<{
  errors: ErrorItem[]
  checking: boolean
  error: string | null
  fixedErrorIds: Set<string>
  activeErrorId?: string | null
  essayText?: string
}>()

const emit = defineEmits<{
  'fix-error': [errorId: string]
  'fix-all': []
  'error-click': [errorId: string]
  'apply-suggestion': [payload: { original: string; suggestion: string }]
  'start-polish': []
  'gpt-errors-loaded': [errors: SuggestionErrorItem[]]
  'gpt-suggestions-loaded': [suggestions: SuggestionItem[]]
}>()

function errorTypeLabel(type: string): string {
  return ERROR_TYPE_LABELS[type] ?? type
}

function hasValidSuggestion(err: { original?: string; suggestion?: string }): boolean {
  const s = err.suggestion?.trim()
  if (!s || /^n\/?a$/i.test(s)) return false
  if (err.original && s === err.original.trim()) return false
  return true
}

function isDeletion(err: { original?: string; suggestion?: string }): boolean {
  const s = err.suggestion?.trim()
  return !!err.original && (!s || s.length === 0)
}

function isFixableError(err: ErrorItem): boolean {
  return !!err.original && (hasValidSuggestion(err) || isDeletion(err))
}

function isFixed(errorId: string): boolean {
  return props.fixedErrorIds.has(errorId)
}

function canFix(err: ErrorItem): boolean {
  return isFixableError(err) && !isFixed(err.id)
}

const fixableErrors = computed(() => props.errors.filter(isFixableError))
const fixedCount = computed(() => fixableErrors.value.filter((e) => props.fixedErrorIds.has(e.id)).length)
const totalFixable = computed(() => fixableErrors.value.length)
const allFixed = computed(() => totalFixable.value > 0 && fixedCount.value >= totalFixable.value)

// ── 进度条 ──
const barPercent = computed(() => {
  if (props.errors.length === 0) return 100
  if (totalFixable.value === 0) return 0
  return Math.round((fixedCount.value / totalFixable.value) * 100)
})

const barColorClass = computed(() => {
  if (props.errors.length === 0) return 'gc-bar--green'
  if (barPercent.value >= 100) return 'gc-bar--green'
  if (barPercent.value >= 50) return 'gc-bar--teal'
  return 'gc-bar--orange'
})

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

// ── AI 改进建议 ──

const SUGGESTION_TYPE_LABELS: Record<string, string> = {
  collocation: '搭配',
  word_choice: '用词',
  register_style: '语体',
  clarity: '清晰度',
  redundancy: '冗余',
  chinglish: '中式英语',
  uncountable: '可数/不可数',
  unnatural: '不自然表达',
}

function suggestionTypeLabel(type: string): string {
  return SUGGESTION_TYPE_LABELS[type] ?? type
}

const suggestionsLoading = ref(false)
const suggestionsLoaded = ref(false)
const suggestionsCollapsed = ref(false)
const suggestionsError = ref<string | null>(null)
const suggestions = ref<SuggestionItem[]>([])
const gptHardErrors = ref<SuggestionErrorItem[]>([])
const appliedSuggestionIds = ref(new Set<string>())
const appliedGptErrorIds = ref(new Set<string>())
let suggestionsAbort: AbortController | null = null
let lastSuggestionsText = ''  // 上次检查时的文本，防止重复检查

// 恢复缓存的润色建议
onMounted(() => {
  const cached = loadPolishSuggestions()
  if (cached) {
    if (cached.errors.length > 0) {
      gptHardErrors.value = cached.errors as SuggestionErrorItem[]
    }
    if (cached.suggestions.length > 0) {
      suggestions.value = cached.suggestions as SuggestionItem[]
    }
    if (gptHardErrors.value.length > 0 || suggestions.value.length > 0) {
      suggestionsLoaded.value = true
    }
  }
})

// 语法检查完成且无错误时显示建议区块
const showSuggestions = computed(() =>
  !props.checking && props.errors.length === 0 && !props.error,
)

function isSuggestionApplied(id: string): boolean {
  return appliedSuggestionIds.value.has(id)
}

function isSuggestionUsable(item: SuggestionItem, essayText: string): boolean {
  const original = item.original?.trim() ?? ''
  const suggestion = item.suggestion?.trim() ?? ''
  if (!original || !suggestion) return false
  if (original === suggestion) return false
  return essayText.includes(original)
}

const usableSuggestions = computed(() => {
  const text = props.essayText ?? ''
  return suggestions.value.filter(item =>
    isSuggestionUsable(item, text) && !isSuggestionApplied(item.id),
  )
})

function applySuggestion(item: SuggestionItem) {
  const text = props.essayText ?? ''
  if (!isSuggestionUsable(item, text)) return
  appliedSuggestionIds.value.add(item.id)
  emit('apply-suggestion', { original: item.original, suggestion: item.suggestion })
  // 已应用建议直接移出列表，避免反复出现
  suggestions.value = suggestions.value.filter((s) => s.id !== item.id)
}

function applyAllSuggestions() {
  const items = [...usableSuggestions.value]
  if (items.length === 0) return
  for (const item of items) {
    appliedSuggestionIds.value.add(item.id)
    emit('apply-suggestion', { original: item.original, suggestion: item.suggestion })
  }
  suggestions.value = suggestions.value.filter(s => !items.some(u => u.id === s.id))
}

async function loadSuggestions() {
  const text = props.essayText?.trim()
  if (!text) {
    suggestionsError.value = '无法获取当前作文内容'
    return
  }
  suggestionsAbort?.abort()
  suggestionsAbort = new AbortController()
  suggestionsLoading.value = true
  suggestionsError.value = null
  try {
    const res = await fetchWritingSuggestions(text, { signal: suggestionsAbort.signal })
    suggestions.value = (res.suggestions ?? []).filter((item) => isSuggestionUsable(item, text))
    // GPT 复检的硬性错误
    gptHardErrors.value = (res.errors ?? []).filter(
      (e) => e.original && e.suggestion && e.original !== e.suggestion && text.includes(e.original),
    )
    // 只 emit 面板实际展示的数据，避免面板无内容但编辑器有红线
    emit('gpt-errors-loaded', gptHardErrors.value)
    emit('gpt-suggestions-loaded', suggestions.value)
    // 缓存到 sessionStorage，刷新后可恢复
    savePolishSuggestions({ errors: gptHardErrors.value, suggestions: suggestions.value })
    lastSuggestionsText = text
    suggestionsLoaded.value = true
  } catch (e: any) {
    if (e?.name === 'CanceledError' || e?.name === 'AbortError') return
    suggestionsError.value = '获取建议失败，请重试'
  } finally {
    suggestionsLoading.value = false
  }
}

// 语法检查完成且无错误时自动加载建议（仅首次）
watch(showSuggestions, (show) => {
  if (show && !suggestionsLoaded.value && !suggestionsLoading.value) {
    loadSuggestions()
  }
})

const canReloadSuggestions = computed(() => {
  const text = props.essayText?.trim() ?? ''
  return text !== lastSuggestionsText
})

function reloadSuggestions() {
  if (!canReloadSuggestions.value) return
  suggestionsLoaded.value = false
  suggestions.value = []
  gptHardErrors.value = []
  suggestionsError.value = null
  appliedSuggestionIds.value.clear()
  appliedGptErrorIds.value.clear()
  loadSuggestions()
}

function isGptErrorApplied(id: string): boolean {
  return appliedGptErrorIds.value.has(id)
}

function applyGptError(item: SuggestionErrorItem) {
  const text = props.essayText ?? ''
  if (!item.original || !item.suggestion || !text.includes(item.original)) return
  appliedGptErrorIds.value.add(item.id)
  emit('apply-suggestion', { original: item.original, suggestion: item.suggestion })
  gptHardErrors.value = gptHardErrors.value.filter((e) => e.id !== item.id)
}


onBeforeUnmount(() => {
  suggestionsAbort?.abort()
})
</script>

<style scoped>
.gc-panel {
  padding: 16px;
}

/* ── 摘要区 ── */
.gc-summary {
  margin-bottom: 16px;
}

.gc-summary-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.gc-summary-title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.gc-summary-status {
  font-size: 13px;
  font-weight: 500;
}

.gc-summary-status--checking {
  color: #6b7280;
}

.gc-summary-status--clean {
  color: #059669;
  font-weight: 600;
}

.gc-summary-score {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.gc-summary-unit {
  font-size: 13px;
  font-weight: 400;
  color: #6b7280;
}

/* ── 进度条 ── */
.gc-bar-track {
  width: 100%;
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  overflow: hidden;
}

.gc-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}

.gc-bar--green  { background: #059669; }
.gc-bar--teal   { background: #0d9488; }
.gc-bar--orange { background: #f59e0b; }

/* 不确定进度动画 */
.gc-bar-indeterminate {
  width: 40%;
  background: linear-gradient(90deg, #0d9488, #059669);
  animation: gc-slide 1.4s ease-in-out infinite;
}

@keyframes gc-slide {
  0%   { transform: translateX(-100%); }
  50%  { transform: translateX(150%); }
  100% { transform: translateX(-100%); }
}

/* ── 修正进度行 ── */
.gc-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.gc-fix-progress {
  font-size: 13px;
  font-weight: 600;
  color: #047857;
}

.btn-fix-all {
  padding: 4px 14px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #047857;
  border-radius: 8px;
  background: #ecfdf5;
  color: #047857;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}
.btn-fix-all:hover { background: #d1fae5; }

/* ── 错误卡片 ── */
.gc-list {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.gc-item {
  padding: 10px;
  border-radius: 10px;
  background: #fefce8;
  border: 1px solid #fde68a;
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
}
.gc-item:hover { background: #fef9c3; }
.gc-item--active { box-shadow: 0 0 0 2px #fbbf24; }
.gc-item--fixed { opacity: 0.5; }
.gc-item--fixed .gc-original { text-decoration: line-through; }

.gc-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.gc-item-header .gc-type { flex: 1; }

.gc-type {
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

.btn-fix-single {
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid #6366f1;
  border-radius: 6px;
  background: #eef2ff;
  color: #4338ca;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}
.btn-fix-single:hover { background: #c7d2fe; }

.gc-correction {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.gc-original {
  background: #fee2e2;
  color: #991b1b;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: monospace;
  text-decoration: line-through;
  font-size: 12px;
}

.gc-arrow { color: #6b7280; }

.gc-suggestion {
  background: #d1fae5;
  color: #065f46;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: monospace;
  font-size: 12px;
}

.gc-deletion {
  color: #dc2626;
  font-style: italic;
  font-size: 12px;
}

.gc-reason {
  margin: 2px 0 0;
  font-size: 12px;
  color: #78350f;
  line-height: 1.4;
}

.gc-done {
  padding: 14px;
  border-radius: 12px;
  background: #f0fdf4;
  border: 1px solid #dcfce7;
  text-align: center;
}

.gc-done-text {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #065f46;
}

.gc-error {
  padding: 20px 16px;
  text-align: center;
}

.gc-error-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #dc2626;
}

.gc-error-detail {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
  word-break: break-all;
}

.gc-empty {
  padding: 24px 16px;
  text-align: center;
}

.gc-empty-text {
  margin: 0 0 8px;
  font-size: 14px;
  color: #065f46;
  font-weight: 600;
}

.gc-empty-hint {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}

.gc-polish-guide {
  margin-top: 20px;
  padding: 14px;
  border-radius: 10px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
}

.gc-polish-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: #0369a1;
  font-weight: 500;
}

.gc-polish-btn {
  padding: 6px 20px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid #0284c7;
  border-radius: 8px;
  background: #e0f2fe;
  color: #0369a1;
  cursor: pointer;
  transition: background 0.15s;
}
.gc-polish-btn:hover { background: #bae6fd; }

/* ── spinner ── */
.gc-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid #d1d5db;
  border-top-color: #0f766e;
  border-radius: 50%;
  animation: gc-spin 0.8s linear infinite;
}

@keyframes gc-spin {
  to { transform: rotate(360deg); }
}

.gc-loading-text {
  font-size: 13px;
  color: #6b7280;
}

/* ── AI 改进建议 ── */
.sg-block {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
}

.sg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: default;
}

.sg-header-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sg-title {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
}

.sg-hint {
  font-size: 11px;
  color: #8b5cf6;
  line-height: 1.4;
}

.sg-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.sg-toggle {
  font-size: 12px;
  color: #7c3aed;
  font-weight: 500;
  flex-shrink: 0;
  cursor: pointer;
}
.sg-toggle--disabled {
  color: #c4b5fd;
  cursor: not-allowed;
}

.sg-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.sg-error {
  margin-top: 10px;
  font-size: 13px;
  color: #991b1b;
}

.sg-retry-btn {
  margin-top: 6px;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #374151;
  cursor: pointer;
}
.sg-retry-btn:hover { background: #f3f4f6; }

.sg-empty {
  margin-top: 10px;
  padding: 8px 0;
  font-size: 13px;
  color: #166534;
}

.sg-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.sg-fix-progress {
  font-size: 13px;
  font-weight: 600;
  color: #5b21b6;
}

.sg-apply-all-btn {
  padding: 4px 14px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #7c3aed;
  border-radius: 8px;
  background: #ede9fe;
  color: #5b21b6;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}
.sg-apply-all-btn:hover { background: #ddd6fe; }

.sg-list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sg-item {
  padding: 8px 10px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ede9fe;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sg-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sg-type {
  font-size: 11px;
  font-weight: 600;
  color: #7c3aed;
  padding: 1px 6px;
  background: #ede9fe;
  border-radius: 4px;
}

.sg-apply-btn {
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid #7c3aed;
  border-radius: 6px;
  background: #ede9fe;
  color: #5b21b6;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}
.sg-apply-btn:hover { background: #ddd6fe; }

.sg-applied-badge {
  display: inline-block;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 20px;
  background: #d1fae5;
  color: #065f46;
  white-space: nowrap;
}

.sg-correction {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.sg-original {
  background: #fef3c7;
  color: #92400e;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: monospace;
  font-size: 12px;
}

.sg-original--applied {
  text-decoration: line-through;
  opacity: 0.6;
}

.sg-arrow { color: #6b7280; }

.sg-fix {
  background: #ddd6fe;
  color: #5b21b6;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: monospace;
  font-size: 12px;
}

.sg-reason {
  margin: 2px 0 0;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}

/* ── GPT 复检错误样式 ── */
.sg-section-label {
  margin-top: 12px;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #7c3aed;
  display: flex;
  align-items: center;
  gap: 4px;
}

.sg-section-label--error {
  color: #dc2626;
}

.sg-section-dot {
  font-size: 10px;
  color: #7c3aed;
}

.sg-section-dot--error {
  color: #ef4444;
}

.sg-item--error {
  background: #fef2f2;
  border-color: #fecaca;
}

.sg-type--error {
  background: #fee2e2;
  color: #dc2626;
}
</style>
