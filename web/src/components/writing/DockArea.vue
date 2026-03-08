<template>
  <div class="dock-area" :style="{ width: `${width}px` }">
    <header class="dock-header">
      <span class="dock-title">{{ modeLabel }}</span>
    </header>
    <div class="dock-body">
      <ScorePanel v-if="mode === 'score'" />
      <PolishPanel v-else-if="mode === 'improve'" />
      <ExplainPanel v-else-if="mode === 'explain'" />
      <TranslatePanel v-else-if="mode === 'translate'" />
    </div>
    <footer class="dock-footer">
      <AiNoteComposer
        v-if="aiNoteOpen"
        :model-value="aiNote"
        @update:model-value="$emit('update:ai-note', $event)"
        @collapse="$emit('collapse-composer')"
      />
      <slot v-else name="footer" />
    </footer>
  </div>
</template>

<style scoped>
.dock-area {
  flex-shrink: 0;
  min-width: 320px;
  max-width: 50vw;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  overflow: hidden;
}
.dock-header {
  flex-shrink: 0;
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.dock-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
.dock-footer {
  flex-shrink: 0;
  padding: 10px 16px;
  border-top: 1px solid #e5e7eb;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import type { PanelMode } from './ToolRail.vue'
import AiNoteComposer from './AiNoteComposer.vue'
import ScorePanel from './panels/ScorePanel.vue'
import PolishPanel from './panels/PolishPanel.vue'
import ExplainPanel from './panels/ExplainPanel.vue'
import TranslatePanel from './panels/TranslatePanel.vue'

const props = defineProps<{
  mode: PanelMode
  width: number
  aiNoteOpen: boolean
  aiNote: string
}>()

defineEmits<{
  'switch-mode': [mode: PanelMode]
  'update:ai-note': [value: string]
  'collapse-composer': []
}>()

const modeLabels: Record<PanelMode, string> = {
  score: '评分',
  grammarCheck: '语法检查',
  rewrite: '改写',
  structure: '结构',
  improve: '提升',
  explain: '解释',
  translate: '翻译',
  aiNote: 'AI 助手',
}

const modeLabel = computed(() => modeLabels[props.mode])
</script>