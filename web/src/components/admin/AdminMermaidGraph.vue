<template>
  <div class="admin-mermaid-shell">
    <div v-if="!definition.trim()" class="admin-empty">{{ emptyText || '暂无图数据。' }}</div>
    <div v-else-if="renderError" class="admin-error">{{ renderError }}</div>
    <div ref="graphHost" class="admin-mermaid-graph"></div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  definition: string
  emptyText?: string
}>(), {
  emptyText: '暂无图数据。',
})

const graphHost = ref<HTMLDivElement | null>(null)
const renderError = ref('')
let renderIndex = 0

watch(() => props.definition, renderGraph, { immediate: true })

async function renderGraph() {
  await nextTick()
  if (!graphHost.value) return
  if (!props.definition.trim()) {
    graphHost.value.innerHTML = ''
    renderError.value = ''
    return
  }

  try {
    const mermaid = (await import('mermaid')).default
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'loose',
      theme: 'base',
      flowchart: {
        htmlLabels: true,
        curve: 'basis',
      },
    })
    const renderId = `admin-data-catalog-mermaid-${renderIndex++}`
    const { svg, bindFunctions } = await mermaid.render(renderId, props.definition)
    graphHost.value.innerHTML = svg
    bindFunctions?.(graphHost.value)
    renderError.value = ''
  } catch (error) {
    console.error(error)
    graphHost.value.innerHTML = ''
    renderError.value = '图形渲染失败，请检查关系定义或稍后重试。'
  }
}
</script>

<style scoped>
.admin-mermaid-shell {
  width: 100%;
}

.admin-mermaid-graph {
  width: 100%;
  overflow: auto;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(248, 252, 249, 0.98), rgba(255, 255, 255, 0.98));
  padding: 16px;
}

.admin-mermaid-graph :deep(svg) {
  min-width: 100%;
  height: auto;
}
</style>
