<template>
  <div class="exam-setup gate-center">
    <div class="setup-card setup-card--wide">
      <button class="setup-back-link" type="button" @click="handleBack">
        &larr; 返回作文列表
      </button>

      <div class="workbench-header workbench-header--canvas">
        <div class="workbench-header-title">
          <h2 class="gate-title gate-title--centered">题目设计</h2>
        </div>
        <div class="workbench-header-actions">
          <div class="mode-switch" role="tablist" aria-label="写作模式">
            <button class="mode-switch-btn" :class="{ active: selectedMode === 'free' }" type="button" @click="handleModeSwitchRequest('free')">自由模式</button>
            <button class="mode-switch-btn" :class="{ active: selectedMode === 'exam' }" type="button" @click="handleModeSwitchRequest('exam')">考试模式</button>
          </div>
          <button class="gate-btn gate-btn--top" :disabled="!canEnterCurrentMode || workbenchBusy" @click="startWritingFromPreview">
            开始写作
          </button>
        </div>
      </div>

      <div class="workbench-board">
        <section class="workbench-pane workbench-pane--dialogue">
          <div class="conversation-shell">
            <div ref="conversationLogRef" class="conversation-log">
              <template v-if="hasConversationLog">
                <div
                  v-for="message in conversationMessages"
                  :key="message.id"
                  class="conversation-entry"
                  :class="{
                    'conversation-entry--user': message.role === 'user',
                    'conversation-entry--assistant': message.role === 'assistant',
                    'conversation-entry--status': message.role === 'status',
                  }"
                >
                  <div
                    v-if="message.kind === 'asset'"
                    class="asset-card"
                    :class="{ 'asset-card--visual': message.assetType === 'image' }"
                  >
                    <div class="asset-card-header">
                      <span class="asset-card-title">{{ getAssetMessageTitle(message) }}</span>
                    </div>
                    <p v-if="message.assetName" class="asset-card-name">{{ message.assetName }}</p>
                    <p v-if="message.assetSummary || message.text" class="asset-card-text">
                      {{ message.assetSummary || message.text }}
                    </p>
                    <img
                      v-if="message.assetType === 'image' && message.imageUrl"
                      :src="message.imageUrl"
                      class="asset-image"
                      alt="题目附件图片"
                    />
                  </div>

                  <div
                    v-else
                    class="conversation-bubble"
                    :class="{
                      'conversation-bubble--assistant': message.role === 'assistant',
                      'conversation-bubble--user': message.role === 'user',
                      'conversation-bubble--warning': message.tone === 'warning',
                      'conversation-bubble--success': message.tone === 'success',
                      'conversation-bubble--muted': message.role === 'status' || message.tone === 'muted',
                    }"
                  >
                    <p v-if="message.role === 'assistant' && message.replyKind" class="bubble-label">
                      {{ getAssistantReplyLabel(message.replyKind) }}
                    </p>
                    <p class="bubble-text" :class="{ 'bubble-text--pre': message.kind !== 'status' }">{{ message.text }}</p>
                  </div>
                </div>

                <div v-if="workbenchBusy" class="conversation-entry conversation-entry--assistant">
                  <div class="conversation-bubble conversation-bubble--assistant conversation-bubble--muted">
                    <p class="bubble-text">正在整理题单...</p>
                  </div>
                </div>
              </template>

              <div v-else class="conversation-empty">
                <p class="conversation-empty-title">从左侧对话开始设计题目</p>
                <p class="conversation-empty-text">输入需求，或插入图片、材料、真题。每轮对话后，右侧题单会自动刷新。</p>
              </div>
            </div>

            <div class="composer-frame">
              <div class="composer-shell">
                <div v-if="hasActiveAssets" class="composer-asset-strip">
                  <div v-if="selectedPrompt" class="composer-asset-chip">
                    <span class="composer-asset-chip-label">真题</span>
                    <span class="composer-asset-chip-text">{{ selectedPrompt.paper || selectedPrompt.title || '已选真题' }}</span>
                    <button type="button" class="composer-asset-chip-close" @click="removeSelectedPrompt">×</button>
                  </div>
                  <div v-if="uploadedImage" class="composer-asset-chip">
                    <span class="composer-asset-chip-label">图片</span>
                    <span class="composer-asset-chip-text">当前图片附件</span>
                    <button type="button" class="composer-asset-chip-action" @click="openAttachmentPicker">更换</button>
                    <button type="button" class="composer-asset-chip-action" :disabled="recognizing" @click="onRecognizeImage">{{ recognizing ? '识别中' : '识别文字' }}</button>
                    <button type="button" class="composer-asset-chip-close" @click="removeImage">×</button>
                  </div>
                  <div v-if="materialAttachmentText" class="composer-asset-chip">
                    <span class="composer-asset-chip-label">材料</span>
                    <span class="composer-asset-chip-text">{{ materialAttachmentName || '已加材料' }}</span>
                    <button type="button" class="composer-asset-chip-close" @click="clearMaterialAttachment">×</button>
                  </div>
                </div>
                <textarea
                  ref="topicInputRef"
                  v-model="topic"
                  class="workbench-input"
                  rows="2"
                  placeholder="例如：请帮我整理一道考研英语风格的作文题，主题是年轻人与家庭责任的平衡。我还想加一段材料。"
                  @keydown="onWorkbenchInputKeydown"
                />

                <div class="workbench-toolbar">
                  <div class="workbench-toolbar-left">
                    <div class="attachment-menu-wrap">
                      <button
                        class="attachment-trigger"
                        type="button"
                        :class="{ active: attachmentMenuOpen }"
                        @click="attachmentMenuOpen = !attachmentMenuOpen"
                      >
                        +
                      </button>
                      <div v-if="attachmentMenuOpen" class="attachment-menu">
                        <button class="attachment-menu-item" type="button" @click="openAttachmentPicker">添加图片和文件</button>
                        <button class="attachment-menu-item" type="button" @click="openPastPromptPicker">历年真题</button>
                      </div>
                    </div>
                    <div ref="providerMenuRef" class="provider-picker-wrap">
                      <button
                        type="button"
                        class="provider-picker-btn"
                        :aria-expanded="providerMenuOpen ? 'true' : 'false'"
                        title="选择题单整理模型"
                        @click.stop="providerMenuOpen = !providerMenuOpen"
                      >
                        <span class="provider-picker-label">
                          <span class="provider-picker-icon" aria-hidden="true">⚡</span>
                          {{ currentProviderLabel }}
                        </span>
                        <span class="provider-picker-caret" aria-hidden="true">▼</span>
                      </button>
                      <div v-if="providerMenuOpen" class="provider-menu" @click.stop>
                        <button
                          v-for="option in providerOptions"
                          :key="option.value"
                          type="button"
                          class="provider-menu-item"
                          :class="{ active: selectedAiProvider === option.value }"
                          @click="selectedAiProvider = option.value; providerMenuOpen = false; saveAiProviderNow(option.value)"
                        >
                          <span class="provider-menu-item-label">
                            <span class="provider-picker-icon" aria-hidden="true">⚡</span>
                            {{ option.label }}
                          </span>
                          <span v-if="selectedAiProvider === option.value" class="provider-menu-check" aria-hidden="true">✓</span>
                        </button>
                      </div>
                    </div>
                  </div>
                  <button
                    class="composer-submit"
                    :class="{ 'composer-submit--cancel': composerAction === 'cancel' }"
                    type="button"
                    :disabled="composerAction === 'submit' ? !canAssemble : false"
                    :title="composerAction === 'cancel' ? '停止整理' : '整理题单'"
                    :aria-label="composerAction === 'cancel' ? '停止整理' : '整理题单'"
                    @click="onComposerPrimaryAction"
                  >
                    <span v-if="composerAction === 'cancel'" class="composer-stop-icon" aria-hidden="true" />
                    <span v-else>&uarr;</span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <input
            ref="attachmentInputRef"
            type="file"
            class="file-input-hidden"
            accept="image/*,.txt,.md"
            @change="onWorkbenchAttachmentChange"
          />

          <p v-if="topic.trim() && topicError" class="field-error">{{ topicError }}</p>
          <p v-if="workbenchError" class="field-error">{{ workbenchError }}</p>
          <p v-if="recognizeError" class="field-error">{{ recognizeError }}</p>

        </section>

        <section class="workbench-pane workbench-pane--canvas">
          <div class="paper-frame paper-frame--canvas">
            <div class="paper-sheet paper-sheet--canvas">
              <template v-if="previewSheet">
                <div class="paper-status-bar" :class="previewStatusToneClass">
                  <span class="paper-status-bar__label">{{ previewStatusText }}</span>
                  <span v-if="previewMissingFields.length" class="paper-status-bar__meta">
                    {{ previewMissingFields.join(' · ') }}
                  </span>
                </div>

                <div v-if="showExamTaskSelector" class="paper-task-center">
                  <button
                    type="button"
                    class="paper-task-title"
                    @click="toggleExamTaskSelection"
                  >
                    {{ selectedExamTaskLabel }}
                  </button>
                </div>

                <h3 class="paper-directions">Directions:</h3>

                <div class="paper-block">
                  <p class="paper-prompt">{{ previewSheet.promptText }}</p>
                  <ul v-if="previewSheet.requirements.length" class="paper-requirements">
                    <li v-for="(requirement, index) in previewSheet.requirements" :key="`requirement-${index}`">{{ requirement }}</li>
                  </ul>
                  <p v-if="previewSheet.wordRange || previewSheet.score" class="paper-meta">
                    <span v-if="previewSheet.wordRange">字数要求：{{ previewSheet.wordRange }} 词</span>
                    <span v-if="previewSheet.score">满分：{{ previewSheet.score }} 分</span>
                  </p>
                </div>

                <div v-if="previewSheet.attachmentType !== 'none'" class="paper-block paper-attachment">
                  <p class="paper-attachment-heading">
                    {{ previewSheet.attachmentTitle || (previewSheet.attachmentType === 'material' ? 'Material' : 'Visual Attachment') }}
                  </p>
                  <template v-if="previewSheet.attachmentType === 'visual'">
                    <img
                      v-if="previewVisualAttachment.mode === 'image' && previewVisualAttachment.imageUrl"
                      :src="previewVisualAttachment.imageUrl"
                      class="paper-attachment-image"
                      alt="题目附件"
                    />

                    <div
                      v-else-if="previewVisualAttachment.mode === 'comic' && previewVisualAttachment.comicScenes.length"
                      class="paper-comic-grid"
                    >
                      <article
                        v-for="(scene, index) in previewVisualAttachment.comicScenes"
                        :key="`comic-scene-${index}`"
                        class="paper-comic-card"
                      >
                        <div class="paper-comic-visual">
                          <span class="paper-comic-badge">{{ scene.title || `Scene ${index + 1}` }}</span>
                          <div class="paper-comic-figure-row">
                            <span class="paper-comic-figure" />
                            <span class="paper-comic-figure" />
                            <span class="paper-comic-figure" />
                          </div>
                        </div>
                        <div class="paper-comic-body">
                          <p class="paper-comic-description">{{ scene.description }}</p>
                          <p v-if="scene.dialogue" class="paper-comic-dialogue">“{{ scene.dialogue }}”</p>
                        </div>
                      </article>
                    </div>

                    <div
                      v-else-if="(previewVisualAttachment.mode === 'chart' || previewVisualAttachment.mode === 'table') && previewVisualAttachment.chartSpec"
                      class="paper-chart-wrap"
                    >
                      <p v-if="previewVisualAttachment.chartSpec.title" class="paper-chart-title">
                        {{ previewVisualAttachment.chartSpec.title }}
                      </p>
                      <table class="paper-chart-table">
                        <thead>
                          <tr>
                            <th v-for="(column, index) in previewVisualAttachment.chartSpec.columns" :key="`chart-column-${index}`">
                              {{ column }}
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr
                            v-for="(row, rowIndex) in previewVisualAttachment.chartSpec.rows"
                            :key="`chart-row-${rowIndex}`"
                          >
                            <td v-for="(cell, cellIndex) in row" :key="`chart-cell-${rowIndex}-${cellIndex}`">
                              {{ cell }}
                            </td>
                          </tr>
                        </tbody>
                      </table>
                      <p v-if="previewVisualAttachment.chartSpec.summary" class="paper-chart-summary">
                        {{ previewVisualAttachment.chartSpec.summary }}
                      </p>
                    </div>

                    <div v-else-if="previewVisualAttachment.text" class="paper-attachment-content">
                      {{ previewVisualAttachment.text }}
                    </div>
                  </template>

                  <div v-else-if="previewSheet.attachmentContent" class="paper-attachment-content">
                    {{ previewSheet.attachmentContent }}
                  </div>
                </div>
              </template>

              <template v-else-if="canvasState === 'waiting'">
                <div v-if="showExamTaskSelector" class="paper-task-center">
                  <button
                    type="button"
                    class="paper-task-title"
                    @click="toggleExamTaskSelection"
                  >
                    {{ selectedExamTaskLabel }}
                  </button>
                </div>
                <div class="canvas-empty canvas-empty--waiting">
                  <p class="canvas-empty-label">Directions:</p>
                  <p class="canvas-empty-title">正在整理题单</p>
                  <p class="canvas-empty-text">AI 正在根据你刚刚提交的内容生成题单，请稍候。</p>
                </div>
              </template>

              <template v-else>
                <div v-if="showExamTaskSelector" class="paper-task-center">
                  <button
                    type="button"
                    class="paper-task-title"
                    @click="toggleExamTaskSelection"
                  >
                    {{ selectedExamTaskLabel }}
                  </button>
                </div>
                <div class="canvas-empty">
                  <p class="canvas-empty-label">Directions:</p>
                  <p class="canvas-empty-title">等待开始整理</p>
                  <p class="canvas-empty-text">请在左侧发出第一条命题消息，或插入图片、材料、真题。</p>
                </div>
              </template>
            </div>
          </div>
        </section>
      </div>

      <!-- 返回确认弹窗 -->
      <div v-if="showBackConfirm" class="confirm-overlay">
        <div class="confirm-card">
          <button class="confirm-close" @click="showBackConfirm = false" title="取消">&times;</button>
          <h3 class="confirm-title">是否保存草稿？</h3>
          <p class="confirm-sub">你已填写了部分题目信息，是否保存后再离开？</p>
          <div class="confirm-actions-3">
            <button class="gate-btn" :disabled="saving" @click="saveAndLeave">{{ saving ? '保存中...' : '保存并退出' }}</button>
            <button class="gate-btn gate-btn--danger" :disabled="saving" @click="confirmLeave">不保存退出</button>
          </div>
        </div>
      </div>

      <div v-if="showModeSwitchConfirm" class="confirm-overlay">
        <div class="confirm-card confirm-card--compact">
          <h3 class="confirm-title">切换模式将更改评分标准</h3>
          <div class="confirm-actions confirm-actions--split">
            <button class="btn-back" type="button" @click="cancelModeSwitch">取消</button>
            <button class="gate-btn" type="button" @click="confirmModeSwitch">继续</button>
          </div>
        </div>
      </div>

      <div v-if="showPastPromptPicker" class="confirm-overlay">
        <div class="confirm-card prompt-picker-card">
          <button class="confirm-close" @click="showPastPromptPicker = false" title="关闭">&times;</button>
          <h3 class="confirm-title">插入历年真题</h3>
          <p class="confirm-sub">选择一题插入当前工作台，再统一整理为标准题单。</p>

          <div class="prompt-filters">
            <div class="prompt-search">
              <input
                v-model="promptKeyword"
                type="text"
                class="prompt-search-input"
                placeholder="搜索题目关键词..."
                @input="onPromptSearch"
              />
            </div>
            <div class="prompt-year-filter">
              <select v-model.number="promptYearSelect" class="prompt-year-select" @change="onYearChange">
                <option :value="0">全部年份</option>
                <option v-for="y in promptYears" :key="y" :value="y">{{ y }} 年</option>
              </select>
            </div>
          </div>

          <div v-if="promptLoading" class="prompt-loading">
            <div class="gate-spinner" />
            <p>加载中...</p>
          </div>

          <div v-else-if="promptItems.length === 0" class="placeholder-box">
            <p class="placeholder-text">{{ promptKeyword || promptYear ? '未找到匹配的题目' : '暂无真题数据' }}</p>
            <p class="placeholder-sub">{{ promptKeyword || promptYear ? '请尝试其他关键词或年份' : '题库数据即将导入' }}</p>
          </div>

          <div v-else class="prompt-list">
            <div
              v-for="item in promptItems"
              :key="item.id"
              class="prompt-card"
              :class="{ selected: selectedPrompt?.id === item.id }"
              @click="selectedPrompt = selectedPrompt?.id === item.id ? null : item"
            >
              <div class="prompt-card-header">
                <span v-if="item.examYear" class="prompt-year-badge">{{ item.examYear }}</span>
                <span class="prompt-paper">{{ item.paper }}</span>
                <span v-if="item.imageUrl" class="prompt-tag prompt-tag--img">图</span>
                <span v-if="item.materialText" class="prompt-tag prompt-tag--mat">材料</span>
              </div>
              <p class="prompt-text-preview">{{ item.promptText.slice(0, 150) }}{{ item.promptText.length > 150 ? '...' : '' }}</p>
            </div>
          </div>

          <div v-if="promptTotal > promptPageSize" class="prompt-pagination">
            <button class="prompt-page-btn" :disabled="promptPage <= 1" @click="loadPrompts(promptPage - 1)">上一页</button>
            <span class="prompt-page-info">{{ promptPage }} / {{ Math.ceil(promptTotal / promptPageSize) }}</span>
            <button class="prompt-page-btn" :disabled="promptPage >= Math.ceil(promptTotal / promptPageSize)" @click="loadPrompts(promptPage + 1)">下一页</button>
          </div>

          <div v-if="selectedPrompt" class="prompt-preview">
            <h4 class="prompt-preview-title">{{ selectedPrompt.paper }}</h4>
            <img v-if="selectedPrompt.imageUrl" :src="selectedPrompt.imageUrl" class="prompt-preview-image" alt="题目图片" />
            <div v-if="selectedPrompt.materialText" class="prompt-material-box">
              <p class="prompt-material-label">阅读材料</p>
              <p class="prompt-material-text">{{ selectedPrompt.materialText }}</p>
            </div>
            <p class="prompt-preview-text">{{ selectedPrompt.promptText }}</p>
          </div>

          <div class="confirm-actions">
            <button class="btn-back" type="button" @click="showPastPromptPicker = false">取消</button>
            <button class="gate-btn" type="button" :disabled="!selectedPrompt" @click="useSelectedPrompt">插入当前题目</button>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useLocalStorage, useDebounceFn, onClickOutside, useEventListener, StorageSerializers } from '@vueuse/core'
import { generateExamDialogueTurn, recognizeTopicImage, startWritingSession, getWritingSessionMetadata, getEssayPrompts } from '@/api/writing'
import type { EssayPromptItem, GenerateExamPromptResponse, WritingAiProvider } from '@/api/writing'
import { getStageId } from '@/constants/stage'
import {
  buildExamResumePreview,
  buildVisualAttachmentPreview,
  buildExamTaskPrompt,
  getExamTaskSelectionLabel,
  getExamTaskSelectionOptions,
  parseWordRange,
} from '@/pages/app/examPromptHelpers'
import type { ExamPromptSheet, ExamPromptType, ExamTaskSelectionValue, ExamTopicInfo } from '@/pages/app/examPromptHelpers'
import type { WritingSessionMetadataResponse } from '@/api/writing'
import {
  isWorkbenchAbortError,
  resolveWorkbenchComposerAction,
  resolveRestoredDraftTopic,
  resolveWorkbenchCanvasState,
  persistExamSetupStateSnapshot,
  restoreExamSetupStateSnapshot,
  shouldConfirmModeSwitch,
  shouldSubmitWorkbenchOnEnter,
  shouldUseInitialTopicSeed,
} from '@/pages/app/examWorkbenchState'
import {
  buildDialogueTurnMessages,
  buildPreviewResultFromDialogue,
  createAssistantReplyMessages,
  createStatusMessage,
  createUserAssetMessage,
  createUserTextMessage,
} from '@/pages/app/examWorkbenchConversation.ts'
import type {
  ExamWorkbenchConversationMessage,
  ExamWorkbenchPreviewStatus,
} from '@/pages/app/examWorkbenchConversation.ts'
import { loadAiProvider, saveAiProviderNow } from '@/components/writing/editorShellStorage'

const props = defineProps<{
  initialTopic?: string
  studyStage?: string
  resumeMetadata?: WritingSessionMetadataResponse | null
}>()

const emit = defineEmits<{
  confirm: [info: ExamTopicInfo]
  back: []
  saveDraft: []
  switchMode: [payload: { mode: 'free' | 'exam'; info?: ExamTopicInfo | null }]
}>()

const tabs = [
  { key: 'manual', label: '手动输入' },
  { key: 'ai', label: 'AI 生成' },
  { key: 'past', label: '历年真题' },
] as const

type TabKey = (typeof tabs)[number]['key']

const activeTab = ref<TabKey>('manual')
type WorkbenchStep = 'compose' | 'preview'
const workbenchStep = ref<WorkbenchStep>('compose')
const workbenchBusy = ref(false)
const workbenchError = ref<string | null>(null)
const attachmentMenuOpen = ref(false)
const providerMenuOpen = ref(false)
const showPastPromptPicker = ref(false)
const attachmentInputRef = ref<HTMLInputElement | null>(null)
const topicInputRef = ref<HTMLTextAreaElement | null>(null)
const providerMenuRef = ref<HTMLElement | null>(null)
const conversationLogRef = ref<HTMLElement | null>(null)
const materialAttachmentText = ref<string | null>(null)
const materialAttachmentName = ref<string | null>(null)
const previewSheet = ref<ExamPromptSheet | null>(null)
const previewTopicInfo = ref<ExamTopicInfo | null>(null)
const previewStatus = ref<ExamWorkbenchPreviewStatus>('empty')
const previewMissingFields = ref<string[]>([])
const previewDirty = ref(false)
const pendingSubmission = ref(false)
const selectedMode = ref<'free' | 'exam'>('exam')
const selectedExamTask = ref<ExamTaskSelectionValue>('task1')
const selectedAiProvider = ref<WritingAiProvider>(loadAiProvider() ?? 'openai')
const submittedTopic = ref('')
const conversationMessages = ref<ExamWorkbenchConversationMessage[]>([])

// ── 手动输入表单 ──

const topic = ref('')
const genre = ref<string | null>(null)
const wordRange = ref<string | null>(null)
const showCustomWordRange = ref(false)
const customWordRange = ref('')
const TOPIC_INPUT_MAX_HEIGHT = 200

function syncTopicInputHeight() {
  const el = topicInputRef.value
  if (!el) return
  el.style.height = 'auto'
  const nextHeight = Math.min(el.scrollHeight, TOPIC_INPUT_MAX_HEIGHT)
  el.style.height = `${nextHeight}px`
  el.style.overflowY = el.scrollHeight > TOPIC_INPUT_MAX_HEIGHT ? 'auto' : 'hidden'
}

// ── 图片上传 ──
const uploadedImage = ref<string | null>(null) // data URL
const uploadedImageBase64 = ref<string | null>(null) // pure base64
const recognizing = ref(false)
const recognizeError = ref<string | null>(null)

function applyImageFile(file: File) {
  if (file.size > 5 * 1024 * 1024) {
    recognizeError.value = '图片不能超过 5MB'
    return
  }
  const isReplacing = !!uploadedImage.value
  recognizeError.value = null
  materialAttachmentText.value = null
  materialAttachmentName.value = null
  const reader = new FileReader()
  reader.onload = async () => {
    const dataUrl = reader.result as string
    uploadedImage.value = dataUrl
    // 提取纯 base64（去掉 data:image/xxx;base64, 前缀）
    uploadedImageBase64.value = dataUrl.split(',')[1] || null
    appendConversationMessages([
      createUserAssetMessage('image', {
        text: isReplacing ? '我更换了一张题目图片' : '我上传了一张题目图片',
        assetName: file.name,
        assetSummary: '图片附件已加入当前命题对话，请优先保留原图题意。',
        imageUrl: dataUrl,
      }),
    ])
    await refreshDialoguePreview()
  }
  reader.readAsDataURL(file)
}

function removeImage() {
  if (!uploadedImage.value) return
  uploadedImage.value = null
  uploadedImageBase64.value = null
  recognizeError.value = null
  if (attachmentInputRef.value) {
    attachmentInputRef.value.value = ''
  }
  appendConversationMessages([
    createStatusMessage('已移除当前图片附件，右侧题单会按剩余上下文重新整理。', 'warning'),
  ])
  void refreshDialoguePreview()
}

async function onWorkbenchAttachmentChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return

  workbenchError.value = null
  attachmentMenuOpen.value = false

  if (file.type.startsWith('image/')) {
    applyImageFile(file)
    ;(e.target as HTMLInputElement).value = ''
    return
  }

  try {
    const text = await file.text()
    const trimmed = text.trim()
    if (!trimmed) {
      workbenchError.value = '文件内容为空，请重新选择'
      return
    }
    materialAttachmentText.value = trimmed
    materialAttachmentName.value = file.name
    uploadedImage.value = null
    uploadedImageBase64.value = null
    recognizeError.value = null
    appendConversationMessages([
      createUserAssetMessage('material', {
        text: '我补充了一份材料附件',
        assetName: file.name,
        assetSummary: trimmed.slice(0, 180),
      }),
    ])
    await refreshDialoguePreview()
  } catch (error) {
    console.warn('[ExamSetup] read attachment failed', error)
    workbenchError.value = '暂不支持该文件，请上传文本类材料或图片'
  } finally {
    ;(e.target as HTMLInputElement).value = ''
  }
}

function openAttachmentPicker() {
  attachmentMenuOpen.value = false
  if (attachmentInputRef.value) {
    attachmentInputRef.value.value = ''
  }
  attachmentInputRef.value?.click()
}

function openPastPromptPicker() {
  attachmentMenuOpen.value = false
  showPastPromptPicker.value = true
  if (!promptLoading.value && promptItems.value.length === 0) {
    void loadPrompts(1)
  }
}

function clearMaterialAttachment() {
  if (!materialAttachmentText.value) return
  materialAttachmentText.value = null
  materialAttachmentName.value = null
  appendConversationMessages([
    createStatusMessage('已移除当前材料附件，右侧题单会按剩余上下文重新整理。', 'warning'),
  ])
  void refreshDialoguePreview()
}

async function onRecognizeImage() {
  if (!uploadedImageBase64.value) return
  recognizing.value = true
  recognizeError.value = null
  try {
    const res = await recognizeTopicImage({
      imageBase64: uploadedImageBase64.value,
      aiProvider: selectedAiProvider.value,
    })
    if (res.text) {
      topic.value = topic.value
        ? topic.value.trimEnd() + '\n' + res.text
        : res.text
      appendConversationMessages([
        createStatusMessage('图片文字已识别并放入输入框，你可以继续补充要求后发送。', 'info'),
      ])
    } else {
      recognizeError.value = '未能识别到文字内容，请尝试更清晰的图片'
    }
  } catch {
    recognizeError.value = '图片识别失败，请重试'
  } finally {
    recognizing.value = false
  }
}

const maxScore = ref(100)
const customMaxScore = ref<number | null>(null)
const wordRangeOptions = ['80-100', '100-120', '120-150']

// ── 校验 ──

const topicError = computed(() => {
  const t = topic.value.trim()
  if (!t) return null
  if (/^\d+$/.test(t)) return '请输入有效的作文题目'
  if (/^[^a-zA-Z\u4e00-\u9fff]*$/.test(t)) return '请输入有效的作文题目'
  if (t.length < 5) return '题目过于简略，建议补充写作情境和要求'
  return null
})

const readyPreview = computed(() => {
  return selectedMode.value === 'exam'
    && workbenchStep.value === 'preview'
    && !!previewTopicInfo.value
    && !!previewSheet.value
    && previewStatus.value === 'ready'
    && !previewDirty.value
})

const hasSubmittedContext = computed(() => {
  return conversationMessages.value.length > 0
    || !!selectedPrompt.value
    || !!uploadedImage.value
    || !!materialAttachmentText.value
})

const canvasState = computed(() => resolveWorkbenchCanvasState({
  workbenchBusy: workbenchBusy.value,
  pendingSubmission: pendingSubmission.value,
  hasPreviewSheet: !!previewSheet.value,
  previewDirty: previewDirty.value,
  hasSubmittedContext: hasSubmittedContext.value,
}))

const previewVisualAttachment = computed(() =>
  buildVisualAttachmentPreview(previewSheet.value, previewTopicInfo.value),
)

const hasGeneratedPromptSheet = computed(() => {
  return workbenchStep.value === 'preview' && !!previewSheet.value
})

const composerAction = computed(() =>
  resolveWorkbenchComposerAction({ workbenchBusy: workbenchBusy.value }),
)

const hasConversationLog = computed(() => {
  return conversationMessages.value.length > 0 || workbenchBusy.value
})

const examTaskSelectionOptions = computed(() => getExamTaskSelectionOptions(props.studyStage ?? null))

const showExamTaskSelector = computed(() => {
  return selectedMode.value === 'exam' && examTaskSelectionOptions.value.length > 0
})

const selectedExamTaskLabel = computed(() => getExamTaskSelectionLabel(selectedExamTask.value) ?? 'Task 1')

const canAssemble = computed(() => {
  return !!topic.value.trim()
})

const canEnterCurrentMode = computed(() => {
  if (selectedMode.value === 'exam') return readyPreview.value
  return workbenchStep.value === 'preview' && !!previewTopicInfo.value
})

const providerOptions: Array<{ value: WritingAiProvider; label: string }> = [
  { value: 'openai', label: 'OpenAI' },
  { value: 'kimi', label: 'Kimi' },
  { value: 'qwen', label: '千问' },
]

const currentProviderLabel = computed(
  () => providerOptions.find((option) => option.value === selectedAiProvider.value)?.label ?? selectedAiProvider.value,
)

const hasActiveAssets = computed(() =>
  !!uploadedImage.value || !!materialAttachmentText.value || !!selectedPrompt.value,
)

const previewStatusText = computed(() => {
  if (previewStatus.value === 'ready') return '已整理完成'
  if (previewStatus.value === 'draft') return '草稿中'
  return '等待输入'
})

const previewStatusToneClass = computed(() => {
  if (previewStatus.value === 'ready') return 'paper-status-bar--ready'
  if (previewStatus.value === 'draft') return 'paper-status-bar--draft'
  return 'paper-status-bar--empty'
})

function getAssistantReplyLabel(kind: string | null | undefined): string {
  if (kind === 'understanding') return '理解'
  if (kind === 'follow_up') return '追问'
  if (kind === 'action') return '动作'
  return '回复'
}

function getAssetMessageTitle(message: ExamWorkbenchConversationMessage): string {
  if (message.assetType === 'image') return '图片附件'
  if (message.assetType === 'material') return '材料附件'
  if (message.assetType === 'past_prompt') return '历年真题'
  return '附件'
}

// ── AI 确认流程 ──

type ConfirmStep = 'idle' | 'parsing' | 'confirming'
const confirmStep = ref<ConfirmStep>('idle')
const parsedResult = ref<ExamTopicInfo>({
  topic: '',
  genre: null,
  wordRange: null,
  requirements: null,
  imageDescription: null,
  materialText: null,
  maxScore: 100,
  sourceType: 'manual',
  examType: null,
  taskType: null,
  minWords: null,
  recommendedMaxWords: null,
})
const auditMessage = ref<string | null>(null)

type AiFlowStep = 'input' | 'parsing' | 'parsed' | 'generating' | 'generated'

interface ParsedAiIntent {
  topic: string
  promptType: ExamPromptType
  genre: string | null
  wordRange: string | null
  requirements: string | null
  maxScore: number
}

const aiFlowStep = ref<AiFlowStep>('input')
const aiRawInput = ref('')
const aiMessage = ref<string | null>(null)
const aiError = ref<string | null>(null)
const aiParsedIntent = ref<ParsedAiIntent | null>(null)
const aiGeneratedPrompt = ref<GenerateExamPromptResponse | null>(null)
const assembleAbortController = ref<AbortController | null>(null)

function getEffectiveWordRange(): string | null {
  return showCustomWordRange.value
    ? (customWordRange.value.trim() || null)
    : wordRange.value
}

function getEffectiveWorkbenchInput(preferSubmittedTopic = false): string {
  const draftInput = topic.value.trim()
  if (draftInput) return draftInput
  if (preferSubmittedTopic) return submittedTopic.value.trim()
  return ''
}

function startPreview(info: ExamTopicInfo, sheet: ExamPromptSheet) {
  previewTopicInfo.value = info
  previewSheet.value = sheet
  const normalizedTask = getExamTaskSelectionLabel(info.taskType) ? (info.taskType as ExamTaskSelectionValue) : null
  if (normalizedTask) {
    selectedExamTask.value = normalizedTask
  }
  workbenchStep.value = 'preview'
  previewDirty.value = false
  pendingSubmission.value = false
  workbenchError.value = null
}

function getActiveAssets() {
  return {
    uploadedImage: uploadedImage.value,
    materialAttachmentText: materialAttachmentText.value,
    materialAttachmentName: materialAttachmentName.value,
    selectedPrompt: selectedPrompt.value,
  }
}

function scrollConversationToBottom() {
  const el = conversationLogRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

function appendConversationMessages(messages: ExamWorkbenchConversationMessage[]) {
  if (!messages.length) return
  conversationMessages.value = [...conversationMessages.value, ...messages]
  void nextTick(() => {
    scrollConversationToBottom()
  })
}

function applyDialoguePreviewState(nextStatus: ExamWorkbenchPreviewStatus, missingFields: string[], info: ExamTopicInfo | null, sheet: ExamPromptSheet | null) {
  previewStatus.value = nextStatus
  previewMissingFields.value = [...missingFields]
  if (info && sheet) {
    startPreview(info, sheet)
    return
  }
  previewTopicInfo.value = null
  previewSheet.value = null
  workbenchStep.value = 'compose'
  previewDirty.value = false
  pendingSubmission.value = false
  workbenchError.value = null
}

async function refreshDialoguePreview() {
  const taskLabel = getExamTaskSelectionLabel(selectedExamTask.value)
  const requestMessages = buildDialogueTurnMessages(
    conversationMessages.value,
    getActiveAssets(),
    taskLabel ? `考试任务要求：当前固定为 ${taskLabel}，请严格按照该任务整理题单。` : null,
  )
  if (!requestMessages.length) {
    applyDialoguePreviewState('empty', [], null, null)
    return
  }

  workbenchError.value = null
  workbenchBusy.value = true
  const abortController = new AbortController()
  assembleAbortController.value = abortController

  try {
    const response = await generateExamDialogueTurn({
      studyStage: props.studyStage ?? null,
      aiProvider: selectedAiProvider.value,
      selectedMode: selectedMode.value,
      messages: requestMessages,
    }, {
      signal: abortController.signal,
    })

    const preview = buildPreviewResultFromDialogue(response, {
      studyStage: props.studyStage ?? null,
      selectedMode: selectedMode.value,
      activeAssets: getActiveAssets(),
    })

    applyDialoguePreviewState(
      preview.previewStatus,
      preview.missingFields,
      preview.previewTopicInfo,
      preview.previewSheet,
    )

    appendConversationMessages([
      ...createAssistantReplyMessages(response.assistantReplyBlocks, response.assistantReply),
      createStatusMessage(
        preview.previewStatus === 'ready'
          ? '右侧题单已刷新为完成态，可以直接开始写作。'
          : preview.previewStatus === 'draft'
            ? '右侧题单已刷新为草稿态，继续补充后我会自动更新。'
            : '当前信息仍不足以生成题单，请继续补充。',
        preview.previewStatus === 'ready'
          ? 'success'
          : preview.previewStatus === 'draft'
            ? 'info'
            : 'warning',
      ),
    ])
    saveLiveStateNow()
  } catch (error) {
    if (isWorkbenchAbortError(error)) return
    console.warn('[ExamSetup] refresh dialogue preview failed', error)
    workbenchError.value = '题单整理失败，请补充更明确的要求后重试'
  } finally {
    assembleAbortController.value = null
    workbenchBusy.value = false
  }
}

async function assembleWorkbenchPromptSheet() {
  const rawInput = getEffectiveWorkbenchInput()
  if (!rawInput) {
    workbenchError.value = '请先输入一条命题需求'
    return
  }
  submittedTopic.value = rawInput
  appendConversationMessages([createUserTextMessage(rawInput)])
  topic.value = ''
  pendingSubmission.value = true
  saveLiveStateNow()
  await nextTick()
  syncTopicInputHeight()
  await refreshDialoguePreview()
}

function onAssembleWorkbenchPromptSheet() {
  void assembleWorkbenchPromptSheet()
}

function cancelWorkbenchPromptAssembly() {
  assembleAbortController.value?.abort()
}

function onComposerPrimaryAction() {
  if (composerAction.value === 'cancel') {
    cancelWorkbenchPromptAssembly()
    return
  }
  onAssembleWorkbenchPromptSheet()
}

function onWorkbenchInputKeydown(event: KeyboardEvent) {
  if (!shouldSubmitWorkbenchOnEnter(event)) return
  event.preventDefault()
  if (!canAssemble.value || workbenchBusy.value) return
  onAssembleWorkbenchPromptSheet()
}

const showModeSwitchConfirm = ref(false)
const pendingModeSwitch = ref<'free' | 'exam' | null>(null)

function applyModeSwitch(nextMode: 'free' | 'exam') {
  if (selectedMode.value === nextMode) return
  selectedMode.value = nextMode
  saveLiveStateNow()
}

function handleModeSwitchRequest(nextMode: 'free' | 'exam') {
  if (!shouldConfirmModeSwitch({
    currentMode: selectedMode.value,
    nextMode,
    hasPreviewSheet: hasGeneratedPromptSheet.value,
  })) {
    applyModeSwitch(nextMode)
    return
  }

  pendingModeSwitch.value = nextMode
  showModeSwitchConfirm.value = true
}

function cancelModeSwitch() {
  pendingModeSwitch.value = null
  showModeSwitchConfirm.value = false
}

function confirmModeSwitch() {
  const nextMode = pendingModeSwitch.value
  cancelModeSwitch()
  if (!nextMode) return
  applyModeSwitch(nextMode)
}

function handleExamTaskSelection(taskType: ExamTaskSelectionValue) {
  if (selectedExamTask.value === taskType) return
  selectedExamTask.value = taskType
  const hasWorkbenchInput = conversationMessages.value.length > 0
    || !!selectedPrompt.value
    || !!uploadedImage.value
    || !!materialAttachmentText.value
  if (!hasWorkbenchInput || selectedMode.value !== 'exam' || workbenchBusy.value) return
  appendConversationMessages([
    createStatusMessage(`已切换为 ${getExamTaskSelectionLabel(taskType) ?? taskType}，右侧题单正在同步刷新。`, 'info'),
  ])
  void refreshDialoguePreview()
}

function toggleExamTaskSelection() {
  if (!showExamTaskSelector.value) return
  const nextTask: ExamTaskSelectionValue = selectedExamTask.value === 'task2' ? 'task1' : 'task2'
  handleExamTaskSelection(nextTask)
}

function startWritingFromPreview() {
  if (!canEnterCurrentMode.value || !previewTopicInfo.value) return
  if (selectedMode.value === 'free') {
    clearExamSetupState()
    emit('switchMode', { mode: 'free', info: previewTopicInfo.value })
    return
  }
  clearExamSetupState()
  emit('confirm', previewTopicInfo.value)
}

function removeSelectedPrompt() {
  if (!selectedPrompt.value) return
  selectedPrompt.value = null
  appendConversationMessages([
    createStatusMessage('已移除当前历年真题参考，右侧题单会按剩余上下文重新整理。', 'warning'),
  ])
  void refreshDialoguePreview()
}

// ── 草稿与页面状态持久化 ──

const DRAFT_KEY = 'peai:examSetup:draft'
const LIVE_STATE_KEY = 'peai:examSetup:live'

interface ExamSetupDraft {
  topic: string
  selectedExamTask: ExamTaskSelectionValue | null
  genre: string | null
  wordRange: string | null
  customWordRange: string
  showCustomWordRange: boolean
  maxScore: number
  uploadedImage: string | null
  uploadedImageBase64: string | null
  materialAttachmentText: string | null
  materialAttachmentName: string | null
  selectedPrompt: EssayPromptItem | null
}

const savedDraft = useLocalStorage<ExamSetupDraft | null>(DRAFT_KEY, null, {
  serializer: StorageSerializers.object,
})

interface ExamSetupLiveState extends ExamSetupDraft {
  selectedMode: 'free' | 'exam'
  activeTab: TabKey
  conversationMessages: ExamWorkbenchConversationMessage[]
  workbenchStep: WorkbenchStep
  previewStatus: ExamWorkbenchPreviewStatus
  previewMissingFields: string[]
  previewDirty: boolean
  pendingSubmission: boolean
  submittedTopic: string
  customMaxScore: number | null
  workbenchError: string | null
  confirmStep: ConfirmStep
  parsedResult: ExamTopicInfo
  auditMessage: string | null
  aiFlowStep: AiFlowStep
  aiRawInput: string
  aiMessage: string | null
  aiError: string | null
  aiParsedIntent: ParsedAiIntent | null
  aiGeneratedPrompt: GenerateExamPromptResponse | null
  promptKeyword: string
  promptYear: number | null
  promptPage: number
  previewSheet: ExamPromptSheet | null
  previewTopicInfo: ExamTopicInfo | null
}

const liveState = useLocalStorage<ExamSetupLiveState | null>(LIVE_STATE_KEY, null, {
  serializer: StorageSerializers.object,
})

let liveStateCleared = false

function restoreLegacySessionValue<T>(key: string): T | null {
  return restoreExamSetupStateSnapshot<T>(window.sessionStorage, key)
}

function clearExamSetupState() {
  liveStateCleared = true
  savedDraft.value = null
  liveState.value = null
  persistExamSetupStateSnapshot(window.localStorage, DRAFT_KEY, null)
  persistExamSetupStateSnapshot(window.localStorage, LIVE_STATE_KEY, null)
}

const restoringLiveState = ref(false)

onMounted(() => {
  selectedAiProvider.value = loadAiProvider() ?? 'openai'

  if (!liveState.value) {
    const legacyLiveState = restoreLegacySessionValue<ExamSetupLiveState>(LIVE_STATE_KEY)
    if (legacyLiveState) liveState.value = legacyLiveState
  }
  if (!savedDraft.value) {
    const legacyDraft = restoreLegacySessionValue<ExamSetupDraft>(DRAFT_KEY)
    if (legacyDraft) savedDraft.value = legacyDraft
  }

  if (liveState.value) {
    restoringLiveState.value = true
    try {
      const state = liveState.value
      selectedMode.value = state.selectedMode ?? 'exam'
      selectedExamTask.value = state.selectedExamTask ?? 'task1'
      const validTabs: TabKey[] = ['manual', 'ai', 'past']
      activeTab.value = validTabs.includes(state.activeTab) ? state.activeTab : 'manual'
      topic.value = resolveRestoredDraftTopic({
        topic: state.topic ?? '',
        submittedTopic: state.submittedTopic ?? '',
        previewDirty: state.previewDirty ?? false,
        pendingSubmission: state.pendingSubmission ?? false,
      })
      genre.value = state.genre ?? null
      wordRange.value = state.wordRange ?? null
      customWordRange.value = state.customWordRange ?? ''
      showCustomWordRange.value = state.showCustomWordRange ?? false
      maxScore.value = state.maxScore ?? 100
      customMaxScore.value = state.customMaxScore ?? null
      uploadedImage.value = state.uploadedImage ?? null
      uploadedImageBase64.value = state.uploadedImageBase64 ?? null
      materialAttachmentText.value = state.materialAttachmentText ?? null
      materialAttachmentName.value = state.materialAttachmentName ?? null
      conversationMessages.value = (state.conversationMessages ?? []).map((message) => ({ ...message }))
      workbenchStep.value = state.workbenchStep ?? 'compose'
      previewStatus.value = state.previewStatus ?? (state.previewSheet ? 'ready' : 'empty')
      previewMissingFields.value = [...(state.previewMissingFields ?? [])]
      previewDirty.value = state.previewDirty ?? false
      pendingSubmission.value = state.pendingSubmission ?? false
      submittedTopic.value = state.submittedTopic ?? ''
      workbenchError.value = state.workbenchError ?? null
      confirmStep.value = 'idle'
      parsedResult.value = state.parsedResult
        ? { ...state.parsedResult }
        : { topic: '', genre: null, wordRange: null, requirements: null, imageDescription: null, materialText: null, maxScore: 100, sourceType: 'manual', examType: props.studyStage ?? null, taskType: null, minWords: null, recommendedMaxWords: null }
      auditMessage.value = state.auditMessage ?? null
      aiFlowStep.value = state.aiFlowStep ?? 'input'
      aiRawInput.value = state.aiRawInput ?? ''
      aiMessage.value = state.aiMessage ?? null
      aiError.value = state.aiError ?? null
      aiParsedIntent.value = state.aiParsedIntent ? { ...state.aiParsedIntent } : null
      aiGeneratedPrompt.value = state.aiGeneratedPrompt
        ? {
            ...state.aiGeneratedPrompt,
            chartSpec: state.aiGeneratedPrompt.chartSpec
              ? {
                  ...state.aiGeneratedPrompt.chartSpec,
                  columns: [...state.aiGeneratedPrompt.chartSpec.columns],
                  rows: state.aiGeneratedPrompt.chartSpec.rows.map((row) => [...row]),
                }
              : null,
            comicScenes: (state.aiGeneratedPrompt.comicScenes ?? []).map((scene) => ({ ...scene })),
          }
        : null
      promptKeyword.value = state.promptKeyword ?? ''
      promptYear.value = state.promptYear ?? null
      promptYearSelect.value = state.promptYear ?? 0
      promptPage.value = state.promptPage ?? 1
      selectedPrompt.value = state.selectedPrompt ?? null
      previewSheet.value = state.previewSheet ? { ...state.previewSheet, requirements: [...state.previewSheet.requirements] } : null
      previewTopicInfo.value = state.previewTopicInfo
        ? {
            ...state.previewTopicInfo,
            chartSpec: state.previewTopicInfo.chartSpec
              ? {
                  ...state.previewTopicInfo.chartSpec,
                  columns: [...state.previewTopicInfo.chartSpec.columns],
                  rows: state.previewTopicInfo.chartSpec.rows.map((row) => [...row]),
                }
              : null,
            comicScenes: (state.previewTopicInfo.comicScenes ?? []).map((scene) => ({ ...scene })),
          }
        : null
      const restoredTask = getExamTaskSelectionLabel(previewTopicInfo.value?.taskType)
        ? (previewTopicInfo.value?.taskType as ExamTaskSelectionValue)
        : null
      if (restoredTask) {
        selectedExamTask.value = restoredTask
      }

      if (showPastPromptPicker.value) {
        void loadPrompts(promptPage.value)
      }
    } catch (e) {
      console.warn('[ExamSetup] liveState restore failed, clearing', e)
      liveState.value = null
    } finally {
      restoringLiveState.value = false
    }
  } else if (props.resumeMetadata) {
    const restored = buildExamResumePreview(props.resumeMetadata, props.studyStage ?? null)
    if (restored) {
      startPreview(restored.topicInfo, restored.sheet)
      previewStatus.value = 'ready'
      previewMissingFields.value = []
      submittedTopic.value = ''
      topic.value = ''
      if (restored.taskType) {
        selectedExamTask.value = restored.taskType
      }
    } else if (props.initialTopic?.trim()) {
      topic.value = props.initialTopic.trim()
    }
  } else if (shouldUseInitialTopicSeed({ initialTopic: props.initialTopic, hasLiveState: false })) {
    topic.value = props.initialTopic!.trim()
  } else if (savedDraft.value) {
    try {
      const d = savedDraft.value
      topic.value = d.topic ?? ''
      selectedExamTask.value = d.selectedExamTask ?? 'task1'
      genre.value = d.genre ?? null
      wordRange.value = d.wordRange ?? null
      customWordRange.value = d.customWordRange ?? ''
      showCustomWordRange.value = d.showCustomWordRange ?? false
      maxScore.value = d.maxScore ?? 100
      uploadedImage.value = d.uploadedImage ?? null
      uploadedImageBase64.value = d.uploadedImageBase64 ?? null
      materialAttachmentText.value = d.materialAttachmentText ?? null
      materialAttachmentName.value = d.materialAttachmentName ?? null
      selectedPrompt.value = d.selectedPrompt ?? null
    } catch (e) {
      console.warn('[ExamSetup] draft restore failed, clearing', e)
    }
    savedDraft.value = null
  }

  void nextTick(() => {
    syncTopicInputHeight()
  })
})

onClickOutside(providerMenuRef, () => {
  providerMenuOpen.value = false
})

useEventListener(window, 'pagehide', () => {
  saveLiveStateNow()
})

useEventListener(window, 'beforeunload', () => {
  saveLiveStateNow()
})

// ── 返回确认 ──

const showBackConfirm = ref(false)

const isDirty = computed(() => {
  return topic.value.trim().length > 0
    || submittedTopic.value.trim().length > 0
    || conversationMessages.value.length > 0
    || selectedExamTask.value !== null
    || genre.value !== null
    || wordRange.value !== null
    || (showCustomWordRange.value && customWordRange.value.trim().length > 0)
    || uploadedImage.value !== null
    || materialAttachmentText.value !== null
    || selectedPrompt.value !== null
    || maxScore.value !== 100
    || pendingSubmission.value
    || workbenchStep.value === 'preview'
})

function handleBack() {
  if (isDirty.value) {
    showBackConfirm.value = true
  } else {
    clearExamSetupState()
    emit('back')
  }
}

const saving = ref(false)

async function saveAndLeave() {
  if (saving.value) return
  saving.value = true
  workbenchError.value = null
  let savedSuccessfully = false
  try {
    let draftInfo: ExamTopicInfo | null = null

    if (previewTopicInfo.value) {
      draftInfo = previewTopicInfo.value
    } else {
      const t = topic.value.trim()
        || (materialAttachmentText.value?.trim() ? '请根据附件材料完成写作。' : '')
        || (uploadedImage.value ? '请根据附件图片完成写作。' : '')
      console.log('[ExamSetup] saveAndLeave, topic:', t)
      if (t) {
        const parsedWordRange = parseWordRange(getEffectiveWordRange())
        draftInfo = {
          paper: null,
          promptSheetId: null,
          topic: t,
          genre: genre.value,
          wordRange: getEffectiveWordRange(),
          requirements: null,
          imageDescription: uploadedImage.value ? '请结合附件图片完成写作。' : selectedPrompt.value?.imageDescription?.trim() || null,
          materialText: materialAttachmentText.value ?? (selectedPrompt.value?.materialText?.trim() || null),
          attachmentImageUrl: uploadedImage.value ?? selectedPrompt.value?.imageUrl?.trim() ?? null,
          maxScore: maxScore.value,
          sourceType: selectedPrompt.value ? 'past_prompt' : 'manual',
          examType: props.studyStage ?? null,
          taskType: selectedExamTask.value ?? selectedPrompt.value?.task ?? null,
          minWords: parsedWordRange.minWords,
          recommendedMaxWords: parsedWordRange.recommendedMaxWords,
        }
      }
    }

    if (draftInfo) {
      const taskPrompt = buildExamTaskPrompt(draftInfo)
      const res = await startWritingSession({
        mode: 'exam',
        taskPrompt,
        title: draftInfo.topic.slice(0, 100),
        draft: true,
        studyStage: props.studyStage ?? undefined,
        sourceType: draftInfo.sourceType,
        titleSnapshot: draftInfo.topic.slice(0, 255),
        topicTitle: draftInfo.topic,
        promptText: taskPrompt,
        promptSheetId: draftInfo.promptSheetId ?? null,
        attachmentImageUrl: draftInfo.attachmentImageUrl ?? null,
        genre: draftInfo.genre,
        examType: props.studyStage ?? null,
        taskType: draftInfo.taskType || null,
        minWords: draftInfo.minWords,
        recommendedMaxWords: draftInfo.recommendedMaxWords,
        maxScore: draftInfo.maxScore,
      })
      console.log('[ExamSetup] startWritingSession result:', JSON.stringify(res))
      const metadata = await getWritingSessionMetadata(res.docId).catch((err) => {
        console.warn('[ExamSetup] load session metadata failed', err)
        return null
      })
      console.log('[ExamSetup] writing metadata:', metadata)
      clearExamSetupState()
      savedSuccessfully = true
    } else {
      liveStateCleared = false
    }
  } catch (e) {
    console.warn('[ExamSetup] save draft doc failed', e)
    workbenchError.value = '保存草稿失败，请稍后重试'
  } finally {
    saving.value = false
  }
  if (savedSuccessfully) {
    showBackConfirm.value = false
    emit('saveDraft')
  }
}

function confirmLeave() {
  clearExamSetupState()
  showBackConfirm.value = false
  emit('back')
}

// ── 历年真题 ──

const promptKeyword = ref('')
const promptYear = ref<number | null>(null)
const promptYearSelect = ref(0)

function onYearChange() {
  promptYear.value = promptYearSelect.value === 0 ? null : promptYearSelect.value
  selectedPrompt.value = null
  loadPrompts(1)
}
const promptItems = ref<EssayPromptItem[]>([])
const promptYears = ref<number[]>([])
const promptTotal = ref(0)
const promptPage = ref(1)
const promptPageSize = 10
const promptLoading = ref(false)
const selectedPrompt = ref<EssayPromptItem | null>(null)

async function loadPrompts(page = 1) {
  promptPage.value = page
  promptLoading.value = true
  try {
    const res = await getEssayPrompts({
      stageId: getStageId(props.studyStage),
      keyword: promptKeyword.value.trim() || undefined,
      year: promptYear.value ?? undefined,
      page,
      size: promptPageSize,
    })
    promptItems.value = res.items
    promptTotal.value = res.total
    if (res.years.length > 0) {
      promptYears.value = res.years
    }
  } catch (e) {
    console.warn('[ExamSetup] loadPrompts failed', e)
  } finally {
    promptLoading.value = false
  }
}

const debouncedLoadPrompts = useDebounceFn(() => loadPrompts(1), 300)

function onPromptSearch() {
  selectedPrompt.value = null
  debouncedLoadPrompts()
}

async function useSelectedPrompt() {
  if (!selectedPrompt.value) return
  const prompt = selectedPrompt.value
  showPastPromptPicker.value = false
  attachmentMenuOpen.value = false
  if (prompt.wordCountMin != null && prompt.wordCountMax != null) {
    const range = `${prompt.wordCountMin}-${prompt.wordCountMax}`
    if (wordRangeOptions.includes(range)) {
      wordRange.value = range
      showCustomWordRange.value = false
      customWordRange.value = ''
    } else {
      wordRange.value = null
      showCustomWordRange.value = true
      customWordRange.value = range
    }
  } else if (prompt.wordCountMin != null) {
    const range = String(prompt.wordCountMin)
    if (wordRangeOptions.includes(range)) {
      wordRange.value = range
      showCustomWordRange.value = false
      customWordRange.value = ''
    } else {
      wordRange.value = null
      showCustomWordRange.value = true
      customWordRange.value = range
    }
  }
  if (prompt.maxScore != null) {
    maxScore.value = prompt.maxScore
    customMaxScore.value = prompt.maxScore
  }
  const normalizedTask = getExamTaskSelectionLabel(prompt.task) ? (prompt.task as ExamTaskSelectionValue) : null
  if (normalizedTask) {
    selectedExamTask.value = normalizedTask
  }
  appendConversationMessages([
    createUserAssetMessage('past_prompt', {
      text: '我插入了一道历年真题作为参考',
      assetName: prompt.paper || prompt.title,
      assetSummary: prompt.promptText.slice(0, 180),
    }),
  ])
  await refreshDialoguePreview()
}

function saveLiveStateNow() {
  if (liveStateCleared) return
  const snapshot: ExamSetupLiveState = {
    selectedMode: selectedMode.value,
    selectedExamTask: selectedExamTask.value,
    activeTab: activeTab.value,
    conversationMessages: conversationMessages.value.map((message) => ({ ...message })),
    workbenchStep: workbenchStep.value,
    previewStatus: previewStatus.value,
    previewMissingFields: [...previewMissingFields.value],
    previewDirty: previewDirty.value,
    pendingSubmission: pendingSubmission.value,
    submittedTopic: submittedTopic.value,
    topic: topic.value,
    genre: genre.value,
    wordRange: wordRange.value,
    customWordRange: customWordRange.value,
    showCustomWordRange: showCustomWordRange.value,
    maxScore: maxScore.value,
    uploadedImage: uploadedImage.value,
    uploadedImageBase64: uploadedImageBase64.value,
    materialAttachmentText: materialAttachmentText.value,
    materialAttachmentName: materialAttachmentName.value,
    selectedPrompt: selectedPrompt.value,
    customMaxScore: customMaxScore.value,
    workbenchError: workbenchError.value,
    confirmStep: 'idle' as ConfirmStep,
    parsedResult: { ...parsedResult.value },
    auditMessage: auditMessage.value,
    aiFlowStep: aiFlowStep.value,
    aiRawInput: aiRawInput.value,
    aiMessage: aiMessage.value,
    aiError: aiError.value,
    aiParsedIntent: aiParsedIntent.value ? { ...aiParsedIntent.value } : null,
    aiGeneratedPrompt: aiGeneratedPrompt.value
      ? {
          ...aiGeneratedPrompt.value,
          chartSpec: aiGeneratedPrompt.value.chartSpec
            ? {
                ...aiGeneratedPrompt.value.chartSpec,
                columns: [...aiGeneratedPrompt.value.chartSpec.columns],
                rows: aiGeneratedPrompt.value.chartSpec.rows.map((row) => [...row]),
              }
            : null,
          comicScenes: (aiGeneratedPrompt.value.comicScenes ?? []).map((scene) => ({ ...scene })),
        }
      : null,
    promptKeyword: promptKeyword.value,
    promptYear: promptYear.value,
    promptPage: promptPage.value,
    previewSheet: previewSheet.value ? { ...previewSheet.value, requirements: [...previewSheet.value.requirements] } : null,
    previewTopicInfo: previewTopicInfo.value
      ? {
          ...previewTopicInfo.value,
          chartSpec: previewTopicInfo.value.chartSpec
            ? {
                ...previewTopicInfo.value.chartSpec,
                columns: [...previewTopicInfo.value.chartSpec.columns],
                rows: previewTopicInfo.value.chartSpec.rows.map((row) => [...row]),
              }
            : null,
          comicScenes: (previewTopicInfo.value.comicScenes ?? []).map((scene) => ({ ...scene })),
        }
      : null,
  }
  liveState.value = snapshot
  persistExamSetupStateSnapshot(window.localStorage, LIVE_STATE_KEY, snapshot)
}

const debouncedSaveLiveState = useDebounceFn(() => {
  saveLiveStateNow()
}, 150)

watch(
  [
    selectedMode,
    selectedExamTask,
    activeTab,
    conversationMessages,
    workbenchStep,
    previewStatus,
    previewMissingFields,
    previewDirty,
    pendingSubmission,
    submittedTopic,
    topic,
    genre,
    wordRange,
    customWordRange,
    showCustomWordRange,
    maxScore,
    customMaxScore,
    uploadedImage,
    uploadedImageBase64,
    materialAttachmentText,
    materialAttachmentName,
    workbenchError,
    previewSheet,
    previewTopicInfo,
    confirmStep,
    parsedResult,
    auditMessage,
    aiFlowStep,
    aiRawInput,
    aiMessage,
    aiError,
    aiParsedIntent,
    aiGeneratedPrompt,
    promptKeyword,
    promptYear,
    promptPage,
    selectedPrompt,
  ],
  () => { debouncedSaveLiveState() },
  { deep: true }
)

watch(
  [
    selectedMode,
    selectedExamTask,
    topic,
    genre,
    wordRange,
    customWordRange,
    showCustomWordRange,
    maxScore,
    uploadedImage,
    materialAttachmentText,
    selectedPrompt,
  ],
  () => {
    if (restoringLiveState.value) return
    if (workbenchStep.value === 'preview') {
      previewDirty.value = true
    }
  },
)

watch(topic, () => {
  void nextTick(() => {
    syncTopicInputHeight()
  })
})

// 切换到历年真题 tab 时自动加载（跳过 liveState 恢复期间，避免重复请求）
watch(activeTab, (tab) => {
  if (restoringLiveState.value) return
  if (tab === 'past' && !promptLoading.value) {
    loadPrompts(1)
  }
})

</script>

<style src="@/styles/gate.css" />
<style scoped>
.exam-setup {
  align-items: stretch;
  justify-content: flex-start;
  padding: 12px 20px 20px;
}

.setup-card {
  width: min(1560px, 100%);
  background: #fff;
  border-radius: 24px;
  padding: 28px 32px 24px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  height: calc(100vh - 32px);
  max-height: calc(100vh - 32px);
  margin: 0 auto;
  overflow: hidden;
}

.setup-card--wide {
  max-width: none;
}

.setup-back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 18px;
  padding: 0;
  border: none;
  background: none;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.setup-back-link:hover {
  color: #047857;
}

.workbench-header--canvas {
  margin-bottom: 18px;
  position: relative;
}

.workbench-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  position: relative;
  z-index: 1;
}

.workbench-header-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: max-content;
  max-width: calc(100% - 360px);
  text-align: center;
}

.gate-title--centered {
  margin: 0;
}

.mode-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 999px;
  border: 1px solid #dbe3ee;
  background: #f8fafc;
}

.mode-switch-btn {
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #475569;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.18s ease;
}

.mode-switch-btn.active {
  background: #ecfdf5;
  color: #047857;
  box-shadow: 0 6px 14px rgba(4, 120, 87, 0.12);
}

.gate-btn--top {
  padding: 10px 22px;
  white-space: nowrap;
}

.workbench-board {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.92fr);
  gap: 24px;
  margin-bottom: 24px;
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  align-items: stretch;
}

.workbench-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.workbench-pane--canvas {
  padding-left: 28px;
  border-left: 1px solid #e5edf5;
}

.conversation-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
  height: 100%;
  overflow: hidden;
}

.conversation-log {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  gap: 14px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 6px;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.55) transparent;
}

.conversation-entry {
  display: flex;
}

.conversation-entry--user {
  justify-content: flex-end;
}

.conversation-entry--assistant,
.conversation-entry--status {
  justify-content: flex-start;
}

.conversation-bubble {
  max-width: 92%;
  padding: 14px 16px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
  user-select: text;
  -webkit-user-select: text;
}

.conversation-bubble--assistant {
  align-self: flex-start;
  background: #f8fffb;
  border-color: #bbf7d0;
}

.conversation-bubble--user {
  align-self: flex-end;
  background: #f8fafc;
  border-color: #dbe3ee;
}

.conversation-bubble--warning {
  background: #fff9eb;
  border-color: #f5d488;
}

.conversation-bubble--success {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.conversation-bubble--muted {
  background: #f8fafc;
  border-color: #e2e8f0;
}

.conversation-empty {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 18px;
  border: 1px dashed #d7e2ea;
  border-radius: 20px;
  background: linear-gradient(180deg, #fbfdff 0%, #f8fafc 100%);
}

.conversation-empty-title {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.conversation-empty-text {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

.bubble-label {
  margin: 0 0 6px;
  color: #047857;
  font-size: 12px;
  font-weight: 700;
}

.bubble-text {
  margin: 0;
  color: #334155;
  font-size: 14px;
  line-height: 1.75;
  user-select: text;
  -webkit-user-select: text;
}

.bubble-text--pre {
  white-space: pre-wrap;
}

.asset-card,
.asset-card-name,
.asset-card-text,
.paper-sheet,
.paper-prompt,
.paper-requirements,
.paper-meta,
.paper-attachment-content,
.paper-chart-summary,
.paper-chart-table,
.paper-chart-title,
.paper-comic-description,
.paper-comic-dialogue {
  user-select: text;
  -webkit-user-select: text;
}

.composer-frame {
  display: block;
  flex: none;
  margin-top: auto;
}

.composer-asset-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.composer-asset-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  padding: 8px 12px;
  border: 1px solid #dbe7f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
}

.composer-asset-chip-label {
  color: #047857;
  font-weight: 700;
}

.composer-asset-chip-text {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-asset-chip-action,
.composer-asset-chip-close {
  border: none;
  background: transparent;
  color: #047857;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.composer-asset-chip-close {
  color: #94a3b8;
}

.composer-asset-chip-close:hover {
  color: #475569;
}

.composer-shell {
  display: flex;
  flex-direction: column;
  flex: none;
  width: 100%;
  border: 1px solid #e4ecf5;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(148, 163, 184, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.72);
  padding: 14px 20px 14px;
}

/* 输入区域允许选中 */
.workbench-input,
.topic-input,
.prompt-search-input,
.custom-input,
.confirm-input,
.prompt-preview-text,
.prompt-text-preview,
.prompt-material-text {
  user-select: text;
  -webkit-user-select: text;
}

.workbench-header,
.preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.workbench-badge,
.preview-source-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.preview-source-chip {
  text-transform: capitalize;
}

.workbench-input {
  width: 100%;
  min-height: 104px;
  max-height: 200px;
  padding: 0;
  border: none;
  background: transparent;
  resize: none;
  overflow-y: hidden;
  font-size: 18px;
  line-height: 1.75;
  color: #111827;
  font-family: inherit;
  box-sizing: border-box;
}

.workbench-input:focus {
  outline: none;
}

.workbench-input::placeholder {
  color: #94a3b8;
}

.workbench-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  margin-top: auto;
  border-top: 1px solid #eef2f7;
}

.workbench-toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.provider-picker-wrap {
  position: relative;
  flex-shrink: 0;
}

.provider-picker-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  height: 38px;
  border: 1px solid #d6dce5;
  border-radius: 999px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.provider-picker-btn:hover {
  border-color: #94a3b8;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.provider-picker-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.provider-picker-icon {
  color: #f97316;
  font-size: 16px;
  line-height: 1;
}

.provider-picker-caret {
  color: #64748b;
  font-size: 12px;
}

.provider-menu {
  position: absolute;
  left: 0;
  bottom: 46px;
  min-width: 220px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 20px 36px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(14px);
  z-index: 12;
}

.provider-menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 14px;
  border: none;
  border-radius: 14px;
  background: transparent;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.provider-menu-item:hover,
.provider-menu-item.active {
  background: #edf5ff;
}

.provider-menu-item-label {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.provider-menu-check {
  color: #047857;
  font-size: 18px;
  font-weight: 800;
}

.composer-submit {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 999px;
  background: #f97316;
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 10px 18px rgba(249, 115, 22, 0.22);
  transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
}

.composer-submit--cancel {
  background: #111827;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.22);
}

.composer-submit:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 14px 24px rgba(249, 115, 22, 0.28);
}

.composer-submit--cancel:hover:not(:disabled) {
  box-shadow: 0 14px 24px rgba(15, 23, 42, 0.28);
}

.composer-submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.composer-stop-icon {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: #fff;
}

.attachment-menu-wrap {
  position: relative;
}

.attachment-trigger {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid #d6dce5;
  background: #fff;
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.15s ease;
}

.attachment-trigger:hover,
.attachment-trigger.active {
  border-color: #047857;
  color: #047857;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.attachment-menu {
  position: absolute;
  left: 0;
  bottom: 46px;
  width: 220px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(12px);
  z-index: 10;
}

.attachment-menu-item {
  width: 100%;
  text-align: left;
  padding: 11px 12px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.attachment-menu-item:hover {
  background: #f8fafc;
}

.asset-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.asset-card--visual {
  grid-column: span 1;
}

.asset-card-header,
.asset-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.asset-card-title,
.asset-card-name {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.asset-card-text {
  margin: 0;
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
  white-space: pre-wrap;
}

.asset-clear-btn,
.asset-inline-btn {
  border: none;
  background: transparent;
  color: #047857;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.asset-clear-btn:hover,
.asset-inline-btn:hover {
  color: #065f46;
}

.asset-image {
  width: 100%;
  max-height: 240px;
  object-fit: contain;
  border-radius: 14px;
  border: 1px solid #e5e7eb;
  background: #f8fafc;
}

.paper-frame {
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.paper-frame--canvas {
  display: flex;
  flex: 1;
  height: 100%;
  min-height: 100%;
  overflow: hidden;
}

.paper-sheet {
  padding: 44px 48px;
  border-radius: 18px;
  background: #fffefb;
  border: 1px solid #ebe4d8;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.paper-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.paper-status-bar--ready {
  border-color: #a7f3d0;
  background: #ecfdf5;
}

.paper-status-bar--draft {
  border-color: #fde68a;
  background: #fffbeb;
}

.paper-status-bar--empty {
  border-color: #e2e8f0;
  background: #f8fafc;
}

.paper-status-bar__label {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.paper-status-bar__meta {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.paper-sheet--canvas {
  flex: 1;
  min-height: 100%;
  overflow-y: auto;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.55) transparent;
}

.conversation-log::-webkit-scrollbar,
.paper-sheet--canvas::-webkit-scrollbar {
  width: 10px;
}

.conversation-log::-webkit-scrollbar-track,
.paper-sheet--canvas::-webkit-scrollbar-track {
  background: transparent;
}

.conversation-log::-webkit-scrollbar-thumb,
.paper-sheet--canvas::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.45);
  border-radius: 999px;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.conversation-log::-webkit-scrollbar-thumb:hover,
.paper-sheet--canvas::-webkit-scrollbar-thumb:hover {
  background: rgba(100, 116, 139, 0.6);
  border-radius: 999px;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.paper-directions {
  margin: 0 0 22px;
  font-size: 32px;
  line-height: 1.15;
  color: #0f172a;
  font-family: Georgia, "Times New Roman", serif;
}

.paper-block {
  margin-top: 22px;
}

.paper-task-center {
  display: flex;
  justify-content: center;
  margin-bottom: 18px;
}

.paper-task-title {
  border: none;
  background: transparent;
  color: #334155;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: color 0.18s ease;
}

.paper-task-title:hover {
  color: #047857;
}

.paper-prompt,
.paper-meta,
.paper-attachment-heading,
.paper-attachment-content,
.paper-requirements li {
  font-family: Georgia, "Times New Roman", serif;
}

.paper-prompt,
.paper-attachment-content {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.paper-requirements {
  margin: 18px 0 0 24px;
  padding: 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.75;
}

.paper-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 18px;
  font-size: 18px;
  line-height: 1.6;
}

.paper-attachment {
  border-top: 1px solid #ebe4d8;
  padding-top: 26px;
}

.paper-attachment-heading {
  margin: 0 0 14px;
  color: #8a6d3b;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.paper-attachment-image {
  width: 100%;
  max-height: 420px;
  object-fit: contain;
  border-radius: 12px;
  border: 1px solid #ebe4d8;
  background: #fff;
  margin-bottom: 14px;
}

.paper-comic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.paper-comic-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #ebe4d8;
  border-radius: 18px;
  background: linear-gradient(180deg, #fffdf8 0%, #fff 100%);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
}

.paper-comic-visual {
  position: relative;
  min-height: 140px;
  padding: 16px;
  background:
    radial-gradient(circle at top left, rgba(254, 240, 138, 0.45), transparent 42%),
    radial-gradient(circle at bottom right, rgba(191, 219, 254, 0.5), transparent 38%),
    linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
  border-bottom: 1px solid #ebe4d8;
}

.paper-comic-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #8a6d3b;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.paper-comic-figure-row {
  position: absolute;
  right: 16px;
  bottom: 16px;
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.paper-comic-figure {
  width: 34px;
  height: 58px;
  border-radius: 16px 16px 10px 10px;
  background: linear-gradient(180deg, #475569 0%, #1f2937 100%);
  box-shadow:
    inset 0 -8px 0 rgba(255, 255, 255, 0.08),
    0 6px 12px rgba(15, 23, 42, 0.16);
}

.paper-comic-figure:nth-child(2) {
  height: 68px;
  background: linear-gradient(180deg, #0f766e 0%, #155e75 100%);
}

.paper-comic-figure:nth-child(3) {
  height: 50px;
  background: linear-gradient(180deg, #7c3aed 0%, #4338ca 100%);
}

.paper-comic-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px 18px;
}

.paper-comic-description,
.paper-comic-dialogue {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  color: #0f172a;
  line-height: 1.8;
}

.paper-comic-description {
  font-size: 18px;
}

.paper-comic-dialogue {
  padding: 10px 12px;
  border-radius: 14px;
  background: #f8fafc;
  color: #475569;
  font-size: 16px;
}

.paper-chart-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.paper-chart-title,
.paper-chart-summary {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  color: #0f172a;
}

.paper-chart-title {
  font-size: 22px;
  font-weight: 700;
}

.paper-chart-summary {
  font-size: 18px;
  line-height: 1.7;
}

.paper-chart-table {
  width: 100%;
  border-collapse: collapse;
  overflow: hidden;
  border-radius: 16px;
  border-style: hidden;
  box-shadow: 0 0 0 1px #e5e7eb;
}

.paper-chart-table th,
.paper-chart-table td {
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  text-align: left;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 17px;
  line-height: 1.6;
}

.paper-chart-table th {
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
}

.preview-actions {
  display: flex;
  gap: 12px;
}

.canvas-empty {
  display: flex;
  flex-direction: column;
  gap: 14px;
  justify-content: flex-start;
  min-height: 380px;
  padding-top: 8px;
  color: #64748b;
}

.canvas-empty-label {
  margin: 0;
  color: #8a6d3b;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.canvas-empty-title {
  margin: 0;
  color: #0f172a;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 36px;
  font-weight: 700;
}

.canvas-empty-text {
  margin: 0;
  max-width: 440px;
  color: #64748b;
  font-size: 16px;
  line-height: 1.9;
}

.field-help {
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
}

.confirm-card--inline {
  margin-bottom: 20px;
}

.audit-message--error {
  background: #fef2f2;
  color: #b91c1c;
}

.prompt-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ai-chart-table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.ai-chart-table th,
.ai-chart-table td {
  border: 1px solid #d1d5db;
  padding: 8px;
  background: #fff;
}

.ai-table-input {
  width: 100%;
  border: none;
  background: transparent;
  color: #111827;
  font-size: 13px;
}

.comic-scene-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid #dbe3ee;
  border-radius: 12px;
  background: #f8fafc;
}

/* Field */
.field {
  margin-bottom: 20px;
}

.field-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.required {
  color: #ef4444;
  margin-left: 2px;
}

.optional {
  font-weight: 400;
  color: #9ca3af;
  font-size: 12px;
}

.topic-input {
  width: 100%;
  padding: 12px 14px;
  border: 1.5px solid #d1d5db;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  color: #111827;
  resize: vertical;
  transition: border-color 0.15s;
  font-family: inherit;
  box-sizing: border-box;
}
.topic-input:focus {
  outline: none;
  border-color: #047857;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.1);
}
.topic-input::placeholder {
  color: #9ca3af;
}

.field-error {
  margin: 6px 0 0;
  font-size: 12px;
  color: #ef4444;
}

/* Chip group */
.chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.chip {
  padding: 6px 16px;
  font-size: 13px;
  color: #374151;
  background: #f3f4f6;
  border: 1.5px solid #e5e7eb;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.chip:hover {
  border-color: #047857;
  color: #047857;
}
.chip.selected {
  background: #ecfdf5;
  border-color: #047857;
  color: #047857;
  font-weight: 600;
}

.custom-word-input {
  display: inline-flex;
}

.custom-input {
  width: 100px;
  padding: 6px 12px;
  font-size: 13px;
  border: 1.5px solid #d1d5db;
  border-radius: 20px;
  outline: none;
  transition: border-color 0.15s;
}
.custom-input:focus {
  border-color: #047857;
}

/* Hint box */
/* Placeholder for future tabs */
.placeholder-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  border: 2px dashed #e5e7eb;
  border-radius: 12px;
  padding: 32px;
}

.placeholder-text {
  font-size: 16px;
  font-weight: 600;
  color: #6b7280;
  margin: 0 0 6px;
}

.placeholder-sub {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
}

.btn-back {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #6b7280;
  background: none;
  border: 1.5px solid #d1d5db;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-back:hover {
  color: #374151;
  border-color: #9ca3af;
}

/* Image upload */
.image-upload-area {
  margin-top: 10px;
}

.image-upload-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 20px;
  border: 2px dashed #d1d5db;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.image-upload-btn:hover {
  border-color: #047857;
  background: #f0fdf4;
}

.file-input-hidden {
  display: none;
}

.upload-icon {
  font-size: 24px;
  color: #9ca3af;
  font-weight: 300;
  line-height: 1;
}

.upload-text {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.upload-hint {
  font-size: 11px;
  color: #9ca3af;
}

.image-preview-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.image-preview {
  max-width: 100%;
  max-height: 200px;
  object-fit: contain;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.image-actions {
  display: flex;
  gap: 8px;
}

.image-action-btn {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
}

.recognize-btn {
  color: #fff;
  background: #047857;
}
.recognize-btn:hover:not(:disabled) {
  background: #065f46;
}
.recognize-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.remove-btn {
  color: #6b7280;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
}
.remove-btn:hover {
  color: #ef4444;
  border-color: #fca5a5;
  background: #fef2f2;
}

/* Audit message */
.audit-message {
  padding: 10px 14px;
  background: #fef3c7;
  border: 1px solid #fbbf24;
  border-radius: 8px;
  font-size: 13px;
  color: #92400e;
  line-height: 1.5;
  margin-bottom: 12px;
}

/* Confirm hint */
.confirm-hint {
  padding: 10px 14px;
  background: #fef3c7;
  border-radius: 8px;
  font-size: 13px;
  color: #92400e;
  line-height: 1.5;
  margin-bottom: 4px;
}

/* Confirm overlay */
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  animation: fadeIn 0.15s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.confirm-card {
  width: 90%;
  max-width: 480px;
  background: #fff;
  border-radius: 16px;
  padding: 32px 28px 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  animation: slideUp 0.2s ease;
}

.prompt-picker-card {
  max-width: 760px;
}

@keyframes slideUp {
  from { transform: translateY(16px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.confirm-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 4px;
}

.confirm-sub {
  font-size: 13px;
  color: #9ca3af;
  margin: 0 0 20px;
}

.confirm-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 24px;
}

.confirm-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.confirm-label {
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
}

.confirm-input {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  color: #111827;
  font-family: inherit;
  box-sizing: border-box;
  transition: border-color 0.15s;
}
.confirm-input:focus {
  outline: none;
  border-color: #047857;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.1);
}

.confirm-textarea {
  resize: vertical;
  line-height: 1.5;
}

.chip-sm {
  padding: 4px 12px;
  font-size: 12px;
}

.confirm-score-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.confirm-score-hint {
  font-size: 13px;
  color: #6b7280;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.parsing-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px 28px;
}

.parsing-text {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
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
  transition: color 0.15s, background 0.15s;
  line-height: 1;
}
.confirm-close:hover {
  color: #374151;
  background: #f3f4f6;
}

.confirm-card {
  position: relative;
}

.confirm-card--compact {
  max-width: 440px;
  padding-bottom: 28px;
}

.confirm-actions-3 {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.confirm-actions--split {
  justify-content: center;
  margin-top: 24px;
}

.gate-btn--danger {
  background: #dc2626;
}
.gate-btn--danger:hover {
  background: #b91c1c;
}

/* ── Past Prompts ── */
.prompt-filters {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.prompt-search {
  flex: 1;
}

.prompt-search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  color: #111827;
  box-sizing: border-box;
  transition: border-color 0.15s;
}
.prompt-search-input:focus {
  outline: none;
  border-color: #047857;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.1);
}

.prompt-year-select {
  padding: 8px 12px;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  color: #374151;
  background: #fff;
  cursor: pointer;
}
.prompt-year-select:focus {
  outline: none;
  border-color: #047857;
}

.prompt-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 0;
  color: #6b7280;
  font-size: 13px;
}

.prompt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
}

.prompt-card {
  padding: 12px 14px;
  border: 1.5px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.prompt-card:hover {
  border-color: #047857;
  background: #f0fdf4;
}
.prompt-card.selected {
  border-color: #047857;
  background: #ecfdf5;
  box-shadow: 0 0 0 2px rgba(4, 120, 87, 0.15);
}

.prompt-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.prompt-year-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  background: #047857;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
}

.prompt-paper {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.prompt-text-preview {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  margin: 0;
}

.prompt-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 12px;
}

.prompt-page-btn {
  padding: 6px 14px;
  font-size: 12px;
  color: #374151;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.prompt-page-btn:hover:not(:disabled) {
  border-color: #047857;
  color: #047857;
}
.prompt-page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.prompt-page-info {
  font-size: 12px;
  color: #6b7280;
}

.prompt-preview {
  margin-top: 16px;
  padding: 14px;
  background: #f9fafb;
  border: 1.5px solid #e5e7eb;
  border-radius: 10px;
}

.prompt-preview-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 8px;
}

.prompt-preview-text {
  font-size: 13px;
  color: #374151;
  line-height: 1.6;
  margin: 0 0 12px;
  white-space: pre-wrap;
}

.prompt-use-btn {
  font-size: 13px;
  padding: 8px 20px;
}

.prompt-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 600;
  border-radius: 3px;
}
.prompt-tag--img {
  background: #dbeafe;
  color: #1d4ed8;
}
.prompt-tag--mat {
  background: #fef3c7;
  color: #92400e;
}

.prompt-preview-image {
  max-width: 100%;
  max-height: 200px;
  object-fit: contain;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  margin-bottom: 10px;
}

.prompt-material-box {
  padding: 10px 12px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  margin-bottom: 10px;
}

.prompt-material-label {
  font-size: 11px;
  font-weight: 600;
  color: #92400e;
  margin: 0 0 4px;
}

.prompt-material-text {
  font-size: 13px;
  color: #374151;
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
}

/* Responsive */
@media (max-width: 560px) {
  .exam-setup {
    padding: 0;
  }
  .setup-card {
    height: 100vh;
    max-height: 100vh;
    border-radius: 0;
    padding: 24px 20px 20px;
  }
  .setup-card--wide {
    max-width: none;
  }
  .workbench-board {
    grid-template-columns: 1fr;
    gap: 22px;
  }
  .workbench-pane--canvas {
    padding-left: 0;
    border-left: none;
    border-top: 1px solid #e5edf5;
    padding-top: 22px;
  }
  .mode-switch {
    width: 100%;
    justify-content: space-between;
  }
  .workbench-header-actions {
    flex-direction: column;
    align-items: stretch;
  }
  .workbench-header-title {
    position: static;
    transform: none;
    width: 100%;
    max-width: none;
    margin-bottom: 8px;
  }
  .mode-switch-btn {
    flex: 1;
  }
  .confirm-card {
    padding: 24px 20px 20px;
  }
  .workbench-header,
  .preview-header {
    flex-direction: column;
    align-items: stretch;
  }
  .paper-sheet {
    padding: 28px 20px;
  }
  .composer-frame {
    padding: 0;
  }
  .composer-shell {
    padding: 14px 16px 14px;
  }
  .workbench-input {
    min-height: 180px;
  }
  .paper-directions {
    font-size: 24px;
  }
  .canvas-empty {
    min-height: 240px;
  }
  .paper-prompt,
  .paper-attachment-content,
  .paper-requirements li {
    font-size: 18px;
  }
}
</style>
