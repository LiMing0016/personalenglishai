<template>
  <div v-if="visible" class="pdf-selection-action-toolbar" role="toolbar" aria-label="PDF 选区操作">
    <span>{{ selectionType === 'region' ? '图表区域' : '文本选区' }}</span>
    <button
      v-for="action in actions"
      :key="action.id"
      type="button"
      @click="emit('action', action.id)">
      {{ action.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
interface SelectionAction {
  id: string
  label: string
}

withDefaults(defineProps<{
  visible: boolean
  selectionType?: 'text' | 'region'
  actions?: SelectionAction[]
}>(), {
  selectionType: 'text',
  actions: () => [
    { id: 'explain', label: '讲解' },
    { id: 'note', label: '写笔记' },
    { id: 'knowledge-card', label: '加入知识卡' },
    { id: 'mistake-book', label: '加入错题本' },
  ],
})

const emit = defineEmits<{
  action: [actionId: string]
}>()
</script>

<style scoped>
.pdf-selection-action-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  color: #102033;
}

.pdf-selection-action-toolbar span {
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.pdf-selection-action-toolbar button {
  min-height: 30px;
  border: 1px solid #d9e2ec;
  border-radius: 7px;
  background: #f8fafc;
  color: #102033;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}
</style>
