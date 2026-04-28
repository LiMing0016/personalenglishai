import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildDialogueTurnMessages,
  buildPreviewResultFromDialogue,
  createAssistantReplyMessages,
  createUserAssetMessage,
  createUserTextMessage,
} from './examWorkbenchConversation.ts'

test('buildDialogueTurnMessages only keeps latest user text and current active assets', () => {
  const conversation = [
    createUserTextMessage('请保留原题表述'),
    createAssistantReplyMessages([{ kind: 'understanding', text: '我会先保留原题。' }])[0],
    createUserTextMessage('我要的是图画作文，你给我配一张图'),
    createUserAssetMessage('image', {
      text: '我上传了一张题目图片',
      assetSummary: '旧图片',
      imageUrl: 'data:image/png;base64,old',
    }),
  ]

  const messages = buildDialogueTurnMessages(conversation, {
    uploadedImage: 'data:image/png;base64,new',
    materialAttachmentText: '这里是一段材料',
    materialAttachmentName: 'material.txt',
    selectedPrompt: {
      id: 1,
      paper: '2024 英语二',
      title: '图画作文',
      promptText: 'Write an essay based on the picture below.',
      examYear: 2024,
      source: 'seed',
      task: 'task2',
      imageUrl: null,
      imageDescription: null,
      materialText: null,
      wordCountMin: 160,
      wordCountMax: 200,
      maxScore: 20,
    },
  })

  assert.equal(messages.length, 4)
  assert.deepEqual(messages.map((message) => message.kind), ['text', 'asset', 'asset', 'asset'])
  assert.equal(messages[0].text, '我要的是图画作文，你给我配一张图')
  assert.equal(messages[1].assetType, 'past_prompt')
  assert.equal(messages[2].assetType, 'material')
  assert.equal(messages[3].assetType, 'image')
  assert.equal(messages[3].assetSummary, '已添加图片附件，请优先保留原图命题场景，并在信息不足时仅补全缺失要求。')
})

test('buildPreviewResultFromDialogue maps draft response into preview sheet and topic info', () => {
  const result = buildPreviewResultFromDialogue({
    previewStatus: 'draft',
    missingFields: ['待补充字数'],
    assistantReplyBlocks: [],
    promptSheetDraft: {
      promptType: 'comic',
      topic: '看图作文',
      promptText: 'Write an essay based on the picture below.',
      requirements: '1) describe 2) interpret 3) comment',
      genre: '议论文',
      wordRange: null,
      maxScore: 100,
      sourceType: 'ai_generated',
      attachmentType: null,
      attachmentTitle: null,
      attachmentContent: null,
      attachmentImageUrl: null,
      visualKind: null,
      materialText: null,
      chartSpec: null,
      comicScenes: [],
    },
  }, {
    studyStage: 'postgrad',
    selectedMode: 'exam',
    activeAssets: {
      uploadedImage: 'data:image/png;base64,image',
      materialAttachmentText: null,
      materialAttachmentName: null,
      selectedPrompt: null,
    },
  })

  assert.equal(result.previewStatus, 'draft')
  assert.deepEqual(result.missingFields, ['待补充字数'])
  assert.equal(result.previewSheet?.attachmentType, 'visual')
  assert.equal(result.previewSheet?.visualKind, 'image')
  assert.equal(result.previewTopicInfo?.promptType, 'comic')
  assert.equal(result.previewTopicInfo?.attachmentImageUrl, 'data:image/png;base64,image')
  assert.equal(result.previewTopicInfo?.examType, 'postgrad')
})

test('buildPreviewResultFromDialogue prefers generated attachment image over stale uploaded image', () => {
  const result = buildPreviewResultFromDialogue({
    previewStatus: 'ready',
    missingFields: [],
    assistantReplyBlocks: [],
    promptSheetDraft: {
      promptType: 'comic',
      topic: '看图作文',
      promptText: 'Write an essay based on the generated comic below.',
      requirements: '1) describe 2) analyze 3) comment',
      genre: '议论文',
      wordRange: '160-200',
      maxScore: 100,
      sourceType: 'ai_generated',
      attachmentType: 'visual',
      attachmentTitle: '新生成配图',
      attachmentContent: '请结合系统生成漫画完成写作。',
      attachmentImageUrl: 'https://cdn.example.com/generated-comic.png',
      attachmentSource: 'agent_generate',
      visualKind: 'comic',
      materialText: null,
      chartSpec: null,
      comicScenes: [],
    },
  }, {
    studyStage: 'postgrad',
    selectedMode: 'exam',
    activeAssets: {
      uploadedImage: 'data:image/png;base64,stale-upload',
      materialAttachmentText: null,
      materialAttachmentName: null,
      selectedPrompt: null,
    },
  })

  assert.equal(result.previewSheet?.attachmentImageUrl, 'https://cdn.example.com/generated-comic.png')
  assert.equal(result.previewTopicInfo?.attachmentImageUrl, 'https://cdn.example.com/generated-comic.png')
})
