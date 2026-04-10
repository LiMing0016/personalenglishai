import assert from 'node:assert/strict'

import { buildToolRailItems } from '../src/components/writing/toolRailState.ts'
import { buildTaskPromptPanelState } from '../src/components/writing/taskPromptPanelState.ts'

const railItems = buildToolRailItems({ showTaskPrompt: true })

assert.equal(railItems[0]?.mode, 'taskPrompt')
assert.equal(railItems[0]?.label, '题目')
assert.equal(railItems[1]?.mode, 'score')

const panelState = buildTaskPromptPanelState({
  writingMode: 'exam',
  taskPrompt: [
    '题目要求（润色后必须继续严格对齐）：',
    'Write an essay about the ethnic diversity of Guizhou Province.',
    '字数要求：300-500词',
    '写作要求：Describe the traditions and give your comments.',
  ].join('\n'),
  taskType: 'task1',
  minWords: 300,
  recommendedMaxWords: 500,
  maxScore: 100,
  studyStage: 'postgrad',
})

assert.equal(panelState.taskTypeLabel, 'Task 1')
assert.equal(panelState.promptTypeLabel, 'Expository')
assert.equal(panelState.sheet?.directions, 'Directions:')
assert.equal(panelState.sheet?.promptText, 'Write an essay about the ethnic diversity of Guizhou Province.')
assert.equal(panelState.sheet?.wordRange, '300-500')
assert.deepEqual(panelState.sheet?.requirements, ['Describe the traditions and give your comments.'])

const chartPanelState = buildTaskPromptPanelState({
  writingMode: 'exam',
  taskPrompt: [
    '题目要求（润色后必须继续严格对齐）：',
    'Write an essay based on the chart below.',
    '图表信息：',
    '贵州省最近5年新生儿增加数量与人口外流数量及出生率全国排名',
    'Year | Newborn Increase | Population Outflow | Birth Rate National Rank',
    '2018 | 50,000 | 30,000 | 15',
    '2019 | 52,000 | 32,000 | 14',
    '概括：Chart showing trends in newborn increase, population outflow, and birth rate ranking in Guizhou over the past 5 years.',
    '字数要求：200词',
  ].join('\n'),
  taskType: 'task1',
  minWords: 200,
  recommendedMaxWords: 200,
  maxScore: 100,
  studyStage: 'postgrad',
})

assert.equal(chartPanelState.sheet?.attachmentType, 'visual')
assert.equal(chartPanelState.visualPreview.mode, 'chart')
assert.deepEqual(chartPanelState.visualPreview.chartSpec?.columns, [
  'Year',
  'Newborn Increase',
  'Population Outflow',
  'Birth Rate National Rank',
])
assert.deepEqual(chartPanelState.visualPreview.chartSpec?.rows, [
  ['2018', '50,000', '30,000', '15'],
  ['2019', '52,000', '32,000', '14'],
])

const imagePanelState = buildTaskPromptPanelState({
  writingMode: 'exam',
  taskPrompt: [
    '题目要求（润色后必须继续严格对齐）：',
    'Write an essay based on the line chart below.',
    '图表信息：',
    'Year | Usage Rate',
    '2021 | 18%',
    '2024 | 63%',
    '字数要求：200词',
  ].join('\n'),
  taskType: 'task1',
  minWords: 200,
  recommendedMaxWords: 200,
  maxScore: 100,
  studyStage: 'postgrad',
  attachmentImageUrl: 'https://example.com/generated-line-chart.png',
})

assert.equal(imagePanelState.sheet?.attachmentImageUrl, 'https://example.com/generated-line-chart.png')
assert.equal(imagePanelState.visualPreview.mode, 'image')
assert.equal(imagePanelState.visualPreview.imageUrl, 'https://example.com/generated-line-chart.png')

console.log('writing-workspace-ui-state-ok')
