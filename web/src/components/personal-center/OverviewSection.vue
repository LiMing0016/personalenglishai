<template>
  <div class="overview-section">
    <h2 class="sr-only">学习概览</h2>

    <LearningContinuityPanel
      :recent-item="recentItems[0] ?? null"
      :study-days="stats?.studyDays"
      :stage-label="stageLabel"
    />

    <div class="overview-grid">
      <section class="summary-panel" aria-labelledby="summary-title">
        <div class="panel-heading">
          <div>
            <p class="panel-eyebrow">学习沉淀</p>
            <h3 id="summary-title">你的学习摘要</h3>
          </div>
          <span v-if="stats?.memberSince" class="member-since">加入已 {{ stats.memberSince }}</span>
        </div>

        <div v-if="loadingStats" class="stats-grid" aria-label="正在加载学习摘要">
          <div v-for="index in 4" :key="index" class="stat-card stat-card--loading">
            <span></span>
            <small></small>
          </div>
        </div>
        <div v-else class="stats-grid">
          <article class="stat-card">
            <FileText :size="18" :stroke-width="1.7" />
            <strong>{{ stats?.totalEssays ?? 0 }}</strong>
            <span>累计作文</span>
          </article>
          <article class="stat-card">
            <Gauge :size="18" :stroke-width="1.7" />
            <strong>{{ stats?.averageScore != null ? stats.averageScore.toFixed(1) : '--' }}</strong>
            <span>平均分</span>
          </article>
          <article class="stat-card">
            <Trophy :size="18" :stroke-width="1.7" />
            <strong>{{ stats?.bestScore ?? '--' }}</strong>
            <span>最高分</span>
          </article>
          <article class="stat-card">
            <Flame :size="18" :stroke-width="1.7" />
            <strong>{{ stats?.studyDays ?? 0 }}</strong>
            <span>学习天数</span>
          </article>
        </div>
      </section>

      <section class="asset-panel" aria-labelledby="asset-title">
        <div class="panel-heading">
          <div>
            <p class="panel-eyebrow">跨能力成长</p>
            <h3 id="asset-title">继续构建学习资产</h3>
          </div>
        </div>

        <div class="asset-links">
          <RouterLink to="/app/writing">
            <span class="asset-icon asset-icon--writing"><PenLine :size="19" :stroke-width="1.8" /></span>
            <span>
              <strong>写作训练</strong>
              <small>练习、评测与作文档案</small>
            </span>
            <ArrowUpRight :size="17" :stroke-width="1.8" />
          </RouterLink>
          <RouterLink to="/app/vocabulary">
            <span class="asset-icon asset-icon--vocabulary"><BookMarked :size="19" :stroke-width="1.8" /></span>
            <span>
              <strong>词汇卡片</strong>
              <small>复习已经沉淀的重点词汇</small>
            </span>
            <ArrowUpRight :size="17" :stroke-width="1.8" />
          </RouterLink>
          <RouterLink to="/app/translation">
            <span class="asset-icon asset-icon--translation"><Languages :size="19" :stroke-width="1.8" /></span>
            <span>
              <strong>翻译精读</strong>
              <small>从文档中继续提取语言资产</small>
            </span>
            <ArrowUpRight :size="17" :stroke-width="1.8" />
          </RouterLink>
        </div>
      </section>
    </div>

    <section class="activity-panel" aria-labelledby="activity-title">
      <div class="panel-heading">
        <div>
          <p class="panel-eyebrow">最近记录</p>
          <h3 id="activity-title">最近写作活动</h3>
        </div>
        <RouterLink v-if="recentItems.length > 0" class="panel-link" :to="{ path: '/app/me', query: { tab: 'records' } }">
          查看全部
          <ArrowRight :size="15" :stroke-width="1.8" />
        </RouterLink>
      </div>

      <div v-if="loadingHistory" class="activity-list" aria-label="正在加载最近活动">
        <div v-for="index in 3" :key="index" class="activity-item activity-item--loading">
          <span></span>
          <small></small>
        </div>
      </div>

      <div v-else-if="recentItems.length === 0" class="empty-state">
        <span class="empty-icon"><FilePenLine :size="24" :stroke-width="1.6" /></span>
        <div>
          <h4>完成第一次写作，建立你的学习记录</h4>
          <p>评测结果会进入能力画像，也会沉淀为可复习的学习资产。</p>
        </div>
        <RouterLink to="/app/writing">
          开始写作
          <ArrowRight :size="15" :stroke-width="1.8" />
        </RouterLink>
      </div>

      <div v-else class="activity-list">
        <article v-for="item in recentItems" :key="item.id" class="activity-item">
          <div class="activity-main">
            <span class="activity-mark"><FileCheck2 :size="18" :stroke-width="1.8" /></span>
            <div>
              <p>{{ item.essay_preview }}</p>
              <time :datetime="item.created_at">{{ formatTime(item.created_at) }}</time>
            </div>
          </div>
          <div class="activity-badges">
            <span v-if="item.overall_score != null" class="score-badge">{{ item.overall_score }} 分</span>
            <span v-if="item.band" class="band-badge">{{ item.band }}</span>
            <span class="mode-badge">{{ item.mode === 'exam' ? '考试写作' : '自由写作' }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ArrowRight,
  ArrowUpRight,
  BookMarked,
  FileCheck2,
  FilePenLine,
  FileText,
  Flame,
  Gauge,
  Languages,
  PenLine,
  Trophy,
} from 'lucide-vue-next'

import { userApi, type UserStats } from '@/api/user'
import { getEvaluationHistory, type EvaluationHistoryItem } from '@/api/writing'

import LearningContinuityPanel from './LearningContinuityPanel.vue'

const props = defineProps<{
  stageLabel?: string | null
  previewMode?: boolean
}>()

const loadingStats = ref(true)
const loadingHistory = ref(true)
const stats = ref<UserStats | null>(null)
const recentItems = ref<EvaluationHistoryItem[]>([])

function formatTime(dateString: string) {
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return '--'
  const now = new Date()
  const diffDays = Math.floor((now.getTime() - date.getTime()) / 86_400_000)
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays > 1 && diffDays < 7) return `${diffDays} 天前`
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

onMounted(() => {
  if (props.previewMode) {
    stats.value = {
      totalEssays: 28,
      averageScore: 7.2,
      bestScore: 8,
      studyDays: 2,
      memberSince: '4 个月',
    }
    recentItems.value = [
      {
        id: 1,
        mode: 'exam',
        gaokao_score: null,
        max_score: null,
        band: 'IELTS',
        overall_score: 7,
        essay_preview: 'Urban green spaces can improve both public health and community life.',
        created_at: new Date(Date.now() - 36 * 60 * 60 * 1000).toISOString(),
        favorited: true,
      },
      {
        id: 2,
        mode: 'free',
        gaokao_score: null,
        max_score: null,
        band: null,
        overall_score: 7.5,
        essay_preview: 'Technology changes the way people learn a second language.',
        created_at: new Date(Date.now() - 3 * 86_400_000).toISOString(),
        favorited: false,
      },
    ]
    loadingStats.value = false
    loadingHistory.value = false
    return
  }

  void userApi
    .getStats()
    .then((response) => {
      stats.value = response.data ?? null
    })
    .catch(() => {
      stats.value = null
    })
    .finally(() => {
      loadingStats.value = false
    })

  void getEvaluationHistory(0, 5)
    .then((response) => {
      recentItems.value = response.items ?? []
    })
    .catch(() => {
      recentItems.value = []
    })
    .finally(() => {
      loadingHistory.value = false
    })
})
</script>

<style scoped>
.overview-section {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr);
  gap: 28px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  clip-path: inset(50%);
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(360px, 0.8fr);
  gap: 22px;
}

.summary-panel,
.asset-panel,
.activity-panel {
  border: 1px solid #dfe7ef;
  border-radius: 22px;
  padding: 24px;
  background: #fff;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
}

.panel-eyebrow {
  margin: 0 0 5px;
  color: #7a8da2;
  font-size: 11px;
  font-weight: 760;
  letter-spacing: 0.12em;
}

.panel-heading h3 {
  margin: 0;
  color: #10243f;
  font-size: 19px;
  font-weight: 740;
  letter-spacing: -0.02em;
}

.member-since {
  color: #7a8da2;
  font-size: 12px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  position: relative;
  display: grid;
  min-height: 132px;
  align-content: center;
  justify-items: start;
  border: 1px solid #e4ebf1;
  border-radius: 17px;
  padding: 18px;
  background: linear-gradient(145deg, #fbfdfd, #f5f9fc);
  color: #6b7f96;
}

.stat-card > svg {
  position: absolute;
  top: 16px;
  right: 16px;
  color: #8ba398;
}

.stat-card strong {
  color: #0b7758;
  font-size: 30px;
  font-variant-numeric: tabular-nums;
  font-weight: 760;
  line-height: 1;
}

.stat-card span {
  margin-top: 9px;
  font-size: 12px;
}

.stat-card--loading {
  gap: 10px;
}

.stat-card--loading span,
.stat-card--loading small,
.activity-item--loading span,
.activity-item--loading small {
  border-radius: 6px;
  background: linear-gradient(90deg, #edf2f6 25%, #f8fafb 50%, #edf2f6 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
}

.stat-card--loading span {
  width: 54px;
  height: 30px;
}

.stat-card--loading small {
  width: 66px;
  height: 12px;
}

.asset-links {
  display: grid;
  gap: 9px;
}

.asset-links > a {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 67px;
  border: 1px solid transparent;
  border-radius: 14px;
  padding: 10px 12px;
  color: #5d7188;
  text-decoration: none;
  transition: border-color 160ms ease, background-color 160ms ease;
}

.asset-links > a:hover {
  border-color: #dce8e3;
  background: #f4faf8;
}

.asset-icon {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 12px;
}

.asset-icon--writing {
  background: #eaf7f2;
  color: #087a59;
}

.asset-icon--vocabulary {
  background: #edf2fb;
  color: #41628f;
}

.asset-icon--translation {
  background: #f5f0fb;
  color: #755293;
}

.asset-links strong,
.asset-links small {
  display: block;
}

.asset-links strong {
  color: #1b314a;
  font-size: 14px;
  font-weight: 700;
}

.asset-links small {
  overflow: hidden;
  margin-top: 3px;
  color: #778a9f;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #087a59;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.activity-list {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.activity-item {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 76px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-top: 1px solid #edf1f5;
  padding: 14px 2px;
}

.activity-item:first-child {
  border-top: 0;
}

.activity-main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 13px;
}

.activity-mark {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 12px;
  background: #edf8f4;
  color: #087a59;
}

.activity-main > div {
  min-width: 0;
}

.activity-main p {
  overflow: hidden;
  margin: 0;
  color: #263d56;
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-main time {
  display: block;
  margin-top: 5px;
  color: #8596a9;
  font-size: 11px;
}

.activity-badges {
  display: flex;
  flex: 0 0 auto;
  gap: 7px;
}

.activity-badges span {
  border-radius: 7px;
  padding: 5px 8px;
  font-size: 11px;
  font-weight: 650;
}

.score-badge {
  background: #eaf7f2;
  color: #087a59;
}

.band-badge {
  background: #edf2fb;
  color: #41628f;
}

.mode-badge {
  background: #f2f5f8;
  color: #687b91;
}

.activity-item--loading {
  display: grid;
  align-content: center;
  justify-content: stretch;
  gap: 8px;
}

.activity-item--loading span {
  width: 58%;
  height: 14px;
}

.activity-item--loading small {
  width: 24%;
  height: 10px;
}

.empty-state {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  min-height: 112px;
  border: 1px dashed #cfdce6;
  border-radius: 16px;
  padding: 20px;
  background: #f9fbfc;
}

.empty-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 14px;
  background: #eaf7f2;
  color: #087a59;
}

.empty-state h4 {
  margin: 0;
  color: #1c334c;
  font-size: 15px;
}

.empty-state p {
  margin: 6px 0 0;
  color: #73869b;
  font-size: 12px;
  line-height: 1.55;
}

.empty-state > a {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  gap: 7px;
  border-radius: 10px;
  padding: 0 14px;
  background: #087a59;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.asset-links a:focus-visible,
.panel-link:focus-visible,
.empty-state > a:focus-visible {
  outline: 3px solid rgba(4, 120, 87, 0.22);
  outline-offset: 3px;
}

@keyframes shimmer {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

@media (max-width: 1120px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .summary-panel,
  .asset-panel,
  .activity-panel {
    border-radius: 18px;
    padding: 20px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .activity-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .activity-main {
    width: 100%;
  }

  .activity-badges {
    margin-left: 53px;
  }

  .empty-state {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .empty-state > a {
    grid-column: 2;
    justify-self: start;
  }
}

@media (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
  }

  .stat-card {
    min-height: 112px;
    padding: 15px;
  }

  .stat-card strong {
    font-size: 25px;
  }

  .activity-badges {
    flex-wrap: wrap;
    margin-left: 0;
  }

  .empty-state {
    grid-template-columns: 1fr;
  }

  .empty-state > a {
    grid-column: 1;
  }
}
</style>
