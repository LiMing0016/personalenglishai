<template>
  <div class="archive-panel">
    <!-- 加载中 -->
    <div v-if="loading" class="state-center">
      <span class="loading-dot">正在加载历史记录…</span>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!items.length" class="state-center">
      <p class="empty-text">还没有评分记录。</p>
      <p class="empty-sub">提交作文后，每次评分结果会自动保存在这里。</p>
    </div>

    <!-- 历史列表 -->
    <template v-else>
      <ul class="history-list">
        <li
          v-for="item in items"
          :key="item.id"
          class="history-item"
          :class="{ 'is-active': activeId === item.id }"
          @click="toggle(item)"
        >
          <div class="item-header">
            <div class="item-meta">
              <span class="item-date">{{ formatDate(item.created_at) }}</span>
              <span class="mode-tag">{{ item.mode === 'exam' ? '考试' : '自由' }}</span>
            </div>
            <div class="item-score">
              <span v-if="item.gaokao_score != null" class="score-num">
                {{ item.gaokao_score }}<span class="score-max">/{{ item.max_score }}</span>
              </span>
              <span v-if="item.band" class="band-chip">{{ item.band }}</span>
            </div>
          </div>
          <p class="item-preview">{{ item.essay_preview }}</p>

          <!-- 展开详情 -->
          <div v-if="activeId === item.id" class="item-detail">
            <div v-if="detailLoading" class="detail-loading">加载详情中…</div>
            <template v-else-if="activeDetail">
              <button
                class="btn-load-score"
                @click.stop="$emit('load-result', activeDetail)"
              >
                在评分面板中查看完整报告 →
              </button>
              <!-- 原文预览（折叠式） -->
              <div v-if="activeDetail.essayText" class="essay-preview-section">
                <div
                  class="essay-preview-text"
                  :class="{ 'is-collapsed': !essayExpanded }"
                >{{ activeDetail.essayText }}</div>
                <button
                  v-if="activeDetail.essayText.length > 150"
                  class="btn-expand-essay"
                  @click.stop="essayExpanded = !essayExpanded"
                >{{ essayExpanded ? '收起原文' : '展开全文' }}</button>
              </div>
              <p v-if="activeDetail.result?.summary" class="detail-summary">
                {{ activeDetail.result.summary }}
              </p>
              <div v-if="activeDetail.result?.grades" class="detail-grades">
                <span
                  v-for="(grade, key) in activeDetail.result.grades"
                  :key="key"
                  class="grade-chip"
                >{{ key.replace('_', ' ') }}：{{ grade }}</span>
              </div>
            </template>
          </div>
        </li>
      </ul>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="pagination">
        <button :disabled="page === 0" class="page-btn" @click="loadPage(page - 1)">‹ 上一页</button>
        <span class="page-info">第 {{ page + 1 }} 页 / 共 {{ totalPages }} 页</span>
        <button :disabled="page >= totalPages - 1" class="page-btn" @click="loadPage(page + 1)">下一页 ›</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { EvaluationHistoryItem, EvaluationDetailResponse } from '@/api/writing'
import { useEvaluationHistory, useEvaluationDetail } from '@/composables/useEvaluationHistory'

defineEmits<{
  'load-result': [detail: EvaluationDetailResponse]
}>()

const pageSize = 10
const { page, totalPages, items, total, isLoading: loading, setPage } = useEvaluationHistory(pageSize)

const activeId = ref<number | null>(null)
const essayExpanded = ref(false)

const { data: activeDetail, isLoading: detailLoading } = useEvaluationDetail(() => activeId.value)

function loadPage(p: number) {
  activeId.value = null
  essayExpanded.value = false
  setPage(p)
}

function toggle(item: EvaluationHistoryItem) {
  if (activeId.value === item.id) {
    activeId.value = null
    essayExpanded.value = false
    return
  }
  activeId.value = item.id
  essayExpanded.value = false
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd} ${hh}:${min}`
}
</script>

<style scoped>
.archive-panel {
  padding: 12px 16px;
}

.state-center {
  padding: 40px 0;
  text-align: center;
}

.loading-dot,
.empty-text {
  font-size: 14px;
  color: #6b7280;
}

.empty-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #9ca3af;
}

.history-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-item {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.history-item:hover {
  border-color: #a7f3d0;
  background: #f0fdf4;
}

.history-item.is-active {
  border-color: #047857;
  background: #f0fdf4;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-date {
  font-size: 12px;
  color: #6b7280;
}

.mode-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #e5e7eb;
  color: #374151;
}

.item-score {
  display: flex;
  align-items: center;
  gap: 6px;
}

.score-num {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.score-max {
  font-size: 12px;
  color: #6b7280;
  font-weight: 400;
}

.band-chip {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 20px;
  background: #d1fae5;
  color: #065f46;
  font-weight: 600;
}

.item-preview {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-detail {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
}

.detail-loading {
  font-size: 12px;
  color: #9ca3af;
}

.btn-load-score {
  display: block;
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #047857;
  background: #fff;
  border: 1px solid #047857;
  border-radius: 8px;
  cursor: pointer;
  text-align: center;
  margin-bottom: 10px;
}

.btn-load-score:hover {
  background: #f0fdf4;
}

.essay-preview-section {
  margin-bottom: 10px;
}

.essay-preview-text {
  font-size: 12px;
  color: #374151;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.essay-preview-text.is-collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.btn-expand-essay {
  margin-top: 4px;
  padding: 0;
  font-size: 12px;
  color: #047857;
  background: none;
  border: none;
  cursor: pointer;
}

.btn-expand-essay:hover {
  text-decoration: underline;
}

.detail-summary {
  margin: 0 0 10px;
  font-size: 12px;
  color: #374151;
  line-height: 1.6;
}

.detail-grades {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.grade-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: #f3f4f6;
  color: #374151;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
}

.page-btn {
  padding: 6px 12px;
  font-size: 13px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  color: #374151;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-btn:not(:disabled):hover {
  border-color: #047857;
  color: #047857;
}

.page-info {
  font-size: 12px;
  color: #6b7280;
}
</style>
