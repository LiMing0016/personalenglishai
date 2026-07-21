<template>
  <div class="markdown-editor">
    <div class="markdown-editor__label-row">
      <label for="vocabulary-markdown">Markdown 内容</label>
      <span :class="{ 'markdown-editor__count--error': tooLong }">{{ modelValue.length.toLocaleString() }} / 20,000</span>
    </div>
    <textarea
      id="vocabulary-markdown"
      :value="modelValue"
      maxlength="20000"
      rows="12"
      :aria-invalid="tooLong"
      aria-describedby="vocabulary-markdown-status"
      @input="updateValue"
    ></textarea>
    <p id="vocabulary-markdown-status" :class="{ 'markdown-editor__error': tooLong }" aria-live="polite">
      {{ tooLong ? '超过 20,000 字限制，请缩短内容后保存。' : '保留 Markdown 源码格式' }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const tooLong = computed(() => props.modelValue.length > 20_000)

function updateValue(event: Event) {
  const input = event.target as HTMLTextAreaElement
  emit('update:modelValue', input.value)
}
</script>

<style scoped>
.markdown-editor { min-width: 0; display: grid; gap: 6px; }
.markdown-editor__label-row { min-width: 0; display: flex; justify-content: space-between; gap: 12px; color: #475569; font-size: 13px; }
.markdown-editor__label-row label { font-weight: 800; }
.markdown-editor__label-row span { flex: none; color: #64748b; font-variant-numeric: tabular-nums; }
.markdown-editor textarea { box-sizing: border-box; width: 100%; min-width: 0; min-height: clamp(420px, calc(100vh - 430px), 720px); max-height: 820px; resize: vertical; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #0f172a; font: 14px/1.65 ui-monospace, SFMono-Regular, Consolas, monospace; padding: 18px 20px; white-space: pre-wrap; overflow-wrap: anywhere; }
.markdown-editor p { min-height: 18px; margin: 0; color: #64748b; font-size: 12px; }
.markdown-editor__count--error, .markdown-editor__error { color: #b91c1c !important; }
@media (max-width: 767px) { .markdown-editor textarea { min-height: 360px; padding: 14px; } }
</style>
