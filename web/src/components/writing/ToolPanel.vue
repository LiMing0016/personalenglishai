<template>
  <div class="tool-panel">
    <button type="button" class="btn-close tool-panel-close" title="关闭" aria-label="关闭" @click="$emit('close')">
      ×
    </button>
    <div class="tool-panel-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
}>()

defineEmits<{
  close: []
}>()
</script>

<style scoped>
.tool-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  background: #fff;
  overflow: hidden;
}
.tool-panel-close {
  position: absolute;
  top: 12px;
  right: var(--assistant-safe-padding-right, 16px);
  z-index: 2;
}
.btn-close {
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: 18px;
  line-height: 1;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}
.btn-close:hover {
  color: #111827;
  background: #f3f4f6;
}
.tool-panel-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding-top: 12px;
}
</style>
