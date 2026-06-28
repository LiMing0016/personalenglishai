<template>
  <div
    v-if="selectedText"
    class="learning-selection-toolbar"
    :style="{ left: `${left}px`, top: `${top}px` }"
    role="toolbar"
    aria-label="学习资产操作"
  >
    <button
      type="button"
      class="toolbar-button toolbar-button--primary"
      @mousedown.prevent
      @click="$emit('create', 'vocabulary')"
    >
      + 单词卡
    </button>
    <span class="toolbar-divider" aria-hidden="true"></span>
    <button
      type="button"
      class="toolbar-button"
      @mousedown.prevent
      @click="$emit('create', 'grammar')"
    >
      + 语法
    </button>
    <button
      type="button"
      class="toolbar-button"
      :disabled="!canAppendToActive"
      @mousedown.prevent
      @click="$emit('append')"
    >
      加入当前
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
  gap: 4px;
  max-width: min(300px, calc(100vw - 24px));
  padding: 4px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12), 0 1px 0 rgba(15, 23, 42, 0.04);
}

.toolbar-divider {
  width: 1px;
  height: 18px;
  background: #e2e8f0;
}

.toolbar-button {
  min-height: 30px;
  border: 0;
  border-radius: 7px;
  background: #ffffff;
  color: #334155;
  padding: 0 9px;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
}

.toolbar-button--primary {
  background: #e8fff2;
  color: #047857;
}

.toolbar-button:hover,
.toolbar-button:focus-visible {
  background: #f1f5f9;
  color: #047857;
  outline: none;
}

.toolbar-button--primary:hover,
.toolbar-button--primary:focus-visible {
  background: #d6fbe6;
  color: #065f46;
  outline: none;
}

.toolbar-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
