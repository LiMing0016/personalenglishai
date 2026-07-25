export interface InteractiveLearningBlock<TItem = unknown> {
  id: string
  type: string
  version: number
  fallbackMarkdown: string
  data: {
    activityId: string
    items: TItem[]
  }
}

export interface ActivityResult {
  correct: boolean
  answer: unknown
  expected: unknown
  message?: string
}

export interface ActivityError {
  code: string
  message: string
}

export interface LearningActivityContext {
  activityId: string
  block?: InteractiveLearningBlock
  questionIndex: number
  draftAnswer?: unknown
  result?: ActivityResult
  error?: ActivityError
}

export type LearningActivityEvent =
  | { type: 'START'; block: InteractiveLearningBlock }
  | { type: 'ANSWER_CHANGE'; answer: unknown }
  | { type: 'REQUEST_HINT' }
  | { type: 'SUBMIT' }
  | { type: 'SUBMIT_SUCCESS'; result: ActivityResult }
  | { type: 'SUBMIT_ERROR'; error: ActivityError }
  | { type: 'NEXT' }
  | { type: 'RETRY' }
  | { type: 'EXIT' }

