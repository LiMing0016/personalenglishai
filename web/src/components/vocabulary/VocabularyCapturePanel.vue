<template>
  <section class="vocabulary-capture-panel" aria-label="批量录入单词">
    <header>
      <div>
        <p>Capture</p>
        <h2>批量录入</h2>
      </div>
      <span>{{ terms.length }} 个待沉淀</span>
    </header>

    <form @submit.prevent="submitCapture">
      <textarea
        v-model="rawTerms"
        rows="5"
        placeholder="输入单词，支持换行、逗号或分号分隔"
        aria-label="批量录入单词"
      ></textarea>

      <label class="context-field">
        <span>来源语境</span>
        <input v-model="sourceContext" type="text" placeholder="可选：记录句子、笔记或材料来源">
      </label>

      <div class="capture-controls">
        <div class="template-control" aria-label="卡片模板">
          <button
            v-for="option in templateOptions"
            :key="option.key"
            type="button"
            :class="{ active: templateKey === option.key }"
            @click="selectTemplate(option.key)"
          >
            {{ option.label }}
          </button>
        </div>
        <button type="submit" class="capture-submit" :disabled="!terms.length || captureMutation.isPending.value">
          {{ captureMutation.isPending.value ? '录入中...' : `沉淀 ${terms.length || ''} 个单词` }}
        </button>
      </div>
    </form>

    <p v-if="requestError" class="capture-message capture-message--error">{{ requestError }}</p>
    <ul v-if="outcomes.length" class="capture-outcomes" aria-label="录入结果">
      <li v-for="item in outcomes" :key="`${item.term}-${item.action}`">
        <strong>{{ item.term }}</strong>
        <span>{{ outcomeLabel(item.action) }}</span>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch, type Ref } from 'vue'

import {
  type VocabularyCaptureRequest,
  type VocabularyCaptureResponse,
  type VocabularyTemplateCatalog,
  type VocabularyTemplateKey,
} from '@/api/vocabulary'
import { createClientRequestId, parseCaptureTerms } from '@/features/vocabulary/captureTerms'

type CaptureMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: VocabularyCaptureRequest) => Promise<VocabularyCaptureResponse>
}

const props = defineProps<{
  templateCatalog?: VocabularyTemplateCatalog
  captureMutation: CaptureMutation
}>()

const emit = defineEmits<{
  captured: [response: VocabularyCaptureResponse]
}>()

const templateOptions: Array<{ key: VocabularyTemplateKey; label: string }> = [
  { key: 'basic', label: '基础' },
  { key: 'exam', label: '考试' },
  { key: 'reading', label: '阅读' },
]
const rawTerms = ref('')
const sourceContext = ref('')
const templateKey = ref<VocabularyTemplateKey>('basic')
const requestId = ref(createClientRequestId())
const outcomes = ref<VocabularyCaptureResponse['items']>([])
const requestError = ref('')
const selectedTemplate = ref(false)

const terms = computed(() => parseCaptureTerms(rawTerms.value))

watch(
  () => props.templateCatalog?.defaultTemplateKey,
  (defaultTemplateKey) => {
    if (defaultTemplateKey && !selectedTemplate.value) templateKey.value = defaultTemplateKey
  },
  { immediate: true },
)

function selectTemplate(key: VocabularyTemplateKey) {
  selectedTemplate.value = true
  templateKey.value = key
}

async function submitCapture() {
  if (!terms.value.length || props.captureMutation.isPending.value) return

  requestError.value = ''
  outcomes.value = []
  try {
    const response = await props.captureMutation.mutateAsync({
      clientRequestId: requestId.value,
      terms: terms.value,
      language: 'en',
      templateKey: templateKey.value,
      source: {
        type: 'manual',
        sourceTitle: '手动录入',
        contextText: sourceContext.value.trim() || undefined,
        metadata: {},
      },
    })
    outcomes.value = response.items
    rawTerms.value = ''
    requestId.value = createClientRequestId()
    emit('captured', response)
  } catch (error) {
    requestError.value = error instanceof Error ? error.message : '录入失败，请重试'
  }
}

function outcomeLabel(action: string) {
  const labels: Record<string, string> = {
    created: '已收下',
    source_merged: '已存在，已追加来源',
    needs_review: '待确认',
    rejected: '已拒绝',
  }
  return labels[action] ?? action
}
</script>

<style scoped>
.vocabulary-capture-panel { padding: 18px; border: 1px solid #dce7e1; border-radius: 8px; background: #fff; }
header, .capture-controls, .capture-outcomes li { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
header p { margin: 0; color: #059669; font-size: 12px; font-weight: 800; }
header h2 { margin: 3px 0 0; color: #0f172a; font-size: 18px; }
header > span { color: #64748b; font-size: 13px; }
form { display: grid; gap: 12px; margin-top: 16px; }
textarea, input { box-sizing: border-box; width: 100%; border: 1px solid #dce7e1; border-radius: 8px; background: #f8fafc; color: #0f172a; font: inherit; padding: 10px 12px; }
textarea { resize: vertical; line-height: 1.55; }
textarea:focus, input:focus { border-color: #14b8a6; outline: none; box-shadow: 0 0 0 3px rgba(20, 184, 166, .12); }
.context-field { display: grid; gap: 6px; color: #475569; font-size: 13px; font-weight: 700; }
.template-control { display: flex; min-width: 0; overflow: hidden; border: 1px solid #dce7e1; border-radius: 8px; }
.template-control button { flex: 1; min-height: 34px; border: 0; border-right: 1px solid #dce7e1; background: #fff; color: #475569; font: inherit; font-size: 13px; cursor: pointer; }
.template-control button:last-child { border-right: 0; }
.template-control button.active { background: #dcfce7; color: #047857; font-weight: 800; }
.capture-submit { min-height: 36px; border: 0; border-radius: 6px; background: #059669; color: #fff; font: inherit; font-size: 13px; font-weight: 800; padding: 0 14px; cursor: pointer; white-space: nowrap; }
.capture-submit:disabled { cursor: not-allowed; opacity: .55; }
.capture-message { margin: 12px 0 0; font-size: 13px; }.capture-message--error { color: #b91c1c; }
.capture-outcomes { display: grid; gap: 6px; margin: 12px 0 0; padding: 0; list-style: none; }
.capture-outcomes li { min-height: 32px; border-top: 1px solid #edf2f7; color: #334155; font-size: 13px; }.capture-outcomes span { color: #047857; }
@media (max-width: 620px) { .capture-controls { align-items: stretch; flex-direction: column; }.capture-submit { width: 100%; } }
</style>
