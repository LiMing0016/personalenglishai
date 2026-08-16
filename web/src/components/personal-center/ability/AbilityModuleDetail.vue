<template>
  <section
    class="ability-detail"
    :aria-labelledby="detailTitleId"
    :aria-busy="loading"
  >
    <button type="button" class="ability-back" @click="emit('back')">
      <ArrowLeft :size="17" aria-hidden="true" />
      返回英语能力画像
    </button>

    <header class="ability-detail-header">
      <div>
        <h2 :id="detailTitleId">{{ detail.title }}</h2>
        <strong>{{ detail.levelLabel }}</strong>
        <span class="ability-evidence-state">
          <CircleCheck v-if="detail.evidenceCount > 0" :size="15" aria-hidden="true" />
          <CircleAlert v-else :size="15" aria-hidden="true" />
          {{ detail.evidenceLabel }}
        </span>
        <span class="ability-trend-label">
          <TrendingUp v-if="detail.history.length > 1" :size="15" aria-hidden="true" />
          <Minus v-else :size="15" aria-hidden="true" />
          {{ detail.trendLabel }}
        </span>
      </div>
      <p>{{ detail.diagnosis }}</p>
    </header>

    <div class="ability-detail-main">
      <section class="ability-subskills" aria-labelledby="subskill-title">
        <h3 id="subskill-title">子能力</h3>
        <div
          v-for="skill in detail.subskills"
          :key="skill.key"
          class="ability-subskill-row"
        >
          <span>{{ skill.label }}</span>
          <strong>{{ skill.valueLabel }}</strong>
          <span class="ability-subskill-track" aria-hidden="true">
            <i :style="{ width: `${skill.value == null ? 0 : Math.min(100, Math.max(0, skill.value))}%` }" />
          </span>
        </div>
      </section>

      <aside class="ability-next-step">
        <h3>下一步</h3>
        <span class="ability-next-step-icon" aria-hidden="true">
          <PenLine :size="25" />
        </span>
        <p>{{ detail.actionLabel }}</p>
        <RouterLink :to="detail.actionTo">
          {{ detail.actionLabel }}
          <ArrowRight :size="17" aria-hidden="true" />
        </RouterLink>
      </aside>
    </div>

    <section class="ability-detail-tabs" aria-label="能力详情信息">
      <div class="ability-tab-list" role="tablist" aria-label="详情分类">
        <button
          v-for="tab in tabs"
          :id="tabId(tab.key)"
          :key="tab.key"
          ref="tabButtons"
          type="button"
          role="tab"
          :aria-controls="panelId"
          :aria-selected="activeTab === tab.key"
          :tabindex="activeTab === tab.key ? 0 : -1"
          @click="activeTab = tab.key"
          @keydown="onTabKeydown($event, tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div
        :id="panelId"
        class="ability-tab-panel"
        role="tabpanel"
        tabindex="0"
        :aria-labelledby="tabId(activeTab)"
      >
        <div v-if="error" class="ability-detail-error" role="alert">
          <span>部分详情暂时无法加载，已展示当前可用数据。</span>
          <button type="button" @click="emit('retry')">重试失败项</button>
        </div>
        <p v-else-if="loading" class="ability-detail-status" role="status">
          正在补充能力详情，当前可用数据已展示。
        </p>

        <template v-if="activeTab === 'diagnosis'">
          <ul v-if="visibleFindings.length" class="ability-finding-list">
            <li v-for="finding in visibleFindings" :key="`${finding.tone}-${finding.text}`">
              <CircleCheck v-if="finding.tone === 'strength'" :size="19" aria-hidden="true" />
              <CircleAlert v-else :size="19" aria-hidden="true" />
              <span>{{ finding.text }}</span>
            </li>
          </ul>
          <p v-else class="ability-empty-state">{{ detail.diagnosis }}</p>
        </template>

        <template v-else-if="activeTab === 'evidence'">
          <ul v-if="detail.evidence.length" class="ability-evidence-list">
            <li v-for="item in detail.evidence" :key="item.id">
              <span>
                <strong>{{ item.title }}</strong>
                <time>{{ item.timeLabel }}</time>
              </span>
              <b>{{ item.scoreLabel }} 分</b>
            </li>
          </ul>
          <p v-else class="ability-empty-state">暂无可展示的有效证据。</p>
        </template>

        <template v-else>
          <ol v-if="detail.history.length" class="ability-history-list">
            <li v-for="item in detail.history" :key="item.id">
              <span>{{ item.label }}</span>
              <strong>{{ item.score }} 分</strong>
              <small :class="{ 'is-positive': item.delta > 0, 'is-negative': item.delta < 0 }">
                {{ formatDelta(item.delta) }}
              </small>
            </li>
          </ol>
          <p v-else class="ability-empty-state">暂无可对比的历史记录。</p>
        </template>

        <p class="ability-source-summary">{{ detail.sourceSummary }}</p>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  CircleAlert,
  CircleCheck,
  Minus,
  PenLine,
  TrendingUp,
} from 'lucide-vue-next'

import type { AbilityModuleDetail } from './abilityProfileModel'

type DetailTab = 'diagnosis' | 'evidence' | 'history'

const props = defineProps<{
  detail: AbilityModuleDetail
  loading: boolean
  error: boolean
}>()

const emit = defineEmits<{ back: []; retry: [] }>()
const activeTab = ref<DetailTab>('diagnosis')
const tabButtons = ref<HTMLButtonElement[]>([])
const tabs: Array<{ key: DetailTab; label: string }> = [
  { key: 'diagnosis', label: '诊断' },
  { key: 'evidence', label: '证据' },
  { key: 'history', label: '历史' },
]

const detailTitleId = computed(() => `ability-${props.detail.key}-title`)
const panelId = computed(() => `ability-${props.detail.key}-tabpanel`)
const visibleFindings = computed(() => props.detail.findings.slice(0, 2))

watch(() => props.detail.key, () => {
  activeTab.value = 'diagnosis'
})

function tabId(tab: DetailTab): string {
  return `ability-${props.detail.key}-${tab}-tab`
}

function formatDelta(delta: number): string {
  if (delta > 0) return `+${delta}`
  if (delta < 0) return `${delta}`
  return '持平'
}

function onTabKeydown(event: KeyboardEvent, current: DetailTab) {
  const currentIndex = tabs.findIndex((tab) => tab.key === current)
  let nextIndex: number | null = null

  if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % tabs.length
  if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + tabs.length) % tabs.length
  if (event.key === 'Home') nextIndex = 0
  if (event.key === 'End') nextIndex = tabs.length - 1
  if (nextIndex == null) return

  event.preventDefault()
  activeTab.value = tabs[nextIndex].key
  void nextTick(() => tabButtons.value[nextIndex]?.focus())
}
</script>

<style scoped>
.ability-detail {
  width: 100%;
  max-width: 1180px;
  color: #10243f;
}

.ability-back {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  gap: 8px;
  margin: 0 0 24px -8px;
  border: 0;
  border-radius: 9px;
  padding: 0 8px;
  background: transparent;
  color: #41566e;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
}

.ability-back:hover {
  background: #edf8f4;
  color: #087a59;
}

.ability-detail-header > div {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 18px;
}

.ability-detail-header h2 {
  margin: 0;
  font-size: clamp(24px, 2.5vw, 31px);
  font-weight: 760;
  letter-spacing: -0.025em;
}

.ability-detail-header > div > strong {
  color: #07835f;
  font-size: clamp(30px, 3.4vw, 43px);
  font-weight: 790;
  line-height: 1;
}

.ability-detail-header span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #41566e;
  font-size: 13px;
}

.ability-detail-header .ability-evidence-state {
  color: #087a59;
}

.ability-detail-header > p {
  margin: 15px 0 0;
  color: #41566e;
  font-size: 16px;
  line-height: 1.7;
}

.ability-detail-main {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(260px, 0.85fr);
  gap: 24px;
  margin-top: 24px;
}

.ability-subskills,
.ability-next-step,
.ability-detail-tabs {
  border: 1px solid #dfe8ee;
  border-radius: 12px;
  background: #fff;
}

.ability-subskills {
  padding: 21px 24px 12px;
}

.ability-subskills h3,
.ability-next-step h3 {
  margin: 0;
  color: #10243f;
  font-size: 16px;
  font-weight: 740;
}

.ability-subskill-row {
  display: grid;
  min-height: 58px;
  grid-template-columns: minmax(110px, 1fr) minmax(66px, auto) minmax(120px, 1.3fr);
  align-items: center;
  gap: 18px;
  border-bottom: 1px solid #e5ebf0;
  color: #41566e;
}

.ability-subskill-row:last-child {
  border-bottom: 0;
}

.ability-subskill-row > span:first-child {
  font-size: 14px;
  font-weight: 650;
}

.ability-subskill-row > strong {
  color: #087a59;
  font-size: 15px;
  font-weight: 740;
}

.ability-subskill-track {
  display: block;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #dcebe6;
}

.ability-subskill-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #07835f;
}

.ability-next-step {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-content: start;
  gap: 18px;
  padding: 21px 24px 24px;
  background: #f5faf8;
}

.ability-next-step h3,
.ability-next-step a {
  grid-column: 1 / -1;
}

.ability-next-step-icon {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border: 1px solid #d0e8df;
  border-radius: 50%;
  color: #07835f;
}

.ability-next-step p {
  align-self: center;
  margin: 0;
  color: #1c334c;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.5;
}

.ability-next-step a {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  margin-top: 9px;
  border-radius: 9px;
  background: #087a59;
  color: #fff;
  font-size: 14px;
  font-weight: 720;
  text-decoration: none;
}

.ability-next-step a:hover {
  background: #066b4f;
}

.ability-detail-tabs {
  margin-top: 24px;
  overflow: hidden;
}

.ability-tab-list {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid #dfe8ee;
  padding: 0 22px;
}

.ability-tab-list button {
  position: relative;
  min-width: 58px;
  min-height: 52px;
  border: 0;
  background: transparent;
  color: #52677d;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 680;
}

.ability-tab-list button[aria-selected='true'] {
  color: #087a59;
}

.ability-tab-list button[aria-selected='true']::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: #07835f;
  content: '';
}

.ability-tab-panel {
  min-height: 146px;
  padding: 22px 26px 18px;
}

.ability-detail-error,
.ability-detail-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 0 0 18px;
  border-radius: 9px;
  padding: 11px 13px;
  background: #f4f7f9;
  color: #52677d;
  font-size: 13px;
}

.ability-detail-error button {
  min-height: 34px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  background: #52677d;
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 680;
}

.ability-finding-list,
.ability-evidence-list,
.ability-history-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.ability-finding-list li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  color: #41566e;
  font-size: 14px;
  line-height: 1.65;
}

.ability-finding-list li + li {
  margin-top: 16px;
}

.ability-finding-list svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: #07835f;
}

.ability-evidence-list li,
.ability-history-list li {
  display: grid;
  min-height: 54px;
  align-items: center;
  gap: 14px;
  border-bottom: 1px solid #e5ebf0;
}

.ability-evidence-list li {
  grid-template-columns: minmax(0, 1fr) auto;
}

.ability-evidence-list li > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.ability-evidence-list strong,
.ability-history-list > li > span {
  overflow: hidden;
  color: #1c334c;
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ability-evidence-list time,
.ability-history-list small {
  color: #71849a;
  font-size: 12px;
}

.ability-evidence-list b,
.ability-history-list strong {
  color: #087a59;
  font-size: 14px;
}

.ability-history-list li {
  grid-template-columns: minmax(0, 1fr) auto 56px;
}

.ability-history-list small {
  text-align: right;
}

.ability-history-list small.is-positive {
  color: #087a59;
}

.ability-history-list small.is-negative {
  color: #a94747;
}

.ability-empty-state {
  margin: 8px 0;
  color: #52677d;
  font-size: 14px;
  line-height: 1.65;
}

.ability-source-summary {
  margin: 22px 0 0;
  color: #71849a;
  font-size: 12px;
}

.ability-back:focus-visible,
.ability-next-step a:focus-visible,
.ability-tab-list button:focus-visible,
.ability-tab-panel:focus-visible,
.ability-detail-error button:focus-visible {
  outline: 3px solid #087a59;
  outline-offset: 3px;
}

@media (prefers-reduced-motion: reduce) {
  .ability-subskill-track i {
    transition: none;
  }
}

@media (max-width: 760px) {
  .ability-detail-main {
    grid-template-columns: 1fr;
  }

  .ability-detail-header > div {
    gap: 10px 14px;
  }

  .ability-subskills,
  .ability-next-step {
    padding-right: 18px;
    padding-left: 18px;
  }
}

@media (max-width: 520px) {
  .ability-back {
    margin-bottom: 18px;
  }

  .ability-detail-header > div > strong {
    order: 2;
  }

  .ability-detail-header span {
    width: 100%;
    order: 3;
  }

  .ability-subskill-row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px 14px;
    padding: 12px 0;
  }

  .ability-subskill-track {
    grid-column: 1 / -1;
  }

  .ability-tab-list {
    justify-content: space-between;
    gap: 8px;
    padding: 0 14px;
  }

  .ability-tab-panel {
    padding: 18px;
  }

  .ability-detail-error {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
