<template>
  <div class="ask-ai-bar" :class="{ expanded: isExpanded }">
    <div class="ask-ai-bar-inner">
      <span class="ask-ai-label">
        <span class="ask-ai-icon" aria-hidden="true">◇</span>
        <span>给 AI 补充说明</span>
      </span>
      <div class="ask-ai-input-wrap" @click="focusInput">
        <input
          v-if="!isExpanded"
          ref="inputRef"
          v-model="localValue"
          type="text"
          class="ask-ai-input single"
      placeholder="输入说明或问题（可选），支持中英文…"
          @focus="expand"
        />
        <textarea
          v-else
          ref="textareaRef"
          v-model="localValue"
          class="ask-ai-input multi"
          placeholder="输入说明或问题（可选），支持中英文…"
          rows="3"
          @blur="maybeCollapse"
        />
      </div>
      <button
        type="button"
        class="ask-ai-btn"
        :title="isExpanded ? '收起' : '展开'"
        :aria-expanded="isExpanded"
        @click="toggleExpand"
      >
        <span v-if="!isExpanded" class="btn-icon">⌃</span>
        <span v-else class="btn-icon">⌄</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const localValue = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v),
})

const isExpanded = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

function expand() {
  isExpanded.value = true
}

function collapse() {
  isExpanded.value = false
}

function toggleExpand() {
  isExpanded.value = !isExpanded.value
  if (!isExpanded.value) {
    inputRef.value?.focus()
  } else {
    textareaRef.value?.focus()
  }
}

function focusInput() {
  if (isExpanded.value) {
    textareaRef.value?.focus()
  } else {
    inputRef.value?.focus()
  }
}

function maybeCollapse() {
  if (!localValue.value.trim()) {
    setTimeout(() => collapse(), 150)
  }
}
</script>

<style scoped>
.ask-ai-bar {
  position: fixed;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  max-width: 1100px;
  width: calc(100% - 48px);
  z-index: 50;
  pointer-events: none;
}

.ask-ai-bar-inner {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px 10px 16px;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  border: 1px solid #e5e7eb;
  min-height: 48px;
}

.ask-ai-label {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  font-size: 13px;
  color: #6b7280;
}

.ask-ai-icon {
  font-size: 14px;
  color: #2563eb;
}

.ask-ai-input-wrap {
  flex: 1;
  min-width: 0;
}

.ask-ai-input {
  width: 100%;
  padding: 6px 0;
  font-size: 14px;
  line-height: 1.5;
  color: #111827;
  border: none;
  background: transparent;
  outline: none;
  font-family: inherit;
  resize: none;
}

.ask-ai-input::placeholder {
  color: #9ca3af;
}

.ask-ai-input.single {
  height: 24px;
}

.ask-ai-input.multi {
  min-height: 60px;
  vertical-align: top;
}

.ask-ai-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  font-size: 18px;
  line-height: 1;
  color: #6b7280;
  background: #f3f4f6;
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.ask-ai-btn:hover {
  background: #e5e7eb;
  color: #111827;
}

.ask-ai-bar.expanded .ask-ai-bar-inner {
  align-items: flex-end;
}

.ask-ai-bar.expanded .ask-ai-btn {
  margin-bottom: 2px;
}
</style>
