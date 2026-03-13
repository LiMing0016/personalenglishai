<template>
  <div class="exam-setup gate-center">
    <div class="setup-card">
      <h2 class="gate-title">考试写作 — 题目设置</h2>
      <p class="gate-desc">填写作文题目信息后开始写作</p>

      <!-- Tab 切换 -->
      <div class="tab-bar">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-item"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 手动输入 Tab -->
      <div v-if="activeTab === 'manual'" class="tab-content">
        <!-- 题目输入 -->
        <div class="field">
          <label class="field-label">
            题目 / 写作要求
            <span class="required">*</span>
          </label>
          <textarea
            v-model="topic"
            class="topic-input"
            rows="7"
            placeholder="请输入完整的作文题目和写作要求，例如：&#10;Write an essay of 120-150 words based on the picture below. In your essay, you should 1) describe the picture briefly, 2) interpret the meaning, and 3) give your comments.&#10;&#10;或者：&#10;假设你是李华，你的外国朋友Tom对中国的春节很感兴趣，请给他写一封信，介绍中国春节的习俗。80-120词"
          />
          <p v-if="topicError" class="field-error">{{ topicError }}</p>

          <!-- 图片上传 -->
          <div class="image-upload-area">
            <div v-if="uploadedImage" class="image-preview-wrap">
              <img :src="uploadedImage" class="image-preview" alt="题目图片" />
              <div class="image-actions">
                <button
                  class="image-action-btn recognize-btn"
                  :disabled="recognizing"
                  @click="onRecognizeImage"
                >
                  {{ recognizing ? '识别中...' : '识别图片文字' }}
                </button>
                <button class="image-action-btn remove-btn" @click="removeImage">删除</button>
              </div>
              <p v-if="recognizeError" class="field-error">{{ recognizeError }}</p>
            </div>
            <label v-else class="image-upload-btn">
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                class="file-input-hidden"
                @change="onFileChange"
              />
              <span class="upload-icon">+</span>
              <span class="upload-text">上传题目图片</span>
              <span class="upload-hint">支持 JPG/PNG/WebP，拍照或截图均可</span>
            </label>
          </div>
        </div>

        <!-- 体裁选择 -->
        <div class="field">
          <label class="field-label">体裁 <span class="optional">（可选）</span></label>
          <div class="chip-group">
            <button
              v-for="g in genres"
              :key="g"
              class="chip"
              :class="{ selected: genre === g }"
              @click="genre = genre === g ? null : g"
            >
              {{ g }}
            </button>
          </div>
        </div>

        <!-- 字数要求 -->
        <div class="field">
          <label class="field-label">字数要求 <span class="optional">（可选）</span></label>
          <div class="chip-group">
            <button
              v-for="w in wordRangeOptions"
              :key="w"
              class="chip"
              :class="{ selected: wordRange === w }"
              @click="wordRange = wordRange === w ? null : w"
            >
              {{ w }}
            </button>
            <div v-if="showCustomWordRange" class="custom-word-input">
              <input
                v-model="customWordRange"
                type="text"
                placeholder="如: 150-200"
                class="custom-input"
              />
            </div>
            <button
              class="chip"
              :class="{ selected: showCustomWordRange }"
              @click="toggleCustomWordRange"
            >
              自定义
            </button>
          </div>
        </div>

        <!-- 满分分值 -->
        <div class="field">
          <label class="field-label">满分分值 <span class="optional">（默认 100 分）</span></label>
          <div class="chip-group">
            <button
              v-for="s in maxScoreOptions"
              :key="s"
              class="chip"
              :class="{ selected: maxScore === s }"
              @click="maxScore = maxScore === s ? 100 : s"
            >
              {{ s }} 分
            </button>
            <div class="custom-word-input">
              <input
                v-model.number="customMaxScore"
                type="number"
                min="1"
                max="200"
                placeholder="自定义"
                class="custom-input"
                @input="maxScore = customMaxScore || 100"
              />
            </div>
          </div>
        </div>

        <!-- AI 审核提示 -->
        <div v-if="auditMessage" class="audit-message">
          <span>{{ auditMessage }}</span>
        </div>

        <!-- 提示 -->
        <div class="hint-box">
          <span class="hint-icon">i</span>
          <span>也可以直接粘贴完整考试题目，未选择的信息系统会自动识别</span>
        </div>
      </div>

      <!-- AI 生成 Tab（占位） -->
      <div v-else-if="activeTab === 'ai'" class="tab-content">
        <div class="placeholder-box">
          <p class="placeholder-text">AI 智能出题功能即将上线</p>
          <p class="placeholder-sub">支持按体裁、难度自动生成模拟考试题目</p>
        </div>
      </div>

      <!-- 历年真题 Tab -->
      <div v-else-if="activeTab === 'past'" class="tab-content">
        <!-- 筛选栏 -->
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

        <!-- 加载中 -->
        <div v-if="promptLoading" class="prompt-loading">
          <div class="gate-spinner" />
          <p>加载中...</p>
        </div>

        <!-- 空状态 -->
        <div v-else-if="promptItems.length === 0" class="placeholder-box">
          <p class="placeholder-text">{{ promptKeyword || promptYear ? '未找到匹配的题目' : '暂无真题数据' }}</p>
          <p class="placeholder-sub">{{ promptKeyword || promptYear ? '请尝试其他关键词或年份' : '题库数据即将导入' }}</p>
        </div>

        <!-- 真题列表 -->
        <div v-else class="prompt-list">
          <div
            v-for="item in promptItems"
            :key="item.id"
            class="prompt-card"
            :class="{ selected: selectedPrompt?.id === item.id }"
            @click="selectedPrompt = selectedPrompt?.id === item.id ? null : item"
          >
            <div class="prompt-card-header">
              <span class="prompt-year-badge" v-if="item.examYear">{{ item.examYear }}</span>
              <span class="prompt-paper">{{ item.paper }}</span>
              <span v-if="item.imageUrl" class="prompt-tag prompt-tag--img">图</span>
              <span v-if="item.materialText" class="prompt-tag prompt-tag--mat">材料</span>
            </div>
            <p class="prompt-text-preview">{{ item.promptText.slice(0, 150) }}{{ item.promptText.length > 150 ? '...' : '' }}</p>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="promptTotal > promptPageSize" class="prompt-pagination">
          <button class="prompt-page-btn" :disabled="promptPage <= 1" @click="loadPrompts(promptPage - 1)">上一页</button>
          <span class="prompt-page-info">{{ promptPage }} / {{ Math.ceil(promptTotal / promptPageSize) }}</span>
          <button class="prompt-page-btn" :disabled="promptPage >= Math.ceil(promptTotal / promptPageSize)" @click="loadPrompts(promptPage + 1)">下一页</button>
        </div>

        <!-- 选中预览 -->
        <div v-if="selectedPrompt" class="prompt-preview">
          <h4 class="prompt-preview-title">{{ selectedPrompt.paper }}</h4>
          <img v-if="selectedPrompt.imageUrl" :src="selectedPrompt.imageUrl" class="prompt-preview-image" alt="题目图片" />
          <div v-if="selectedPrompt.materialText" class="prompt-material-box">
            <p class="prompt-material-label">阅读材料</p>
            <p class="prompt-material-text">{{ selectedPrompt.materialText }}</p>
          </div>
          <p class="prompt-preview-text">{{ selectedPrompt.promptText }}</p>
          <button class="gate-btn prompt-use-btn" @click="useSelectedPrompt">使用此题目</button>
        </div>
      </div>

      <!-- AI 确认卡片 -->
      <div v-if="confirmStep === 'confirming'" class="confirm-overlay">
        <div class="confirm-card">
          <h3 class="confirm-title">AI 已识别题目信息</h3>
          <p class="confirm-sub">请确认或修改以下信息</p>
          <div class="confirm-body">
            <div v-if="auditMessage" class="confirm-hint">{{ auditMessage }}</div>
            <div class="confirm-field">
              <label class="confirm-label">题目</label>
              <textarea
                v-model="parsedResult.topic"
                class="confirm-input confirm-textarea"
                rows="3"
              />
            </div>
            <div class="confirm-field">
              <label class="confirm-label">体裁</label>
              <div class="chip-group">
                <button
                  v-for="g in genres"
                  :key="g"
                  class="chip chip-sm"
                  :class="{ selected: parsedResult.genre === g }"
                  @click="parsedResult.genre = parsedResult.genre === g ? null : g"
                >
                  {{ g }}
                </button>
              </div>
            </div>
            <div class="confirm-field">
              <label class="confirm-label">字数</label>
              <input
                v-model="parsedResult.wordRange"
                class="confirm-input"
                placeholder="如: 80-120"
              />
            </div>
            <div class="confirm-field">
              <label class="confirm-label">写作要求</label>
              <textarea
                v-model="parsedResult.requirements"
                class="confirm-input confirm-textarea"
                rows="3"
                placeholder="如有具体写作要点可在此补充"
              />
            </div>
            <div class="confirm-field">
              <label class="confirm-label">满分</label>
              <div class="confirm-score-row">
                <input
                  v-model.number="parsedResult.maxScore"
                  type="number"
                  min="1"
                  max="200"
                  class="confirm-input"
                  style="width: 100px;"
                />
                <span class="confirm-score-hint">分</span>
              </div>
            </div>
          </div>
          <div class="confirm-actions">
            <button class="btn-back" @click="onCancelConfirm">返回修改</button>
            <button class="gate-btn" @click="onFinalConfirm">确认，开始写作</button>
          </div>
        </div>
      </div>

      <!-- 解析中 -->
      <div v-if="confirmStep === 'parsing'" class="confirm-overlay">
        <div class="confirm-card parsing-card">
          <div class="gate-spinner" />
          <p class="parsing-text">AI 正在解析题目信息...</p>
        </div>
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

      <!-- 底部操作栏 -->
      <div class="action-bar">
        <button class="btn-back" @click="handleBack">
          返回选择模式
        </button>
        <button
          class="gate-btn"
          :disabled="!canStart || confirmStep !== 'idle'"
          @click="onStartConfirm"
        >
          开始写作
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useSessionStorage, useDebounceFn } from '@vueuse/core'
import { auditTopic, recognizeTopicImage, startWritingSession, getWritingSessionMetadata, getEssayPrompts } from '@/api/writing'
import type { EssayPromptItem } from '@/api/writing'
import { getStageId } from '@/constants/stage'

export interface ExamTopicInfo {
  topic: string
  genre: string | null
  wordRange: string | null
  requirements: string | null
  maxScore: number
  sourceType: 'manual' | 'past_prompt' | 'ai_generated' | 'free_input'
  examType: string | null
  taskType: string | null
  minWords: number | null
  recommendedMaxWords: number | null
}

const props = defineProps<{
  initialTopic?: string
  studyStage?: string
}>()

const emit = defineEmits<{
  confirm: [info: ExamTopicInfo]
  back: []
  saveDraft: []
}>()

const tabs = [
  { key: 'manual', label: '手动输入' },
  { key: 'ai', label: 'AI 生成' },
  { key: 'past', label: '历年真题' },
] as const

type TabKey = (typeof tabs)[number]['key']

const activeTab = ref<TabKey>('manual')

// ── 手动输入表单 ──

const topic = ref('')
const genre = ref<string | null>(null)
const wordRange = ref<string | null>(null)
const showCustomWordRange = ref(false)
const customWordRange = ref('')

// ── 图片上传 ──
const uploadedImage = ref<string | null>(null) // data URL
const uploadedImageBase64 = ref<string | null>(null) // pure base64
const recognizing = ref(false)
const recognizeError = ref<string | null>(null)

function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    recognizeError.value = '图片不能超过 5MB'
    return
  }
  recognizeError.value = null
  const reader = new FileReader()
  reader.onload = () => {
    const dataUrl = reader.result as string
    uploadedImage.value = dataUrl
    // 提取纯 base64（去掉 data:image/xxx;base64, 前缀）
    uploadedImageBase64.value = dataUrl.split(',')[1] || null
  }
  reader.readAsDataURL(file)
  // 清空 input 以便重复选同一文件
  ;(e.target as HTMLInputElement).value = ''
}

function removeImage() {
  uploadedImage.value = null
  uploadedImageBase64.value = null
  recognizeError.value = null
}

async function onRecognizeImage() {
  if (!uploadedImageBase64.value) return
  recognizing.value = true
  recognizeError.value = null
  try {
    const res = await recognizeTopicImage({ imageBase64: uploadedImageBase64.value })
    if (res.text) {
      // 追加到现有题目文本
      topic.value = topic.value
        ? topic.value.trimEnd() + '\n' + res.text
        : res.text
    } else {
      recognizeError.value = '未能识别到文字内容，请尝试更清晰的图片'
    }
  } catch {
    recognizeError.value = '图片识别失败，请重试'
  } finally {
    recognizing.value = false
  }
}

const genres = ['书信', '议论文', '说明文', '演讲稿', '看图作文', '通知', '日记']

const wordRangeOptions = ['80-100', '100-120', '120-150']

const maxScore = ref(100)
const customMaxScore = ref<number | null>(null)
const maxScoreOptions = [10, 15, 20, 25]

function toggleCustomWordRange() {
  showCustomWordRange.value = !showCustomWordRange.value
  if (!showCustomWordRange.value) {
    customWordRange.value = ''
  } else {
    wordRange.value = null
  }
}

// ── 校验 ──

const topicError = computed(() => {
  const t = topic.value.trim()
  if (!t) return null // 空的时候不报错，靠 canStart 禁用按钮
  if (/^\d+$/.test(t)) return '请输入有效的作文题目'
  if (/^[^a-zA-Z\u4e00-\u9fff]*$/.test(t)) return '请输入有效的作文题目'
  if (t.length < 5) return '题目过于简略，建议补充写作情境和要求'
  return null
})

const canStart = computed(() => {
  if (activeTab.value === 'past') return !!selectedPrompt.value
  const t = topic.value.trim()
  if (!t) return false
  if (topicError.value) return false
  return true
})

// ── AI 确认流程 ──

type ConfirmStep = 'idle' | 'parsing' | 'confirming'
const confirmStep = ref<ConfirmStep>('idle')
const parsedResult = ref<ExamTopicInfo>({
  topic: '',
  genre: null,
  wordRange: null,
  requirements: null,
  maxScore: 100,
  sourceType: 'manual',
  examType: null,
  taskType: null,
  minWords: null,
  recommendedMaxWords: null,
})
const auditMessage = ref<string | null>(null)

function getEffectiveWordRange(): string | null {
  return showCustomWordRange.value
    ? (customWordRange.value.trim() || null)
    : wordRange.value
}

function parseWordRange(value: string | null): { minWords: number | null; recommendedMaxWords: number | null } {
  if (!value) {
    return { minWords: null, recommendedMaxWords: null }
  }
  const compact = value.trim().replace(/\s+/g, '')
  const rangeMatch = compact.match(/^(\d+)\s*[-~至]\s*(\d+)$/)
  if (rangeMatch) {
    return {
      minWords: Number(rangeMatch[1]),
      recommendedMaxWords: Number(rangeMatch[2]),
    }
  }
  const singleMatch = compact.match(/^(\d+)$/)
  if (singleMatch) {
    const value = Number(singleMatch[1])
    return {
      minWords: value,
      recommendedMaxWords: value
    }
  }
  return { minWords: null, recommendedMaxWords: null }
}


function extractPromptFallback(text: string, currentWordRange?: string | null, currentRequirements?: string | null) {
  const source = text.trim()
  const existingWordRange = currentWordRange?.trim() || null
  const existingRequirements = currentRequirements?.trim() || null

  let detectedWordRange = existingWordRange
  if (!detectedWordRange) {
    const rangeMatch = source.match(/(\d+\s*[-~至]\s*\d+)\s*(words?|词)/i)
    const singleMatch = source.match(/(?:at least|不少于|至少)\s*(\d+)\s*(words?|词)/i)
    if (rangeMatch) {
      detectedWordRange = rangeMatch[1].replace(/\s+/g, '')
    } else if (singleMatch) {
      detectedWordRange = singleMatch[1]
    }
  }

  let detectedRequirements = existingRequirements
  if (!detectedRequirements) {
    const englishReq = source.match(/In your essay, you should:?\s*([\s\S]*)/i)
    const chineseReq = source.match(/写作要求[:：]?\s*([\s\S]*)/i)
    if (englishReq?.[1]) {
      detectedRequirements = englishReq[1].trim()
    } else if (chineseReq?.[1]) {
      detectedRequirements = chineseReq[1].trim()
    }
  }

  return {
    wordRange: detectedWordRange,
    requirements: detectedRequirements,
  }
}

async function onStartConfirm() {
  if (!canStart.value) return

  const pendingPrompt = selectedPrompt.value
  const rawTopic = activeTab.value === 'past' && pendingPrompt
    ? pendingPrompt.promptText.trim()
    : topic.value.trim()

  if (!rawTopic) return

  topic.value = rawTopic
  confirmStep.value = 'parsing'
  auditMessage.value = null

  try {
    const res = await auditTopic({
      topic: rawTopic,
      genre: genre.value,
      wordRange: getEffectiveWordRange() ?? undefined,
    })

    if (res.status === 'invalid') {
      auditMessage.value = res.message || '请输入有效的作文题目'
      confirmStep.value = 'idle'
      return
    }

    const normalizedTopic = res.topic || rawTopic
    const extractionSource = [rawTopic, normalizedTopic].filter(Boolean).join('\n')
    const fallback = extractPromptFallback(
      extractionSource,
      res.wordRange || getEffectiveWordRange(),
      res.requirements || null,
    )
    const parsedWordRange = parseWordRange(fallback.wordRange)
    parsedResult.value = {
      topic: normalizedTopic,
      genre: res.genre || genre.value || null,
      wordRange: fallback.wordRange || null,
      requirements: fallback.requirements || null,
      maxScore: maxScore.value,
      sourceType: pendingPrompt ? 'past_prompt' : 'manual',
      examType: props.studyStage || null,
      taskType: pendingPrompt?.task || null,
      minWords: parsedWordRange.minWords,
      recommendedMaxWords: parsedWordRange.recommendedMaxWords,
    }

    if (res.status === 'need_more_info' && res.message) {
      auditMessage.value = res.message
    }

    confirmStep.value = 'confirming'
  } catch (e) {
    // API 失败时兜底：直接使用用户输入
    const fallback = extractPromptFallback(rawTopic, getEffectiveWordRange(), null)
    const parsedWordRange = parseWordRange(fallback.wordRange)
    parsedResult.value = {
      topic: rawTopic,
      genre: genre.value,
      wordRange: fallback.wordRange || null,
      requirements: fallback.requirements || null,
      maxScore: maxScore.value,
      sourceType: pendingPrompt ? 'past_prompt' : 'manual',
      examType: props.studyStage || null,
      taskType: pendingPrompt?.task || null,
      minWords: parsedWordRange.minWords,
      recommendedMaxWords: parsedWordRange.recommendedMaxWords,
    }
    confirmStep.value = 'confirming'
  }
}

function onCancelConfirm() {
  if (parsedResult.value.sourceType === 'past_prompt' && selectedPrompt.value) {
    activeTab.value = 'past'
  }
  confirmStep.value = 'idle'
}

function onFinalConfirm() {
  clearExamSetupState()
  emit('confirm', parsedResult.value)
  confirmStep.value = 'idle'
}

// ── 草稿与页面状态持久化 ──

const DRAFT_KEY = 'peai:examSetup:draft'
const LIVE_STATE_KEY = 'peai:examSetup:live'

interface ExamSetupDraft {
  topic: string
  genre: string | null
  wordRange: string | null
  customWordRange: string
  showCustomWordRange: boolean
  maxScore: number
  uploadedImage: string | null
  uploadedImageBase64: string | null
}

const savedDraft = useSessionStorage<ExamSetupDraft | null>(DRAFT_KEY, null)

interface ExamSetupLiveState extends ExamSetupDraft {
  activeTab: TabKey
  customMaxScore: number | null
  confirmStep: ConfirmStep
  parsedResult: ExamTopicInfo
  auditMessage: string | null
  promptKeyword: string
  promptYear: number | null
  promptPage: number
  selectedPrompt: EssayPromptItem | null
}

const liveState = useSessionStorage<ExamSetupLiveState | null>(LIVE_STATE_KEY, null)

let liveStateCleared = false

function clearExamSetupState() {
  liveStateCleared = true
  savedDraft.value = null
  liveState.value = null
}

const restoringLiveState = ref(false)

onMounted(() => {
  if (liveState.value) {
    restoringLiveState.value = true
    try {
      const state = liveState.value
      const validTabs: TabKey[] = ['manual', 'ai', 'past']
      activeTab.value = validTabs.includes(state.activeTab) ? state.activeTab : 'manual'
      topic.value = state.topic ?? ''
      genre.value = state.genre ?? null
      wordRange.value = state.wordRange ?? null
      customWordRange.value = state.customWordRange ?? ''
      showCustomWordRange.value = state.showCustomWordRange ?? false
      maxScore.value = state.maxScore ?? 100
      customMaxScore.value = state.customMaxScore ?? null
      uploadedImage.value = state.uploadedImage ?? null
      uploadedImageBase64.value = state.uploadedImageBase64 ?? null
      confirmStep.value = 'idle'
      parsedResult.value = state.parsedResult
        ? { ...state.parsedResult }
        : { topic: '', genre: null, wordRange: null, requirements: null, maxScore: 100, sourceType: 'manual', examType: props.studyStage ?? null, taskType: null, minWords: null, recommendedMaxWords: null }
      auditMessage.value = state.auditMessage ?? null
      promptKeyword.value = state.promptKeyword ?? ''
      promptYear.value = state.promptYear ?? null
      promptYearSelect.value = state.promptYear ?? 0
      promptPage.value = state.promptPage ?? 1
      selectedPrompt.value = state.selectedPrompt ?? null

      if (activeTab.value === 'past') {
        void loadPrompts(promptPage.value)
      }
    } catch (e) {
      console.warn('[ExamSetup] liveState restore failed, clearing', e)
      liveState.value = null
    } finally {
      restoringLiveState.value = false
    }
  } else if (savedDraft.value) {
    try {
      const d = savedDraft.value
      topic.value = d.topic ?? ''
      genre.value = d.genre ?? null
      wordRange.value = d.wordRange ?? null
      customWordRange.value = d.customWordRange ?? ''
      showCustomWordRange.value = d.showCustomWordRange ?? false
      maxScore.value = d.maxScore ?? 100
      uploadedImage.value = d.uploadedImage ?? null
      uploadedImageBase64.value = d.uploadedImageBase64 ?? null
    } catch (e) {
      console.warn('[ExamSetup] draft restore failed, clearing', e)
    }
    savedDraft.value = null
  } else if (props.initialTopic) {
    topic.value = props.initialTopic
  }
})

// ── 返回确认 ──

const showBackConfirm = ref(false)

const isDirty = computed(() => {
  return topic.value.trim().length > 0
    || genre.value !== null
    || wordRange.value !== null
    || (showCustomWordRange.value && customWordRange.value.trim().length > 0)
    || uploadedImage.value !== null
    || maxScore.value !== 100
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
  try {
    const t = topic.value.trim()
    console.log('[ExamSetup] saveAndLeave, topic:', t)
    if (t) {
      const parsedWordRange = parseWordRange(getEffectiveWordRange())
      const sourceType = activeTab.value === 'past' ? 'past_prompt' : 'manual'
      const res = await startWritingSession({
        mode: 'exam',
        taskPrompt: t,
        title: t.slice(0, 100),
        draft: true,
        studyStage: props.studyStage ?? undefined,
        sourceType,
        titleSnapshot: t.slice(0, 255),
        topicTitle: topic.value.trim(),
        promptText: t,
        genre: genre.value,
        examType: props.studyStage ?? null,
        taskType: selectedPrompt.value?.task || null,
        minWords: parsedWordRange.minWords,
        recommendedMaxWords: parsedWordRange.recommendedMaxWords,
        maxScore: maxScore.value,
      })
      console.log('[ExamSetup] startWritingSession result:', JSON.stringify(res))
      const metadata = await getWritingSessionMetadata(res.docId).catch((err) => {
        console.warn('[ExamSetup] load session metadata failed', err)
        return null
      })
      console.log('[ExamSetup] writing metadata:', metadata)

    }
  } catch (e) {
    console.warn('[ExamSetup] save draft doc failed', e)
  } finally {
    saving.value = false
  }
  clearExamSetupState()
  showBackConfirm.value = false
  emit('saveDraft')
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
  topic.value = prompt.promptText
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
  await onStartConfirm()
}

const debouncedSaveLiveState = useDebounceFn(() => {
  if (liveStateCleared) return
  liveState.value = {
    activeTab: activeTab.value,
    topic: topic.value,
    genre: genre.value,
    wordRange: wordRange.value,
    customWordRange: customWordRange.value,
    showCustomWordRange: showCustomWordRange.value,
    maxScore: maxScore.value,
    uploadedImage: uploadedImage.value,
    uploadedImageBase64: uploadedImageBase64.value,
    customMaxScore: customMaxScore.value,
    confirmStep: 'idle' as ConfirmStep,
    parsedResult: { ...parsedResult.value },
    auditMessage: auditMessage.value,
    promptKeyword: promptKeyword.value,
    promptYear: promptYear.value,
    promptPage: promptPage.value,
    selectedPrompt: selectedPrompt.value,
  }
}, 150)

watch(
  [
    activeTab,
    topic,
    genre,
    wordRange,
    customWordRange,
    showCustomWordRange,
    maxScore,
    customMaxScore,
    uploadedImage,
    uploadedImageBase64,
    confirmStep,
    parsedResult,
    auditMessage,
    promptKeyword,
    promptYear,
    promptPage,
    selectedPrompt,
  ],
  () => { debouncedSaveLiveState() },
  { deep: true }
)

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
.setup-card {
  width: 100%;
  max-width: 640px;
  background: #fff;
  border-radius: 16px;
  padding: 40px 36px 32px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  user-select: none;
  -webkit-user-select: none;
}

/* 输入区域允许选中 */
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

/* Tab bar */
.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: 2px solid #e5e7eb;
  margin-bottom: 24px;
}

.tab-item {
  flex: 1;
  padding: 10px 0;
  font-size: 14px;
  font-weight: 600;
  color: #6b7280;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.tab-item:hover {
  color: #374151;
}
.tab-item.active {
  color: #047857;
  border-bottom-color: #047857;
}

/* Tab content */
.tab-content {
  min-height: 280px;
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
.hint-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  background: #f0fdf4;
  border-radius: 10px;
  font-size: 13px;
  color: #374151;
  line-height: 1.5;
  margin-top: 8px;
}

.hint-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #047857;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 1px;
}

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

/* Action bar */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid #f3f4f6;
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

.confirm-actions-3 {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
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
  .setup-card {
    padding: 28px 20px 24px;
  }
  .confirm-card {
    padding: 24px 20px 20px;
  }
}
</style>



