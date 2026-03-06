<template>
  <aside class="right-panel">
    <AiNotePanel
      ref="aiNotePanelRef"
      v-if="panel === 'aiNote'"
      :model-value="aiNote"
      :selected-text-pinned="selectedTextPinned"
      :selection-dismissed="selectionDismissed"
      :selected-span-pinned="selectedSpanPinned"
      :last-chat-result="lastChatResult"
      :conversation-id="conversationId"
      :is-generating="aiGenerating"
      :writing-mode="writingMode"
      :task-prompt="taskPrompt"
      @update:model-value="$emit('update:aiNote', $event)"
      @update:writing-mode="$emit('update:writingMode', $event)"
      @update:task-prompt="$emit('update:taskPrompt', $event)"
      @send="$emit('ai-note-send')"
      @stop="$emit('ai-note-stop')"
      @dismiss-selection="$emit('dismiss-selection')"
      @replace-selection-with="$emit('replace-selection-with', $event)"
      @cleared="$emit('ai-chat-cleared')"
      @close="$emit('close')"
    />
    <ToolPanel v-else :title="scorePanelTitle" @close="$emit('close')">
      <ScorePanel
        v-if="panel === 'score'"
        :evaluate-result="evaluateResult"
        :active-error-id="activeErrorId"
        :submitting="submitting"
        :evaluate-error="evaluateError"
        @error-click="$emit('error-click', $event)"
        @retry="$emit('retry')"
        @close="$emit('close')"
        @start-grammar-check="$emit('start-grammar-check')"
      />
      <GrammarCheckPanel
        v-else-if="panel === 'grammarCheck'"
        :errors="grammarErrors ?? []"
        :checking="grammarChecking ?? false"
        :error="grammarCheckError ?? null"
        :fixed-error-ids="grammarFixedErrorIds ?? new Set()"
        :active-error-id="activeErrorId"
        :essay-text="essay"
        @fix-error="$emit('grammar-fix-error', $event)"
        @fix-all="$emit('grammar-fix-all')"
        @error-click="$emit('error-click', $event)"
        @apply-suggestion="$emit('apply-suggestion', $event)"
        @start-polish="$emit('start-polish')"
        @gpt-errors-loaded="$emit('gpt-errors-loaded', $event)"
        @gpt-suggestions-loaded="$emit('gpt-suggestions-loaded', $event)"
      />
      <RewritePanel
        v-else-if="panel === 'rewrite'"
        :full-essay="essay"
        @replace-sentence="$emit('replace-sentence', $event)"
        @sentence-focus="$emit('sentence-focus', $event)"
      />
      <PolishPanel v-else-if="panel === 'improve'" />
      <ExplainPanel v-else-if="panel === 'explain'" />
      <TranslatePanel v-else-if="panel === 'translate'" />
      <ArchivePanel
        v-else-if="panel === 'archive'"
        @archived="$emit('archived')"
        @load-result="$emit('load-history-result', $event)"
      />
    </ToolPanel>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WritingEvaluateResponse, EvaluationDetailResponse } from '@/api/writing'
import type { PanelMode } from './ToolRail.vue'
import ToolPanel from './ToolPanel.vue'
import ScorePanel from './panels/ScorePanel.vue'
import RewritePanel from './panels/RewritePanel.vue'
import GrammarCheckPanel from './panels/GrammarCheckPanel.vue'
import PolishPanel from './panels/PolishPanel.vue'
import ExplainPanel from './panels/ExplainPanel.vue'
import TranslatePanel from './panels/TranslatePanel.vue'
import ArchivePanel from './panels/ArchivePanel.vue'
import AiNotePanel from './panels/AiNotePanel.vue'

const props = defineProps<{
  panel: PanelMode
  title: string
  width: number
  essay: string
  selectionState: { text: string; start: number; end: number } | null
  selectionDismissed: boolean
  selectedTextPinned: string
  selectedSpanPinned: { start: number; end: number } | null
  lastChatResult: { displayText: string; replaceText?: string } | null
  conversationId: string
  aiGenerating: boolean
  writingMode: 'free' | 'exam'
  taskPrompt: string
  aiNote: string
  evaluateResult: WritingEvaluateResponse | null
  activeErrorId?: string | null
  submitting?: boolean
  evaluateError?: string | null
  grammarErrors?: WritingEvaluateResponse['errors']
  grammarChecking?: boolean
  grammarCheckError?: string | null
  grammarFixedErrorIds?: Set<string>
  rewriteSuggestions?: WritingEvaluateResponse['errors']
}>()

defineEmits<{
  close: []
  'error-click': [errorId: string]
  'apply-polish': [payload: { errorId: string; polished: string }]
  'replace-sentence': [payload: { original: string; replacement: string }]
  'sentence-focus': [range: { start: number; end: number } | null]
  'start-polish': []
  'grammar-fix-error': [errorId: string]
  'grammar-fix-all': []
  'apply-suggestion': [payload: { original: string; suggestion: string }]
  'gpt-errors-loaded': [errors: import('@/api/writing').SuggestionErrorItem[]]
  'gpt-suggestions-loaded': [suggestions: import('@/api/writing').SuggestionItem[]]
  retry: []
  'start-grammar-check': []
  'dismiss-selection': []
  'replace-selection-with': [resultText: string]
  archived: []
  'load-history-result': [detail: EvaluationDetailResponse]
  'update:aiNote': [value: string]
  'ai-note-send': []
  'ai-note-stop': []
  'ai-chat-cleared': []
  'update:writingMode': [value: 'free' | 'exam']
  'update:taskPrompt': [value: string]
}>()

const scorePanelTitle = computed(() => {
  if (props.panel === 'score') return '评价与建议'
  if (props.panel === 'grammarCheck') return '语法检查'
  if (props.panel === 'rewrite') return '润色'
  return props.title
})

type RecentMessageDto = { role: 'user' | 'assistant'; content: string }

const aiNotePanelRef = ref<{
  setComposerText: (text: string) => void
  focusComposer: () => void
  getRecentMessages: (max?: number) => RecentMessageDto[]
  isIncludeDraft: () => boolean
} | null>(null)

function setAiComposerText(text: string): boolean {
  if (props.panel !== 'aiNote') return false
  aiNotePanelRef.value?.setComposerText(text)
  return true
}

function focusAiComposer(): boolean {
  if (props.panel !== 'aiNote') return false
  aiNotePanelRef.value?.focusComposer()
  return true
}

function getAiRecentMessages(max = 8): RecentMessageDto[] {
  if (props.panel !== 'aiNote') return []
  return aiNotePanelRef.value?.getRecentMessages(max) ?? []
}

function isIncludeDraft(): boolean {
  if (props.panel !== 'aiNote') return false
  return aiNotePanelRef.value?.isIncludeDraft() ?? false
}

defineExpose<{
  setAiComposerText: (text: string) => boolean
  focusAiComposer: () => boolean
  getAiRecentMessages: (max?: number) => RecentMessageDto[]
  isIncludeDraft: () => boolean
}>({
  setAiComposerText,
  focusAiComposer,
  getAiRecentMessages,
  isIncludeDraft,
})
</script>

<style scoped>
.right-panel {
  flex-shrink: 0;
  min-width: 320px;
  max-width: none;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
  overflow: hidden;
}
:deep(.tool-panel-body) {
  padding-right: var(--assistant-safe-padding-right, 16px);
  box-sizing: border-box;
}
</style>
