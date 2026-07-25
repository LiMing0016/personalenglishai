<template>
  <section class="vocabulary-card-list" aria-label="沉淀单词卡列表">
    <div class="card-list-filters">
      <input v-model="keyword" type="search" placeholder="搜索单词" aria-label="搜索单词">
      <select v-model="status" aria-label="按状态筛选">
        <option value="">全部状态</option>
        <option value="generating">正在生成</option>
        <option value="ready">已就绪</option>
        <option value="needs_review">待确认</option>
        <option value="failed">生成失败</option>
      </select>
      <select v-model="sourceType" aria-label="按来源筛选">
        <option value="">全部来源</option>
        <option value="manual">手动录入</option>
        <option value="dictionary">词典收藏</option>
      </select>
      <select v-model="sort" aria-label="排序方式">
        <option value="recent">最近沉淀</option>
        <option value="az">A-Z</option>
      </select>
    </div>

    <div v-if="loading" class="card-list-state">正在加载沉淀单词...</div>
    <div v-else-if="error" class="card-list-state card-list-state--error">{{ error }}</div>
    <div v-else-if="!items.length" class="card-list-state">暂无沉淀单词，先录入一组需要学习的单词。</div>
    <div v-else class="card-list-rows" role="list">
      <button
        v-for="card in items"
        :key="card.cardUid"
        type="button"
        class="card-row"
        :class="{ selected: card.cardUid === selectedCardUid }"
        role="listitem"
        @click="emit('select', card.cardUid)"
      >
        <span class="card-term">
          <strong>{{ card.displayTerm }}</strong>
          <small>{{ card.phonetic || card.templateKey }}</small>
          <span>{{ card.coreDefinition || '释义生成中' }}</span>
        </span>
        <span class="card-source">
          <strong>{{ card.sourceTypes.join(' / ') || '未标注来源' }}</strong>
          <small>{{ card.sourceCount }} 条来源 · {{ formatUpdatedAt(card.updatedAt) }}</small>
        </span>
        <span class="card-status" :class="`card-status--${effectiveStatus(card)}`">{{ statusLabel(effectiveStatus(card)) }}</span>
      </button>
    </div>

    <footer class="card-list-pagination">
      <span>共 {{ total }} 张单词卡</span>
      <div>
        <button type="button" :disabled="page <= 1 || loading" aria-label="上一页" @click="setPage(page - 1)">‹</button>
        <strong>{{ page }}</strong>
        <button type="button" :disabled="page >= pageCount || loading" aria-label="下一页" @click="setPage(page + 1)">›</button>
      </div>
      <span>{{ size }} 条/页</span>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

import type { VocabularyCardFilters, VocabularyCardStatus, VocabularyCardSummary } from '@/api/vocabulary'

const props = defineProps<{
  filters: VocabularyCardFilters
  items: VocabularyCardSummary[]
  total: number
  page: number
  size: number
  loading: boolean
  error?: string
  selectedCardUid: string | null
}>()

const emit = defineEmits<{
  select: [cardUid: string]
  'update:filters': [filters: VocabularyCardFilters]
}>()

const keyword = ref(props.filters.keyword ?? '')
const status = ref(props.filters.status ?? '')
const sourceType = ref(props.filters.sourceType ?? '')
const sort = ref<'recent' | 'az'>(props.filters.sort ?? 'recent')
const pageCount = ref(Math.max(1, Math.ceil(props.total / props.size)))

watch(() => props.filters, (filters) => {
  keyword.value = filters.keyword ?? ''
  status.value = filters.status ?? ''
  sourceType.value = filters.sourceType ?? ''
  sort.value = filters.sort ?? 'recent'
}, { deep: true })
watch(() => [props.total, props.size], () => {
  pageCount.value = Math.max(1, Math.ceil(props.total / props.size))
})
watch([keyword, status, sourceType, sort], () => emitFilters(1))

function emitFilters(page: number) {
  emit('update:filters', {
    keyword: keyword.value.trim() || undefined,
    status: (status.value || undefined) as VocabularyCardStatus | undefined,
    sourceType: sourceType.value || undefined,
    sort: sort.value,
    page,
    size: props.size,
  })
}

function effectiveStatus(card: VocabularyCardSummary): VocabularyCardStatus {
  return card.generationStatus === 'pending' || card.generationStatus === 'running'
    ? 'generating'
    : card.status
}

function formatUpdatedAt(value: string | null) {
  if (!value) return '时间未知'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN')
}

function setPage(nextPage: number) {
  emitFilters(nextPage)
}

function statusLabel(value: VocabularyCardStatus) {
  const labels: Record<VocabularyCardStatus, string> = {
    captured: '已收下',
    generating: '正在生成',
    ready: '已就绪',
    needs_review: '待确认',
    failed: '生成失败',
  }
  return labels[value]
}
</script>

<style scoped>
.vocabulary-card-list { overflow: hidden; border-top: 1px solid #edf2f7; }
.card-list-filters { display: grid; grid-template-columns: minmax(180px, 1fr) 120px 120px 112px; gap: 10px; padding: 14px 0; }
.card-list-filters input, .card-list-filters select { min-width: 0; height: 36px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #334155; font: inherit; font-size: 13px; padding: 0 10px; }
.card-list-state { display: grid; min-height: 180px; place-items: center; border-top: 1px solid #edf2f7; color: #64748b; text-align: center; }.card-list-state--error { color: #b91c1c; }
.card-list-rows { border-top: 1px solid #edf2f7; }.card-row { display: grid; grid-template-columns: minmax(180px, 1.35fr) minmax(150px, 1fr) 92px; width: 100%; min-height: 78px; align-items: center; gap: 14px; border: 0; border-bottom: 1px solid #edf2f7; background: transparent; color: #334155; font: inherit; padding: 9px 12px; text-align: left; cursor: pointer; }.card-row:hover, .card-row.selected { background: #f0fdf4; }.card-term, .card-source { display: grid; min-width: 0; gap: 3px; }.card-term strong, .card-term small, .card-term span, .card-source strong, .card-source small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.card-term strong { color: #0f172a; }.card-term small, .card-term span, .card-source { color: #64748b; font-size: 12px; }.card-source strong { color: #475569; font-size: 12px; }.card-source small { color: #94a3b8; }.card-status { justify-self: start; border-radius: 999px; font-size: 12px; font-weight: 800; padding: 5px 8px; }.card-status--generating { background: #dbeafe; color: #2563eb; }.card-status--ready { background: #dcfce7; color: #047857; }.card-status--needs_review { background: #fef3c7; color: #b45309; }.card-status--failed { background: #fee2e2; color: #b91c1c; }.card-status--captured { background: #e2e8f0; color: #475569; }
.card-list-pagination { display: flex; min-height: 58px; align-items: center; justify-content: space-between; gap: 12px; color: #64748b; font-size: 13px; }.card-list-pagination div { display: flex; align-items: center; gap: 8px; }.card-list-pagination button, .card-list-pagination strong { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 6px; }.card-list-pagination button { border: 1px solid #dce7e1; background: #fff; cursor: pointer; }.card-list-pagination button:disabled { cursor: not-allowed; opacity: .45; }.card-list-pagination strong { background: #059669; color: #fff; }
@media (max-width: 620px) { .card-list-filters { grid-template-columns: 1fr; }.card-row { grid-template-columns: minmax(0, 1fr) auto; padding: 10px 0; }.card-source { grid-column: 1 / -1; }.card-list-pagination > span:last-child { display: none; } }
</style>
