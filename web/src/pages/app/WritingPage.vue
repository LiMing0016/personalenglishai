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

    <!-- Analytics carousel -->
    <div class="analytics-carousel">
      <div class="carousel-header">
        <span class="carousel-title">{{ ['得分趋势', '能力雷达', '错误分析'][carouselIndex] }}</span>
        <div class="carousel-nav">
          <button class="carousel-arrow" :disabled="carouselIndex <= 0" @click="carouselIndex--">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <span class="carousel-indicator">{{ carouselIndex + 1 }} / 3</span>
          <button class="carousel-arrow" :disabled="carouselIndex >= 2" @click="carouselIndex++">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
        </div>
      </div>
      <div class="carousel-viewport">
        <div class="carousel-track" :style="{ transform: `translateX(-${carouselIndex * 100}%)` }">
          <!-- Panel 1: 得分趋势 -->
          <div class="carousel-slide">
            <div v-if="scoredDocs.length >= 3" ref="chartRef" class="carousel-chart" />
            <div v-else class="carousel-placeholder">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
              <span>完成 3 次以上评分后展示趋势图</span>
            </div>
          </div>
          <!-- Panel 2: 能力雷达 -->
          <div class="carousel-slide">
            <div v-if="hasRadarData" ref="radarRef" class="carousel-chart" />
            <div v-else class="carousel-placeholder">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5"><polygon points="12 2 22 8.5 22 15.5 12 22 2 15.5 2 8.5"/></svg>
              <span>完成评分后展示六维能力雷达图</span>
            </div>
          </div>
          <!-- Panel 3: 错误分析 -->
          <div class="carousel-slide">
            <div v-if="hasErrorData" ref="errorRef" class="carousel-chart" />
            <div v-else class="carousel-placeholder">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg>
              <span>完成评分后展示错误类型分布</span>
            </div>
          </div>
        </div>
      </div>
      <div class="carousel-dots">
        <button v-for="i in 3" :key="i" class="carousel-dot" :class="{ active: carouselIndex === i - 1 }" @click="carouselIndex = i - 1" />
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

  <!-- New writing task modal -->
  <div v-else-if="phase === 'mode-select'" class="task-modal-page">
    <div class="task-modal-backdrop" />
    <section class="task-modal" aria-label="新建写作任务">
      <button class="task-modal-close" type="button" title="退出" @click="navigateToPhase('doc-list')">&times;</button>
      <p class="task-modal-kicker">写作设置</p>
      <h2 class="task-modal-title">新建写作任务</h2>

      <div class="task-modal-section">
        <p class="task-modal-label">写作模式</p>
        <div class="task-option-grid task-option-grid--two">
          <button
            class="task-option"
            :class="{ active: newTaskMode === 'free' }"
            type="button"
            @click="newTaskMode = 'free'"
          >
            <span class="task-option-title">自由写作</span>
            <span class="task-option-desc">直接进入空白写作页</span>
          </button>
          <button
            class="task-option"
            :class="{ active: newTaskMode === 'exam' }"
            type="button"
            @click="newTaskMode = 'exam'"
          >
            <span class="task-option-title">考试写作</span>
            <span class="task-option-desc">先确定题目来源</span>
          </button>
        </div>
      </div>

      <div v-if="newTaskMode === 'exam'" class="task-modal-section">
        <p class="task-modal-label">题目来源</p>
        <div class="task-option-grid task-option-grid--two">
          <button
            class="task-option"
            :class="{ active: newTaskSource === 'past_prompt' }"
            type="button"
            @click="newTaskSource = 'past_prompt'"
          >
            <span class="task-option-title">历年真题</span>
            <span class="task-option-desc">进入真题选择页，忠实复现原题</span>
          </button>
          <button
            class="task-option"
            :class="{ active: newTaskSource === 'ai_design' }"
            type="button"
            @click="newTaskSource = 'ai_design'"
          >
            <span class="task-option-title">AI 题目设计</span>
            <span class="task-option-desc">按要求生成原创练习题</span>
          </button>
        </div>
      </div>

      <div v-if="newTaskMode === 'exam' && newTaskSource === 'ai_design'" class="task-modal-section">
        <p class="task-modal-label">AI 题目设计要求</p>
        <div class="task-field-grid">
          <label class="task-field">
            <span>体裁</span>
            <select v-model="newTaskGenre" class="task-select">
              <option :value="null">请选择体裁</option>
              <option v-for="option in newTaskGenreOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="task-field">
            <span>字数</span>
            <div class="task-word-row">
              <select
                v-model="newTaskWordRange"
                class="task-select"
                @change="newTaskCustomWordRange = newTaskWordRange === '__custom__' ? newTaskCustomWordRange : ''"
              >
                <option :value="null">请选择字数</option>
                <option v-for="option in newTaskWordRangeOptions" :key="option" :value="option">{{ option }} 词</option>
                <option value="__custom__">自定义</option>
              </select>
              <input
                v-if="newTaskWordRange === '__custom__'"
                v-model="newTaskCustomWordRange"
                class="task-input"
                type="text"
                inputmode="numeric"
                placeholder="160-200"
              />
            </div>
          </label>
        </div>
      </div>

      <div class="task-modal-actions">
        <button class="task-btn task-btn--secondary" type="button" @click="navigateToPhase('doc-list')">退出</button>
        <button class="task-btn task-btn--primary" type="button" @click="continueNewWritingTask">继续</button>
      </div>
    </section>
  </div>

  <!-- Past prompt select -->
  <div v-else-if="phase === 'past-prompt-select'" class="past-prompt-page">
    <button class="setup-back-link" type="button" @click="navigateToPhase('mode-select')">
      &larr; 返回新建任务
    </button>
    <div class="past-prompt-header">
      <div>
        <p class="past-prompt-kicker">历年真题</p>
        <h2 class="past-prompt-title">选择一套真题开始写作</h2>
      </div>
      <button class="task-btn task-btn--primary" type="button" :disabled="!selectedPastPrompt" @click="startWritingFromPastPrompt">
        使用该真题
      </button>
    </div>

    <div class="past-prompt-toolbar">
      <input
        v-model="pastPromptKeyword"
        class="past-prompt-search"
        type="text"
        placeholder="搜索题目、试卷或关键词"
        @keyup.enter="loadPastPrompts(1)"
      />
      <select v-model="pastPromptYearSelect" class="past-prompt-select" @change="onPastPromptYearChange">
        <option :value="0">全部年份</option>
        <option v-for="year in pastPromptYears" :key="year" :value="year">{{ year }}</option>
      </select>
      <button class="task-btn task-btn--secondary" type="button" @click="loadPastPrompts(1)">搜索</button>
    </div>

    <div v-if="pastPromptLoading" class="past-prompt-empty">
      <div class="gate-spinner" />
      <span>加载真题中...</span>
    </div>
    <div v-else-if="pastPromptItems.length === 0" class="past-prompt-empty">
      <span>暂未找到匹配的真题</span>
    </div>
    <div v-else class="past-prompt-layout">
      <div class="past-prompt-list">
        <button
          v-for="prompt in pastPromptItems"
          :key="prompt.id"
          class="past-prompt-item"
          :class="{ active: selectedPastPrompt?.id === prompt.id }"
          type="button"
          @click="selectedPastPrompt = prompt"
        >
          <span class="past-prompt-item-title">{{ prompt.title || prompt.paper || '未命名真题' }}</span>
          <span class="past-prompt-item-meta">
            {{ prompt.examYear || '年份未知' }} · {{ prompt.paper || '试卷未知' }} · {{ prompt.task || 'Task 未标注' }}
          </span>
          <span class="past-prompt-item-text">{{ prompt.promptText }}</span>
        </button>
      </div>

      <aside class="past-prompt-preview">
        <template v-if="selectedPastPrompt">
          <p class="past-prompt-preview-kicker">{{ selectedPastPrompt.paper || '历年真题' }}</p>
          <h3>{{ selectedPastPrompt.title || '真题预览' }}</h3>
          <div class="past-prompt-preview-meta">
            <span>{{ selectedPastPrompt.examYear || '年份未知' }}</span>
            <span>{{ selectedPastPrompt.task || 'Task 未标注' }}</span>
            <span>{{ formatPastPromptWordRange(selectedPastPrompt) || '字数未标注' }}</span>
          </div>
          <p class="past-prompt-preview-text">{{ selectedPastPrompt.promptText }}</p>
          <div v-if="selectedPastPrompt.imageDescription" class="past-prompt-preview-block">
            <strong>图片/图表信息</strong>
            <p>{{ selectedPastPrompt.imageDescription }}</p>
          </div>
          <div v-if="selectedPastPrompt.materialText" class="past-prompt-preview-block">
            <strong>材料内容</strong>
            <p>{{ selectedPastPrompt.materialText }}</p>
          </div>
          <img
            v-if="selectedPastPrompt.imageUrl"
            class="past-prompt-preview-image"
            :src="selectedPastPrompt.imageUrl"
            alt="真题图片"
          />
        </template>
        <p v-else class="past-prompt-preview-placeholder">从左侧选择一套真题查看题面。</p>
      </aside>
    </div>
  </div>

  <!-- Exam setup -->
  <ExamSetupPage
    v-else-if="phase === 'exam-setup'"
    :initial-topic="resumeTopicForSetup"
    :resume-metadata="resumeMetadataForSetup"
    :study-stage="currentStage ?? ''"
    :initial-genre="examSetupInitialGenre"
    :initial-word-range="examSetupInitialWordRange"
    :initial-tab="examSetupInitialTab"
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
import { LineChart, RadarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  RadarComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart, RadarChart, PieChart,
  GridComponent, TooltipComponent, RadarComponent, LegendComponent,
  CanvasRenderer,
])
import EditorShell from '@/components/writing/EditorShell.vue'
import ExamSetupPage from '@/pages/app/ExamSetupPage.vue'
import type { ExamPromptType, ExamTopicInfo } from '@/pages/app/examPromptHelpers'
import { buildExamTaskPrompt } from '@/pages/app/examPromptHelpers'
import { stageCache } from '@/stores/stageCache'
import { getStageId } from '@/constants/stage'
import { getWritingSessionMetadata, startWritingSession, getWritingDocuments, getWritingStats, getEssayPrompts } from '@/api/writing'
import type { EssayPromptItem, WritingDocumentItem, WritingSessionMetadataResponse, WritingStatsResponse } from '@/api/writing'
import { renameDocument, deleteDocument } from '@/api/document'
import { showToast } from '@/utils/toast'

type Phase = 'loading' | 'doc-list' | 'mode-select' | 'past-prompt-select' | 'exam-setup' | 'editor'
type RoutePhase = Exclude<Phase, 'loading'>
type NewTaskMode = 'free' | 'exam'
type NewTaskSource = 'past_prompt' | 'ai_design'
type ExamSetupInitialTab = 'manual' | 'ai' | 'past'

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
const newTaskMode = ref<NewTaskMode>('free')
const newTaskSource = ref<NewTaskSource>('ai_design')
const newTaskGenre = ref<string | null>(null)
const newTaskWordRange = ref<string | null>(null)
const newTaskCustomWordRange = ref('')
const examSetupInitialGenre = ref<string | null>(null)
const examSetupInitialWordRange = ref<string | null>(null)
const examSetupInitialTab = ref<ExamSetupInitialTab | null>(null)

const newTaskGenreOptions = [
  { value: 'argumentative', label: '议论文' },
  { value: 'material', label: '材料作文' },
  { value: 'chart', label: '图表作文' },
  { value: 'picture', label: '图画作文' },
  { value: 'practical', label: '应用文' },
  { value: 'letter', label: '书信' },
]
const newTaskWordRangeOptions = ['80-100', '100-120', '120-150', '160-200', '160-220', '250']

function resolveRoutePhase(): RoutePhase {
  switch (route.name) {
    case 'WritingModeSelect':
      return 'mode-select'
    case 'WritingPastPromptSelect':
      return 'past-prompt-select'
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
    case 'past-prompt-select':
      return 'WritingPastPromptSelect'
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
  if (!booting.value && p === 'past-prompt-select' && pastPromptItems.value.length === 0 && !pastPromptLoading.value) {
    void loadPastPrompts(1)
  }
}, { immediate: true })

onBeforeUnmount(() => {
  setImmersive(null)
})

// Carousel
const carouselIndex = ref(0)

// Charts
const chartRef = ref<HTMLElement | null>(null)
const radarRef = ref<HTMLElement | null>(null)
const errorRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null
let radarInstance: echarts.ECharts | null = null
let errorInstance: echarts.ECharts | null = null

// Stats data
const writingStats = ref<WritingStatsResponse | null>(null)

const hasRadarData = computed(() => {
  const s = writingStats.value
  return s && (s.avgContentQuality != null || s.avgTaskAchievement != null || s.avgStructureScore != null || s.avgVocabularyScore != null || s.avgGrammarScore != null || s.avgExpressionScore != null)
})

const hasErrorData = computed(() => {
  const s = writingStats.value
  return s && (s.totalGrammarErrors > 0 || s.totalSpellingErrors > 0 || s.totalVocabularyErrors > 0)
})

watch([() => scoredDocs.value, chartRef, carouselIndex], async () => {
  await nextTick()
  if (carouselIndex.value === 0 && scoredDocs.value.length >= 3 && chartRef.value) {
    renderChart()
  }
}, { immediate: true })

watch([hasRadarData, radarRef, carouselIndex], async () => {
  await nextTick()
  if (carouselIndex.value === 1 && hasRadarData.value && radarRef.value) {
    renderRadarChart()
  }
}, { immediate: true })

watch([hasErrorData, errorRef, carouselIndex], async () => {
  await nextTick()
  if (carouselIndex.value === 2 && hasErrorData.value && errorRef.value) {
    renderErrorChart()
  }
}, { immediate: true })

function renderChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  const sorted = [...scoredDocs.value].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
  chartInstance.setOption({
    grid: { top: 10, right: 16, bottom: 24, left: 36 },
    xAxis: {
      type: 'category',
      data: sorted.map((_, i) => `#${i + 1}`),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    series: [{
      type: 'line',
      data: sorted.map(d => d.latestScore),
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#047857', width: 2 },
      itemStyle: { color: '#047857' },
      areaStyle: { color: 'rgba(4, 120, 87, 0.08)' },
    }],
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => `第${params[0].dataIndex + 1}篇：${params[0].value}分`,
    },
  })
}

function renderRadarChart() {
  if (!radarRef.value) return
  if (radarInstance) radarInstance.dispose()
  radarInstance = echarts.init(radarRef.value)
  const s = writingStats.value!
  radarInstance.setOption({
    radar: {
      indicator: [
        { name: '内容质量', max: 100 },
        { name: '任务完成', max: 100 },
        { name: '篇章结构', max: 100 },
        { name: '词汇运用', max: 100 },
        { name: '语法准确', max: 100 },
        { name: '语言表达', max: 100 },
      ],
      shape: 'polygon',
      splitNumber: 4,
      axisName: { color: '#6b7280', fontSize: 11 },
      splitLine: { lineStyle: { color: '#e5e7eb' } },
      splitArea: { areaStyle: { color: ['#fff', '#f9fafb', '#f3f4f6', '#e5e7eb'] } },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          s.avgContentQuality ?? 0,
          s.avgTaskAchievement ?? 0,
          s.avgStructureScore ?? 0,
          s.avgVocabularyScore ?? 0,
          s.avgGrammarScore ?? 0,
          s.avgExpressionScore ?? 0,
        ],
        areaStyle: { color: 'rgba(4, 120, 87, 0.15)' },
        lineStyle: { color: '#047857', width: 2 },
        itemStyle: { color: '#047857' },
        symbol: 'circle',
        symbolSize: 5,
      }],
    }],
    tooltip: {
      trigger: 'item',
    },
  })
}

function renderErrorChart() {
  if (!errorRef.value) return
  if (errorInstance) errorInstance.dispose()
  errorInstance = echarts.init(errorRef.value)
  const s = writingStats.value!
  const data = [
    { value: s.totalGrammarErrors, name: '语法错误', itemStyle: { color: '#ef4444' } },
    { value: s.totalSpellingErrors, name: '拼写错误', itemStyle: { color: '#f59e0b' } },
    { value: s.totalVocabularyErrors, name: '词汇错误', itemStyle: { color: '#6366f1' } },
  ].filter(d => d.value > 0)
  errorInstance.setOption({
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '55%'],
      avoidLabelOverlap: true,
      label: {
        formatter: '{b}\n{c}次 ({d}%)',
        fontSize: 11,
        color: '#374151',
        lineHeight: 16,
      },
      emphasis: {
        label: { fontSize: 13, fontWeight: 'bold' },
      },
      data,
    }],
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}次 ({d}%)',
    },
  })
}

onBeforeUnmount(() => {
  if (chartInstance) { chartInstance.dispose(); chartInstance = null }
  if (radarInstance) { radarInstance.dispose(); radarInstance = null }
  if (errorInstance) { errorInstance.dispose(); errorInstance = null }
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

async function loadDocList() {
  docListLoading.value = true
  try {
    const [docRes, statsRes] = await Promise.all([
      getWritingDocuments(0, 200),
      getWritingStats().catch(() => null),
    ])
    docList.value = docRes.items ?? []
    writingStats.value = statsRes
  } catch (e) {
    console.warn('[WritingPage] loadDocList failed', e)
  } finally {
    docListLoading.value = false
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

function getNewTaskEffectiveWordRange() {
  if (newTaskWordRange.value === '__custom__') {
    return newTaskCustomWordRange.value.trim() || null
  }
  return newTaskWordRange.value?.trim() || null
}

async function continueNewWritingTask() {
  if (newTaskMode.value === 'free') {
    examSetupInitialGenre.value = null
    examSetupInitialWordRange.value = null
    examSetupInitialTab.value = null
    await createBlankFreeDoc()
    return
  }
  if (newTaskSource.value === 'past_prompt') {
    examSetupInitialGenre.value = null
    examSetupInitialWordRange.value = null
    examSetupInitialTab.value = null
    await navigateToPhase('past-prompt-select')
    return
  }
  const effectiveWordRange = getNewTaskEffectiveWordRange()
  if (!newTaskGenre.value) {
    showToast('请先选择体裁', 'error')
    return
  }
  if (!effectiveWordRange) {
    showToast('请先选择字数', 'error')
    return
  }
  examSetupInitialGenre.value = newTaskGenre.value
  examSetupInitialWordRange.value = effectiveWordRange
  examSetupInitialTab.value = 'ai'
  await openExamSetupFromModeSelect()
}

async function openExamSetupFromModeSelect() {
  resumeTopicForSetup.value = undefined
  resumeMetadataForSetup.value = null
  await navigateToPhase('exam-setup')
}

const pastPromptKeyword = ref('')
const pastPromptYear = ref<number | null>(null)
const pastPromptYearSelect = ref(0)
const pastPromptItems = ref<EssayPromptItem[]>([])
const pastPromptYears = ref<number[]>([])
const pastPromptLoading = ref(false)
const selectedPastPrompt = ref<EssayPromptItem | null>(null)

function onPastPromptYearChange() {
  pastPromptYear.value = pastPromptYearSelect.value === 0 ? null : pastPromptYearSelect.value
  selectedPastPrompt.value = null
  void loadPastPrompts(1)
}

async function loadPastPrompts(page = 1) {
  pastPromptLoading.value = true
  try {
    const res = await getEssayPrompts({
      stageId: getStageId(currentStage.value),
      keyword: pastPromptKeyword.value.trim() || undefined,
      year: pastPromptYear.value ?? undefined,
      page,
      size: 12,
    })
    pastPromptItems.value = res.items
    if (res.years.length > 0) {
      pastPromptYears.value = res.years
    }
    if (selectedPastPrompt.value && !res.items.some((item) => item.id === selectedPastPrompt.value?.id)) {
      selectedPastPrompt.value = null
    }
  } catch (e) {
    console.warn('[WritingPage] load past prompts failed', e)
    showToast('加载历年真题失败，请稍后重试', 'error')
  } finally {
    pastPromptLoading.value = false
  }
}

function formatPastPromptWordRange(prompt: EssayPromptItem) {
  if (prompt.wordCountMin != null && prompt.wordCountMax != null) {
    return `${prompt.wordCountMin}-${prompt.wordCountMax} 词`
  }
  if (prompt.wordCountMin != null) {
    return `${prompt.wordCountMin}+ 词`
  }
  return ''
}

function buildPastPromptTopicInfo(prompt: EssayPromptItem): ExamTopicInfo {
  const wordRange = formatPastPromptWordRange(prompt).replace(/\s*词$/, '') || null
  const promptType: ExamPromptType = prompt.materialText?.trim()
    ? 'material'
    : prompt.imageUrl?.trim() || prompt.imageDescription?.trim()
      ? 'comic'
      : 'general'
  const promptTitle = prompt.title?.trim() || prompt.paper?.trim() || prompt.promptText.trim()
  return {
    paper: prompt.paper?.trim() || null,
    promptSheetId: null,
    topic: promptTitle,
    genre: null,
    wordRange,
    requirements: prompt.promptText.trim(),
    imageDescription: prompt.imageDescription?.trim() || null,
    materialText: prompt.materialText?.trim() || null,
    attachmentImageUrl: prompt.imageUrl?.trim() || null,
    maxScore: prompt.maxScore ?? 100,
    sourceType: 'past_prompt',
    examType: currentStage.value,
    taskType: prompt.task ?? null,
    minWords: prompt.wordCountMin ?? null,
    recommendedMaxWords: prompt.wordCountMax ?? null,
    promptType,
    chartSpec: null,
    comicScenes: [],
  }
}

async function startWritingFromPastPrompt() {
  if (!selectedPastPrompt.value) {
    showToast('请先选择一套历年真题', 'error')
    return
  }
  await onExamConfirm(buildPastPromptTopicInfo(selectedPastPrompt.value))
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
  examSetupInitialGenre.value = null
  examSetupInitialWordRange.value = null
  examSetupInitialTab.value = null
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
  examSetupInitialGenre.value = null
  examSetupInitialWordRange.value = null
  examSetupInitialTab.value = null
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

/* ── Analytics carousel ── */
.analytics-carousel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 18px 20px 14px;
  margin-bottom: 24px;
}

.carousel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.carousel-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.carousel-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.carousel-indicator {
  font-size: 12px;
  color: #9ca3af;
  min-width: 28px;
  text-align: center;
}

.carousel-arrow {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  color: #374151;
  cursor: pointer;
  transition: all 0.15s;
}
.carousel-arrow:hover:not(:disabled) {
  border-color: #047857;
  color: #047857;
  background: #ecfdf5;
}
.carousel-arrow:disabled {
  opacity: 0.25;
  cursor: default;
}

.carousel-viewport {
  overflow: hidden;
  width: 100%;
}

.carousel-track {
  display: flex;
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.carousel-slide {
  min-width: 100%;
  flex-shrink: 0;
}

.carousel-chart {
  width: 100%;
  height: 170px;
}

.carousel-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 140px;
  color: #9ca3af;
  font-size: 13px;
}

.carousel-dots {
  display: flex;
  justify-content: center;
  gap: 6px;
  padding-top: 8px;
}

.carousel-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  border: none;
  background: #d1d5db;
  cursor: pointer;
  padding: 0;
  transition: all 0.2s;
}
.carousel-dot.active {
  background: #047857;
  width: 16px;
  border-radius: 3px;
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

/* ── New task setup ── */
.task-modal-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: #f3f4f6;
}

.task-modal-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.28);
}

.task-modal {
  position: relative;
  z-index: 1;
  width: min(880px, calc(100vw - 32px));
  padding: 32px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.20);
}

.task-modal-close {
  position: absolute;
  top: 20px;
  right: 22px;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 32px;
  line-height: 1;
  cursor: pointer;
}
.task-modal-close:hover { color: #334155; }

.task-modal-kicker {
  margin: 0 0 8px;
  color: #047857;
  font-size: 13px;
  font-weight: 800;
}

.task-modal-title {
  margin: 0 0 28px;
  color: #0f172a;
  font-size: 28px;
  font-weight: 900;
}

.task-modal-section {
  padding: 22px 0;
  border-top: 1px solid #e5e7eb;
}

.task-modal-label {
  margin: 0 0 12px;
  color: #334155;
  font-size: 15px;
  font-weight: 800;
}

.task-option-grid {
  display: grid;
  gap: 14px;
}

.task-option-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.task-option {
  min-height: 86px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 8px;
  padding: 18px 20px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}
.task-option:hover {
  border-color: #10b981;
}
.task-option.active {
  border-color: #10b981;
  background: #ecfdf5;
  box-shadow: inset 0 0 0 1px rgba(16, 185, 129, 0.20);
}

.task-option-title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.task-option-desc {
  color: #64748b;
  font-size: 13px;
  line-height: 1.45;
}

.task-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.task-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #334155;
  font-size: 14px;
  font-weight: 800;
}

.task-select,
.task-input {
  width: 100%;
  height: 48px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
}

.task-word-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 10px;
}

.task-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 26px;
  border-top: 1px solid #e5e7eb;
}

.task-btn {
  min-width: 116px;
  height: 44px;
  padding: 0 22px;
  border-radius: 8px;
  border: 1px solid transparent;
  font-size: 15px;
  font-weight: 800;
  cursor: pointer;
  transition: opacity 0.15s, background 0.15s, border-color 0.15s;
}
.task-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.task-btn--secondary {
  background: #fff;
  color: #475569;
  border-color: #cbd5e1;
}
.task-btn--secondary:hover:not(:disabled) {
  border-color: #94a3b8;
}
.task-btn--primary {
  background: #047857;
  color: #fff;
}
.task-btn--primary:hover:not(:disabled) {
  background: #065f46;
}

/* ── Past prompts ── */
.past-prompt-page {
  min-height: 100vh;
  padding: 32px min(5vw, 72px);
  background: #f8fafc;
}

.setup-back-link {
  border: none;
  background: transparent;
  color: #047857;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.past-prompt-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-top: 28px;
  margin-bottom: 20px;
}

.past-prompt-kicker {
  margin: 0 0 6px;
  color: #047857;
  font-size: 13px;
  font-weight: 800;
}

.past-prompt-title {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
  font-weight: 900;
}

.past-prompt-toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 180px auto;
  gap: 12px;
  margin-bottom: 18px;
}

.past-prompt-search,
.past-prompt-select {
  height: 44px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
}

.past-prompt-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.7fr);
  gap: 18px;
  align-items: start;
}

.past-prompt-list,
.past-prompt-preview,
.past-prompt-empty {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.past-prompt-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.past-prompt-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.past-prompt-item:hover {
  border-color: #10b981;
}
.past-prompt-item.active {
  border-color: #10b981;
  background: #ecfdf5;
}

.past-prompt-item-title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.past-prompt-item-meta {
  color: #64748b;
  font-size: 13px;
}

.past-prompt-item-text {
  display: -webkit-box;
  overflow: hidden;
  color: #334155;
  font-size: 14px;
  line-height: 1.55;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.past-prompt-preview {
  position: sticky;
  top: 24px;
  padding: 22px;
}

.past-prompt-preview h3 {
  margin: 0 0 12px;
  color: #0f172a;
  font-size: 22px;
  line-height: 1.25;
}

.past-prompt-preview-kicker {
  margin: 0 0 8px;
  color: #047857;
  font-size: 13px;
  font-weight: 800;
}

.past-prompt-preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.past-prompt-preview-meta span {
  padding: 5px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.past-prompt-preview-text,
.past-prompt-preview-block p {
  color: #334155;
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.past-prompt-preview-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.past-prompt-preview-block strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
}

.past-prompt-preview-image {
  width: 100%;
  margin-top: 16px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.past-prompt-preview-placeholder {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.past-prompt-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 240px;
  color: #64748b;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .task-modal { padding: 26px 20px; }
  .task-option-grid--two,
  .task-field-grid,
  .past-prompt-layout,
  .past-prompt-toolbar {
    grid-template-columns: 1fr;
  }
  .task-word-row {
    grid-template-columns: 1fr;
  }
  .past-prompt-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .past-prompt-preview {
    position: static;
  }
}

@media (max-width: 560px) {
  .hub-header { flex-direction: column; align-items: flex-start; gap: 12px; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .mode-grid { grid-template-columns: 1fr; }
  .doc-grid { grid-template-columns: 1fr; }
  .doc-section-header { flex-direction: column; align-items: flex-start; }
  .task-modal-page { align-items: flex-start; }
  .task-modal-actions {
    flex-direction: column-reverse;
  }
  .task-btn {
    width: 100%;
  }
}
</style>












