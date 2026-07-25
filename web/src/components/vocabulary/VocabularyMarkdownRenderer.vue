<template>
  <article
    v-if="props.markdown.trim()"
    class="vocabulary-markdown"
    v-html="document.html"
    @click="copyMarkdownCodeFromClick"
  ></article>
  <p v-else class="vocabulary-markdown__empty">暂无主题内容</p>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'

import {
  copyMarkdownCodeFromClick,
  renderMarkdownDocument,
  type MarkdownSection,
} from '../assistant/markdown'

const props = defineProps<{ markdown: string }>()
const emit = defineEmits<{
  'sections-change': [sections: MarkdownSection[]]
}>()

const document = computed(() => renderMarkdownDocument(props.markdown, {
  allowImages: false,
  allowHtmlBreaks: false,
  headingAnchors: true,
}))

watch(
  () => document.value.sections,
  (sections) => emit('sections-change', sections),
  { immediate: true },
)
</script>

<style scoped>
.vocabulary-markdown {
  min-width: 0;
  color: #334155;
  font-size: 15px;
  line-height: 1.75;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.vocabulary-markdown :deep(h1),
.vocabulary-markdown :deep(h2),
.vocabulary-markdown :deep(h3),
.vocabulary-markdown :deep(h4),
.vocabulary-markdown :deep(h5),
.vocabulary-markdown :deep(h6) {
  color: #0f172a;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.vocabulary-markdown :deep(h1) { margin: 0 0 18px; font-size: 25px; }
.vocabulary-markdown :deep(h2) { margin: 28px 0 12px; padding-bottom: 7px; border-bottom: 1px solid #dce7e1; font-size: 21px; }
.vocabulary-markdown :deep(h3) { margin: 22px 0 10px; font-size: 18px; }
.vocabulary-markdown :deep(h4),
.vocabulary-markdown :deep(h5),
.vocabulary-markdown :deep(h6) { margin: 18px 0 8px; font-size: 15px; }
.vocabulary-markdown :deep(h1:first-child),
.vocabulary-markdown :deep(h2:first-child),
.vocabulary-markdown :deep(h3:first-child) { margin-top: 0; }

.vocabulary-markdown :deep(p) { margin: 0 0 14px; }
.vocabulary-markdown :deep(strong) { color: #0f172a; }
.vocabulary-markdown :deep(ul),
.vocabulary-markdown :deep(ol) { margin: 0 0 16px; padding-left: 24px; }
.vocabulary-markdown :deep(li) { margin: 5px 0; padding-left: 2px; }
.vocabulary-markdown :deep(li::marker) { color: #059669; font-weight: 700; }

.vocabulary-markdown :deep(blockquote) {
  margin: 16px 0;
  padding: 10px 14px;
  border-left: 3px solid #34d399;
  background: #f0fdf4;
  color: #475569;
}
.vocabulary-markdown :deep(blockquote p:last-child) { margin-bottom: 0; }

.vocabulary-markdown :deep(.markdown-table-scroll) {
  width: 100%;
  margin: 18px 0;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
}
.vocabulary-markdown :deep(table) { width: 100%; min-width: 480px; border-collapse: collapse; }
.vocabulary-markdown :deep(th),
.vocabulary-markdown :deep(td) { padding: 9px 11px; border: 1px solid #dce7e1; text-align: left; vertical-align: top; }
.vocabulary-markdown :deep(th) { background: #f0fdf4; color: #065f46; font-size: 13px; }

.vocabulary-markdown :deep(code) {
  border-radius: 4px;
  background: #ecfdf5;
  color: #065f46;
  font: 0.9em/1.5 ui-monospace, SFMono-Regular, Consolas, monospace;
  padding: 1px 4px;
}
.vocabulary-markdown :deep(.markdown-code-block) {
  max-width: 100%;
  margin: 18px 0;
  border: 1px solid #dce7e1;
  border-radius: 6px;
  overflow: hidden;
  background: #f8fafc;
}
.vocabulary-markdown :deep(.markdown-code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 10px;
  border-bottom: 1px solid #dce7e1;
  background: #f0fdf4;
  color: #047857;
  font: 12px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace;
}
.vocabulary-markdown :deep(.markdown-code-copy) {
  flex: none;
  border: 0;
  background: transparent;
  color: #047857;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}
.vocabulary-markdown :deep(pre) { max-width: 100%; margin: 0; padding: 12px 14px; overflow-x: auto; }
.vocabulary-markdown :deep(pre code) { display: block; min-width: max-content; padding: 0; background: transparent; color: #1e293b; }
.vocabulary-markdown :deep(hr) { margin: 24px 0; border: 0; border-top: 1px solid #dce7e1; }

.vocabulary-markdown__empty {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

@media (max-width: 620px) {
  .vocabulary-markdown { font-size: 14px; }
  .vocabulary-markdown :deep(h1) { font-size: 22px; }
  .vocabulary-markdown :deep(h2) { margin-top: 24px; font-size: 19px; }
  .vocabulary-markdown :deep(table) { min-width: 420px; }
  .vocabulary-markdown :deep(th),
  .vocabulary-markdown :deep(td) { padding: 8px; }
}
</style>
