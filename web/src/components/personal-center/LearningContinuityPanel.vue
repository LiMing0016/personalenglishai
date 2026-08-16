<template>
  <section class="continuity-panel" aria-labelledby="continuity-title">
    <div class="continuity-line" aria-hidden="true"></div>

    <article class="continuity-step continuity-step--previous">
      <div class="continuity-node continuity-node--quiet" aria-hidden="true">
        <Check v-if="model.previous.hasHistory" :size="22" :stroke-width="1.8" />
        <CircleDashed v-else :size="22" :stroke-width="1.7" />
      </div>
      <p class="step-kicker">
        <span>上次完成</span>
        <time v-if="model.previous.occurredAt" :datetime="model.previous.occurredAt">
          {{ formatDateTime(model.previous.occurredAt) }}
        </time>
      </p>
      <h3>{{ model.previous.title }}</h3>
      <p class="step-description">{{ model.previous.description }}</p>
    </article>

    <article class="continuity-step continuity-step--current">
      <p class="step-kicker step-kicker--active">
        <span>现在继续</span>
        <time :datetime="currentIsoDate">{{ currentDate }}</time>
      </p>
      <div class="continuity-node continuity-node--active" aria-hidden="true">
        <BookOpen :size="34" :stroke-width="1.65" />
      </div>
      <h2 id="continuity-title">AI 学习助手 · {{ planName }}</h2>
      <p class="current-description">继续你的表达、阅读与词汇训练</p>

      <RouterLink class="primary-action" to="/app/assistant">
        <Play :size="18" :stroke-width="1.9" />
        继续学习
      </RouterLink>

      <div class="secondary-actions" aria-label="快速开始">
        <RouterLink to="/app/writing">
          <PenLine :size="17" :stroke-width="1.8" />
          开始写作
        </RouterLink>
        <span class="action-divider" aria-hidden="true"></span>
        <RouterLink to="/app/translation">
          <Languages :size="17" :stroke-width="1.8" />
          新建翻译
        </RouterLink>
      </div>

      <div class="weekly-progress" aria-label="本周学习节点">
        <p>
          本周
          <strong>{{ model.weeklyProgress.completed }} / {{ model.weeklyProgress.total }}</strong>
          个学习节点
        </p>
        <div class="progress-dots" aria-hidden="true">
          <span
            v-for="index in model.weeklyProgress.total"
            :key="index"
            :class="{ complete: index <= model.weeklyProgress.completed }"
          ></span>
        </div>
      </div>
    </article>

    <article class="continuity-step continuity-step--next">
      <div class="continuity-node continuity-node--quiet" aria-hidden="true">
        <CalendarDays :size="22" :stroke-width="1.7" />
      </div>
      <p class="step-kicker"><span>为你准备</span></p>
      <h3>复习重点词汇</h3>
      <p class="step-description">回顾已经沉淀的词汇卡片</p>
      <RouterLink class="review-action" to="/app/vocabulary">
        <RefreshCw :size="16" :stroke-width="1.9" />
        开始复习
      </RouterLink>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  BookOpen,
  CalendarDays,
  Check,
  CircleDashed,
  Languages,
  PenLine,
  Play,
  RefreshCw,
} from 'lucide-vue-next'

import {
  buildLearningContinuity,
  type LearningContinuityHistoryItem,
} from './learningContinuity'

const props = defineProps<{
  recentItem: LearningContinuityHistoryItem | null
  studyDays: number | null | undefined
  stageLabel?: string | null
}>()

const model = computed(() =>
  buildLearningContinuity({
    recentItem: props.recentItem,
    studyDays: props.studyDays,
  }),
)

const now = new Date()
const currentIsoDate = now.toISOString()
const currentDate = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(now)
const planName = computed(() =>
  props.stageLabel && props.stageLabel !== '未设置'
    ? `${props.stageLabel}提升计划`
    : '英语提升计划',
)

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
</script>

<style scoped>
.continuity-panel {
  --continuity-green: #087a59;
  --continuity-navy: #10243f;
  position: relative;
  display: grid;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  grid-template-columns: minmax(0, 0.9fr) minmax(420px, 1.35fr) minmax(0, 0.9fr);
  min-height: 390px;
  overflow: hidden;
  border: 1px solid #d8e3ef;
  border-radius: 24px;
  background:
    radial-gradient(circle at 50% 44%, rgba(28, 159, 116, 0.075), transparent 23%),
    linear-gradient(135deg, #ffffff 0%, #fcfefe 57%, #f7fbff 100%);
  box-shadow: 0 26px 60px rgba(38, 66, 94, 0.08);
}

.continuity-panel::before {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, transparent 12%, rgba(255, 255, 255, 0.7) 46%, transparent 72%);
  content: '';
  pointer-events: none;
}

.continuity-line {
  position: absolute;
  z-index: 0;
  top: 108px;
  right: 10.5%;
  left: 10.5%;
  height: 1px;
  background: linear-gradient(90deg, #7e91aa 0%, var(--continuity-green) 50%, #7e91aa 100%);
}

.continuity-line::before,
.continuity-line::after {
  position: absolute;
  top: -4px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #36516f;
  content: '';
}

.continuity-line::before {
  left: 14%;
}

.continuity-line::after {
  right: 14%;
}

.continuity-step {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.continuity-step--previous,
.continuity-step--next {
  padding: 80px 34px 34px;
}

.continuity-step--current {
  padding: 46px 30px 26px;
}

.continuity-node {
  display: grid;
  place-items: center;
  color: var(--continuity-navy);
}

.continuity-node--quiet {
  width: 58px;
  height: 58px;
  margin-bottom: 30px;
  border: 2px solid #71869f;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 8px 22px rgba(49, 74, 103, 0.08);
}

.continuity-node--active {
  width: 84px;
  height: 84px;
  margin: 22px 0 24px;
  border: 8px solid rgba(255, 255, 255, 0.95);
  border-radius: 50%;
  background: linear-gradient(145deg, #10956d, #006d4d);
  color: #fff;
  outline: 2px solid #d6e2ef;
  box-shadow:
    0 0 0 8px rgba(230, 241, 248, 0.72),
    0 18px 34px rgba(4, 120, 87, 0.2);
}

.step-kicker {
  display: flex;
  min-height: 24px;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin: 0 0 24px;
  color: #536780;
  font-size: 13px;
  line-height: 1.5;
}

.step-kicker span {
  color: var(--continuity-navy);
  font-weight: 700;
}

.step-kicker--active {
  margin: 0;
  color: var(--continuity-green);
}

.step-kicker--active span {
  color: #075f47;
}

.continuity-step h2,
.continuity-step h3 {
  overflow: hidden;
  max-width: 100%;
  margin: 0;
  color: var(--continuity-navy);
  text-overflow: ellipsis;
}

.continuity-step h2 {
  font-size: clamp(24px, 2vw, 31px);
  font-weight: 760;
  letter-spacing: -0.035em;
  line-height: 1.22;
}

.continuity-step h3 {
  display: -webkit-box;
  font-size: 18px;
  font-weight: 720;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.step-description,
.current-description {
  margin: 10px 0 0;
  color: #62748c;
  font-size: 14px;
  line-height: 1.65;
}

.current-description {
  margin-top: 10px;
}

.primary-action,
.review-action,
.secondary-actions a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
}

.primary-action {
  width: min(100%, 250px);
  min-height: 50px;
  gap: 10px;
  margin-top: 24px;
  border-radius: 12px;
  background: linear-gradient(135deg, #09845f, #006c4d);
  box-shadow: 0 12px 24px rgba(4, 120, 87, 0.18);
  color: #fff;
  font-size: 16px;
  font-weight: 730;
  transition: transform 160ms ease, box-shadow 160ms ease;
}

.primary-action:hover {
  box-shadow: 0 16px 30px rgba(4, 120, 87, 0.24);
  transform: translateY(-1px);
}

.secondary-actions {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-top: 18px;
}

.secondary-actions a {
  gap: 8px;
  color: var(--continuity-navy);
  font-size: 14px;
  font-weight: 650;
}

.secondary-actions a:hover {
  color: var(--continuity-green);
}

.action-divider {
  width: 1px;
  height: 20px;
  background: #c8d4e1;
}

.weekly-progress {
  width: min(100%, 365px);
  margin-top: 22px;
}

.weekly-progress p {
  margin: 0 0 12px;
  color: #536780;
  font-size: 13px;
}

.weekly-progress strong {
  margin: 0 4px;
  color: var(--continuity-green);
  font-size: 16px;
}

.progress-dots {
  display: flex;
  align-items: center;
}

.progress-dots span {
  position: relative;
  flex: 1;
  height: 1px;
  background: #c9d6e3;
}

.progress-dots span::before {
  position: absolute;
  z-index: 1;
  top: 50%;
  left: 0;
  width: 9px;
  height: 9px;
  border: 2px solid #c9d6e3;
  border-radius: 50%;
  background: #f9fcfe;
  content: '';
  transform: translate(-50%, -50%);
}

.progress-dots span:last-child::after {
  position: absolute;
  top: 50%;
  right: 0;
  width: 9px;
  height: 9px;
  border: 2px solid #c9d6e3;
  border-radius: 50%;
  background: #f9fcfe;
  content: '';
  transform: translate(50%, -50%);
}

.progress-dots span.complete {
  background: var(--continuity-green);
}

.progress-dots span.complete::before {
  border-color: var(--continuity-green);
  background: var(--continuity-green);
}

.review-action {
  min-height: 42px;
  gap: 8px;
  margin-top: 22px;
  border: 1px solid var(--continuity-green);
  border-radius: 12px;
  padding: 0 20px;
  color: var(--continuity-green);
  font-size: 14px;
  font-weight: 700;
  transition: background-color 160ms ease;
}

.review-action:hover {
  background: #edf9f5;
}

.primary-action:focus-visible,
.review-action:focus-visible,
.secondary-actions a:focus-visible {
  outline: 3px solid rgba(8, 122, 89, 0.25);
  outline-offset: 3px;
}

@media (max-width: 1120px) {
  .continuity-panel {
    grid-template-columns: minmax(0, 0.8fr) minmax(360px, 1.4fr) minmax(0, 0.8fr);
  }

  .continuity-step--previous,
  .continuity-step--next {
    padding-right: 20px;
    padding-left: 20px;
  }
}

@media (max-width: 840px) {
  .continuity-panel {
    grid-template-columns: 1fr;
    padding: 8px 22px;
  }

  .continuity-line {
    top: 58px;
    bottom: 58px;
    left: 53px;
    width: 1px;
    height: auto;
    background: linear-gradient(180deg, #7e91aa 0%, var(--continuity-green) 50%, #7e91aa 100%);
  }

  .continuity-line::before,
  .continuity-line::after {
    display: none;
  }

  .continuity-step,
  .continuity-step--previous,
  .continuity-step--current,
  .continuity-step--next {
    min-height: 0;
    align-items: flex-start;
    padding: 26px 4px 26px 78px;
    text-align: left;
  }

  .continuity-step--current {
    order: -1;
  }

  .continuity-node--quiet,
  .continuity-node--active {
    position: absolute;
    top: 28px;
    left: 2px;
    margin: 0;
  }

  .continuity-node--active {
    width: 54px;
    height: 54px;
    border-width: 5px;
    outline-width: 1px;
    box-shadow: 0 0 0 5px rgba(230, 241, 248, 0.8);
  }

  .continuity-node--active :deep(svg) {
    width: 24px;
    height: 24px;
  }

  .continuity-node--quiet {
    width: 46px;
    height: 46px;
  }

  .step-kicker {
    justify-content: flex-start;
    margin-bottom: 8px;
  }

  .step-kicker--active {
    margin-bottom: 8px;
  }

  .continuity-step h2 {
    font-size: 24px;
  }

  .primary-action {
    width: 100%;
  }

  .weekly-progress {
    max-width: 100%;
  }
}

@media (max-width: 520px) {
  .continuity-panel {
    padding: 4px 14px;
    border-radius: 20px;
  }

  .continuity-line {
    left: 39px;
  }

  .continuity-step,
  .continuity-step--previous,
  .continuity-step--current,
  .continuity-step--next {
    padding-left: 64px;
  }

  .continuity-node--quiet,
  .continuity-node--active {
    left: 0;
  }

  .secondary-actions {
    flex-wrap: wrap;
    gap: 12px;
  }

  .action-divider {
    display: none;
  }
}
</style>
