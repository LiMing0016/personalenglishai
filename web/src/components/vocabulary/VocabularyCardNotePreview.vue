<template>
  <article class="vocabulary-note-preview" aria-label="当前单词沉淀笔记">
    <header class="vocabulary-note-preview__header">
      <div class="vocabulary-note-preview__identity">
        <span class="vocabulary-note-preview__eyebrow">My note</span>
        <h2>{{ card.displayTerm }}</h2>
        <div class="vocabulary-note-preview__metadata">
          <span :class="`vocabulary-note-preview__status vocabulary-note-preview__status--${card.status}`">
            {{ statusLabel(card.status) }}
          </span>
          <span>{{ card.theme?.name || '兼容卡片' }}<template v-if="card.themeVersion"> · v{{ card.themeVersion }}</template></span>
          <span v-if="card.updatedAt">更新于 {{ formatTime(card.updatedAt) }}</span>
        </div>
      </div>
      <button type="button" class="vocabulary-note-preview__open" @click="emit('open')">
        打开完整笔记
        <span aria-hidden="true">↗</span>
      </button>
    </header>

    <div v-if="hasReadableContent" class="vocabulary-note-preview__document">
      <section v-if="card.core" class="vocabulary-note-preview__section" aria-label="单词核心信息">
        <VocabularyCoreSummary :core="card.core" @pronounce="forwardPronunciation" />
      </section>

      <VocabularyCardBlocks
        v-if="card.cardBlocks"
        :card-blocks="card.cardBlocks"
      />
      <VocabularyMarkdownRenderer
        v-else-if="card.markdown"
        :markdown="card.markdown"
      />
    </div>
    <div v-else class="vocabulary-note-preview__pending" role="status">
      <strong>{{ pendingTitle }}</strong>
      <span>可以先查看词典释义，完整笔记入口仍然可用。</span>
    </div>

    <section v-if="card.sources.length" class="vocabulary-note-preview__sources" aria-label="笔记来源">
      <header>
        <span>Sources</span>
        <h3>来源与语境</h3>
      </header>
      <div class="vocabulary-note-preview__source-list">
        <article v-for="source in card.sources" :key="source.sourceUid">
          <strong>{{ source.sourceTitle || source.sourceType }}</strong>
          <p>{{ source.contextText || '未记录语境' }}</p>
          <a
            v-if="safeExternalUrl(source.sourceUrl)"
            :href="safeExternalUrl(source.sourceUrl)!"
            target="_blank"
            rel="noreferrer"
          >查看原始来源</a>
        </article>
      </div>
    </section>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type {
  VocabularyCardDetail,
  VocabularyCardStatus,
  VocabularyCoreContent,
} from '@/api/vocabulary'
import { safeExternalUrl } from '@/features/vocabulary/safeExternalUrl'
import VocabularyCardBlocks from './VocabularyCardBlocks.vue'
import VocabularyCoreSummary from './VocabularyCoreSummary.vue'
import VocabularyMarkdownRenderer from './VocabularyMarkdownRenderer.vue'

const props = defineProps<{ card: VocabularyCardDetail }>()
const emit = defineEmits<{
  open: []
  pronounce: [payload: { audioUrl?: string, text: string, language: string }]
}>()

const hasReadableContent = computed(() => Boolean(
  props.card.core || props.card.cardBlocks || props.card.markdown?.trim(),
))
const pendingTitle = computed(() => (
  props.card.status === 'failed' ? '笔记内容暂不可用' : '笔记内容正在生成'
))

function statusLabel(status: VocabularyCardStatus) {
  return ({
    captured: '已沉淀',
    generating: '生成中',
    ready: '当前版本',
    needs_review: '待确认',
    failed: '生成失败',
  } as const)[status]
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function forwardPronunciation(phonetic: VocabularyCoreContent['phonetics'][number]) {
  emit('pronounce', {
    ...(phonetic.audioUrl ? { audioUrl: phonetic.audioUrl } : {}),
    text: props.card.displayTerm,
    language: phonetic.region === 'us' ? 'en-US' : 'en-GB',
  })
}
</script>

<style scoped>
.vocabulary-note-preview {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #dce7e1;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.055);
}

.vocabulary-note-preview__header {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 26px 28px;
  border-bottom: 1px solid #dce7e1;
  background:
    radial-gradient(circle at 0 0, rgba(16, 185, 129, 0.12), transparent 34%),
    linear-gradient(135deg, #f7fcfa 0%, #ffffff 70%);
}

.vocabulary-note-preview__identity { min-width: 0; }
.vocabulary-note-preview__eyebrow,
.vocabulary-note-preview__sources header span {
  color: #059669;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.vocabulary-note-preview h2 {
  margin: 5px 0 10px;
  color: #0f172a;
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.05;
  overflow-wrap: anywhere;
}
.vocabulary-note-preview__metadata {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  color: #64748b;
  font-size: 13px;
}
.vocabulary-note-preview__status {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 850;
}
.vocabulary-note-preview__status--needs_review { background: #fffbeb; color: #b45309; }
.vocabulary-note-preview__status--failed { background: #fef2f2; color: #dc2626; }
.vocabulary-note-preview__open {
  flex: none;
  display: inline-flex;
  min-height: 42px;
  gap: 9px;
  align-items: center;
  padding: 0 15px;
  border: 1px solid #a7d7c2;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.82);
  color: #047857;
  font: inherit;
  font-weight: 850;
  cursor: pointer;
}
.vocabulary-note-preview__open:hover { border-color: #34d399; background: #ecfdf5; }
.vocabulary-note-preview__open:focus-visible { outline: 3px solid rgba(16, 185, 129, 0.26); outline-offset: 2px; }

.vocabulary-note-preview__document { padding: 28px; }
.vocabulary-note-preview__section { margin-bottom: 30px; padding-bottom: 26px; border-bottom: 1px solid #e5eee9; }
.vocabulary-note-preview__document :deep(.card-blocks__section + .card-blocks__section) { margin-top: 28px; }
.vocabulary-note-preview__pending {
  display: grid;
  min-height: 180px;
  place-content: center;
  gap: 8px;
  padding: 32px;
  text-align: center;
}
.vocabulary-note-preview__pending strong { color: #0f172a; font-size: 18px; }
.vocabulary-note-preview__pending span { color: #64748b; font-size: 13px; }

.vocabulary-note-preview__sources {
  padding: 24px 28px 28px;
  border-top: 1px solid #dce7e1;
  background: #fbfdfc;
}
.vocabulary-note-preview__sources h3 { margin: 4px 0 0; color: #0f172a; font-size: 18px; }
.vocabulary-note-preview__source-list { display: grid; gap: 10px; margin-top: 16px; }
.vocabulary-note-preview__source-list article {
  min-width: 0;
  display: grid;
  gap: 4px;
  padding: 13px 15px;
  border: 1px solid #e5eee9;
  border-radius: 9px;
  background: #ffffff;
}
.vocabulary-note-preview__source-list strong { color: #1e293b; font-size: 14px; }
.vocabulary-note-preview__source-list p { margin: 0; color: #64748b; line-height: 1.55; overflow-wrap: anywhere; }
.vocabulary-note-preview__source-list a { color: #047857; font-size: 13px; word-break: break-all; }

@media (max-width: 720px) {
  .vocabulary-note-preview__header { align-items: stretch; flex-direction: column; padding: 20px; }
  .vocabulary-note-preview__open { justify-content: center; }
  .vocabulary-note-preview__document,
  .vocabulary-note-preview__sources { padding: 20px; }
}
</style>
