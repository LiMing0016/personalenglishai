<template>
  <article class="learning-block grammar-tree">
    <header class="block-header">
      <p class="block-kicker">语法树</p>
      <h3>{{ block.title || data.topic }}</h3>
    </header>

    <ul class="tree-root">
      <GrammarTreeNodeView :node="data.root" />
    </ul>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import GrammarTreeNodeView from './GrammarTreeNodeView.vue'
import type { GrammarTreeBlock } from '@/types/assistantBlocks.ts'

const props = defineProps<{
  block: GrammarTreeBlock
}>()

const data = computed(() => props.block.data)
</script>

<style scoped>
.learning-block {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
  color: #0f172a;
}

.grammar-tree {
  padding: 18px;
}

.block-header {
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 12px;
}

.block-kicker {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 850;
}

.block-header h3 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.35;
}

.tree-root {
  margin: 16px 0 0;
  padding: 0;
}
</style>
