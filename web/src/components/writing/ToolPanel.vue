<template>
  <div class="tool-panel">
    <header class="tool-panel-header">
      <span class="tool-panel-title">{{ title }}</span>
      <div class="tool-panel-actions">
        <button type="button" class="btn-close" title="关闭" aria-label="关闭" @click="$emit('close')">
          ×
        </button>
      </div>
    </header>
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
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  background: #fff;
  overflow: hidden;
}
.tool-panel-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px var(--assistant-safe-padding-right, 16px) 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}
.tool-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.tool-panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
}
</style>
