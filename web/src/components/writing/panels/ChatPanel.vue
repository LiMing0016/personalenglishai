<template>
  <div class="chat-panel">
    <p class="chat-desc">AI 对话（本地 mock，后续接 GPT）</p>
    <div class="chat-messages">
      <div v-for="(m, i) in messages" :key="i" class="message" :class="m.role">
        <span class="message-role">{{ m.role === 'user' ? '你' : 'AI' }}</span>
        <p class="message-text">{{ m.text }}</p>
      </div>
    </div>
    <div class="chat-input-row">
      <input
        v-model="inputText"
        type="text"
        class="chat-input"
        placeholder="输入消息…"
        @keydown.enter.exact.prevent="send"
      />
      <button type="button" class="btn-send" @click="send">发送</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { showToast } from '@/utils/toast'

const messages = ref<{ role: 'user' | 'assistant'; text: string }[]>([
  { role: 'assistant', text: '你好，我可以帮你润色作文、解释语法或回答问题。直接输入即可。（mock）' },
])
const inputText = ref('')

function send() {
  const t = inputText.value.trim()
  if (!t) return
  messages.value.push({ role: 'user', text: t })
  inputText.value = ''
  messages.value.push({ role: 'assistant', text: '收到你的消息，AI 回复功能即将上线。（mock）' })
  showToast('已发送（mock）', 'info')
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
  box-sizing: border-box;
}
.chat-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: #9ca3af;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  min-height: 120px;
  margin-bottom: 12px;
}
.message {
  margin-bottom: 12px;
}
.message.user .message-role {
  color: #047857;
}
.message.assistant .message-role {
  color: #6b7280;
}
.message-role {
  font-size: 12px;
  font-weight: 600;
  display: block;
  margin-bottom: 4px;
}
.message-text {
  margin: 0;
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
}
.chat-input-row {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
}
.chat-input {
  flex: 1;
  padding: 10px 12px;
  font-size: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  outline: none;
}
.chat-input:focus {
  border-color: #047857;
}
.btn-send {
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 10px;
  cursor: pointer;
}
.btn-send:hover {
  background: #065f46;
}
</style>
