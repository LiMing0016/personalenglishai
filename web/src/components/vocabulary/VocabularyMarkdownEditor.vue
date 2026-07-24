<template>
  <div class="markdown-editor">
    <header class="markdown-editor__toolbar">
      <div v-if="editor && !sourceMode" class="markdown-editor__formatting" role="toolbar" aria-label="学习内容格式">
        <button
          type="button"
          :aria-pressed="editor.isActive('bold')"
          aria-label="加粗"
          title="加粗"
          @click="editor.chain().focus().toggleBold().run()"
        >
          <Bold aria-hidden="true" />
        </button>
        <button
          type="button"
          :aria-pressed="editor.isActive('italic')"
          aria-label="斜体"
          title="斜体"
          @click="editor.chain().focus().toggleItalic().run()"
        >
          <Italic aria-hidden="true" />
        </button>
        <button
          type="button"
          :aria-pressed="editor.isActive('heading', { level: 2 })"
          aria-label="二级标题"
          title="二级标题"
          @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
        >
          <Heading2 aria-hidden="true" />
        </button>
        <button
          type="button"
          :aria-pressed="editor.isActive('bulletList')"
          aria-label="无序列表"
          title="无序列表"
          @click="editor.chain().focus().toggleBulletList().run()"
        >
          <List aria-hidden="true" />
        </button>
        <button
          type="button"
          :aria-pressed="editor.isActive('orderedList')"
          aria-label="有序列表"
          title="有序列表"
          @click="editor.chain().focus().toggleOrderedList().run()"
        >
          <ListOrdered aria-hidden="true" />
        </button>
        <button
          type="button"
          :aria-pressed="editor.isActive('blockquote')"
          aria-label="引用"
          title="引用"
          @click="editor.chain().focus().toggleBlockquote().run()"
        >
          <Quote aria-hidden="true" />
        </button>
      </div>

      <div class="markdown-editor__meta">
        <button
          class="markdown-editor__outline-trigger"
          type="button"
          :aria-expanded="outlineOpen"
          aria-controls="vocabulary-markdown-outline"
          @click="outlineOpen = true"
        >
          <ListTree aria-hidden="true" />
          <span>学习内容目录</span>
        </button>
        <button
          class="markdown-editor__source-toggle"
          type="button"
          :aria-pressed="sourceMode"
          @click="toggleSourceMode"
        >
          <FileCode2 aria-hidden="true" />
          <span>{{ sourceMode ? '返回所见即所得' : '高级源码' }}</span>
        </button>
        <span :class="{ 'markdown-editor__count--error': tooLong }">
          {{ modelValue.length.toLocaleString() }} / 20,000
        </span>
      </div>
    </header>

    <div class="markdown-editor__layout">
      <main class="markdown-editor__canvas">
        <template v-if="!sourceMode">
          <BubbleMenu
            v-if="editor"
            :editor="editor"
            :options="{ placement: 'top', offset: 8 }"
            class="markdown-editor__bubble-menu"
          >
            <button
              type="button"
              :aria-pressed="editor.isActive('bold')"
              aria-label="加粗"
              title="加粗"
              @click="editor.chain().focus().toggleBold().run()"
            >
              <Bold aria-hidden="true" />
            </button>
            <button
              type="button"
              :aria-pressed="editor.isActive('italic')"
              aria-label="斜体"
              title="斜体"
              @click="editor.chain().focus().toggleItalic().run()"
            >
              <Italic aria-hidden="true" />
            </button>
            <button
              type="button"
              :aria-pressed="editor.isActive('code')"
              aria-label="行内代码"
              title="行内代码"
              @click="editor.chain().focus().toggleCode().run()"
            >
              <Code aria-hidden="true" />
            </button>
          </BubbleMenu>
          <EditorContent :editor="editor" class="markdown-editor__surface" />
        </template>

        <div v-else class="markdown-editor__source">
          <label for="vocabulary-markdown">Markdown 源码</label>
          <textarea
            id="vocabulary-markdown"
            :value="modelValue"
            maxlength="20000"
            rows="20"
            :aria-invalid="tooLong"
            aria-describedby="vocabulary-markdown-status"
            @input="updateValue"
          ></textarea>
          <p id="vocabulary-markdown-status" :class="{ 'markdown-editor__error': tooLong }" aria-live="polite">
            {{ tooLong ? '超过 20,000 字限制，请缩短内容后保存。' : '用于修复复杂 Markdown 结构，日常编辑无需进入源码。' }}
          </p>
        </div>
      </main>

      <aside class="markdown-editor__outline" aria-label="学习内容目录">
        <span>学习内容目录</span>
        <nav v-if="outline.length">
          <button
            v-for="item in outline"
            :key="item.id"
            type="button"
            :aria-current="activeOutlineId === item.id ? 'location' : undefined"
            @click="scrollToOutline(item.id)"
          >
            {{ item.title }}
          </button>
        </nav>
        <p v-else>使用二级标题组织内容后，将在这里生成目录。</p>
      </aside>
    </div>

    <div v-if="outlineOpen" class="markdown-editor__outline-backdrop" @click.self="outlineOpen = false">
      <aside id="vocabulary-markdown-outline" class="markdown-editor__outline-drawer" aria-label="学习内容目录">
        <header>
          <strong>学习内容目录</strong>
          <button type="button" aria-label="关闭目录" title="关闭目录" @click="outlineOpen = false">
            <X aria-hidden="true" />
          </button>
        </header>
        <nav v-if="outline.length">
          <button
            v-for="item in outline"
            :key="item.id"
            type="button"
            :aria-current="activeOutlineId === item.id ? 'location' : undefined"
            @click="scrollToOutline(item.id)"
          >
            {{ item.title }}
          </button>
        </nav>
        <p v-else>使用二级标题组织内容后，将在这里生成目录。</p>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import CharacterCount from '@tiptap/extension-character-count'
import Placeholder from '@tiptap/extension-placeholder'
import { Markdown } from '@tiptap/markdown'
import StarterKit from '@tiptap/starter-kit'
import { BubbleMenu } from '@tiptap/vue-3/menus'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import {
  Bold,
  Code,
  FileCode2,
  Heading2,
  Italic,
  List,
  ListOrdered,
  ListTree,
  Quote,
  X,
} from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'

import { buildVocabularyMarkdownOutline } from '@/features/vocabulary/vocabularyLearningMarkdown'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const sourceMode = ref(false)
const outlineOpen = ref(false)
const activeOutlineId = ref('')
const tooLong = computed(() => props.modelValue.length > 20_000)
const outline = computed(() => buildVocabularyMarkdownOutline(props.modelValue))

const editor = useEditor({
  extensions: [
    StarterKit,
    Markdown,
    Placeholder.configure({
      placeholder: '在这里补充例句、使用边界或个人笔记…',
    }),
    CharacterCount.configure({ limit: 20_000 }),
  ],
  content: props.modelValue,
  contentType: 'markdown',
  editorProps: {
    attributes: {
      'aria-label': '学习内容 Markdown 编辑器',
      spellcheck: 'true',
    },
  },
  onCreate: () => {
    void nextTick(syncOutlineAnchors)
  },
  onUpdate: ({ editor: currentEditor }) => {
    emit('update:modelValue', currentEditor.getMarkdown())
    void nextTick(syncOutlineAnchors)
  },
  onSelectionUpdate: ({ editor: currentEditor }) => {
    updateActiveOutline(currentEditor.state.selection.from)
  },
})

watch(
  () => props.modelValue,
  (value) => {
    if (sourceMode.value) return
    const currentEditor = editor.value
    if (!currentEditor || currentEditor.getMarkdown() === value) return
    currentEditor.commands.setContent(value, {
      contentType: 'markdown',
      emitUpdate: false,
    })
    void nextTick(syncOutlineAnchors)
  },
)

watch(outline, (items) => {
  if (items.some((item) => item.id === activeOutlineId.value)) return
  activeOutlineId.value = items[0]?.id ?? ''
})

function updateValue(event: Event) {
  const input = event.target as HTMLTextAreaElement
  emit('update:modelValue', input.value)
}

async function toggleSourceMode() {
  sourceMode.value = !sourceMode.value
  if (!sourceMode.value && editor.value) {
    editor.value.commands.setContent(props.modelValue, {
      contentType: 'markdown',
      emitUpdate: false,
    })
    await nextTick()
    syncOutlineAnchors()
    editor.value.commands.focus()
  }
}

function syncOutlineAnchors() {
  const headings = editor.value?.view.dom.querySelectorAll('h2') ?? []
  headings.forEach((heading, index) => {
    const item = outline.value[index]
    if (item) heading.id = item.id
  })
}

function updateActiveOutline(selectionPosition: number) {
  const currentEditor = editor.value
  if (!currentEditor) return

  let headingIndex = -1
  currentEditor.state.doc.descendants((node, position) => {
    if (position >= selectionPosition) return false
    if (node.type.name === 'heading' && node.attrs.level === 2) headingIndex += 1
    return true
  })
  activeOutlineId.value = outline.value[Math.max(0, headingIndex)]?.id ?? ''
}

function scrollToOutline(id: string) {
  if (sourceMode.value) {
    sourceMode.value = false
    void nextTick(() => scrollToOutline(id))
    return
  }
  const targetIndex = outline.value.findIndex((item) => item.id === id)
  if (targetIndex < 0) return
  activeOutlineId.value = id
  outlineOpen.value = false
  void nextTick(() => {
    const headings = editor.value?.view.dom.querySelectorAll<HTMLElement>('h2')
    headings?.[targetIndex]?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    })
  })
}
</script>

<style scoped>
.markdown-editor {
  min-width: 0;
  color: #172033;
}

.markdown-editor__toolbar {
  min-width: 0;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #e4ece8;
}

.markdown-editor__formatting,
.markdown-editor__meta,
.markdown-editor__bubble-menu {
  display: flex;
  align-items: center;
}

.markdown-editor__formatting {
  gap: 2px;
}

.markdown-editor__meta {
  gap: 12px;
  color: #718096;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.markdown-editor__formatting button,
.markdown-editor__bubble-menu button,
.markdown-editor__outline-trigger,
.markdown-editor__source-toggle,
.markdown-editor__outline-drawer header button {
  display: inline-grid;
  place-items: center;
  border: 0;
  background: transparent;
  color: #526174;
  cursor: pointer;
}

.markdown-editor__formatting button,
.markdown-editor__bubble-menu button,
.markdown-editor__outline-drawer header button {
  width: 34px;
  height: 34px;
  padding: 0;
}

.markdown-editor__formatting button:hover,
.markdown-editor__formatting button[aria-pressed="true"],
.markdown-editor__bubble-menu button:hover,
.markdown-editor__bubble-menu button[aria-pressed="true"] {
  background: #e9f8f1;
  color: #047857;
}

.markdown-editor__formatting svg,
.markdown-editor__bubble-menu svg,
.markdown-editor__outline-trigger svg,
.markdown-editor__source-toggle svg,
.markdown-editor__outline-drawer svg {
  width: 17px;
  height: 17px;
}

.markdown-editor__outline-trigger,
.markdown-editor__source-toggle {
  grid-auto-flow: column;
  gap: 6px;
  min-height: 34px;
  padding: 0 8px;
  font: inherit;
  font-weight: 700;
}

.markdown-editor__outline-trigger {
  display: none;
}

.markdown-editor__source-toggle:hover {
  color: #047857;
}

.markdown-editor__layout {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 820px) 200px;
  gap: 48px;
  justify-content: center;
  align-items: start;
}

.markdown-editor__canvas {
  min-width: 0;
  padding: 20px 0 80px;
}

.markdown-editor__surface {
  min-width: 0;
}

.markdown-editor__surface :deep(.tiptap) {
  min-height: clamp(520px, calc(100vh - 390px), 840px);
  outline: none;
  color: #2d3748;
  font-size: 16px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.markdown-editor__surface :deep(.tiptap > *:first-child) {
  margin-top: 0;
}

.markdown-editor__surface :deep(.tiptap h2) {
  scroll-margin-top: 96px;
  margin: 42px 0 18px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e6eeea;
  color: #172033;
  font-size: 24px;
  line-height: 1.3;
}

.markdown-editor__surface :deep(.tiptap h3) {
  margin: 28px 0 12px;
  color: #263449;
  font-size: 17px;
  line-height: 1.4;
}

.markdown-editor__surface :deep(.tiptap p) {
  margin: 10px 0;
}

.markdown-editor__surface :deep(.tiptap ul),
.markdown-editor__surface :deep(.tiptap ol) {
  margin: 10px 0 18px;
  padding-left: 24px;
}

.markdown-editor__surface :deep(.tiptap li::marker) {
  color: #059669;
  font-weight: 800;
}

.markdown-editor__surface :deep(.tiptap blockquote) {
  margin: 22px 0;
  border-left: 3px solid #34d399;
  padding-left: 18px;
  color: #526174;
}

.markdown-editor__surface :deep(.tiptap table) {
  width: 100%;
  margin: 24px 0;
  border-collapse: collapse;
  font-size: 14px;
}

.markdown-editor__surface :deep(.tiptap th),
.markdown-editor__surface :deep(.tiptap td) {
  border-bottom: 1px solid #dce7e1;
  padding: 11px 12px;
  text-align: left;
  vertical-align: top;
}

.markdown-editor__surface :deep(.tiptap th) {
  color: #526174;
  font-size: 12px;
  font-weight: 800;
}

.markdown-editor__surface :deep(.is-editor-empty:first-child::before) {
  float: left;
  height: 0;
  color: #94a3b8;
  content: attr(data-placeholder);
  pointer-events: none;
}

.markdown-editor__bubble-menu {
  gap: 2px;
  border: 1px solid #1f2937;
  background: #172033;
  padding: 4px;
  box-shadow: 0 12px 24px rgba(15, 23, 42, .18);
}

.markdown-editor__bubble-menu button {
  color: #f8fafc;
}

.markdown-editor__bubble-menu button:hover,
.markdown-editor__bubble-menu button[aria-pressed="true"] {
  background: #334155;
  color: #86efac;
}

.markdown-editor__outline {
  position: sticky;
  top: 24px;
  min-width: 0;
  padding-top: 24px;
}

.markdown-editor__outline > span {
  display: block;
  margin-bottom: 10px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 800;
  text-transform: uppercase;
}

.markdown-editor__outline nav,
.markdown-editor__outline-drawer nav {
  display: grid;
  gap: 3px;
}

.markdown-editor__outline nav button,
.markdown-editor__outline-drawer nav button {
  min-width: 0;
  border: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  overflow: hidden;
  padding: 7px 8px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.markdown-editor__outline nav button:hover,
.markdown-editor__outline nav button[aria-current="location"],
.markdown-editor__outline-drawer nav button:hover,
.markdown-editor__outline-drawer nav button[aria-current="location"] {
  background: #e9f8f1;
  color: #047857;
}

.markdown-editor__outline p,
.markdown-editor__outline-drawer p,
.markdown-editor__source p {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
}

.markdown-editor__source {
  display: grid;
  gap: 8px;
}

.markdown-editor__source label {
  color: #526174;
  font-size: 13px;
  font-weight: 800;
}

.markdown-editor__source textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: clamp(520px, calc(100vh - 390px), 840px);
  resize: vertical;
  border: 1px solid #dce7e1;
  border-radius: 4px;
  outline: none;
  background: #fbfdfc;
  color: #172033;
  padding: 18px 20px;
  font: 14px/1.75 ui-monospace, SFMono-Regular, Consolas, monospace;
  overflow-wrap: anywhere;
}

.markdown-editor__source textarea:focus {
  border-color: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, .12);
}

.markdown-editor__count--error,
.markdown-editor__error {
  color: #b91c1c !important;
}

.markdown-editor__outline-backdrop {
  display: none;
}

@media (max-width: 1023px) {
  .markdown-editor__layout {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .markdown-editor__outline {
    display: none;
  }

  .markdown-editor__outline-trigger {
    display: inline-grid;
  }

  .markdown-editor__outline-backdrop {
    position: fixed;
    z-index: 40;
    inset: 0;
    display: flex;
    justify-content: flex-end;
    background: rgba(15, 23, 42, .28);
  }

  .markdown-editor__outline-drawer {
    box-sizing: border-box;
    width: min(320px, 88vw);
    height: 100%;
    overflow: auto;
    background: #fff;
    padding: 20px;
    box-shadow: -16px 0 36px rgba(15, 23, 42, .14);
  }

  .markdown-editor__outline-drawer header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }
}

@media (max-width: 767px) {
  .markdown-editor__toolbar {
    align-items: flex-start;
    flex-direction: column-reverse;
    padding: 8px 0;
  }

  .markdown-editor__meta {
    width: 100%;
    flex-wrap: wrap;
  }

  .markdown-editor__meta > span {
    margin-left: auto;
  }

  .markdown-editor__canvas {
    padding-top: 12px;
  }

  .markdown-editor__surface :deep(.tiptap),
  .markdown-editor__source textarea {
    min-height: 420px;
  }

  .markdown-editor__surface :deep(.tiptap) {
    font-size: 15px;
    line-height: 1.7;
  }

  .markdown-editor__surface :deep(.tiptap h2) {
    margin-top: 34px;
    font-size: 21px;
  }
}
</style>
