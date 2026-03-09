<template>
  <div class="translate-panel">
    <!-- Mode Toggle -->
    <div class="mode-toggle">
      <button
        type="button"
        class="mode-btn"
        :class="{ 'mode-btn--active': mode === 'full' }"
        @click="mode = 'full'"
      >全文翻译</button>
      <button
        type="button"
        class="mode-btn"
        :class="{ 'mode-btn--active': mode === 'detailed' }"
        @click="mode = 'detailed'"
      >逐句精讲</button>
    </div>

    <!-- Translate Button -->
    <div class="action-bar">
      <button
        type="button"
        class="btn btn-primary"
        :disabled="!canTranslate"
        @click="doTranslate"
      >{{ loading ? '翻译中...' : '开始翻译' }}</button>
    </div>

    <!-- Stale hint -->
    <p v-if="isStale && !loading" class="stale-hint">
      内容已变更，点击「开始翻译」重新翻译
    </p>

    <!-- Loading -->
    <div v-if="loading" class="translate-loading">
      <div class="skeleton-block skeleton-long"></div>
      <div class="skeleton-block skeleton-short"></div>
      <p class="loading-hint">AI 正在翻译，请稍候...</p>
    </div>

    <!-- Error -->
    <div v-else-if="errorMsg" class="translate-error">
      <p class="error-text">{{ errorMsg }}</p>
      <button type="button" class="btn btn-primary btn-sm" @click="doTranslate">重试</button>
    </div>

    <!-- Has data -->
    <template v-else-if="hasResult">
      <!-- Full mode result -->
      <div v-if="mode === 'full'" class="full-result">
        <pre class="full-text">{{ fullTranslation }}</pre>
      </div>

      <!-- Detailed mode result -->
      <ul v-else class="sentence-list">
        <li
          v-for="(s, idx) in sentences"
          :key="idx"
          class="sentence-card"
          :class="{ 'sentence-card--expanded': expandedIdx === idx }"
        >
          <div class="sentence-header" @click="toggleExpand(idx)">
            <span class="sentence-dot"></span>
            <span class="sentence-preview">{{ truncate(s.english, 50) }}</span>
            <span class="sentence-arrow">{{ expandedIdx === idx ? '▾' : '▸' }}</span>
          </div>
          <div v-if="expandedIdx === idx" class="sentence-body">
            <div class="sentence-english">
              <span class="label-tag">原文：</span>{{ s.english }}
            </div>
            <div class="sentence-chinese">{{ s.chinese }}</div>
            <div v-if="s.structure" class="sentence-structure">
              <div class="section-label">📐 句子结构</div>
              <p class="section-text">{{ s.structure }}</p>
            </div>
            <div v-if="s.highlights && s.highlights.length > 0" class="sentence-highlights">
              <div class="section-label">💡 重点表达</div>
              <ul class="highlight-list">
                <li v-for="(h, hi) in s.highlights" :key="hi" class="highlight-item">
                  <span class="hl-word">{{ h.word }}</span>
                  <span v-if="h.meaning" class="hl-meaning">{{ h.meaning }}</span>
                  <p v-if="h.detail" class="hl-detail">{{ h.detail }}</p>
                </li>
              </ul>
            </div>
          </div>
        </li>
      </ul>
    </template>

    <!-- Empty state -->
    <div v-else class="translate-empty">
      <p>点击「开始翻译」将当前作文翻译为中文</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { translateEssay } from '@/api/writing'
import type { SentenceTranslation } from '@/api/writing'
import { useWritingDraftStore } from '@/stores/writingDraftStore'
import { saveTranslateResult, loadTranslateResult } from '@/components/writing/editorShellStorage'
import { showToast } from '@/utils/toast'

const emit = defineEmits<{
  'sentence-focus': [range: { start: number; end: number } | null]
}>()

const draftStore = useWritingDraftStore()

const mode = ref<'full' | 'detailed'>('full')
const loading = ref(false)
const errorMsg = ref<string | null>(null)
const fullTranslation = ref<string | null>(null)
const sentences = ref<SentenceTranslation[]>([])
const expandedIdx = ref<number | null>(null)
let lastTranslatedText = ''
let lastRequestTime = 0
let abortController: AbortController | null = null

// 恢复缓存
onMounted(() => {
  const cached = loadTranslateResult(draftStore.docId)
  if (cached && cached.sentences.length > 0) {
    mode.value = cached.mode
    fullTranslation.value = cached.fullTranslation
    sentences.value = cached.sentences as SentenceTranslation[]
    lastTranslatedText = cached.lastTranslatedText
  }
})

const canTranslate = computed(() => {
  return !loading.value && draftStore.draftText.trim().length >= 10
})

const isStale = computed(() => {
  if (!lastTranslatedText) return false
  return draftStore.draftText.trim() !== lastTranslatedText
})

const hasResult = computed(() => {
  return fullTranslation.value !== null || sentences.value.length > 0
})

async function doTranslate() {
  const now = Date.now()
  if (now - lastRequestTime < 3000) {
    showToast('操作太频繁，请稍候', 'info')
    return
  }

  const text = draftStore.draftText.trim()
  if (!text || text.length < 10) {
    showToast('作文内容太少，至少需要 10 个字符', 'info')
    return
  }

  lastRequestTime = now
  abortController?.abort()
  abortController = new AbortController()
  loading.value = true
  errorMsg.value = null

  try {
    // 只调一次 GPT（按当前模式），后端返回 translation/sentences
    const res = await translateEssay(
      { text, mode: mode.value },
      { signal: abortController.signal },
    )

    sentences.value = res.sentences ?? []
    fullTranslation.value = res.translation
      ?? (buildFullTranslation(sentences.value, text) || null)

    if (sentences.value.length === 0 && !fullTranslation.value) {
      errorMsg.value = 'AI 未返回翻译结果，请重试'
    }

    lastTranslatedText = text
    saveTranslateResult({
      mode: mode.value,
      fullTranslation: fullTranslation.value,
      sentences: sentences.value,
      lastTranslatedText: text,
    }, draftStore.docId)
  } catch (e: any) {
    if (e?.name === 'CanceledError' || e?.name === 'AbortError' || e?.code === 'ERR_CANCELED') return
    errorMsg.value = e?.message ?? '翻译失败，请重试'
    showToast(errorMsg.value!, 'error')
  } finally {
    loading.value = false
  }
}

/** 按原文段落结构拼接中文翻译 */
function buildFullTranslation(items: SentenceTranslation[], originalText: string): string {
  if (items.length === 0) return ''
  const paragraphs = originalText.split(/\n\s*\n/)
  if (paragraphs.length <= 1) {
    return items.map((s) => s.chinese).join('')
  }
  // 分配句子到段落
  const result: string[] = []
  let cursor = 0
  for (const para of paragraphs) {
    const paraChinese: string[] = []
    while (cursor < items.length) {
      const eng = items[cursor].english
      if (para.includes(eng)) {
        paraChinese.push(items[cursor].chinese)
        cursor++
      } else {
        break
      }
    }
    if (paraChinese.length > 0) {
      result.push(paraChinese.join(''))
    }
  }
  // 剩余未匹配的句子追加到最后
  while (cursor < items.length) {
    result.push(items[cursor].chinese)
    cursor++
  }
  return result.join('\n\n')
}

function toggleExpand(idx: number) {
  if (expandedIdx.value === idx) {
    expandedIdx.value = null
    emit('sentence-focus', null)
  } else {
    expandedIdx.value = idx
    const s = sentences.value[idx]
    if (s) {
      const text = draftStore.draftText
      const start = text.indexOf(s.english)
      if (start !== -1) {
        emit('sentence-focus', { start, end: start + s.english.length })
      }
    }
  }
}

function truncate(text: string, maxLen: number): string {
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}
</script>

<style scoped>
.translate-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── Mode Toggle ── */
.mode-toggle {
  display: flex;
  gap: 0;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  overflow: hidden;
}

.mode-btn {
  flex: 1;
  padding: 8px 0;
  font-size: 13px;
  font-weight: 600;
  border: none;
  background: #fff;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.15s;
}

.mode-btn:first-child {
  border-right: 1px solid #d1d5db;
}

.mode-btn--active {
  background: #0f766e;
  color: #fff;
}

.mode-btn:hover:not(.mode-btn--active) {
  background: #f3f4f6;
}

/* ── Action Bar ── */
.action-bar {
  display: flex;
}

.btn {
  height: 36px;
  border: none;
  border-radius: 10px;
  padding: 0 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.btn-primary {
  background: #0f766e;
  color: #fff;
  width: 100%;
}

.btn-primary:hover:not(:disabled) {
  background: #115e59;
}

.btn-sm {
  height: 30px;
  padding: 0 14px;
  font-size: 12px;
}

/* ── Stale hint ── */
.stale-hint {
  margin: 0;
  font-size: 12px;
  color: #d97706;
  text-align: center;
}

/* ── Loading ── */
.translate-loading {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-block {
  border-radius: 10px;
  background: linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-long { height: 60px; }
.skeleton-short { height: 40px; width: 70%; }

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.loading-hint {
  text-align: center;
  font-size: 13px;
  color: #6b7280;
  margin: 4px 0 0;
}

/* ── Error ── */
.translate-error {
  padding: 16px;
  text-align: center;
}

.error-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: #991b1b;
}

/* ── Full mode result ── */
.full-result {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  padding: 14px;
}

.full-text {
  margin: 0;
  font-size: 14px;
  color: #1f2937;
  line-height: 1.8;
  white-space: pre-wrap;
  font-family: inherit;
}

/* ── Detailed mode: sentence list ── */
.sentence-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sentence-card {
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
  overflow: hidden;
  transition: border-color 0.15s;
}

.sentence-card--expanded {
  border-color: #93c5fd;
}

.sentence-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  user-select: none;
}

.sentence-header:hover {
  background: #f9fafb;
}

.sentence-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #3b82f6;
}

.sentence-preview {
  flex: 1;
  font-size: 13px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sentence-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: #9ca3af;
}

.sentence-body {
  padding: 0 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sentence-english {
  font-size: 14px;
  color: #1f2937;
  line-height: 1.6;
}

.label-tag {
  font-size: 12px;
  font-weight: 600;
  color: #3b82f6;
  margin-right: 4px;
}

.sentence-chinese {
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
  padding: 8px 10px;
  background: #f0fdf4;
  border-radius: 8px;
  border-left: 3px solid #10b981;
}

/* ── Structure & Highlights ── */
.sentence-structure,
.sentence-highlights {
  padding: 10px 12px;
  border-radius: 8px;
}

.sentence-structure {
  background: #f0f9ff;
  border-left: 3px solid #3b82f6;
}

.sentence-highlights {
  background: #fffbeb;
  border-left: 3px solid #f59e0b;
}

.section-label {
  font-size: 12px;
  font-weight: 700;
  color: #374151;
  margin-bottom: 6px;
}

.section-text {
  margin: 0;
  font-size: 13px;
  color: #4b5563;
  line-height: 1.6;
}

.highlight-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.highlight-item {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
}

.hl-word {
  font-size: 13px;
  font-weight: 700;
  color: #b45309;
}

.hl-meaning {
  font-size: 13px;
  color: #6b7280;
}

.hl-detail {
  width: 100%;
  margin: 2px 0 0;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}

/* ── Empty state ── */
.translate-empty {
  padding: 32px 16px;
  text-align: center;
  font-size: 14px;
  color: #9ca3af;
}

.translate-empty p {
  margin: 0;
}
</style>
