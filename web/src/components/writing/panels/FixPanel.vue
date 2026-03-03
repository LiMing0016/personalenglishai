<template>
  <div class="fix-panel">
    <template v-if="errors.length">
      <div class="fix-header">
        <span class="fix-progress">{{ fixedCount }}/{{ totalFixable }} 已订正</span>
        <button
          v-if="!allFixed && totalFixable > 0"
          type="button"
          class="btn-fix-all"
          @click="emit('fix-all')"
        >一键全部订正</button>
      </div>

      <ul class="fix-list">
        <li
          v-for="err in errors"
          :key="err.id"
          class="fix-item"
          :class="{
            'fix-item--active': err.id === activeErrorId,
            'fix-item--fixed': isFixed(err.id),
          }"
          :data-error-id="err.id"
          @click="emit('error-click', err.id)"
        >
          <div class="fix-item-header">
            <span class="fix-type">{{ errorTypeLabel(err.type) }} · {{ err.severity === 'major' ? '严重' : '轻微' }}</span>
            <span v-if="isFixed(err.id)" class="badge-fixed">已修改</span>
            <button
              v-else-if="canFix(err)"
              type="button"
              class="btn-fix-single"
              @click.stop="emit('fix-error', err.id)"
            >替换</button>
          </div>
          <div v-if="err.original && hasValidSuggestion(err)" class="fix-correction">
            <span class="fix-original">{{ err.original }}</span>
            <span class="fix-arrow">&rarr;</span>
            <span class="fix-suggestion">{{ err.suggestion }}</span>
          </div>
          <div v-else-if="err.original" class="fix-correction">
            <span class="fix-original">{{ err.original }}</span>
          </div>
          <p v-if="err.reason" class="fix-reason">{{ err.reason }}</p>
        </li>
      </ul>

      <div v-if="allFixed" class="fix-done">
        <p class="fix-done-text">全部订正完成！</p>
      </div>
      <div class="fix-footer">
        <button
          v-if="hasSuggestions"
          type="button"
          class="btn btn-primary"
          @click="emit('start-polish')"
        >开始润色</button>
        <button type="button" class="btn btn-secondary" @click="emit('exit-correction')">{{ allFixed ? '暂不润色' : '退出订正' }}</button>
      </div>
    </template>

    <template v-else>
      <div class="fix-empty">
        <p class="fix-empty-text">没有需要订正的错误。</p>
        <div v-if="hasSuggestions" class="fix-done-actions">
          <button type="button" class="btn btn-primary" @click="emit('start-polish')">开始润色</button>
          <button type="button" class="btn btn-secondary" @click="emit('exit-correction')">暂不润色</button>
        </div>
        <div v-else class="fix-done-actions">
          <button type="button" class="btn btn-secondary" @click="emit('exit-correction')">返回</button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, nextTick } from 'vue'
import type { WritingEvaluateResponse } from '@/api/writing'

type ErrorItem = WritingEvaluateResponse['errors'][number]

const ERROR_TYPE_LABELS: Record<string, string> = {
  spelling: '拼写',
  morphology: '词形',
  subject_verb: '主谓一致',
  tense: '时态',
  article: '冠词',
  preposition: '介词',
  collocation: '搭配',
  syntax: '句法',
  word_choice: '用词',
  part_of_speech: '词性',
  punctuation: '标点',
  logic: '逻辑',
  grammar: '语法',
  expression: '表达',
  coherence: '连贯',
  format: '格式',
}

const props = defineProps<{
  errors: ErrorItem[]
  fixedErrorIds: Set<string>
  activeErrorId?: string | null
  hasSuggestions: boolean
}>()

const emit = defineEmits<{
  'fix-error': [errorId: string]
  'fix-all': []
  'error-click': [errorId: string]
  'exit-correction': []
  'start-polish': []
}>()

function errorTypeLabel(type: string): string {
  return ERROR_TYPE_LABELS[type] ?? type
}

function hasValidSuggestion(err: { original?: string; suggestion?: string }): boolean {
  const s = err.suggestion?.trim()
  if (!s || /^n\/?a$/i.test(s)) return false
  if (err.original && s === err.original.trim()) return false
  return true
}

function isFixableError(err: ErrorItem): boolean {
  return !!err.original && hasValidSuggestion(err)
}

function isFixed(errorId: string): boolean {
  return props.fixedErrorIds.has(errorId)
}

function canFix(err: ErrorItem): boolean {
  return isFixableError(err) && !isFixed(err.id)
}

const fixableErrors = computed(() => props.errors.filter(isFixableError))
const fixedCount = computed(() => fixableErrors.value.filter((e) => props.fixedErrorIds.has(e.id)).length)
const totalFixable = computed(() => fixableErrors.value.length)
const allFixed = computed(() => totalFixable.value > 0 && fixedCount.value >= totalFixable.value)

watch(
  () => props.activeErrorId,
  (id) => {
    if (!id) return
    nextTick(() => {
      const el = document.querySelector(`[data-error-id="${CSS.escape(id)}"]`)
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
  },
)
</script>

<style scoped>
.fix-panel {
  padding: 16px;
}

.fix-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.fix-progress {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #047857;
}

.btn-fix-all {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #047857;
  border-radius: 8px;
  background: #ecfdf5;
  color: #047857;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}
.btn-fix-all:hover { background: #d1fae5; }

.fix-list {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fix-item {
  padding: 10px;
  border-radius: 10px;
  background: #fefce8;
  border: 1px solid #fde68a;
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
}
.fix-item:hover { background: #fef9c3; }
.fix-item--active { box-shadow: 0 0 0 2px #fbbf24; }
.fix-item--fixed { opacity: 0.5; }
.fix-item--fixed .fix-original { text-decoration: line-through; }

.fix-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.fix-item-header .fix-type { flex: 1; }

.fix-type {
  font-size: 12px;
  color: #6b7280;
}

.badge-fixed {
  display: inline-block;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 20px;
  background: #d1fae5;
  color: #065f46;
  white-space: nowrap;
}

.btn-fix-single {
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid #6366f1;
  border-radius: 6px;
  background: #eef2ff;
  color: #4338ca;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
}
.btn-fix-single:hover { background: #c7d2fe; }

.fix-correction {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.fix-original {
  background: #fee2e2;
  color: #991b1b;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: monospace;
  text-decoration: line-through;
  font-size: 12px;
}

.fix-arrow { color: #6b7280; }

.fix-suggestion {
  background: #d1fae5;
  color: #065f46;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: monospace;
  font-size: 12px;
}

.fix-reason {
  margin: 2px 0 0;
  font-size: 12px;
  color: #78350f;
  line-height: 1.4;
}

.fix-done {
  padding: 14px;
  border-radius: 12px;
  background: #f0fdf4;
  border: 1px solid #dcfce7;
  text-align: center;
}

.fix-done-text {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #065f46;
}

.fix-done-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

.fix-footer {
  display: flex;
  gap: 10px;
}

.fix-empty {
  padding: 32px 16px;
  text-align: center;
}

.fix-empty-text {
  margin: 0 0 12px;
  font-size: 14px;
  color: #6b7280;
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
.btn-primary { background: #047857; color: #fff; }
.btn-primary:hover { background: #065f46; }
.btn-secondary { background: #f3f4f6; color: #374151; }
.btn-secondary:hover { background: #e5e7eb; }
</style>
