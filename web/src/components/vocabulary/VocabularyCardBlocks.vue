<template>
  <div class="card-blocks">
    <section
      v-for="block in orderedBlocks"
      :id="sectionId(block.id)"
      :key="block.id"
      class="card-blocks__section card-inspector__document-section"
      :aria-labelledby="`${sectionId(block.id)}-title`"
    >
      <header class="card-blocks__heading">
        <component :is="blockIcon(block.type)" aria-hidden="true" />
        <h3 :id="`${sectionId(block.id)}-title`">{{ block.title }}</h3>
      </header>

      <ol v-if="block.type === 'exampleList'" class="card-blocks__examples">
        <li v-for="(item, index) in block.content.items" :key="`${block.id}-${index}`">
          <div><span>{{ item.sentence }}</span><CopyButton :text="item.sentence" /></div>
          <p>{{ item.translation }}</p>
        </li>
      </ol>

      <ul v-else-if="block.type === 'collocationList'" class="card-blocks__collocations">
        <li v-for="(item, index) in block.content.items" :key="`${block.id}-${index}`">
          <div><strong>{{ item.expression }}</strong><span>{{ item.translation }}</span></div>
          <CopyButton :text="item.expression" />
        </li>
      </ul>

      <div v-else-if="block.type === 'usageBoundary'" class="card-blocks__usage">
        <section v-if="block.content.useWhen.length">
          <h4>适合使用</h4>
          <ul><li v-for="item in block.content.useWhen" :key="item">{{ item }}</li></ul>
        </section>
        <section v-if="block.content.avoidWhen.length">
          <h4>谨慎使用</h4>
          <ul><li v-for="item in block.content.avoidWhen" :key="item">{{ item }}</li></ul>
        </section>
      </div>

      <div v-else-if="block.type === 'contrastTable'" class="card-blocks__table-wrap">
        <table>
          <thead><tr><th>词汇</th><th>侧重点</th><th>典型语境</th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in block.content.rows" :key="`${block.id}-${index}`">
              <th scope="row">{{ row.term }}</th><td>{{ row.focus }}</td><td>{{ row.typicalContext }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <ul v-else-if="block.type === 'memoryTip'" class="card-blocks__memory">
        <li v-for="point in block.content.points" :key="point">{{ point }}</li>
      </ul>

      <VocabularyMarkdownRenderer
        v-else-if="block.type === 'note' || block.type === 'legacyMarkdown'"
        :markdown="block.content"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import {
  BookOpen,
  Brain,
  Copy,
  Link2,
  NotebookPen,
  Scale,
  ShieldCheck,
} from 'lucide-vue-next'
import { computed, defineComponent, h, watch } from 'vue'

import type { MarkdownSection } from '../assistant/markdown'
import type { VocabularyCardBlock, VocabularyCardBlocks } from '@/api/vocabulary'
import VocabularyMarkdownRenderer from './VocabularyMarkdownRenderer.vue'

const props = defineProps<{ cardBlocks: VocabularyCardBlocks }>()
const emit = defineEmits<{ 'sections-change': [sections: MarkdownSection[]] }>()

const orderedBlocks = computed(() => [...props.cardBlocks.blocks].sort((a, b) => a.sortOrder - b.sortOrder))

const CopyButton = defineComponent({
  props: { text: { type: String, required: true } },
  setup(copyProps) {
    return () => h('button', {
      type: 'button',
      class: 'card-blocks__copy',
      title: '复制',
      'aria-label': `复制：${copyProps.text}`,
      onClick: () => copyText(copyProps.text),
    }, [h(Copy, { 'aria-hidden': 'true' })])
  },
})

function sectionId(id: string) {
  return `card-block-${id.replace(/[^A-Za-z0-9_-]/g, '-')}`
}

function blockIcon(type: VocabularyCardBlock['type']) {
  return {
    exampleList: BookOpen,
    collocationList: Link2,
    usageBoundary: ShieldCheck,
    contrastTable: Scale,
    memoryTip: Brain,
    note: NotebookPen,
    legacyMarkdown: NotebookPen,
  }[type]
}

async function copyText(value: string) {
  if (typeof navigator === 'undefined' || !navigator.clipboard) return
  await navigator.clipboard.writeText(value)
}

watch(orderedBlocks, (blocks) => {
  emit('sections-change', blocks.map((block) => ({
    id: sectionId(block.id),
    title: block.title,
    level: 2,
  })))
}, { immediate: true })
</script>

<style scoped>
.card-blocks { min-width: 0; }
.card-blocks__section { min-width: 0; padding-top: 26px; border-top: 1px solid #dce7e1; }
.card-blocks__section + .card-blocks__section { margin-top: 34px; }
.card-blocks__heading { display: flex; align-items: center; gap: 9px; margin-bottom: 16px; }
.card-blocks__heading svg { width: 19px; height: 19px; color: #059669; }
.card-blocks__heading h3 { margin: 0; color: #0f172a; font-size: 20px; letter-spacing: 0; }
.card-blocks__examples, .card-blocks__collocations, .card-blocks__memory, .card-blocks__usage ul { margin: 0; color: #334155; line-height: 1.7; }
.card-blocks__examples { display: grid; gap: 16px; padding-left: 26px; }
.card-blocks__examples li::marker { color: #059669; font-weight: 800; }
.card-blocks__examples li > div { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.card-blocks__examples p { margin: 3px 0 0; color: #64748b; }
.card-blocks__copy { width: 30px; height: 30px; min-width: 30px; display: inline-grid; place-items: center; border: 0; background: transparent; color: #94a3b8; padding: 0; }
.card-blocks__copy:hover { color: #047857; background: #ecfdf5; }
.card-blocks__copy svg { width: 16px; height: 16px; }
.card-blocks__collocations { display: grid; gap: 10px; padding: 0; list-style: none; }
.card-blocks__collocations li { min-width: 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.card-blocks__collocations li > div { min-width: 0; display: flex; flex-wrap: wrap; gap: 8px 16px; }
.card-blocks__collocations strong { color: #0f172a; }
.card-blocks__collocations span { color: #64748b; }
.card-blocks__usage { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 28px; }
.card-blocks__usage h4 { margin: 0 0 8px; color: #334155; font-size: 14px; }
.card-blocks__usage ul { padding-left: 20px; }
.card-blocks__table-wrap { min-width: 0; overflow-x: auto; }
.card-blocks table { width: 100%; border-collapse: collapse; color: #334155; font-size: 14px; }
.card-blocks th, .card-blocks td { border: 1px solid #dce7e1; padding: 10px 12px; text-align: left; vertical-align: top; overflow-wrap: anywhere; }
.card-blocks thead th { background: #f8fafc; color: #475569; }
.card-blocks tbody th { color: #0f172a; }
.card-blocks__memory { border-left: 3px solid #34d399; background: #f0fdf4; padding: 14px 18px 14px 36px; }
@media (max-width: 767px) {
  .card-blocks__usage { grid-template-columns: 1fr; gap: 18px; }
  .card-blocks__heading h3 { font-size: 18px; }
  .card-blocks th, .card-blocks td { min-width: 130px; padding: 9px; }
}
</style>
