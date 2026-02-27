<template>
  <div class="ai-note-bar" :class="{ expanded: isExpanded }">
    <div class="ai-note-bar-inner">
      <p v-show="isExpanded" class="ai-note-hint">此内容仅作评估参考，不影响评分。</p>
      <div v-show="isExpanded" class="input-row">
        <button type="button" class="btn-upload" title="上传图片/文件（mock）" @click="onUploadMock">
          📎
        </button>
        <textarea
          v-model="localNote"
          class="ai-note-input"
          placeholder="给 AI 的补充说明（可选），支持中英文…"
          rows="2"
          @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
          @keydown.enter.exact.prevent="submit"
        />
        <button type="button" class="btn-submit" title="提交" @click="submit">
          发送
        </button>
      </div>
      <button
        type="button"
        class="btn-toggle"
        :title="isExpanded ? '收起' : '展开'"
        :aria-expanded="isExpanded"
        @click="isExpanded = !isExpanded"
      >
        <span class="chevron">{{ isExpanded ? '⌄' : '⌃' }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { showToast } from '@/utils/toast'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: []
}>()

const isExpanded = ref(true)

const localNote = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v),
})

function onUploadMock() {
  showToast('上传功能即将开放', 'info')
}

function submit() {
  if (!props.modelValue.trim()) return
  emit('submit')
  showToast('已作为参考提交（mock）', 'info')
}
</script>

<style scoped>
.ai-note-bar {
  position: fixed;
  bottom: 0;
  left: 24px;
  right: var(--writing-right-inset, 24px);
  z-index: 60;
  padding: 12px 0 24px;
  pointer-events: none;
}
.ai-note-bar-inner {
  pointer-events: auto;
  max-width: 720px;
  margin: 0 auto;
  padding: 16px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid #e5e7eb;
}
.ai-note-bar:not(.expanded) .ai-note-bar-inner {
  padding: 10px 16px;
}
.ai-note-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}
.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}
.btn-upload {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  padding: 0;
  font-size: 18px;
  background: #f3f4f6;
  border: none;
  border-radius: 12px;
  cursor: pointer;
}
.btn-upload:hover {
  background: #e5e7eb;
}
.ai-note-input {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.5;
  color: #111827;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  resize: none;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
}
.ai-note-input::placeholder {
  color: #9ca3af;
}
.ai-note-input:focus {
  border-color: #047857;
}
.btn-submit {
  flex-shrink: 0;
  padding: 10px 18px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 12px;
  cursor: pointer;
}
.btn-submit:hover {
  background: #065f46;
}
.btn-toggle {
  margin-top: 10px;
  margin-left: auto;
  display: block;
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
.btn-toggle:hover {
  background: #e5e7eb;
  color: #111827;
}
.chevron {
  line-height: 1;
}
</style>
