<template>
  <aside class="assistant-sidebar">
    <button type="button" class="new-button" @click="$emit('newConversation')">
      + 新建对话
    </button>

    <div class="search-box">
      <input
        :value="searchValue"
        type="text"
        class="search-input"
        placeholder="搜索历史对话"
        @input="$emit('update:searchValue', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <div class="conversation-scroll">
      <AssistantConversationList
        :groups="groups"
        :active-conversation-id="activeConversationId"
        @select="$emit('selectConversation', $event)"
        @rename="$emit('renameConversation', $event)"
        @archive="$emit('archiveConversation', $event)"
        @delete="$emit('deleteConversation', $event)"
        @share="$emit('shareConversation', $event)"
        @pin="(id, pinned) => $emit('pinConversation', id, pinned)"
        @move="$emit('moveConversation', $event)"
      />
    </div>

    <div class="sidebar-footer">
      <span class="footer-label">PEAI 学习助手</span>
      <span class="footer-subtitle">纯文本聊天体验（第一版）</span>
    </div>
  </aside>
</template>

<script setup lang="ts">
import AssistantConversationList from './AssistantConversationList.vue'
import type { AssistantConversation } from '@/pages/app/assistantMock.ts'

interface ConversationGroup {
  label: string
  conversations: AssistantConversation[]
}

defineProps<{
  searchValue: string
  groups: ConversationGroup[]
  activeConversationId: string
}>()

defineEmits<{
  newConversation: []
  'update:searchValue': [value: string]
  selectConversation: [id: string]
  renameConversation: [id: string]
  archiveConversation: [id: string]
  deleteConversation: [id: string]
  shareConversation: [id: string]
  pinConversation: [id: string, pinned: boolean]
  moveConversation: [id: string]
}>()
</script>

<style scoped>
.assistant-sidebar {
  display: flex;
  flex-direction: column;
  width: 280px;
  min-width: 280px;
  height: 100%;
  padding: 18px;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  box-sizing: border-box;
}

.new-button {
  width: 100%;
  padding: 14px 16px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #047857 0%, #059669 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.search-box {
  margin-top: 14px;
}

.search-input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #dbe3ea;
  border-radius: 12px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 13px;
  box-sizing: border-box;
  outline: none;
}

.search-input::placeholder {
  color: #64748b;
}

.search-input:focus {
  border-color: #10b981;
}

.conversation-scroll {
  flex: 1;
  min-height: 0;
  margin-top: 18px;
  overflow-y: auto;
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e2e8f0;
}

.footer-label {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.footer-subtitle {
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 960px) {
  .assistant-sidebar {
    width: 100%;
    min-width: 0;
    height: auto;
    border-right: none;
    border-bottom: 1px solid #e2e8f0;
  }
}
</style>
