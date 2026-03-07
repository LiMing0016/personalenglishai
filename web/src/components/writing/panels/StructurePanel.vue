<template>
  <div class="structure-panel">
    <div v-if="paragraphs.length === 0" class="empty-state">
      <p>尚无内容，开始写作后将自动分析段落结构。</p>
    </div>
    <div v-else class="structure-list">
      <div class="structure-summary">
        <span class="summary-item">{{ paragraphs.length }} 段</span>
        <span class="summary-sep">·</span>
        <span class="summary-item">{{ totalSentences }} 句</span>
        <span class="summary-sep">·</span>
        <span class="summary-item">{{ totalWords }} 词</span>
        <span class="summary-sep">·</span>
        <span class="summary-item">均句长 {{ avgSentenceLength }}</span>
      </div>
      <div
        v-for="(p, idx) in paragraphs"
        :key="idx"
        class="para-card"
        @click="$emit('paragraph-click', p.startOffset)"
      >
        <div class="para-header">
          <span class="para-role" :class="'role-' + p.role">{{ roleLabel(p.role) }}</span>
          <span class="para-meta">{{ p.wordCount }} 词 · {{ p.sentenceCount }} 句</span>
        </div>
        <p class="para-preview">{{ p.preview }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  essay: string
}>()

defineEmits<{
  'paragraph-click': [offset: number]
}>()

interface ParagraphInfo {
  text: string
  preview: string
  role: 'intro' | 'body' | 'conclusion'
  wordCount: number
  sentenceCount: number
  startOffset: number
}

function countSentences(text: string): number {
  const matches = text.match(/[^.!?]*[.!?]+/g)
  return matches ? matches.length : (text.trim() ? 1 : 0)
}

function countWords(text: string): number {
  const t = text.trim()
  if (!t) return 0
  return t.split(/\s+/).filter(Boolean).length
}

const paragraphs = computed<ParagraphInfo[]>(() => {
  const text = props.essay.trim()
  if (!text) return []

  const rawParas = text.split(/\n+/).filter((p) => p.trim().length > 0)
  if (rawParas.length === 0) return []

  let offset = 0
  return rawParas.map((p, idx) => {
    const startOffset = props.essay.indexOf(p, offset)
    offset = startOffset + p.length

    let role: ParagraphInfo['role'] = 'body'
    if (rawParas.length === 1) {
      role = 'body'
    } else if (idx === 0) {
      role = 'intro'
    } else if (idx === rawParas.length - 1) {
      role = 'conclusion'
    }

    return {
      text: p,
      preview: p.length > 80 ? p.slice(0, 80) + '...' : p,
      role,
      wordCount: countWords(p),
      sentenceCount: countSentences(p),
      startOffset,
    }
  })
})

const totalSentences = computed(() => paragraphs.value.reduce((s, p) => s + p.sentenceCount, 0))
const totalWords = computed(() => paragraphs.value.reduce((s, p) => s + p.wordCount, 0))
const avgSentenceLength = computed(() => {
  if (totalSentences.value === 0) return '0'
  return (totalWords.value / totalSentences.value).toFixed(1)
})

function roleLabel(role: ParagraphInfo['role']): string {
  const map: Record<string, string> = {
    intro: 'Introduction',
    body: 'Body',
    conclusion: 'Conclusion',
  }
  return map[role] ?? role
}
</script>

<style scoped>
.structure-panel {
  padding: 16px;
}
.empty-state {
  text-align: center;
  color: #9ca3af;
  padding: 40px 16px;
  font-size: 14px;
}
.structure-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #f9fafb;
  border-radius: 8px;
}
.summary-sep {
  color: #d1d5db;
}
.structure-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.para-card {
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.para-card:hover {
  background: #f0fdf4;
  border-color: #a7f3d0;
}
.para-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.para-role {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.role-intro {
  color: #0369a1;
  background: #e0f2fe;
}
.role-body {
  color: #6b7280;
  background: #f3f4f6;
}
.role-conclusion {
  color: #7c3aed;
  background: #ede9fe;
}
.para-meta {
  font-size: 12px;
  color: #9ca3af;
}
.para-preview {
  font-size: 13px;
  color: #374151;
  line-height: 1.5;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
