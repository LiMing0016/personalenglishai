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
        :exam-max-score="examMaxScore"
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
        :locked="examFirstWriteLocked"
        @fix-error="$emit('grammar-fix-error', $event)"
        @fix-all="$emit('grammar-fix-all')"
        @dismiss-error="$emit('grammar-dismiss-error', $event)"
        @error-click="$emit('error-click', $event)"
        @apply-suggestion="$emit('apply-suggestion', $event)"
        @start-polish="$emit('start-polish')"
        @gpt-errors-loaded="$emit('gpt-errors-loaded', $event)"
        @gpt-suggestions-loaded="$emit('gpt-suggestions-loaded', $event)"
      />
      <StructurePanel
        v-else-if="panel === 'structure'"
        :essay="essay"
        @paragraph-click="$emit('paragraph-click', $event)"
      />
      <RewritePanel
        v-else-if="panel === 'rewrite'"
        :full-essay="essay"
        :locked="examFirstWriteLocked"
        @replace-sentence="$emit('replace-sentence', $event)"
        @sentence-focus="$emit('sentence-focus', $event)"
      />
      <PolishPanel
        v-else-if="panel === 'improve'"
        :full-essay="essay"
        :task-prompt="taskPrompt"
        :study-stage="studyStage"
        :writing-mode="writingMode"
      />
      <ExplainPanel
        v-else-if="panel === 'explain'"
        :task-prompt="taskPrompt"
        :study-stage="studyStage"
        :writing-mode="writingMode"
      />
      <TranslatePanel
        v-else-if="panel === 'translate'"
        @sentence-focus="$emit('sentence-focus', $event)"
      />
    </ToolPanel>
  </aside>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import type { WritingEvaluateResponse } from '@/api/writing'
import type { PanelMode } from './ToolRail.vue'
import ToolPanel from './ToolPanel.vue'
// Static: high-frequency panels loaded on first visit
import AiNotePanel from './panels/AiNotePanel.vue'
import GrammarCheckPanel from './panels/GrammarCheckPanel.vue'
// Lazy: low-frequency panels loaded on demand
const ScorePanel = defineAsyncComponent(() => import('./panels/ScorePanel.vue'))
const RewritePanel = defineAsyncComponent(() => import('./panels/RewritePanel.vue'))
const StructurePanel = defineAsyncComponent(() => import('./panels/StructurePanel.vue'))
const PolishPanel = defineAsyncComponent(() => import('./panels/PolishPanel.vue'))
const ExplainPanel = defineAsyncComponent(() => import('./panels/ExplainPanel.vue'))
const TranslatePanel = defineAsyncComponent(() => import('./panels/TranslatePanel.vue'))

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
  examMaxScore?: number | null
  examFirstWriteLocked?: boolean
  studyStage?: string | null
}>()

defineEmits<{
  close: []
  'error-click': [errorId: string]
  'apply-polish': [payload: { errorId: string; polished: string }]
  'replace-sentence': [payload: { start: number; end: number; original: string; replacement: string }]
  'sentence-focus': [range: { start: number; end: number } | null]
  'start-polish': []
  'grammar-fix-error': [errorId: string]
  'grammar-fix-all': []
  'grammar-dismiss-error': [errorId: string]
  'apply-suggestion': [payload: { original: string; suggestion: string }]
  'gpt-errors-loaded': [errors: import('@/api/writing').SuggestionErrorItem[]]
  'gpt-suggestions-loaded': [suggestions: import('@/api/writing').SuggestionItem[]]
  retry: []
  'paragraph-click': [offset: number]
  'start-grammar-check': []
  'dismiss-selection': []
  'replace-selection-with': [resultText: string]
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
  if (props.panel === 'structure') return '段落结构'
  if (props.panel === 'rewrite') return '润色'
  if (props.panel === 'improve') return '写作模版'
  if (props.panel === 'explain') return '写作素材'
  if (props.panel === 'translate') return '翻译'
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
