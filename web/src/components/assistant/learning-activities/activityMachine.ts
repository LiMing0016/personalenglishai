import { assign, setup } from 'xstate'

import type { LearningActivityContext, LearningActivityEvent } from './contracts.ts'

function hasAnswer(answer: unknown) {
  if (Array.isArray(answer)) return answer.length > 0
  if (typeof answer === 'string') return answer.trim().length > 0
  return answer !== undefined && answer !== null
}

export const activityMachine = setup({
  types: {
    context: {} as LearningActivityContext,
    events: {} as LearningActivityEvent,
  },
  guards: {
    hasDraftAnswer: ({ context }) => hasAnswer(context.draftAnswer),
    hasNextQuestion: ({ context }) => {
      const itemCount = context.block?.data.items.length ?? 0
      return context.questionIndex + 1 < itemCount
    },
  },
  actions: {
    startActivity: assign(({ event }) => {
      if (event.type !== 'START') return {}
      return {
        activityId: event.block.data.activityId,
        block: event.block,
        questionIndex: 0,
        draftAnswer: undefined,
        result: undefined,
        error: undefined,
      }
    }),
    updateAnswer: assign(({ event }) =>
      event.type === 'ANSWER_CHANGE' ? { draftAnswer: event.answer } : {},
    ),
    recordResult: assign(({ event }) =>
      event.type === 'SUBMIT_SUCCESS'
        ? { result: event.result, error: undefined }
        : {},
    ),
    recordError: assign(({ event }) =>
      event.type === 'SUBMIT_ERROR'
        ? { error: event.error }
        : {},
    ),
    advanceQuestion: assign(({ context }) => ({
      questionIndex: context.questionIndex + 1,
      draftAnswer: undefined,
      result: undefined,
      error: undefined,
    })),
    resetAttempt: assign({
      draftAnswer: undefined,
      result: undefined,
      error: undefined,
    }),
  },
}).createMachine({
  id: 'learningActivity',
  initial: 'idle',
  context: {
    activityId: '',
    questionIndex: 0,
  },
  on: {
    EXIT: { target: '.cancelled' },
  },
  states: {
    idle: {
      on: {
        START: {
          target: 'awaitingAnswer',
          actions: 'startActivity',
        },
      },
    },
    awaitingAnswer: {
      on: {
        ANSWER_CHANGE: { actions: 'updateAnswer' },
        SUBMIT: {
          guard: 'hasDraftAnswer',
          target: 'submitting',
        },
      },
    },
    submitting: {
      on: {
        SUBMIT_SUCCESS: {
          target: 'reviewing',
          actions: 'recordResult',
        },
        SUBMIT_ERROR: {
          target: 'error',
          actions: 'recordError',
        },
      },
    },
    reviewing: {
      on: {
        NEXT: [
          {
            guard: 'hasNextQuestion',
            target: 'awaitingAnswer',
            actions: 'advanceQuestion',
          },
          { target: 'completed' },
        ],
        RETRY: {
          target: 'awaitingAnswer',
          actions: 'resetAttempt',
        },
      },
    },
    error: {
      on: {
        RETRY: {
          target: 'awaitingAnswer',
          actions: 'resetAttempt',
        },
      },
    },
    completed: { type: 'final' },
    cancelled: { type: 'final' },
  },
})
