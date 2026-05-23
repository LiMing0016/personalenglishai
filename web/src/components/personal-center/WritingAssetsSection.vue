<template>
  <div class="assets-section">
    <div class="assets-heading">
      <div>
        <h2 class="section-title">作文资产</h2>
        <p>集中查看已归档作文，继续修改、复盘和沉淀高分表达。</p>
      </div>
      <span>{{ total }} 篇</span>
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>
    <div v-else-if="items.length === 0" class="empty-state">暂无归档作文</div>

    <div v-else class="asset-list">
      <article v-for="item in items" :key="item.docId" class="asset-card">
        <div class="asset-card-main">
          <div class="asset-tags">
            <span>{{ item.taskPrompt ? '考试' : '自由' }}</span>
            <span class="asset-archived">已归档</span>
          </div>
          <h3>{{ item.title || '未命名作文' }}</h3>
          <p>{{ promptSummary(item) }}</p>
          <div class="asset-metrics">
            <span><em>最新分数</em><strong>{{ item.latestScore != null ? `${item.latestScore} 分` : '未评分' }}</strong></span>
            <span><em>评分次数</em><strong>{{ item.submitCount || 0 }}</strong></span>
            <span><em>更新时间</em><strong>{{ formatDate(item.updatedAt) }}</strong></span>
          </div>
        </div>
        <div class="asset-actions">
          <button type="button" class="asset-primary" @click="openEditor(item)">编辑</button>
          <button type="button" class="asset-secondary" @click="restoreAsset(item)">取消归档</button>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getWritingDocuments, type WritingDocumentItem } from '@/api/writing'
import { unarchiveDocument } from '@/api/document'
import { showToast } from '@/utils/toast'

const router = useRouter()
const loading = ref(true)
const items = ref<WritingDocumentItem[]>([])
const total = ref(0)

async function loadAssets() {
  loading.value = true
  try {
    const res = await getWritingDocuments(0, 50, { archived: true })
    items.value = res.items ?? []
    total.value = res.total ?? items.value.length
  } catch (e) {
    console.warn('[WritingAssetsSection] load assets failed', e)
    showToast('作文资产加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function openEditor(item: WritingDocumentItem) {
  sessionStorage.setItem('peai:writing:docId', item.docId)
  void router.push('/app/writing/editor')
}

async function restoreAsset(item: WritingDocumentItem) {
  try {
    await unarchiveDocument(item.docId)
    items.value = items.value.filter((doc) => doc.docId !== item.docId)
    total.value = Math.max(0, total.value - 1)
    showToast('已取消归档', 'success')
  } catch (e) {
    console.warn('[WritingAssetsSection] unarchive failed', e)
    showToast('取消归档失败，请稍后重试', 'error')
  }
}

function promptSummary(item: WritingDocumentItem) {
  return item.taskPrompt?.replace(/\s+/g, ' ').trim() || '自由写作，无固定题目'
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return dateStr
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(loadAssets)
</script>

<style scoped>
.assets-section {
  max-width: 900px;
}

.assets-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.section-title {
  margin: 0 0 8px;
  color: #1e293b;
  font-size: 22px;
  font-weight: 700;
}

.assets-heading p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.assets-heading > span {
  flex: 0 0 auto;
  padding: 5px 12px;
  border-radius: 999px;
  color: #0f766e;
  background: #e8f6ef;
  font-size: 13px;
  font-weight: 700;
}

.loading-state,
.empty-state {
  padding: 40px 0;
  color: #94a3b8;
  font-size: 14px;
  text-align: center;
}

.asset-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.asset-card {
  display: flex;
  gap: 18px;
  justify-content: space-between;
  padding: 18px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.asset-card-main {
  min-width: 0;
}

.asset-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.asset-tags span {
  padding: 3px 10px;
  border-radius: 999px;
  color: #64748b;
  background: #f1f5f9;
  font-size: 12px;
  font-weight: 700;
}

.asset-tags .asset-archived {
  color: #0f766e;
  background: #e8f6ef;
}

.asset-card h3 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 16px;
}

.asset-card p {
  display: -webkit-box;
  max-width: 680px;
  margin: 0 0 14px;
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.asset-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.asset-metrics span {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 96px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.asset-metrics em {
  color: #94a3b8;
  font-size: 11px;
  font-style: normal;
}

.asset-metrics strong {
  color: #1e293b;
  font-size: 12px;
}

.asset-actions {
  display: flex;
  flex: 0 0 96px;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
}

.asset-primary,
.asset-secondary {
  min-height: 34px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.asset-primary {
  color: #fff;
  background: #0f766e;
}

.asset-secondary {
  color: #0f766e;
  background: #e8f6ef;
}

@media (max-width: 760px) {
  .asset-card {
    flex-direction: column;
  }

  .asset-actions {
    flex: none;
    flex-direction: row;
  }
}
</style>
