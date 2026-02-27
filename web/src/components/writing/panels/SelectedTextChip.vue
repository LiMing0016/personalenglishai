<template>
  <span class="chip" :title="text">
    <span class="chip-text">{{ displayText }}</span>
    <button
      type="button"
      class="chip-dismiss"
      aria-label="取消引用"
      @click="$emit('dismiss')"
    >
      ×
    </button>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  text: string
  maxChars?: number
}>()

defineEmits<{ dismiss: [] }>()

const MAX_LEN = 60

const displayText = computed(() => {
  const t = props.text
  const max = props.maxChars ?? MAX_LEN
  if (!t) return ''
  if (t.length <= max) return t
  return t.slice(0, max) + '…'
})
</script>

<style scoped>
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  min-width: 2em;
  max-width: 240px;
  padding: 4px 6px 4px 10px;
  font-size: 13px;
  line-height: 1.4;
  color: #065f46;
  background: #d1fae5;
  border-radius: 6px;
  white-space: nowrap;
  overflow: hidden;
}
.chip-text {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chip-dismiss {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  padding: 0;
  font-size: 16px;
  line-height: 1;
  color: #047857;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.chip-dismiss:hover {
  color: #064e3b;
  background: #a7f3d0;
}
</style>
