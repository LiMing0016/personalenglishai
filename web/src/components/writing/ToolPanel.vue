<template>
  <div class="tool-panel">
    <header class="tool-panel-header">
      <span class="tool-panel-title">{{ title }}</span>
      <div class="tool-panel-actions">
        <span class="size-switcher">
          <button
            v-for="s in sizes"
            :key="s"
            type="button"
            class="size-btn"
            :class="{ active: currentSize === s }"
            @click="currentSize = s"
          >
            {{ s }}
          </button>
        </span>
        <button type="button" class="btn-close" title="关闭" aria-label="关闭" @click="$emit('close')">
          ×
        </button>
      </div>
    </header>
    <div class="tool-panel-body" :class="currentSize">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  title: string
}>()

defineEmits<{
  close: []
}>()

const sizes = ['sm', 'md', 'lg'] as const
const currentSize = ref<'sm' | 'md' | 'lg'>('md')
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
.size-switcher {
  display: flex;
  gap: 2px;
}
.size-btn {
  padding: 4px 8px;
  font-size: 11px;
  color: #6b7280;
  background: #f3f4f6;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.size-btn:hover {
  color: #111827;
}
.size-btn.active {
  background: #e5e7eb;
  color: #111827;
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
.tool-panel-body.sm {
  max-width: 100%;
}
.tool-panel-body.md {
  max-width: 100%;
}
.tool-panel-body.lg {
  max-width: 100%;
}
</style>

