import { expect, test, type Page } from '@playwright/test'

test.use({ storageState: { cookies: [], origins: [] } })

type CardContent = Record<string, string | string[]>
type Card = {
  cardUid: string
  displayTerm: string
  normalizedTerm: string
  templateKey: 'basic'
  status: 'ready' | 'failed' | 'needs_review'
  activeRevisionUid: string
  sourceTypes: string[]
  lastCapturedAt: string
  updatedAt: string
  candidateRevisionUid: string | null
  conflictStatus: 'none' | 'needs_review'
  language: string
  templateVersion: number
  content: CardContent
  sources: Array<Record<string, unknown>>
  generationStatus: string
  generationError: string | null
  createdAt: string
  candidateContent: CardContent | null
}

function makeCard(overrides: Partial<Card> = {}): Card {
  return {
    cardUid: 'card_ready',
    displayTerm: 'innovative',
    normalizedTerm: 'innovative',
    templateKey: 'basic',
    status: 'ready',
    activeRevisionUid: 'rev_current',
    sourceTypes: ['manual'],
    lastCapturedAt: '2026-07-11T00:00:00Z',
    updatedAt: '2026-07-11T00:00:00Z',
    candidateRevisionUid: null,
    conflictStatus: 'none',
    language: 'en',
    templateVersion: 1,
    content: {
      term: 'innovative',
      definitions: ['using new ideas or methods'],
      examples: ['The team proposed an innovative solution.'],
      notes: 'Original personal note.',
    },
    sources: [{
      sourceUid: 'source_1',
      sourceType: 'manual',
      sourceRef: null,
      sourceTitle: '产品写作笔记',
      sourceUrl: null,
      contextText: 'An innovative solution',
      rawTerm: 'innovative',
      metadata: {},
      capturedAt: '2026-07-11T00:00:00Z',
      createdAt: '2026-07-11T00:00:00Z',
    }],
    generationStatus: 'ready',
    generationError: null,
    createdAt: '2026-07-11T00:00:00Z',
    candidateContent: null,
    ...overrides,
  }
}

function collectRuntimeErrors(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(`console: ${message.text()}`)
  })
  return errors
}

async function installApiMocks(page: Page, initialCards: Card[]) {
  const cards = initialCards.map((card) => structuredClone(card))
  const requests: Array<{ method: string, path: string, body: unknown }> = []

  await page.addInitScript(() => localStorage.setItem('auth_token', 'vocabulary-e2e-token'))
  await page.route('**/users/me/profile', (route) => route.fulfill({ json: { code: '0', data: { studyStage: 'college' } } }))
  await page.route('**/v1/auth/refresh', (route) => route.fulfill({ json: { code: '0', data: { token: 'vocabulary-e2e-token' } } }))
  await page.route('**/api/vocabulary/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/\/$/, '')
    const method = route.request().method()
    const body = route.request().postDataJSON?.() ?? null
    requests.push({ method, path, body })

    if (path.endsWith('/templates')) {
      return route.fulfill({ json: { code: '0', data: { items: [{ key: 'basic', version: 1, name: '基础卡片', fields: ['term', 'definitions', 'examples', 'usage'] }], defaultTemplateKey: 'basic' } } })
    }
    if (path.endsWith('/cards') && method === 'GET') {
      return route.fulfill({ json: { code: '0', data: { items: cards, total: cards.length, page: 1, size: 20 } } })
    }
    const match = path.match(/\/cards\/([^/]+)(?:\/(.*))?$/)
    const cardUid = match?.[1]
    const suffix = match?.[2] ?? ''
    const card = cards.find((item) => item.cardUid === cardUid)

    if (!card) return route.fulfill({ status: 404, json: { code: '404', message: 'card not found', data: null } })
    if (!suffix && method === 'GET') return route.fulfill({ json: { code: '0', data: card } })
    if (suffix === 'revisions') {
      return route.fulfill({ json: { code: '0', data: {
        currentRevisionUid: card.activeRevisionUid,
        candidateRevisionUid: card.candidateRevisionUid,
        conflictStatus: card.conflictStatus,
        items: [{ revisionUid: card.activeRevisionUid, baseRevisionUid: null, authorType: 'user', templateKey: 'basic', templateVersion: 1, content: card.content, changeSummary: '创建卡片', active: true, candidate: false, createdAt: card.createdAt }],
      } } })
    }
    if (!suffix && method === 'PUT') {
      card.content = (body as { content: CardContent }).content
      return route.fulfill({ json: { code: '0', data: card } })
    }
    if (!suffix && method === 'DELETE') {
      cards.splice(cards.indexOf(card), 1)
      return route.fulfill({ json: { code: '0', data: null } })
    }
    if (suffix === 'retry' || suffix === 'regenerate') {
      return route.fulfill({ json: { code: '0', data: { jobUid: `job_${suffix}`, status: 'queued' } } })
    }
    if (suffix.startsWith('conflicts/')) {
      card.status = 'ready'
      card.conflictStatus = 'none'
      card.candidateRevisionUid = null
      card.candidateContent = null
      return route.fulfill({ json: { code: '0', data: card } })
    }
    return route.fulfill({ status: 404, json: { code: '404', message: `unmocked ${method} ${path}`, data: null } })
  })

  return { cards, requests }
}

async function expectCleanRuntime(page: Page, errors: string[]) {
  const errorToastCount = await page.locator('#toast-container > div').evaluateAll((toasts) => toasts.filter((toast) => (
    getComputedStyle(toast).backgroundColor === 'rgb(244, 67, 54)'
  )).length)
  expect(errorToastCount).toBe(0)
  const redToasts = page.locator('#toast-container > div').filter({ hasText: /失败|错误|异常/ })
  await expect(redToasts).toHaveCount(0)
  expect(errors).toEqual([])
}

for (const choice of ['keep_current', 'use_ai', 'merge_fields'] as const) {
  test(`persisted needs-review card resolves ${choice} immediately`, async ({ page }) => {
    const errors = collectRuntimeErrors(page)
    const reviewCard = makeCard({
      cardUid: `card_${choice}`,
      status: 'needs_review',
      conflictStatus: 'needs_review',
      candidateRevisionUid: 'rev_candidate',
      content: { term: 'innovative', definitions: ['current definition'], examples: ['current example'], notes: 'current note' },
      candidateContent: { term: 'innovative', definitions: ['AI definition'], usage: 'AI usage' },
    })
    const { requests } = await installApiMocks(page, [reviewCard])

    await page.goto(`/app/vocabulary/card/${reviewCard.cardUid}`)
    await expect.poll(() => requests.filter((request) => request.path.endsWith('/templates')).length).toBe(1)
    expect(errors).toEqual([])
    await expect(page.getByRole('dialog', { name: '发现版本冲突' })).toBeVisible()
    await page.getByRole('radio', { name: choice === 'keep_current' ? '保留当前内容' : choice === 'use_ai' ? '使用 AI 新版本' : '逐字段合并' }).check()

    if (choice === 'merge_fields') {
      await expect(page.getByLabel('合并释义')).toBeVisible()
      await expect(page.getByLabel('合并例句')).toBeVisible()
      await expect(page.getByLabel('合并usage')).toBeVisible()
      await expect(page.getByLabel('合并个人笔记')).toBeVisible()
      await expect(page.getByLabel('合并单词')).toHaveCount(0)
      await page.getByLabel('合并释义').selectOption('candidate')
      await page.getByLabel('合并usage').selectOption('candidate')
    }

    await page.getByRole('button', { name: '确认处理' }).click()
    await expect(page.getByRole('dialog', { name: '发现版本冲突' })).toHaveCount(0)
    const resolveRequest = requests.find((request) => request.path.includes('/conflicts/'))
    expect(resolveRequest?.body).toMatchObject({ choice })
    if (choice === 'merge_fields') {
      expect(resolveRequest?.body).toMatchObject({ mergeFields: {
        definitions: ['AI definition'],
        examples: ['current example'],
        usage: 'AI usage',
        notes: 'current note',
      } })
      expect((resolveRequest?.body as { mergeFields: CardContent }).mergeFields).not.toHaveProperty('term')
    }
    await expectCleanRuntime(page, errors)
  })
}

test('card commands, source/history tabs, cancel reset, and delete confirmation are operable', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  const failedCard = makeCard({ cardUid: 'card_failed', status: 'failed', generationStatus: 'failed', generationError: 'temporary generation failure' })
  const { requests } = await installApiMocks(page, [failedCard])
  await page.goto('/app/vocabulary/card/card_failed')

  await page.getByRole('button', { name: '编辑卡片' }).click()
  await page.getByLabel('个人笔记').fill('Unsaved note')
  await page.getByRole('button', { name: '取消编辑' }).click()
  await page.getByRole('button', { name: '编辑卡片' }).click()
  await expect(page.getByLabel('个人笔记')).toHaveValue('Original personal note.')
  await page.getByRole('button', { name: '取消编辑' }).click()

  await page.getByRole('tab', { name: '来源' }).click()
  await expect(page.getByText('产品写作笔记')).toBeVisible()
  await page.getByRole('tab', { name: '历史' }).click()
  await expect(page.getByText('创建卡片')).toBeVisible()

  await page.getByRole('button', { name: '重试生成' }).click()
  await page.getByRole('button', { name: '重新生成' }).click()
  await expect.poll(() => requests.filter((request) => request.path.endsWith('/retry')).length).toBe(1)
  await expect.poll(() => requests.filter((request) => request.path.endsWith('/regenerate')).length).toBe(1)

  await page.getByRole('button', { name: '删除' }).click()
  await expect(page.getByRole('dialog', { name: '删除单词卡？' })).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('dialog', { name: '删除单词卡？' })).toHaveCount(0)
  await page.getByRole('button', { name: '删除' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(page).toHaveURL(/\/app\/vocabulary\?tab=collection$/)
  await expect.poll(() => requests.filter((request) => request.method === 'DELETE').length).toBe(1)
  await expectCleanRuntime(page, errors)
})

for (const { name, viewport } of [
  { name: 'desktop', viewport: { width: 1440, height: 900 } },
  { name: 'mobile', viewport: { width: 390, height: 844 } },
]) {
  test(`vocabulary detail is clean and responsive on ${name}`, async ({ page }) => {
    await page.setViewportSize(viewport)
    const errors = collectRuntimeErrors(page)
    await installApiMocks(page, [makeCard()])
    await page.goto('/app/vocabulary/card/card_ready')
    await expect(page.getByRole('heading', { name: 'innovative' })).toBeVisible()

    const navButtons = page.locator('.vocabulary-nav button')
    for (let index = 0; index < await navButtons.count(); index += 1) {
      const metrics = await navButtons.nth(index).evaluate((button) => {
        const style = getComputedStyle(button)
        return { whiteSpace: style.whiteSpace, flexShrink: style.flexShrink }
      })
      expect(metrics.whiteSpace).toBe('nowrap')
      expect(metrics.flexShrink).toBe('0')
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false)
    await expectCleanRuntime(page, errors)
    await page.screenshot({ path: `test-results/vocabulary-deposition-${name}.png`, fullPage: true })
  })
}
