<template>
  <section
    class="ability-overview"
    aria-labelledby="ability-overview-title"
    :aria-busy="loading"
  >
    <header class="ability-heading">
      <h2 id="ability-overview-title">英语能力画像</h2>
      <div class="ability-explanation">
        <button
          type="button"
          class="ability-explanation-trigger"
          aria-controls="ability-explanation-content"
          :aria-expanded="explanationOpen"
          @click="explanationOpen = !explanationOpen"
          @keydown.esc="explanationOpen = false"
        >
          <CircleHelp :size="16" aria-hidden="true" />
          评估说明
        </button>
        <div
          v-if="explanationOpen"
          id="ability-explanation-content"
          class="ability-explanation-content"
          role="note"
        >
          <p>综合 CEFR 在形成校准证据前保持“待形成”。</p>
          <p>有写作证据时显示“待校准”；无证据时显示“待测”。其他模块为“待测”。</p>
        </div>
      </div>
    </header>

    <template v-if="loading">
      <div class="ability-summary-strip ability-summary-strip--loading" aria-hidden="true">
        <span v-for="item in 3" :key="item"><i class="ability-skeleton" /></span>
      </div>

      <div class="ability-module-row" aria-label="英语能力模块">
        <div v-for="item in 5" :key="item" class="ability-module ability-module--loading">
          <i class="ability-skeleton ability-skeleton--icon" />
          <i class="ability-skeleton ability-skeleton--label" />
          <i class="ability-skeleton ability-skeleton--level" />
          <i class="ability-skeleton ability-skeleton--status" />
        </div>
      </div>

      <div class="ability-priority ability-priority--loading" aria-hidden="true">
        <i class="ability-skeleton ability-skeleton--priority" />
        <i class="ability-skeleton ability-skeleton--action" />
      </div>
      <span class="visually-hidden" role="status">正在加载英语能力画像</span>
    </template>

    <template v-else>
      <div class="ability-summary-strip">
        <span>
          综合 CEFR
          <strong>{{ model.overallLevelLabel }}</strong>
        </span>
        <span>
          覆盖
          <strong>{{ error ? '—' : model.coverageCount }}</strong>
          / {{ model.coverageTotal }}
        </span>
        <span>
          可信度
          <strong>{{ error ? '暂不可用' : model.confidenceLabel }}</strong>
          <i v-if="!error" class="confidence-steps" aria-hidden="true">
            <b
              v-for="step in 3"
              :key="step"
              :class="{ active: step <= model.confidenceSteps }"
            />
          </i>
        </span>
      </div>

      <div class="ability-module-row" aria-label="英语能力模块">
        <button
          v-for="module in model.modules"
          :key="module.key"
          type="button"
          class="ability-module"
          :aria-disabled="error"
          @click="openModule(module.key)"
        >
          <i class="ability-module-icon" aria-hidden="true">
            <component :is="moduleIcons[module.key]" :size="24" />
          </i>
          <span>{{ module.title }}</span>
          <strong>{{ error ? '暂不可用' : module.levelLabel }}</strong>
          <small :class="{ 'is-unavailable': error || module.evidenceState === 'unmeasured' }">
            {{ error ? '数据加载失败' : module.evidenceLabel }}
          </small>
        </button>
      </div>

      <div class="ability-priority" :class="{ 'ability-priority--error': error }">
        <strong>{{ error ? '能力数据暂时无法加载' : model.priorityText }}</strong>
        <button v-if="error" type="button" class="ability-retry" @click="emit('retry')">
          重新加载
        </button>
        <RouterLink v-else :to="model.priorityAction.to">
          {{ model.priorityAction.label }}
          <ArrowRight :size="17" aria-hidden="true" />
        </RouterLink>
      </div>

      <button
        v-if="!error && model.recentEvidence"
        class="ability-recent-evidence"
        type="button"
        @click="emit('open-module', 'writing')"
      >
        <Clock3 :size="18" aria-hidden="true" />
        <span>最近证据</span>
        <strong>{{ model.recentEvidence.detail }}</strong>
        <time :datetime="model.recentEvidence.timeLabel">
          {{ formatEvidenceTime(model.recentEvidence.timeLabel) }}
        </time>
        <ArrowRight :size="17" aria-hidden="true" />
      </button>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, type Component } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  BookOpen,
  CircleHelp,
  Clock3,
  FileText,
  Headphones,
  MessageCircle,
  PenLine,
} from 'lucide-vue-next'

import type {
  AbilityModuleKey,
  AbilityOverviewModel,
} from './abilityProfileModel'

const props = defineProps<{
  model: AbilityOverviewModel
  loading: boolean
  error: boolean
}>()

const emit = defineEmits<{
  'open-module': [key: AbilityModuleKey]
  retry: []
}>()

const explanationOpen = ref(false)

const moduleIcons: Record<AbilityModuleKey, Component> = {
  writing: PenLine,
  vocabulary: BookOpen,
  reading: FileText,
  listening: Headphones,
  speaking: MessageCircle,
}

function formatEvidenceTime(value: string): string {
  return /^\d{4}-\d{2}-\d{2}/.test(value) ? value.slice(0, 10) : value
}

function openModule(key: AbilityModuleKey) {
  if (!props.error) emit('open-module', key)
}
</script>

<style scoped>
.ability-overview {
  width: 100%;
  max-width: 1180px;
  color: #10243f;
}

.ability-heading {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.ability-heading h2 {
  margin: 0;
  font-size: clamp(23px, 2vw, 28px);
  font-weight: 760;
  letter-spacing: -0.025em;
}

.ability-explanation {
  position: relative;
}

.ability-explanation-trigger {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 9px;
  padding: 0 8px;
  background: transparent;
  color: #087a59;
  cursor: pointer;
  font-size: 13px;
  font-weight: 680;
}

.ability-explanation-trigger:hover {
  background: #edf8f4;
}

.ability-explanation-content {
  position: absolute;
  z-index: 10;
  top: calc(100% + 8px);
  left: 0;
  width: min(320px, calc(100vw - 40px));
  border: 1px solid #dbe7e2;
  border-radius: 12px;
  padding: 13px 15px;
  background: #fff;
  box-shadow: 0 14px 30px rgba(27, 50, 75, 0.12);
  color: #53677e;
  font-size: 13px;
  line-height: 1.6;
}

.ability-explanation-content p {
  margin: 0;
}

.ability-explanation-content p + p {
  margin-top: 4px;
}

.ability-summary-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  min-height: 70px;
  align-items: center;
  border-top: 1px solid #dfe8ee;
  border-bottom: 1px solid #dfe8ee;
  background: #fbfdfd;
}

.ability-summary-strip > span {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 15px 24px;
  color: #667a91;
  font-size: 13px;
}

.ability-summary-strip > span + span {
  border-left: 1px solid #dfe8ee;
}

.ability-summary-strip strong {
  color: #10243f;
  font-size: 18px;
  font-weight: 760;
}

.confidence-steps {
  display: inline-flex;
  gap: 5px;
  margin-left: 3px;
}

.confidence-steps b {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #dfe8e8;
}

.confidence-steps b.active {
  background: #07835f;
}

.ability-module-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-top: 38px;
}

.ability-module {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 176px;
  align-items: center;
  flex-direction: column;
  border: 0;
  padding: 4px 20px 24px;
  background: transparent;
  color: #41566e;
  cursor: pointer;
  font: inherit;
}

.ability-module + .ability-module::before {
  position: absolute;
  top: 34px;
  bottom: 24px;
  left: 0;
  width: 1px;
  background: #e2e9ef;
  content: '';
}

.ability-module:hover:not([aria-disabled='true']) .ability-module-icon {
  background: #dff2ec;
  transform: translateY(-2px);
}

.ability-module:hover:not([aria-disabled='true']) > strong {
  color: #087a59;
}

.ability-module[aria-disabled='true'] {
  cursor: not-allowed;
}

.ability-module-icon {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  margin-bottom: 17px;
  border-radius: 50%;
  background: #eaf5f1;
  color: #07835f;
  transition: background 160ms ease, transform 160ms ease;
}

.ability-module > span {
  font-size: 14px;
  font-weight: 650;
}

.ability-module > strong {
  margin-top: 7px;
  color: #10243f;
  font-size: 21px;
  font-weight: 760;
  transition: color 160ms ease;
}

.ability-module > small {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 10px;
  color: #087a59;
  font-size: 12px;
}

.ability-module > small::before {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.ability-module > small.is-unavailable {
  color: #52677d;
}

.ability-priority {
  display: flex;
  min-height: 74px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 4px;
  border-top: 1px solid #d8e7e1;
  border-bottom: 1px solid #d8e7e1;
  padding: 14px 18px 14px 24px;
  background: #f3faf7;
}

.ability-priority > strong {
  color: #1c334c;
  font-size: 15px;
  line-height: 1.5;
}

.ability-priority a,
.ability-retry {
  display: inline-flex;
  min-height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 10px;
  padding: 0 17px;
  background: #087a59;
  color: #fff;
  cursor: pointer;
  font-size: 13px;
  font-weight: 720;
  text-decoration: none;
}

.ability-priority a:hover,
.ability-retry:hover {
  background: #066b4f;
}

.ability-priority--error {
  background: #f8fafc;
}

.ability-retry {
  min-height: 36px;
  padding: 0 14px;
  background: #52677d;
}

.ability-retry:hover {
  background: #41566c;
}

.ability-recent-evidence {
  display: grid;
  width: 100%;
  min-height: 60px;
  grid-template-columns: auto auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  border: 0;
  border-top: 1px solid #e2e9ef;
  border-bottom: 1px solid #e2e9ef;
  padding: 13px 10px;
  background: transparent;
  color: #71849a;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.ability-recent-evidence > span,
.ability-recent-evidence > time {
  font-size: 13px;
}

.ability-recent-evidence > strong {
  overflow: hidden;
  color: #52677d;
  font-size: 13px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ability-recent-evidence:hover {
  color: #087a59;
}

.ability-summary-strip--loading > span {
  min-height: 68px;
}

.ability-module--loading {
  cursor: default;
}

.ability-skeleton {
  display: block;
  width: 82px;
  height: 12px;
  border-radius: 999px;
  background: linear-gradient(90deg, #edf2f5 25%, #f8fafb 45%, #edf2f5 65%);
  background-size: 220% 100%;
  animation: ability-skeleton-pulse 1.4s ease-in-out infinite;
}

.ability-skeleton--icon {
  width: 54px;
  height: 54px;
  margin-bottom: 17px;
  border-radius: 50%;
}

.ability-skeleton--label {
  width: 42px;
}

.ability-skeleton--level {
  width: 62px;
  height: 20px;
  margin-top: 10px;
}

.ability-skeleton--status {
  width: 76px;
  margin-top: 12px;
}

.ability-priority--loading {
  pointer-events: none;
}

.ability-skeleton--priority {
  width: min(280px, 58%);
  height: 15px;
}

.ability-skeleton--action {
  width: 112px;
  height: 42px;
  border-radius: 10px;
}

.ability-explanation-trigger:focus-visible,
.ability-priority a:focus-visible,
.ability-retry:focus-visible,
.ability-recent-evidence:focus-visible {
  outline: 3px solid #087a59;
  outline-offset: 3px;
}

.ability-module:focus-visible {
  border-radius: 10px;
  outline: 3px solid #087a59;
  outline-offset: -3px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  margin: -1px;
  padding: 0;
  border: 0;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@keyframes ability-skeleton-pulse {
  to { background-position-x: -220%; }
}

@media (prefers-reduced-motion: reduce) {
  .ability-skeleton {
    animation: none;
  }

  .ability-module-icon {
    transition: none;
  }
}

@media (max-width: 900px) {
  .ability-module-row {
    display: flex;
    overflow-x: auto;
    padding-bottom: 8px;
    scroll-snap-type: x proximity;
  }

  .ability-module {
    min-width: 160px;
    scroll-snap-align: start;
  }
}

@media (max-width: 600px) {
  .ability-heading {
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 22px;
  }

  .ability-explanation-content {
    right: 0;
    left: auto;
  }

  .ability-summary-strip {
    grid-template-columns: 1fr;
  }

  .ability-summary-strip > span {
    min-height: 54px;
    justify-content: space-between;
    padding: 10px 14px;
  }

  .ability-summary-strip > span + span {
    border-top: 1px solid #dfe8ee;
    border-left: 0;
  }

  .ability-module-row {
    margin-top: 30px;
  }

  .ability-priority {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
    padding: 17px;
  }

  .ability-priority a,
  .ability-retry {
    width: 100%;
  }

  .ability-recent-evidence {
    grid-template-columns: auto auto minmax(0, 1fr) auto;
  }

  .ability-recent-evidence > time {
    display: none;
  }
}
</style>
