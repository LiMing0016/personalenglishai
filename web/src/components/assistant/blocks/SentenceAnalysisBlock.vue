<template>
  <article class="learning-block sentence-analysis">
    <header class="block-header">
      <p class="block-kicker">句子分析</p>
      <h3>{{ block.title || '句子结构拆解' }}</h3>
    </header>

    <p class="sentence">{{ data.sentence }}</p>
    <p v-if="data.translation" class="translation">{{ data.translation }}</p>

    <section v-if="structureItems.length" class="block-section">
      <p class="section-title">句子主干</p>
      <div class="structure-grid">
        <div v-for="item in structureItems" :key="item.label" class="structure-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </section>

    <section v-if="data.chunks?.length" class="block-section">
      <p class="section-title">成分拆解</p>
      <div class="chunk-list">
        <div v-for="chunk in data.chunks" :key="`${chunk.role}-${chunk.text}`" class="chunk-row">
          <span class="chunk-role">{{ chunk.role }}</span>
          <div>
            <p class="chunk-text">{{ chunk.text }}</p>
            <p v-if="chunk.explanation" class="chunk-explanation">{{ chunk.explanation }}</p>
          </div>
        </div>
      </div>
    </section>

    <section v-if="data.grammarPoints?.length" class="block-section">
      <p class="section-title">语法点</p>
      <ul class="grammar-list">
        <li v-for="point in data.grammarPoints" :key="point.name">
          <strong>{{ point.name }}</strong>
          <span v-if="point.explanation">{{ point.explanation }}</span>
        </li>
      </ul>
    </section>

    <section v-if="data.improvedVersions?.length" class="block-section">
      <p class="section-title">表达升级</p>
      <ol class="upgrade-list">
        <li v-for="version in data.improvedVersions" :key="version">{{ version }}</li>
      </ol>
    </section>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { SentenceAnalysisBlock } from '@/types/assistantBlocks.ts'

const props = defineProps<{
  block: SentenceAnalysisBlock
}>()

const data = computed(() => props.block.data)

const structureItems = computed(() => {
  const structure = data.value.structure ?? {}
  return [
    ['主语', structure.subject],
    ['谓语', structure.predicate],
    ['宾语', structure.object],
    ['补语', structure.complement],
  ]
    .filter((item): item is [string, string] => typeof item[1] === 'string' && item[1].trim().length > 0)
    .map(([label, value]) => ({ label, value }))
})
</script>

<style scoped>
.learning-block {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
  color: #0f172a;
}

.sentence-analysis {
  padding: 18px;
}

.block-header {
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 12px;
}

.block-kicker,
.section-title {
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

.sentence {
  margin: 14px 0 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.55;
}

.translation {
  margin: 6px 0 0;
  color: #475569;
  line-height: 1.6;
}

.block-section {
  margin-top: 16px;
}

.structure-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.structure-item {
  min-width: 0;
  border-radius: 8px;
  background: #f8fafc;
  padding: 9px 10px;
}

.structure-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.structure-item strong {
  display: block;
  margin-top: 3px;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.chunk-row {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 10px;
  border-top: 1px solid #e2e8f0;
  padding-top: 10px;
}

.chunk-row:first-child {
  border-top: none;
  padding-top: 0;
}

.chunk-role {
  align-self: flex-start;
  border-radius: 999px;
  background: #eef2ff;
  color: #3730a3;
  padding: 4px 7px;
  text-align: center;
  font-size: 12px;
  font-weight: 800;
}

.chunk-text,
.chunk-explanation {
  margin: 0;
  line-height: 1.55;
}

.chunk-text {
  font-weight: 800;
}

.chunk-explanation {
  margin-top: 3px;
  color: #64748b;
  font-size: 13px;
}

.grammar-list,
.upgrade-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 8px 0 0;
  padding-left: 18px;
}

.grammar-list li {
  color: #334155;
  line-height: 1.55;
}

.grammar-list span {
  display: block;
  color: #64748b;
}

.upgrade-list li {
  color: #334155;
  line-height: 1.55;
}

@media (max-width: 640px) {
  .structure-grid,
  .chunk-row {
    grid-template-columns: 1fr;
  }

  .chunk-role {
    width: fit-content;
  }
}
</style>
