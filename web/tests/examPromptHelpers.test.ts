import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildExamResumePreview,
  buildChartPreviewFigure,
  buildPromptSheetCopyText,
  derivePromptConfigUpdateFromPromptSheet,
  buildVisualAttachmentPreview,
  buildExamTaskPrompt,
  getExamTaskSelectionOptions,
  getExamTaskSelectionLabel,
  isAiGenerationSupportedStage,
  shouldRenderChartAsTable,
  type ExamTopicInfo,
} from '../src/pages/app/examPromptHelpers.ts'

test('buildChartPreviewFigure builds normalized multi-series trend preview for chart data', () => {
  const preview = buildChartPreviewFigure({
    title: 'China GDP and Engel Coefficient Trends',
    displayType: 'chart',
    columns: ['Year', 'GDP (trillion USD)', 'Engel coefficient (%)'],
    rows: [
      ['2014', '10.5', '31.2%'],
      ['2017', '12.3', '29.3%'],
      ['2020', '14.7', '27.5%'],
      ['2023', '17.8', '25.1%'],
    ],
    summary: 'GDP rose while the Engel coefficient declined.',
  })

  assert.deepEqual(preview.labels, ['2014', '2017', '2020', '2023'])
  assert.equal(preview.series.length, 2)
  assert.equal(preview.series[0].points.length, 4)
  assert.match(preview.series[0].polyline, /10,/)
  assert.equal(shouldRenderChartAsTable({ displayType: 'table', columns: [], rows: [] }), true)
  assert.equal(shouldRenderChartAsTable({ displayType: 'chart', columns: [], rows: [] }), false)
})

test('derivePromptConfigUpdateFromPromptSheet maps generated chart sheet back to setup config', () => {
  const update = derivePromptConfigUpdateFromPromptSheet({
    promptType: 'chart',
    topic: 'Campus life trends',
    promptText: 'Write an essay based on the chart below.',
    requirements: 'describe the trend and comment on its significance',
    genre: 'chart',
    wordRange: '160-200 words',
    maxScore: 20,
    sourceType: 'ai_generated',
    taskType: 'task2',
    chartSpec: {
      title: 'Campus activity participation',
      displayType: 'chart',
      columns: ['Year', 'Rate'],
      rows: [['2020', '42%'], ['2024', '63%']],
    },
  })

  assert.equal(update.taskType, 'task2')
  assert.equal(update.promptType, 'chart')
  assert.equal(update.genre, 'chart')
  assert.equal(update.wordRange, '160-200')
  assert.equal(update.requirements, 'describe the trend and comment on its significance')
})

test('derivePromptConfigUpdateFromPromptSheet maps visual kind comic back to picture genre', () => {
  const update = derivePromptConfigUpdateFromPromptSheet({
    promptType: 'comic',
    topic: 'Family responsibility',
    promptText: 'Write an essay based on the comic below.',
    requirements: null,
    genre: null,
    wordRange: null,
    maxScore: null,
    sourceType: 'ai_generated',
    taskType: 'task1',
    visualKind: 'comic',
    comicScenes: [
      { title: 'Scene 1', description: 'A child helps an elderly parent.', dialogue: null },
    ],
  })

  assert.equal(update.promptType, 'comic')
  assert.equal(update.genre, 'picture')
})

test('buildExamTaskPrompt adds chart summary for chart prompts', () => {
  const info: ExamTopicInfo = {
    topic: '人工智能学习工具使用变化',
    genre: '议论文',
    wordRange: '160-200',
    requirements: '1) describe the changes 2) analyze the reasons 3) give your comments',
    imageDescription: null,
    materialText: null,
    maxScore: 20,
    sourceType: 'ai_generated',
    examType: 'postgrad',
    taskType: 'chart',
    minWords: 160,
    recommendedMaxWords: 200,
    promptType: 'chart',
    chartSpec: {
      title: 'College Students Using AI Study Tools',
      displayType: 'table',
      columns: ['Year', 'Usage Rate'],
      rows: [['2021', '18%'], ['2024', '63%']],
      summary: 'The usage rate rose steadily from 18% to 63%.',
    },
    comicScenes: [],
  }

  const prompt = buildExamTaskPrompt(info)

  assert.match(prompt, /图表信息：/)
  assert.match(prompt, /College Students Using AI Study Tools/)
  assert.match(prompt, /18%/)
})

test('buildExamTaskPrompt adds comic scene summary for comic prompts', () => {
  const info: ExamTopicInfo = {
    topic: '手机依赖',
    genre: '议论文',
    wordRange: '120-150',
    requirements: 'describe the scenes and explain the message',
    imageDescription: null,
    materialText: null,
    maxScore: 25,
    sourceType: 'ai_generated',
    examType: 'highschool',
    taskType: 'comic',
    minWords: 120,
    recommendedMaxWords: 150,
    promptType: 'comic',
    chartSpec: null,
    comicScenes: [
      { title: 'Scene 1', description: 'A family sits together but each person is staring at a phone.', dialogue: 'No one is talking.' },
      { title: 'Scene 2', description: 'The parents look worried while the child keeps scrolling.', dialogue: 'Put it down for dinner.' },
    ],
  }

  const prompt = buildExamTaskPrompt(info)

  assert.match(prompt, /漫画信息：/)
  assert.match(prompt, /Scene 1/)
  assert.match(prompt, /Put it down for dinner/)
})

test('isAiGenerationSupportedStage only enables first-launch stages', () => {
  assert.equal(isAiGenerationSupportedStage('highschool'), true)
  assert.equal(isAiGenerationSupportedStage('cet4'), true)
  assert.equal(isAiGenerationSupportedStage('cet6'), true)
  assert.equal(isAiGenerationSupportedStage('postgrad'), true)
  assert.equal(isAiGenerationSupportedStage('junior'), false)
  assert.equal(isAiGenerationSupportedStage('ielts'), false)
})

test('buildVisualAttachmentPreview prefers comic scenes over plain text fallback', () => {
  const info: ExamTopicInfo = {
    topic: 'OpenAI 和 Anthropic 的漫画作文',
    genre: 'Essay',
    wordRange: '160-200',
    requirements: 'describe and comment',
    imageDescription: null,
    materialText: null,
    maxScore: 100,
    sourceType: 'ai_generated',
    examType: 'postgrad',
    taskType: 'comic',
    minWords: 160,
    recommendedMaxWords: 200,
    promptType: 'comic',
    chartSpec: null,
    comicScenes: [
      { title: 'Scene 1', description: 'OpenAI and Anthropic face each other across a street.', dialogue: 'Speed or safety?' },
      { title: 'Scene 2', description: 'Lightning flashes between the two buildings.', dialogue: null },
    ],
  }

  const preview = buildVisualAttachmentPreview({
    part: 'Part B',
    questionNo: null,
    directions: 'Directions:',
    promptText: 'Write an essay based on the comic below.',
    requirements: [],
    wordRange: '160-200',
    score: 100,
    attachmentType: 'visual',
    attachmentTitle: null,
    attachmentContent: 'fallback text',
    attachmentImageUrl: null,
    visualKind: 'comic',
    sourceType: 'ai_generated',
  }, info)

  assert.equal(preview.mode, 'comic')
  assert.equal(preview.comicScenes.length, 2)
  assert.equal(preview.text, 'fallback text')
})

test('buildVisualAttachmentPreview uses image mode when image url exists', () => {
  const preview = buildVisualAttachmentPreview({
    part: 'Part B',
    questionNo: null,
    directions: 'Directions:',
    promptText: 'Write an essay based on the picture below.',
    requirements: [],
    wordRange: '120-150',
    score: 20,
    attachmentType: 'visual',
    attachmentTitle: 'Picture',
    attachmentContent: 'describe the image',
    attachmentImageUrl: 'https://example.com/topic.png',
    visualKind: 'image',
    sourceType: 'ai_generated',
  }, null)

  assert.equal(preview.mode, 'image')
  assert.equal(preview.imageUrl, 'https://example.com/topic.png')
})

test('buildPromptSheetCopyText formats prompt sheet into plain text', () => {
  const text = buildPromptSheetCopyText({
    part: 'Part B',
    questionNo: null,
    directions: 'Directions:',
    promptText: 'Write an essay based on the comic below.',
    requirements: ['describe the comic', 'explain its meaning'],
    wordRange: '160-200',
    score: 20,
    attachmentType: 'visual',
    attachmentTitle: 'Visual Attachment',
    attachmentContent: 'fallback text',
    attachmentImageUrl: null,
    visualKind: 'comic',
    sourceType: 'ai_generated',
  }, {
    comicScenes: [
      { title: 'Scene 1', description: 'A student studies in the library.', dialogue: 'The deadline is close.' },
      { title: 'Scene 2', description: 'The same student joins a campus activity.', dialogue: 'Let us have a break.' },
    ],
    chartSpec: null,
  })

  assert.match(text, /^Directions:/)
  assert.match(text, /Write an essay based on the comic below\./)
  assert.match(text, /1\. describe the comic/)
  assert.match(text, /字数要求：160-200词/)
  assert.match(text, /满分：20分/)
  assert.match(text, /Scene 1: A student studies in the library\./)
})

test('getExamTaskSelectionOptions exposes task gating only for postgrad stage', () => {
  assert.deepEqual(
    getExamTaskSelectionOptions('postgrad').map((item) => item.value),
    ['task1', 'task2'],
  )
  assert.deepEqual(getExamTaskSelectionOptions('cet4'), [])
})

test('getExamTaskSelectionLabel returns display label for known task types', () => {
  assert.equal(getExamTaskSelectionLabel('task1'), 'Task 1')
  assert.equal(getExamTaskSelectionLabel('task2'), 'Task 2')
  assert.equal(getExamTaskSelectionLabel('unknown'), null)
})

test('buildExamResumePreview restores exam preview from saved metadata without using raw task prompt as prompt text', () => {
  const restored = buildExamResumePreview({
    titleSnapshot: '贵州民族文化保护',
    topicTitle: 'Write an essay on the ethnic groups in Guizhou and their cultural preservation.',
    promptText: [
      '题目要求（润色后必须继续严格对齐）：',
      'Write an essay on the ethnic groups in Guizhou and their cultural preservation.',
      '材料信息：',
      'Guizhou is home to many ethnic groups with rich traditions.',
      '体裁：Essay',
      '字数要求：300-500词',
      '写作要求：Your essay should demonstrate a clear understanding of the topic.',
      '满分分值：100分',
    ].join('\n'),
    genre: 'Essay',
    sourceType: 'ai_generated',
    examType: 'postgrad',
    taskType: 'task1',
    minWords: 300,
    recommendedMaxWords: 500,
    maxScore: 100,
  }, 'postgrad')

  assert.ok(restored)
  assert.equal(restored?.topicInfo.topic, 'Write an essay on the ethnic groups in Guizhou and their cultural preservation.')
  assert.equal(restored?.topicInfo.wordRange, '300-500')
  assert.equal(restored?.topicInfo.requirements, 'Your essay should demonstrate a clear understanding of the topic.')
  assert.equal(restored?.sheet.promptText, 'Write an essay on the ethnic groups in Guizhou and their cultural preservation.')
  assert.equal(restored?.sheet.attachmentType, 'material')
  assert.equal(restored?.sheet.attachmentContent, 'Guizhou is home to many ethnic groups with rich traditions.')
  assert.equal(restored?.taskType, 'task1')
})

test('buildExamResumePreview keeps generated visual attachment url for writing workspace restore', () => {
  const restored = buildExamResumePreview({
    titleSnapshot: '折线图作文',
    topicTitle: 'Write an essay based on the line chart below.',
    promptText: [
      '题目要求（润色后必须继续严格对齐）：',
      'Write an essay based on the line chart below.',
      '图表信息：',
      'Year | Usage Rate',
      '2021 | 18%',
      '2024 | 63%',
      '字数要求：200词',
    ].join('\n'),
    sourceType: 'ai_generated',
    examType: 'postgrad',
    taskType: 'task1',
    minWords: 200,
    recommendedMaxWords: 200,
    maxScore: 100,
    attachmentImageUrl: 'https://example.com/generated-line-chart.png',
  }, 'postgrad')

  assert.ok(restored)
  assert.equal(restored?.sheet.attachmentImageUrl, 'https://example.com/generated-line-chart.png')

  const preview = buildVisualAttachmentPreview(restored?.sheet, restored?.topicInfo ?? null)
  assert.equal(preview.mode, 'image')
  assert.equal(preview.imageUrl, 'https://example.com/generated-line-chart.png')
})

test('buildExamResumePreview restores past prompt drawing image as visual attachment', () => {
  const restored = buildExamResumePreview({
    titleSnapshot: '考研英语一-大作文 2023',
    topicTitle: 'Write an essay based on the picture below.',
    promptText: [
      '题目要求（润色后必须继续严格对齐）：',
      'Write an essay based on the picture below.',
      '图画信息：',
      'A boatman throws discarded hotpot items into a river.',
      '字数要求：160-200词',
      '写作要求：Describe the picture briefly, interpret the implied meaning, and give your comments.',
    ].join('\n'),
    sourceType: 'past_prompt',
    examType: 'postgrad',
    taskType: 'task2',
    minWords: 160,
    recommendedMaxWords: 200,
    maxScore: 20,
    attachmentImageUrl: '/uploads/past-prompts/postgrad/en1/2023-task2.png',
  }, 'postgrad')

  assert.ok(restored)
  assert.equal(restored?.sheet.attachmentType, 'visual')
  assert.equal(restored?.sheet.attachmentContent, 'A boatman throws discarded hotpot items into a river.')
  assert.equal(restored?.sheet.attachmentImageUrl, '/uploads/past-prompts/postgrad/en1/2023-task2.png')

  const preview = buildVisualAttachmentPreview(restored?.sheet, restored?.topicInfo ?? null)
  assert.equal(preview.mode, 'image')
  assert.equal(preview.imageUrl, '/uploads/past-prompts/postgrad/en1/2023-task2.png')
})

test('buildExamResumePreview infers postgrad English I drawing image for legacy past prompt sessions', () => {
  const restored = buildExamResumePreview({
    titleSnapshot: '考研英语一大作文 2023',
    topicTitle: '考研英语一大作文 2023',
    promptText: [
      '题目要求（润色后必须继续严格对齐）：',
      'Write an essay based on the picture below.',
      '图画信息：',
      '（龙舟赛）。河边龙舟竞渡，岸上观众人山人海。',
      '字数要求：160-200词',
      '写作要求：Describe the picture briefly, interpret the implied meaning, and give your comments.',
    ].join('\n'),
    sourceType: 'past_prompt',
    examType: 'postgrad',
    taskType: 'task2',
    minWords: 160,
    recommendedMaxWords: 200,
    maxScore: 20,
    attachmentImageUrl: null,
  }, 'postgrad')

  assert.ok(restored)
  assert.equal(restored?.sheet.attachmentType, 'visual')
  assert.equal(restored?.sheet.attachmentImageUrl, '/uploads/past-prompts/postgrad/en1/2023-task2.png')

  const preview = buildVisualAttachmentPreview(restored?.sheet, restored?.topicInfo ?? null)
  assert.equal(preview.mode, 'image')
  assert.equal(preview.imageUrl, '/uploads/past-prompts/postgrad/en1/2023-task2.png')
})
