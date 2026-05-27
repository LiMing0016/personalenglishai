<template>
  <div class="translation-page">
    <header class="translation-hero">
      <nav class="translation-tabs" aria-label="翻译页面导航">
        <RouterLink class="translation-tab active" to="/app/translation">翻译练习</RouterLink>
        <RouterLink class="translation-tab" to="/app/writing">写作练习</RouterLink>
      </nav>
      <p class="eyebrow">PEAI TRANSLATION / PRACTICE</p>
      <h1>翻译练习</h1>
      <p class="hero-subtitle">输入英文内容，快速得到中文译文，也可以逐句拆解重点表达。</p>
    </header>

    <main class="translator-shell" aria-labelledby="translation-title">
      <section class="translator-card">
        <div class="card-heading">
          <div>
            <p class="section-label">SOURCE</p>
            <h2 id="translation-title">原文</h2>
          </div>
          <div class="mode-switch" aria-label="翻译模式">
            <button type="button" :class="{ active: mode === 'full' }" @click="mode = 'full'">全文翻译</button>
            <button type="button" :class="{ active: mode === 'detailed' }" @click="mode = 'detailed'">逐句精讲</button>
          </div>
        </div>

        <textarea
          v-model="sourceText"
          class="source-input"
          rows="14"
          placeholder="Paste or type an English paragraph here..."
          aria-label="输入要翻译的英文内容"
        />

        <div class="source-footer">
          <span>{{ sourceText.trim().length }} 字符</span>
          <button type="button" class="primary-action" :disabled="!canTranslate" @click="handleTranslate">
            {{ loading ? '翻译中...' : '开始翻译' }}
          </button>
        </div>
      </section>

      <section class="translator-card result-card" aria-live="polite">
        <div class="card-heading">
          <div>
            <p class="section-label">RESULT</p>
            <h2>译文</h2>
          </div>
          <button v-if="resultText" type="button" class="ghost-action" @click="copyResult">复制</button>
        </div>

        <div v-if="loading" class="result-placeholder">
          <span class="loading-line loading-line--long"></span>
          <span class="loading-line"></span>
          <span class="loading-line loading-line--short"></span>
        </div>

        <div v-else-if="errorMsg" class="error-state">
          <p>{{ errorMsg }}</p>
          <button type="button" class="ghost-action" @click="handleTranslate">重试</button>
        </div>

        <template v-else-if="mode === 'detailed' && sentences.length > 0">
          <ol class="sentence-list">
            <li v-for="(sentence, index) in sentences" :key="`${sentence.english}-${index}`" class="sentence-item">
              <p class="sentence-english">{{ sentence.english }}</p>
              <p class="sentence-chinese">{{ sentence.chinese }}</p>
              <p v-if="sentence.structure" class="sentence-structure">{{ sentence.structure }}</p>
            </li>
          </ol>
        </template>

        <pre v-else-if="resultText" class="result-text">{{ resultText }}</pre>

        <div v-else class="empty-state">
          <p>翻译结果会显示在这里。</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { translateEssay } from '@/api/writing'
import type { SentenceTranslation } from '@/api/writing'
import { showToast } from '@/utils/toast'

const mode = ref<'full' | 'detailed'>('full')
const sourceText = ref('')
const loading = ref(false)
const errorMsg = ref<string | null>(null)
const resultText = ref('')
const sentences = ref<SentenceTranslation[]>([])
let abortController: AbortController | null = null

const canTranslate = computed(() => sourceText.value.trim().length >= 10 && !loading.value)

async function handleTranslate() {
  const text = sourceText.value.trim()
  if (text.length < 10) {
    showToast('请输入至少 10 个字符', 'info')
    return
  }

  abortController?.abort()
  abortController = new AbortController()
  loading.value = true
  errorMsg.value = null
  resultText.value = ''
  sentences.value = []

  try {
    const response = await translateEssay(
      { text, mode: mode.value },
      { signal: abortController.signal },
    )
    sentences.value = response.sentences ?? []
    resultText.value = response.translation ?? sentences.value.map((item) => item.chinese).join('\n')

    if (!resultText.value && sentences.value.length === 0) {
      errorMsg.value = '没有拿到翻译结果，请重试。'
    }
  } catch (err: any) {
    if (err?.name === 'CanceledError' || err?.name === 'AbortError' || err?.code === 'ERR_CANCELED') return
    errorMsg.value = err?.message ?? '翻译失败，请稍后再试。'
  } finally {
    loading.value = false
  }
}

async function copyResult() {
  const text = resultText.value.trim()
  if (!text) return
  await navigator.clipboard?.writeText(text)
  showToast('已复制译文', 'success')
}
</script>

<style scoped>
.translation-page {
  min-height: 100vh;
  padding: 48px 42px 64px;
  background: #f7f5ef;
  color: #1f2937;
}

.translation-hero {
  max-width: 1180px;
  margin: 0 auto 34px;
}

.translation-tabs {
  display: inline-flex;
  align-items: center;
  gap: 34px;
  margin-bottom: 42px;
  border-bottom: 1px solid #ddd6c8;
}

.translation-tab {
  padding: 0 0 16px;
  color: #756f65;
  font-size: 18px;
  font-weight: 800;
  text-decoration: none;
}

.translation-tab.active {
  color: #1f1f1d;
  border-bottom: 3px solid #1f1f1d;
}

.eyebrow,
.section-label {
  margin: 0;
  color: #756f65;
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 0;
}

.translation-hero h1 {
  margin: 12px 0 10px;
  color: #1f1f1d;
  font-size: 72px;
  line-height: 0.95;
  font-weight: 950;
}

.hero-subtitle {
  margin: 0;
  color: #756f65;
  font-size: 20px;
  font-weight: 700;
}

.translator-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
  max-width: 1180px;
  margin: 0 auto;
}

.translator-card {
  min-height: 520px;
  padding: 28px;
  border: 1px solid #e4dfd3;
  border-radius: 24px;
  background: #fffdfa;
  box-shadow: 0 18px 44px rgba(31, 41, 55, 0.06);
}

.card-heading,
.source-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-heading h2 {
  margin: 6px 0 0;
  color: #111827;
  font-size: 26px;
  font-weight: 900;
}

.mode-switch {
  display: inline-flex;
  padding: 4px;
  border: 1px solid #d9e2ec;
  border-radius: 999px;
  background: #f8fafc;
}

.mode-switch button,
.ghost-action,
.primary-action {
  border: 0;
  font-weight: 800;
  cursor: pointer;
}

.mode-switch button {
  padding: 9px 14px;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
}

.mode-switch button.active {
  background: #047857;
  color: #ffffff;
}

.source-input {
  width: 100%;
  min-height: 360px;
  margin: 24px 0 18px;
  padding: 20px;
  border: 1px solid #d9e2ec;
  border-radius: 18px;
  background: #ffffff;
  color: #1f2937;
  font: 18px/1.75 Georgia, 'Times New Roman', serif;
  resize: vertical;
  box-sizing: border-box;
}

.source-input:focus {
  border-color: #34d399;
  outline: 3px solid rgba(52, 211, 153, 0.18);
}

.source-footer {
  color: #64748b;
  font-size: 14px;
  font-weight: 700;
}

.primary-action {
  padding: 12px 22px;
  border-radius: 14px;
  background: #047857;
  color: #ffffff;
  font-size: 15px;
}

.primary-action:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.ghost-action {
  padding: 9px 14px;
  border: 1px solid #a7f3d0;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
}

.result-card {
  background: #ffffff;
}

.result-text,
.sentence-list {
  margin: 24px 0 0;
}

.result-text {
  white-space: pre-wrap;
  color: #1f2937;
  font: 18px/1.85 Georgia, 'Times New Roman', serif;
}

.sentence-list {
  display: grid;
  gap: 14px;
  padding: 0;
  list-style: none;
}

.sentence-item {
  padding: 18px;
  border: 1px solid #dbeafe;
  border-radius: 18px;
  background: #f8fafc;
}

.sentence-english,
.sentence-chinese,
.sentence-structure {
  margin: 0;
}

.sentence-english {
  color: #475569;
  font: 16px/1.7 Georgia, 'Times New Roman', serif;
}

.sentence-chinese {
  margin-top: 10px;
  color: #111827;
  font-size: 17px;
  line-height: 1.75;
  font-weight: 700;
}

.sentence-structure {
  margin-top: 10px;
  color: #047857;
  font-size: 14px;
  line-height: 1.6;
}

.empty-state,
.error-state,
.result-placeholder {
  display: grid;
  place-items: center;
  min-height: 360px;
  color: #94a3b8;
  font-size: 16px;
  font-weight: 700;
}

.error-state {
  gap: 16px;
  color: #b91c1c;
}

.loading-line {
  display: block;
  width: 72%;
  height: 16px;
  border-radius: 999px;
  background: linear-gradient(90deg, #e2e8f0, #f8fafc, #e2e8f0);
}

.loading-line--long {
  width: 88%;
}

.loading-line--short {
  width: 48%;
}

@media (max-width: 980px) {
  .translation-page {
    padding: 30px 18px 44px;
  }

  .translator-shell {
    grid-template-columns: 1fr;
  }

  .translation-tabs {
    margin-bottom: 30px;
  }

  .translation-hero h1 {
    font-size: 46px;
  }

  .card-heading,
  .source-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .mode-switch {
    width: 100%;
  }

  .mode-switch button {
    flex: 1;
  }
}
</style>
