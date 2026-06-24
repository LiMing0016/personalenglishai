<template>
  <aside class="learning-asset-canvas" aria-label="学习资产画布">
    <header class="canvas-header">
      <div>
        <span class="canvas-kicker">学习资产画布</span>
        <input
          :value="draft.title"
          class="title-input"
          type="text"
          aria-label="单词"
          @input="handleTitleInput"
        >
      </div>
      <div class="header-actions">
        <span class="type-chip">单词卡</span>
        <button type="button" class="icon-button" aria-label="关闭学习资产画布" @click="$emit('close')">×</button>
      </div>
    </header>

    <section class="canvas-toolbar" aria-label="画布操作">
      <button type="button" :disabled="isOrganizing" @click="$emit('organize', 'create')">
        {{ isOrganizing ? '整理中' : 'AI 整理' }}
      </button>
      <button type="button" :disabled="isOrganizing" @click="$emit('organize', 'format')">
        调整格式
      </button>
      <button type="button" class="save-button" :disabled="isSaving" @click="$emit('save')">
        {{ isSaving ? '保存中' : '保存' }}
      </button>
    </section>

    <p v-if="errorMessage" class="canvas-error">{{ errorMessage }}</p>

    <textarea
      class="markdown-editor"
      :value="draft.contentMarkdown"
      spellcheck="false"
      aria-label="单词卡 Markdown 正文"
      @input="handleContentInput"
    ></textarea>

    <section v-if="candidateMarkdown" class="candidate-panel" aria-label="AI 整理候选">
      <header>
        <strong>AI 候选预览</strong>
        <div>
          <button type="button" @click="$emit('cancelCandidate')">取消候选</button>
          <button type="button" class="apply-button" @click="$emit('applyCandidate')">应用候选</button>
        </div>
      </header>
      <pre>{{ candidateMarkdown }}</pre>
    </section>
  </aside>
</template>

<script setup lang="ts">
import type { LearningAssetDraft } from '../../types/learningAssets.ts'

defineProps<{
  draft: LearningAssetDraft
  candidateMarkdown: string
  isOrganizing: boolean
  isSaving: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  close: []
  organize: [mode: 'create' | 'format']
  save: []
  applyCandidate: []
  cancelCandidate: []
  'update:title': [title: string]
  'update:contentMarkdown': [contentMarkdown: string]
}>()

function handleTitleInput(event: Event) {
  emit('update:title', (event.target as HTMLInputElement).value)
}

function handleContentInput(event: Event) {
  emit('update:contentMarkdown', (event.target as HTMLTextAreaElement).value)
}
</script>

<style scoped>
.learning-asset-canvas {
  display: flex;
  flex: 0 0 420px;
  width: 420px;
  max-width: 420px;
  min-width: 0;
  height: 100vh;
  min-height: 0;
  flex-direction: column;
  border-left: 1px solid #dbe3ea;
  background: #ffffff;
  box-sizing: border-box;
}

.canvas-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid #e2e8f0;
}

.canvas-kicker {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.title-input {
  width: 100%;
  min-width: 0;
  border: 0;
  background: transparent;
  color: #0f172a;
  padding: 0;
  font-size: 22px;
  font-weight: 900;
  outline: none;
}

.header-actions,
.canvas-toolbar,
.candidate-panel header,
.candidate-panel header div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  border-radius: 999px;
  background: #dcfce7;
  color: #047857;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.icon-button {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: #f1f5f9;
  color: #334155;
  font-size: 18px;
  cursor: pointer;
}

.canvas-toolbar {
  flex-wrap: wrap;
  padding: 12px 18px;
  border-bottom: 1px solid #e2e8f0;
}

.canvas-toolbar button,
.candidate-panel button {
  min-height: 34px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  background: #ffffff;
  color: #334155;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.canvas-toolbar button:disabled,
.candidate-panel button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.canvas-toolbar .save-button,
.candidate-panel .apply-button {
  border-color: #047857;
  background: #047857;
  color: #ffffff;
}

.canvas-error {
  margin: 12px 18px 0;
  border-radius: 6px;
  background: #fef2f2;
  color: #991b1b;
  padding: 10px 12px;
  font-size: 13px;
}

.markdown-editor {
  flex: 1;
  min-height: 0;
  width: 100%;
  resize: none;
  border: 0;
  border-bottom: 1px solid #e2e8f0;
  background: #fbfdff;
  color: #0f172a;
  padding: 18px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 14px;
  line-height: 1.7;
  outline: none;
  box-sizing: border-box;
}

.candidate-panel {
  flex: 0 0 240px;
  min-height: 180px;
  overflow: hidden;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}

.candidate-panel header {
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
}

.candidate-panel strong {
  color: #0f172a;
  font-size: 13px;
}

.candidate-panel pre {
  height: calc(100% - 59px);
  margin: 0;
  overflow: auto;
  padding: 14px;
  color: #334155;
  white-space: pre-wrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.65;
}

@media (max-width: 960px) {
  .learning-asset-canvas {
    position: fixed;
    inset: 0 0 0 auto;
    z-index: 65;
    width: min(100vw, 420px);
    max-width: 100vw;
  }
}
</style>
