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
        @start-fix="$emit('start-fix')"
        @error-click="$emit('error-click', $event)"
        @retry="$emit('retry')"
      />
      <RewritePanel
        v-else-if="panel === 'rewrite'"
        :essay="essay"
        :ai-hint="aiNote"
        @apply-rewrite="$emit('apply-rewrite', $event)"
      />
      <FixPanel v-else-if="panel === 'revise'" />
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
import FixPanel from './panels/FixPanel.vue'
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
}>()

defineEmits<{
  close: []
  'start-fix': []
  'error-click': [errorId: string]
  retry: []
  'apply-rewrite': [fullText: string]
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

const scorePanelTitle = computed(() =>
  props.panel === 'score' ? '评价与建议' : props.title
)

type RecentMessageDto = { role: 'user' | 'assistant'; content: string }

const aiNotePanelRef = ref<{
  setComposerText: (text: string) => void
  focusComposer: () => void
  getRecentMessages: (max?: number) => RecentMessageDto[]
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

defineExpose<{
  setAiComposerText: (text: string) => boolean
  focusAiComposer: () => boolean
  getAiRecentMessages: (max?: number) => RecentMessageDto[]
}>({
  setAiComposerText,
  focusAiComposer,
  getAiRecentMessages,
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

