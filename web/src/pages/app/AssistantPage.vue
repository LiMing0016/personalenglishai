<template>
  <div class="assistant-page">
    <AssistantSidebar
      :search-value="searchText"
      :groups="conversationGroups"
      :active-conversation-id="activeConversationId"
      @new-conversation="createConversation"
      @update:search-value="searchText = $event"
      @select-conversation="selectConversation"
    />

    <div class="assistant-main">
      <header class="main-header">
        <span class="main-title">{{ pageTitle }}</span>
      </header>

      <AssistantChatView
        :messages="activeConversation.messages"
        :error-message="errorMessage"
        :can-retry="canRetry"
        :empty-title="emptyTitle"
        :empty-subtitle="emptySubtitle"
        @choose-starter="applyStarter"
        @retry="retryLastMessage"
      />

      <div class="composer-dock" :class="{ composerDocked }">
        <AssistantComposer
          :model-value="composerText"
          :loading="isSending"
          :attachments="composerAttachments"
          :assistant-mode="assistantMode"
          @update:model-value="composerText = $event"
          @add-files="handleFileSelect"
          @remove-attachment="removeAttachment"
          @set-assistant-mode="setAssistantMode"
          @send="sendMessage"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import AssistantChatView from '@/components/assistant/AssistantChatView.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantSidebar from '@/components/assistant/AssistantSidebar.vue'
import { createAssistantState } from './assistantState.ts'

const {
  conversations,
  activeConversationId,
  activeConversation,
  composerText,
  composerAttachments,
  assistantMode,
  searchText,
  isSending,
  errorMessage,
  canRetry,
  applyStarter,
  addAttachments,
  removeAttachment,
  setAssistantMode,
  createConversation,
  selectConversation,
  sendMessage,
  retryLastMessage,
} = createAssistantState()

const pageTitle = '学习助手'
const emptyTitle = '今天想练什么？'
const emptySubtitle = '我可以陪你做英语评价、表达润色、题目设计和词句讲解。'
const composerDocked = true

function handleFileSelect(files: File[]) {
  addAttachments(files)
}

const conversationGroups = computed(() => {
  const keyword = searchText.value.trim()
  const filtered = keyword
    ? conversations.value.filter((conversation) =>
        `${conversation.title} ${conversation.summary}`.includes(keyword),
      )
    : conversations.value

  const now = Date.now()
  const dayMs = 24 * 60 * 60 * 1000

  const groups = [
    {
      label: '今天',
      conversations: filtered.filter((conversation) => now - conversation.updatedAt < dayMs),
    },
    {
      label: '最近 7 天',
      conversations: filtered.filter(
        (conversation) => now - conversation.updatedAt >= dayMs && now - conversation.updatedAt < dayMs * 7,
      ),
    },
    {
      label: '更早',
      conversations: filtered.filter((conversation) => now - conversation.updatedAt >= dayMs * 7),
    },
  ]

  return groups.filter((group) => group.conversations.length > 0)
})
</script>

<style scoped>
.assistant-page {
  --assistant-sidebar-width: 280px;
  display: flex;
  flex: 1;
  height: calc(100vh - 48px);
  min-height: calc(100vh - 48px);
  overflow: hidden;
  background: #f8fafc;
}

.assistant-main {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  position: relative;
  box-sizing: border-box;
  background: #f8fafc;
}

.main-header {
  display: flex;
  align-items: center;
  flex: 0 0 56px;
  min-height: 56px;
  padding: 0 24px 0 28px;
}

.main-title {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.composer-dock {
  position: fixed;
  left: calc(var(--assistant-sidebar-width) + 1px);
  right: 0;
  bottom: 0;
  z-index: 40;
  padding: 18px 24px max(6px, env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(248, 250, 252, 0) 0%, rgba(248, 250, 252, 0.88) 34%, #f8fafc 100%);
}

@media (max-width: 960px) {
  .assistant-page {
    flex-direction: column;
    height: calc(100vh - 48px);
    min-height: calc(100vh - 48px);
  }

  .main-header {
    padding: 0 18px;
  }

  .composer-dock {
    left: 0;
    padding: 14px 12px max(4px, env(safe-area-inset-bottom));
  }
}
</style>
