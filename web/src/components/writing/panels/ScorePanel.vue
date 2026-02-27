<template>
  <div class="score-panel">
    <template v-if="evaluateResult">
      <div class="score-block">
        <span class="score-value">{{ evaluateResult.score.overall }}</span>
        <span class="score-max">/ 100</span>
      </div>
      <div class="dimensions">
        <div v-for="d in dimensionItems" :key="d.key" class="dim-row">
          <span class="dim-label">{{ d.label }}</span>
          <div class="progress-wrap">
            <div class="progress-bar" :style="{ width: d.percent + '%' }" />
          </div>
          <span class="dim-value">{{ d.current }} / {{ d.max }}</span>
        </div>
      </div>
      <div v-if="evaluateResult.errors?.length" class="errors-block">
        <span class="errors-label">错误与建议</span>
        <ul class="errors-list">
          <li
            v-for="err in evaluateResult.errors"
            :key="err.id"
            class="error-item"
          >
            <span class="error-meta">{{ err.type }} · {{ err.severity }}</span>
            <span v-if="err.suggestion" class="error-suggestion">{{ err.suggestion }}</span>
          </li>
        </ul>
      </div>
      <div class="report-summary">
        <span class="summary-label">讲评</span>
        <p class="summary-text">{{ evaluateResult.summary }}</p>
      </div>
      <div class="actions">
        <button type="button" class="btn btn-primary" @click="$emit('start-fix')">开始订正</button>
        <button type="button" class="btn btn-secondary">暂不订正</button>
      </div>
    </template>
    <template v-else>
      <div class="score-empty">
        <p>提交作文后显示评价报告</p>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WritingEvaluateResponse } from '@/api/writing'

const props = withDefaults(
  defineProps<{
    evaluateResult?: WritingEvaluateResponse | null
  }>(),
  { evaluateResult: null }
)

defineEmits<{
  'start-fix': []
}>()

const DIM_MAX = 25
const dimensionItems = computed(() => {
  if (!props.evaluateResult?.score) return []
  const s = props.evaluateResult.score
  return [
    { key: 'task', label: '任务完成度', current: s.task, max: DIM_MAX, percent: Math.min(100, (s.task / DIM_MAX) * 100) },
    { key: 'coherence', label: '连贯', current: s.coherence, max: DIM_MAX, percent: Math.min(100, (s.coherence / DIM_MAX) * 100) },
    { key: 'lexical', label: '词汇', current: s.lexical, max: DIM_MAX, percent: Math.min(100, (s.lexical / DIM_MAX) * 100) },
    { key: 'grammar', label: '语法', current: s.grammar, max: DIM_MAX, percent: Math.min(100, (s.grammar / DIM_MAX) * 100) },
  ]
})
</script>

<style scoped>
.score-panel {
  padding: 16px;
}
.score-block {
  margin-bottom: 16px;
}
.score-value {
  font-size: 32px;
  font-weight: 700;
  color: #111827;
}
.score-max {
  font-size: 18px;
  color: #6b7280;
}
.dimensions {
  margin-bottom: 16px;
}
.dim-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
}
.dim-label {
  flex: 0 0 80px;
  color: #374151;
}
.progress-wrap {
  flex: 1;
  height: 8px;
  background: #e5e7eb;
  border-radius: 4px;
  overflow: hidden;
}
.progress-bar {
  height: 100%;
  background: #047857;
  border-radius: 4px;
  transition: width 0.2s ease;
}
.dim-value {
  flex: 0 0 50px;
  text-align: right;
  color: #6b7280;
  font-size: 12px;
}
.errors-block {
  margin-bottom: 16px;
}
.errors-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 8px;
}
.errors-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.error-item {
  padding: 8px 0;
  border-top: 1px solid #f3f4f6;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.error-item:first-child {
  border-top: none;
}
.error-meta {
  color: #6b7280;
  font-size: 12px;
}
.error-suggestion {
  color: #374151;
}
.report-summary {
  margin-bottom: 20px;
}
.summary-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}
.summary-text {
  margin: 0;
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
}
.actions {
  display: flex;
  gap: 10px;
}
.btn {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-primary {
  background: #047857;
  color: #fff;
}
.btn-primary:hover {
  background: #065f46;
}
.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}
.btn-secondary:hover {
  background: #e5e7eb;
}
.score-empty {
  padding: 32px 0;
  text-align: center;
  font-size: 14px;
  color: #9ca3af;
}
</style>
