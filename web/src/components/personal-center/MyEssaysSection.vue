<template>
  <div class="essays-section">
    <div class="section-heading">
      <div>
        <p class="section-eyebrow">学习记录</p>
        <h2 class="section-title">写作记录</h2>
      </div>
      <RouterLink class="start-writing-link" to="/app/writing">开始新写作</RouterLink>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">加载中...</div>

    <!-- Empty -->
    <div v-else-if="items.length === 0" class="empty-state">
      <h3>还没有写作记录</h3>
      <p>完成一次写作与评测后，分数、反馈和能力变化会沉淀在这里。</p>
      <RouterLink to="/app/writing">开始第一次写作</RouterLink>
    </div>

    <!-- Essay Cards -->
    <div v-else class="essay-list">
      <div class="essay-card" v-for="item in items" :key="item.id">
        <div class="essay-header">
          <span class="badge badge-mode">{{ item.mode === 'exam' ? '考试' : '自由' }}</span>
          <span class="essay-time">{{ formatTime(item.created_at) }}</span>
          <button class="star-btn" @click.stop="toggleFavorite(item)" :title="item.favorited ? '取消收藏' : '收藏'">
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
              <path
                d="M9 1l2.47 5.01L17 6.76l-4 3.9.94 5.5L9 13.77l-4.94 2.4.94-5.5-4-3.9 5.53-.75L9 1z"
                :stroke="item.favorited ? '#eab308' : '#94a3b8'"
                :fill="item.favorited ? '#eab308' : 'none'"
                stroke-width="1.3"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </div>

        <div class="essay-preview" @click="openDetail(item)">
          {{ item.essay_preview }}
        </div>

        <div class="essay-pills">
          <span class="pill pill-score" v-if="item.overall_score != null">
            {{ item.overall_score }}分
          </span>
          <span class="pill pill-gaokao" v-if="item.gaokao_score != null">
            高考 {{ item.gaokao_score }}/{{ item.max_score ?? 25 }}
          </span>
          <span class="pill pill-band" v-if="item.band">
            {{ item.band }}
          </span>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div class="pagination" v-if="total > pageSize">
      <button class="page-btn" :disabled="page === 0" @click="goPage(page - 1)">上一页</button>
      <span class="page-info">{{ page + 1 }} / {{ totalPages }}</span>
      <button class="page-btn" :disabled="page >= totalPages - 1" @click="goPage(page + 1)">下一页</button>
    </div>

    <!-- Detail Modal -->
    <Teleport to="body">
      <div class="modal-overlay" v-if="detailVisible" @click.self="detailVisible = false">
        <div class="modal-content">
          <div class="modal-header">
            <h3>作文详情</h3>
            <button class="modal-close" @click="detailVisible = false">&times;</button>
          </div>
          <div class="modal-body" v-if="detailLoading">加载中...</div>
          <div class="modal-body" v-else-if="detail">
            <div class="detail-essay-text">{{ detail.essayText }}</div>
            <div class="detail-summary" v-if="detail.result?.summary">
              <h4>评价摘要</h4>
              <p>{{ detail.result.summary }}</p>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getEvaluationHistory,
  getEvaluationDetail,
  toggleEssayFavorite,
  type EvaluationHistoryItem,
  type EvaluationDetailResponse,
} from '@/api/writing'

const loading = ref(true)
const items = ref<EvaluationHistoryItem[]>([])
const total = ref(0)
const page = ref(0)
const pageSize = 10

const totalPages = computed(() => Math.ceil(total.value / pageSize))

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<EvaluationDetailResponse | null>(null)

function formatTime(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function loadPage(p: number) {
  loading.value = true
  try {
    const res = await getEvaluationHistory(p, pageSize)
    items.value = res.items ?? []
    total.value = res.total ?? 0
    page.value = p
  } catch {
    // silent
  } finally {
    loading.value = false
  }
}

function goPage(p: number) {
  if (p < 0 || p >= totalPages.value) return
  loadPage(p)
}

async function toggleFavorite(item: EvaluationHistoryItem) {
  try {
    const res = await toggleEssayFavorite(item.id)
    item.favorited = res.favorited
  } catch {
    // silent
  }
}

async function openDetail(item: EvaluationHistoryItem) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getEvaluationDetail(item.id)
  } catch {
    // silent
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadPage(0)
})
</script>

<style scoped>
.essays-section {
  max-width: 980px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 24px;
}

.section-eyebrow {
  margin: 0 0 5px;
  color: #7a8da2;
  font-size: 11px;
  font-weight: 760;
  letter-spacing: 0.12em;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.start-writing-link,
.empty-state a {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  padding: 0 14px;
  background: #087a59;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.start-writing-link:focus-visible,
.empty-state a:focus-visible {
  outline: 3px solid rgba(4, 120, 87, 0.22);
  outline-offset: 3px;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 54px 24px;
  color: #94a3b8;
  font-size: 14px;
  background: #fff;
  border: 1px dashed #cfdce6;
  border-radius: 18px;
}

.empty-state h3 {
  margin: 0;
  color: #1c334c;
  font-size: 18px;
}

.empty-state p {
  max-width: 520px;
  margin: 10px auto 20px;
  color: #73869b;
  line-height: 1.65;
}

.essay-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.essay-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 18px 22px;
  transition: box-shadow 0.15s;
}
.essay-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.essay-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.badge-mode {
  background: #f1f5f9;
  color: #64748b;
}

.essay-time {
  font-size: 12px;
  color: #94a3b8;
  flex: 1;
}

.star-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px;
  display: flex;
  align-items: center;
  transition: transform 0.15s;
}
.star-btn:hover {
  transform: scale(1.15);
}

.essay-preview {
  font-size: 14px;
  color: #334155;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
  cursor: pointer;
}
.essay-preview:hover {
  color: #047857;
}

.essay-pills {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pill {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.pill-score {
  background: #ecfdf5;
  color: #047857;
}

.pill-gaokao {
  background: #fef3c7;
  color: #92400e;
}

.pill-band {
  background: #eff6ff;
  color: #2563eb;
}

/* Pagination */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}

.page-btn {
  padding: 8px 18px;
  font-size: 13px;
  color: #047857;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.page-btn:hover:not(:disabled) {
  background: #ecfdf5;
}
.page-btn:disabled {
  color: #cbd5e1;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: #64748b;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  border-radius: 18px;
  width: 90%;
  max-width: 680px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}
.modal-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #1e293b;
}

.modal-close {
  background: none;
  border: none;
  font-size: 22px;
  color: #94a3b8;
  cursor: pointer;
  padding: 0 4px;
}
.modal-close:hover {
  color: #334155;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
}

.detail-essay-text {
  font-size: 15px;
  line-height: 1.8;
  color: #334155;
  white-space: pre-wrap;
  margin-bottom: 24px;
}

.detail-summary h4 {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 8px;
}

.detail-summary p {
  font-size: 14px;
  color: #475569;
  line-height: 1.7;
  margin: 0;
}

@media (max-width: 640px) {
  .modal-content {
    width: 95%;
    max-height: 90vh;
  }
}
</style>
