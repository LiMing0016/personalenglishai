<template>
  <section class="backlinks-panel" aria-label="反向链接">
    <header>
      <strong>反向链接</strong>
      <span>{{ backlinks.length }} 个来源引用当前知识卡</span>
    </header>

    <button
      v-for="backlink in backlinks"
      :key="backlink.id"
      type="button"
      class="backlinks-panel__item"
      @click="emit('openSource', backlink.sourceId)">
      <span>{{ resolveSourceLabel(backlink.sourceType) }}</span>
      <strong>{{ backlink.title }}</strong>
      <p>{{ backlink.excerpt }}</p>
      <small>
        {{ backlink.blockRef.pageNumber ? `Page ${backlink.blockRef.pageNumber}` : '块级引用' }}
        <template v-if="backlink.blockRef.bbox"> · {{ backlink.blockRef.bbox }}</template>
      </small>
      <mark v-for="tag in backlink.tags" :key="tag.id">#{{ tag.label }}</mark>
    </button>
  </section>
</template>

<script setup lang="ts">
import type { KnowledgeBacklink, LearningObjectType } from '../../types/learningIde'

defineProps<{
  backlinks: KnowledgeBacklink[]
}>()

const emit = defineEmits<{
  openSource: [sourceId: string]
}>()

function resolveSourceLabel(type: LearningObjectType) {
  const labels: Record<string, string> = {
    'pdf-selection': 'PDF 选区',
    note: '笔记',
    mistake: '错题',
    'word-card': '单词卡',
    quiz: '练习',
  }
  return labels[type] ?? '来源'
}
</script>

<style scoped>
.backlinks-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #102033;
}

.backlinks-panel header {
  display: grid;
  gap: 3px;
}

.backlinks-panel header span {
  color: #667085;
  font-size: 12px;
}

.backlinks-panel__item {
  display: grid;
  gap: 5px;
  padding: 10px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
  color: #102033;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.backlinks-panel__item span,
.backlinks-panel__item small {
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.backlinks-panel__item p {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.45;
}

.backlinks-panel__item mark {
  justify-self: start;
  border-radius: 999px;
  background: #eef7f6;
  color: #0f8f89;
  padding: 2px 7px;
  font-size: 11px;
}
</style>
