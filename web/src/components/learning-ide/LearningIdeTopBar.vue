<template>
  <header class="learning-ide-top-bar">
    <div class="learning-ide-top-bar__brand">
      <span aria-hidden="true">X</span>
      <strong>{{ brand }}</strong>
    </div>

    <nav class="learning-ide-top-bar__menu" aria-label="工作台菜单">
      <button v-for="item in menuItems" :key="item" type="button">{{ item }}</button>
    </nav>

    <button type="button" class="learning-ide-top-bar__add" @click="emit('addModule')">
      <span aria-hidden="true">+</span>
      添加学习工具
    </button>

    <form class="learning-ide-top-bar__search" role="search" @submit.prevent="submitCommand">
      <span aria-hidden="true">⌕</span>
      <input
        v-model="query"
        type="search"
        :placeholder="searchPlaceholder"
        aria-label="搜索或输入命令"
      />
    </form>

    <div class="learning-ide-top-bar__actions">
      <button type="button" aria-label="返回" title="返回" @click="emit('back')">←</button>
      <button type="button" aria-label="同步状态" title="同步状态">{{ syncStatus }}</button>
      <button type="button" class="learning-ide-top-bar__primary" @click="emit('complete')">完成学习</button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  brand?: string
  searchPlaceholder?: string
  syncStatus?: string
}>(), {
  brand: 'StudyingX',
  searchPlaceholder: '搜索 PDF、笔记、知识点，或输入命令...',
  syncStatus: '已同步',
})

const emit = defineEmits<{
  addModule: []
  commandSearch: [query: string]
  back: []
  complete: []
}>()

const menuItems = ['文件', '编辑', '视图', '学习', '工具', '窗口', '帮助']
const query = ref('')

function submitCommand() {
  const value = query.value.trim()
  if (!value) return
  emit('commandSearch', value)
}

void props
</script>

<style scoped>
.learning-ide-top-bar {
  display: grid;
  grid-template-columns: minmax(180px, 240px) auto auto minmax(260px, 420px) auto;
  gap: 12px;
  align-items: center;
  min-height: 58px;
  padding: 8px 16px;
  border-bottom: 1px solid #d9e2ec;
  background: #f7fafc;
  color: #102033;
}

.learning-ide-top-bar__brand,
.learning-ide-top-bar__menu,
.learning-ide-top-bar__actions,
.learning-ide-top-bar__search,
.learning-ide-top-bar__add {
  display: flex;
  align-items: center;
}

.learning-ide-top-bar__brand {
  min-width: 0;
  gap: 8px;
}

.learning-ide-top-bar__brand span {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 8px;
  background: #0f8f89;
  color: #ffffff;
  font-weight: 900;
}

.learning-ide-top-bar__brand strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.learning-ide-top-bar__menu {
  min-width: 0;
  gap: 4px;
}

.learning-ide-top-bar button {
  min-height: 34px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #26364a;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.learning-ide-top-bar__menu button {
  padding: 0 8px;
}

.learning-ide-top-bar button:hover,
.learning-ide-top-bar button:focus-visible {
  border-color: #c7d7e5;
  background: #ffffff;
  outline: none;
}

.learning-ide-top-bar__add {
  justify-content: center;
  gap: 8px;
  padding: 0 14px;
  border-color: rgba(15, 143, 137, 0.35) !important;
  background: #ffffff !important;
  color: #0f8f89 !important;
}

.learning-ide-top-bar__search {
  min-width: 0;
  gap: 8px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #667085;
}

.learning-ide-top-bar__search input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #102033;
  font: inherit;
}

.learning-ide-top-bar__actions {
  justify-content: flex-end;
  gap: 6px;
}

.learning-ide-top-bar__primary {
  border-color: #0f8f89 !important;
  background: #0f8f89 !important;
  color: #ffffff !important;
}

@media (max-width: 1280px) {
  .learning-ide-top-bar {
    grid-template-columns: minmax(160px, 220px) auto minmax(220px, 1fr) auto;
  }

  .learning-ide-top-bar__menu {
    display: none;
  }
}
</style>
