import type { Page, Route } from '@playwright/test';

const testDocId = 'e2e-writing-doc';

function json(route: Route, body: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

export async function mockWritingWorkspaceApis(page: Page) {
  await page.route('**/api/users/me/profile', (route) => json(route, {
    code: '0',
    message: 'OK',
    data: {
      userId: 1,
      email: 'admin01@admin.com',
      nickname: 'E2E Admin',
      studyStage: 'highschool',
      emailVerified: true,
    },
  }));

  await page.route(`**/api/docs/${testDocId}`, (route) => json(route, {
    title: 'E2E 写作练习',
    latestRevision: 1,
    content: 'Technology helps students learn English with more confidence.',
    taskPrompt: null,
    submitCount: 0,
    initialScore: null,
    latestScore: null,
    mode: 'free',
  }));

  await page.route(`**/api/writing/documents/${testDocId}/metadata`, (route) => json(route, {
    documentId: testDocId,
    metadataId: 1,
    mode: 'free',
    studyStage: 'highschool',
    titleSnapshot: 'E2E 写作练习',
    topicTitle: 'E2E 写作练习',
    promptText: null,
    attachmentImageUrl: null,
    genre: null,
    sourceType: 'free_input',
    createdAt: '2026-05-23T00:00:00Z',
    updatedAt: '2026-05-23T00:00:00Z',
    examType: null,
    taskType: null,
    minWords: null,
    recommendedMaxWords: null,
    maxScore: null,
  }));

  await page.route('**/api/writing/start-session', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    return json(route, {
      docId: testDocId,
      latestRevision: 1,
      isNew: true,
      existingContent: 'Technology helps students learn English with more confidence.',
      initialScore: null,
      latestScore: null,
      submitCount: 0,
      mode: 'free',
      writingMetadata: null,
    });
  });

  await page.route('**/api/writing/documents?**', (route) => json(route, {
    items: [],
    total: 0,
  }));

  await page.route('**/api/writing/stats', (route) => json(route, {
    avgContentQuality: null,
    avgTaskAchievement: null,
    avgStructureScore: null,
    avgVocabularyScore: null,
    avgGrammarScore: null,
    avgExpressionScore: null,
    totalGrammarErrors: 0,
    totalSpellingErrors: 0,
    totalVocabularyErrors: 0,
  }));

  await page.route('**/api/writing/stage-config/highschool', (route) => json(route, {
    stageCode: 'highschool',
    minWordCount: 60,
  }));
}
