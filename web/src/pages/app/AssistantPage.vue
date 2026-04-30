<template>
  <div class="assistant-page">
    <AssistantSidebar
      :search-value="searchText"
      :groups="conversationGroups"
      :active-conversation-id="activeConversationId"
      @new-conversation="createConversation"
      @update:search-value="searchText = $event"
      @select-conversation="selectConversation"
      @rename-conversation="handleRenameConversation"
      @archive-conversation="handleArchiveConversation"
      @delete-conversation="handleDeleteConversation"
      @share-conversation="handleShareConversation"
      @pin-conversation="setConversationPinned"
      @move-conversation="handleMoveConversation"
    />

    <div class="assistant-main">
      <header class="main-header">
        <span class="main-title">{{ pageTitle }}</span>
        <span v-if="isLoadingConversations" class="loading-label">同步中</span>
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
import { computed, onMounted } from 'vue'

import AssistantChatView from '@/components/assistant/AssistantChatView.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantSidebar from '@/components/assistant/AssistantSidebar.vue'
import { showToast } from '@/utils/toast'
import { createAssistantState } from './assistantState.ts'

const {
  conversations,
  projects,
  activeConversationId,
  activeConversation,
  isLoadingConversations,
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
  loadRemoteState,
  createConversation,
  selectConversation,
  renameConversation,
  setConversationPinned,
  archiveConversation,
  deleteConversation,
  moveConversation,
  shareConversation,
  createProject,
  sendMessage,
  retryLastMessage,
} = createAssistantState({ remote: true })

const pageTitle = '学习助手'
const emptyTitle = '今天想练什么？'
const emptySubtitle = '我可以陪你做英语评价、表达润色、题目设计和词句讲解。'
const composerDocked = true

function handleFileSelect(files: File[]) {
  addAttachments(files)
}

onMounted(() => {
  void loadRemoteState()
})

async function handleRenameConversation(id: string) {
  const conversation = conversations.value.find((item) => item.id === id)
  const nextTitle = window.prompt('重命名对话', conversation?.title ?? '')
  if (nextTitle === null) return
  try {
    await renameConversation(id, nextTitle)
    showToast('已重命名', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '重命名失败', 'error')
  }
}

async function handleArchiveConversation(id: string) {
  try {
    await archiveConversation(id)
    showToast('已归档', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '归档失败', 'error')
  }
}

async function handleDeleteConversation(id: string) {
  if (!window.confirm('删除后当前列表将不再显示这个对话。确定删除吗？')) return
  try {
    await deleteConversation(id)
    showToast('已删除', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '删除失败', 'error')
  }
}

async function handleShareConversation(id: string) {
  try {
    const share = await shareConversation(id)
    const url = `${window.location.origin}${share.sharePath}`
    await navigator.clipboard?.writeText(url)
    showToast('分享链接已复制', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '分享失败', 'error')
  }
}

async function handleMoveConversation(id: string) {
  const projectNames = projects.value.map((project) => project.name).join('、')
  const input = window.prompt(
    projectNames ? `输入项目名称，留空移出项目。现有项目：${projectNames}` : '输入新项目名称，留空移出项目。',
    '',
  )
  if (input === null) return
  const name = input.trim()
  try {
    if (!name) {
      await moveConversation(id, null)
      showToast('已移出项目', 'success')
      return
    }
    const existing = projects.value.find((project) => project.name === name)
    const project = existing ?? await createProject(name)
    await moveConversation(id, project.id)
    showToast('已移动到项目', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '移动失败', 'error')
  }
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
  gap: 10px;
  flex: 0 0 56px;
  min-height: 56px;
  padding: 0 24px 0 28px;
}

.loading-label {
  padding: 3px 8px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 600;
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
