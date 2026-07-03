<template>
  <aside class="context-assistant-panel" aria-label="AI 上下文助手">
    <header>
      <div>
        <strong>AI 上下文助手</strong>
        <span>{{ activeModeLabel }}</span>
      </div>
      <button type="button" aria-label="收起助手" @click="emit('collapse')">×</button>
    </header>

    <nav class="context-assistant-panel__tabs" aria-label="助手视图">
      <button
        v-for="tab in tabs"
        :key="tab"
        type="button"
        :class="{ active: tab === activeTab }"
        @click="activeTab = tab">
        {{ tab }}
      </button>
    </nav>

    <section
      v-if="noteComposerActive"
      class="study-note-panel study-note-panel--composer-active"
      aria-label="锚点笔记编辑">
      <header class="study-note-panel__header">
        <div>
          <span>锚点笔记</span>
          <strong>{{ noteContextLabel }}</strong>
        </div>
        <button type="button" @click="emit('cancelNote')">取消</button>
      </header>

      <blockquote v-if="noteSelectedText" class="study-note-selected-text">
        {{ noteSelectedText }}
      </blockquote>

      <input
        :value="noteTitle"
        type="text"
        placeholder="笔记标题"
        aria-label="笔记标题"
        @input="emit('update:noteTitle', ($event.target as HTMLInputElement).value)"
      />
      <textarea
        ref="noteContentInputRef"
        :value="noteContent"
        rows="7"
        placeholder="围绕这个选区写下理解、疑问或总结。"
        aria-label="笔记内容"
        @input="emit('update:noteContent', ($event.target as HTMLTextAreaElement).value)"
      />

      <div class="note-agent-compose" aria-label="Agent 辅助补充笔记">
        <textarea
          :value="noteAgentPrompt"
          rows="2"
          placeholder="边问边补：例如这里为什么能推出这个结论？"
          aria-label="问 Agent 并追加到当前笔记"
          @input="emit('update:noteAgentPrompt', ($event.target as HTMLTextAreaElement).value)"
        />
        <div class="note-agent-compose__actions">
          <span>{{ noteAgentLoading ? 'Agent 正在补充...' : '回答会先进入候选区' }}</span>
          <button
            type="button"
            :disabled="noteAgentLoading || !noteAgentPrompt.trim()"
            @click="emit('askNoteAgent', noteAgentPrompt)">
            问 AI 生成候选
          </button>
        </div>
      </div>

      <section class="ai-candidate-card" aria-label="AI 候选补充">
        <span>AI 候选补充</span>
        <blockquote>{{ aiCandidateContent || 'Agent 的补充会先出现在这里，确认后再追加到笔记。' }}</blockquote>
        <button type="button" :disabled="!aiCandidateContent" @click="emit('appendAiCandidate')">
          追加到笔记
        </button>
      </section>

      <div class="study-note-panel__actions">
        <button type="button" @click="emit('cancelNote')">取消</button>
        <button type="button" class="context-assistant-panel__primary" @click="emit('saveNote')">保存笔记</button>
      </div>
    </section>

    <section class="context-assistant-panel__context" aria-label="上下文">
      <span>{{ contextTitle }}</span>
      <blockquote>{{ contextText || '选择 PDF 选区、笔记或知识卡后，这里会显示上下文。' }}</blockquote>
    </section>

    <section class="context-assistant-panel__quick" aria-label="快捷操作">
      <button
        v-for="action in quickActions"
        :key="action"
        type="button"
        @click="emit('quickAction', action)">
        {{ action }}
      </button>
    </section>

    <section class="context-assistant-panel__messages" aria-label="对话记录">
      <article v-for="message in messages" :key="message.id" :class="`message--${message.role}`">
        <strong>{{ message.role === 'assistant' ? 'AI' : '我' }}</strong>
        <p>{{ message.content }}</p>
        <button
          v-if="message.role === 'assistant' && noteComposerActive"
          type="button"
          class="message-append-note"
          @click="emit('appendAgentAnswer', message.content)">
          追加到当前笔记
        </button>
        <div v-if="message.citations?.length" class="context-assistant-panel__citations" aria-label="引用来源">
          <button
            v-for="citation in message.citations"
            :key="`${citation.chunkId}-${citation.elementId || citation.pageNumber}`"
            type="button"
            @click="emit('openCitation', citation)">
            引用 Page {{ citation.pageNumber || '?' }} · {{ citation.elementId || citation.chunkId }}
          </button>
        </div>
      </article>
    </section>

    <form class="context-assistant-panel__composer" @submit.prevent="emit('submit')">
      <textarea
        :value="prompt"
        rows="3"
        placeholder="向我提问，支持 @ 知识点、# 标签和当前 PDF 选区"
        @input="emit('update:prompt', ($event.target as HTMLTextAreaElement).value)"
      />
      <button type="submit" :disabled="loading">{{ loading ? '检索中...' : '发送' }}</button>
    </form>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import type {
  LearningAssistantCitation,
  LearningAssistantMessage,
} from '../../types/learningIde'

withDefaults(defineProps<{
  activeModeLabel: string
  contextTitle: string
  contextText: string
  messages: LearningAssistantMessage[]
  prompt: string
  loading?: boolean
  noteComposerActive?: boolean
  noteTitle?: string
  noteContent?: string
  noteSelectedText?: string
  noteContextLabel?: string
  noteAgentPrompt?: string
  noteAgentLoading?: boolean
  aiCandidateContent?: string
}>(), {
  loading: false,
  noteComposerActive: false,
  noteTitle: '',
  noteContent: '',
  noteSelectedText: '',
  noteContextLabel: '',
  noteAgentPrompt: '',
  noteAgentLoading: false,
  aiCandidateContent: '',
})

const emit = defineEmits<{
  collapse: []
  quickAction: [prompt: string]
  submit: []
  'update:prompt': [value: string]
  'update:noteTitle': [value: string]
  'update:noteContent': [value: string]
  'update:noteAgentPrompt': [value: string]
  openCitation: [citation: LearningAssistantCitation]
  saveNote: []
  cancelNote: []
  askNoteAgent: [prompt: string]
  appendAiCandidate: []
  appendAgentAnswer: [content: string]
}>()

const tabs = ['讲解', '笔记建议', '生成练习', '模块推荐']
const activeTab = ref('讲解')
const quickActions = ['解释选区', '加入知识卡', '生成练习', '整理笔记']
</script>

<style scoped>
.context-assistant-panel {
  display: grid;
  grid-template-rows: auto auto auto auto minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 0;
  border-left: 1px solid #d9e2ec;
  background: #ffffff;
  color: #102033;
}

.context-assistant-panel header,
.context-assistant-panel__tabs,
.context-assistant-panel__context,
.context-assistant-panel__quick,
.context-assistant-panel__composer {
  border-bottom: 1px solid #e5edf4;
}

.context-assistant-panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
}

.context-assistant-panel header div,
.context-assistant-panel__context {
  display: grid;
  gap: 4px;
}

.context-assistant-panel span,
.context-assistant-panel__context span {
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.context-assistant-panel button {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
  color: #102033;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.context-assistant-panel header button {
  width: 32px;
  min-height: 32px;
}

.context-assistant-panel__tabs,
.context-assistant-panel__quick {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 10px 14px;
}

.context-assistant-panel__tabs button,
.context-assistant-panel__quick button {
  min-height: 32px;
  white-space: nowrap;
}

.context-assistant-panel__tabs button.active {
  border-color: rgba(15, 143, 137, 0.35);
  background: #eef7f6;
  color: #0f8f89;
}

.context-assistant-panel__context,
.study-note-panel {
  padding: 12px 14px;
}

.study-note-panel {
  display: grid;
  gap: 10px;
  border-bottom: 1px solid #e5edf4;
  background: #fbfdff;
}

.study-note-panel__header,
.study-note-panel__actions,
.note-agent-compose__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.study-note-panel__header div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.study-note-selected-text,
.ai-candidate-card blockquote {
  margin: 0;
  border-left: 3px solid #0f8f89;
  padding-left: 10px;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.study-note-panel input,
.study-note-panel textarea {
  width: 100%;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  padding: 9px 10px;
  color: #102033;
  font: inherit;
  box-sizing: border-box;
}

.study-note-panel textarea {
  resize: vertical;
}

.note-agent-compose,
.ai-candidate-card {
  display: grid;
  gap: 8px;
}

.ai-candidate-card {
  padding: 10px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
}

.context-assistant-panel__primary {
  border-color: #0f8f89 !important;
  background: #0f8f89 !important;
  color: #ffffff !important;
}

.context-assistant-panel__context blockquote {
  max-height: 96px;
  margin: 0;
  overflow: auto;
  border-left: 3px solid #0f8f89;
  padding-left: 10px;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.context-assistant-panel__messages {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 0;
  overflow: auto;
  padding: 12px 14px;
}

.context-assistant-panel__messages article {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
}

.context-assistant-panel__messages p {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.45;
}

.context-assistant-panel__citations {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.context-assistant-panel__citations button {
  min-height: 28px;
  border-color: #cfe4f2;
  background: #eef7ff;
  color: #2563eb;
  font-size: 12px;
}

.context-assistant-panel__composer {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
}

.context-assistant-panel__composer textarea {
  resize: vertical;
  min-height: 72px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  padding: 9px 10px;
  color: #102033;
  font: inherit;
}

.context-assistant-panel__composer button {
  min-height: 36px;
  border-color: #0f8f89;
  background: #0f8f89;
  color: #ffffff;
}
</style>
