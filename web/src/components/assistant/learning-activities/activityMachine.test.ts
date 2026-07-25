import test from 'node:test'
import assert from 'node:assert/strict'
import { createActor } from 'xstate'

import { activityMachine } from './activityMachine.ts'
import type { InteractiveLearningBlock } from './contracts.ts'

function block(itemCount = 2): InteractiveLearningBlock {
  return {
    id: 'block-1',
    type: 'test_activity',
    version: 1,
    fallbackMarkdown: '练习暂不可用',
    data: {
      activityId: 'activity-1',
      items: Array.from({ length: itemCount }, (_, index) => ({ id: `item-${index + 1}` })),
    },
  }
}

function startActor(itemCount = 2) {
  const actor = createActor(activityMachine)
  actor.start()
  actor.send({ type: 'START', block: block(itemCount) })
  return actor
}

test('moves through answer, submit, review, and next question', () => {
  const actor = startActor(2)

  assert.equal(actor.getSnapshot().value, 'awaitingAnswer')
  actor.send({ type: 'ANSWER_CHANGE', answer: ['token-1'] })
  assert.deepEqual(actor.getSnapshot().context.draftAnswer, ['token-1'])

  actor.send({ type: 'SUBMIT' })
  assert.equal(actor.getSnapshot().value, 'submitting')
  actor.send({ type: 'SUBMIT' })
  assert.equal(actor.getSnapshot().value, 'submitting')

  actor.send({
    type: 'SUBMIT_SUCCESS',
    result: { correct: true, answer: ['token-1'], expected: ['token-1'] },
  })
  assert.equal(actor.getSnapshot().value, 'reviewing')

  actor.send({ type: 'NEXT' })
  const next = actor.getSnapshot()
  assert.equal(next.value, 'awaitingAnswer')
  assert.equal(next.context.questionIndex, 1)
  assert.equal(next.context.draftAnswer, undefined)
  assert.equal(next.context.result, undefined)
})

test('completes after NEXT on the final question', () => {
  const actor = startActor(1)
  actor.send({ type: 'ANSWER_CHANGE', answer: ['token-1'] })
  actor.send({ type: 'SUBMIT' })
  actor.send({
    type: 'SUBMIT_SUCCESS',
    result: { correct: true, answer: ['token-1'], expected: ['token-1'] },
  })
  actor.send({ type: 'NEXT' })

  assert.equal(actor.getSnapshot().value, 'completed')
  actor.send({ type: 'ANSWER_CHANGE', answer: ['late-answer'] })
  assert.deepEqual(actor.getSnapshot().context.draftAnswer, ['token-1'])
})

test('retries after grading errors and clears the error', () => {
  const actor = startActor()
  actor.send({ type: 'ANSWER_CHANGE', answer: ['token-1'] })
  actor.send({ type: 'SUBMIT' })
  actor.send({
    type: 'SUBMIT_ERROR',
    error: { code: 'GRADING_FAILED', message: '判分失败' },
  })

  assert.equal(actor.getSnapshot().value, 'error')
  assert.equal(actor.getSnapshot().context.error?.code, 'GRADING_FAILED')

  actor.send({ type: 'RETRY' })
  assert.equal(actor.getSnapshot().value, 'awaitingAnswer')
  assert.equal(actor.getSnapshot().context.error, undefined)
  assert.equal(actor.getSnapshot().context.draftAnswer, undefined)
})

test('allows retrying a reviewed answer without advancing', () => {
  const actor = startActor()
  actor.send({ type: 'ANSWER_CHANGE', answer: ['wrong'] })
  actor.send({ type: 'SUBMIT' })
  actor.send({
    type: 'SUBMIT_SUCCESS',
    result: { correct: false, answer: ['wrong'], expected: ['right'] },
  })
  actor.send({ type: 'RETRY' })

  assert.equal(actor.getSnapshot().value, 'awaitingAnswer')
  assert.equal(actor.getSnapshot().context.questionIndex, 0)
  assert.equal(actor.getSnapshot().context.draftAnswer, undefined)
  assert.equal(actor.getSnapshot().context.result, undefined)
})

test('can exit from active states and ignores later activity events', () => {
  const actor = startActor()
  actor.send({ type: 'EXIT' })

  assert.equal(actor.getSnapshot().value, 'cancelled')
  actor.send({ type: 'ANSWER_CHANGE', answer: ['late-answer'] })
  actor.send({ type: 'RETRY' })
  assert.equal(actor.getSnapshot().value, 'cancelled')
  assert.equal(actor.getSnapshot().context.draftAnswer, undefined)
})

test('ignores submit until the learner has supplied an answer', () => {
  const actor = startActor()
  actor.send({ type: 'SUBMIT' })

  assert.equal(actor.getSnapshot().value, 'awaitingAnswer')
})
