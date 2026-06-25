<template>
  <div
    v-if="selectedText"
    class="learning-selection-toolbar"
    :style="{ left: `${left}px`, top: `${top}px` }"
    role="toolbar"
    aria-label="学习资产操作"
  >
    <span class="selection-text">{{ selectedText }}</span>
    <button
      type="button"
      class="toolbar-button toolbar-button--primary"
      @mousedown.prevent
      @click="$emit('create', 'vocabulary')"
    >
      新建单词卡
    </button>
    <button
      type="button"
      class="toolbar-button"
      @mousedown.prevent
      @click="$emit('create', 'grammar')"
    >
      新建语法笔记
    </button>
    <button
      type="button"
      class="toolbar-button"
      :disabled="!canAppendToActive"
      @mousedown.prevent
      @click="$emit('append')"
    >
      加入当前笔记
    </button>
  </div>
</template>

<script setup lang="ts">
import type { LearningAssetType } from '@/types/learningAssets.ts'

defineProps<{
  selectedText: string
  left: number
  top: number
  canAppendToActive: boolean
}>()

defineEmits<{
  create: [type: LearningAssetType]
  append: []
}>()
</script>

<style scoped>
.learning-selection-toolbar {
  position: fixed;
  z-index: 70;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: min(360px, calc(100vw - 24px));
  padding: 6px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.14);
}

.selection-text {
  max-width: 150px;
  overflow: hidden;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-button {
  min-height: 30px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  background: #ffffff;
  color: #334155;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.toolbar-button--primary {
  border-color: #047857;
  background: #047857;
  color: #ffffff;
}

.toolbar-button:hover,
.toolbar-button:focus-visible {
  border-color: #047857;
  color: #047857;
  outline: none;
}

.toolbar-button--primary:hover,
.toolbar-button--primary:focus-visible {
  background: #065f46;
  color: #ffffff;
  outline: none;
}

.toolbar-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
