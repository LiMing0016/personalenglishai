<template>
  <section class="vocabulary-capture-panel" aria-labelledby="capture-heading">
    <div>
      <h2 id="capture-heading">导入单词</h2>
      <p>输入、粘贴或添加图片</p>
    </div>
    <button type="button" class="capture-open" @click="dialogOpen = true">
      <Plus :size="18" aria-hidden="true" />
      导入单词
    </button>

    <VocabularyImportDialog
      v-if="dialogOpen"
      :theme-catalog="themeCatalog"
      :themes-loading="themesLoading"
      :themes-error="themesError"
      :capture-mutation="captureMutation"
      :import-analysis-enabled="importAnalysisEnabled"
      :import-analysis-mutation="importAnalysisMutation"
      @close="dialogOpen = false"
      @captured="handleCaptured"
    />

    <ul v-if="outcomes.length" class="capture-outcomes" aria-label="最近导入结果">
      <li v-for="(item, index) in outcomes" :key="`${item.term}-${item.action}-${index}`">
        <strong>{{ item.term }}</strong>
        <span>{{ outcomeLabel(item.action) }}</span>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { ref, type Ref } from 'vue'
import { Plus } from 'lucide-vue-next'

import type {
  VocabularyCaptureRequest,
  VocabularyCaptureResponse,
  VocabularyImportAnalysisResponse,
  VocabularyThemeCatalog,
} from '@/api/vocabulary'
import VocabularyImportDialog from './VocabularyImportDialog.vue'

type CaptureMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: VocabularyCaptureRequest) => Promise<VocabularyCaptureResponse>
}

type ImportAnalysisMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: {
    text: string
    file: File | null
    inputFingerprint: string
    signal: AbortSignal
  }) => Promise<VocabularyImportAnalysisResponse>
}

defineProps<{
  themeCatalog?: VocabularyThemeCatalog
  themesLoading?: boolean
  themesError?: boolean
  captureMutation: CaptureMutation
  importAnalysisEnabled: boolean
  importAnalysisMutation: ImportAnalysisMutation
}>()

const emit = defineEmits<{
  captured: [response: VocabularyCaptureResponse]
}>()

const dialogOpen = ref(false)
const outcomes = ref<VocabularyCaptureResponse['items']>([])

function handleCaptured(response: VocabularyCaptureResponse) {
  outcomes.value = response.items
  emit('captured', response)
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
.vocabulary-capture-panel {
  box-sizing: border-box;
  display: flex;
  min-width: 0;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
}

.vocabulary-capture-panel h2,
.vocabulary-capture-panel p {
  margin: 0;
}

.vocabulary-capture-panel h2 {
  color: #0f172a;
  font-size: 16px;
}

.vocabulary-capture-panel p {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.capture-open {
  display: inline-flex;
  min-height: 38px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid #059669;
  border-radius: 6px;
  background: #059669;
  color: #ffffff;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
  cursor: pointer;
}

.capture-open:hover,
.capture-open:focus-visible {
  background: #047857;
  outline: none;
}

.capture-outcomes {
  display: none;
}

@media (max-width: 520px) {
  .vocabulary-capture-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .capture-open {
    width: 100%;
  }
}
</style>
