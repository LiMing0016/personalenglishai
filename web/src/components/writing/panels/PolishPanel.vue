<template>
  <div class="template-panel">
    <div class="template-hero">
      <div>
        <p class="template-eyebrow">作文模板提炼</p>
        <h3 class="template-title">按段落拆解，沉淀可复用句式</h3>
        <p class="template-desc">基于当前作文逐段分析，提炼每段的功能、句式模版和高级表达，方便下次直接套用。</p>
      </div>
      <button
        type="button"
        class="template-generate-btn"
        :disabled="loading || !canGenerate"
        @click="generateTemplates"
      >{{ loading ? '提炼中...' : hasResult ? '重新提炼' : '开始提炼' }}</button>
    </div>

    <div v-if="!canGenerate" class="template-empty-block">
      <p class="template-empty-title">当前内容还不够生成模板</p>
      <p class="template-empty-text">先完成一段较完整的作文（至少60字），再提炼模板更有价值。</p>
    </div>

    <div v-else-if="loading" class="template-loading-block">
      <div class="skeleton-row"></div>
      <div class="skeleton-row short"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
    </div>

    <div v-else-if="error" class="template-error-block">
      <p class="template-error-text">{{ error }}</p>
      <button type="button" class="template-secondary-btn" @click="generateTemplates">重试</button>
    </div>

    <template v-else-if="result">
      <!-- 文章体裁标签 -->
      <div v-if="result.essayType" class="essay-type-badge">
        {{ result.essayType }}
      </div>

      <!-- 逐段模版卡片 -->
      <section
        v-for="para in result.paragraphs"
        :key="para.paragraphIndex"
        class="para-section"
        :class="{ 'para-section--expanded': expandedIdx === para.paragraphIndex }"
      >
        <div class="para-header" @click="toggleExpand(para.paragraphIndex)">
          <span class="para-index">P{{ para.paragraphIndex }}</span>
          <div class="para-header-text">
            <span class="para-function">{{ para.function }}</span>
            <span class="para-summary">{{ para.summary }}</span>
          </div>
          <span class="para-arrow">{{ expandedIdx === para.paragraphIndex ? '▾' : '▸' }}</span>
        </div>

        <div v-if="expandedIdx === para.paragraphIndex" class="para-body">
          <!-- 句式模版 -->
          <div v-if="para.templates.length > 0" class="para-block">
            <div class="block-label">
              <span class="block-icon">✏️</span> 句式模版
            </div>
            <div class="template-list">
              <div v-for="(tpl, ti) in para.templates" :key="ti" class="template-card">
                <div class="template-card-main">
                  <p class="template-text">{{ tpl.template }}</p>
                  <button type="button" class="copy-inline-btn" @click="copyText(tpl.template)">复制</button>
                </div>
                <!-- 占位符替换示例 -->
                <div v-if="tpl.placeholders && Object.keys(tpl.placeholders).length > 0" class="placeholder-area">
                  <div v-for="(examples, phName) in tpl.placeholders" :key="phName" class="placeholder-row">
                    <span class="ph-name">[{{ phName }}]</span>
                    <span class="ph-arrow">→</span>
                    <span v-for="(ex, exi) in examples" :key="exi" class="ph-example">{{ ex }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 高级表达 -->
          <div v-if="para.keyExpressions.length > 0" class="para-block">
            <div class="block-label">
              <span class="block-icon">💎</span> 高级表达
            </div>
            <ul class="expr-list">
              <li v-for="(expr, ei) in para.keyExpressions" :key="ei" class="expr-item">
                <div class="expr-header">
                  <span class="expr-word">{{ expr.expression }}</span>
                  <span v-if="expr.usage" class="expr-usage">{{ expr.usage }}</span>
                </div>
                <div v-if="expr.usageTips && expr.usageTips.length > 0" class="expr-tips">
                  <span v-for="(tip, eti) in expr.usageTips" :key="eti" class="expr-tip-tag">{{ tip }}</span>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <!-- 使用建议 -->
      <section v-if="result.usageTips.length > 0" class="tips-section">
        <div class="block-label">
          <span class="block-icon">💡</span> 套用提醒
        </div>
        <ul class="tips-list">
          <li v-for="tip in result.usageTips" :key="tip">{{ tip }}</li>
        </ul>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { extractWritingTemplate, type WritingTemplateResponse } from '@/api/writing'
import { showToast } from '@/utils/toast'
import {
  clearWritingTemplateResult,
  loadWritingTemplateResult,
  saveWritingTemplateResult,
} from '@/components/writing/editorShellStorage'

const props = withDefaults(defineProps<{
  fullEssay?: string
  taskPrompt?: string
  studyStage?: string | null
  writingMode?: 'free' | 'exam'
}>(), {
  fullEssay: '',
  taskPrompt: '',
  studyStage: null,
  writingMode: 'free',
})

const loading = ref(false)
const error = ref<string | null>(null)
const result = ref<WritingTemplateResponse | null>(null)
const expandedIdx = ref<number | null>(null)

const canGenerate = computed(() => props.fullEssay.trim().length >= 60)
const hasResult = computed(() => result.value !== null && result.value.paragraphs.length > 0)

function currentSnapshot() {
  return {
    essaySnapshot: props.fullEssay,
    taskPromptSnapshot: props.taskPrompt ?? '',
  }
}

function restoreCache() {
  const cached = loadWritingTemplateResult()
  if (!cached) return
  const snapshot = currentSnapshot()
  if (cached.essaySnapshot !== snapshot.essaySnapshot || cached.taskPromptSnapshot !== snapshot.taskPromptSnapshot) {
    return
  }
  result.value = {
    essayType: (cached as any).essayType ?? null,
    paragraphs: (cached.paragraphs ?? []) as WritingTemplateResponse['paragraphs'],
    usageTips: cached.usageTips ?? [],
  }
  // 默认展开第一段
  if (result.value.paragraphs.length > 0) {
    expandedIdx.value = result.value.paragraphs[0].paragraphIndex
  }
}

onMounted(() => {
  restoreCache()
})

watch(() => [props.fullEssay, props.taskPrompt], () => {
  error.value = null
  const cached = loadWritingTemplateResult()
  const snapshot = currentSnapshot()
  if (cached && cached.essaySnapshot === snapshot.essaySnapshot && cached.taskPromptSnapshot === snapshot.taskPromptSnapshot) {
    // Props hydrated after mount — restore cache if result is still empty
    if (!result.value) {
      restoreCache()
    }
    return
  }
  result.value = null
  clearWritingTemplateResult()
})

async function generateTemplates() {
  if (!canGenerate.value || loading.value) return
  loading.value = true
  error.value = null
  try {
    const res = await extractWritingTemplate({
      text: props.fullEssay,
      taskPrompt: props.taskPrompt?.trim() || undefined,
      studyStage: props.studyStage,
      writingMode: props.writingMode ?? 'free',
    })
    result.value = res
    const snapshot = currentSnapshot()
    saveWritingTemplateResult({
      essayType: res.essayType ?? null,
      paragraphs: res.paragraphs,
      usageTips: res.usageTips,
      essaySnapshot: snapshot.essaySnapshot,
      taskPromptSnapshot: snapshot.taskPromptSnapshot,
    })
    // 默认展开第一段
    if (res.paragraphs.length > 0) {
      expandedIdx.value = res.paragraphs[0].paragraphIndex
    }
  } catch (e: any) {
    error.value = e?.message ?? '模板提炼失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function toggleExpand(idx: number) {
  expandedIdx.value = expandedIdx.value === idx ? null : idx
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    showToast('已复制模板', 'success')
  } catch {
    showToast('复制失败，请手动选择文本', 'error')
  }
}
</script>

<style scoped>
.template-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.template-hero {
  display: flex;
  gap: 14px;
  justify-content: space-between;
  align-items: flex-start;
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f0fdf4 0%, #ecfeff 100%);
  border: 1px solid #d1fae5;
}

.template-eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #047857;
}

.template-title {
  margin: 0 0 8px;
  font-size: 18px;
  line-height: 1.2;
  color: #0f172a;
}

.template-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
  max-width: 460px;
}

.template-generate-btn,
.template-secondary-btn,
.copy-inline-btn {
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.template-generate-btn {
  flex-shrink: 0;
  padding: 10px 16px;
  border-radius: 10px;
  background: #047857;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.template-generate-btn:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.template-empty-block,
.template-error-block,
.template-loading-block {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.template-empty-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 700;
  color: #334155;
}

.template-empty-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}

.template-error-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: #991b1b;
}

.template-secondary-btn {
  padding: 8px 14px;
  border-radius: 8px;
  background: #fee2e2;
  color: #991b1b;
  font-size: 12px;
  font-weight: 700;
}

/* ── 体裁标签 ── */
.essay-type-badge {
  display: inline-flex;
  align-self: flex-start;
  padding: 4px 12px;
  border-radius: 20px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  font-size: 12px;
  font-weight: 700;
  color: #047857;
}

/* ── 段落卡片 ── */
.para-section {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  overflow: hidden;
  transition: border-color 0.15s;
}

.para-section--expanded {
  border-color: #93c5fd;
}

.para-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  user-select: none;
}

.para-header:hover {
  background: #f9fafb;
}

.para-index {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #0f766e;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
}

.para-header-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.para-function {
  font-size: 13px;
  font-weight: 700;
  color: #1f2937;
}

.para-summary {
  font-size: 12px;
  color: #6b7280;
}

.para-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: #9ca3af;
}

.para-body {
  padding: 0 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── 区块标签 ── */
.para-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.block-label {
  font-size: 12px;
  font-weight: 700;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 4px;
}

.block-icon {
  font-size: 13px;
}

/* ── 句式模版卡片 ── */
.template-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.template-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f0fdf4;
  border: 1px solid #d1fae5;
}

.template-card-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.template-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #1f2937;
  white-space: pre-wrap;
}

.copy-inline-btn {
  flex-shrink: 0;
  padding: 4px 8px;
  background: #dcfce7;
  color: #047857;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
}

.copy-inline-btn:hover {
  background: #bbf7d0;
}

/* ── 占位符替换示例 ── */
.placeholder-area {
  padding-top: 4px;
  border-top: 1px dashed #d1fae5;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.placeholder-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.ph-name {
  font-size: 11px;
  font-weight: 700;
  color: #0f766e;
  font-family: monospace;
}

.ph-arrow {
  font-size: 11px;
  color: #9ca3af;
}

.ph-example {
  font-size: 11px;
  color: #374151;
  padding: 1px 6px;
  background: #fff;
  border: 1px solid #d1fae5;
  border-radius: 4px;
}

/* ── 高级表达 ── */
.expr-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.expr-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
}

.expr-header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
}

.expr-word {
  font-size: 13px;
  font-weight: 700;
  color: #1d4ed8;
}

.expr-usage {
  font-size: 12px;
  color: #6b7280;
}

.expr-tips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.expr-tip-tag {
  font-size: 11px;
  color: #4338ca;
  padding: 1px 6px;
  background: #e0e7ff;
  border-radius: 4px;
}

/* ── 使用建议 ── */
.tips-section {
  padding: 14px;
  border-radius: 12px;
  background: #fffbeb;
  border: 1px solid #fef3c7;
}

.tips-list {
  margin: 8px 0 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tips-list li {
  font-size: 13px;
  line-height: 1.6;
  color: #92400e;
}

/* ── Skeleton ── */
.skeleton-row,
.skeleton-card {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 10px;
}

.skeleton-row {
  height: 18px;
  margin-bottom: 10px;
}

.skeleton-row.short {
  width: 62%;
}

.skeleton-card {
  height: 76px;
  margin-top: 12px;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
