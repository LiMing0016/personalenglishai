<template>
  <div class="writing-page" :class="{ resizing }">
    <!-- MainCanvas：文档编辑区，占据剩余空间 -->
    <div class="main-canvas">
      <div class="doc-canvas">
        <div class="doc-paper">
          <div
            ref="docEl"
            class="doc-editor"
            :class="{ empty: !essay.trim() && !correctionMode }"
            :contenteditable="!correctionMode"
            data-placeholder="在此输入英文作文…"
            @input="syncEssayFromDoc"
            @paste="onDocPaste"
          />
        </div>
        <p v-if="correctionMode" class="locked-notice">当前为订正模式，正文已锁定</p>
        <div class="doc-footer">
          <span class="word-count">{{ wordCount }} 词</span>
          <div class="actions">
            <Button variant="secondary" :disabled="submitting" @click="handleClear">
              清空
            </Button>
            <Button
              variant="primary"
              :disabled="!essay.trim() || correctionMode"
              :loading="submitting"
              @click="handleSubmit"
            >
              {{ submitting ? '评估中…' : '提交' }}
            </Button>
          </div>
        </div>
      </div>
    </div>

    <!-- Splitter：可拖拽分隔条 -->
    <div
      ref="splitterRef"
      class="splitter"
      role="separator"
      aria-orientation="vertical"
      aria-valuenow="0"
      tabindex="0"
      @pointerdown="onSplitterPointerDown"
    />

    <!-- RightDock：右侧工作区列，宽度可拖拽调整 -->
    <div
      class="right-dock"
      :style="rightDockStyle"
    >
      <div
        class="inspector-drawer"
        :class="[inspectorSize, { open: activeInspectorMode !== null }]"
      >
        <div class="inspector-header">
          <span class="inspector-title">{{ activeModeLabel }}</span>
          <div class="inspector-header-actions">
            <span class="size-switcher">
              <button
                v-for="s in sizeOptions"
                :key="s"
                type="button"
                class="size-btn"
                :class="{ active: inspectorSize === s }"
                @click="inspectorSize = s"
              >
                {{ s }}
              </button>
            </span>
            <button type="button" class="inspector-close" title="关闭" @click="closeInspector">×</button>
          </div>
        </div>
        <div class="inspector-body">
          <!-- 评分 -->
          <template v-if="activeInspectorMode === 'score'">
            <template v-if="result">
              <div class="score-block">
                <span class="score-value">{{ result.raw_score }}</span>
                <span class="score-max">/ 100</span>
              </div>
              <div class="dimensions">
                <div v-for="item in dimensionItems" :key="item.key" class="dim-row">
                  <span class="dim-label">{{ item.label }} <span class="dim-max">满分 {{ item.max }}</span></span>
                  <div class="progress-wrap">
                    <div class="progress-bar" :style="{ width: item.percent + '%' }" />
                  </div>
                  <span class="dim-value">{{ item.current }} / {{ item.max }}</span>
                </div>
              </div>
              <div class="error-tags-block">
                <span class="error-label">错误类型统计（处）</span>
                <div class="tag-list">
                  <span v-for="(count, tag) in result.error_tags" :key="String(tag)" class="tag">{{ tag }} {{ count }}</span>
                </div>
              </div>
              <div class="report-summary">
                <span class="summary-label">讲评</span>
                <p class="summary-text">{{ result.summary }}</p>
              </div>
              <div class="next-step">
                <span class="next-step-label">下一步建议</span>
                <div class="next-step-actions">
                  <Button variant="primary" @click="startCorrection">开始订正</Button>
                  <Button variant="secondary" @click="skipCorrection">暂不订正</Button>
                </div>
              </div>
            </template>
            <div v-else class="inspector-empty">
              <p>提交作文后显示评分报告</p>
            </div>
          </template>
          <!-- 订正 -->
          <template v-else-if="activeInspectorMode === 'revise'">
            <div class="correction-area-inner">
              <p class="correction-area-desc">根据评估中的错误类型，修改下列典型问题句。</p>
              <div v-for="item in correctionItems" :key="item.id" class="correction-item">
                <span class="correction-item-type">{{ item.errorTypeLabel }}</span>
                <p class="correction-original">原句：{{ item.originalSentence }}</p>
                <div class="correction-input-row">
                  <input v-model="item.studentAnswer" type="text" class="correction-input" placeholder="在此输入你的订正" />
                  <button type="button" class="btn-hint" @click="item.showHint = !item.showHint">
                    {{ item.showHint ? '收起提示' : '查看提示' }}
                  </button>
                </div>
                <p v-show="item.showHint" class="correction-hint">提示：{{ item.hint }}</p>
                <div class="correction-item-footer">
                  <Button
                    v-if="!item.submitted"
                    variant="primary"
                    :disabled="!item.studentAnswer.trim()"
                    class="btn-submit-item"
                    @click="submitCorrectionItem(item)"
                  >
                    提交订正
                  </Button>
                  <span v-else class="submitted-badge">已提交</span>
                </div>
              </div>
            </div>
          </template>
          <!-- 提升（占位） -->
          <template v-else-if="activeInspectorMode === 'improve'">
            <div class="inspector-empty">
              <p>提升（润色/高级词/同义替换）即将上线</p>
            </div>
          </template>
          <!-- 解释（占位） -->
          <template v-else-if="activeInspectorMode === 'explain'">
            <div class="inspector-empty">
              <p>解释（语法/句法分析）即将上线</p>
            </div>
          </template>
          <!-- 翻译（占位） -->
          <template v-else-if="activeInspectorMode === 'translate'">
            <div class="inspector-empty">
              <p>翻译（整篇翻译）即将上线</p>
            </div>
          </template>
          <!-- 归档 -->
          <template v-else-if="activeInspectorMode === 'archive'">
            <div class="archive-panel">
              <p class="archive-desc">将本次提交的写作记录保存到归档：原文、补充说明、评分、维度、错误标签、讲评与时间。</p>
              <template v-if="result">
                <Button
                  variant="primary"
                  :disabled="currentSubmissionArchived || archiving"
                  :loading="archiving"
                  class="archive-btn"
                  @click="doArchive"
                >
                  {{ currentSubmissionArchived ? '已归档' : '一键归档此篇' }}
                </Button>
                <p v-if="currentSubmissionArchived" class="archive-done">本篇已归档</p>
              </template>
              <p v-else class="inspector-empty">提交作文后可在此归档</p>
            </div>
          </template>
        </div>
      </div>
      <!-- 右侧胶囊工具栏（贴在 RightDock 内缘） -->
      <div class="toolbar-pill">
        <button
          v-for="item in toolbarItems"
          :key="item.mode"
          type="button"
          class="pill-btn"
          :class="{
            active: activeInspectorMode === item.mode,
            highlighted: item.mode === 'archive' && currentSubmissionArchived,
          }"
          :title="item.title"
          @click="onToolbarClick(item.mode)"
        >
          <span class="pill-btn-dot" aria-hidden="true" />
          <span class="pill-btn-icon" aria-hidden="true">{{ item.icon }}</span>
          <span v-if="item.mode === 'archive' && currentSubmissionArchived" class="pill-btn-check">✓</span>
        </button>
      </div>
    </div>

    <AskAiBar v-model="notes" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Button from '@/components/Button.vue'
import AskAiBar from '@/components/AskAiBar.vue'
import { showToast } from '@/utils/toast'

export interface WritingAssessmentResult {
  raw_score: number
  dimension_scores: {
    grammar: number
    vocabulary: number
    structure: number
    expression: number
  }
  error_tags: Record<string, number>
  summary: string
}

export interface CorrectionItem {
  id: string
  errorType: string
  errorTypeLabel: string
  originalSentence: string
  hint: string
  studentAnswer: string
  showHint: boolean
  submitted: boolean
}

const DIMENSION_CONFIG = [
  { key: 'grammar', label: '语法', max: 30 },
  { key: 'vocabulary', label: '词汇', max: 25 },
  { key: 'structure', label: '结构', max: 25 },
  { key: 'expression', label: '表达', max: 20 },
] as const

const essay = ref('')
const result = ref<WritingAssessmentResult | null>(null)
const submitting = ref(false)
const docEl = ref<HTMLDivElement | null>(null)

const wordCount = computed(() => {
  const t = essay.value.trim()
  if (!t) return 0
  return t.split(/\s+/).filter(Boolean).length
})

function syncEssayFromDoc() {
  if (docEl.value) essay.value = docEl.value.innerText ?? ''
}

function onDocPaste(e: ClipboardEvent) {
  e.preventDefault()
  const text = e.clipboardData?.getData('text/plain') ?? ''
  document.execCommand?.('insertText', false, text)
  syncEssayFromDoc()
}

const dimensionItems = computed(() => {
  if (!result.value) return []
  const d = result.value.dimension_scores
  return DIMENSION_CONFIG.map(({ key, label, max }) => ({
    key,
    label,
    max,
    current: d[key],
    percent: Math.min(100, (d[key] / max) * 100),
  }))
})

const notes = ref('')
const correctionMode = ref(false)
const lockedEssay = ref('')
const correctionItems = ref<CorrectionItem[]>([])

type InspectorSize = 'sm' | 'md' | 'lg'
type InspectorMode = 'score' | 'revise' | 'improve' | 'explain' | 'translate' | 'archive'

const sizeOptions: InspectorSize[] = ['sm', 'md', 'lg']

const toolbarItems: { mode: InspectorMode; label: string; title: string; icon: string }[] = [
  { mode: 'score', label: '评分', title: '评分报告', icon: '◆' },
  { mode: 'revise', label: '订正', title: '订正练习', icon: '✎' },
  { mode: 'improve', label: '提升', title: '提升（润色/高级词/同义替换）', icon: '↑' },
  { mode: 'explain', label: '解释', title: '解释（语法/句法分析）', icon: '?.' },
  { mode: 'translate', label: '翻译', title: '翻译', icon: '⇄' },
  { mode: 'archive', label: '归档', title: '归档', icon: '▣' },
]

const activeInspectorMode = ref<InspectorMode | null>(null)
const inspectorSize = ref<InspectorSize>('md')
const currentSubmissionArchived = ref(false)
const archiving = ref(false)
const archivedList = ref<ArchivedRecord[]>([])

interface ArchivedRecord {
  id: string
  essayText: string
  instructionText: string
  result: WritingAssessmentResult
  archivedAt: string
}

const activeModeLabel = computed(() => {
  if (activeInspectorMode.value === null) return ''
  const labels: Record<InspectorMode, string> = {
    score: '评分报告',
    revise: '订正练习',
    improve: '提升',
    explain: '解释',
    translate: '翻译',
    archive: '归档',
  }
  return labels[activeInspectorMode.value]
})

/* Resizable split: RightDock 宽度由拖拽决定 */
const SPLITTER_WIDTH = 8
const RIGHT_DOCK_MIN = 320
const rightDockWidthPx = ref(400)
const splitterRef = ref<HTMLElement | null>(null)
const resizing = ref(false)
const resizePointerId = ref<number | null>(null)

const rightDockStyle = computed(() => ({ width: `${rightDockWidthPx.value}px` }))

function getMaxRightWidth() {
  return Math.floor(window.innerWidth * 0.5)
}

function onSplitterPointerDown(e: PointerEvent) {
  if (e.button !== 0) return
  resizing.value = true
  resizePointerId.value = e.pointerId
  ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  window.addEventListener('pointermove', onResizePointerMove)
  window.addEventListener('pointerup', onResizePointerUp)
  window.addEventListener('pointercancel', onResizePointerUp)
}

function onResizePointerMove(e: PointerEvent) {
  if (!resizing.value) return
  const maxW = getMaxRightWidth()
  let w = window.innerWidth - e.clientX - SPLITTER_WIDTH
  w = Math.max(RIGHT_DOCK_MIN, Math.min(maxW, w))
  rightDockWidthPx.value = w
}

function onResizePointerUp(_e: PointerEvent) {
  resizing.value = false
  const el = splitterRef.value
  if (el && resizePointerId.value != null) {
    try { el.releasePointerCapture(resizePointerId.value) } catch (_) {}
  }
  resizePointerId.value = null
  window.removeEventListener('pointermove', onResizePointerMove)
  window.removeEventListener('pointerup', onResizePointerUp)
  window.removeEventListener('pointercancel', onResizePointerUp)
}

function onToolbarClick(mode: InspectorMode) {
  if (activeInspectorMode.value === mode) {
    activeInspectorMode.value = null
    return
  }
  activeInspectorMode.value = mode
  if (mode === 'revise') inspectorSize.value = 'lg'
  else if (inspectorSize.value === 'lg') inspectorSize.value = 'md'
}

function closeInspector() {
  activeInspectorMode.value = null
}

function handleClear() {
  essay.value = ''
  notes.value = ''
  result.value = null
  correctionMode.value = false
  lockedEssay.value = ''
  correctionItems.value = []
  activeInspectorMode.value = null
  currentSubmissionArchived.value = false
  if (docEl.value) {
    docEl.value.contentEditable = 'true'
    docEl.value.innerText = ''
  }
}

function startCorrection() {
  if (!result.value) return
  if (docEl.value) {
    lockedEssay.value = docEl.value.innerText ?? essay.value
    docEl.value.contentEditable = 'false'
    docEl.value.innerText = lockedEssay.value
  } else {
    lockedEssay.value = essay.value
  }
  correctionMode.value = true
  correctionItems.value = buildCorrectionItems(result.value)
  activeInspectorMode.value = 'revise'
  inspectorSize.value = 'lg'
}

async function doArchive() {
  if (!result.value || currentSubmissionArchived.value || archiving.value) return
  archiving.value = true
  const record: ArchivedRecord = {
    id: `arch-${Date.now()}`,
    essayText: lockedEssay.value || essay.value,
    instructionText: notes.value,
    result: result.value,
    archivedAt: new Date().toISOString(),
  }
  await new Promise((r) => setTimeout(r, 400))
  archivedList.value.push(record)
  currentSubmissionArchived.value = true
  archiving.value = false
  showToast('归档成功', 'success')
}

function skipCorrection() {
  // 暂不订正：无操作，仅关闭或不做任何事
}

function buildCorrectionItems(res: WritingAssessmentResult): CorrectionItem[] {
  const tags = res.error_tags || {}
  const items: CorrectionItem[] = []
  if (tags.tense) {
    items.push({
      id: 'tense-1',
      errorType: 'tense',
      errorTypeLabel: '时态',
      originalSentence: 'Yesterday I go to the library.',
      hint: '叙述过去发生的事应使用过去时。',
      studentAnswer: '',
      showHint: false,
      submitted: false,
    })
  }
  if (tags.article) {
    items.push({
      id: 'article-1',
      errorType: 'article',
      errorTypeLabel: '冠词',
      originalSentence: 'I saw a owl in the tree.',
      hint: 'owl 以元音音素开头，前面应用 an。',
      studentAnswer: '',
      showHint: false,
      submitted: false,
    })
  }
  if (tags.collocation) {
    items.push({
      id: 'collocation-1',
      errorType: 'collocation',
      errorTypeLabel: '搭配',
      originalSentence: 'I made my homework.',
      hint: '「做作业」常用 do homework。',
      studentAnswer: '',
      showHint: false,
      submitted: false,
    })
  }
  return items
}

function submitCorrectionItem(item: CorrectionItem) {
  if (!item.studentAnswer.trim()) return
  item.submitted = true
}

async function handleSubmit() {
  if (!essay.value.trim() || submitting.value) return
  submitting.value = true
  void essay.value.trim()
  void notes.value.trim()
  await new Promise((r) => setTimeout(r, 600))
  result.value = getMockResult()
  submitting.value = false
  currentSubmissionArchived.value = false
  activeInspectorMode.value = 'score'
  inspectorSize.value = 'md'
}

function getMockResult(): WritingAssessmentResult {
  return {
    raw_score: 72,
    dimension_scores: {
      grammar: 22,
      vocabulary: 18,
      structure: 20,
      expression: 12,
    },
    error_tags: {
      tense: 3,
      article: 2,
      collocation: 1,
    },
    summary: '整体完成度不错，结构清晰。建议重点注意时态一致性与冠词使用，词汇上可适当增加衔接词。订正后可再次提交评估。',
  }
}
</script>

<style scoped>
.writing-page {
  display: flex;
  flex-direction: row;
  padding: 0 0 120px;
  min-height: 100vh;
  max-width: none;
  margin: 0;
  box-sizing: border-box;
}

.writing-page.resizing {
  user-select: none;
  cursor: col-resize;
}

.main-canvas {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: #f5f6f7;
}

.splitter {
  flex-shrink: 0;
  width: 8px;
  cursor: col-resize;
  background: #e5e7eb;
  transition: background 0.15s ease;
}

.splitter:hover {
  background: #d1d5db;
}

.right-dock {
  flex-shrink: 0;
  min-width: 320px;
  max-width: 50vw;
  display: flex;
  flex-direction: row;
  align-items: stretch;
  background: #fafafa;
  border-left: 1px solid #e5e7eb;
  overflow: hidden;
}

.doc-canvas {
  min-height: 100%;
  padding: 32px 24px 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
}

.doc-paper {
  width: 100%;
  max-width: 820px;
  min-height: 480px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  padding: 48px 56px 56px;
  box-sizing: border-box;
}

.doc-editor {
  min-height: 360px;
  font-size: 16px;
  line-height: 1.75;
  color: #1a1a1a;
  outline: none;
  font-family: Georgia, 'Times New Roman', serif;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.doc-editor.empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
}

.doc-editor:focus {
  outline: none;
}

.doc-editor[contenteditable='false'] {
  cursor: default;
  background: transparent;
}

.locked-notice {
  margin: 16px 0 0;
  font-size: 12px;
  color: #6b7280;
  text-align: center;
}

.doc-footer {
  margin-top: 24px;
  width: 100%;
  max-width: 820px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.word-count {
  font-size: 12px;
  color: #6b7280;
}

.actions {
  display: flex;
  gap: 10px;
}

/* RightDock 内：Inspector 抽屉 + 胶囊工具栏 */
.toolbar-pill {
  flex-shrink: 0;
  align-self: center;
  margin-right: 8px;
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  padding: 12px 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.pill-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  padding: 0;
  font-size: 16px;
  line-height: 1;
  color: #047857;
  background: transparent;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.pill-btn:hover {
  background: rgba(4, 120, 87, 0.1);
  color: #065f46;
}

.pill-btn.active {
  background: #047857;
  color: #fff;
}

.pill-btn.highlighted:not(.active) {
  color: #059669;
}

.pill-btn-dot {
  position: absolute;
  left: 6px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.pill-btn.active .pill-btn-dot {
  opacity: 1;
  background: #fff;
}

.pill-btn-icon {
  font-size: 18px;
}

.pill-btn-check {
  position: absolute;
  top: 4px;
  right: 6px;
  font-size: 10px;
  color: #059669;
}

.pill-btn.active .pill-btn-check {
  color: #fff;
}

.inspector-drawer {
  width: 0;
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: width 0.2s ease, min-width 0.2s ease, flex 0.2s ease;
}

.inspector-drawer.open {
  overflow: hidden;
  flex: 1;
  min-width: 0;
  width: auto;
}

.inspector-drawer.open.sm {
  max-width: 280px;
}

.inspector-drawer.open.md {
  max-width: 360px;
}

.inspector-drawer.open.lg {
  max-width: 440px;
}

.inspector-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #e5e7eb;
}

.inspector-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.inspector-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.size-switcher {
  display: flex;
  gap: 2px;
}

.size-btn {
  padding: 2px 6px;
  font-size: 11px;
  color: #6b7280;
  background: #f3f4f6;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.size-btn:hover {
  color: #111827;
}

.size-btn.active {
  background: #e5e7eb;
  color: #111827;
}

.inspector-close {
  padding: 0 6px;
  font-size: 18px;
  line-height: 1;
  color: #6b7280;
  background: none;
  border: none;
  cursor: pointer;
}

.inspector-close:hover {
  color: #111827;
}

.inspector-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
}

.inspector-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #6b7280;
}

.archive-panel {
  padding: 0;
}

.archive-desc {
  margin: 0 0 14px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

.archive-btn {
  margin-bottom: 8px;
}

.archive-done {
  margin: 0;
  font-size: 12px;
  color: #059669;
}

/* 订正练习（Inspector 内） */
.correction-area-inner {
  padding: 0;
}

.correction-area-desc {
  margin: 0 0 14px;
  font-size: 13px;
  color: #6b7280;
}

.correction-item {
  padding: 14px 0;
  border-top: 1px solid #e5e7eb;
}

.correction-item:first-of-type {
  border-top: none;
  padding-top: 0;
}

.correction-item-type {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
  padding: 2px 8px;
  background: #f3f4f6;
  border-radius: 4px;
}

.correction-original {
  margin: 0 0 10px;
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
}

.correction-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.correction-input {
  flex: 1;
  padding: 8px 12px;
  font-size: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  box-sizing: border-box;
}

.correction-input:focus {
  outline: none;
  border-color: #2563eb;
}

.btn-hint {
  flex-shrink: 0;
  padding: 8px 12px;
  font-size: 12px;
  color: #6b7280;
  background: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  cursor: pointer;
}

.btn-hint:hover {
  background: #f9fafb;
  color: #111827;
}

.correction-hint {
  margin: 0 0 10px;
  padding: 8px 12px;
  font-size: 12px;
  color: #6b7280;
  background: #f9fafb;
  border-radius: 4px;
  border-left: 3px solid #2563eb;
}

.correction-item-footer {
  margin-top: 8px;
}

.btn-submit-item {
  margin-top: 4px;
}

.submitted-badge {
  font-size: 12px;
  color: #059669;
}

/* 下一步建议（评估报告内） */
.next-step {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.next-step-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 10px;
}

.next-step-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.score-block {
  margin-bottom: 16px;
  padding-top: 4px;
  text-align: left;
}

.score-value {
  font-size: 42px;
  font-weight: 700;
  color: #1e40af;
  line-height: 1;
}

.score-max {
  font-size: 20px;
  font-weight: 500;
  color: #6b7280;
  margin-left: 2px;
}

.dimensions {
  margin-bottom: 12px;
}

.dim-row {
  display: grid;
  grid-template-columns: 88px 1fr 52px;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.dim-row:last-of-type {
  margin-bottom: 0;
}

.dim-label {
  font-size: 12px;
  color: #111827;
}

.dim-max {
  font-weight: 400;
  color: #6b7280;
  font-size: 11px;
}

.progress-wrap {
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: #1e40af;
  border-radius: 3px;
}

.dim-value {
  font-size: 12px;
  color: #6b7280;
  text-align: right;
}

.error-tags-block {
  margin-bottom: 12px;
}

.error-label {
  display: block;
  font-size: 12px;
  color: #111827;
  margin-bottom: 6px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  font-size: 12px;
  color: #374151;
  background: #f3f4f6;
  border-radius: 4px;
}

.report-summary {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.summary-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 8px;
}

.summary-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: #374151;
}
</style>
