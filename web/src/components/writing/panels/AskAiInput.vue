<template>
  <div class="ask-ai-input" :class="{ focused: isFocused }">
    <SelectedTextChip
      v-if="selectedText"
      :text="selectedText"
      :max-chars="60"
      @dismiss="$emit('dismiss-selection')"
    />
    <textarea
      :value="modelValue"
      class="ask-ai-textarea"
      :placeholder="placeholder"
      rows="3"
      @input="onInput"
      @focus="isFocused = true"
      @blur="isFocused = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import SelectedTextChip from './SelectedTextChip.vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    selectedText: string
    placeholder?: string
  }>(),
  { placeholder: '输入需求，如：帮我润色、简化这句…' }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'dismiss-selection': []
}>()

const isFocused = ref(false)

function onInput(e: Event) {
  const value = (e.target as HTMLTextAreaElement).value
  emit('update:modelValue', value)
}
</script>

<style scoped>
.ask-ai-input {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 80px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.5;
  color: #111827;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-sizing: border-box;
}
.ask-ai-input.focused {
  border-color: #047857;
  outline: none;
}
.ask-ai-textarea {
  flex: 1;
  min-width: 0;
  padding: 0;
  font-size: inherit;
  line-height: inherit;
  color: inherit;
  background: none;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
}
.ask-ai-textarea::placeholder {
  color: #9ca3af;
}
</style>
