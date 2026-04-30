<template>
  <div class="conversation-groups">
    <section
      v-for="group in groups"
      :key="group.label"
      class="conversation-group"
    >
      <h3 class="group-label">{{ group.label }}</h3>
      <button
        v-for="conversation in group.conversations"
        :key="conversation.id"
        type="button"
        class="conversation-item"
        :class="{ 'conversation-item--active': conversation.id === activeConversationId }"
        @click="$emit('select', conversation.id)"
      >
        <span class="conversation-row">
          <span class="conversation-title">
            <span v-if="conversation.pinned" class="pin-marker" aria-label="已置顶">⌖</span>
            {{ conversation.title }}
          </span>
          <span class="conversation-actions" @click.stop>
            <button
              type="button"
              class="action-button"
              :title="conversation.pinned ? '取消置顶' : '置顶'"
              @click="$emit('pin', conversation.id, !conversation.pinned)"
            >
              ⌖
            </button>
            <button type="button" class="action-button" title="分享" @click="$emit('share', conversation.id)">⇧</button>
            <button type="button" class="action-button" title="重命名" @click="$emit('rename', conversation.id)">✎</button>
            <button type="button" class="action-button" title="移动到项目" @click="$emit('move', conversation.id)">□</button>
            <button type="button" class="action-button" title="归档" @click="$emit('archive', conversation.id)">▤</button>
            <button type="button" class="action-button action-button--danger" title="删除" @click="$emit('delete', conversation.id)">⌫</button>
          </span>
        </span>
        <span v-if="conversation.summary" class="conversation-summary">{{ conversation.summary }}</span>
        <span class="conversation-time">{{ formatUpdatedAt(conversation.updatedAt) }}</span>
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { AssistantConversation } from '@/pages/app/assistantMock.ts'

interface ConversationGroup {
  label: string
  conversations: AssistantConversation[]
}

defineProps<{
  groups: ConversationGroup[]
  activeConversationId: string
}>()

defineEmits<{
  select: [id: string]
  rename: [id: string]
  archive: [id: string]
  delete: [id: string]
  share: [id: string]
  pin: [id: string, pinned: boolean]
  move: [id: string]
}>()

function formatUpdatedAt(updatedAt: number) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(updatedAt)
}
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
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}

.conversation-item:hover {
  background: #f8fafc;
  border-color: #dbe3ea;
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

.conversation-actions {
  display: none;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
}

.conversation-item:hover .conversation-actions,
.conversation-item--active .conversation-actions {
  display: flex;
}

.action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.action-button:hover {
  background: #e2e8f0;
  color: #0f172a;
}

.action-button--danger {
  color: #dc2626;
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
