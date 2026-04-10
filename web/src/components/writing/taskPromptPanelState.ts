import {
  buildExamResumePreview,
  buildVisualAttachmentPreview,
  normalizePromptSheet,
  type ExamPromptSheet,
  type VisualAttachmentPreview,
} from '../../pages/app/examPromptHelpers.ts'

const EMPTY_VISUAL_PREVIEW: VisualAttachmentPreview = {
  mode: 'none',
  title: null,
  text: null,
  imageUrl: null,
  comicScenes: [],
  chartSpec: null,
}

function normalizeTaskTypeLabel(taskType?: string | null) {
  const normalized = taskType?.trim()
  if (!normalized) return null
  if (/^task\s*1$/i.test(normalized) || normalized.toLowerCase() === 'task1') return 'Task 1'
  if (/^task\s*2$/i.test(normalized) || normalized.toLowerCase() === 'task2') return 'Task 2'
  return normalized
}

function resolveWordRange(minWords?: number | null, recommendedMaxWords?: number | null) {
  if (minWords && recommendedMaxWords) return `${minWords}-${recommendedMaxWords}`
  if (minWords) return `${minWords}`
  if (recommendedMaxWords) return `${recommendedMaxWords}`
  return null
}

function resolvePromptTypeLabel(sheet: ExamPromptSheet | null) {
  if (!sheet) return 'Expository'
  if (sheet.attachmentType === 'material') return 'Material-based Essay'
  if (sheet.attachmentType === 'visual') return 'Picture-based Essay'
  return 'Expository'
}

export function buildTaskPromptPanelState(input: {
  writingMode: 'free' | 'exam'
  taskPrompt: string
  attachmentImageUrl?: string | null
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
  maxScore?: number | null
  studyStage?: string | null
}) {
  const prompt = input.taskPrompt.trim()
  const taskTypeLabel = normalizeTaskTypeLabel(input.taskType)

  if (!prompt) {
    return {
      taskTypeLabel,
      promptTypeLabel: 'Expository',
      sheet: null,
      visualPreview: EMPTY_VISUAL_PREVIEW,
    }
  }

  if (input.writingMode === 'exam') {
    const restored = buildExamResumePreview({
      promptText: prompt,
      attachmentImageUrl: input.attachmentImageUrl ?? null,
      sourceType: 'manual',
      examType: input.studyStage ?? null,
      taskType: input.taskType ?? null,
      minWords: input.minWords ?? null,
      recommendedMaxWords: input.recommendedMaxWords ?? null,
      maxScore: input.maxScore ?? null,
    }, input.studyStage)

    if (restored) {
      return {
        taskTypeLabel: taskTypeLabel ?? normalizeTaskTypeLabel(restored.taskType),
        promptTypeLabel: resolvePromptTypeLabel(restored.sheet),
        sheet: restored.sheet,
        visualPreview: buildVisualAttachmentPreview(restored.sheet, restored.topicInfo),
      }
    }
  }

  const fallbackSheet = normalizePromptSheet({
    promptText: prompt,
    wordRange: resolveWordRange(input.minWords, input.recommendedMaxWords),
    maxScore: input.maxScore ?? null,
    sourceType: 'manual',
  })

  return {
    taskTypeLabel,
    promptTypeLabel: resolvePromptTypeLabel(fallbackSheet),
    sheet: fallbackSheet,
    visualPreview: EMPTY_VISUAL_PREVIEW,
  }
}
