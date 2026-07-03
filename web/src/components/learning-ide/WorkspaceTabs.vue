<template>
  <header class="learning-workspace-tabs" aria-label="工作区标签页">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      class="learning-workspace-tab"
      :class="{ active: tab.id === activeTabId, dirty: tab.dirty }"
      @click="emit('activate', tab.id)">
      <strong>{{ tab.title }}</strong>
      <small v-if="tab.dirty">●</small>
      <small
        v-else
        role="button"
        tabindex="-1"
        aria-label="关闭标签页"
        @click.stop="emit('close', tab.id)">
        ×
      </small>
    </button>
    <button type="button" class="learning-workspace-tab learning-workspace-tab--new" @click="emit('create')">
      +
    </button>
  </header>
</template>

<script setup lang="ts">
import type { LearningWorkspaceTab } from '../../types/learningIde'

defineProps<{
  tabs: LearningWorkspaceTab[]
  activeTabId: string | null
}>()

const emit = defineEmits<{
  activate: [tabId: string]
  close: [tabId: string]
  create: []
}>()
</script>

<style scoped>
.learning-workspace-tabs {
  display: flex;
  min-height: 38px;
  min-width: 0;
  overflow-x: auto;
  border-bottom: 1px solid #d9e2ec;
  background: #f8fafc;
}

.learning-workspace-tab {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 22px;
  min-width: 178px;
  max-width: 260px;
  min-height: 36px;
  padding: 0 8px 0 12px;
  border: 0;
  border-right: 1px solid #d9e2ec;
  background: #f1f5f9;
  color: #102033;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.learning-workspace-tab.active {
  background: #ffffff;
  box-shadow: inset 0 -2px 0 #0f8f89;
}

.learning-workspace-tab strong {
  align-self: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.learning-workspace-tab small {
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.learning-workspace-tab strong {
  grid-column: 1 / span 1;
  font-size: 12px;
  line-height: 1.2;
}

.learning-workspace-tab small {
  grid-column: 2;
  align-self: center;
  justify-self: center;
}

.learning-workspace-tab--new {
  min-width: 42px;
  max-width: 42px;
  padding: 0;
  place-items: center;
  text-align: center;
}
</style>
