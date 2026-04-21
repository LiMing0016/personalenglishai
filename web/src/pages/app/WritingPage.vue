<template>
  <!-- Loading -->
  <div v-if="phase === 'loading'" class="gate-center">
    <div class="gate-spinner" />
    <p class="gate-hint">加载中…</p>
  </div>

  <!-- Document list hub -->
  <div v-else-if="phase === 'doc-list'" class="hub-page">
    <!-- Header -->
    <div class="hub-header">
      <h2 class="hub-title">写作练习</h2>
      <button class="new-doc-btn" @click="navigateToPhase('mode-select')">+ 新建作文</button>
    </div>

    <!-- Stats cards -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon" style="background: #ecfdf5; color: #047857;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ docList.length }}</span>
          <span class="stat-label">篇作文</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #ecfdf5; color: #047857;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ totalSubmits }}</span>
          <span class="stat-label">次评分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #ecfdf5; color: #047857;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ avgScore ?? '--' }}</span>
          <span class="stat-label">平均分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #eff6ff; color: #2563eb;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ bestScore ?? '--' }}</span>
          <span class="stat-label">最高分</span>
        </div>
      </div>
    </div>

    <!-- Analytics panel -->
    <div class="analytics-panel">
      <div class="analytics-panel-header">
        <div class="analytics-tab-group">
          <button
            class="analytics-tab"
            :class="{ active: analyticsView === 'growth' }"
            @click="analyticsView = 'growth'"
          >成长趋势</button>
          <button
            class="analytics-tab"
            :class="{ active: analyticsView === 'habit' }"
            @click="analyticsView = 'habit'"
          >写作习惯</button>
        </div>

        <div v-if="analyticsView === 'growth'" class="analytics-segmented">
          <button
            v-for="option in analyticsModeOptions"
            :key="option.value"
            class="analytics-chip"
            :class="{ active: growthMode === option.value }"
            @click="growthMode = option.value"
          >{{ option.label }}</button>
        </div>

        <div v-else class="analytics-segmented">
          <button
            v-for="option in habitWindowOptions"
            :key="option.value"
            class="analytics-chip"
            :class="{ active: habitWindow === option.value }"
            @click="habitWindow = option.value"
          >{{ option.label }}</button>
        </div>
      </div>

      <template v-if="analyticsView === 'growth'">
        <template v-if="growthScreen === 'assets'">
          <div class="analytics-subheader">
            <div>
              <h3 class="analytics-title">写作资产</h3>
              <p class="analytics-subtitle">累计看我写了多少，主图看当期词数和句子产出</p>
            </div>

            <div class="analytics-range-controls">
              <button
                v-for="option in assetGranularityOptions"
                :key="option.value"
                class="analytics-chip"
                :class="{ active: assetGranularity === option.value }"
                @click="assetGranularity = option.value"
              >{{ option.label }}</button>
            </div>
          </div>

          <div class="analytics-metrics analytics-metrics--asset">
            <div class="analytics-metric-card analytics-metric-card--asset">
              <span class="analytics-metric-label">累计作文</span>
              <span class="analytics-metric-value">{{ formatAssetMetric(assetSummary.totalEssays) }}</span>
            </div>
            <div class="analytics-metric-card analytics-metric-card--asset">
              <span class="analytics-metric-label">累计词数</span>
              <span class="analytics-metric-value">{{ formatAssetMetric(assetSummary.totalWords) }}</span>
            </div>
            <div class="analytics-metric-card analytics-metric-card--asset">
              <span class="analytics-metric-label">累计句子</span>
              <span class="analytics-metric-value">{{ formatAssetMetric(assetSummary.totalSentences) }}</span>
            </div>
            <div class="analytics-metric-card analytics-metric-card--asset">
              <span class="analytics-metric-label">篇均语法错误</span>
              <span class="analytics-metric-value">{{ formatAssetAverage(assetSummary.avgGrammarErrorsPerEssay) }}</span>
            </div>
          </div>

          <div v-if="assetSeries.length > 0" class="asset-board">
            <div class="asset-board-header">
              <div>
                <h4 class="asset-board-title">{{ assetPrimaryTitle }}</h4>
              </div>
            </div>

            <div class="asset-board-summary">
              <div class="asset-hero-card">
                <div class="asset-hero-top">
                  <div>
                    <span class="asset-card-label">{{ assetCurrentLabel }}</span>
                    <div class="asset-hero-value">{{ formatAssetMetric(assetCurrentPeriod?.wordCount) }}</div>
                    <div class="asset-hero-unit">词数</div>
                  </div>
                  <span class="asset-hero-period">{{ assetCurrentPeriod?.periodLabel ?? '--' }}</span>
                </div>

                <div class="asset-hero-meta">
                  <div class="asset-hero-meta-item">
                    <span class="asset-meta-label">句子</span>
                    <strong>{{ formatAssetMetric(assetCurrentPeriod?.sentenceCount) }}</strong>
                  </div>
                  <div class="asset-hero-meta-item">
                    <span class="asset-meta-label">作文</span>
                    <strong>{{ formatAssetMetric(assetCurrentPeriod?.essayCount) }}</strong>
                  </div>
                  <div class="asset-hero-meta-item">
                    <span class="asset-meta-label">篇均词数</span>
                    <strong>{{ formatAssetMetric(assetCurrentAverageWords) }}</strong>
                  </div>
                </div>
              </div>

              <div class="asset-side-cards">
                <div class="asset-side-card">
                  <span class="asset-card-label">较上一{{ assetGranularityUnit }}变化</span>
                  <div class="asset-side-value-row">
                    <strong>{{ formatSignedAssetMetric(assetWordDelta) }}</strong>
                    <span class="asset-side-trend" :class="{ down: (assetWordDeltaPercent ?? 0) < 0 }">
                      {{ formatSignedPercent(assetWordDeltaPercent) }}
                    </span>
                  </div>
                  <p class="asset-side-note">{{ assetDeltaDescription }}</p>
                </div>

                <div class="asset-side-card">
                  <span class="asset-card-label">最高产{{ assetGranularityUnit }}</span>
                  <div class="asset-side-value-row">
                    <strong>{{ assetPeakPeriod?.periodLabel ?? '--' }}</strong>
                    <span class="asset-side-trend neutral">词数峰值</span>
                  </div>
                  <p class="asset-side-note">
                    {{ assetPeakDescription }}
                  </p>
                </div>
              </div>
            </div>

            <div class="asset-periods-section">
              <div class="asset-periods-head">
                <div>
                  <h5 class="asset-periods-title">{{ assetRecentSectionTitle }}</h5>
                  <p class="asset-periods-subtitle">当前{{ assetGranularityUnit }}高亮，保留最近几个周期的连续感</p>
                </div>
              </div>

              <div class="asset-period-grid">
                <article
                  v-for="period in assetVisiblePeriods"
                  :key="period.periodStart"
                  class="asset-period-card"
                  :class="{ active: assetCurrentPeriod?.periodStart === period.periodStart }"
                >
                  <div class="asset-period-top">
                    <strong class="asset-period-name">{{ period.periodLabel }}</strong>
                    <span
                      class="asset-period-tag"
                      :class="{ active: assetCurrentPeriod?.periodStart === period.periodStart }"
                    >
                      {{ assetCurrentPeriod?.periodStart === period.periodStart ? '当前重点' : '历史产出' }}
                    </span>
                  </div>

                  <div class="asset-period-value">{{ formatAssetMetric(period.wordCount) }}</div>
                  <div class="asset-period-label">词数</div>

                  <div class="asset-period-progress">
                    <div class="asset-period-progress-row">
                      <span>句子 {{ formatAssetMetric(period.sentenceCount) }}</span>
                      <span>作文 {{ formatAssetMetric(period.essayCount) }}</span>
                    </div>
                    <div class="asset-progress-track">
                      <span :style="{ width: `${assetWordWidth(period.wordCount)}%` }" />
                    </div>
                  </div>

                  <div class="asset-period-footer">
                    <div class="asset-mini-bars">
                      <span
                        v-for="(height, index) in assetMiniHeights(period)"
                        :key="`${period.periodStart}-${index}`"
                        :style="{ height: `${height}px` }"
                      />
                    </div>
                    <span class="asset-period-footnote">{{ assetPeriodFootnote(period) }}</span>
                  </div>
                </article>
              </div>
            </div>
          </div>

          <div v-else class="analytics-placeholder analytics-placeholder--large">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5"><path d="M4 19V5"/><path d="M20 19V5"/><path d="M8 16V9"/><path d="M12 16V7"/><path d="M16 16V11"/></svg>
            <span>{{ assetEmptyText }}</span>
          </div>
        </template>

        <template v-else>
          <div class="analytics-subheader">
            <div>
              <h3 class="analytics-title">首次得分 vs 最新得分</h3>
              <p class="analytics-subtitle">按最近已评分作文对比前后变化</p>
            </div>

            <div class="analytics-range-controls">
              <button
                v-for="option in growthRangeOptions"
                :key="option.value"
                class="analytics-chip"
                :class="{ active: growthRange === option.value }"
                @click="growthRange = option.value"
              >{{ option.label }}</button>

              <label v-if="growthRange === 'custom'" class="analytics-custom-input">
                <input
                  v-model.number="growthCustomCount"
                  type="number"
                  min="1"
                  max="200"
                  inputmode="numeric"
                />
                <span>篇</span>
              </label>
            </div>
          </div>

          <div v-if="growthTrendDocs.length >= 2" ref="growthChartRef" class="analytics-chart" />
          <div v-else class="analytics-placeholder analytics-placeholder--large">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
            <span>{{ growthEmptyText }}</span>
          </div>

          <div class="analytics-metrics">
            <div class="analytics-metric-card">
              <span class="analytics-metric-label">首次均分</span>
              <span class="analytics-metric-value">{{ formatTrendMetric(growthInitialAverage) }}</span>
            </div>
            <div class="analytics-metric-card">
              <span class="analytics-metric-label">最新均分</span>
              <span class="analytics-metric-value">{{ formatTrendMetric(growthLatestAverage) }}</span>
            </div>
            <div class="analytics-metric-card">
              <span class="analytics-metric-label">平均提分</span>
              <span class="analytics-metric-value" :class="{ up: growthAverageDelta != null && growthAverageDelta > 0 }">
                {{ formatTrendDelta(growthAverageDelta) }}
              </span>
            </div>
          </div>
        </template>
      </template>

      <template v-else>
        <div class="analytics-subheader">
          <div>
            <h3 class="analytics-title">评分提交习惯</h3>
            <p class="analytics-subtitle">按评分提交记录展示活跃天数与热力分布</p>
          </div>
        </div>

        <div class="analytics-placeholder analytics-placeholder--large">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5"><path d="M3 5h18"/><path d="M8 3v4"/><path d="M16 3v4"/><rect x="3" y="5" width="18" height="16" rx="2"/></svg>
          <span>{{ habitPlaceholderText }}</span>
        </div>
      </template>

      <div v-if="analyticsView === 'growth'" class="analytics-footer-nav">
        <div class="analytics-dots">
          <button
            v-for="item in growthPagerItems"
            :key="item.value"
            class="analytics-dot"
            :class="{ active: growthScreen === item.value }"
            :aria-label="`切换到${item.label}`"
            @click="growthScreen = item.value"
          />
        </div>
        <div class="analytics-footer-actions">
          <button
            class="analytics-arrow"
            :disabled="growthPagerIndex <= 0"
            aria-label="上一个成长趋势屏"
            @click="goPrevGrowthScreen"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <button
            class="analytics-arrow"
            :disabled="growthPagerIndex >= growthPagerItems.length - 1"
            aria-label="下一个成长趋势屏"
            @click="goNextGrowthScreen"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
        </div>
      </div>
    </div>

    <!-- Search -->
    <div class="search-bar">
      <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
      <input
        v-model="searchQuery"
        class="search-input"
        type="text"
        placeholder="搜索作文标题..."
      />
      <button v-if="searchQuery" class="search-clear" @click="searchQuery = ''">&times;</button>
    </div>

    <!-- Document grid section -->
    <div class="doc-section">
      <div class="doc-section-header">
        <span class="doc-section-title">历史作文</span>
        <div class="doc-filters">
          <div class="filter-pills">
            <button
              v-for="f in filterOptions"
              :key="f.value"
              class="filter-pill"
              :class="{ active: filterMode === f.value }"
              @click="filterMode = f.value"
            >{{ f.label }}</button>
          </div>
          <select v-model="sortBy" class="sort-select">
            <option value="updatedAt">最近修改</option>
            <option value="createdAt">最近创建</option>
            <option value="score">最高分</option>
          </select>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="docListLoading" class="doc-empty">
        <div class="gate-spinner" />
      </div>

      <!-- Empty -->
      <div v-else-if="docList.length === 0" class="doc-empty">
        <p class="empty-icon">&#128221;</p>
        <p class="empty-text">还没有写过作文</p>
        <p class="empty-hint">点击「新建作文」开始你的第一篇写作练习</p>
        <button class="gate-btn" style="margin-top: 16px;" @click="navigateToPhase('mode-select')">开始写作</button>
      </div>

      <!-- No results after filter -->
      <div v-else-if="filteredDocs.length === 0" class="doc-empty">
        <p class="empty-text">没有找到符合条件的作文</p>
      </div>

      <!-- Cards grid -->
      <div v-else class="doc-grid">
        <div
          v-for="doc in displayDocs"
          :key="doc.docId"
          class="doc-card"
          @click="openDocument(doc)"
        >
          <div class="doc-card-top">
            <span class="doc-mode-tag" :class="doc.taskPrompt ? 'exam' : 'free'">
              {{ doc.taskPrompt ? '考试' : '自由' }}
            </span>
            <button class="doc-menu-btn" @click.stop="toggleMenu(doc.docId)" title="更多操作">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><circle cx="8" cy="3" r="1.5"/><circle cx="8" cy="8" r="1.5"/><circle cx="8" cy="13" r="1.5"/></svg>
            </button>
            <!-- Dropdown menu -->
            <div v-if="openMenuId === doc.docId" class="doc-menu" @click.stop>
              <button class="doc-menu-item" @click="startRename(doc)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 3a2.85 2.85 0 114 4L7.5 20.5 2 22l1.5-5.5z"/></svg>
                重命名
              </button>
              <button class="doc-menu-item doc-menu-danger" @click="confirmDelete(doc)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                删除
              </button>
            </div>
          </div>
          <h3 class="doc-card-title">{{ doc.title || '未命名作文' }}</h3>
          <div class="doc-card-score-area">
            <template v-if="doc.latestScore != null">
              <span class="doc-score-num" :class="scoreColor(doc.latestScore)">{{ doc.latestScore }}</span>
              <span class="doc-score-max">/ 100</span>
              <span
                v-if="doc.initialScore != null && doc.latestScore !== doc.initialScore"
                class="doc-score-delta"
                :class="doc.latestScore - doc.initialScore > 0 ? 'up' : 'down'"
              >{{ doc.latestScore - doc.initialScore > 0 ? '+' : '' }}{{ doc.latestScore - doc.initialScore }}</span>
            </template>
            <span v-else class="doc-score-none">未评分</span>
          </div>
          <div class="doc-card-bottom">
            <span class="doc-card-time">{{ formatTime(doc.updatedAt) }}</span>
            <span class="doc-card-action">继续写作 &rarr;</span>
          </div>
        </div>
      </div>

      <!-- Rename dialog -->
      <div v-if="renameDialog.visible" class="confirm-overlay" @click.self="renameDialog.visible = false">
        <div class="rename-dialog">
          <button class="confirm-close" @click="renameDialog.visible = false">&times;</button>
          <h3 class="rename-title">重命名</h3>
          <input
            v-model="renameDialog.title"
            class="rename-input"
            placeholder="请输入新标题"
            maxlength="100"
            @keyup.enter="doRename"
          />
          <div class="rename-actions">
            <button class="btn-cancel" @click="renameDialog.visible = false">取消</button>
            <button class="gate-btn" :disabled="!renameDialog.title.trim()" @click="doRename">确定</button>
          </div>
        </div>
      </div>

      <!-- Delete confirm dialog -->
      <div v-if="deleteDialog.visible" class="confirm-overlay" @click.self="deleteDialog.visible = false">
        <div class="rename-dialog">
          <button class="confirm-close" @click="deleteDialog.visible = false">&times;</button>
          <h3 class="rename-title">确认删除</h3>
          <p class="delete-hint">删除「{{ deleteDialog.title }}」后将无法恢复，确定要删除吗？</p>
          <div class="rename-actions">
            <button class="btn-cancel" @click="deleteDialog.visible = false">取消</button>
            <button class="gate-btn gate-btn--danger" @click="doDelete">删除</button>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="filteredDocs.length > PAGE_SIZE" class="pagination">
        <button
          class="page-btn"
          :disabled="currentPage <= 1"
          @click="currentPage--"
        >&lsaquo;</button>
        <button
          v-for="p in paginationPages"
          :key="p"
          class="page-btn"
          :class="{ active: currentPage === p, ellipsis: p === -1 }"
          :disabled="p === -1"
          @click="p !== -1 && (currentPage = p)"
        >{{ p === -1 ? '...' : p }}</button>
        <button
          class="page-btn"
          :disabled="currentPage >= Math.ceil(filteredDocs.length / PAGE_SIZE)"
          @click="currentPage++"
        >&rsaquo;</button>
      </div>
    </div>
  </div>

  <!-- Mode select -->
  <div v-else-if="phase === 'mode-select'" class="gate-center">
    <h2 class="gate-title">选择写作模式</h2>
    <p class="gate-desc">
      当前学段：<strong>{{ getStageLabel(currentStage) }}</strong>
    </p>
    <div class="mode-grid">
      <button class="mode-card" @click="createBlankFreeDoc">
        <span class="mode-icon">&#9997;&#65039;</span>
        <span class="mode-name">自由模式</span>
        <span class="mode-desc">自由写作，AI 实时辅助与反馈</span>
      </button>
      <button class="mode-card" @click="openExamSetupFromModeSelect">
        <span class="mode-icon">&#9200;</span>
        <span class="mode-name">考试模式</span>
        <span class="mode-desc">模拟考试环境，限时写作与评分</span>
      </button>
    </div>
    <button class="back-link" @click="navigateToPhase('doc-list')">&#8592; 返回文档列表</button>
  </div>

  <!-- Exam setup -->
  <ExamSetupPage
    v-else-if="phase === 'exam-setup'"
    :initial-topic="resumeTopicForSetup"
    :resume-metadata="resumeMetadataForSetup"
    :study-stage="currentStage ?? ''"
    @confirm="onExamConfirm"
    @back="onExamSetupBack"
    @save-draft="onExamSaveDraft"
    @switch-mode="onExamSetupSwitchMode"
  />

  <!-- Editor -->
  <EditorShell
    v-else-if="phase === 'editor'"
    :initial-writing-mode="chosenMode"
    :initial-task-prompt="initialTaskPrompt"
    :initial-doc-id="initialDocId"
    :initial-existing-content="initialExistingContent"
    :exam-max-score="examMaxScore"
    :initial-submit-count="initialSubmitCount"
    :study-stage="currentStage ?? ''"
    @back="onEditorBack"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStorage, useEventListener } from '@vueuse/core'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  GridComponent, TooltipComponent, LegendComponent,
  CanvasRenderer,
])
import EditorShell from '@/components/writing/EditorShell.vue'
import ExamSetupPage from '@/pages/app/ExamSetupPage.vue'
import type { ExamTopicInfo } from '@/pages/app/examPromptHelpers'
import { buildExamTaskPrompt } from '@/pages/app/examPromptHelpers'
import { stageCache } from '@/stores/stageCache'
import { getStageLabel } from '@/constants/stage'
import { getWritingSessionMetadata, startWritingSession, getWritingDocuments, getWritingDashboardAssets } from '@/api/writing'
import type {
  WritingDashboardAssetsResponse,
  WritingDashboardAssetSummary,
  WritingDocumentItem,
  WritingSessionMetadataResponse,
} from '@/api/writing'
import { renameDocument, deleteDocument } from '@/api/document'
import { showToast } from '@/utils/toast'

type Phase = 'loading' | 'doc-list' | 'mode-select' | 'exam-setup' | 'editor'
type RoutePhase = Exclude<Phase, 'loading'>

const router = useRouter()
const route = useRoute()
const setImmersive = inject<(v: boolean | null) => void>('setImmersive', () => {})

const booting = ref(true)
const currentStage = ref<string | null>(null)
const chosenMode = useSessionStorage<'free' | 'exam'>('peai:writing:chosenMode', 'free')
const initialTaskPrompt = ref<string | undefined>(undefined)
const initialDocId = useSessionStorage<string | null>('peai:writing:docId', null)
const initialExistingContent = ref<string | null>(null)
const examMaxScore = useSessionStorage<number | null>('peai:writing:examMaxScore', null)
const initialSubmitCount = ref(0)
const resumeTopicForSetup = ref<string | undefined>(undefined)
const resumeMetadataForSetup = ref<WritingSessionMetadataResponse | null>(null)

function resolveRoutePhase(): RoutePhase {
  switch (route.name) {
    case 'WritingModeSelect':
      return 'mode-select'
    case 'WritingExamSetup':
      return 'exam-setup'
    case 'WritingEditor':
      return 'editor'
    default:
      return 'doc-list'
  }
}

const phase = computed<Phase>(() => {
  if (booting.value) return 'loading'
  return resolveRoutePhase()
})

function routeNameForPhase(nextPhase: RoutePhase) {
  switch (nextPhase) {
    case 'mode-select':
      return 'WritingModeSelect'
    case 'exam-setup':
      return 'WritingExamSetup'
    case 'editor':
      return 'WritingEditor'
    default:
      return 'WritingDocList'
  }
}

async function navigateToPhase(nextPhase: RoutePhase, replace = false) {
  // Mark navigation to editor so EditorShell can distinguish from refresh
  if (nextPhase === 'editor') {
    try { sessionStorage.setItem('peai:writing:freshNav', '1') } catch {}
  }
  const target = { name: routeNameForPhase(nextPhase) }
  if (replace) {
    await router.replace(target)
    return
  }
  await router.push(target)
}

// Document list & pagination
const PAGE_SIZE = 9
const docList = ref<WritingDocumentItem[]>([])
const currentPage = ref(1)
const docListLoading = ref(false)
const filterMode = ref<'all' | 'free' | 'exam'>('all')
const sortBy = ref<'updatedAt' | 'createdAt' | 'score'>('updatedAt')
const searchQuery = ref('')

const filterOptions = [
  { value: 'all' as const, label: '全部' },
  { value: 'free' as const, label: '自由' },
  { value: 'exam' as const, label: '考试' },
]

type AnalyticsView = 'growth' | 'habit'
type GrowthScreen = 'assets' | 'scores'
type GrowthRange = '10' | '20' | 'custom'
type HabitWindow = '7d' | '30d'
type AssetGranularity = 'week' | 'month'

const analyticsView = useSessionStorage<AnalyticsView>('peai:writing:analytics:view', 'growth')
const growthScreen = useSessionStorage<GrowthScreen>('peai:writing:analytics:growth-screen', 'assets')
const growthMode = useSessionStorage<'all' | 'free' | 'exam'>('peai:writing:analytics:growth-mode', 'all')
const growthRange = useSessionStorage<GrowthRange>('peai:writing:analytics:growth-range', '10')
const growthCustomCount = useSessionStorage<number>('peai:writing:analytics:growth-custom-count', 30)
const habitWindow = useSessionStorage<HabitWindow>('peai:writing:analytics:habit-window', '7d')
const assetGranularity = useSessionStorage<AssetGranularity>('peai:writing:analytics:asset-granularity', 'month')

const analyticsModeOptions = [
  { value: 'all' as const, label: '全部' },
  { value: 'free' as const, label: '自由' },
  { value: 'exam' as const, label: '考试' },
]

const assetGranularityOptions = [
  { value: 'month' as const, label: '按月' },
  { value: 'week' as const, label: '按周' },
]

const growthRangeOptions = [
  { value: '10' as const, label: '最近 10 篇' },
  { value: '20' as const, label: '最近 20 篇' },
  { value: 'custom' as const, label: '自定义' },
]

const habitWindowOptions = [
  { value: '7d' as const, label: '近 7 天' },
  { value: '30d' as const, label: '近 30 天' },
]

const growthPagerItems = [
  { value: 'assets' as const, label: '写作资产' },
  { value: 'scores' as const, label: '分数成长' },
]

const growthPagerIndex = computed(() =>
  growthPagerItems.findIndex((item) => item.value === growthScreen.value),
)

// Computed stats
const scoredDocs = computed(() => docList.value.filter(d => d.latestScore != null))
const totalSubmits = computed(() => docList.value.reduce((s, d) => s + (d.submitCount || 0), 0))
const avgScore = computed(() => {
  if (scoredDocs.value.length === 0) return null
  return Math.round(scoredDocs.value.reduce((s, d) => s + (d.latestScore ?? 0), 0) / scoredDocs.value.length)
})
const bestScore = computed(() => {
  if (scoredDocs.value.length === 0) return null
  return Math.max(...scoredDocs.value.map(d => d.latestScore ?? 0))
})

const filteredDocs = computed(() => {
  let list = [...docList.value]
  // Search
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(d => (d.title || '').toLowerCase().includes(q) || (d.taskPrompt || '').toLowerCase().includes(q))
  // Filter
  if (filterMode.value === 'exam') list = list.filter(d => !!d.taskPrompt)
  else if (filterMode.value === 'free') list = list.filter(d => !d.taskPrompt)
  if (sortBy.value === 'score') {
    list.sort((a, b) => (b.latestScore ?? -1) - (a.latestScore ?? -1))
  } else if (sortBy.value === 'createdAt') {
    list.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  } else {
    list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  }
  return list
})

// Clamp currentPage when filteredDocs shrinks
const maxPage = computed(() => Math.max(1, Math.ceil(filteredDocs.value.length / PAGE_SIZE)))
watch(maxPage, (mp) => {
  if (currentPage.value > mp) currentPage.value = mp
})

const displayDocs = computed(() => {
  const page = Math.min(currentPage.value, maxPage.value)
  const start = (page - 1) * PAGE_SIZE
  return filteredDocs.value.slice(start, start + PAGE_SIZE)
})

const paginationPages = computed(() => {
  const total = maxPage.value
  const cur = Math.min(currentPage.value, total)
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages: number[] = [1]
  if (cur > 3) pages.push(-1)
  for (let i = Math.max(2, cur - 1); i <= Math.min(total - 1, cur + 1); i++) pages.push(i)
  if (cur < total - 2) pages.push(-1)
  pages.push(total)
  return pages
})

// Reset page when filter/sort changes
watch([filterMode, sortBy, searchQuery], () => { currentPage.value = 1 })

// Immersive toggle: only editor is immersive
watch(phase, (p, prev) => {
  setImmersive(p === 'editor' ? true : false)
  if (!booting.value && p === 'doc-list' && prev && prev !== 'doc-list') {
    void loadDocList()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  setImmersive(null)
})

const growthChartRef = ref<HTMLElement | null>(null)
let growthChartInstance: echarts.ECharts | null = null
const assetDashboard = ref<WritingDashboardAssetsResponse | null>(null)
const emptyAssetSummary: WritingDashboardAssetSummary = {
  totalEssays: 0,
  totalWords: 0,
  totalSentences: 0,
  avgGrammarErrorsPerEssay: 0,
}

function isExamDoc(doc: WritingDocumentItem) {
  return Boolean(doc.taskPrompt)
}

function resolveInitialScore(doc: WritingDocumentItem) {
  return doc.initialScore ?? doc.latestScore
}

function normalizeGrowthCount(value: number) {
  if (!Number.isFinite(value)) return 30
  return Math.min(200, Math.max(1, Math.round(value)))
}

const growthSelectedCount = computed(() => {
  if (growthRange.value === '10') return 10
  if (growthRange.value === '20') return 20
  return normalizeGrowthCount(Number(growthCustomCount.value))
})

const growthSourceDocs = computed(() => {
  let list = [...scoredDocs.value]
  if (growthMode.value === 'exam') {
    list = list.filter(isExamDoc)
  } else if (growthMode.value === 'free') {
    list = list.filter((doc) => !isExamDoc(doc))
  }
  list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  return list
})

const growthTrendDocs = computed(() => {
  const recent = growthSourceDocs.value.slice(0, growthSelectedCount.value)
  return [...recent].sort((a, b) => new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime())
})

const growthInitialAverage = computed(() => {
  if (growthTrendDocs.value.length === 0) return null
  const total = growthTrendDocs.value.reduce((sum, doc) => sum + (resolveInitialScore(doc) ?? 0), 0)
  return total / growthTrendDocs.value.length
})

const growthLatestAverage = computed(() => {
  if (growthTrendDocs.value.length === 0) return null
  const total = growthTrendDocs.value.reduce((sum, doc) => sum + (doc.latestScore ?? 0), 0)
  return total / growthTrendDocs.value.length
})

const growthAverageDelta = computed(() => {
  if (growthTrendDocs.value.length === 0) return null
  const total = growthTrendDocs.value.reduce((sum, doc) => {
    const baseline = resolveInitialScore(doc) ?? 0
    return sum + ((doc.latestScore ?? baseline) - baseline)
  }, 0)
  return total / growthTrendDocs.value.length
})

const growthEmptyText = computed(() => {
  if (growthSourceDocs.value.length === 0) {
    if (growthMode.value === 'exam') return '当前还没有已评分的考试作文'
    if (growthMode.value === 'free') return '当前还没有已评分的自由写作'
    return '完成评分后，这里会显示首次得分和最新得分的变化'
  }
  return '至少需要 2 篇已评分作文，才能更稳定地展示趋势'
})

const habitPlaceholderText = computed(() => {
  return habitWindow.value === '7d'
    ? '近 7 天评分提交热力图将在评分聚合接口接入后显示'
    : '近 30 天评分提交热力图将在评分聚合接口接入后显示'
})

const assetSummary = computed(() => assetDashboard.value?.summary ?? emptyAssetSummary)
const assetSeries = computed(() => assetDashboard.value?.series ?? [])
const assetCurrentPeriod = computed(() =>
  assetSeries.value.length > 0 ? assetSeries.value[assetSeries.value.length - 1] : null,
)
const assetPreviousPeriod = computed(() =>
  assetSeries.value.length > 1 ? assetSeries.value[assetSeries.value.length - 2] : null,
)
const assetPeakPeriod = computed(() => {
  if (assetSeries.value.length === 0) return null
  return assetSeries.value.reduce((peak, period) =>
    period.wordCount > peak.wordCount ? period : peak,
  )
})
const assetVisiblePeriods = computed(() => assetSeries.value.slice(-3))
const assetCurrentAverageWords = computed(() => {
  if (!assetCurrentPeriod.value || assetCurrentPeriod.value.essayCount <= 0) return null
  return assetCurrentPeriod.value.wordCount / assetCurrentPeriod.value.essayCount
})
const assetWordDelta = computed(() => {
  if (!assetCurrentPeriod.value || !assetPreviousPeriod.value) return null
  return assetCurrentPeriod.value.wordCount - assetPreviousPeriod.value.wordCount
})
const assetWordDeltaPercent = computed(() => {
  if (!assetPreviousPeriod.value || assetPreviousPeriod.value.wordCount <= 0 || assetWordDelta.value == null) {
    return null
  }
  return (assetWordDelta.value / assetPreviousPeriod.value.wordCount) * 100
})
const assetMaxWordCount = computed(() => {
  if (assetSeries.value.length === 0) return 0
  return Math.max(...assetSeries.value.map((period) => period.wordCount))
})
const assetGranularityUnit = computed(() => (assetGranularity.value === 'month' ? '月' : '周'))
const assetPrimaryTitle = computed(() => (assetGranularity.value === 'month' ? '本月写作产出' : '本周写作产出'))
const assetCurrentLabel = computed(() => (assetGranularity.value === 'month' ? '当前月份' : '当前周次'))
const assetRecentSectionTitle = computed(() => (assetGranularity.value === 'month' ? '最近月份' : '最近周次'))
const assetDeltaDescription = computed(() => {
  if (!assetCurrentPeriod.value || !assetPreviousPeriod.value || assetWordDelta.value == null) {
    return `还需要上一${assetGranularityUnit.value}数据，才能显示环比变化`
  }
  if (assetWordDelta.value === 0) {
    return `和上一${assetGranularityUnit.value}持平，当前产出比较稳定`
  }
  return assetWordDelta.value > 0
    ? `比上一${assetGranularityUnit.value}多写了 ${formatAssetMetric(assetWordDelta.value)} 词`
    : `比上一${assetGranularityUnit.value}少写了 ${formatAssetMetric(Math.abs(assetWordDelta.value))} 词`
})
const assetPeakDescription = computed(() => {
  if (!assetPeakPeriod.value) return `还没有可统计的${assetGranularityUnit.value}度产出`
  return `${assetPeakPeriod.value.periodLabel}共写了 ${formatAssetMetric(assetPeakPeriod.value.wordCount)} 词、${formatAssetMetric(assetPeakPeriod.value.sentenceCount)} 句`
})

const assetEmptyText = computed(() => {
  if (growthMode.value === 'exam') return '当前还没有已评分的考试作文资产数据'
  if (growthMode.value === 'free') return '当前还没有已评分的自由写作资产数据'
  return '完成评分后，这里会显示累计资产和当期产出'
})

function goPrevGrowthScreen() {
  const nextIndex = Math.max(0, growthPagerIndex.value - 1)
  growthScreen.value = growthPagerItems[nextIndex]?.value ?? 'assets'
}

function goNextGrowthScreen() {
  const nextIndex = Math.min(growthPagerItems.length - 1, growthPagerIndex.value + 1)
  growthScreen.value = growthPagerItems[nextIndex]?.value ?? 'scores'
}

watch([growthTrendDocs, growthChartRef, analyticsView, growthScreen], async () => {
  await nextTick()
  if (
    analyticsView.value === 'growth'
    && growthScreen.value === 'scores'
    && growthTrendDocs.value.length >= 2
    && growthChartRef.value
  ) {
    renderGrowthChart()
    return
  }
  if (growthChartInstance) {
    growthChartInstance.dispose()
    growthChartInstance = null
  }
}, { immediate: true })

watch(growthCustomCount, (value) => {
  const normalized = normalizeGrowthCount(Number(value))
  if (normalized !== value) {
    growthCustomCount.value = normalized
  }
})

useEventListener(window, 'resize', () => {
  growthChartInstance?.resize()
})

function renderGrowthChart() {
  if (!growthChartRef.value) return
  if (growthChartInstance) growthChartInstance.dispose()
  growthChartInstance = echarts.init(growthChartRef.value)

  const docs = growthTrendDocs.value
  growthChartInstance.setOption({
    animationDuration: 300,
    grid: { top: 42, right: 18, bottom: 26, left: 40 },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 12,
      textStyle: { color: '#6b7280', fontSize: 12 },
      data: ['首次得分', '最新得分'],
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#111827',
      borderWidth: 0,
      textStyle: { color: '#f9fafb' },
      formatter: (params: Array<{ dataIndex: number; seriesName: string; value: number }>) => {
        const index = params[0]?.dataIndex ?? 0
        const doc = docs[index]
        if (!doc) return ''
        const baseline = resolveInitialScore(doc)
        const latest = doc.latestScore
        const delta = baseline != null && latest != null ? latest - baseline : 0
        return [
          `<div style="font-weight:600;margin-bottom:6px;">${doc.title || `作文 #${index + 1}`}</div>`,
          `<div style="color:#9ca3af;margin-bottom:6px;">${formatTime(doc.updatedAt)}</div>`,
          `<div>首次得分：${baseline ?? '--'}</div>`,
          `<div>最新得分：${latest ?? '--'}</div>`,
          `<div>提分：${delta >= 0 ? '+' : ''}${delta}</div>`,
        ].join('')
      },
    },
    xAxis: {
      type: 'category',
      data: docs.map((_, index) => `#${index + 1}`),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    series: [
      {
        name: '首次得分',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: docs.map((doc) => resolveInitialScore(doc)),
        lineStyle: { color: '#94a3b8', width: 2 },
        itemStyle: { color: '#94a3b8' },
      },
      {
        name: '最新得分',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: docs.map((doc) => doc.latestScore),
        lineStyle: { color: '#047857', width: 3 },
        itemStyle: { color: '#047857' },
        areaStyle: { color: 'rgba(4, 120, 87, 0.10)' },
      },
    ],
  })
}

onBeforeUnmount(() => {
  if (growthChartInstance) { growthChartInstance.dispose(); growthChartInstance = null }
})

// Lifecycle
onMounted(async () => {
  const cached = stageCache.value
  if (!cached || cached === '' || cached === '__error__') {
    router.replace({ path: '/app/stage-setup', query: { redirect: '/app/writing' } })
    return
  }
  currentStage.value = cached

  const currentPhase = resolveRoutePhase()

  if (currentPhase === 'editor') {
    if (!initialDocId.value) {
      await loadDocList()
      booting.value = false
      await navigateToPhase('doc-list', true)
      return
    }
    booting.value = false
    return
  }

  if (currentPhase === 'doc-list') {
    await loadDocList()
  }

  booting.value = false
})

watch([growthMode, assetGranularity, phase], async () => {
  if (phase.value !== 'doc-list') return
  if (booting.value) return
  await loadDashboardAssets()
})

async function loadDocList() {
  docListLoading.value = true
  try {
    const docRes = await getWritingDocuments(0, 200)
    docList.value = docRes.items ?? []
    await loadDashboardAssets()
  } catch (e) {
    console.warn('[WritingPage] loadDocList failed', e)
  } finally {
    docListLoading.value = false
  }
}

async function loadDashboardAssets() {
  try {
    assetDashboard.value = await getWritingDashboardAssets({
      mode: growthMode.value,
      granularity: assetGranularity.value,
    })
  } catch (e) {
    console.warn('[WritingPage] loadDashboardAssets failed', e)
    assetDashboard.value = null
  }
}

async function createFreeDoc(seed?: { title?: string; initialTaskPrompt?: string | null }) {
  chosenMode.value = 'free'
  initialTaskPrompt.value = seed?.initialTaskPrompt ?? undefined
  examMaxScore.value = null
  initialExistingContent.value = null
  initialSubmitCount.value = 0
  try {
    const now = new Date()
    const freeTitle = seed?.title?.trim() || `自由写作 ${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
    const session = await startWritingSession({
      mode: 'free',
      title: freeTitle,
      studyStage: currentStage.value,
      sourceType: 'free_input',
      titleSnapshot: freeTitle,
      topicTitle: freeTitle,
    })
    initialDocId.value = session.docId
    initialExistingContent.value = session.existingContent ?? null
    initialSubmitCount.value = session.submitCount ?? 0
    const sessionMetadata = await getWritingSessionMetadata(session.docId).catch((err) => {
      console.warn('[WritingPage] load session metadata failed', err)
      return null
    })
    if (sessionMetadata) {
      console.log('[WritingPage] writing metadata', sessionMetadata)
    }
  } catch (e) {
    console.warn('[WritingPage] create free doc failed', e)
    initialDocId.value = null
    initialExistingContent.value = null
    showToast('创建写作会话失败，请重试', 'error')
    return
  }
  await navigateToPhase('editor')
}

async function createBlankFreeDoc() {
  await createFreeDoc()
}

async function openExamSetupFromModeSelect() {
  resumeTopicForSetup.value = undefined
  resumeMetadataForSetup.value = null
  await navigateToPhase('exam-setup')
}

async function onExamConfirm(info: ExamTopicInfo) {
  resumeTopicForSetup.value = undefined
  chosenMode.value = 'exam'
  const prompt = buildExamTaskPrompt(info)
  examMaxScore.value = info.maxScore ?? 100
  initialTaskPrompt.value = prompt
  initialExistingContent.value = null
  initialSubmitCount.value = 0
  try {
    const session = await startWritingSession({
      mode: 'exam',
      taskPrompt: prompt,
      title: info.topic.slice(0, 100),
      studyStage: currentStage.value,
      sourceType: info.sourceType,
      titleSnapshot: info.topic.slice(0, 100),
      topicTitle: info.topic,
      promptText: prompt,
      promptSheetId: info.promptSheetId ?? null,
      attachmentImageUrl: info.attachmentImageUrl ?? null,
      genre: info.genre ?? undefined,
      examType: info.examType,
      taskType: info.taskType,
      minWords: info.minWords,
      recommendedMaxWords: info.recommendedMaxWords,
      maxScore: info.maxScore,
    })
    initialDocId.value = session.docId
    initialExistingContent.value = session.existingContent ?? null
    initialSubmitCount.value = session.submitCount ?? 0
    const sessionMetadata = await getWritingSessionMetadata(session.docId).catch((err) => {
      console.warn('[WritingPage] load session metadata failed', err)
      return null
    })
    if (sessionMetadata) {
      console.log('[WritingPage] writing metadata', sessionMetadata)
    }
  } catch (e) {
    console.warn('[WritingPage] create exam doc failed', e)
    initialDocId.value = null
    initialExistingContent.value = null
    showToast('创建考试写作会话失败，请重试', 'error')
    return
  }
  await navigateToPhase('editor')
}

async function openDocument(doc: WritingDocumentItem) {
  chosenMode.value = doc.taskPrompt ? 'exam' : 'free'
  initialTaskPrompt.value = doc.taskPrompt ?? undefined
  initialDocId.value = doc.docId
  initialExistingContent.value = null
  examMaxScore.value = null
  initialSubmitCount.value = doc.submitCount ?? 0

  // 考试模式草稿（status=0，从题目设置页保存退出，未点击"开始写作"）→ 回到题目设置页
  if (doc.taskPrompt && doc.status === 0) {
    const metadata = await getWritingSessionMetadata(doc.docId).catch((err) => {
      console.warn('[WritingPage] load setup draft metadata failed', err)
      return null
    })
    resumeMetadataForSetup.value = metadata
    resumeTopicForSetup.value = metadata?.topicTitle?.trim() || doc.title?.trim() || undefined
    void navigateToPhase('exam-setup')
    return
  }

  await navigateToPhase('editor')
}

async function onExamSetupBack() {
  resumeTopicForSetup.value = undefined
  resumeMetadataForSetup.value = null
  await navigateToPhase('doc-list')
}

async function onExamSetupSwitchMode(payload: { mode: 'free' | 'exam'; info?: ExamTopicInfo | null }) {
  if (payload.mode === 'free') {
    resumeTopicForSetup.value = undefined
    resumeMetadataForSetup.value = null
    const seedInfo = payload.info ?? null
    await createFreeDoc(seedInfo ? {
      title: seedInfo.topic.slice(0, 100),
      initialTaskPrompt: buildExamTaskPrompt(seedInfo),
    } : undefined)
  }
}

async function onEditorBack() {
  initialDocId.value = null
  initialExistingContent.value = null
  initialTaskPrompt.value = undefined
  examMaxScore.value = null
  await navigateToPhase('doc-list')
}

async function onExamSaveDraft() {
  resumeTopicForSetup.value = undefined
  resumeMetadataForSetup.value = null
  await navigateToPhase('doc-list')
}

// ── Card menu ──
const openMenuId = ref<string | null>(null)

function toggleMenu(docId: string) {
  openMenuId.value = openMenuId.value === docId ? null : docId
}

// Close menu on outside click (auto-cleanup by useEventListener)
useEventListener(document, 'click', () => { openMenuId.value = null })

// ── Rename ──
const renameDialog = ref({ visible: false, docId: '', title: '' })

function startRename(doc: WritingDocumentItem) {
  openMenuId.value = null
  renameDialog.value = { visible: true, docId: doc.docId, title: doc.title || '' }
}

async function doRename() {
  const { docId, title } = renameDialog.value
  if (!title.trim()) return
  try {
    await renameDocument(docId, title.trim())
    const item = docList.value.find(d => d.docId === docId)
    if (item) item.title = title.trim()
  } catch (e) {
    console.warn('[WritingPage] rename failed', e)
  }
  renameDialog.value.visible = false
}

// ── Delete ──
const deleteDialog = ref({ visible: false, docId: '', title: '' })

function confirmDelete(doc: WritingDocumentItem) {
  openMenuId.value = null
  deleteDialog.value = { visible: true, docId: doc.docId, title: doc.title || '未命名作文' }
}

async function doDelete() {
  const { docId } = deleteDialog.value
  try {
    await deleteDocument(docId)
    docList.value = docList.value.filter(d => d.docId !== docId)
  } catch (e) {
    console.warn('[WritingPage] delete failed', e)
  }
  deleteDialog.value.visible = false
}

function scoreColor(score: number) {
  if (score >= 80) return 'high'
  if (score >= 60) return 'mid'
  return 'low'
}

function formatTrendMetric(value: number | null) {
  if (value == null || !Number.isFinite(value)) return '--'
  return Math.round(value).toString()
}

function formatAssetMetric(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '--'
  return Math.round(value).toLocaleString('zh-CN')
}

function formatAssetAverage(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '--'
  return Number.isInteger(value) ? value.toFixed(0) : value.toFixed(1)
}

function formatSignedAssetMetric(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '--'
  const rounded = Math.round(value)
  const sign = rounded > 0 ? '+' : ''
  return `${sign}${rounded.toLocaleString('zh-CN')}`
}

function formatSignedPercent(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '--'
  const rounded = Math.round(value)
  const sign = rounded > 0 ? '+' : ''
  return `${sign}${rounded}%`
}

function assetWordWidth(wordCount: number) {
  if (!Number.isFinite(wordCount) || wordCount <= 0) return 10
  if (!assetMaxWordCount.value || assetMaxWordCount.value <= 0) return 10
  return Math.max(10, Math.round((wordCount / assetMaxWordCount.value) * 100))
}

function assetMiniHeights(period: { wordCount: number; sentenceCount: number; essayCount: number }) {
  const values = [period.essayCount, period.sentenceCount, period.wordCount / 40, period.wordCount / 24]
  const max = Math.max(...values, 1)
  return values.map((value) => Math.max(8, Math.round((value / max) * 28)))
}

function assetPeriodFootnote(period: { wordCount: number; essayCount: number }) {
  if (!period.essayCount) return '本期暂无作文'
  const average = period.wordCount / period.essayCount
  return `篇均 ${formatAssetMetric(average)} 词`
}

function formatTrendDelta(value: number | null) {
  if (value == null || !Number.isFinite(value)) return '--'
  const rounded = Math.round(value * 10) / 10
  return `${rounded > 0 ? '+' : ''}${Number.isInteger(rounded) ? rounded.toFixed(0) : rounded.toFixed(1)}`
}

function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diffMin = Math.floor((now.getTime() - d.getTime()) / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) return `${diffDay} 天前`
  return d.toLocaleDateString('zh-CN')
}
</script>

<style src="@/styles/gate.css" />
<style scoped>
/* ── Hub page ── */
.hub-page {
  min-height: 100%;
  background: #f3f4f6;
  padding: 28px 24px 48px;
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
}

.hub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.hub-title {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.new-doc-btn {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.new-doc-btn:hover { background: #065f46; }

/* ── Stats grid ── */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  line-height: 1.1;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}

/* ── Analytics panel ── */
.analytics-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 18px 20px 18px;
  margin-bottom: 24px;
}

.analytics-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.analytics-tab-group,
.analytics-segmented,
.analytics-range-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.analytics-tab,
.analytics-chip {
  height: 36px;
  padding: 0 14px;
  border: 1px solid #dbe3ea;
  border-radius: 999px;
  background: #fff;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.analytics-tab.active,
.analytics-chip.active {
  background: #ecfdf5;
  border-color: #a7f3d0;
  color: #047857;
  font-weight: 600;
}

.analytics-tab:hover,
.analytics-chip:hover {
  border-color: #047857;
  color: #047857;
}

.analytics-subheader {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.analytics-title {
  margin: 0;
  font-size: 20px;
  line-height: 1.2;
  color: #0f172a;
}

.analytics-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.analytics-chart-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.2;
  color: #0f172a;
}

.analytics-subheader--chart {
  margin-top: 8px;
}

.analytics-custom-input {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #dbe3ea;
  border-radius: 999px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
}

.analytics-custom-input input {
  width: 56px;
  border: none;
  outline: none;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  background: transparent;
}

.analytics-chart {
  width: 100%;
  height: 320px;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  padding: 8px;
}

.analytics-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  color: #9ca3af;
  font-size: 13px;
  text-align: center;
  padding: 20px;
}

.analytics-placeholder--large {
  min-height: 320px;
}

.analytics-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.analytics-metrics--asset {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 0;
  margin-bottom: 18px;
}

.analytics-footer-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
}

.analytics-dots {
  display: flex;
  align-items: center;
  gap: 8px;
}

.analytics-dot {
  width: 8px;
  height: 8px;
  border: none;
  border-radius: 999px;
  padding: 0;
  background: #d1d5db;
  cursor: pointer;
  transition: all 0.2s;
}

.analytics-dot.active {
  width: 22px;
  background: #047857;
}

.analytics-footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.analytics-arrow {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbe3ea;
  border-radius: 10px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}

.analytics-arrow:hover:not(:disabled) {
  border-color: #047857;
  color: #047857;
  background: #ecfdf5;
}

.analytics-arrow:disabled {
  opacity: 0.4;
  cursor: default;
}

.analytics-metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 108px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fff;
}

.analytics-metric-card--asset {
  min-height: 96px;
  background: linear-gradient(180deg, #f0fdf9 0%, #ffffff 100%);
}

.analytics-metric-label {
  font-size: 13px;
  color: #64748b;
}

.analytics-metric-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
  color: #0f172a;
}

.analytics-metric-value.up {
  color: #047857;
}

.asset-board {
  border: 1px solid #eef2f7;
  border-radius: 20px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  overflow: hidden;
}

.asset-board-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid #eef2f7;
}

.asset-board-title {
  margin: 0;
  font-size: 20px;
  line-height: 1.2;
  color: #0f172a;
}

.asset-board-summary {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, 0.95fr);
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid #eef2f7;
}

.asset-hero-card {
  border: 1px solid #dff3ea;
  border-radius: 20px;
  background: linear-gradient(135deg, #f0fdf9 0%, #ffffff 100%);
  padding: 18px 18px 16px;
}

.asset-hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.asset-card-label,
.asset-meta-label,
.asset-period-label,
.asset-period-footnote,
.asset-side-note,
.asset-periods-subtitle {
  color: #64748b;
}

.asset-card-label,
.asset-meta-label {
  font-size: 12px;
}

.asset-hero-value {
  margin-top: 12px;
  font-size: 44px;
  line-height: 0.95;
  font-weight: 800;
  color: #0f172a;
}

.asset-hero-unit {
  margin-top: 10px;
  font-size: 15px;
  color: #64748b;
}

.asset-hero-period {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 13px;
  font-weight: 700;
}

.asset-hero-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #d1fae5;
}

.asset-hero-meta-item strong {
  display: block;
  margin-top: 6px;
  font-size: 24px;
  line-height: 1;
  color: #0f172a;
}

.asset-side-cards {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.asset-side-card {
  border: 1px solid #eef2f7;
  border-radius: 18px;
  padding: 18px;
  background: #fff;
}

.asset-side-value-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.asset-side-value-row strong {
  font-size: 30px;
  line-height: 1;
  color: #0f172a;
}

.asset-side-trend {
  font-size: 14px;
  font-weight: 700;
  color: #047857;
}

.asset-side-trend.down {
  color: #b45309;
}

.asset-side-trend.neutral {
  color: #2563eb;
}

.asset-side-note {
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 1.65;
}

.asset-periods-section {
  padding: 18px 20px 20px;
}

.asset-periods-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.asset-periods-title {
  margin: 0;
  font-size: 17px;
  line-height: 1.2;
  color: #0f172a;
}

.asset-period-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.asset-period-card {
  border: 1px solid #eef2f7;
  border-radius: 18px;
  background: #fff;
  padding: 16px;
}

.asset-period-card.active {
  border-color: #a7f3d0;
  box-shadow: 0 10px 24px rgba(16, 185, 129, 0.08);
  background: linear-gradient(180deg, #f0fdf9 0%, #ffffff 100%);
}

.asset-period-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.asset-period-name {
  font-size: 16px;
  line-height: 1.1;
  color: #0f172a;
}

.asset-period-tag {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
}

.asset-period-tag.active {
  background: #ecfdf5;
  color: #047857;
}

.asset-period-value {
  font-size: 38px;
  line-height: 1;
  font-weight: 800;
  color: #0f172a;
}

.asset-period-label {
  margin-top: 8px;
  font-size: 14px;
}

.asset-period-progress {
  margin-top: 16px;
}

.asset-period-progress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #64748b;
}

.asset-progress-track {
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: #ecfdf5;
}

.asset-progress-track span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #34d399 0%, #10b981 100%);
}

.asset-period-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}

.asset-mini-bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 34px;
}

.asset-mini-bars span {
  width: 8px;
  border-radius: 6px 6px 3px 3px;
  background: #86efac;
}

.asset-period-footnote {
  font-size: 12px;
}

/* ── Search bar ── */
.search-bar {
  position: relative;
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.search-icon {
  position: absolute;
  left: 14px;
  color: #9ca3af;
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 10px 36px 10px 40px;
  font-size: 14px;
  color: #111827;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.search-input:focus {
  border-color: #047857;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.08);
}
.search-input::placeholder { color: #9ca3af; }

.search-clear {
  position: absolute;
  right: 8px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #9ca3af;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  line-height: 1;
}
.search-clear:hover { color: #374151; background: #f3f4f6; }

/* ── Document section ── */
.doc-section {
  margin-top: 4px;
}

.doc-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.doc-section-title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.doc-filters {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-pills {
  display: flex;
  gap: 4px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 2px;
}

.filter-pill {
  padding: 4px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.filter-pill.active {
  background: #ecfdf5;
  color: #047857;
  font-weight: 600;
}
.filter-pill:hover:not(.active) {
  background: #f9fafb;
}

.sort-select {
  padding: 5px 10px;
  font-size: 13px;
  color: #6b7280;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  outline: none;
}
.sort-select:focus { border-color: #047857; }

/* ── Document grid ── */
.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.doc-card {
  display: flex;
  flex-direction: column;
  min-height: 180px;
  padding: 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.12s;
}
.doc-card:hover {
  border-color: #047857;
  box-shadow: 0 4px 16px rgba(4, 120, 87, 0.08);
  transform: translateY(-2px);
}

.doc-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  position: relative;
}

.doc-menu-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  border-radius: 6px;
  color: #9ca3af;
  cursor: pointer;
  transition: all 0.15s;
  opacity: 0;
}
.doc-card:hover .doc-menu-btn { opacity: 1; }
.doc-menu-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.doc-menu {
  position: absolute;
  top: 32px;
  right: 0;
  z-index: 20;
  min-width: 130px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.10);
  padding: 4px;
  animation: menuIn 0.12s ease;
}
@keyframes menuIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.doc-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  color: #374151;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s;
}
.doc-menu-item:hover { background: #f3f4f6; }
.doc-menu-danger { color: #dc2626; }
.doc-menu-danger:hover { background: #fef2f2; }

.doc-mode-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}
.doc-mode-tag.exam { background: #fef3c7; color: #92400e; }
.doc-mode-tag.free { background: #dbeafe; color: #1e40af; }

.doc-submit-count {
  font-size: 11px;
  color: #9ca3af;
}

.doc-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-card-prompt {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.4;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-card-score-area {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: auto;
  margin-bottom: 12px;
}

.doc-score-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}
.doc-score-num.high { color: #047857; }
.doc-score-num.mid { color: #d97706; }
.doc-score-num.low { color: #dc2626; }

.doc-score-max {
  font-size: 13px;
  color: #9ca3af;
}

.doc-score-delta {
  font-size: 12px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  margin-left: 4px;
}
.doc-score-delta.up { background: #ecfdf5; color: #047857; }
.doc-score-delta.down { background: #fef2f2; color: #dc2626; }

.doc-score-none {
  font-size: 14px;
  color: #9ca3af;
}

.doc-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

.doc-card-time {
  font-size: 12px;
  color: #9ca3af;
}

.doc-card-action {
  font-size: 12px;
  color: #047857;
  font-weight: 500;
  opacity: 0;
  transition: opacity 0.15s;
}
.doc-card:hover .doc-card-action { opacity: 1; }

/* ── Dialogs ── */
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
}

.confirm-close {
  position: absolute;
  top: 12px;
  right: 14px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #9ca3af;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  line-height: 1;
}
.confirm-close:hover { color: #374151; background: #f3f4f6; }

.rename-dialog {
  position: relative;
  width: 90%;
  max-width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 28px 24px 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.rename-title {
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 16px;
}

.rename-input {
  width: 100%;
  padding: 10px 14px;
  font-size: 14px;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
}
.rename-input:focus { border-color: #047857; box-shadow: 0 0 0 3px rgba(4,120,87,0.1); }

.rename-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.btn-cancel {
  padding: 8px 18px;
  font-size: 14px;
  color: #6b7280;
  background: none;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  cursor: pointer;
}
.btn-cancel:hover { border-color: #9ca3af; color: #374151; }

.gate-btn--danger { background: #dc2626; }
.gate-btn--danger:hover { background: #b91c1c; }

.delete-hint {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 4px;
  line-height: 1.5;
}

/* ── Pagination ── */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  margin-top: 24px;
}

.page-btn {
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.page-btn:hover:not(:disabled):not(.active) {
  border-color: #047857;
  color: #047857;
}
.page-btn.active {
  background: #047857;
  border-color: #047857;
  color: #fff;
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: default;
}
.page-btn.ellipsis {
  border: none;
  background: none;
  cursor: default;
}

/* ── Empty state ── */
.doc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64px 20px;
}

.empty-icon { font-size: 48px; margin: 0 0 12px; }
.empty-text { font-size: 16px; font-weight: 600; color: #374151; margin: 0 0 4px; }
.empty-hint { font-size: 13px; color: #9ca3af; margin: 0; }

/* ── Mode select ── */
.mode-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  max-width: 520px;
  width: 100%;
}

.mode-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 20px;
  border: 2px solid #e5e7eb;
  border-radius: 14px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.12s;
}
.mode-card:hover {
  border-color: #047857;
  box-shadow: 0 4px 16px rgba(4, 120, 87, 0.10);
  transform: translateY(-2px);
}

.mode-icon { font-size: 32px; line-height: 1; }
.mode-name { font-size: 17px; font-weight: 700; color: #111827; }
.mode-desc { font-size: 13px; color: #6b7280; text-align: center; line-height: 1.4; }

.back-link {
  margin-top: 24px;
  font-size: 14px;
  color: #6b7280;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.15s;
}
.back-link:hover { color: #047857; }

/* ── Responsive ── */
@media (max-width: 768px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .analytics-panel-header,
  .analytics-subheader { flex-direction: column; align-items: flex-start; }
  .analytics-chart,
  .analytics-placeholder--large { min-height: 280px; height: 280px; }
  .analytics-metrics--asset { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .asset-board-summary,
  .asset-period-grid { grid-template-columns: 1fr; }
  .asset-hero-meta { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 560px) {
  .hub-header { flex-direction: column; align-items: flex-start; gap: 12px; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .analytics-metrics { grid-template-columns: 1fr; }
  .analytics-metrics--asset { grid-template-columns: 1fr; }
  .analytics-footer-nav { width: 100%; }
  .asset-board-header,
  .asset-period-top,
  .asset-side-value-row,
  .asset-period-footer { flex-direction: column; align-items: flex-start; }
  .asset-hero-meta { grid-template-columns: 1fr; }
  .mode-grid { grid-template-columns: 1fr; }
  .doc-grid { grid-template-columns: 1fr; }
  .doc-section-header { flex-direction: column; align-items: flex-start; }
}
</style>












