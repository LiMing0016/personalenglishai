<template>
  <div class="ai-note-composer">
    <div class="composer-header">
      <span class="composer-label">给 AI 的补充说明（可选）</span>
      <div class="composer-actions">
        <button
          v-if="modelValue.trim()"
          type="button"
          class="btn-clear"
          title="清空"
          @click="clear"
        >
          清空
        </button>
        <button
          type="button"
          class="btn-collapse"
          title="收起"
          aria-label="收起"
          @click="$emit('collapse')"
        >
          <span class="chevron">⌄</span>
        </button>
      </div>
    </div>
    <textarea
      v-model="localNote"
      class="composer-input"
      placeholder="给 AI 的补充说明（可选），支持中英文，仅作参考…"
      rows="3"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  collapse: []
}>()

const localNote = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v),
})

function clear() {
  emit('update:modelValue', '')
}
</script>

<style scoped>
.ai-note-composer {
  padding: 12px 0 0;
  border-top: 1px solid #e5e7eb;
  margin-top: 8px;
}
.composer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.composer-label {
  font-size: 12px;
  color: #6b7280;
}
.composer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.btn-clear {
  padding: 4px 8px;
  font-size: 12px;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}
.btn-clear:hover {
  color: #111827;
  background: #f3f4f6;
}
.btn-collapse {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: 14px;
  color: #6b7280;
  background: #f3f4f6;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}
.btn-collapse:hover {
  color: #111827;
  background: #e5e7eb;
}
.chevron {
  line-height: 1;
}
.composer-input {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.5;
  color: #111827;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
}
.composer-input::placeholder {
  color: #9ca3af;
}
.composer-input:focus {
  border-color: #047857;
}
</style>
