<template>
  <div class="coach-panel">
    <header class="coach-header">
      <div class="coach-title-block">
        <span class="coach-kicker">Agent Composer</span>
        <h3 class="coach-title">写作教练</h3>
      </div>
    </header>

    <section class="status-strip" aria-label="写作上下文">
      <span class="status-chip">{{ writingMode === 'exam' ? '考试模式' : '自由写作' }}</span>
      <span v-if="studyStageLabel" class="status-chip">{{ studyStageLabel }}</span>
      <span v-if="taskType" class="status-chip">{{ taskType }}</span>
      <span v-if="wordRangeLabel" class="status-chip">{{ wordRangeLabel }}</span>
      <span class="status-chip status-chip-strong">{{ stageLabel }}</span>
    </section>

    <section class="flow-shell" aria-label="写作流程">
      <div class="flow-head">
        <div>
          <strong>写作流程</strong>
          <small>{{ flowSummary }}</small>
        </div>
        <span class="flow-progress">{{ activeFlowIndex + 1 }} / {{ writingFlowSteps.length }}</span>
      </div>

      <div class="flow-steps">
        <button
          v-for="(step, index) in writingFlowSteps"
          :key="step.key"
          type="button"
          class="flow-step"
          :class="flowStepPhase(step)"
          @click="selectFlowStep(step)"
        >
          <span class="flow-node">
            <span v-if="index < activeFlowIndex">✓</span>
            <span v-else>{{ index + 1 }}</span>
          </span>
          <span class="flow-copy">
            <strong>{{ step.title }}</strong>
            <small>{{ step.brief }}</small>
          </span>
        </button>
      </div>

      <article class="current-stage-card">
        <div class="current-stage-copy">
          <span class="current-stage-kicker">当前阶段</span>
          <h4>{{ activeFlowStep.title }}</h4>
          <p>{{ activeFlowStep.description }}</p>
        </div>
        <div class="stage-insights">
          <div class="stage-insight">
            <span>中心任务</span>
            <strong>{{ writingPlan.centralTaskShort }}</strong>
          </div>
          <div class="stage-insight">
            <span>阶段目标</span>
            <strong>{{ activeFlowStep.goal }}</strong>
          </div>
          <div class="stage-insight">
            <span>下一步</span>
            <strong>{{ nextFlowStepTitle }}</strong>
          </div>
        </div>
        <div class="stage-actions">
          <button type="button" class="stage-primary" @click="selectFlowStep(activeFlowStep)">
            {{ activeFlowStep.actionLabel }}
          </button>
          <button
            v-if="previousFlowStep"
            type="button"
            class="stage-secondary"
            @click="selectFlowStep(previousFlowStep)"
          >
            返回上一步
          </button>
        </div>
      </article>
    </section>

    <section ref="messageListRef" class="message-list">
      <div
        v-for="(message, index) in messages"
        :key="`${message.role}-${message.at}-${index}`"
        class="message-row"
        :class="message.role"
      >
        <div class="message-bubble" v-html="renderMarkdown(message.text)" @click="onRenderedMarkdownClick"></div>
      </div>

      <article v-if="activeSuggestion" class="suggestion-card">
        <div class="suggestion-meta">
          <span class="suggestion-type">{{ suggestionTypeLabel }}</span>
          <span class="suggestion-state">待确认</span>
        </div>
        <p class="suggestion-text">{{ activeSuggestion.text }}</p>
        <div class="suggestion-actions">
          <button type="button" class="ghost-btn" @click="activeSuggestion = null">取消</button>
          <button type="button" class="apply-btn" @click="onApplySuggestion">应用到正文</button>
        </div>
      </article>
    </section>

    <footer class="composer">
      <div class="composer-shell" :class="{ 'tool-menu-open': toolMenuOpen }">
        <div v-if="selectedText" class="selected-line">
          <span class="selected-label">选区</span>
          <SelectedTextChip :text="selectedText" :max-chars="72" @dismiss="clearSelectedText" />
        </div>
        <textarea
          ref="composerInputRef"
          :value="modelValue"
          class="composer-input"
          :class="{ 'has-selection': !!selectedText }"
          rows="3"
          :placeholder="composerPlaceholder"
          @input="onInput"
          @keydown="onKeydown"
        />
        <div v-if="toolMenuOpen" class="tool-menu" role="menu" aria-label="选择写作教练能力">
          <button
            v-for="item in coachTools"
            :key="item.key"
            type="button"
            class="tool-menu-item"
            :class="{ active: selectedTool.key === item.key }"
            role="menuitem"
            @click="selectTool(item)"
          >
            <span class="tool-icon">{{ item.icon }}</span>
            <span class="tool-copy">
              <strong>{{ item.label }}</strong>
              <small>{{ item.hint }}</small>
            </span>
          </button>
        </div>
        <div class="composer-bottom">
          <div class="composer-toolbar">
            <button
              type="button"
              class="plus-btn"
              :aria-expanded="toolMenuOpen"
              aria-label="选择写作教练能力"
              @click="toolMenuOpen = !toolMenuOpen"
            >
              +
            </button>
            <button type="button" class="agent-chip" @click="toolMenuOpen = !toolMenuOpen">
              <span class="agent-dot">{{ selectedTool.icon }}</span>
              <span>{{ selectedTool.label }}</span>
            </button>
            <button
              type="button"
              class="draft-toggle"
              :class="{ active: includeDraft }"
              @click="includeDraft = !includeDraft"
            >
              {{ includeDraft ? '引用作文' : '不引用作文' }}
            </button>
          </div>
          <div class="provider-pill">{{ isGenerating ? 'Thinking' : currentProviderLabel }}</div>
          <button
            type="button"
            class="send-btn"
            :class="{ generating: isGenerating }"
            :disabled="!isGenerating && !canSend"
            @click="onSendOrStop"
          >
            <span v-if="isGenerating">停</span>
            <span v-else>↑</span>
          </button>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onMounted, ref, watch } from 'vue'
import { writingSelectionStoreKey } from '../useWritingSelectionStore'
import SelectedTextChip from './SelectedTextChip.vue'
import { copyMarkdownCodeFromClick, renderAssistantMarkdown } from '@/components/assistant/markdown'
import type { WritingAiProvider } from '@/api/writing'

type WritingMode = 'free' | 'exam'
type CoachStage = 'idle' | 'analyzing' | 'idea_confirmed' | 'outlined' | 'paragraphing' | 'drafted'
type CoachToolKey = 'coach' | 'analyze' | 'outline' | 'next' | 'topic' | 'polish' | 'draft'
type WritingFlowKey =
  | 'analysis'
  | 'ideation'
  | 'materials'
  | 'outline'
  | 'drafting'
  | 'revision'
  | 'polish'
  | 'final_check'
type MessageRole = 'user' | 'assistant'
type ChatMessage = { role: MessageRole; text: string; at: number }
type RecentMessageDto = { role: 'user' | 'assistant'; content: string }
type SuggestionType = 'replace_selection' | 'append_text' | 'replace_all'
type SafeApplySuggestion = { type: SuggestionType; text: string }
type WritingFlowStep = {
  key: WritingFlowKey
  title: string
  brief: string
  description: string
  goal: string
  actionLabel: string
  prompt: string
  toolKey: CoachToolKey
  coachStage: CoachStage
}
type CoachTool = {
  key: CoachToolKey
  label: string
  icon: string
  hint: string
  stage: CoachStage
  flowStage: WritingFlowKey
  prompt: string
}

const HISTORY_KEY_PREFIX = 'peai:writing-coach:history:'
const DEFAULT_HINT = '我会按“审题 → 提纲 → 下一段 → 偏题检查 → 润色 → 终稿”的节奏陪你写作文。先选择一个阶段，或直接输入你的需求。'

const props = withDefaults(
  defineProps<{
    modelValue: string
    selectedTextPinned?: string
    selectedSpanPinned?: { start: number; end: number } | null
    lastChatResult?: { displayText: string; replaceText?: string } | null
    conversationId?: string
    isGenerating?: boolean
    writingMode?: WritingMode
    aiProvider: WritingAiProvider
    taskPrompt?: string
    essay?: string
    studyStage?: string | null
    taskType?: string | null
    minWords?: number | null
    recommendedMaxWords?: number | null
  }>(),
  {
    selectedTextPinned: '',
    isGenerating: false,
    writingMode: 'free',
    taskPrompt: '',
    essay: '',
    studyStage: null,
    taskType: null,
    minWords: null,
    recommendedMaxWords: null,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  stop: []
  'dismiss-selection': []
  'replace-selection-with': [resultText: string]
  'apply-suggestion': [payload: SafeApplySuggestion]
  close: []
  cleared: []
}>()

const selectionStore = inject(writingSelectionStoreKey, null)
const messageListRef = ref<HTMLElement | null>(null)
const composerInputRef = ref<HTMLTextAreaElement | null>(null)
const includeDraft = ref(false)
const toolMenuOpen = ref(false)
const selectedToolKey = ref<CoachToolKey>('coach')
const coachStage = ref<CoachStage>('idle')
const activeFlowKey = ref<WritingFlowKey>('analysis')
const activeSuggestion = ref<SafeApplySuggestion | null>(null)
const lastAssistantPayload = ref('')
const messages = ref<ChatMessage[]>([{ role: 'assistant', text: DEFAULT_HINT, at: Date.now() }])

const selectedText = computed(() => selectionStore?.selectedText.value || props.selectedTextPinned || '')
const canSend = computed(() => props.modelValue.trim().length > 0)
const studyStageLabel = computed(() => props.studyStage?.trim() || '')
const wordRangeLabel = computed(() => {
  if (props.minWords && props.recommendedMaxWords && props.minWords !== props.recommendedMaxWords) {
    return `${props.minWords}-${props.recommendedMaxWords}词`
  }
  if (props.minWords) return `不少于${props.minWords}词`
  if (props.recommendedMaxWords) return `${props.recommendedMaxWords}词左右`
  return ''
})

const providerLabels: Record<WritingAiProvider, string> = {
  openai: 'OpenAI',
  kimi: 'Kimi',
  qwen: '千问',
}
const currentProviderLabel = computed(() => providerLabels[props.aiProvider] ?? props.aiProvider)

const stageLabels: Record<CoachStage, string> = {
  idle: '未开始',
  analyzing: '审题中',
  idea_confirmed: '已确认立意',
  outlined: '已有提纲',
  paragraphing: '分段陪写中',
  drafted: '已有完整草稿',
}
const stageLabel = computed(() => stageLabels[coachStage.value])

const writingFlowSteps: WritingFlowStep[] = [
  {
    key: 'analysis',
    title: '审题',
    brief: '题目主旨 / 必答点',
    description: '先判断题目到底要求写什么，明确中心任务、必答点和偏题风险。',
    goal: '看懂题意',
    actionLabel: '开始审题',
    prompt: '请先帮我审题：题目中心任务是什么？必须回答哪些点？有哪些偏题风险？',
    toolKey: 'analyze',
    coachStage: 'analyzing',
  },
  {
    key: 'outline',
    title: '提纲',
    brief: '段落结构',
    description: '把观点和素材组织成段落结构，明确每段服务哪个任务。',
    goal: '搭好结构',
    actionLabel: '搭提纲',
    prompt: '请根据题目、立场和素材，帮我搭一个切题的作文提纲。',
    toolKey: 'outline',
    coachStage: 'outlined',
  },
  {
    key: 'drafting',
    title: '下一段',
    brief: '分段陪写',
    description: '按当前题目、提纲和已有正文，判断下一段应该写什么。',
    goal: '推进正文',
    actionLabel: '写下一段',
    prompt: '请根据当前题目和提纲，陪我开始起草下一段，并说明这一段服务哪个要点。',
    toolKey: 'next',
    coachStage: 'paragraphing',
  },
  {
    key: 'revision',
    title: '偏题检查',
    brief: '漏点 / 跑题风险',
    description: '检查当前正文、提纲或段落是否真正服务题目中心任务。',
    goal: '先纠偏',
    actionLabel: '检查偏题',
    prompt: '请检查我的当前作文是否偏题，指出缺失要点和需要重构的地方。',
    toolKey: 'topic',
    coachStage: 'analyzing',
  },
  {
    key: 'polish',
    title: '润色',
    brief: '表达优化',
    description: '在不改变原意和题目方向的前提下，优化表达、衔接和句式。',
    goal: '提升表达',
    actionLabel: '润色表达',
    prompt: '请在不改变原意和题目方向的前提下，润色我选中的表达。',
    toolKey: 'polish',
    coachStage: 'paragraphing',
  },
  {
    key: 'final_check',
    title: '终稿',
    brief: '完整草稿',
    description: '生成或检查完整草稿，确认切题、字数、段落完整性和明显语言问题。',
    goal: '交付终稿',
    actionLabel: '生成终稿',
    prompt: '请基于题目要求、当前提纲和已有正文，生成一版完整终稿，并说明是否建议应用到正文。',
    toolKey: 'draft',
    coachStage: 'drafted',
  },
]

const coachTools: CoachTool[] = [
  {
    key: 'coach',
    label: '写作教练',
    icon: '✦',
    hint: '自由提问，按当前作文上下文回答',
    stage: 'idle',
    flowStage: 'analysis',
    prompt: '',
  },
  {
    key: 'analyze',
    label: '审题',
    icon: '题',
    hint: '拆中心任务、必答点和偏题风险',
    stage: 'analyzing',
    flowStage: 'analysis',
    prompt: '请先帮我审题：题目中心任务是什么？必须回答哪些点？有哪些偏题风险？',
  },
  {
    key: 'outline',
    label: '搭提纲',
    icon: '纲',
    hint: '生成开头、主体段和结尾结构',
    stage: 'outlined',
    flowStage: 'outline',
    prompt: '请根据题目和考试标准，帮我搭一个切题的作文提纲。',
  },
  {
    key: 'next',
    label: '写下一段',
    icon: '段',
    hint: '承接当前正文继续分段陪写',
    stage: 'paragraphing',
    flowStage: 'drafting',
    prompt: '请根据当前提纲和已有正文，陪我写下一段，并说明这一段服务哪个要点。',
  },
  {
    key: 'topic',
    label: '检查偏题',
    icon: '偏',
    hint: '对照题目检查缺失点和跑题风险',
    stage: 'analyzing',
    flowStage: 'revision',
    prompt: '请检查我的当前作文是否偏题，指出缺失要点和需要调整的地方。',
  },
  {
    key: 'polish',
    label: '润色表达',
    icon: '润',
    hint: '保持原意，提升表达和衔接',
    stage: 'paragraphing',
    flowStage: 'polish',
    prompt: '请在不改变原意和题目方向的前提下，润色我选中的表达。',
  },
  {
    key: 'draft',
    label: '生成终稿',
    icon: '稿',
    hint: '基于题目、提纲和正文生成完整草稿',
    stage: 'drafted',
    flowStage: 'final_check',
    prompt: '请根据题目、提纲和当前正文，生成一版完整终稿，并保留应用到正文前的确认。',
  },
]
const selectedTool = computed(() =>
  coachTools.find((item) => item.key === selectedToolKey.value) ?? coachTools[0]!,
)
const activeFlowIndex = computed(() => {
  const index = writingFlowSteps.findIndex((step) => step.key === activeFlowKey.value)
  return index >= 0 ? index : 0
})
const activeFlowStep = computed(() => writingFlowSteps[activeFlowIndex.value] ?? writingFlowSteps[0]!)
const previousFlowStep = computed(() =>
  activeFlowIndex.value > 0 ? writingFlowSteps[activeFlowIndex.value - 1] : null,
)
const nextFlowStepTitle = computed(() => writingFlowSteps[activeFlowIndex.value + 1]?.title ?? '完成终稿检查')
const flowSummary = computed(() => {
  if (activeFlowIndex.value === 0 && coachStage.value === 'idle') return '从审题开始，按阶段推进作文'
  return `当前在「${activeFlowStep.value.title}」，前面 ${activeFlowIndex.value} 步已完成`
})
const composerPlaceholder = computed(() => {
  if (selectedTool.value.key === 'coach') {
    return '有问题，尽管问。'
  }
  return selectedTool.value.prompt
})

const writingPlan = computed(() => {
  const prompt = props.taskPrompt?.trim()
  const centralTask = prompt
    ? `围绕题目要求完成写作，确保观点、理由和例子都服务于：${shorten(prompt, 90)}`
    : '先确认题目，再生成中心任务、必答点和推荐结构。'

  return {
    centralTask,
    centralTaskShort: prompt ? shorten(prompt, 32) : '等待题目',
    mustAnswerPoints: [
      '明确回应题目中的核心问题',
      '主体段提供理由或例子',
      '结尾回扣观点，避免新增无关信息',
    ],
    riskPoints: [
      '只写泛泛感受，没有回答题目问题',
      '例子和观点脱节',
      '为了高级表达牺牲清晰度和切题性',
    ],
    recommendedStructure: props.writingMode === 'exam'
      ? '开头表明观点；主体段分别展开理由和例子；结尾总结并回扣题目。'
      : '先确定目标读者和写作目的，再组织开头、主体和结尾。',
  }
})

const suggestionTypeLabel = computed(() => {
  if (!activeSuggestion.value) return ''
  if (activeSuggestion.value.type === 'replace_selection') return '替换选区'
  if (activeSuggestion.value.type === 'append_text') return '追加正文'
  return '替换全文'
})

watch(
  () => props.lastChatResult?.displayText ?? '',
  (value) => {
    if (!value || value === lastAssistantPayload.value) return
    lastAssistantPayload.value = value
    messages.value.push({ role: 'assistant', text: value, at: Date.now() })
    if (props.lastChatResult?.replaceText) {
      activeSuggestion.value = {
        type: props.selectedSpanPinned ? 'replace_selection' : 'append_text',
        text: props.lastChatResult.replaceText,
      }
    }
    scrollToBottom()
  },
)

watch(
  () => props.conversationId ?? '',
  (conversationId) => restoreMessages(conversationId),
  { immediate: true },
)

watch(
  messages,
  (list) => saveMessagesToStorage(props.conversationId, list),
  { deep: true },
)

function selectTool(item: CoachTool) {
  selectedToolKey.value = item.key
  coachStage.value = item.stage
  activeFlowKey.value = item.flowStage
  toolMenuOpen.value = false
  activeSuggestion.value = null
  if (item.prompt) {
    setComposerText(item.prompt)
  } else {
    focusComposer()
  }
  scrollToBottom()
}

function selectFlowStep(step: WritingFlowStep) {
  activeFlowKey.value = step.key
  selectedToolKey.value = step.toolKey
  coachStage.value = step.coachStage
  toolMenuOpen.value = false
  activeSuggestion.value = null
  setComposerText(step.prompt)
  scrollToBottom()
}

function flowStepPhase(step: WritingFlowStep): 'done' | 'current' | 'todo' {
  const index = writingFlowSteps.findIndex((item) => item.key === step.key)
  if (index < activeFlowIndex.value) return 'done'
  if (index === activeFlowIndex.value) return 'current'
  return 'todo'
}

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}

function onSend() {
  const text = props.modelValue.trim()
  if (!text) return
  messages.value.push({ role: 'user', text, at: Date.now() })
  toolMenuOpen.value = false
  emit('send')
  emit('update:modelValue', '')
  scrollToBottom()
}

function onSendOrStop() {
  if (props.isGenerating) {
    emit('stop')
    return
  }
  if (canSend.value) onSend()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) return
  event.preventDefault()
  if (!props.isGenerating && canSend.value) onSend()
}

function onApplySuggestion() {
  const suggestion = activeSuggestion.value
  if (!suggestion) return
  if (suggestion.type === 'replace_selection') {
    emit('replace-selection-with', suggestion.text)
  } else {
    emit('apply-suggestion', suggestion)
  }
  messages.value.push({ role: 'assistant', text: `已准备${suggestionTypeLabel.value}。`, at: Date.now() })
  activeSuggestion.value = null
}

function clearSelectedText() {
  selectionStore?.clear()
  emit('dismiss-selection')
}

function setComposerText(text: string) {
  emit('update:modelValue', text)
  focusComposer(text.length, text.length)
}

function focusComposer(start?: number, end?: number) {
  nextTick(() => {
    const element = composerInputRef.value
    if (!element) return
    element.focus()
    const cursorStart = start ?? element.value.length
    const cursorEnd = end ?? cursorStart
    element.setSelectionRange(cursorStart, cursorEnd)
  })
}

function getRecentMessages(max = 8): RecentMessageDto[] {
  const normalized = messages.value
    .filter((message) => {
      const text = message.text.trim()
      if (!text) return false
      if (message.role === 'assistant' && text === DEFAULT_HINT) return false
      return true
    })
    .map((message) => ({ role: message.role, content: message.text.trim() }))
  if (max <= 0) return normalized
  return normalized.slice(-max)
}

function isIncludeDraft(): boolean {
  return includeDraft.value
}

function getSelectedTool(): Pick<CoachTool, 'key' | 'label' | 'prompt'> {
  const tool = selectedTool.value
  return { key: tool.key, label: tool.label, prompt: tool.prompt }
}

function scrollToBottom() {
  nextTick(() => {
    if (!messageListRef.value) return
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  })
}

function defaultMessages(): ChatMessage[] {
  return [{ role: 'assistant', text: DEFAULT_HINT, at: Date.now() }]
}

function historyStorageKey(conversationId?: string): string | null {
  const id = conversationId?.trim()
  return id ? `${HISTORY_KEY_PREFIX}${id}` : null
}

function restoreMessages(conversationId?: string) {
  messages.value = loadMessagesFromStorage(conversationId) ?? defaultMessages()
  lastAssistantPayload.value = [...messages.value].reverse().find((message) => message.role === 'assistant')?.text ?? ''
  scrollToBottom()
}

function loadMessagesFromStorage(conversationId?: string): ChatMessage[] | null {
  const key = historyStorageKey(conversationId)
  if (!key) return null
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Array<{ role?: unknown; text?: unknown; at?: unknown }>
    if (!Array.isArray(parsed)) return null
    const restored = parsed
      .map((message) => {
        const role = message.role === 'user' ? 'user' : message.role === 'assistant' ? 'assistant' : null
        const text = typeof message.text === 'string' ? message.text.trim() : ''
        const at = typeof message.at === 'number' && Number.isFinite(message.at) ? message.at : Date.now()
        if (!role || !text) return null
        return { role, text, at } as ChatMessage
      })
      .filter((message): message is ChatMessage => Boolean(message))
    return restored.length ? restored : null
  } catch {
    return null
  }
}

function saveMessagesToStorage(conversationId: string | undefined, list: ChatMessage[]) {
  const key = historyStorageKey(conversationId)
  if (!key) return
  try {
    localStorage.setItem(key, JSON.stringify(list))
  } catch {}
}

function renderMarkdown(text: string): string {
  return renderAssistantMarkdown(text)
}

function onRenderedMarkdownClick(event: MouseEvent) {
  void copyMarkdownCodeFromClick(event)
}

function shorten(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max)}...` : text
}

defineExpose<{
  setComposerText: (text: string) => void
  focusComposer: () => void
  getRecentMessages: (max?: number) => RecentMessageDto[]
  isIncludeDraft: () => boolean
  getSelectedTool: () => Pick<CoachTool, 'key' | 'label' | 'prompt'>
}>({
  setComposerText,
  focusComposer: () => focusComposer(),
  getRecentMessages,
  isIncludeDraft,
  getSelectedTool,
})

onMounted(() => {
  restoreMessages(props.conversationId)
})
</script>

<style scoped>
.coach-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f8fafc;
}
.coach-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px var(--assistant-safe-padding-right, 16px) 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.coach-title-block {
  min-width: 0;
}
.coach-kicker {
  display: block;
  margin-bottom: 2px;
  font-size: 11px;
  font-weight: 700;
  color: #0f766e;
}
.coach-title {
  margin: 0;
  font-size: 16px;
  font-weight: 750;
  color: #111827;
}
.action-btn {
  height: 28px;
  border: 1px solid #d1d5db;
  border-radius: 7px;
  background: #fff;
  color: #374151;
  font-size: 12px;
  cursor: pointer;
}
.status-strip {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  padding: 10px var(--assistant-safe-padding-right, 16px) 8px 16px;
  background: #fff;
}
.status-chip {
  flex-shrink: 0;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 12px;
  color: #4b5563;
  background: #f9fafb;
}
.status-chip-strong {
  color: #065f46;
  background: #ecfdf5;
  border-color: #a7f3d0;
  font-weight: 700;
}
.flow-shell {
  flex-shrink: 0;
  margin: 10px var(--assistant-safe-padding-right, 16px) 0 16px;
  border: 1px solid #dbe8df;
  border-radius: 10px;
  background: #fbfefc;
  overflow: hidden;
}
.flow-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 12px 8px;
  color: #111827;
}
.flow-head strong,
.flow-head small {
  display: block;
}
.flow-head strong {
  font-size: 13px;
}
.flow-head small {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}
.flow-progress {
  flex-shrink: 0;
  border: 1px solid #bbf7d0;
  border-radius: 999px;
  background: #ecfdf5;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 750;
  color: #047857;
}
.flow-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 6px;
  padding: 4px 10px 12px;
}
.flow-step {
  min-width: 0;
  position: relative;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: flex-start;
  gap: 6px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  padding: 7px 5px;
  text-align: left;
  cursor: pointer;
}
.flow-step:hover,
.flow-step.current {
  border-color: #a7f3d0;
  background: #ecfdf5;
}
.flow-step.done .flow-node {
  border-color: #0f766e;
  background: #0f766e;
  color: #fff;
}
.flow-step.current .flow-node {
  border-color: #0f766e;
  background: #fff;
  color: #065f46;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.14);
}
.flow-node {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
}
.flow-copy {
  min-width: 0;
}
.flow-copy strong,
.flow-copy small {
  display: block;
}
.flow-copy strong {
  color: #111827;
  font-size: 12px;
  line-height: 1.2;
}
.flow-step.current .flow-copy strong {
  color: #065f46;
}
.flow-copy small {
  margin-top: 3px;
  color: #64748b;
  font-size: 10px;
  line-height: 1.25;
}
.current-stage-card {
  margin: 0 10px 10px;
  border: 1px solid #b7ead0;
  border-radius: 10px;
  background: #ecfdf5;
  padding: 12px;
}
.current-stage-kicker {
  display: block;
  margin-bottom: 3px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
}
.current-stage-copy h4 {
  margin: 0;
  color: #064e3b;
  font-size: 18px;
  font-weight: 800;
}
.current-stage-copy p {
  margin: 6px 0 10px;
  color: #115e45;
  font-size: 12px;
  line-height: 1.45;
}
.stage-insights {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.stage-insight {
  min-width: 0;
  border: 1px solid #d1fae5;
  border-radius: 8px;
  background: #fff;
  padding: 8px;
}
.stage-insight span,
.stage-insight strong {
  display: block;
}
.stage-insight span {
  color: #64748b;
  font-size: 10px;
  font-weight: 700;
}
.stage-insight strong {
  margin-top: 3px;
  overflow: hidden;
  color: #111827;
  font-size: 12px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.stage-primary,
.stage-secondary {
  height: 32px;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
}
.stage-primary {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #fff;
}
.stage-secondary {
  border: 1px solid #a7f3d0;
  background: #fff;
  color: #065f46;
}
.message-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding: 14px var(--assistant-safe-padding-right, 16px) 14px 16px;
}
.message-row {
  display: flex;
}
.message-row.user {
  justify-content: flex-end;
}
.message-row.assistant {
  justify-content: flex-start;
}
.message-bubble {
  max-width: 88%;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
  color: #111827;
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.message-row.assistant .message-bubble {
  width: 100%;
  max-width: 100%;
  white-space: normal;
}
.message-row.user .message-bubble {
  max-width: 72%;
  border-color: #0f766e;
  background: #0f766e;
  color: #fff;
}
.message-row.assistant .message-bubble :deep(p) {
  margin: 0 0 10px;
}
.message-row.assistant .message-bubble :deep(p:last-child) {
  margin-bottom: 0;
}
.message-row.assistant .message-bubble :deep(h1),
.message-row.assistant .message-bubble :deep(h2),
.message-row.assistant .message-bubble :deep(h3),
.message-row.assistant .message-bubble :deep(h4),
.message-row.assistant .message-bubble :deep(h5),
.message-row.assistant .message-bubble :deep(h6) {
  margin: 14px 0 8px;
  color: #0f172a;
  line-height: 1.25;
}
.message-row.assistant .message-bubble :deep(h1:first-child),
.message-row.assistant .message-bubble :deep(h2:first-child),
.message-row.assistant .message-bubble :deep(h3:first-child),
.message-row.assistant .message-bubble :deep(h4:first-child),
.message-row.assistant .message-bubble :deep(h5:first-child),
.message-row.assistant .message-bubble :deep(h6:first-child) {
  margin-top: 0;
}
.message-row.assistant .message-bubble :deep(h1) {
  font-size: 20px;
}
.message-row.assistant .message-bubble :deep(h2) {
  font-size: 18px;
}
.message-row.assistant .message-bubble :deep(h3) {
  font-size: 16px;
}
.message-row.assistant .message-bubble :deep(h4),
.message-row.assistant .message-bubble :deep(h5),
.message-row.assistant .message-bubble :deep(h6) {
  font-size: 15px;
}
.message-row.assistant .message-bubble :deep(ul),
.message-row.assistant .message-bubble :deep(ol) {
  margin: 8px 0 10px;
  padding-left: 22px;
}
.message-row.assistant .message-bubble :deep(li) {
  margin: 4px 0;
}
.message-row.assistant .message-bubble :deep(code) {
  border-radius: 5px;
  background: #f1f5f9;
  padding: 1px 5px;
  color: #0f172a;
  font-size: 0.92em;
}
.message-row.assistant .message-bubble :deep(.markdown-table-scroll) {
  max-width: 100%;
  margin: 10px 0;
  overflow-x: auto;
}
.message-row.assistant .message-bubble :deep(table) {
  width: 100%;
  min-width: 420px;
  border-collapse: collapse;
  font-size: 13px;
  white-space: normal;
}
.message-row.assistant .message-bubble :deep(th),
.message-row.assistant .message-bubble :deep(td) {
  border: 1px solid #cbd5e1;
  padding: 7px 8px;
  text-align: left;
  vertical-align: top;
}
.message-row.assistant .message-bubble :deep(th) {
  background: #f8fafc;
  color: #0f172a;
  font-weight: 800;
}
.message-row.assistant .message-bubble :deep(td:first-child),
.message-row.assistant .message-bubble :deep(th:first-child) {
  width: 116px;
  font-weight: 750;
}
.message-row.assistant .message-bubble :deep(.markdown-code-block) {
  margin: 12px 0;
  overflow: hidden;
  border: 1px solid #dbe7ef;
  border-radius: 12px;
  background: #f8fafc;
  color: #1e293b;
}
.message-row.assistant .message-bubble :deep(.markdown-code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid #e2e8f0;
  background: #eef6f4;
  padding: 8px 12px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}
.message-row.assistant .message-bubble :deep(.markdown-code-copy) {
  height: 26px;
  border: 1px solid #b7e4dc;
  border-radius: 999px;
  background: #ffffff;
  color: #0f766e;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
}
.message-row.assistant .message-bubble :deep(.markdown-code-copy:hover),
.message-row.assistant .message-bubble :deep(.markdown-code-copy--copied) {
  border-color: #0f766e;
  background: #ccfbf1;
}
.message-row.assistant .message-bubble :deep(.markdown-code-block pre) {
  margin: 0;
  overflow-x: auto;
  padding: 13px 14px;
  white-space: pre-wrap;
}
.message-row.assistant .message-bubble :deep(.markdown-code-block code) {
  background: transparent;
  padding: 0;
  color: inherit;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}
.suggestion-card {
  border: 1px solid #99f6e4;
  border-radius: 8px;
  background: #f0fdfa;
  padding: 12px;
}
.suggestion-meta,
.suggestion-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.suggestion-type {
  font-size: 12px;
  font-weight: 750;
  color: #0f766e;
}
.suggestion-state {
  font-size: 12px;
  color: #64748b;
}
.suggestion-text {
  margin: 8px 0 12px;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}
.ghost-btn,
.apply-btn {
  border-radius: 7px;
  padding: 7px 10px;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}
.ghost-btn {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #475569;
}
.apply-btn {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #fff;
}
.composer {
  flex-shrink: 0;
  padding: 10px var(--assistant-safe-padding-right, 16px) 12px 16px;
  border-top: 1px solid #e5e7eb;
  background: #fff;
}
.composer-shell {
  position: relative;
}
.draft-toggle {
  height: 30px;
  flex-shrink: 0;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #f9fafb;
  color: #6b7280;
  font-size: 12px;
  padding: 0 10px;
  cursor: pointer;
}
.draft-toggle.active {
  border-color: #5eead4;
  background: #ecfdf5;
  color: #047857;
}
.selected-line {
  position: absolute;
  top: 10px;
  left: 12px;
  right: 12px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.selected-label {
  flex-shrink: 0;
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
}
.composer-input {
  width: 100%;
  min-height: 118px;
  max-height: 190px;
  resize: vertical;
  border: 1px solid #d1d5db;
  border-radius: 14px;
  background: #fff;
  color: #111827;
  padding: 14px 14px 54px;
  font-size: 14px;
  line-height: 1.5;
  box-sizing: border-box;
}
.composer-input.has-selection {
  padding-top: 42px;
}
.composer-bottom {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.composer-toolbar {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.plus-btn {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #fff;
  color: #111827;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}
.plus-btn:hover,
.plus-btn[aria-expanded='true'] {
  border-color: #14b8a6;
  background: #f0fdfa;
  color: #0f766e;
}
.agent-chip {
  min-width: 0;
  height: 30px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid #99f6e4;
  border-radius: 999px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 12px;
  font-weight: 750;
  padding: 0 10px 0 7px;
  cursor: pointer;
}
.agent-chip span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-dot {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #0f766e, #2563eb);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
}
.tool-menu {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 48px;
  z-index: 20;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  max-height: 270px;
  overflow-y: auto;
  border: 1px solid #d1d5db;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
  padding: 10px;
}
.tool-menu-item {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 9px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #fff;
  padding: 9px;
  text-align: left;
  cursor: pointer;
}
.tool-menu-item:hover,
.tool-menu-item.active {
  border-color: #99f6e4;
  background: #f0fdfa;
}
.tool-icon {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 8px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}
.tool-copy {
  min-width: 0;
}
.tool-copy strong,
.tool-copy small {
  display: block;
}
.tool-copy strong {
  color: #111827;
  font-size: 13px;
  line-height: 1.2;
}
.tool-copy small {
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}
.provider-pill {
  height: 30px;
  margin-left: auto;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  padding: 0 10px;
  background: #fff;
  color: #111827;
  font-size: 12px;
  font-weight: 700;
}
.send-btn {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border: none;
  border-radius: 999px;
  background: #0f766e;
  color: #fff;
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
}
.send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.send-btn.generating {
  background: #111827;
}
@media (max-width: 1100px) {
  .flow-steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .stage-insights {
    grid-template-columns: 1fr;
  }
  .status-strip,
  .composer {
    padding-right: var(--assistant-safe-padding-right, 16px);
  }
  .tool-menu {
    grid-template-columns: 1fr;
  }
}
</style>
