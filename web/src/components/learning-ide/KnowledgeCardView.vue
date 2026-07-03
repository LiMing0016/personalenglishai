<template>
  <article class="knowledge-card-view" aria-label="知识卡">
    <header>
      <span>知识卡</span>
      <h2>[[{{ card.title }}]]</h2>
      <p>{{ card.summary }}</p>
    </header>

    <section class="knowledge-card-view__aliases" aria-label="别名">
      <span v-for="alias in card.aliases" :key="alias">{{ alias }}</span>
    </section>

    <section class="knowledge-card-view__tags" aria-label="标签">
      <span v-for="tag in card.tags" :key="tag.id" :style="{ '--tag-color': tag.color }">
        #{{ tag.path.replace(/^#/, '') }}
      </span>
    </section>

    <section class="knowledge-card-view__refs" aria-label="块级引用">
      <button
        v-for="blockRef in card.blockRefs"
        :key="blockRef.id"
        type="button"
        @click="emit('openBlockRef', blockRef.id)">
        <strong>{{ blockRef.sourceType }} · {{ blockRef.pageNumber ? `Page ${blockRef.pageNumber}` : '块引用' }}</strong>
        <span>{{ blockRef.excerpt }}</span>
      </button>
    </section>
  </article>
</template>

<script setup lang="ts">
import type { KnowledgeCard } from '../../types/learningIde'

defineProps<{
  card: KnowledgeCard
}>()

const emit = defineEmits<{
  openBlockRef: [blockRefId: string]
}>()
</script>

<style scoped>
.knowledge-card-view {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #102033;
}

.knowledge-card-view header,
.knowledge-card-view__refs {
  display: grid;
  gap: 8px;
}

.knowledge-card-view h2,
.knowledge-card-view p {
  margin: 0;
}

.knowledge-card-view header > span {
  color: #0f8f89;
  font-size: 12px;
  font-weight: 900;
}

.knowledge-card-view h2 {
  font-size: 18px;
  line-height: 1.25;
}

.knowledge-card-view p,
.knowledge-card-view__refs span {
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.knowledge-card-view__aliases,
.knowledge-card-view__tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.knowledge-card-view__aliases span,
.knowledge-card-view__tags span {
  border: 1px solid #d9e2ec;
  border-radius: 999px;
  padding: 3px 8px;
  background: #f8fafc;
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.knowledge-card-view__tags span {
  border-color: color-mix(in srgb, var(--tag-color) 24%, #d9e2ec);
  background: color-mix(in srgb, var(--tag-color) 9%, #ffffff);
  color: var(--tag-color);
}

.knowledge-card-view__refs button {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
  color: #102033;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
</style>
