<template>
  <div class="gc-panel">
    <!-- ── 考试模式首次写作锁定 ── -->
    <div v-if="locked" class="gc-locked">
      <div class="gc-locked-icon">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" stroke-width="1.5">
          <rect x="3" y="11" width="18" height="11" rx="2" />
          <path d="M7 11V7a5 5 0 0110 0v4" />
        </svg>
      </div>
      <p class="gc-locked-title">首次写作模式</p>
      <p class="gc-locked-hint">当前为考试模式下的首次写作，语法检查将在提交作文后解锁。<br/>请先独立完成写作，以便系统准确评估你的真实水平。</p>
    </div>

    <template v-else>
    <!-- ── Grammarly 风格摘要区 ── -->
    <div class="gc-summary">
      <div class="gc-summary-row">
        <span class="gc-summary-title">语法检查</span>
        <span v-if="checking" class="gc-summary-status gc-summary-status--checking">检查中</span>
        <span v-else-if="!hasContent" class="gc-summary-status gc-summary-status--idle">等待输入</span>
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
          <div class="gc-item-tags">
            <span class="gc-type">{{ displayLabel(err) }}</span>
          </div>
          <span v-if="isFixed(err.id)" class="badge-fixed">已修改</span>
          <template v-else>
            <button
              v-if="canFix(err)"
              type="button"
              class="btn-fix-single"
              @click.stop="emit('fix-error', err.id)"
            >替换</button>
            <button
              type="button"
              class="btn-dismiss"
              @click.stop="emit('dismiss-error', err.id)"
            >忽略</button>
          </template>
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
        <div v-if="err.alternatives && err.alternatives.length > 1 && !isFixed(err.id)" class="gc-alternatives">
          <span class="gc-alt-label">其他建议：</span>
          <button
            v-for="(alt, i) in err.alternatives.slice(1, 4)"
            :key="i"
            type="button"
            class="gc-alt-btn"
            @click.stop="emit('apply-suggestion', { original: err.original!, suggestion: alt })"
          >{{ alt }}</button>
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

    <!-- 未输入内容 -->
    <div v-if="!checking && !hasContent && !error" class="gc-empty">
      <p class="gc-empty-text">请先输入作文内容</p>
      <p class="gc-empty-hint">输入英文内容后，系统将自动进行语法检查。</p>
    </div>

    <!-- 无错误空状态 + 润色引导 -->
    <div v-else-if="!checking && hasContent && errors.length === 0 && !error" class="gc-empty">
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
          <button type="button" class="sg-retry-btn" @click="retrySuggestions">重试</button>
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, nextTick, ref, onMounted } from 'vue'
import type { WritingEvaluateResponse, SuggestionItem, SuggestionErrorItem } from '@/api/writing'
import { useWritingSuggestions } from '@/composables/useWritingSuggestions'
import { savePolishSuggestions, saveAppliedSuggestionIds, loadAppliedSuggestionIds } from '../editorShellStorage'
import { useWritingDraftStore } from '@/stores/writingDraftStore'

const draftStore = useWritingDraftStore()
function getScope() { return draftStore.docId || undefined }

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

/** Fallback: backend already returns Chinese, this handles any edge cases */
const LANG_CATEGORY_LABELS: Record<string, string> = {
  // Correctness → Grammar
  'Articles': '冠词', 'Conjunctions': '连词', 'Prepositions': '介词',
  'Pronouns & Determiners': '代词/限定词', 'Singular-Plural Nouns': '单复数',
  'Singular-Plural nouns': '单复数', 'Subject-Verb Agreement': '主谓一致',
  'Tense': '时态', 'Verbs': '动词', 'Verb Forms': '动词形式',
  'Word Form': '词形', 'Adjectives/Adverbs': '形容词/副词',
  'Modal Verbs': '情态动词', 'Gerunds & Infinitives': '动名词/不定式',
  'Conditional Sentences': '条件句', 'Relative Clauses': '定语从句',
  'Reported Speech': '间接引语', 'Double Negatives': '双重否定',
  'Comparatives & Superlatives': '比较级/最高级', 'Modifiers': '修饰语',
  // Correctness → Spelling
  'Spellings': '拼写', 'Spelling': '拼写', 'Spellings & Typos': '拼写/错字',
  // Correctness → Punctuation
  'Punctuation': '标点', 'Comma Usage': '逗号用法', 'Hyphenation': '连字符', 'Apostrophe': '撇号',
  // Correctness → Syntax & Vocabulary
  'Syntax': '句法', 'Other Errors': '其他错误', 'Accurate Phrasing': '精确措辞',
  'Run-on Sentences': '连写句', 'Sentence Fragments': '句子残缺',
  'Parallelism': '平行结构', 'Word Order': '语序',
  // Clarity
  'Word Choice': '用词', 'Word choice': '用词', 'Brevity': '简洁',
  'Vague Words/Phrases': '模糊表达', 'Hedge Words': '模糊词', 'Idioms/Clichés': '习语/陈词',
  // Fluency
  'Redundancy': '冗余', 'Noun Stacks': '名词堆砌', 'Plain Language': '通俗表达',
  'Enhancement': '表达优化', 'Active/Passive Voice': '主动/被动语态', 'Passive Voice': '被动语态',
  // Style
  'Capitalization & Spacing': '大小写/间距', 'Number Style': '数字格式',
  'Contractions': '缩写', 'Formal Word/Phrase Choice': '正式用词', 'Consistency': '一致性',
  // Top-level
  'Grammar': '语法', 'Correctness': '正确性', 'Clarity': '清晰度',
  'Fluency': '流畅度', 'Style': '风格', 'Inclusivity': '包容性',
  'Style Guide Compliance': '风格规范', 'Other': '其他',
}

const props = defineProps<{
  errors: ErrorItem[]
  checking: boolean
  error: string | null
  fixedErrorIds: Set<string>
  activeErrorId?: string | null
  essayText?: string
  locked?: boolean
}>()

const emit = defineEmits<{
  'fix-error': [errorId: string]
  'fix-all': []
  'dismiss-error': [errorId: string]
  'error-click': [errorId: string]
  'apply-suggestion': [payload: { original: string; suggestion: string }]
  'start-polish': []
  'gpt-errors-loaded': [errors: SuggestionErrorItem[]]
  'gpt-suggestions-loaded': [suggestions: SuggestionItem[]]
}>()

const hasContent = computed(() => (props.essayText ?? '').trim().length > 0)

function errorTypeLabel(type: string): string {
  return ERROR_TYPE_LABELS[type] ?? type
}

function langCategoryLabel(cat: string): string {
  return LANG_CATEGORY_LABELS[cat] ?? cat
}

function displayLabel(err: ErrorItem): string {
  if (err.lang_category) return langCategoryLabel(err.lang_category)
  return errorTypeLabel(err.type)
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

// 语法检查完成且无错误时显示建议区块
const showSuggestions = computed(() =>
  !props.checking && props.errors.length === 0 && !props.error,
)

const {
  suggestions,
  gptHardErrors,
  isLoading: suggestionsLoading,
  error: suggestionsError,
  loaded: suggestionsLoaded,
  canReload: canReloadSuggestions,
  reload: reloadSuggestions,
  refetch: retrySuggestions,
} = useWritingSuggestions(
  () => props.essayText ?? '',
  () => showSuggestions.value,
)

const suggestionsCollapsed = ref(false)
const appliedSuggestionIds = ref(new Set<string>())
const appliedGptErrorIds = ref(new Set<string>())

function persistAppliedIds() {
  saveAppliedSuggestionIds({
    suggestions: [...appliedSuggestionIds.value],
    gptErrors: [...appliedGptErrorIds.value],
  }, getScope())
}

onMounted(() => {
  const cached = loadAppliedSuggestionIds(getScope())
  if (cached) {
    if (cached.suggestions.length > 0) appliedSuggestionIds.value = new Set(cached.suggestions)
    if (cached.gptErrors.length > 0) appliedGptErrorIds.value = new Set(cached.gptErrors)
  }
})

// Emit GPT results to parent + cache to sessionStorage when data changes
watch([suggestions, gptHardErrors], ([sgs, errs]) => {
  if (sgs.length > 0 || errs.length > 0) {
    emit('gpt-errors-loaded', errs)
    emit('gpt-suggestions-loaded', sgs)
    savePolishSuggestions({ errors: errs, suggestions: sgs }, getScope())
  }
})

function isSuggestionApplied(id: string): boolean {
  return appliedSuggestionIds.value.has(id)
}

const usableSuggestions = computed(() => {
  return suggestions.value.filter(item => !isSuggestionApplied(item.id))
})

function applySuggestion(item: SuggestionItem) {
  const text = props.essayText ?? ''
  if (!text.includes(item.original)) return
  appliedSuggestionIds.value.add(item.id)
  persistAppliedIds()
  emit('apply-suggestion', { original: item.original, suggestion: item.suggestion })
}

function applyAllSuggestions() {
  const items = [...usableSuggestions.value]
  if (items.length === 0) return
  for (const item of items) {
    appliedSuggestionIds.value.add(item.id)
    emit('apply-suggestion', { original: item.original, suggestion: item.suggestion })
  }
  persistAppliedIds()
}

function isGptErrorApplied(id: string): boolean {
  return appliedGptErrorIds.value.has(id)
}

function applyGptError(item: SuggestionErrorItem) {
  const text = props.essayText ?? ''
  if (!item.original || !item.suggestion || !text.includes(item.original)) return
  appliedGptErrorIds.value.add(item.id)
  persistAppliedIds()
  emit('apply-suggestion', { original: item.original, suggestion: item.suggestion })
}
</script>

<style scoped>
.gc-panel {
  padding: 16px;
}

/* ── 首次写作锁定 ── */
.gc-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  text-align: center;
}

.gc-locked-icon {
  margin-bottom: 16px;
  opacity: 0.6;
}

.gc-locked-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #374151;
}

.gc-locked-hint {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
  line-height: 1.6;
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

.gc-summary-status--idle {
  color: #9ca3af;
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
  padding: 12px 12px 11px;
  border-radius: 14px;
  background: linear-gradient(180deg, #fffef7 0%, #fffdf2 100%);
  border: 1px solid #f3df9a;
  display: flex;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s, border-color 0.15s, transform 0.15s;
}
.gc-item:hover {
  background: linear-gradient(180deg, #fffdf0 0%, #fff9e8 100%);
  border-color: #ecc86a;
  box-shadow: 0 8px 18px rgba(120, 53, 15, 0.08);
  transform: translateY(-1px);
}
.gc-item--active {
  border-color: #d4a72c;
  box-shadow: 0 0 0 2px rgba(212, 167, 44, 0.18);
}
.gc-item--fixed {
  opacity: 0.68;
}
.gc-item--fixed .gc-original { text-decoration: line-through; }

.gc-item-header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.gc-item-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  flex-wrap: wrap;
}

.gc-type {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fff3cd;
  border: 1px solid #f1d48a;
  font-size: 12px;
  font-weight: 600;
  color: #8a5a10;
  letter-spacing: 0.01em;
}

.badge-fixed {
  display: inline-block;
  padding: 3px 9px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
  background: #d1fae5;
  color: #065f46;
  white-space: nowrap;
}

.btn-fix-single {
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 600;
  border: 1px solid #0f766e;
  border-radius: 999px;
  background: #ecfdf5;
  color: #0f766e;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, border-color 0.15s;
}
.btn-fix-single:hover {
  background: #d1fae5;
  border-color: #047857;
}

.btn-dismiss {
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #fff;
  color: #6b7280;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}
.btn-dismiss:hover {
  background: #f9fafb;
  border-color: #9ca3af;
  color: #374151;
}

.gc-correction {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.gc-original {
  background: #fde2e2;
  color: #9f1d1d;
  border-radius: 8px;
  padding: 3px 8px;
  font-family: monospace;
  text-decoration: line-through;
  font-size: 12px;
}

.gc-arrow { color: #6b7280; }

.gc-suggestion {
  background: #d8f5e8;
  color: #065f46;
  border-radius: 8px;
  padding: 3px 8px;
  font-family: monospace;
  font-size: 12px;
}

.gc-deletion {
  color: #dc2626;
  font-style: italic;
  font-size: 12px;
}

.gc-alternatives {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
}

.gc-alt-label {
  font-size: 11px;
  color: #9ca3af;
}

.gc-alt-btn {
  padding: 1px 6px;
  font-size: 11px;
  font-family: monospace;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #f9fafb;
  color: #374151;
  cursor: pointer;
  transition: background 0.15s;
}
.gc-alt-btn:hover {
  background: #e0f2fe;
  border-color: #7dd3fc;
  color: #0369a1;
}

.gc-reason {
  margin: 0;
  font-size: 12px;
  color: #7c4a13;
  line-height: 1.5;
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
