<template>
  <div class="canvas-area">
    <header class="canvas-header">
      <span class="canvas-title">自由写作</span>
    </header>
    <div class="canvas-editor-wrap">
      <textarea
        v-model="localDraft"
        class="canvas-editor"
        placeholder="在此输入英文作文…"
        spellcheck="true"
        @input="emit('update:draftText', ($event.target as HTMLTextAreaElement).value)"
      />
    </div>
    <footer class="canvas-statusbar">
      <span class="word-count">{{ wordCount }} 词</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  draftText: string
}>()

const emit = defineEmits<{
  'update:draftText': [value: string]
}>()

const localDraft = computed({
  get: () => props.draftText,
  set: (v: string) => emit('update:draftText', v),
})

const wordCount = computed(() => {
  const t = props.draftText.trim()
  if (!t) return 0
  return t.split(/\s+/).filter(Boolean).length
})
</script>

<style scoped>
.canvas-area {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  height: 100%;
  background: #f5f6f7;
}
.canvas-header {
  flex-shrink: 0;
  padding: 16px 24px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.canvas-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}
.canvas-editor-wrap {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  display: flex;
  justify-content: center;
}
.canvas-editor {
  width: 100%;
  max-width: 900px;
  min-height: 360px;
  padding: 32px 40px;
  font-size: 16px;
  line-height: 1.75;
  color: #1a1a1a;
  background: #fff;
  border: none;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  resize: vertical;
  font-family: Georgia, 'Times New Roman', serif;
  outline: none;
}
.canvas-editor::placeholder {
  color: #9ca3af;
}
.canvas-statusbar {
  flex-shrink: 0;
  padding: 8px 24px;
  font-size: 12px;
  color: #6b7280;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}
</style>
