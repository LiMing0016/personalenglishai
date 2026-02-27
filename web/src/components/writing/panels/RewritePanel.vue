<template>
  <div class="rewrite-panel">
    <div class="instruction-row">
      <input
        v-model="instruction"
        type="text"
        class="instruction-input"
        placeholder="输入改进需求，如：让语气更正式、简化长句…"
        @keydown.enter.exact.prevent="send"
      />
      <button
        type="button"
        class="btn-send"
        :disabled="!canSend || loading"
        @click="send"
      >
        {{ loading ? '请求中…' : '发送' }}
      </button>
    </div>
    <template v-if="lastResponse">
      <div class="assistant-block">
        <span class="block-label">AI 回复</span>
        <p class="assistant-text">{{ lastResponse.assistantMessage }}</p>
      </div>
      <div v-if="lastResponse.rewrite?.fullText" class="rewrite-block">
        <span class="block-label">改写全文</span>
        <div class="rewrite-text">{{ lastResponse.rewrite.fullText }}</div>
        <button type="button" class="btn-apply" @click="apply">
          应用到作文
        </button>
      </div>
    </template>
    <p v-else class="rewrite-hint">输入改进需求并发送，AI 将返回改写后的作文。</p>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { chatWriting } from '@/api/writing'
import type { WritingChatResponse } from '@/api/writing'
import { showToast } from '@/utils/toast'

const props = defineProps<{
  essay: string
  aiHint: string
}>()

const emit = defineEmits<{
  'apply-rewrite': [fullText: string]
}>()

const instruction = ref('')
const loading = ref(false)
const lastResponse = ref<WritingChatResponse | null>(null)

const canSend = computed(
  () => props.essay.trim().length > 0 && instruction.value.trim().length > 0
)

async function send() {
  if (!canSend.value || loading.value) return
  loading.value = true
  lastResponse.value = null
  try {
    const res = await chatWriting({
      essay: props.essay.trim(),
      instruction: instruction.value.trim(),
      lang: 'en',
      mode: 'free',
      aiHint: props.aiHint.trim() || undefined,
    })
    lastResponse.value = res
  } catch (e) {
    showToast(e instanceof Error ? e.message : '请求失败', 'error')
  } finally {
    loading.value = false
  }
}

function apply() {
  const fullText = lastResponse.value?.rewrite?.fullText
  if (!fullText) return
  emit('apply-rewrite', fullText)
}
</script>

<style scoped>
.rewrite-panel {
  padding: 16px;
}
.instruction-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.instruction-input {
  flex: 1;
  padding: 10px 12px;
  font-size: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  outline: none;
}
.instruction-input:focus {
  border-color: #047857;
}
.instruction-input::placeholder {
  color: #9ca3af;
}
.btn-send {
  flex-shrink: 0;
  padding: 10px 18px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 10px;
  cursor: pointer;
}
.btn-send:hover:not(:disabled) {
  background: #065f46;
}
.btn-send:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.assistant-block,
.rewrite-block {
  margin-bottom: 16px;
}
.block-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}
.assistant-text {
  margin: 0;
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
}
.rewrite-text {
  padding: 12px;
  font-size: 14px;
  line-height: 1.6;
  color: #111827;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  margin-bottom: 10px;
}
.btn-apply {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 10px;
  cursor: pointer;
}
.btn-apply:hover {
  background: #065f46;
}
.rewrite-hint {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
}
</style>
