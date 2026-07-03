<template>
  <aside class="learning-resource-panel" aria-label="学习资源">
    <nav class="learning-resource-panel__scope-tabs" aria-label="资源视图">
      <button
        type="button"
        :class="{ active: activeExplorerView === 'project' }"
        @click="emit('selectExplorerView', 'project')">
        项目
      </button>
      <button
        type="button"
        :class="{ active: activeExplorerView === 'file' }"
        @click="emit('selectExplorerView', 'file')">
        当前文件
      </button>
    </nav>

    <section v-if="activeExplorerView === 'file'" class="learning-resource-panel__file" aria-label="当前文件资源">
      <div>
        <strong>{{ currentFileTitle || '当前文件' }}</strong>
        <span>{{ currentFileSubtitle || '按当前资源类型显示大纲、标注和引用' }}</span>
      </div>

      <nav class="learning-resource-panel__switcher" aria-label="当前文件快捷筛选">
        <button
          v-for="panel in sidePanels"
          :key="panel.id"
          type="button"
          :class="{ active: panel.id === activeSidePanel }"
          @click="emit('selectPanel', panel.id)">
          <span>{{ panel.label }}</span>
          <small>{{ panel.count }}</small>
        </button>
      </nav>
    </section>

    <section class="learning-resource-panel__tree" aria-label="项目资源树">
      <article
        v-for="folder in renderFolders(activeFolders)"
        :key="folder.id"
        class="learning-resource-folder"
        :class="{ collapsed: collapsedFolderIds.includes(folder.id) }">
        <button
          type="button"
          class="learning-resource-folder__header"
          :aria-expanded="!collapsedFolderIds.includes(folder.id)"
          @click="emit('toggleFolder', folder.id)">
          <span aria-hidden="true">›</span>
          <strong>{{ folder.label }}</strong>
          <small>{{ folder.badge }}</small>
        </button>

        <div v-if="!collapsedFolderIds.includes(folder.id)" class="learning-resource-folder__body">
          <article
            v-for="childFolder in renderFolders(folder.children ?? [])"
            :key="childFolder.id"
            class="learning-resource-folder learning-resource-folder--child"
            :class="{ collapsed: collapsedFolderIds.includes(childFolder.id) }">
            <button
              type="button"
              class="learning-resource-folder__header"
              :aria-expanded="!collapsedFolderIds.includes(childFolder.id)"
              @click="emit('toggleFolder', childFolder.id)">
              <span aria-hidden="true">›</span>
              <strong>{{ childFolder.label }}</strong>
              <small>{{ childFolder.badge }}</small>
            </button>

            <div v-if="!collapsedFolderIds.includes(childFolder.id)" class="learning-resource-folder__body">
              <button
                v-for="resource in childFolder.resources"
                :key="resource.id"
                type="button"
                class="learning-resource-item"
                @click="emit('openResource', resource)">
                <span aria-hidden="true">{{ resolveResourceIcon(resource.kind) }}</span>
                <span>
                  <strong>{{ resource.title }}</strong>
                  <small v-if="resource.subtitle">{{ resource.subtitle }}</small>
                </span>
                <mark v-if="resource.count">{{ resource.count }}</mark>
              </button>
              <button
                v-if="childFolder.resources.length === 0 && (childFolder.children?.length ?? 0) === 0"
                type="button"
                class="learning-resource-empty"
                @click="emit('emptyAction', childFolder)">
                {{ childFolder.emptyText }}
              </button>
            </div>
          </article>

          <button
            v-for="resource in folder.resources"
            :key="resource.id"
            type="button"
            class="learning-resource-item"
            @click="emit('openResource', resource)">
            <span aria-hidden="true">{{ resolveResourceIcon(resource.kind) }}</span>
            <span>
              <strong>{{ resource.title }}</strong>
              <small v-if="resource.subtitle">{{ resource.subtitle }}</small>
            </span>
            <mark v-if="resource.count">{{ resource.count }}</mark>
          </button>
          <button
            v-if="folder.resources.length === 0 && (folder.children?.length ?? 0) === 0"
            type="button"
            class="learning-resource-empty"
            @click="emit('emptyAction', folder)">
            {{ folder.emptyText }}
          </button>
        </div>
      </article>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type {
  LearningResourceExplorerView,
  LearningResourceTreeFolder,
  LearningResourceTreeItem,
  LearningSidePanelOption,
} from '../../types/learningIde'

const props = withDefaults(defineProps<{
  sidePanels: LearningSidePanelOption[]
  activeSidePanel: string
  activeExplorerView?: LearningResourceExplorerView
  projectFolders: LearningResourceTreeFolder[]
  fileFolders: LearningResourceTreeFolder[]
  currentFileTitle?: string
  currentFileSubtitle?: string
  collapsedFolderIds?: string[]
}>(), {
  activeExplorerView: 'project',
  currentFileTitle: '',
  currentFileSubtitle: '',
  collapsedFolderIds: () => [],
})

const emit = defineEmits<{
  selectExplorerView: [view: LearningResourceExplorerView]
  selectPanel: [panelId: string]
  toggleFolder: [folderId: string]
  openResource: [resource: LearningResourceTreeItem]
  emptyAction: [folder: LearningResourceTreeFolder]
}>()

const activeFolders = computed(() => {
  return props.activeExplorerView === 'file' ? props.fileFolders : props.projectFolders
})

function renderFolders(folders: LearningResourceTreeFolder[]) {
  return folders
}

function resolveResourceIcon(kind: string) {
  const iconMap: Record<string, string> = {
    pdf: 'PDF',
    outline: '目',
    page: 'P',
    bookmark: '签',
    selection: '标',
    reference: '引',
    note: 'N',
    'anchor-note': 'A',
    asset: 'K',
    review: 'R',
    'question-bank': 'Q',
    mistake: 'M',
    prompt: 'P',
  }
  return iconMap[kind] ?? '•'
}
</script>

<style scoped>
.learning-resource-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  border-right: 1px solid #d9e2ec;
  background: #ffffff;
  color: #102033;
}

.learning-resource-panel__scope-tabs,
.learning-resource-panel__file,
.learning-resource-panel__switcher {
  border-bottom: 1px solid #e5edf4;
}

.learning-resource-panel strong,
.learning-resource-panel span,
.learning-resource-panel small {
  min-width: 0;
}

.learning-resource-item small {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.learning-resource-panel button {
  border: 1px solid #d9e2ec;
  border-radius: 7px;
  background: #f8fafc;
  color: #102033;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.learning-resource-panel__scope-tabs,
.learning-resource-panel__switcher {
  display: grid;
  gap: 6px;
  padding: 8px 10px;
}

.learning-resource-panel__scope-tabs {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.learning-resource-panel__scope-tabs button {
  min-height: 32px;
  font-size: 12px;
}

.learning-resource-panel__scope-tabs button.active {
  border-color: rgba(15, 143, 137, 0.36);
  background: #eef7f6;
  color: #0f766e;
}

.learning-resource-panel__file {
  display: grid;
  gap: 8px;
  padding: 8px 10px;
  background: #fbfdff;
}

.learning-resource-panel__file > div {
  display: grid;
  gap: 3px;
}

.learning-resource-panel__file span {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.learning-resource-panel__switcher {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 0;
  border-bottom: 0;
  background: transparent;
}

.learning-resource-panel__switcher button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  padding: 0 8px;
  border-color: #e5edf4;
  background: #ffffff;
  font-size: 12px;
}

.learning-resource-panel__switcher button.active {
  border-color: rgba(15, 143, 137, 0.35);
  background: #f7fffd;
  box-shadow: inset 2px 0 0 #0f8f89;
  color: #0f8f89;
}

.learning-resource-panel__tree {
  min-height: 0;
  overflow: auto;
  padding: 8px 10px 12px;
}

.learning-resource-folder {
  margin-bottom: 4px;
}

.learning-resource-folder--child {
  margin-bottom: 3px;
}

.learning-resource-folder__header {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
  width: 100%;
  min-height: 30px;
  padding: 0 8px;
  border-color: transparent;
  background: transparent;
  font-size: 12px;
  text-align: left;
}

.learning-resource-folder__header:hover {
  background: #f8fafc;
}

.learning-resource-folder.collapsed .learning-resource-folder__header span {
  transform: rotate(0deg);
}

.learning-resource-folder__header span {
  transform: rotate(90deg);
}

.learning-resource-folder__body {
  display: grid;
  gap: 3px;
  padding: 2px 0 4px 20px;
}

.learning-resource-folder--child > .learning-resource-folder__header {
  min-height: 34px;
  border-color: #e5edf4;
  background: #ffffff;
}

.learning-resource-folder--child > .learning-resource-folder__body {
  padding-left: 18px;
}

.learning-resource-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 7px;
  align-items: center;
  min-height: 38px;
  padding: 5px 6px;
  border-color: transparent;
  background: #ffffff;
  text-align: left;
}

.learning-resource-item:hover {
  border-color: #e5edf4;
  background: #f8fafc;
}

.learning-resource-item > span:first-child {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 6px;
  background: #f1f5f9;
  color: #0f766e;
  font-size: 11px;
}

.learning-resource-item > span:nth-child(2) {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.learning-resource-item mark {
  border-radius: 999px;
  background: #eaf2ff;
  color: #2563eb;
  padding: 2px 6px;
  font-size: 11px;
}

.learning-resource-empty {
  min-height: 32px;
  border-style: dashed !important;
  background: #fbfdff !important;
  color: #667085 !important;
}
</style>
