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
      @mousedown.prevent
      @click="$emit('create')"
    >
      新建单词卡
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  selectedText: string
  left: number
  top: number
}>()

defineEmits<{
  create: []
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

button {
  min-height: 30px;
  border: 0;
  border-radius: 6px;
  background: #047857;
  color: #ffffff;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

button:hover,
button:focus-visible {
  background: #065f46;
  outline: none;
}
</style>
