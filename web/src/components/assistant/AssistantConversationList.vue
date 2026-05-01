<template>
  <div class="conversation-groups">
    <section
      v-for="group in groups"
      :key="group.label"
      class="conversation-group"
    >
      <h3 class="group-label">{{ group.label }}</h3>
      <article
        v-for="conversation in group.conversations"
        :key="conversation.id"
        class="conversation-item"
        :class="{ 'conversation-item--active': conversation.id === activeConversationId }"
        role="button"
        tabindex="0"
        @click="selectConversation(conversation.id)"
        @keydown.enter.prevent="selectConversation(conversation.id)"
        @keydown.space.prevent="selectConversation(conversation.id)"
      >
        <span class="conversation-row">
          <span class="conversation-title">
            <span v-if="conversation.pinned" class="pin-marker" aria-label="已置顶">⌖</span>
            {{ conversation.title }}
          </span>
          <span class="conversation-menu-wrap" @click.stop>
            <button
              type="button"
              class="conversation-menu-button"
              title="更多操作"
              :aria-expanded="openMenuId === conversation.id"
              aria-haspopup="menu"
              @click="toggleMenu(conversation.id)"
            >
              ...
            </button>
            <span
              v-if="openMenuId === conversation.id"
              class="conversation-action-menu"
              role="menu"
            >
              <button type="button" class="conversation-menu-item" role="menuitem" @click="runMenuAction('share', conversation.id)">
                <span class="conversation-menu-icon">⇧</span>
                <span>分享</span>
              </button>
              <button type="button" class="conversation-menu-item" role="menuitem" @click="runMenuAction('rename', conversation.id)">
                <span class="conversation-menu-icon">✎</span>
                <span>重命名</span>
              </button>
              <button type="button" class="conversation-menu-item" role="menuitem" @click="runMenuAction('move', conversation.id)">
                <span class="conversation-menu-icon">□</span>
                <span>移动到项目</span>
              </button>
              <span class="conversation-menu-separator" aria-hidden="true" />
              <button type="button" class="conversation-menu-item" role="menuitem" @click="runPinAction(conversation.id, !conversation.pinned)">
                <span class="conversation-menu-icon">⌖</span>
                <span>{{ conversation.pinned ? '取消置顶' : '置顶聊天' }}</span>
              </button>
              <button type="button" class="conversation-menu-item" role="menuitem" @click="runMenuAction('archive', conversation.id)">
                <span class="conversation-menu-icon">▤</span>
                <span>归档</span>
              </button>
              <button type="button" class="conversation-menu-item conversation-menu-item--danger" role="menuitem" @click="runMenuAction('delete', conversation.id)">
                <span class="conversation-menu-icon">⌫</span>
                <span>删除</span>
              </button>
            </span>
          </span>
        </span>
        <span v-if="conversation.summary" class="conversation-summary">{{ conversation.summary }}</span>
        <span class="conversation-time">{{ formatUpdatedAt(conversation.updatedAt) }}</span>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import type { AssistantConversation } from '@/pages/app/assistantMock.ts'

interface ConversationGroup {
  label: string
  conversations: AssistantConversation[]
}

defineProps<{
  groups: ConversationGroup[]
  activeConversationId: string
}>()

const emit = defineEmits<{
  select: [id: string]
  rename: [id: string]
  archive: [id: string]
  delete: [id: string]
  share: [id: string]
  pin: [id: string, pinned: boolean]
  move: [id: string]
}>()

type MenuAction = 'share' | 'rename' | 'move' | 'archive' | 'delete'

const openMenuId = ref<string | null>(null)

function selectConversation(id: string) {
  openMenuId.value = null
  emit('select', id)
}

function toggleMenu(id: string) {
  openMenuId.value = openMenuId.value === id ? null : id
}

function closeMenu() {
  openMenuId.value = null
}

function runMenuAction(action: MenuAction, id: string) {
  switch (action) {
    case 'share':
      emit('share', id)
      break
    case 'rename':
      emit('rename', id)
      break
    case 'move':
      emit('move', id)
      break
    case 'archive':
      emit('archive', id)
      break
    case 'delete':
      emit('delete', id)
      break
  }
  closeMenu()
}

function runPinAction(id: string, pinned: boolean) {
  emit('pin', id, pinned)
  closeMenu()
}

function formatUpdatedAt(updatedAt: number) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(updatedAt)
}

onMounted(() => {
  document.addEventListener('click', closeMenu)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeMenu)
})
</script>

<style scoped>
.conversation-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.conversation-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-label {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.conversation-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  position: relative;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
  box-sizing: border-box;
}

.conversation-item:hover {
  background: #f8fafc;
  border-color: #dbe3ea;
}

.conversation-item:focus-visible {
  outline: 2px solid #10b981;
  outline-offset: 2px;
}

.conversation-item--active {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.conversation-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.conversation-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.conversation-menu-wrap {
  display: none;
  position: relative;
  flex: 0 0 auto;
}

.conversation-item:hover .conversation-menu-wrap,
.conversation-item--active .conversation-menu-wrap,
.conversation-menu-wrap:focus-within {
  display: flex;
}

.conversation-menu-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
}

.conversation-menu-button:hover,
.conversation-menu-button[aria-expanded='true'] {
  background: #e2e8f0;
  color: #0f172a;
}

.conversation-action-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 30;
  display: flex;
  width: 190px;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  border: 1px solid #dbe3ea;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.18);
}

.conversation-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #0f172a;
  padding: 10px 11px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
  text-align: left;
  cursor: pointer;
}

.conversation-menu-item:hover {
  background: #f1f5f9;
}

.conversation-menu-icon {
  width: 18px;
  color: currentColor;
  font-size: 15px;
  font-weight: 700;
  text-align: center;
}

.conversation-menu-separator {
  height: 1px;
  margin: 6px 4px;
  background: #e2e8f0;
}

.conversation-menu-item--danger {
  color: #dc2626;
}

.conversation-menu-item--danger:hover {
  background: #fef2f2;
}

.pin-marker {
  margin-right: 4px;
  color: #059669;
}

.conversation-summary {
  display: -webkit-box;
  overflow: hidden;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.conversation-time {
  font-size: 11px;
  color: #64748b;
}
</style>
