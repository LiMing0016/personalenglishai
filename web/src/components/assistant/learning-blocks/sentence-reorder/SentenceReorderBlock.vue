<template>
  <article class="sentence-reorder-card" aria-labelledby="sentence-reorder-title">
    <header class="card-header">
      <div>
        <p class="card-kicker">重组成句</p>
        <h3 id="sentence-reorder-title">{{ block.title || currentItem?.instruction }}</h3>
      </div>
      <span v-if="currentItem" class="question-progress">
        {{ snapshot.context.questionIndex + 1 }} / {{ block.data.items.length }}
      </span>
    </header>

    <template v-if="currentItem && !isTerminal">
      <p v-if="currentItem.translation" class="translation">{{ currentItem.translation }}</p>

      <section class="answer-area" aria-label="当前答案">
        <p class="area-label">你的句子</p>
        <div class="token-row token-row--answer">
          <button
            v-for="token in answerTokens"
            :key="token.id"
            type="button"
            class="token-button token-button--selected"
            :disabled="isLocked"
            :aria-label="`移除 ${token.text}`"
            @click="removeToken(token.id)"
          >
            {{ token.text }}
          </button>
          <span v-if="answerTokens.length === 0" class="answer-placeholder">依次选择下方单词</span>
        </div>
      </section>

      <section class="token-bank" aria-label="可选单词">
        <p class="area-label">单词</p>
        <div class="token-row">
          <button
            v-for="token in availableTokens"
            :key="token.id"
            type="button"
            class="token-button"
            :disabled="isLocked"
            :aria-label="`添加 ${token.text}`"
            @click="addToken(token.id)"
          >
            {{ token.text }}
          </button>
        </div>
      </section>

      <p v-if="showHint && currentItem.hint" class="hint-box">提示：{{ currentItem.hint }}</p>

      <div
        v-if="snapshot.value === 'reviewing' || snapshot.value === 'error'"
        class="feedback"
        :class="{ 'feedback--correct': result?.correct, 'feedback--incorrect': result && !result.correct }"
        role="status"
        aria-live="polite"
      >
        <template v-if="result">
          <strong>{{ result.correct ? '回答正确' : '再想一想' }}</strong>
          <p v-if="!result.correct">正确句子：{{ expectedSentence }}</p>
          <p v-if="currentItem.explanation">{{ currentItem.explanation }}</p>
        </template>
        <template v-else>
          <strong>暂时无法判分</strong>
          <p>{{ snapshot.context.error?.message }}</p>
        </template>
      </div>

      <footer class="card-actions">
        <button
          v-if="currentItem.hint && snapshot.value === 'awaitingAnswer'"
          type="button"
          class="secondary-action"
          @click="requestHint"
        >
          提示
        </button>
        <button
          v-if="snapshot.value === 'awaitingAnswer'"
          type="button"
          class="primary-action"
          :disabled="answerTokens.length !== currentItem.tokens.length"
          @click="submitAnswer"
        >
          检查答案
        </button>
        <button
          v-if="snapshot.value === 'reviewing' && !result?.correct"
          type="button"
          class="secondary-action"
          @click="retryQuestion"
        >
          重试
        </button>
        <button
          v-if="snapshot.value === 'reviewing'"
          type="button"
          class="primary-action"
          @click="nextQuestion"
        >
          {{ isLastQuestion ? '完成练习' : '下一题' }}
        </button>
        <button
          v-if="snapshot.value === 'error'"
          type="button"
          class="secondary-action"
          @click="retryQuestion"
        >
          重新尝试
        </button>
        <button type="button" class="exit-action" @click="send({ type: 'EXIT' })">结束练习</button>
      </footer>
    </template>

    <div v-else class="terminal-message" role="status" aria-live="polite">
      <strong>{{ snapshot.value === 'completed' ? '练习完成' : '练习已结束' }}</strong>
      <p>{{ snapshot.value === 'completed' ? '你已经完成本组重组成句。' : '可以随时开始新的练习。' }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { useMachine } from '@xstate/vue'
import { computed, onMounted, ref } from 'vue'

import { activityMachine } from '../../learning-activities/activityMachine.ts'
import type { SentenceReorderBlock, SentenceReorderToken } from '../contracts.ts'
import { gradeSentenceReorder } from './grader.ts'

const props = defineProps<{
  block: SentenceReorderBlock
}>()

const { snapshot, send } = useMachine(activityMachine)
const showHint = ref(false)

const currentItem = computed(() => props.block.data.items[snapshot.value.context.questionIndex])
const answerIds = computed<string[]>(() => {
  const answer = snapshot.value.context.draftAnswer
  return Array.isArray(answer) && answer.every((id) => typeof id === 'string') ? answer : []
})
const tokenById = computed(() => new Map(currentItem.value?.tokens.map((token) => [token.id, token]) ?? []))
const answerTokens = computed<SentenceReorderToken[]>(() =>
  answerIds.value.flatMap((id) => {
    const token = tokenById.value.get(id)
    return token ? [token] : []
  }),
)
const availableTokens = computed<SentenceReorderToken[]>(() => {
  const selected = new Set(answerIds.value)
  return (currentItem.value?.initialOrder ?? []).flatMap((id) => {
    const token = tokenById.value.get(id)
    return token && !selected.has(id) ? [token] : []
  })
})
const result = computed(() => snapshot.value.context.result)
const isLocked = computed(() => snapshot.value.value !== 'awaitingAnswer')
const isTerminal = computed(() => ['completed', 'cancelled'].includes(String(snapshot.value.value)))
const isLastQuestion = computed(
  () => snapshot.value.context.questionIndex + 1 >= props.block.data.items.length,
)
const expectedSentence = computed(() => sentenceFromIds(result.value?.expected))

onMounted(() => {
  send({ type: 'START', block: props.block })
})

function sentenceFromIds(value: unknown) {
  if (!Array.isArray(value)) return ''
  return value
    .map((id) => (typeof id === 'string' ? tokenById.value.get(id)?.text : undefined))
    .filter((text): text is string => Boolean(text))
    .join(' ')
}

function addToken(tokenId: string) {
  if (isLocked.value) return
  send({ type: 'ANSWER_CHANGE', answer: [...answerIds.value, tokenId] })
}

function removeToken(tokenId: string) {
  if (isLocked.value) return
  send({ type: 'ANSWER_CHANGE', answer: answerIds.value.filter((id) => id !== tokenId) })
}

function requestHint() {
  showHint.value = true
  send({ type: 'REQUEST_HINT' })
}

function submitAnswer() {
  const item = currentItem.value
  if (!item || answerIds.value.length !== item.tokens.length) return
  send({ type: 'SUBMIT' })
  try {
    const grade = gradeSentenceReorder(answerIds.value, item.acceptedOrders)
    send({ type: 'SUBMIT_SUCCESS', result: grade })
  } catch (error) {
    send({
      type: 'SUBMIT_ERROR',
      error: {
        code: 'GRADING_FAILED',
        message: error instanceof Error ? error.message : '判分失败，请重试。',
      },
    })
  }
}

function retryQuestion() {
  showHint.value = false
  send({ type: 'RETRY' })
}

function nextQuestion() {
  showHint.value = false
  send({ type: 'NEXT' })
}
</script>

<style scoped>
.sentence-reorder-card {
  border: 1px solid #dbe3ea;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
  padding: 18px;
  color: #0f172a;
}

.card-header,
.card-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.card-kicker,
.area-label {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 850;
}

.card-header h3 {
  margin: 4px 0 0;
  font-size: 18px;
  line-height: 1.4;
}

.question-progress {
  flex-shrink: 0;
  border-radius: 999px;
  background: #f1f5f9;
  padding: 5px 9px;
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.translation {
  margin: 12px 0 0;
  color: #475569;
  line-height: 1.6;
}

.answer-area,
.token-bank {
  margin-top: 16px;
}

.token-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.token-row--answer {
  min-height: 58px;
  align-content: flex-start;
  border: 1px dashed #94a3b8;
  border-radius: 10px;
  background: #f8fafc;
  padding: 8px;
}

.token-button,
.primary-action,
.secondary-action,
.exit-action {
  min-height: 40px;
  border-radius: 9px;
  padding: 8px 12px;
  font: inherit;
  font-weight: 750;
  cursor: pointer;
}

.token-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #1e293b;
}

.token-button--selected {
  border-color: #5eead4;
  background: #f0fdfa;
}

.token-button:hover:not(:disabled),
.token-button:focus-visible,
.secondary-action:hover,
.secondary-action:focus-visible {
  border-color: #14b8a6;
  outline: 2px solid rgba(20, 184, 166, 0.2);
  outline-offset: 2px;
}

.answer-placeholder {
  align-self: center;
  color: #94a3b8;
  font-size: 13px;
}

.hint-box,
.feedback,
.terminal-message {
  margin: 14px 0 0;
  border-radius: 10px;
  padding: 12px 14px;
  line-height: 1.55;
}

.hint-box {
  background: #fffbeb;
  color: #92400e;
}

.feedback {
  background: #f8fafc;
  color: #334155;
}

.feedback--correct {
  background: #ecfdf5;
  color: #047857;
}

.feedback--incorrect {
  background: #fff7ed;
  color: #9a3412;
}

.feedback p,
.terminal-message p {
  margin: 4px 0 0;
}

.card-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-top: 16px;
}

.primary-action {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #ffffff;
}

.secondary-action,
.exit-action {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.exit-action {
  margin-right: auto;
  color: #64748b;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

@media (max-width: 390px) {
  .sentence-reorder-card {
    padding: 14px;
  }

  .card-actions > button {
    flex: 1 1 auto;
  }
}
</style>
