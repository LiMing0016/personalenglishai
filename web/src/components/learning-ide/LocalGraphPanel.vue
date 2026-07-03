<template>
  <section class="local-graph-panel" aria-label="局部知识图谱">
    <header>
      <strong>局部图谱</strong>
      <span>{{ graph.nodes.length }} 个节点 · {{ graph.edges.length }} 条关系</span>
    </header>

    <div class="local-graph-panel__nodes">
      <button
        v-for="node in graph.nodes"
        :key="node.id"
        type="button"
        :class="{ active: node.id === activeNodeId }"
        :style="{ '--node-weight': String(Math.max(1, Math.min(node.weight, 10))) }"
        @click="emit('selectNode', node.id)">
        <span>{{ node.type }}</span>
        <strong>{{ node.label }}</strong>
      </button>
    </div>

    <div class="local-graph-panel__edges" aria-label="知识关系">
      <span v-for="edge in graph.edges.slice(0, 5)" :key="edge.id">
        {{ edge.source }} → {{ edge.target }} · {{ edge.relation }}
      </span>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { KnowledgeGraph } from '../../types/learningIde'

defineProps<{
  graph: KnowledgeGraph
  activeNodeId?: string
}>()

const emit = defineEmits<{
  selectNode: [nodeId: string]
}>()
</script>

<style scoped>
.local-graph-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #102033;
}

.local-graph-panel header {
  display: grid;
  gap: 3px;
}

.local-graph-panel header span,
.local-graph-panel__edges span,
.local-graph-panel__nodes button span {
  color: #667085;
  font-size: 12px;
}

.local-graph-panel__nodes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.local-graph-panel__nodes button {
  display: grid;
  gap: 4px;
  min-height: calc(48px + var(--node-weight) * 1px);
  padding: 8px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
  color: #102033;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.local-graph-panel__nodes button.active {
  border-color: rgba(15, 143, 137, 0.38);
  background: #eef7f6;
}

.local-graph-panel__nodes button strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.local-graph-panel__edges {
  display: grid;
  gap: 4px;
}
</style>
