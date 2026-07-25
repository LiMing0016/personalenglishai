<template>
  <div class="blocks-editor">
    <section v-for="(block, blockIndex) in model.blocks" :key="block.id" class="blocks-editor__block">
      <header>
        <span>{{ blockTypeLabel(block.type) }}</span>
        <div class="blocks-editor__actions">
          <button type="button" title="上移" aria-label="上移内容块" :disabled="blockIndex === 0" @click="moveBlock(blockIndex, -1)"><ChevronUp aria-hidden="true" /></button>
          <button type="button" title="下移" aria-label="下移内容块" :disabled="blockIndex === model.blocks.length - 1" @click="moveBlock(blockIndex, 1)"><ChevronDown aria-hidden="true" /></button>
          <button type="button" title="删除" aria-label="删除内容块" class="blocks-editor__delete" @click="removeBlock(blockIndex)"><Trash2 aria-hidden="true" /></button>
        </div>
      </header>

      <label class="blocks-editor__title">
        <span>标题</span>
        <input :value="block.title" maxlength="200" @input="updateTitle(blockIndex, $event)">
      </label>

      <template v-if="block.type === 'exampleList'">
        <div v-for="(item, itemIndex) in block.content.items" :key="itemIndex" class="blocks-editor__pair">
          <input :value="item.sentence" aria-label="英文例句" placeholder="英文例句" @input="updateItemField(blockIndex, itemIndex, 'sentence', $event)">
          <input :value="item.translation" aria-label="例句翻译" placeholder="中文翻译" @input="updateItemField(blockIndex, itemIndex, 'translation', $event)">
          <RemoveItemButton @remove="removeItem(blockIndex, itemIndex)" />
        </div>
        <button type="button" class="blocks-editor__add-row" @click="addItem(blockIndex)"><Plus aria-hidden="true" />添加例句</button>
      </template>

      <template v-else-if="block.type === 'collocationList'">
        <div v-for="(item, itemIndex) in block.content.items" :key="itemIndex" class="blocks-editor__pair">
          <input :value="item.expression" aria-label="搭配表达" placeholder="搭配表达" @input="updateItemField(blockIndex, itemIndex, 'expression', $event)">
          <input :value="item.translation" aria-label="搭配释义" placeholder="中文释义" @input="updateItemField(blockIndex, itemIndex, 'translation', $event)">
          <RemoveItemButton @remove="removeItem(blockIndex, itemIndex)" />
        </div>
        <button type="button" class="blocks-editor__add-row" @click="addItem(blockIndex)"><Plus aria-hidden="true" />添加搭配</button>
      </template>

      <div v-else-if="block.type === 'usageBoundary'" class="blocks-editor__columns">
        <label><span>适合使用</span><textarea :value="block.content.useWhen.join('\n')" rows="5" @input="updateLines(blockIndex, 'useWhen', $event)"></textarea></label>
        <label><span>谨慎使用</span><textarea :value="block.content.avoidWhen.join('\n')" rows="5" @input="updateLines(blockIndex, 'avoidWhen', $event)"></textarea></label>
      </div>

      <template v-else-if="block.type === 'contrastTable'">
        <div v-for="(row, rowIndex) in block.content.rows" :key="rowIndex" class="blocks-editor__contrast">
          <input :value="row.term" aria-label="对比词" placeholder="词汇" @input="updateContrastField(blockIndex, rowIndex, 'term', $event)">
          <input :value="row.focus" aria-label="对比侧重点" placeholder="侧重点" @input="updateContrastField(blockIndex, rowIndex, 'focus', $event)">
          <input :value="row.typicalContext" aria-label="典型语境" placeholder="典型语境" @input="updateContrastField(blockIndex, rowIndex, 'typicalContext', $event)">
          <RemoveItemButton @remove="removeItem(blockIndex, rowIndex)" />
        </div>
        <button type="button" class="blocks-editor__add-row" @click="addItem(blockIndex)"><Plus aria-hidden="true" />添加对比词</button>
      </template>

      <label v-else-if="block.type === 'memoryTip'" class="blocks-editor__wide-field">
        <span>记忆提示（每行一条）</span>
        <textarea :value="block.content.points.join('\n')" rows="6" @input="updateLines(blockIndex, 'points', $event)"></textarea>
      </label>

      <label v-else-if="block.type === 'note'" class="blocks-editor__wide-field">
        <span>Markdown 笔记</span>
        <textarea :value="block.content" rows="10" maxlength="20000" @input="updateNote(blockIndex, $event)"></textarea>
      </label>

      <p v-else class="blocks-editor__legacy">历史 Markdown 为只读内容，重新生成或新增笔记后可继续结构化整理。</p>
    </section>

    <button type="button" class="blocks-editor__add-note" @click="addNote"><Plus aria-hidden="true" />添加笔记</button>
  </div>
</template>

<script setup lang="ts">
import { ChevronDown, ChevronUp, Plus, Trash2, X } from 'lucide-vue-next'
import { defineComponent, h } from 'vue'

import type {
  VocabularyCardBlock,
  VocabularyCardBlocks,
} from '@/api/vocabulary'

const model = defineModel<VocabularyCardBlocks>({ required: true })

const RemoveItemButton = defineComponent({
  emits: ['remove'],
  setup(_, { emit }) {
    return () => h('button', {
      type: 'button',
      class: 'blocks-editor__remove-item',
      title: '移除',
      'aria-label': '移除此项',
      onClick: () => emit('remove'),
    }, [h(X, { 'aria-hidden': 'true' })])
  },
})

function cloneBlocks() {
  return JSON.parse(JSON.stringify(model.value)) as VocabularyCardBlocks
}

function commit(blocks: VocabularyCardBlocks) {
  blocks.blocks.forEach((block, index) => { block.sortOrder = (index + 1) * 10 })
  model.value = blocks
}

function markEdited(block: VocabularyCardBlock) {
  if (block.type === 'legacyMarkdown') return
  block.source = 'user'
  block.sourceRef = null
  block.userEdited = true
  block.locked = true
}

function editBlock(index: number, change: (block: VocabularyCardBlock) => void) {
  const next = cloneBlocks()
  const block = next.blocks[index]
  if (!block) return
  change(block)
  markEdited(block)
  commit(next)
}

function eventValue(event: Event) {
  return (event.target as HTMLInputElement | HTMLTextAreaElement).value
}

function updateTitle(index: number, event: Event) {
  editBlock(index, (block) => { block.title = eventValue(event) })
}

function updateItemField(
  blockIndex: number,
  itemIndex: number,
  field: 'sentence' | 'translation' | 'expression',
  event: Event,
) {
  editBlock(blockIndex, (block) => {
    const value = eventValue(event)
    if (block.type === 'exampleList') {
      const item = block.content.items[itemIndex]
      if (item && (field === 'sentence' || field === 'translation')) item[field] = value
    }
    if (block.type === 'collocationList') {
      const item = block.content.items[itemIndex]
      if (item && (field === 'expression' || field === 'translation')) item[field] = value
    }
  })
}

function updateContrastField(
  blockIndex: number,
  rowIndex: number,
  field: 'term' | 'focus' | 'typicalContext',
  event: Event,
) {
  editBlock(blockIndex, (block) => {
    if (block.type !== 'contrastTable') return
    const row = block.content.rows[rowIndex]
    if (row) row[field] = eventValue(event)
  })
}

function updateLines(blockIndex: number, field: 'useWhen' | 'avoidWhen' | 'points', event: Event) {
  const lines = eventValue(event).split('\n').map((line) => line.trim()).filter(Boolean)
  editBlock(blockIndex, (block) => {
    if (block.type === 'usageBoundary' && (field === 'useWhen' || field === 'avoidWhen')) {
      block.content[field] = lines
    } else if (block.type === 'memoryTip' && field === 'points') {
      block.content.points = lines
    }
  })
}

function updateNote(blockIndex: number, event: Event) {
  editBlock(blockIndex, (block) => {
    if (block.type === 'note') block.content = eventValue(event)
  })
}

function addItem(blockIndex: number) {
  editBlock(blockIndex, (block) => {
    if (block.type === 'exampleList') block.content.items.push({ sentence: '', translation: '' })
    if (block.type === 'collocationList') block.content.items.push({ expression: '', translation: '' })
    if (block.type === 'contrastTable') block.content.rows.push({ term: '', focus: '', typicalContext: '' })
  })
}

function removeItem(blockIndex: number, itemIndex: number) {
  editBlock(blockIndex, (block) => {
    if (block.type === 'exampleList' || block.type === 'collocationList') block.content.items.splice(itemIndex, 1)
    if (block.type === 'contrastTable') block.content.rows.splice(itemIndex, 1)
  })
}

function moveBlock(index: number, direction: -1 | 1) {
  const next = cloneBlocks()
  const target = index + direction
  if (target < 0 || target >= next.blocks.length) return
  const [block] = next.blocks.splice(index, 1)
  if (!block) return
  next.blocks.splice(target, 0, block)
  markEdited(block)
  commit(next)
}

function removeBlock(index: number) {
  const next = cloneBlocks()
  next.blocks.splice(index, 1)
  commit(next)
}

function addNote() {
  const next = cloneBlocks()
  next.blocks.push({
    id: `block_note_${Date.now().toString(36)}`,
    type: 'note',
    title: '我的笔记',
    meaningRefs: [],
    format: 'markdown',
    content: '## 我的笔记\n',
    source: 'user',
    sourceRef: null,
    sortOrder: (next.blocks.length + 1) * 10,
    userEdited: true,
    locked: true,
  })
  commit(next)
}

function blockTypeLabel(type: VocabularyCardBlock['type']) {
  return {
    exampleList: '例句',
    collocationList: '搭配',
    usageBoundary: '使用边界',
    contrastTable: '易混辨析',
    memoryTip: '记忆提示',
    note: '笔记',
    legacyMarkdown: '历史内容',
  }[type]
}

</script>

<style scoped>
.blocks-editor { min-width: 0; max-width: 1060px; display: grid; gap: 18px; margin: 22px auto 0; }
.blocks-editor__block { min-width: 0; display: grid; gap: 12px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; padding: 16px; }
.blocks-editor__block > header { min-width: 0; display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.blocks-editor__block > header > span { color: #047857; font-size: 12px; font-weight: 800; }
.blocks-editor__actions { display: flex; gap: 4px; }
.blocks-editor button { min-height: 34px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #475569; }
.blocks-editor__actions button, .blocks-editor__remove-item { width: 34px; min-width: 34px; display: inline-grid; place-items: center; padding: 0; }
.blocks-editor button svg { width: 16px; height: 16px; }
.blocks-editor button:disabled { opacity: .45; }
.blocks-editor__delete, .blocks-editor__remove-item { color: #b91c1c !important; }
.blocks-editor label { min-width: 0; display: grid; gap: 6px; }
.blocks-editor label > span { color: #475569; font-size: 12px; font-weight: 800; }
.blocks-editor input, .blocks-editor textarea { box-sizing: border-box; width: 100%; min-width: 0; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #0f172a; font: inherit; padding: 9px 11px; resize: vertical; }
.blocks-editor textarea { line-height: 1.6; }
.blocks-editor__pair { min-width: 0; display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr) 34px; gap: 8px; }
.blocks-editor__contrast { min-width: 0; display: grid; grid-template-columns: minmax(100px, .6fr) minmax(0, 1fr) minmax(0, 1fr) 34px; gap: 8px; }
.blocks-editor__columns { min-width: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.blocks-editor__add-row, .blocks-editor__add-note { justify-self: start; display: inline-flex; align-items: center; gap: 7px; padding: 7px 11px; }
.blocks-editor__add-note { border-color: #a7f3d0 !important; color: #047857 !important; }
.blocks-editor__legacy { margin: 0; color: #64748b; line-height: 1.6; }
@media (max-width: 767px) {
  .blocks-editor__pair, .blocks-editor__contrast, .blocks-editor__columns { grid-template-columns: 1fr; }
  .blocks-editor__pair .blocks-editor__remove-item, .blocks-editor__contrast .blocks-editor__remove-item { justify-self: end; }
}
</style>
