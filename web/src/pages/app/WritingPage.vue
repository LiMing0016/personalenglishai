<template>
  <!-- Loading (defensive check) -->
  <div v-if="phase === 'loading'" class="gate-center">
    <div class="gate-spinner" />
    <p class="gate-hint">加载中…</p>
  </div>

  <!-- Mode select -->
  <div v-else-if="phase === 'mode-select'" class="gate-center">
    <h2 class="gate-title">选择写作模式</h2>
    <p class="gate-desc">
      当前学段：<strong>{{ getStageLabel(currentStage) }}</strong>
    </p>
    <div class="mode-grid">
      <button class="mode-card" @click="enterEditor('free')">
        <span class="mode-icon">&#9997;&#65039;</span>
        <span class="mode-name">自由模式</span>
        <span class="mode-desc">自由写作，AI 实时辅助与反馈</span>
      </button>
      <button class="mode-card" @click="enterExamSetup">
        <span class="mode-icon">&#9200;</span>
        <span class="mode-name">考试模式</span>
        <span class="mode-desc">模拟考试环境，限时写作与评分</span>
      </button>
    </div>
  </div>

  <!-- Exam setup -->
  <ExamSetupPage
    v-else-if="phase === 'exam-setup'"
    @confirm="onExamConfirm"
    @back="phase = 'mode-select'"
  />

  <!-- Editor -->
  <EditorShell
    v-else
    :initial-writing-mode="chosenMode"
    :initial-task-prompt="initialTaskPrompt"
    :exam-max-score="examMaxScore"
    :study-stage="currentStage!"
  />
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import EditorShell from '@/components/writing/EditorShell.vue'
import ExamSetupPage from '@/pages/app/ExamSetupPage.vue'
import type { ExamTopicInfo } from '@/pages/app/ExamSetupPage.vue'
import { stageCache } from '@/stores/stageCache'
import { getStageLabel } from '@/constants/stage'

type Phase = 'loading' | 'mode-select' | 'exam-setup' | 'editor'

const router = useRouter()

const phase = ref<Phase>('loading')
const currentStage = ref<string | null>(null)
const chosenMode = ref<'free' | 'exam'>('free')
const initialTaskPrompt = ref<string | undefined>(undefined)
const examMaxScore = ref<number | null>(null)

onMounted(() => {
  // Router guard already fetched profile into stageCache
  const cached = stageCache.value
  if (!cached || cached === '' || cached === '__error__') {
    router.replace({ path: '/app/stage-setup', query: { redirect: '/app/writing' } })
    return
  }
  currentStage.value = cached

  // 恢复上次选择的模式，刷新后直接进入编辑器
  const savedMode = sessionStorage.getItem('writingMode')
  if (savedMode === 'free' || savedMode === 'exam') {
    chosenMode.value = savedMode
    phase.value = 'editor'
  } else {
    phase.value = 'mode-select'
  }
})

function enterEditor(mode: 'free' | 'exam') {
  chosenMode.value = mode
  sessionStorage.setItem('writingMode', mode)
  phase.value = 'editor'
}

function enterExamSetup() {
  phase.value = 'exam-setup'
}

function onExamConfirm(info: ExamTopicInfo) {
  chosenMode.value = 'exam'
  // 组装 taskPrompt
  let prompt = info.topic
  if (info.genre) prompt += `\n体裁：${info.genre}`
  if (info.wordRange) prompt += `\n字数要求：${info.wordRange}词`
  if (info.requirements) prompt += `\n写作要求：${info.requirements}`
  if (info.maxScore && info.maxScore !== 100) prompt += `\n满分分值：${info.maxScore}分`
  examMaxScore.value = info.maxScore ?? 100
  initialTaskPrompt.value = prompt
  sessionStorage.setItem('writingMode', 'exam')
  phase.value = 'editor'
}
</script>

<style src="@/styles/gate.css" />
<style scoped>
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

.mode-icon {
  font-size: 32px;
  line-height: 1;
}

.mode-name {
  font-size: 17px;
  font-weight: 700;
  color: #111827;
}

.mode-desc {
  font-size: 13px;
  color: #6b7280;
  text-align: center;
  line-height: 1.4;
}

@media (max-width: 560px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }
}
</style>
