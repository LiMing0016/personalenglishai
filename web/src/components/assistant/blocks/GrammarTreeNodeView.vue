<template>
  <li class="grammar-node">
    <div class="node-body">
      <span class="node-dot"></span>
      <div class="node-content">
        <p class="node-label">{{ node.label }}</p>
        <p v-if="node.description" class="node-description">{{ node.description }}</p>
        <ul v-if="node.examples?.length" class="node-examples">
          <li v-for="example in node.examples" :key="example">{{ example }}</li>
        </ul>
      </div>
    </div>

    <ul v-if="node.children?.length" class="node-children">
      <GrammarTreeNodeView
        v-for="child in node.children"
        :key="child.id"
        :node="child"
      />
    </ul>
  </li>
</template>

<script setup lang="ts">
import type { GrammarTreeNode } from '@/types/assistantBlocks.ts'

defineOptions({
  name: 'GrammarTreeNodeView',
})

defineProps<{
  node: GrammarTreeNode
}>()
</script>

<style scoped>
.grammar-node {
  list-style: none;
}

.node-body {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.node-dot {
  width: 9px;
  height: 9px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #14b8a6;
  margin-top: 8px;
  box-shadow: 0 0 0 4px #ccfbf1;
}

.node-content {
  min-width: 0;
}

.node-label {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 850;
  line-height: 1.45;
}

.node-description {
  margin: 4px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.55;
}

.node-examples {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 6px 0 0;
  padding-left: 16px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.node-children {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 12px 0 0 4px;
  border-left: 1px solid #dbe3ea;
  padding-left: 18px;
}
</style>
