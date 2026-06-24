<template>
  <article class="learning-block vocab-card">
    <header class="block-header">
      <div>
        <p class="block-kicker">单词卡</p>
        <h3>{{ block.title || data.word }}</h3>
      </div>
      <span v-if="data.partOfSpeech" class="part-badge">{{ data.partOfSpeech }}</span>
    </header>

    <div class="word-line">
      <span class="word">{{ data.word }}</span>
      <span v-if="data.phonetic" class="phonetic">{{ data.phonetic }}</span>
    </div>

    <section v-if="data.meanings?.length" class="block-section">
      <p class="section-title">核心释义</p>
      <ul class="meaning-list">
        <li v-for="meaning in data.meanings" :key="meaning.text">
          <strong>{{ meaning.text }}</strong>
          <span v-if="meaning.usage">{{ meaning.usage }}</span>
        </li>
      </ul>
    </section>

    <section v-if="data.examples?.length" class="block-section">
      <p class="section-title">例句</p>
      <div class="example-list">
        <div v-for="example in data.examples" :key="example.en" class="example-row">
          <p class="example-en">{{ example.en }}</p>
          <p v-if="example.zh" class="example-zh">{{ example.zh }}</p>
        </div>
      </div>
    </section>

    <section v-if="data.collocations?.length" class="block-section">
      <p class="section-title">常见搭配</p>
      <div class="chip-list">
        <span
          v-for="item in data.collocations"
          :key="item.phrase"
          class="phrase-chip"
          :title="item.meaning"
        >
          {{ item.phrase }}
        </span>
      </div>
    </section>

    <p v-if="data.memoryTip" class="memory-tip">{{ data.memoryTip }}</p>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { VocabCardBlock } from '@/types/assistantBlocks.ts'

const props = defineProps<{
  block: VocabCardBlock
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

.vocab-card {
  padding: 18px;
}

.block-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
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

.part-badge {
  flex-shrink: 0;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  background: #fff7ed;
  color: #9a3412;
  padding: 5px 9px;
  font-size: 12px;
  font-weight: 800;
}

.word-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
  margin-top: 14px;
}

.word {
  font-size: 30px;
  font-weight: 850;
  line-height: 1.1;
}

.phonetic {
  color: #64748b;
  font-size: 14px;
  font-weight: 650;
}

.block-section {
  margin-top: 16px;
}

.meaning-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.meaning-list li {
  display: flex;
  flex-direction: column;
  gap: 3px;
  border-left: 3px solid #14b8a6;
  padding-left: 10px;
}

.meaning-list span,
.example-zh {
  color: #64748b;
}

.example-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.example-row {
  border-top: 1px solid #e2e8f0;
  padding-top: 10px;
}

.example-row:first-child {
  border-top: none;
  padding-top: 0;
}

.example-en,
.example-zh {
  margin: 0;
  line-height: 1.6;
}

.example-en {
  font-weight: 700;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.phrase-chip {
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #f8fafc;
  color: #334155;
  padding: 5px 9px;
  font-size: 12px;
  font-weight: 700;
}

.memory-tip {
  margin: 16px 0 0;
  border-radius: 8px;
  background: #f8fafc;
  padding: 11px 12px;
  color: #334155;
  line-height: 1.6;
}
</style>
