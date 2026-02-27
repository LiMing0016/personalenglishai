<template>
  <div class="ai-test-page">
    <h2>AI Command Test (C1)</h2>
    <input v-model="docId" class="input" placeholder="docId" />
    <input v-model="instruction" class="input" placeholder="instruction" />
    <button class="btn" :disabled="loading" @click="runRewrite">
      {{ loading ? 'Sending...' : 'Run rewrite' }}
    </button>
    <pre class="result">{{ applyResult }}</pre>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { aiCommand } from '@/api/ai'
import { showToast } from '@/utils/toast'

const docId = ref('')
const instruction = ref('Please rewrite this in a more academic tone.')
const applyResult = ref('')
const loading = ref(false)

async function runRewrite() {
  if (!docId.value.trim()) {
    showToast('docId is required', 'info')
    return
  }
  if (!instruction.value.trim()) {
    showToast('instruction is required', 'info')
    return
  }
  loading.value = true
  try {
    const result = await aiCommand({
      apiVersion: 1,
      intent: 'rewrite',
      mode: 'md',
      instruction: instruction.value.trim(),
      contextRefs: { docId: docId.value.trim() },
    })
    applyResult.value = result.apply
    console.log('aiCommand result.apply:', result.apply)
  } catch (e) {
    const err = e as Error & { status?: number }
    if (err.status === 401) {
      showToast('请先登录', 'error')
      window.location.href = '/login'
      return
    }
    showToast(err.message || 'AI request failed', 'error')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.ai-test-page {
  max-width: 840px;
  margin: 24px auto;
  padding: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.input {
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
}
.btn {
  padding: 10px 16px;
  border: 0;
  border-radius: 8px;
  background: #0f766e;
  color: #fff;
  cursor: pointer;
}
.btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.result {
  margin-top: 16px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
