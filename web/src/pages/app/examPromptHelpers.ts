export type ExamPromptType = 'general' | 'material' | 'chart' | 'comic'
export type ExamPromptAttachmentType = 'none' | 'material' | 'visual'
export type ExamPromptVisualKind = 'image' | 'comic' | 'chart' | 'table'
export type ExamTaskSelectionValue = 'task1' | 'task2'

export interface ExamTaskSelectionOption {
  value: ExamTaskSelectionValue
  label: string
}

export interface ExamChartSpec {
  title?: string | null
  displayType?: string | null
  columns: string[]
  rows: string[][]
  summary?: string | null
}

export interface ExamChartPreviewPoint {
  x: number
  y: number
  label: string
  value: string
}

export interface ExamChartPreviewSeries {
  name: string
  color: string
  kind: 'bar' | 'line'
  axis: 'left' | 'right'
  points: ExamChartPreviewPoint[]
  polyline: string
}

export interface ExamChartPreviewFigure {
  labels: string[]
  series: ExamChartPreviewSeries[]
  leftAxisLabel: string | null
  rightAxisLabel: string | null
  isComboChart: boolean
}

export interface ExamComicScene {
  title?: string | null
  description: string
  dialogue?: string | null
}

export interface ExamTopicInfo {
  paper?: string | null
  promptSheetId?: number | null
  topic: string
  genre: string | null
  wordRange: string | null
  requirements: string | null
  imageDescription: string | null
  materialText: string | null
  attachmentImageUrl?: string | null
  maxScore: number
  sourceType: 'manual' | 'past_prompt' | 'ai_generated' | 'free_input'
  examType: string | null
  taskType: string | null
  minWords: number | null
  recommendedMaxWords: number | null
  promptType?: ExamPromptType | null
  chartSpec?: ExamChartSpec | null
  comicScenes?: ExamComicScene[] | null
}

export interface ExamPromptSheet {
  part: string
  questionNo: string | null
  directions: string
  promptText: string
  requirements: string[]
  wordRange: string | null
  score: number | null
  attachmentType: ExamPromptAttachmentType
  attachmentTitle: string | null
  attachmentContent: string | null
  attachmentImageUrl: string | null
  visualKind: ExamPromptVisualKind | null
  sourceType: ExamTopicInfo['sourceType']
}

export interface VisualAttachmentPreview {
  mode: 'none' | 'image' | 'comic' | 'chart' | 'table' | 'text'
  title: string | null
  text: string | null
  imageUrl: string | null
  comicScenes: ExamComicScene[]
  chartSpec: ExamChartSpec | null
}

export interface PromptDesignSeedInput {
  studyStage?: string | null
  taskLabel?: string | null
  genreLabel?: string | null
  wordRange?: string | null
  requirements?: string | null
  attachmentLabel?: string | null
  hasMaterial?: boolean
  hasImage?: boolean
}

export interface PromptSheetLike {
  promptType?: ExamPromptType | null
  taskType?: string | null
  topic?: string | null
  promptText?: string | null
  requirements?: string | null
  genre?: string | null
  wordRange?: string | null
  maxScore?: number | null
  materialText?: string | null
  chartSpec?: ExamChartSpec | null
  comicScenes?: ExamComicScene[] | null
  sourceType?: ExamPromptSheet['sourceType']
  part?: string | null
  questionNo?: string | null
  directions?: string | null
  attachmentType?: ExamPromptAttachmentType | null
  attachmentSource?: 'none' | 'user_upload' | 'agent_generate' | 'user_text' | null
  attachmentTitle?: string | null
  attachmentContent?: string | null
  attachmentImageUrl?: string | null
  visualKind?: ExamPromptVisualKind | null
}

export interface PromptConfigUpdate {
  taskType: ExamTaskSelectionValue | null
  promptType: ExamPromptType | null
  genre: string | null
  wordRange: string | null
  requirements: string | null
}

export function hasPromptDesignSeed(input: PromptDesignSeedInput): boolean {
  return Boolean(
    input.genreLabel?.trim()
      || input.wordRange?.trim()
      || input.requirements?.trim()
      || input.hasMaterial
      || input.hasImage,
  )
}

export function buildPromptDesignSeedRequest(input: PromptDesignSeedInput): string {
  const lines = ['请根据以下配置，生成一份可以直接用于写作练习的英语考试作文题单。']

  if (input.studyStage?.trim()) {
    lines.push(`学段/考试：${input.studyStage.trim()}`)
  }
  if (input.taskLabel?.trim()) {
    lines.push(`任务类型：${input.taskLabel.trim()}`)
  }
  if (input.genreLabel?.trim()) {
    lines.push(`体裁：${input.genreLabel.trim()}`)
  }
  if (input.wordRange?.trim()) {
    lines.push(`字数要求：${input.wordRange.trim()}词`)
  }
  if (input.requirements?.trim()) {
    lines.push(`写作要求：${input.requirements.trim()}`)
  }
  if (input.hasMaterial) {
    lines.push('附件要求：已提供材料，请把材料内容整理进题面。')
  } else if (input.hasImage) {
    lines.push('附件要求：已提供图片，请生成看图/图表/图画写作题面。')
  } else if (input.attachmentLabel?.trim() && input.attachmentLabel.trim() !== '无') {
    lines.push(`附件要求：${input.attachmentLabel.trim()}`)
  }

  lines.push('请补全清晰的主题、题目情境和写作任务要求；不要生成范文或答案。')
  return lines.join('\n')
}

export interface PastPromptLike {
  paper?: string | null
  promptText: string
  imageUrl?: string | null
  imageDescription?: string | null
  materialText?: string | null
  task?: string | null
  wordCountMin?: number | null
  wordCountMax?: number | null
  maxScore?: number | null
}

export interface ExamResumeMetadataLike {
  titleSnapshot?: string | null
  topicTitle?: string | null
  promptText?: string | null
  attachmentImageUrl?: string | null
  genre?: string | null
  sourceType?: ExamTopicInfo['sourceType']
  examType?: string | null
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
  maxScore?: number | null
}

export function parseWordRange(value: string | null): { minWords: number | null; recommendedMaxWords: number | null } {
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
    const numericValue = Number(singleMatch[1])
    return {
      minWords: numericValue,
      recommendedMaxWords: numericValue,
    }
  }
  return { minWords: null, recommendedMaxWords: null }
}

function normalizeWordRangeLabel(value: string | null | undefined): string | null {
  const source = value?.trim()
  if (!source) return null
  return source
    .replace(/\s*(words?|词)\s*$/i, '')
    .replace(/\s+/g, '')
    .trim() || null
}

function normalizeGenreForPromptType(input: PromptSheetLike): string | null {
  const promptType = input.promptType ?? null
  if (promptType === 'chart' || input.visualKind === 'chart' || input.visualKind === 'table' || input.chartSpec) {
    return 'chart'
  }
  if (promptType === 'comic' || input.visualKind === 'comic' || (input.comicScenes?.length ?? 0) > 0) {
    return 'picture'
  }
  if (promptType === 'material' || input.materialText?.trim()) {
    return 'material'
  }
  return input.genre?.trim() || null
}

export function derivePromptConfigUpdateFromPromptSheet(input: PromptSheetLike): PromptConfigUpdate {
  const taskType = getExamTaskSelectionLabel(input.taskType) ? (input.taskType as ExamTaskSelectionValue) : null
  const promptType: ExamPromptType | null =
    input.promptType
      ?? (input.chartSpec ? 'chart'
        : (input.comicScenes?.length ?? 0) > 0 ? 'comic'
          : input.materialText?.trim() ? 'material'
            : null)

  return {
    taskType,
    promptType,
    genre: normalizeGenreForPromptType({ ...input, promptType }),
    wordRange: normalizeWordRangeLabel(input.wordRange),
    requirements: input.requirements?.trim() || null,
  }
}

export function extractPromptFallback(text: string, currentWordRange?: string | null, currentRequirements?: string | null) {
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

export function isAiGenerationSupportedStage(stage: string | null | undefined): boolean {
  return ['highschool', 'senior', 'cet4', 'cet6', 'postgrad'].includes((stage ?? '').toLowerCase())
}

export function getExamTaskSelectionOptions(stage: string | null | undefined): ExamTaskSelectionOption[] {
  if ((stage ?? '').toLowerCase() !== 'postgrad') return []
  return [
    { value: 'task1', label: 'Task 1' },
    { value: 'task2', label: 'Task 2' },
  ]
}

export function getExamTaskSelectionLabel(taskType: string | null | undefined): string | null {
  if (!taskType) return null
  const normalized = taskType.trim().toLowerCase()
  if (normalized === 'task1') return 'Task 1'
  if (normalized === 'task2') return 'Task 2'
  return null
}

export function summarizeChartSpec(chartSpec?: ExamChartSpec | null): string | null {
  if (!chartSpec) return null
  const lines: string[] = []
  if (chartSpec.title?.trim()) {
    lines.push(chartSpec.title.trim())
  }
  if (chartSpec.columns.length > 0 && chartSpec.rows.length > 0) {
    lines.push(chartSpec.columns.join(' | '))
    for (const row of chartSpec.rows) {
      lines.push(row.join(' | '))
    }
  }
  if (chartSpec.summary?.trim()) {
    lines.push(`概括：${chartSpec.summary.trim()}`)
  }
  return lines.length > 0 ? lines.join('\n') : null
}

const CHART_PREVIEW_COLORS = ['#0f766e', '#b45309', '#2563eb', '#7c3aed']

function parseChartNumericValue(value: string | null | undefined): number | null {
  const source = value?.trim()
  if (!source) return null
  const match = source.replace(/,/g, '').match(/-?\d+(?:\.\d+)?/)
  if (!match) return null
  const parsed = Number(match[0])
  return Number.isFinite(parsed) ? parsed : null
}

export function shouldRenderChartAsTable(chartSpec?: ExamChartSpec | null): boolean {
  return (chartSpec?.displayType ?? '').trim().toLowerCase() === 'table'
}

export function buildChartPreviewFigure(chartSpec?: ExamChartSpec | null): ExamChartPreviewFigure {
  const rows = chartSpec?.rows ?? []
  const columns = chartSpec?.columns ?? []
  const labels = rows.map((row, index) => row[0]?.trim() || `${index + 1}`)

  if (columns.length < 2 || rows.length < 2) {
    return { labels, series: [], leftAxisLabel: null, rightAxisLabel: null, isComboChart: false }
  }

  const rawSeries = columns.slice(1).flatMap((column, seriesIndex) => {
    const numericRows = rows
      .map((row, rowIndex) => ({
        rowIndex,
        label: labels[rowIndex],
        rawValue: row[seriesIndex + 1]?.trim() || '',
        value: parseChartNumericValue(row[seriesIndex + 1]),
      }))
      .filter((item): item is { rowIndex: number; label: string; rawValue: string; value: number } => item.value !== null)

    if (numericRows.length < 2) return []

    const lowerName = column.toLowerCase()
    const isRateSeries = /%|rate|ratio|coefficient|growth|增速|增长率|增长|比例|率|系数/.test(lowerName)
    return [{
      name: column,
      color: CHART_PREVIEW_COLORS[seriesIndex % CHART_PREVIEW_COLORS.length],
      axis: isRateSeries ? 'right' as const : 'left' as const,
      numericRows,
      sourceIndex: seriesIndex,
    }]
  })

  if (rawSeries.length === 0) {
    return { labels, series: [], leftAxisLabel: null, rightAxisLabel: null, isComboChart: false }
  }

  const hasRightAxis = rawSeries.some((series) => series.axis === 'right')
  const hasLeftAxis = rawSeries.some((series) => series.axis === 'left')
  const isComboChart = hasLeftAxis && hasRightAxis

  const axisRanges = {
    left: getChartAxisRange(rawSeries.filter((series) => series.axis === 'left').flatMap((series) => series.numericRows.map((item) => item.value)), true),
    right: getChartAxisRange(rawSeries.filter((series) => series.axis === 'right').flatMap((series) => series.numericRows.map((item) => item.value)), true),
  }

  const series = rawSeries.map((raw) => {
    const rangeInfo = axisRanges[raw.axis]
    const points = raw.numericRows.map((item) => {
      const normalized = rangeInfo.range === 0 ? 0.5 : (item.value - rangeInfo.min) / rangeInfo.range
      const x = rows.length === 1 ? 50 : 10 + (item.rowIndex / (rows.length - 1)) * 80
      const y = 86 - normalized * 68
      return {
        x,
        y,
        label: item.label,
        value: item.rawValue,
      }
    })

    const shouldUseBar = isComboChart && raw.axis === 'left' && raw.sourceIndex === 0
    return {
      name: raw.name,
      color: raw.color,
      kind: shouldUseBar ? 'bar' as const : 'line' as const,
      axis: raw.axis,
      points,
      polyline: points.map((point) => `${point.x},${point.y}`).join(' '),
    }
  })

  return {
    labels,
    series,
    leftAxisLabel: rawSeries.find((series) => series.axis === 'left')?.name ?? null,
    rightAxisLabel: rawSeries.find((series) => series.axis === 'right')?.name ?? null,
    isComboChart,
  }
}

function getChartAxisRange(values: number[], includeZero: boolean): { min: number; max: number; range: number } {
  if (values.length === 0) {
    return { min: 0, max: 1, range: 1 }
  }

  const minValue = Math.min(...values)
  const maxValue = Math.max(...values)
  const min = includeZero && minValue > 0 ? 0 : minValue
  const max = maxValue === min ? maxValue + 1 : maxValue
  return { min, max, range: max - min }
}

export function buildLegacyLineChartPreviewFigure(chartSpec?: ExamChartSpec | null): ExamChartPreviewFigure {
  const rows = chartSpec?.rows ?? []
  const columns = chartSpec?.columns ?? []
  const labels = rows.map((row, index) => row[0]?.trim() || `${index + 1}`)

  if (columns.length < 2 || rows.length < 2) {
    return { labels, series: [], leftAxisLabel: null, rightAxisLabel: null, isComboChart: false }
  }

  const series = columns.slice(1).flatMap((column, seriesIndex) => {
    const numericRows = rows
      .map((row, rowIndex) => ({
        rowIndex,
        label: labels[rowIndex],
        rawValue: row[seriesIndex + 1]?.trim() || '',
        value: parseChartNumericValue(row[seriesIndex + 1]),
      }))
      .filter((item): item is { rowIndex: number; label: string; rawValue: string; value: number } => item.value !== null)

    if (numericRows.length < 2) return []

    const values = numericRows.map((item) => item.value)
    const min = Math.min(...values)
    const max = Math.max(...values)
    const range = max - min
    const points = numericRows.map((item) => {
      const normalized = range === 0 ? 0.5 : (item.value - min) / range
      const x = rows.length === 1 ? 50 : 10 + (item.rowIndex / (rows.length - 1)) * 80
      const y = 86 - normalized * 68
      return {
        x,
        y,
        label: item.label,
        value: item.rawValue,
      }
    })

    return [{
      name: column,
      color: CHART_PREVIEW_COLORS[seriesIndex % CHART_PREVIEW_COLORS.length],
      kind: 'line' as const,
      axis: 'left' as const,
      points,
      polyline: points.map((point) => `${point.x},${point.y}`).join(' '),
    }]
  })

  return { labels, series, leftAxisLabel: columns[1] ?? null, rightAxisLabel: null, isComboChart: false }
}

export function summarizeComicScenes(comicScenes?: ExamComicScene[] | null): string | null {
  if (!comicScenes || comicScenes.length === 0) return null
  const lines: string[] = []
  comicScenes.forEach((scene, index) => {
    const label = scene.title?.trim() || `Scene ${index + 1}`
    lines.push(`${label}: ${scene.description.trim()}`)
    if (scene.dialogue?.trim()) {
      lines.push(`对白：${scene.dialogue.trim()}`)
    }
  })
  return lines.join('\n')
}

function parsePipeRow(line: string): string[] {
  return line
    .split('|')
    .map((item) => item.trim())
    .filter(Boolean)
}

export function parseChartSpecFromText(value: string | null | undefined): ExamChartSpec | null {
  const lines = (value ?? '')
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)

  if (lines.length === 0) return null

  let title: string | null = null
  let summary: string | null = null
  const tableLines: string[][] = []

  for (const line of lines) {
    if (/^概括[:：]/.test(line)) {
      summary = line.replace(/^概括[:：]\s*/, '').trim() || null
      continue
    }

    const row = parsePipeRow(line)
    if (row.length >= 2) {
      tableLines.push(row)
      continue
    }

    if (!title) {
      title = line
    }
  }

  if (tableLines.length < 2) return null

  const [columns, ...rows] = tableLines
  return {
    title,
    displayType: 'chart',
    columns,
    rows,
    summary,
  }
}

export function buildExamTaskPrompt(info: ExamTopicInfo) {
  const lines: string[] = []
  const topic = info.topic?.trim()
  const imageDescription = info.imageDescription?.trim()
  const materialText = info.materialText?.trim()
  const genre = info.genre?.trim()
  const wordRange = info.wordRange?.trim()
  const requirements = info.requirements?.trim()
  const chartSummary = summarizeChartSpec(info.chartSpec)
  const comicSummary = summarizeComicScenes(info.comicScenes)

  if (topic) {
    lines.push('题目要求（润色后必须继续严格对齐）：')
    lines.push(topic)
  }
  if (imageDescription && imageDescription !== topic) {
    lines.push('图画信息：')
    lines.push(imageDescription)
  }
  if (materialText && materialText !== topic) {
    lines.push('材料信息：')
    lines.push(materialText)
  }
  if (chartSummary) {
    lines.push('图表信息：')
    lines.push(chartSummary)
  }
  if (comicSummary) {
    lines.push('漫画信息：')
    lines.push(comicSummary)
  }
  if (genre) lines.push(`体裁：${genre}`)
  if (wordRange) lines.push(`字数要求：${wordRange}词`)
  if (requirements) lines.push(`写作要求：${requirements}`)
  if (info.maxScore && info.maxScore !== 100) lines.push(`满分分值：${info.maxScore}分`)

  return lines.join('\n')
}

function toRequirementList(value: string | null | undefined): string[] {
  const source = value?.trim()
  if (!source) return []
  return source
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export function normalizePromptSheet(input: PromptSheetLike): ExamPromptSheet {
  const promptType = input.promptType ?? 'general'
  const chartSummary = summarizeChartSpec(input.chartSpec)
  const comicSummary = summarizeComicScenes(input.comicScenes)

  let attachmentType: ExamPromptAttachmentType = input.attachmentType ?? 'none'
  let attachmentTitle = input.attachmentTitle?.trim() || null
  let attachmentContent = input.attachmentContent?.trim() || null
  let visualKind: ExamPromptVisualKind | null = input.visualKind ?? null

  if (promptType === 'material') {
    attachmentType = 'material'
    attachmentContent = attachmentContent ?? input.materialText?.trim() ?? null
  } else if (promptType === 'chart') {
    attachmentType = 'visual'
    visualKind = visualKind ?? (input.chartSpec?.displayType === 'table' ? 'table' : 'chart')
    attachmentTitle = attachmentTitle ?? input.chartSpec?.title?.trim() ?? null
    attachmentContent = attachmentContent ?? chartSummary
  } else if (promptType === 'comic') {
    attachmentType = 'visual'
    visualKind = visualKind ?? 'comic'
    attachmentContent = attachmentContent ?? comicSummary
  }

  return {
    part: input.part?.trim() || 'Part B',
    questionNo: input.questionNo?.trim() || null,
    directions: input.directions?.trim() || 'Directions:',
    promptText: input.promptText?.trim() || input.topic?.trim() || '',
    requirements: toRequirementList(input.requirements),
    wordRange: input.wordRange?.trim() || null,
    score: input.maxScore ?? null,
    attachmentType,
    attachmentTitle,
    attachmentContent,
    attachmentImageUrl: input.attachmentImageUrl?.trim() || null,
    visualKind,
    sourceType: input.sourceType ?? 'ai_generated',
  }
}

export function buildPromptSheetFromPastPrompt(prompt: PastPromptLike): ExamPromptSheet {
  const attachmentType: ExamPromptAttachmentType =
    prompt.materialText?.trim() ? 'material' : prompt.imageUrl?.trim() || prompt.imageDescription?.trim() ? 'visual' : 'none'
  const visualKind: ExamPromptVisualKind | null = attachmentType === 'visual' ? 'image' : null
  const wordRange =
    prompt.wordCountMin && prompt.wordCountMax
      ? `${prompt.wordCountMin}-${prompt.wordCountMax}`
      : prompt.wordCountMin
        ? `${prompt.wordCountMin}`
        : null

  return {
    part: 'Part B',
    questionNo: null,
    directions: 'Directions:',
    promptText: prompt.promptText.trim(),
    requirements: [],
    wordRange,
    score: prompt.maxScore ?? null,
    attachmentType,
    attachmentTitle: prompt.paper?.trim() || null,
    attachmentContent: prompt.materialText?.trim() || prompt.imageDescription?.trim() || null,
    attachmentImageUrl: prompt.imageUrl?.trim() || null,
    visualKind,
    sourceType: 'past_prompt',
  }
}

function normalizeWordRangeFromMetadata(metadata: ExamResumeMetadataLike, promptText: string | null): string | null {
  if (metadata.minWords && metadata.recommendedMaxWords) {
    return `${metadata.minWords}-${metadata.recommendedMaxWords}`
  }
  if (metadata.minWords) {
    return `${metadata.minWords}`
  }
  const match = promptText?.match(/字数要求：\s*([0-9]+(?:\s*[-~至]\s*[0-9]+)?)\s*词?/)
  return match?.[1]?.replace(/\s+/g, '') ?? null
}

function inferPostgradEnglishOneTask2ImageUrl(
  metadata: ExamResumeMetadataLike,
  fallbackStage?: string | null,
): string | null {
  if (metadata.sourceType !== 'past_prompt') return null

  const stage = `${metadata.examType ?? fallbackStage ?? ''}`.trim().toLowerCase()
  const searchableText = [
    metadata.titleSnapshot,
    metadata.topicTitle,
    metadata.promptText,
  ]
    .filter((item): item is string => Boolean(item?.trim()))
    .join('\n')

  const isPostgradEnglishOne =
    stage === 'postgrad'
      || /考研英语一|考研英语\(一\)|postgrad[-_\s]?en1/i.test(searchableText)
  const isTask2 =
    metadata.taskType?.trim().toLowerCase() === 'task2'
      || /大作文|task\s*2/i.test(searchableText)

  if (!isPostgradEnglishOne || !isTask2) return null

  const yearMatch = searchableText.match(/\b(20(?:0[5-9]|1[0-9]|2[0-5]))\b/)
  if (!yearMatch) return null

  return `/uploads/past-prompts/postgrad/en1/${yearMatch[1]}-task2.png`
}

function parseResumePromptSections(promptText: string | null | undefined) {
  const lines = (promptText ?? '')
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)

  const singleLineMap = new Map<string, string>()
  const blockMap = new Map<string, string>()
  const labeledPrefixes = ['体裁：', '字数要求：', '写作要求：', '满分分值：']
  const blockLabels = ['题目要求（润色后必须继续严格对齐）：', '图画信息：', '材料信息：', '图表信息：', '漫画信息：']

  let index = 0
  while (index < lines.length) {
    const line = lines[index]
    const singlePrefix = labeledPrefixes.find((prefix) => line.startsWith(prefix))
    if (singlePrefix) {
      singleLineMap.set(singlePrefix, line.slice(singlePrefix.length).trim())
      index += 1
      continue
    }

    if (blockLabels.includes(line)) {
      const sectionLines: string[] = []
      index += 1
      while (index < lines.length) {
        const current = lines[index]
        const isNextLabel = blockLabels.includes(current) || labeledPrefixes.some((prefix) => current.startsWith(prefix))
        if (isNextLabel) break
        sectionLines.push(current)
        index += 1
      }
      blockMap.set(line, sectionLines.join('\n').trim())
      continue
    }

    index += 1
  }

  return { singleLineMap, blockMap }
}

export function buildExamResumePreview(
  metadata: ExamResumeMetadataLike | null | undefined,
  fallbackStage?: string | null,
): { topicInfo: ExamTopicInfo; sheet: ExamPromptSheet; taskType: ExamTaskSelectionValue | null } | null {
  if (!metadata) return null

  const { singleLineMap, blockMap } = parseResumePromptSections(metadata.promptText)
  const topic = metadata.topicTitle?.trim()
    || blockMap.get('题目要求（润色后必须继续严格对齐）：')
    || metadata.titleSnapshot?.trim()
    || ''

  if (!topic) return null

  const chartSpec = parseChartSpecFromText(blockMap.get('图表信息：') || null)
  const drawingInfo = blockMap.get('图画信息：') || null
  const comicInfo = blockMap.get('漫画信息：') || null
  const materialInfo = blockMap.get('材料信息：') || null
  const attachmentImageUrl = metadata.attachmentImageUrl?.trim()
    || inferPostgradEnglishOneTask2ImageUrl(metadata, fallbackStage)

  const promptType: ExamPromptType =
    chartSpec ? 'chart'
      : comicInfo || drawingInfo || attachmentImageUrl ? 'comic'
        : materialInfo ? 'material'
          : 'general'

  const genre = metadata.genre?.trim() || singleLineMap.get('体裁：') || null
  const wordRange = normalizeWordRangeFromMetadata(metadata, metadata.promptText ?? null)
  const requirements = singleLineMap.get('写作要求：') || null
  const extractedScore = Number(singleLineMap.get('满分分值：')?.replace(/[^\d]/g, '') || 0)
  const maxScore = metadata.maxScore ?? extractedScore ?? 100
  const taskType = getExamTaskSelectionLabel(metadata.taskType) ? (metadata.taskType as ExamTaskSelectionValue) : null
  const parsedWordRange = parseWordRange(wordRange)

  const topicInfo: ExamTopicInfo = {
    paper: null,
    promptSheetId: null,
    topic,
    genre,
    wordRange,
    requirements,
    imageDescription: drawingInfo || comicInfo,
    materialText: materialInfo,
    attachmentImageUrl,
    maxScore,
    sourceType: metadata.sourceType ?? 'ai_generated',
    examType: metadata.examType ?? fallbackStage ?? null,
    taskType: taskType ?? metadata.taskType ?? null,
    minWords: metadata.minWords ?? parsedWordRange.minWords,
    recommendedMaxWords: metadata.recommendedMaxWords ?? parsedWordRange.recommendedMaxWords,
    promptType,
    chartSpec,
    comicScenes: [],
  }

  const sheet = normalizePromptSheet({
    promptType,
    promptText: topic,
    requirements,
    wordRange,
    maxScore,
    sourceType: topicInfo.sourceType,
    attachmentType: promptType === 'material' ? 'material' : promptType === 'chart' || promptType === 'comic' ? 'visual' : 'none',
    attachmentContent:
      promptType === 'material' ? materialInfo
        : promptType === 'chart' ? blockMap.get('图表信息：') || null
          : promptType === 'comic' ? drawingInfo || comicInfo
            : null,
    attachmentImageUrl,
    chartSpec,
    visualKind: promptType === 'chart'
      ? chartSpec?.displayType === 'table' ? 'table' : 'chart'
      : promptType === 'comic' ? 'comic' : null,
  })

  return { topicInfo, sheet, taskType }
}

export function buildVisualAttachmentPreview(
  sheet: ExamPromptSheet | null | undefined,
  info?: Pick<ExamTopicInfo, 'comicScenes' | 'chartSpec'> | null,
): VisualAttachmentPreview {
  if (!sheet || sheet.attachmentType !== 'visual') {
    return {
      mode: 'none',
      title: null,
      text: null,
      imageUrl: null,
      comicScenes: [],
      chartSpec: null,
    }
  }

  if (sheet.attachmentImageUrl?.trim()) {
    return {
      mode: 'image',
      title: sheet.attachmentTitle,
      text: sheet.attachmentContent,
      imageUrl: sheet.attachmentImageUrl.trim(),
      comicScenes: [],
      chartSpec: null,
    }
  }

  if (sheet.visualKind === 'comic' && (info?.comicScenes?.length ?? 0) > 0) {
    return {
      mode: 'comic',
      title: sheet.attachmentTitle,
      text: sheet.attachmentContent,
      imageUrl: null,
      comicScenes: info?.comicScenes ?? [],
      chartSpec: null,
    }
  }

  if ((sheet.visualKind === 'chart' || sheet.visualKind === 'table') && info?.chartSpec) {
    return {
      mode: sheet.visualKind,
      title: sheet.attachmentTitle ?? info.chartSpec.title ?? null,
      text: sheet.attachmentContent,
      imageUrl: null,
      comicScenes: [],
      chartSpec: info.chartSpec,
    }
  }

  return {
    mode: 'text',
    title: sheet.attachmentTitle,
    text: sheet.attachmentContent,
    imageUrl: null,
    comicScenes: [],
    chartSpec: null,
  }
}

export function buildPromptSheetCopyText(
  sheet: ExamPromptSheet | null | undefined,
  info?: Pick<ExamTopicInfo, 'comicScenes' | 'chartSpec'> | null,
): string {
  if (!sheet) return ''

  const lines: string[] = []
  const preview = buildVisualAttachmentPreview(sheet, info)

  if (sheet.directions?.trim()) {
    lines.push(sheet.directions.trim())
  }

  if (sheet.promptText?.trim()) {
    lines.push(sheet.promptText.trim())
  }

  if (sheet.requirements.length > 0) {
    lines.push('写作要求：')
    sheet.requirements.forEach((requirement, index) => {
      lines.push(`${index + 1}. ${requirement}`)
    })
  }

  const metaLine: string[] = []
  if (sheet.wordRange?.trim()) {
    metaLine.push(`字数要求：${sheet.wordRange.trim()}词`)
  }
  if (sheet.score) {
    metaLine.push(`满分：${sheet.score}分`)
  }
  if (metaLine.length > 0) {
    lines.push(metaLine.join('  '))
  }

  if (sheet.attachmentType !== 'none') {
    const attachmentTitle = sheet.attachmentTitle?.trim() || '附件内容'
    lines.push(`${attachmentTitle}：`)

    if (preview.mode === 'image' && preview.imageUrl) {
      lines.push(preview.imageUrl)
      if (preview.text?.trim()) {
        lines.push(preview.text.trim())
      }
    } else if (preview.mode === 'comic' && preview.comicScenes.length > 0) {
      preview.comicScenes.forEach((scene, index) => {
        const label = scene.title?.trim() || `Scene ${index + 1}`
        lines.push(`${label}: ${scene.description.trim()}`)
        if (scene.dialogue?.trim()) {
          lines.push(`对白：${scene.dialogue.trim()}`)
        }
      })
    } else if ((preview.mode === 'chart' || preview.mode === 'table') && preview.chartSpec) {
      if (preview.chartSpec.title?.trim()) {
        lines.push(preview.chartSpec.title.trim())
      }
      if (preview.chartSpec.columns.length > 0) {
        lines.push(preview.chartSpec.columns.join(' | '))
      }
      preview.chartSpec.rows.forEach((row) => {
        lines.push(row.join(' | '))
      })
      if (preview.chartSpec.summary?.trim()) {
        lines.push(`概括：${preview.chartSpec.summary.trim()}`)
      }
    } else if (preview.text?.trim()) {
      lines.push(preview.text.trim())
    } else if (sheet.attachmentContent?.trim()) {
      lines.push(sheet.attachmentContent.trim())
    }
  }

  return lines.join('\n\n').trim()
}
