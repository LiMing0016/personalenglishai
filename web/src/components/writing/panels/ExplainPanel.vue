<template>
  <div class="material-panel">
    <!-- Generate button -->
    <div class="generate-area">
      <button
        type="button"
        class="material-generate-btn"
        :disabled="loading || !canGenerate"
        @click="doGenerate"
      >{{ loading ? '生成中...' : hasResult ? '重新生成' : '生成素材' }}</button>
      <p v-if="!canGenerate && !loading && !hasResult" class="generate-hint">请先在左侧填写作文题目</p>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="material-loading">
      <div class="skeleton-row"></div>
      <div class="skeleton-row short"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="material-error">
      <p class="error-text">{{ error }}</p>
      <button type="button" class="retry-btn" @click="doGenerate">重试</button>
    </div>

    <!-- Results -->
    <template v-else-if="result">
      <!-- 主题词 Vocabulary (分组) -->
      <section v-if="result.vocabulary.length > 0" class="section-card section-vocab">
        <div class="section-header" @click="toggleSection('vocabulary')">
          <span class="section-icon">Aa</span>
          <span class="section-title">主题词 ({{ totalVocabCount }})</span>
          <span class="section-arrow">{{ openSections.vocabulary ? '▾' : '▸' }}</span>
        </div>
        <div v-if="openSections.vocabulary" class="section-body">
          <div v-for="(group, gi) in result.vocabulary" :key="gi" class="vocab-group">
            <div class="vocab-group-label">{{ group.category }}</div>
            <div class="vocab-grid">
              <div v-for="(v, vi) in group.words" :key="vi" class="vocab-item">
                <span class="vocab-word">{{ v.word }}</span>
                <span class="vocab-meaning">{{ v.meaning }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 常用短语 Phrases -->
      <section v-if="result.phrases.length > 0" class="section-card section-phrases">
        <div class="section-header" @click="toggleSection('phrases')">
          <span class="section-icon">Ph</span>
          <span class="section-title">常用短语 ({{ result.phrases.length }})</span>
          <span class="section-arrow">{{ openSections.phrases ? '▾' : '▸' }}</span>
        </div>
        <div v-if="openSections.phrases" class="section-body">
          <div class="phrase-list">
            <div v-for="(p, pi) in result.phrases" :key="pi" class="phrase-item">
              <span class="phrase-text">{{ p.phrase }}</span>
              <span class="phrase-meaning">{{ p.meaning }}</span>
              <button type="button" class="copy-mini-btn" @click="copyText(p.phrase)">复制</button>
            </div>
          </div>
        </div>
      </section>

      <!-- 常用句子 Sentences -->
      <section v-if="result.sentences.length > 0" class="section-card section-sentences">
        <div class="section-header" @click="toggleSection('sentences')">
          <span class="section-icon">S</span>
          <span class="section-title">常用句子 ({{ result.sentences.length }})</span>
          <span class="section-arrow">{{ openSections.sentences ? '▾' : '▸' }}</span>
        </div>
        <div v-if="openSections.sentences" class="section-body">
          <div v-for="(s, si) in result.sentences" :key="si" class="sentence-card">
            <p class="sentence-text">{{ s.sentence }}</p>
            <div class="sentence-bottom">
              <span class="sentence-desc">{{ s.description }}</span>
              <button type="button" class="copy-mini-btn" @click="copyText(s.sentence)">复制</button>
            </div>
          </div>
        </div>
      </section>
    </template>

    <!-- Empty state -->
    <div v-else class="material-empty">
      <p>点击「生成素材」，AI 将根据题目为你准备写作素材包</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { generateWritingMaterial, type WritingMaterialResponse } from '@/api/writing'
import { useWritingDraftStore } from '@/stores/writingDraftStore'
import { saveWritingMaterialResult, loadWritingMaterialResult } from '../editorShellStorage'
import { showToast } from '@/utils/toast'

const props = withDefaults(defineProps<{
  taskPrompt?: string
  studyStage?: string | null
  writingMode?: 'free' | 'exam'
}>(), {
  taskPrompt: '',
  studyStage: null,
  writingMode: 'free',
})

const draftStore = useWritingDraftStore()

const loading = ref(false)
const error = ref<string | null>(null)
const result = ref<WritingMaterialResponse | null>(null)
let lastRequestTime = 0

const openSections = ref({
  vocabulary: true,
  phrases: true,
  sentences: true,
})

const canGenerate = computed(() => !loading.value && props.taskPrompt.trim().length >= 5)
const hasResult = computed(() => result.value !== null)
const totalVocabCount = computed(() =>
  result.value?.vocabulary.reduce((sum, g) => sum + g.words.length, 0) ?? 0
)

onMounted(() => {
  const cached = loadWritingMaterialResult() as WritingMaterialResponse | null
  if (cached) {
    result.value = cached
  }
})

function toggleSection(key: keyof typeof openSections.value) {
  openSections.value[key] = !openSections.value[key]
}

async function doGenerate() {
  if (!canGenerate.value) return
  const now = Date.now()
  if (now - lastRequestTime < 3000) {
    showToast('操作太频繁，请稍候', 'info')
    return
  }
  lastRequestTime = now
  loading.value = true
  error.value = null
  try {
    const essayText = draftStore.draftText?.trim()
    result.value = await generateWritingMaterial({
      taskPrompt: props.taskPrompt.trim(),
      essayText: essayText && essayText.length >= 20 ? essayText : undefined,
      studyStage: props.studyStage,
      writingMode: props.writingMode,
    })
    openSections.value = { vocabulary: true, phrases: true, sentences: true }
    saveWritingMaterialResult(result.value)
  } catch (e: any) {
    if (e?.name === 'CanceledError' || e?.name === 'AbortError') return
    error.value = e?.message ?? '素材生成失败，请重试'
  } finally {
    loading.value = false
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    showToast('已复制', 'success')
  } catch {
    showToast('复制失败', 'error')
  }
}
</script>

<style scoped>
.material-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── Generate Area ── */
.generate-area {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.generate-hint {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}

.material-generate-btn {
  padding: 10px 16px;
  border: none;
  border-radius: 10px;
  background: #1d4ed8;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s;
}

.material-generate-btn:hover:not(:disabled) {
  background: #1e40af;
}

.material-generate-btn:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

/* ── Section cards ── */
.section-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  cursor: pointer;
  user-select: none;
}

.section-header:hover {
  background: #f8fafc;
}

.section-icon {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 800;
  color: #fff;
}

.section-vocab .section-icon {
  background: #3b82f6;
}

.section-phrases .section-icon {
  background: #10b981;
}

.section-sentences .section-icon {
  background: #8b5cf6;
}

.section-title {
  flex: 1;
  font-size: 14px;
  font-weight: 700;
  color: #1f2937;
}

.section-arrow {
  font-size: 12px;
  color: #9ca3af;
}

.section-body {
  padding: 0 14px 14px;
}

/* ── Vocabulary Groups ── */
.vocab-group {
  margin-bottom: 10px;
}

.vocab-group:last-child {
  margin-bottom: 0;
}

.vocab-group-label {
  font-size: 12px;
  font-weight: 700;
  color: #1e40af;
  margin-bottom: 6px;
  padding-left: 2px;
}

.vocab-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.vocab-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
}

.vocab-word {
  font-size: 13px;
  font-weight: 700;
  color: #1e40af;
}

.vocab-meaning {
  font-size: 11px;
  color: #6b7280;
}

/* ── Phrases ── */
.phrase-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.phrase-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f0fdf4;
  border: 1px solid #d1fae5;
}

.phrase-text {
  font-size: 13px;
  font-weight: 600;
  color: #065f46;
  flex: 1;
  min-width: 0;
}

.phrase-meaning {
  font-size: 11px;
  color: #6b7280;
  flex-shrink: 0;
}

/* ── Sentences ── */
.sentence-card {
  padding: 10px 12px;
  border-radius: 10px;
  background: #faf5ff;
  border: 1px solid #e9d5ff;
  margin-bottom: 6px;
}

.sentence-card:last-child {
  margin-bottom: 0;
}

.sentence-text {
  margin: 0 0 6px;
  font-size: 13px;
  color: #1f2937;
  line-height: 1.7;
}

.sentence-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.sentence-desc {
  font-size: 11px;
  color: #7c3aed;
  font-style: italic;
}

/* ── Copy button ── */
.copy-mini-btn {
  flex-shrink: 0;
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: #e0e7ff;
  color: #4338ca;
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s;
}

.copy-mini-btn:hover {
  background: #c7d2fe;
}

/* ── Loading / Error / Empty ── */
.material-loading {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton-row, .skeleton-card {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 10px;
}

.skeleton-row { height: 18px; }
.skeleton-row.short { width: 62%; }
.skeleton-card { height: 76px; }

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.material-error {
  padding: 16px;
  text-align: center;
}

.error-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: #991b1b;
}

.retry-btn {
  padding: 6px 14px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #374151;
  font-size: 12px;
  cursor: pointer;
}

.retry-btn:hover { background: #f3f4f6; }

.material-empty {
  padding: 32px 16px;
  text-align: center;
  font-size: 14px;
  color: #9ca3af;
}

.material-empty p { margin: 0; }
</style>
