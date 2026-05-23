<template>
  <div class="chatkit-coach-panel">
    <header class="chatkit-coach-header">
      <div>
        <span class="chatkit-kicker">Agent Builder</span>
        <h3>写作教练</h3>
      </div>
      <button type="button" class="context-sync-btn" @click="resetChatKit">同步上下文</button>
    </header>

    <section class="chatkit-context-strip" aria-label="写作上下文">
      <span>{{ writingMode === 'exam' ? '考试模式' : '自由写作' }}</span>
      <span v-if="studyStage">{{ studyStage }}</span>
      <span v-if="taskType">{{ taskType }}</span>
      <span v-if="wordRangeLabel">{{ wordRangeLabel }}</span>
    </section>

    <section class="chatkit-stage-bar" aria-label="写作教练快捷阶段">
      <button
        v-for="stage in stagePrompts"
        :key="stage.key"
        type="button"
        @click="sendStagePrompt(stage.prompt)"
      >
        <strong>{{ stage.label }}</strong>
        <small>{{ stage.hint }}</small>
      </button>
    </section>

    <div v-if="errorMessage" class="chatkit-error">
      {{ errorMessage }}
    </div>
    <div v-else-if="loading" class="chatkit-loading">
      正在连接写作教练...
    </div>

    <div ref="chatkitHostRef" class="chatkit-host" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  createWritingCoachChatKitSession,
  type WritingCoachChatKitContext,
} from '@/api/assistant'
import type { WritingAiProvider } from '@/api/writing'

type ChatKitElement = HTMLElement & {
  setOptions?: (options: Record<string, unknown>) => void
  sendUserMessage?: (message: { text: string }) => Promise<void>
}

type StagePrompt = {
  key: string
  label: string
  hint: string
  prompt: string
}

const CHATKIT_SCRIPT_SRC = 'https://cdn.platform.openai.com/deployments/chatkit/chatkit.js'

const props = defineProps<{
  modelValue: string
  selectedTextPinned: string
  selectedSpanPinned: { start: number; end: number } | null
  lastChatResult: { displayText: string; replaceText?: string } | null
  conversationId: string
  isGenerating: boolean
  writingMode: 'free' | 'exam'
  aiProvider: WritingAiProvider
  taskPrompt: string
  essay: string
  studyStage?: string | null
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
}>()

const emit = defineEmits<{
  'update:model-value': [value: string]
  send: []
  stop: []
  'dismiss-selection': []
  'replace-selection-with': [resultText: string]
  'apply-suggestion': [payload: { type: 'replace_selection' | 'append_text' | 'replace_all'; text: string }]
  cleared: []
  close: []
}>()

const chatkitHostRef = ref<HTMLElement | null>(null)
const loading = ref(true)
const errorMessage = ref('')
let chatkitElement: ChatKitElement | null = null
let initialClientSecret = ''

const stagePrompts: StagePrompt[] = [
  {
    key: 'analyze',
    label: '审题',
    hint: '中心任务 / 必答点',
    prompt: '请先帮我审题：题目中心任务是什么？必须回答哪些点？有哪些偏题风险？',
  },
  {
    key: 'outline',
    label: '提纲',
    hint: '段落结构',
    prompt: '请基于当前题目和学段，帮我搭一个切题的作文提纲。',
  },
  {
    key: 'next',
    label: '下一段',
    hint: '分段陪写',
    prompt: '请结合已有正文，告诉我下一段应该写什么，并给出可参考草稿。',
  },
  {
    key: 'topic',
    label: '偏题检查',
    hint: '漏点 / 跑题风险',
    prompt: '请检查当前作文是否偏题或漏答，并指出需要修正的地方。',
  },
  {
    key: 'polish',
    label: '润色',
    hint: '表达优化',
    prompt: '请在不改变原意和题目方向的前提下，优化我当前选区或正文的表达。',
  },
  {
    key: 'draft',
    label: '终稿',
    hint: '完整草稿',
    prompt: '请基于当前题目、学段和已有内容，生成一版完整草稿，并说明是否建议应用到正文。',
  },
]

const wordRangeLabel = computed(() => {
  if (props.minWords && props.recommendedMaxWords) return `${props.minWords}-${props.recommendedMaxWords} 词`
  if (props.minWords) return `不少于 ${props.minWords} 词`
  if (props.recommendedMaxWords) return `建议 ${props.recommendedMaxWords} 词内`
  return ''
})

function buildWritingContext(inputAsText = ''): WritingCoachChatKitContext {
  return {
    inputAsText,
    writingMode: props.writingMode,
    studyStage: props.studyStage ?? null,
    taskType: props.taskType ?? null,
    essayQuestion: props.taskPrompt || null,
    questionMaterials: null,
    essayGenre: null,
    minWords: props.minWords ?? null,
    maxWords: props.recommendedMaxWords ?? null,
    includeDraft: true,
    essayText: props.essay || null,
    selectedText: props.selectedTextPinned || null,
  }
}

async function ensureChatKitScript() {
  if (customElements.get('openai-chatkit')) return
  const existing = document.querySelector<HTMLScriptElement>(`script[src="${CHATKIT_SCRIPT_SRC}"]`)
  if (!existing) {
    const script = document.createElement('script')
    script.src = CHATKIT_SCRIPT_SRC
    script.async = true
    document.head.appendChild(script)
  }
  await customElements.whenDefined('openai-chatkit')
}

async function mountChatKit() {
  loading.value = true
  errorMessage.value = ''
  try {
    initialClientSecret = await requestClientSecret()
    await ensureChatKitScript()
    await nextTick()
    if (!chatkitHostRef.value) return

    chatkitHostRef.value.innerHTML = ''
    chatkitElement = document.createElement('openai-chatkit') as ChatKitElement
    chatkitHostRef.value.appendChild(chatkitElement)
    chatkitElement.setOptions?.({
      api: {
        async getClientSecret(currentClientSecret: string | null) {
          if (!currentClientSecret && initialClientSecret) {
            const secret = initialClientSecret
            initialClientSecret = ''
            return secret
          }
          return requestClientSecret()
        },
      },
      composer: {
        placeholder: '问写作教练：审题、提纲、下一段、偏题检查、润色...',
      },
      onClientTool: handleClientToolCall,
    })
  } catch (error) {
    errorMessage.value = resolveChatKitError(error)
  } finally {
    loading.value = false
  }
}

async function requestClientSecret() {
  const session = await createWritingCoachChatKitSession({
    conversationId: props.conversationId,
    writingContext: buildWritingContext(),
  })
  return session.clientSecret
}

function resolveChatKitError(error: unknown): string {
  const response = (error as { response?: { data?: { message?: string; code?: string } } })?.response
  const code = response?.data?.code
  const message = response?.data?.message
  if (code === '503030' || message?.includes('ChatKit workflow id')) {
    return 'ChatKit workflow id 未配置。请在 backend/.env 中设置 OPENAI_CHATKIT_WORKFLOW_ID 后重启后端。'
  }
  if (message) return message
  return error instanceof Error ? error.message : '写作教练连接失败'
}

async function handleClientToolCall(call: unknown) {
  const payload = call as {
    name?: string
    params?: Record<string, unknown>
    arguments?: Record<string, unknown>
    input?: Record<string, unknown>
  }
  const name = payload.name
  if (name === 'get_current_writing_context' || name === 'get_writing_context') {
    return buildWritingContext(String(payload.params?.inputAsText ?? payload.arguments?.inputAsText ?? payload.input?.inputAsText ?? ''))
  }
  if (name === 'safe_apply' || name === 'apply_to_editor') {
    const args = payload.params ?? payload.arguments ?? payload.input ?? {}
    const text = String(args.text ?? args.replacementText ?? '')
    const mode = String(args.mode ?? args.type ?? 'append_text')
    if (text.trim()) {
      emit('apply-suggestion', {
        type: mode === 'replace_all' || mode === 'replace_selection' ? mode : 'append_text',
        text,
      })
    }
    return { status: 'pending_user_confirmation' }
  }
  return { error: `Unsupported client tool: ${name || 'unknown'}` }
}

async function sendStagePrompt(prompt: string) {
  if (!chatkitElement?.sendUserMessage) {
    errorMessage.value = '写作教练尚未连接完成'
    return
  }
  await chatkitElement.sendUserMessage({
    text: [
      prompt,
      '',
      '[当前写作上下文]',
      JSON.stringify(buildWritingContext(prompt), null, 2),
    ].join('\n'),
  })
}

function resetChatKit() {
  mountChatKit()
}

function setComposerText(text: string) {
  emit('update:model-value', text)
}

function focusComposer() {
  chatkitElement?.focus()
}

function getRecentMessages() {
  return []
}

function isIncludeDraft() {
  return true
}

function getSelectedTool() {
  return { key: 'coach', label: '写作教练', prompt: '' }
}

onMounted(mountChatKit)
onBeforeUnmount(() => {
  chatkitElement = null
})

defineExpose({
  setComposerText,
  focusComposer,
  getRecentMessages,
  isIncludeDraft,
  getSelectedTool,
})
</script>

<style scoped>
.chatkit-coach-panel {
  display: flex;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  background: #f7faf9;
}

.chatkit-coach-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 10px;
}

.chatkit-kicker {
  display: block;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.chatkit-coach-header h3 {
  margin: 3px 0 0;
  color: #102a43;
  font-size: 20px;
  line-height: 1.2;
}

.context-sync-btn {
  border: 1px solid #b7e4d7;
  border-radius: 999px;
  background: #ffffff;
  color: #0f766e;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  padding: 7px 12px;
}

.chatkit-context-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 20px 12px;
}

.chatkit-context-strip span {
  border: 1px solid #d8ebe6;
  border-radius: 999px;
  background: #ffffff;
  color: #325c67;
  font-size: 12px;
  font-weight: 700;
  padding: 5px 9px;
}

.chatkit-stage-bar {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 0 20px 12px;
}

.chatkit-stage-bar button {
  border: 1px solid #cfe7df;
  border-radius: 8px;
  background: #ffffff;
  color: #17324d;
  cursor: pointer;
  min-height: 54px;
  padding: 8px 10px;
  text-align: left;
}

.chatkit-stage-bar strong {
  display: block;
  font-size: 14px;
}

.chatkit-stage-bar small {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-top: 2px;
}

.chatkit-host {
  flex: 1;
  min-height: 0;
  padding: 0 12px 12px;
}

.chatkit-host :deep(openai-chatkit) {
  display: block;
  height: 100%;
  min-height: 420px;
  border: 1px solid #d7e4e0;
  border-radius: 10px;
  overflow: hidden;
  background: #ffffff;
}

.chatkit-loading,
.chatkit-error {
  margin: 0 20px 12px;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
}

.chatkit-loading {
  background: #ecfdf5;
  color: #0f766e;
}

.chatkit-error {
  background: #fef2f2;
  color: #b42318;
}

@media (max-width: 900px) {
  .chatkit-stage-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
