<template>
  <div v-if="blocks.length" class="assistant-blocks" aria-label="学习组件">
    <section
      v-for="block in blocks"
      :key="block.id"
      class="assistant-block-shell"
    >
      <component
        :is="componentForBlock(block)"
        v-if="componentForBlock(block)"
        :block="block"
      />
      <div v-else class="assistant-block-fallback">
        <p class="assistant-block-fallback-title">学习卡片</p>
        <div
          class="assistant-block-fallback-content"
          v-html="renderAssistantMarkdown(block.fallbackMarkdown)"
        ></div>
      </div>

      <div v-if="actionsFor(block)?.length" class="assistant-block-actions">
        <button
          v-for="action in actionsFor(block)"
          :key="action.id"
          type="button"
          class="assistant-block-action"
          @click="$emit('action', action.prompt)"
        >
          {{ action.label }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent, type Component } from 'vue'

import { definitionFor, isFallbackAssistantBlock } from './learning-blocks/registry.ts'
import { renderAssistantMarkdown } from './markdown.ts'
import type { RenderableAssistantBlock } from '@/types/assistantBlocks.ts'

defineProps<{
  blocks: RenderableAssistantBlock[]
}>()

defineEmits<{
  action: [prompt: string]
}>()

const componentCache = new Map<string, Component>()

function componentForBlock(block: RenderableAssistantBlock) {
  const definition = definitionFor(block)
  if (!definition) return undefined
  const key = `${definition.type}@${definition.version}`
  const cached = componentCache.get(key)
  if (cached) return cached
  const component = defineAsyncComponent(definition.loadComponent)
  componentCache.set(key, component)
  return component
}

function actionsFor(block: RenderableAssistantBlock) {
  return isFallbackAssistantBlock(block) ? undefined : block.actions
}
</script>

<style scoped>
.assistant-blocks {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
}

.assistant-block-shell {
  width: min(640px, 100%);
}

.assistant-block-fallback {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  padding: 14px 16px;
  color: #334155;
  font-size: 14px;
  line-height: 1.6;
}

.assistant-block-fallback-title {
  margin: 0 0 6px;
  color: #0f172a;
  font-weight: 800;
}

.assistant-block-fallback p {
  margin: 0;
}

.assistant-block-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.assistant-block-action {
  min-height: 32px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  color: #0f766e;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
}

.assistant-block-action:hover,
.assistant-block-action:focus-visible {
  border-color: #14b8a6;
  background: #f0fdfa;
  outline: none;
}
</style>
