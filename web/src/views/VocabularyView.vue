<template>
  <main class="vocab-page">
    <section class="lookup-header">
      <div>
        <p class="eyebrow">Oxford Dictionaries</p>
        <h1 class="page-title">单词查询</h1>
      </div>
      <form class="lookup-form" @submit.prevent="submitLookup">
        <input
          v-model="query"
          class="lookup-input"
          type="search"
          autocomplete="off"
          spellcheck="false"
          placeholder="输入英文单词，例如 apple"
          aria-label="输入英文单词"
        >
        <select v-model="language" class="language-select" aria-label="选择词典语言">
          <option value="en-gb">en-gb</option>
          <option value="en-us">en-us</option>
        </select>
        <button type="submit" class="lookup-button" :disabled="loading">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </form>
    </section>

    <section v-if="emptyState" class="empty-state">
      <h2>输入一个英文单词</h2>
      <p>查看 Oxford 英文释义、音标、发音和例句。</p>
    </section>

    <section v-else-if="errorMessage" class="error-state">
      <h2>{{ errorMessage }}</h2>
      <p v-if="debugMessage" class="debug-message">{{ debugMessage }}</p>
    </section>

    <section v-else-if="result" class="result-layout">
      <article class="result-main">
        <header class="word-header">
          <div>
            <h2>{{ result.word }}</h2>
            <p v-if="primaryPhonetic?.text" class="phonetic">/{{ primaryPhonetic.text }}/</p>
          </div>
          <button
            v-if="primaryPhonetic?.audioUrl"
            type="button"
            class="audio-button"
            @click="playAudio(primaryPhonetic.audioUrl)"
          >
            播放发音
          </button>
        </header>

        <div class="entries">
          <section v-for="entry in result.entries" :key="entry.partOfSpeech || 'unknown'" class="entry-block">
            <h3>{{ entry.partOfSpeech || 'unknown' }}</h3>
            <ol class="definition-list">
              <li
                v-for="(definition, index) in visibleDefinitions(entry)"
                :key="`${entry.partOfSpeech}-${index}-${definition}`"
              >
                <p class="definition">{{ definition }}</p>
                <p v-if="entry.examples[index]" class="example">{{ entry.examples[index] }}</p>
              </li>
            </ol>
            <button
              v-if="entry.definitions.length > maxVisibleDefinitions"
              type="button"
              class="expand-button"
              @click="toggleEntry(entry.partOfSpeech || 'unknown')"
            >
              {{ isExpanded(entry.partOfSpeech || 'unknown') ? '收起' : '展开更多' }}
            </button>
          </section>
        </div>
      </article>

      <aside class="result-meta">
        <dl>
          <div>
            <dt>Source</dt>
            <dd>Oxford Dictionaries</dd>
          </div>
          <div>
            <dt>Language</dt>
            <dd>{{ result.language }}</dd>
          </div>
          <div v-if="lastLookupAt">
            <dt>Updated</dt>
            <dd>{{ lastLookupAt }}</dd>
          </div>
        </dl>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { lookupDictionary } from '@/api/dictionary'
import type { DictionaryEntry, DictionaryLanguage, DictionaryLookupResponse } from '@/api/dictionary'

const maxVisibleDefinitions = 3

const query = ref('')
const language = ref<DictionaryLanguage>('en-gb')
const loading = ref(false)
const result = ref<DictionaryLookupResponse | null>(null)
const errorMessage = ref('')
const debugMessage = ref('')
const expandedEntries = ref<Set<string>>(new Set())
const lastLookupAt = ref('')

const emptyState = computed(() => !loading.value && !result.value && !errorMessage.value)
const primaryPhonetic = computed(() => result.value?.phonetics.find((item) => item.text || item.audioUrl))

async function submitLookup() {
  const word = query.value.trim()
  if (!word) {
    errorMessage.value = '请输入要查询的单词'
    debugMessage.value = import.meta.env.DEV ? '400 / INVALID_WORD' : ''
    result.value = null
    return
  }

  loading.value = true
  errorMessage.value = ''
  debugMessage.value = ''
  expandedEntries.value = new Set()

  try {
    result.value = await lookupDictionary(word, language.value)
    lastLookupAt.value = new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(Date.now())
  } catch (err) {
    result.value = null
    const normalized = normalizeError(err)
    errorMessage.value = normalized.message
    debugMessage.value = import.meta.env.DEV ? normalized.debug : ''
  } finally {
    loading.value = false
  }
}

function visibleDefinitions(entry: DictionaryEntry) {
  const key = entry.partOfSpeech || 'unknown'
  return isExpanded(key)
    ? entry.definitions
    : entry.definitions.slice(0, maxVisibleDefinitions)
}

function isExpanded(key: string) {
  return expandedEntries.value.has(key)
}

function toggleEntry(key: string) {
  const next = new Set(expandedEntries.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedEntries.value = next
}

function playAudio(audioUrl: string) {
  void new Audio(audioUrl).play()
}

function normalizeError(err: unknown) {
  const response = (err as { response?: { status?: number; data?: { code?: string; message?: string } } }).response
  const status = response?.status
  const code = response?.data?.code
  const message = response?.data?.message

  if (status === 404) {
    return { message: '未找到该单词', debug: `${status} / ${code ?? 'DICTIONARY_NOT_FOUND'}` }
  }
  if (status === 429) {
    return { message: '词典服务额度已用完，请稍后再试', debug: `${status} / ${code ?? 'OXFORD_QUOTA_EXCEEDED'}` }
  }
  if (status === 504) {
    return { message: '词典服务响应超时', debug: `${status} / ${code ?? 'OXFORD_TIMEOUT'}` }
  }
  return {
    message: message || '词典服务暂时不可用，请稍后再试',
    debug: `${status ?? 'NETWORK'} / ${code ?? 'DICTIONARY_LOOKUP_FAILED'}`,
  }
}
</script>

<style scoped>
.vocab-page {
  width: min(1120px, calc(100% - 48px));
  margin: 0 auto;
  padding: 32px 0 48px;
}

.lookup-header {
  display: grid;
  grid-template-columns: minmax(220px, 0.7fr) minmax(420px, 1.3fr);
  gap: 24px;
  align-items: end;
  margin-bottom: 28px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #047857;
  font-size: 13px;
  font-weight: 700;
}

.page-title {
  margin: 0;
  color: #111827;
  font-size: 26px;
  font-weight: 800;
}

.lookup-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px 92px;
  gap: 10px;
}

.lookup-input,
.language-select,
.lookup-button {
  min-height: 44px;
  border-radius: 8px;
  font-size: 15px;
}

.lookup-input,
.language-select {
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #111827;
}

.lookup-input {
  min-width: 0;
  padding: 0 14px;
}

.language-select {
  padding: 0 10px;
}

.lookup-button {
  border: 0;
  background: #047857;
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
}

.lookup-button:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.empty-state,
.error-state {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  padding: 48px 32px;
  text-align: center;
}

.empty-state h2,
.error-state h2 {
  margin: 0 0 10px;
  color: #111827;
  font-size: 20px;
}

.empty-state p,
.debug-message {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.result-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 20px;
  align-items: start;
}

.result-main,
.result-meta {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.result-main {
  padding: 28px;
}

.word-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 22px;
}

.word-header h2 {
  margin: 0;
  color: #111827;
  font-size: 34px;
  line-height: 1.1;
}

.phonetic {
  margin: 8px 0 0;
  color: #047857;
  font-size: 16px;
}

.audio-button,
.expand-button {
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  background: #ecfdf5;
  color: #047857;
  font-weight: 700;
  cursor: pointer;
}

.audio-button {
  align-self: start;
  padding: 10px 14px;
}

.entries {
  display: grid;
  gap: 24px;
  margin-top: 24px;
}

.entry-block h3 {
  margin: 0 0 12px;
  color: #111827;
  font-size: 18px;
  text-transform: lowercase;
}

.definition-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding-left: 24px;
}

.definition {
  margin: 0;
  color: #111827;
  font-size: 15px;
  line-height: 1.6;
}

.example {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 14px;
  font-style: italic;
  line-height: 1.5;
}

.expand-button {
  margin-top: 14px;
  padding: 8px 12px;
}

.result-meta {
  padding: 22px;
}

.result-meta dl {
  display: grid;
  gap: 18px;
  margin: 0;
}

.result-meta dt {
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.result-meta dd {
  margin: 4px 0 0;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

@media (max-width: 860px) {
  .vocab-page {
    width: min(100% - 32px, 640px);
    padding-top: 24px;
  }

  .lookup-header,
  .result-layout {
    grid-template-columns: 1fr;
  }

  .lookup-form {
    grid-template-columns: 1fr;
  }

  .word-header {
    flex-direction: column;
  }
}
</style>
