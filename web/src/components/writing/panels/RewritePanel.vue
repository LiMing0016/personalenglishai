<template>
  <div class="polish-panel">
    <!-- ── 考试模式首次写作锁定 ── -->
    <div v-if="locked" class="polish-locked">
      <div class="polish-locked-icon">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" stroke-width="1.5">
          <rect x="3" y="11" width="18" height="11" rx="2" />
          <path d="M7 11V7a5 5 0 0110 0v4" />
        </svg>
      </div>
      <p class="polish-locked-title">首次写作模式</p>
      <p class="polish-locked-hint">当前为考试模式下的首次写作，润色功能将在提交作文后解锁。<br/>请先独立完成写作，以便系统准确评估你的真实水平。</p>
    </div>

    <template v-else>
    <!-- 全局润色档次选择 -->
    <div class="polish-tier-global">
      <span class="polish-tier-label">润色档次</span>
      <div class="polish-tiers">
        <button
          v-for="t in tierOptions"
          :key="t.value"
          type="button"
          class="tier-chip"
          :class="{ 'tier-chip--active': polishTier === t.value }"
          @click="onSelectTier(t.value)"
        >{{ t.label }}</button>
      </div>
    </div>

    <!-- 全文加载中 -->
    <div v-if="polishingAll" class="polish-loading">
      <div class="skeleton-block skeleton-polish"></div>
      <div class="skeleton-block skeleton-polish short"></div>
      <p class="loading-hint">AI 正在逐句润色全文，请稍候...</p>
    </div>

    <!-- 润色失败 -->
    <div v-else-if="polishError" class="polish-error-block">
      <p class="polish-error-hint">{{ polishError }}</p>
      <button type="button" class="btn btn-primary btn-sm" @click="doPolishAll">重试</button>
    </div>

    <!-- AI 总结卡片（可折叠） -->
    <div v-if="polishSummary && !polishingAll && !polishError" class="summary-card" :class="{ 'summary-card--collapsed': summaryCollapsed }">
      <div class="summary-toggle" @click="summaryCollapsed = !summaryCollapsed">
        <span class="summary-toggle-label">AI 润色建议</span>
        <span class="summary-toggle-arrow">{{ summaryCollapsed ? '▸' : '▾' }}</span>
      </div>
      <div v-show="!summaryCollapsed" class="summary-body">
        <div class="summary-section" v-if="polishSummary.strengths.length">
          <div class="summary-heading strengths-heading">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M8 1l2 5h5l-4 3 1.5 5L8 11l-4.5 3L5 9 1 6h5l2-5z" fill="#10b981"/></svg>
            做得好的方面
          </div>
          <ul class="summary-list">
            <li v-for="(s, i) in polishSummary.strengths" :key="'s'+i">{{ s }}</li>
          </ul>
        </div>
        <div class="summary-section" v-if="polishSummary.improvements.length">
          <div class="summary-heading improvements-heading">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M8 1v14M1 8h14" stroke="#f59e0b" stroke-width="2" stroke-linecap="round"/></svg>
            {{ tierLabel }}需要改进
          </div>
          <ul class="summary-list">
            <li v-for="(s, i) in polishSummary.improvements" :key="'i'+i">{{ s }}</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 句子列表 -->
    <template v-if="!polishingAll && !polishError && sentences.length">
      <ul class="sentence-list">
        <li
          v-for="(s, idx) in sentences"
          :key="idx"
          class="sentence-card"
          :class="{
            'sentence-card--expanded': expandedIdx === idx,
            'sentence-card--replaced': replacedSet.has(idx),
            'sentence-card--unchanged': s.polished && s.polished === s.text,
          }"
        >
          <!-- 折叠态：句子预览 -->
          <div class="sentence-header" @click="toggleExpand(idx)">
            <span class="sentence-dot" :class="{
              'dot--replaced': replacedSet.has(idx),
              'dot--unchanged': s.polished && s.polished === s.text,
              'dot--ready': s.polished && s.polished !== s.text && !replacedSet.has(idx),
            }"></span>
            <span class="sentence-preview">{{ truncate(s.text, 40) }}</span>
            <span class="sentence-arrow">{{ expandedIdx === idx ? '▾' : '▸' }}</span>
          </div>

          <!-- 展开态 -->
          <div v-if="expandedIdx === idx" class="sentence-body">
            <div class="sentence-original">
              <span class="label-original">原文：</span>
              <span>{{ s.text }}</span>
            </div>

            <template v-if="s.polished">
              <!-- 无改动 -->
              <p v-if="s.polished === s.text" class="no-change-hint">此句无需改动。</p>

              <!-- 有润色结果 -->
              <div v-else class="candidate-card">
                <span class="candidate-label">推荐句子</span>
                <p class="candidate-text" v-html="diffHighlight(s.text, s.polished)"></p>
                <p v-if="s.explanation" class="candidate-explanation">{{ s.explanation }}</p>
                <div class="candidate-actions">
                  <button
                    type="button"
                    class="btn-replace"
                    @click="applyCandidate(idx)"
                  >替换</button>
                  <button
                    type="button"
                    class="btn-dismiss"
                    @click="dismissSentence(idx)"
                  >忽略</button>
                </div>
              </div>
            </template>

            <p v-else class="no-result-hint">选择润色档次后自动生成润色建议。</p>
          </div>
        </li>
      </ul>
    </template>

    <!-- 空状态 -->
    <template v-if="!polishingAll && !polishError && !sentences.length">
      <div class="polish-empty">
        <p>选择润色档次，AI 将逐句润色全文。</p>
      </div>
    </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { PolishTier, PolishEssaySummary } from '@/api/writing'
import { polishEssay } from '@/api/writing'
import { savePolishResult, loadPolishResult } from '@/components/writing/editorShellStorage'
import { useWritingDraftStore } from '@/stores/writingDraftStore'

interface SentenceItem {
  text: string
  start: number
  end: number
  polished?: string
  explanation?: string
}

const tierOptions: { value: PolishTier; label: string }[] = [
  { value: 'basic', label: '基础改进' },
  { value: 'steady', label: '稳步提升' },
  { value: 'advanced', label: '进阶表达' },
  { value: 'perfect', label: '满分冲刺' },
]

const props = defineProps<{
  fullEssay: string
  locked?: boolean
}>()

const emit = defineEmits<{
  'replace-sentence': [payload: { start: number; end: number; original: string; replacement: string }]
  'sentence-focus': [range: { start: number; end: number } | null]
}>()

const draftStore = useWritingDraftStore()

const polishTier = ref<PolishTier | null>(null)
const expandedIdx = ref<number | null>(null)
const polishingAll = ref(false)
const polishError = ref<string | null>(null)
const polishSummary = ref<PolishEssaySummary | null>(null)
const summaryCollapsed = ref(false)
const replacedSet = ref<Set<number>>(new Set())
const sentences = ref<SentenceItem[]>([])
let polishAbortToken = 0
let ignoreNextEssayChange = false
let cacheRestored = false

function normalizeEssaySnapshot(text: string): string {
  return (text ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim()
}

function tryRestoreCachedState(): boolean {
  const scope = draftStore.docId
  const cached = loadPolishResult(scope)
  if (!cached || cached.sentences.length === 0) return false
  if (normalizeEssaySnapshot(cached.essaySnapshot) !== normalizeEssaySnapshot(props.fullEssay)) return false

  polishTier.value = cached.tier as PolishTier
  polishSummary.value = cached.summary as PolishEssaySummary | null
  sentences.value = cached.sentences as SentenceItem[]
  replacedSet.value = new Set(cached.replacedIndices ?? [])
  cacheRestored = true
  return true
}

function persistState() {
  if (!polishTier.value) return
  savePolishResult({
    tier: polishTier.value,
    summary: polishSummary.value,
    sentences: sentences.value,
    replacedIndices: [...replacedSet.value],
    essaySnapshot: normalizeEssaySnapshot(props.fullEssay),
  }, draftStore.docId)
}

// 恢复缓存
onMounted(() => {
  tryRestoreCachedState()
})

const tierLabel = computed(() => {
  if (!polishTier.value) return ''
  const found = tierOptions.find(t => t.value === polishTier.value)
  return found ? `达到「${found.label}」` : ''
})

function splitSentences(text: string): SentenceItem[] {
  if (!text || !text.trim()) return []
  const items: SentenceItem[] = []
  // 按句末标点分割，但跳过小数点（数字.数字）和缩写（如 Mr. Dr. etc.）
  const sentenceEnd = /(?<!\d)\.(?!\d)(?:\s|$)|[!?](?:\s|$)|[.!?]$/g
  let lastIdx = 0
  let match: RegExpExecArray | null
  while ((match = sentenceEnd.exec(text)) !== null) {
    const punctEnd = match.index + match[0].trimEnd().length
    const raw = text.slice(lastIdx, punctEnd).trim()
    if (raw.length >= 3) {
      const start = text.indexOf(raw, lastIdx)
      items.push({ text: raw, start, end: start + raw.length })
    }
    lastIdx = match.index + match[0].length
  }
  // 剩余文本
  const tail = text.slice(lastIdx).trim()
  if (tail.length >= 3) {
    const start = text.indexOf(tail, lastIdx)
    items.push({ text: tail, start, end: start + tail.length })
  }
  return items
}

// Split sentences on mount (but don't call GPT yet)
// Skip reset when the change was caused by a sentence replacement
watch(() => props.fullEssay, () => {
  if (!cacheRestored && tryRestoreCachedState()) return
  // Skip first trigger if cache was restored
  if (cacheRestored) {
    cacheRestored = false
    return
  }
  if (ignoreNextEssayChange) {
    ignoreNextEssayChange = false
    // Re-split to update positions, but preserve polish results
    const newSentences = splitSentences(props.fullEssay)
    for (const ns of newSentences) {
      const old = sentences.value.find(s =>
        s.text === ns.text || s.polished === ns.text
      )
      if (old) {
        ns.polished = old.polished
        ns.explanation = old.explanation
      }
    }
    sentences.value = newSentences
    return
  }
  sentences.value = splitSentences(props.fullEssay)
  expandedIdx.value = null
  replacedSet.value = new Set()
  polishSummary.value = null
  polishError.value = null
  polishAbortToken++
}, { immediate: true })

function truncate(text: string, maxLen: number): string {
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}

function onSelectTier(tier: PolishTier) {
  if (polishingAll.value) return
  polishTier.value = tier
  doPolishAll()
}

async function doPolishAll() {
  if (!props.fullEssay?.trim() || !polishTier.value) return

  const token = ++polishAbortToken
  polishingAll.value = true
  polishError.value = null

  // Reset previous results
  for (const s of sentences.value) {
    s.polished = undefined
    s.explanation = undefined
  }
  replacedSet.value = new Set()
  polishSummary.value = null

  try {
    const res = await polishEssay({ text: props.fullEssay, tier: polishTier.value })
    if (token !== polishAbortToken) return

    // Save summary
    polishSummary.value = res.summary ?? null
    summaryCollapsed.value = false

    // One-to-one mapping by order + text similarity, avoids duplicate sentence collisions.
    const available = new Set(sentences.value.map((_, i) => i))
    const normalize = (v: string) => v.replace(/\s+/g, ' ').trim()
    let cursor = 0

    const pickIndex = (gptOriginal: string): number => {
      const targetRaw = gptOriginal.trim()
      const targetNorm = normalize(gptOriginal)
      if (!targetNorm) return -1

      const ordered = [
        ...Array.from({ length: Math.max(0, sentences.value.length - cursor) }, (_, k) => cursor + k),
        ...Array.from({ length: cursor }, (_, k) => k),
      ]

      const findInOrdered = (predicate: (s: SentenceItem) => boolean): number => {
        for (const idx of ordered) {
          if (!available.has(idx)) continue
          if (predicate(sentences.value[idx])) return idx
        }
        return -1
      }

      let idx = findInOrdered((s) => s.text.trim() === targetRaw)
      if (idx !== -1) return idx

      idx = findInOrdered((s) => normalize(s.text) === targetNorm)
      if (idx !== -1) return idx

      return findInOrdered((s) => {
        const cur = normalize(s.text)
        return cur.includes(targetNorm) || targetNorm.includes(cur)
      })
    }

    for (const gpt of res.sentences) {
      const idx = pickIndex(gpt.original)
      if (idx === -1) continue
      const match = sentences.value[idx]
      match.polished = gpt.polished
      match.explanation = gpt.explanation
      available.delete(idx)
      cursor = (idx + 1) % Math.max(1, sentences.value.length)
    }
    persistState()
  } catch {
    if (token !== polishAbortToken) return
    polishError.value = '润色请求失败，请重试。'
  } finally {
    if (token === polishAbortToken) {
      polishingAll.value = false
    }
  }
}

function toggleExpand(idx: number) {
  if (expandedIdx.value === idx) {
    expandedIdx.value = null
    emit('sentence-focus', null)
  } else {
    expandedIdx.value = idx
    const s = sentences.value[idx]
    emit('sentence-focus', { start: s.start, end: s.end })
  }
}

function applyCandidate(idx: number) {
  const s = sentences.value[idx]
  if (!s?.polished) return
  ignoreNextEssayChange = true
  emit('replace-sentence', {
    start: s.start,
    end: s.end,
    original: s.text,
    replacement: s.polished,
  })
  replacedSet.value = new Set([...replacedSet.value, idx])
  persistState()
}

function dismissSentence(idx: number) {
  const s = sentences.value[idx]
  if (s) {
    s.polished = undefined
    s.explanation = undefined
  }
  expandedIdx.value = null
  emit('sentence-focus', null)
  persistState()
}

function diffHighlight(original: string, polished: string): string {
  const origWords = original.split(/(\s+)/)
  const polWords = polished.split(/(\s+)/)
  const origSet = new Set(origWords.filter(w => w.trim()))

  return polWords.map(w => {
    if (!w.trim()) return escapeHtml(w)
    if (!origSet.has(w)) {
      return `<b class="diff-changed">${escapeHtml(w)}</b>`
    }
    return escapeHtml(w)
  }).join('')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
</script>

<style scoped>
.polish-panel {
  padding: 16px;
}

/* ── 首次写作锁定 ── */
.polish-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  text-align: center;
}

.polish-locked-icon {
  margin-bottom: 16px;
  opacity: 0.6;
}

.polish-locked-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #374151;
}

.polish-locked-hint {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
  line-height: 1.6;
}

/* ── AI 总结卡片（可折叠） ── */
.summary-card {
  margin-bottom: 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.summary-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.12s;
}
.summary-toggle:hover { background: #f9fafb; }

.summary-toggle-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.summary-toggle-arrow {
  font-size: 12px;
  color: #9ca3af;
}

.summary-body {
  padding: 0 14px 14px;
}

.summary-section + .summary-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.summary-heading {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.strengths-heading { color: #047857; }
.improvements-heading { color: #b45309; }

.summary-list {
  margin: 0;
  padding-left: 20px;
  list-style: disc;
}

.summary-list li {
  font-size: 13px;
  color: #475569;
  line-height: 1.8;
}

/* ── 全局润色档次 ── */
.polish-tier-global {
  margin-bottom: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #f0fdf4;
  border: 1px solid #dcfce7;
}

.polish-tier-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #065f46;
  margin-bottom: 8px;
}

.polish-tiers {
  display: flex;
  gap: 6px;
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

/* ── 句子列表 ── */
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
.sentence-card--expanded { border-color: #93c5fd; }
.sentence-card--replaced { opacity: 0.5; }
.sentence-card--unchanged { opacity: 0.6; }

.sentence-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  user-select: none;
}
.sentence-header:hover { background: #f9fafb; }

.sentence-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
}
.dot--ready { background: #3b82f6; }
.dot--replaced { background: #10b981; }
.dot--unchanged { background: #d1d5db; }

.sentence-preview {
  flex: 1;
  font-size: 13px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sentence-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: #9ca3af;
}

/* ── 展开态 ── */
.sentence-body {
  padding: 0 12px 12px;
}

.sentence-original {
  margin-bottom: 10px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
.label-original {
  font-weight: 600;
  color: #374151;
}

/* ── 候选卡片 ── */
.candidate-card {
  margin-bottom: 8px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
}

.candidate-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 4px;
}

.candidate-text {
  margin: 0 0 6px;
  font-size: 14px;
  color: #1f2937;
  line-height: 1.6;
}

.candidate-text :deep(.diff-changed) {
  color: #2563eb;
  font-weight: 700;
}

.candidate-explanation {
  margin: 0 0 8px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}

.candidate-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-replace {
  padding: 4px 14px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #3b82f6;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-replace:hover { background: #dbeafe; }

.btn-dismiss {
  padding: 2px 10px;
  font-size: 11px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
}
.btn-dismiss:hover { color: #6b7280; }

.no-change-hint {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
  font-style: italic;
}

.no-result-hint {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
}

/* ── 加载 / 错误 / 空状态 ── */
.polish-loading { padding: 8px 0; display: flex; flex-direction: column; gap: 8px; }
.skeleton-block { border-radius: 10px; background: linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-polish { height: 60px; }
.skeleton-polish.short { height: 40px; width: 70%; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.loading-hint { text-align: center; font-size: 13px; color: #6b7280; margin: 4px 0 0; }

.polish-error-block { padding: 16px; text-align: center; }
.polish-error-hint { margin: 0 0 10px; font-size: 13px; color: #991b1b; }

.polish-empty { padding: 32px 16px; text-align: center; font-size: 14px; color: #9ca3af; }

.btn { padding: 10px 20px; font-size: 14px; font-weight: 500; border: none; border-radius: 12px; cursor: pointer; transition: background 0.2s; }
.btn-primary { background: #047857; color: #fff; }
.btn-primary:hover { background: #065f46; }
.btn-sm { padding: 6px 16px; font-size: 13px; }
</style>

