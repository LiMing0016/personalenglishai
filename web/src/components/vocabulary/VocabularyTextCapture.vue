<template>
  <label class="text-capture">
    <span>输入单词</span>
    <textarea
      :value="modelValue"
      rows="5"
      placeholder="支持换行、逗号或分号分隔"
      aria-label="输入要沉淀的单词"
      @input="updateValue"
    ></textarea>
  </label>
</template>

<script setup lang="ts">
import { parseCaptureTerms } from '@/features/vocabulary/captureTerms'

defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  terms: [terms: string[]]
}>()

function updateValue(event: Event) {
  const value = (event.target as HTMLTextAreaElement).value
  emit('update:modelValue', value)
  emit('terms', parseCaptureTerms(value))
}
</script>

<style scoped>
.text-capture { display: grid; min-width: 0; gap: 6px; color: #334155; font-size: 13px; font-weight: 800; }
.text-capture textarea { box-sizing: border-box; width: 100%; min-height: 118px; resize: vertical; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #0f172a; font: inherit; line-height: 1.55; padding: 10px 12px; }
.text-capture textarea:focus { border-color: #14b8a6; outline: none; box-shadow: 0 0 0 3px rgba(20, 184, 166, .12); }
</style>
