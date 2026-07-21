<template>
  <aside
    class="assistant-sidebar"
    :class="{ 'assistant-sidebar--collapsed': collapsed }"
    aria-label="学习助手侧边栏"
  >
    <div v-if="collapsed" class="collapsed-sidebar">
      <AppNavigationMenu :collapsed="true" @toggle="requestOpenSidebar" />

      <button
        type="button"
        class="collapsed-sidebar-button"
        title="新聊天"
        aria-label="新聊天"
        @click="$emit('newConversation')"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 20h4l11-11a2.8 2.8 0 0 0-4-4L4 16v4Z" />
          <path d="m13.5 6.5 4 4" />
        </svg>
      </button>

      <div class="collapsed-sidebar-spacer" aria-hidden="true"></div>

      <RouterLink to="/app/me" class="collapsed-profile-link" title="个人中心" aria-label="个人中心">
        我
      </RouterLink>
    </div>

    <div v-else class="sidebar-panel">
      <AppNavigationMenu :collapsed="false" @toggle="$emit('closeSidebar')" />

      <section class="assistant-context-section" aria-label="助手空间">
        <div class="workspace-section-label">助手空间</div>
        <div class="sidebar-primary-actions" aria-label="学习助手操作">
          <button type="button" class="sidebar-new-chat-button" @click="$emit('newConversation')">
            <span class="workspace-action-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <path d="M4 20h4l11-11a2.8 2.8 0 0 0-4-4L4 16v4Z" />
                <path d="m13.5 6.5 4 4" />
              </svg>
            </span>
            <span>新聊天</span>
          </button>
        </div>
      </section>

      <section class="chat-library-section">
        <div class="workspace-section-label">学习助手对话</div>
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
        <section class="sidebar-folder">
          <div class="sidebar-folder-header-row">
            <button
              type="button"
              class="sidebar-folder-header"
              :aria-expanded="projectFolderOpen"
              @click="projectFolderOpen = !projectFolderOpen"
            >
              <span>文件夹</span>
              <span class="folder-chevron" :class="{ 'folder-chevron--open': projectFolderOpen }">›</span>
            </button>
            <button
              type="button"
              class="folder-create-button"
              title="创建文件夹"
              aria-label="创建文件夹"
              @click="$emit('createFolder')"
            >
              +
            </button>
          </div>

          <div v-if="projectFolderOpen" class="sidebar-folder-content">
            <p v-if="folderGroups.length === 0" class="folder-empty">暂无文件夹</p>
            <section
              v-for="folderGroup in folderGroups"
              :key="folderGroup.id"
              class="conversation-folder"
            >
              <button
                type="button"
                class="conversation-folder-header"
                :aria-expanded="isConversationFolderOpen(folderGroup.id)"
                @click="toggleConversationFolder(folderGroup.id)"
              >
                <span class="conversation-folder-name">{{ folderGroup.name }}</span>
                <span class="conversation-folder-meta">
                  <span>{{ folderGroup.conversationCount }}</span>
                  <span
                    class="folder-chevron folder-chevron--small"
                    :class="{ 'folder-chevron--open': isConversationFolderOpen(folderGroup.id) }"
                  >
                    ›
                  </span>
                </span>
              </button>

              <div
                v-if="isConversationFolderOpen(folderGroup.id)"
                class="conversation-folder-content"
              >
                <p v-if="folderGroup.conversationCount === 0" class="folder-empty">暂无对话</p>
                <AssistantConversationList
                  v-else
                  :groups="folderGroup.groups"
                  :active-conversation-id="activeConversationId"
                  :folders="folders"
                  @select="$emit('selectConversation', $event)"
                  @rename="$emit('renameConversation', $event)"
                  @archive="$emit('archiveConversation', $event)"
                  @delete="$emit('deleteConversation', $event)"
                  @share="$emit('shareConversation', $event)"
                  @pin="(id, pinned) => $emit('pinConversation', id, pinned)"
                  @move-to-folder="(id, folderId) => $emit('moveConversationToFolder', id, folderId)"
                  @create-folder-and-move="$emit('createFolderAndMove', $event)"
                />
              </div>
            </section>
          </div>
        </section>

        <section class="sidebar-folder">
          <button
            type="button"
            class="sidebar-folder-header"
            :aria-expanded="recentFolderOpen"
            @click="recentFolderOpen = !recentFolderOpen"
          >
            <span>最近</span>
            <span class="folder-chevron" :class="{ 'folder-chevron--open': recentFolderOpen }">›</span>
          </button>

          <div v-if="recentFolderOpen" class="sidebar-folder-content">
            <AssistantConversationList
              :groups="groups"
              :active-conversation-id="activeConversationId"
              :folders="folders"
              @select="$emit('selectConversation', $event)"
              @rename="$emit('renameConversation', $event)"
              @archive="$emit('archiveConversation', $event)"
              @delete="$emit('deleteConversation', $event)"
              @share="$emit('shareConversation', $event)"
              @pin="(id, pinned) => $emit('pinConversation', id, pinned)"
              @move-to-folder="(id, folderId) => $emit('moveConversationToFolder', id, folderId)"
              @create-folder-and-move="$emit('createFolderAndMove', $event)"
            />
          </div>
        </section>

        <section class="sidebar-folder">
          <button
            type="button"
            class="sidebar-folder-header"
            :aria-expanded="archiveFolderOpen"
            @click="archiveFolderOpen = !archiveFolderOpen"
          >
            <span>归档</span>
            <span class="conversation-folder-meta">
              <span>{{ archivedConversationCount }}</span>
              <span class="folder-chevron" :class="{ 'folder-chevron--open': archiveFolderOpen }">›</span>
            </span>
          </button>

          <div v-if="archiveFolderOpen" class="sidebar-folder-content">
            <form class="archive-setting-card" @submit.prevent="submitArchiveDir">
              <label class="archive-setting-label" for="assistant-archive-dir">本地归档目录</label>
              <input
                id="assistant-archive-dir"
                v-model="archiveDirDraft"
                class="archive-setting-input"
                type="text"
                placeholder="默认保存到 Documents"
              />
              <p class="archive-setting-hint">
                归档后会生成 Markdown、JSON 和元数据文件。
              </p>
              <div class="archive-setting-actions">
                <button
                  type="button"
                  class="archive-setting-button"
                  :disabled="archiveDirSaving || !defaultArchiveDir"
                  @click="resetArchiveDir"
                >
                  默认
                </button>
                <button
                  type="submit"
                  class="archive-setting-button archive-setting-button--primary"
                  :disabled="archiveDirSaving"
                >
                  {{ archiveDirSaving ? '保存中' : '保存' }}
                </button>
              </div>
            </form>
            <p v-if="archivedConversationCount === 0" class="folder-empty">暂无归档对话</p>
            <AssistantConversationList
              v-else
              archived
              :groups="archivedGroups"
              :active-conversation-id="activeConversationId"
              :folders="folders"
              @select="$emit('selectConversation', $event)"
              @restore="$emit('restoreConversation', $event)"
              @delete="$emit('deleteConversation', $event)"
              @share="$emit('shareConversation', $event)"
            />
          </div>
        </section>
        </div>
      </section>

      <RouterLink to="/app/me" class="sidebar-profile-link">
        <span class="sidebar-profile-avatar">我</span>
        <span class="sidebar-profile-copy">
          <span class="sidebar-profile-name">个人中心</span>
          <span class="sidebar-profile-subtitle">账号设置与订阅</span>
        </span>
      </RouterLink>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import AppNavigationMenu from '@/components/AppNavigationMenu.vue'
import AssistantConversationList from './AssistantConversationList.vue'
import type { AssistantConversation } from '@/pages/app/assistantMock.ts'

interface ConversationGroup {
  label: string
  conversations: AssistantConversation[]
}

interface FolderOption {
  id: number
  name: string
}

interface FolderConversationGroup {
  id: number
  name: string
  conversationCount: number
  groups: ConversationGroup[]
}

const projectFolderOpen = ref(false)
const recentFolderOpen = ref(true)
const archiveFolderOpen = ref(false)
const openConversationFolderIds = ref<Set<number>>(new Set())

const props = defineProps<{
  collapsed: boolean
  requestOpenSidebar: () => void
  searchValue: string
  groups: ConversationGroup[]
  archivedGroups: ConversationGroup[]
  folderGroups: FolderConversationGroup[]
  activeConversationId: string
  folders: FolderOption[]
  archiveDir: string
  defaultArchiveDir: string
  archiveDirSaving: boolean
}>()

const emit = defineEmits<{
  newConversation: []
  closeSidebar: []
  'update:searchValue': [value: string]
  selectConversation: [id: string]
  renameConversation: [id: string]
  archiveConversation: [id: string]
  restoreConversation: [id: string]
  deleteConversation: [id: string]
  shareConversation: [id: string]
  pinConversation: [id: string, pinned: boolean]
  moveConversationToFolder: [id: string, folderId: number | null]
  createFolder: []
  createFolderAndMove: [id: string]
  saveArchiveDir: [value: string]
}>()

const archiveDirDraft = ref(props.archiveDir)

const archivedConversationCount = computed(() =>
  props.archivedGroups.reduce((total, group) => total + group.conversations.length, 0),
)

watch(
  () => props.archiveDir,
  (value) => {
    archiveDirDraft.value = value
  },
)

function isConversationFolderOpen(id: number) {
  return openConversationFolderIds.value.has(id)
}

function toggleConversationFolder(id: number) {
  const next = new Set(openConversationFolderIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  openConversationFolderIds.value = next
}

function submitArchiveDir() {
  emit('saveArchiveDir', archiveDirDraft.value)
}

function resetArchiveDir() {
  archiveDirDraft.value = props.defaultArchiveDir
  emit('saveArchiveDir', props.defaultArchiveDir)
}

</script>

<style scoped>
.assistant-sidebar {
  display: flex;
  flex: 0 0 218px;
  width: 218px;
  min-width: 218px;
  height: 100%;
  padding: 0;
  background: #eaf4fc;
  border-right: 1px solid var(--app-sidebar-border, #e2e8f0);
  color: #0f172a;
  box-sizing: border-box;
}

.assistant-sidebar--collapsed {
  flex-basis: 72px;
  width: 72px;
  min-width: 72px;
  background: #eaf4fc;
}

.assistant-sidebar.assistant-sidebar--collapsed,
.assistant-sidebar.assistant-sidebar--collapsed .collapsed-sidebar {
  background: #eaf4fc;
  color: #334155;
}

.collapsed-sidebar,
.sidebar-panel {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  box-sizing: border-box;
  background: #eaf4fc;
}

.collapsed-sidebar {
  align-items: center;
  gap: 14px;
  padding: 16px 14px;
}

.collapsed-sidebar-button,
.folder-create-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  color: #334155;
  cursor: pointer;
}

.collapsed-sidebar-button {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: transparent;
  text-decoration: none;
}

.collapsed-sidebar-button:hover,
.collapsed-sidebar-button:focus-visible {
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.collapsed-sidebar-button svg,
.workspace-action-icon svg {
  width: 23px;
  height: 23px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.collapsed-sidebar-spacer {
  flex: 1 1 auto;
}

.collapsed-profile-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #047857;
  color: #ffffff;
  font-size: 12px;
  font-weight: 900;
  text-decoration: none;
}

.sidebar-panel {
  padding: 16px 12px 18px;
}

.workspace-section-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.assistant-context-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(100, 116, 139, 0.18);
}

.sidebar-primary-actions {
  margin-top: 8px;
}

.workspace-action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: #047857;
}

.sidebar-new-chat-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 38px;
  padding: 9px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  text-decoration: none;
  box-sizing: border-box;
}

.sidebar-new-chat-button {
  justify-content: center;
  min-height: 44px;
  border-color: #047857;
  border-radius: 14px;
  background: #047857;
  color: #ffffff;
  box-shadow: 0 12px 24px rgba(4, 120, 87, 0.16);
}

.sidebar-new-chat-button .workspace-action-icon {
  color: #ffffff;
}

.sidebar-new-chat-button:hover,
.sidebar-new-chat-button:focus-visible {
  border-color: #065f46;
  background: #065f46;
  color: #ffffff;
}

.workspace-action-icon svg {
  width: 18px;
  height: 18px;
}

.workspace-section-label {
  padding: 0 2px;
}

.chat-library-section {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.search-box {
  margin-top: 10px;
  padding: 0 2px;
}

.search-input {
  width: 100%;
  padding: 12px 13px;
  border: 1px solid #dbe3ea;
  border-radius: 13px;
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
  box-sizing: border-box;
  outline: none;
}

.search-input::placeholder {
  color: #737373;
}

.search-input:focus {
  border-color: #34d399;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.14);
}

.conversation-scroll {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  min-height: 0;
  margin-top: 14px;
  overflow-y: auto;
  padding: 2px 2px 0;
}

.conversation-scroll::-webkit-scrollbar {
  width: 8px;
}

.conversation-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cbd5e1;
}

.sidebar-folder {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sidebar-folder-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.sidebar-folder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: 0;
  background: transparent;
  color: #0f172a;
  cursor: pointer;
  font-size: 14px;
  font-weight: 800;
  text-align: left;
}

.sidebar-folder-header:hover,
.sidebar-folder-header:focus-visible {
  color: #047857;
}

.folder-create-button {
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: transparent;
  color: #64748b;
  font-size: 22px;
  line-height: 1;
}

.folder-create-button:hover,
.folder-create-button:focus-visible {
  background: #ffffff;
  color: #047857;
}

.folder-chevron {
  color: #a3a3a3;
  font-size: 21px;
  line-height: 1;
  transition: transform 0.15s ease;
}

.folder-chevron--small {
  font-size: 18px;
}

.folder-chevron--open {
  transform: rotate(90deg);
}

.sidebar-folder-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.conversation-folder {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conversation-folder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  min-height: 36px;
  padding: 8px 10px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: #0f172a;
  cursor: pointer;
  text-align: left;
}

.conversation-folder-header:hover,
.conversation-folder-header:focus-visible {
  background: #ffffff;
}

.conversation-folder-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 700;
}

.conversation-folder-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  color: #a3a3a3;
  font-size: 12px;
  font-weight: 700;
}

.conversation-folder-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 8px;
}

.folder-empty {
  margin: 0;
  padding: 8px 10px;
  color: #737373;
  font-size: 12px;
}

.archive-setting-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid #dbe3ea;
  border-radius: 12px;
  background: #ffffff;
}

.archive-setting-label {
  color: #0f172a;
  font-size: 12px;
  font-weight: 800;
}

.archive-setting-input {
  width: 100%;
  padding: 9px 10px;
  border: 1px solid #dbe3ea;
  border-radius: 10px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 12px;
  box-sizing: border-box;
  outline: none;
}

.archive-setting-input:focus {
  border-color: #34d399;
}

.archive-setting-hint {
  margin: 0;
  color: #a3a3a3;
  font-size: 11px;
  line-height: 1.45;
}

.archive-setting-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.archive-setting-button {
  border: 1px solid #dbe3ea;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.archive-setting-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.archive-setting-button--primary {
  border-color: #059669;
  background: #047857;
  color: #ffffff;
}

.sidebar-profile-link {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 12px 10px;
  border-top: 1px solid #e2e8f0;
  border-radius: 12px;
  color: #0f172a;
  text-decoration: none;
}

.sidebar-profile-link:hover,
.sidebar-profile-link:focus-visible {
  background: #ffffff;
}

.sidebar-profile-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #047857;
  color: #ffffff;
  font-size: 13px;
  font-weight: 900;
}

.sidebar-profile-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.sidebar-profile-name {
  font-size: 12px;
  font-weight: 800;
  color: #0f172a;
}

.sidebar-profile-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: #a3a3a3;
}

:deep(.conversation-groups) {
  gap: 14px;
}

:deep(.group-label) {
  color: #a3a3a3;
}

:deep(.conversation-item) {
  border-color: transparent;
  color: #0f172a;
}

:deep(.conversation-item:hover) {
  background: #ffffff;
  border-color: #e2e8f0;
}

:deep(.conversation-item--active) {
  background: #ffffff;
  border-color: #a7f3d0;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

:deep(.conversation-title) {
  color: #0f172a;
}

:deep(.conversation-summary) {
  color: #a3a3a3;
}

:deep(.conversation-time),
:deep(.conversation-menu-button) {
  color: #a3a3a3;
}

:deep(.conversation-menu-button:hover),
:deep(.conversation-menu-button[aria-expanded='true']) {
  background: #e2e8f0;
  color: #0f172a;
}

@media (max-width: 960px) {
  .assistant-sidebar {
    flex-basis: 300px;
    width: 300px;
    min-width: 300px;
  }

  .assistant-sidebar--collapsed {
    flex-basis: 72px;
    width: 72px;
    min-width: 72px;
  }
}
</style>
