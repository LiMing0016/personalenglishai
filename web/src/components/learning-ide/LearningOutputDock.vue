<template>
  <section class="learning-output-dock" aria-label="学习产出">
    <header>
      <strong>学习产出</strong>
      <span>从 PDF、笔记、知识卡和练习沉淀</span>
    </header>

    <nav class="learning-output-dock__tabs" aria-label="产出类型">
      <button
        v-for="item in outputs"
        :key="item.id"
        type="button"
        :class="{ active: item.id === activeOutputId }"
        @click="emit('selectOutput', item.id)">
        <span>{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
      </button>
    </nav>

    <article class="learning-output-dock__preview">
      <strong>{{ activeOutput?.label || '结构化笔记' }}</strong>
      <p>{{ previewText }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { LearningOutputItem } from '../../types/learningIde'

const props = defineProps<{
  outputs: LearningOutputItem[]
  activeOutputId?: string
}>()

const emit = defineEmits<{
  selectOutput: [outputId: string]
}>()

const activeOutput = computed(() => {
  return props.outputs.find((item) => item.id === props.activeOutputId) ?? props.outputs[0] ?? null
})

const previewText = computed(() => {
  if (!activeOutput.value) return '当前资料还没有沉淀内容。'
  if (activeOutput.value.status === 'draft') return '有草稿等待确认，可以继续让 AI 生成或手动修改。'
  if (activeOutput.value.status === 'reviewing') return '这些内容已经加入复习队列。'
  return '这些内容已经可以作为知识卡、笔记或练习继续使用。'
})
</script>

<style scoped>
.learning-output-dock {
  display: grid;
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr) minmax(240px, 360px);
  gap: 12px;
  align-items: stretch;
  min-height: 118px;
  border-top: 1px solid #d9e2ec;
  background: #ffffff;
  color: #102033;
}

.learning-output-dock header,
.learning-output-dock__preview {
  display: grid;
  align-content: center;
  gap: 5px;
  padding: 12px 14px;
}

.learning-output-dock header span,
.learning-output-dock__preview p {
  margin: 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.45;
}

.learning-output-dock__tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 12px 0;
}

.learning-output-dock button {
  display: grid;
  gap: 4px;
  align-content: center;
  min-height: 74px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
  color: #102033;
  font: inherit;
  cursor: pointer;
}

.learning-output-dock button.active {
  border-color: rgba(15, 143, 137, 0.35);
  background: #eef7f6;
  color: #0f8f89;
}

.learning-output-dock button span {
  font-size: 12px;
  font-weight: 800;
}

.learning-output-dock button strong {
  font-size: 18px;
}

.learning-output-dock__preview {
  border-left: 1px solid #e5edf4;
}
</style>
