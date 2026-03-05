<template>
  <div class="overview-section">
    <h2 class="section-title">综合能力</h2>

    <!-- Stats Cards -->
    <div class="stats-grid" v-if="!loading">
      <div class="stat-card">
        <div class="stat-value">{{ stats?.totalEssays ?? 0 }}</div>
        <div class="stat-label">累计作文</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats?.averageScore != null ? stats.averageScore.toFixed(1) : '--' }}</div>
        <div class="stat-label">平均分</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats?.bestScore ?? '--' }}</div>
        <div class="stat-label">最高分</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats?.studyDays ?? 0 }}</div>
        <div class="stat-label">学习天数</div>
      </div>
    </div>

    <!-- Skeleton loading -->
    <div class="stats-grid" v-else>
      <div class="stat-card skeleton-card" v-for="i in 4" :key="i">
        <div class="skeleton-line skeleton-value"></div>
        <div class="skeleton-line skeleton-label"></div>
      </div>
    </div>

    <div class="member-since" v-if="stats?.memberSince">
      加入已 {{ stats.memberSince }}
    </div>

    <!-- Recent Activity -->
    <h3 class="sub-title">最近活动</h3>

    <div v-if="loadingHistory" class="activity-list">
      <div class="activity-item skeleton-item" v-for="i in 5" :key="i">
        <div class="skeleton-line" style="width: 60%"></div>
        <div class="skeleton-line" style="width: 30%; height: 12px; margin-top: 6px"></div>
      </div>
    </div>

    <div v-else-if="recentItems.length === 0" class="empty-state">
      暂无作文记录
    </div>

    <div v-else class="activity-list">
      <div class="activity-item" v-for="item in recentItems" :key="item.id">
        <div class="activity-top">
          <span class="activity-preview">{{ item.essay_preview }}</span>
          <span class="activity-time">{{ formatTime(item.created_at) }}</span>
        </div>
        <div class="activity-badges">
          <span class="badge badge-score" v-if="item.overall_score != null">
            {{ item.overall_score }}分
          </span>
          <span class="badge badge-band" v-if="item.band">
            {{ item.band }}
          </span>
          <span class="badge badge-mode">
            {{ item.mode === 'exam' ? '考试' : '自由' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userApi, type UserStats } from '@/api/user'
import { getEvaluationHistory, type EvaluationHistoryItem } from '@/api/writing'

const loading = ref(true)
const loadingHistory = ref(true)
const stats = ref<UserStats | null>(null)
const recentItems = ref<EvaluationHistoryItem[]>([])

function formatTime(dateStr: string): string {
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return `${diffDays}天前`
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  try {
    const res = await userApi.getStats()
    stats.value = res.data ?? null
  } catch {
    // silent
  } finally {
    loading.value = false
  }

  try {
    const res = await getEvaluationHistory(0, 5)
    recentItems.value = res.items ?? []
  } catch {
    // silent
  } finally {
    loadingHistory.value = false
  }
})
</script>

<style scoped>
.overview-section {
  max-width: 800px;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 24px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 24px 20px;
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #047857;
  margin-bottom: 6px;
}

.stat-label {
  font-size: 13px;
  color: #94a3b8;
}

.member-since {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 32px;
}

.sub-title {
  font-size: 17px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 16px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-item {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 16px 20px;
  transition: box-shadow 0.15s;
}
.activity-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.activity-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.activity-preview {
  font-size: 14px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 12px;
}

.activity-time {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}

.activity-badges {
  display: flex;
  gap: 8px;
}

.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.badge-score {
  background: #ecfdf5;
  color: #047857;
}

.badge-band {
  background: #eff6ff;
  color: #2563eb;
}

.badge-mode {
  background: #f1f5f9;
  color: #64748b;
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: #94a3b8;
  font-size: 14px;
}

/* Skeleton */
.skeleton-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.skeleton-line {
  background: linear-gradient(90deg, #e5e7eb 25%, #f1f5f9 50%, #e5e7eb 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 6px;
  height: 16px;
}

.skeleton-value {
  width: 50px;
  height: 28px;
}

.skeleton-label {
  width: 60px;
  height: 14px;
}

.skeleton-item {
  padding: 20px;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
