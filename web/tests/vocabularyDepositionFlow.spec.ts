import { expect, test, type Page } from '@playwright/test'

test.use({ storageState: { cookies: [], origins: [] } })

type CardContent = Record<string, unknown>
type VocabularyCore = {
  schemaVersion: 1
  term: string
  phonetics: Array<{ region: 'uk' | 'us' | 'other', text: string, audioUrl: string | null }>
  senses: Array<{
    partOfSpeech: string
    meanings: Array<{ definitionEn: string, definitionZh: string }>
  }>
}
type Theme = {
  themeUid: string
  ownerType: 'system' | 'user'
  name: string
  purpose: string
  version: number
  status: 'active' | 'disabled'
  system: boolean
  defaultTheme: boolean
  recent: boolean
  promptStrategyKey: string
}
type ConflictPayload = {
  currentRevisionUid: string
  candidateRevisionUid: string
  currentContent: CardContent
  candidateContent: CardContent
  currentContentFormatVersion: number | null
  candidateContentFormatVersion: number | null
  conflictStatus: 'needs_review'
}
type DetailStatusSequences = Record<string, number[]>
type DetailCardSequences = Record<string, Card[]>
type Card = {
  cardUid: string
  displayTerm: string
  normalizedTerm: string
  templateKey: 'basic'
  status: 'captured' | 'generating' | 'ready' | 'failed' | 'needs_review'
  activeRevisionUid: string | null
  sourceTypes: string[]
  lastCapturedAt: string
  updatedAt: string
  candidateRevisionUid: string | null
  conflictStatus: 'none' | 'needs_review'
  phonetic: string | null
  coreDefinition: string | null
  sourceCount: number
  language: string
  templateVersion: number
  content: CardContent
  theme: { themeUid: string, name: string, purpose: string } | null
  themeVersion: number | null
  core: VocabularyCore | null
  markdown: string | null
  contentFormatVersion: number | null
  sources: Array<Record<string, unknown>>
  generationStatus: string
  generationError: string | null
  generationOutcome: 'complete' | 'partial' | 'failed' | null
  warning: string | null
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
    phonetic: '/in/',
    coreDefinition: 'using new ideas or methods',
    sourceCount: 1,
    language: 'en',
    templateVersion: 1,
    content: {
      term: 'innovative',
      definitions: ['using new ideas or methods'],
      examples: ['The team proposed an innovative solution.'],
      notes: 'Original personal note.',
    },
    theme: { themeUid: 'theme_system_general', name: '通用积累', purpose: '通用词汇积累' },
    themeVersion: 1,
    core: {
      schemaVersion: 1,
      term: 'innovative',
      phonetics: [{ region: 'uk', text: '/ˈɪnəveɪtɪv/', audioUrl: null }],
      senses: [{
        partOfSpeech: 'adjective',
        meanings: [{ definitionEn: 'using new ideas or methods', definitionZh: '创新的' }],
      }],
    },
    markdown: '## 学习提示\n\nUse it for products, methods, and solutions.',
    contentFormatVersion: 1,
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
    generationOutcome: 'complete',
    warning: null,
    createdAt: '2026-07-11T00:00:00Z',
    candidateContent: null,
    ...overrides,
  }
}

function collectRuntimeErrors(page: Page, ignoredHttpStatuses: number[] = []) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    const text = message.text()
    const ignoredHttpError = ignoredHttpStatuses.some((status) => text.includes(`status of ${status}`))
    if (message.type() === 'error' && !ignoredHttpError) errors.push(`console: ${text}`)
  })
  return errors
}

async function installApiMocks(
  page: Page,
  initialCards: Card[],
  updateConflicts: Record<string, ConflictPayload> = {},
  initialUserThemes: Theme[] = [],
  detailStatusSequences: DetailStatusSequences = {},
  detailCardSequences: DetailCardSequences = {},
) {
  const cards = initialCards.map((card) => structuredClone(card))
  const systemThemes: Theme[] = [{
    themeUid: 'theme_system_general', ownerType: 'system', name: '通用积累', purpose: '通用词汇积累',
    version: 1, status: 'active', system: true, defaultTheme: initialUserThemes.every((theme) => !theme.defaultTheme),
    recent: false, promptStrategyKey: 'general-markdown-v1',
  }]
  const userThemes = initialUserThemes.map((theme) => structuredClone(theme))
  let defaultThemeUid = userThemes.find((theme) => theme.defaultTheme)?.themeUid ?? 'theme_system_general'
  let recentThemeUids = userThemes.filter((theme) => theme.recent).map((theme) => theme.themeUid)
  const requests: Array<{ method: string, path: string, body: unknown }> = []
  const detailAttempts = new Map<string, number>()

  await page.addInitScript(() => localStorage.setItem('auth_token', 'vocabulary-e2e-token'))
  await page.route('**/users/me/profile', (route) => route.fulfill({ json: { code: '0', data: { studyStage: 'college' } } }))
  await page.route('**/v1/auth/refresh', (route) => route.fulfill({ json: { code: '0', data: { token: 'vocabulary-e2e-token' } } }))
  await page.route('**/api/vocabulary/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/\/$/, '')
    const method = route.request().method()
    const body = route.request().postDataJSON?.() ?? null
    requests.push({ method, path, body })

    if (path.endsWith('/themes') && method === 'GET') {
      return route.fulfill({ json: { code: '0', data: {
        systemThemes,
        userThemes,
        defaultThemeUid,
        recentThemeUids,
      } } })
    }
    if (path.endsWith('/themes') && method === 'POST') {
      const payload = body as { name: string, purpose: string }
      const theme: Theme = {
        themeUid: `theme_user_${userThemes.length + 1}`,
        ownerType: 'user',
        name: payload.name,
        purpose: payload.purpose,
        version: 1,
        status: 'active',
        system: false,
        defaultTheme: false,
        recent: false,
        promptStrategyKey: 'custom-markdown-v1',
      }
      userThemes.push(theme)
      return route.fulfill({ json: { code: '0', data: theme } })
    }
    const themeMatch = path.match(/\/themes\/([^/]+)(?:\/(.*))?$/)
    if (themeMatch) {
      const theme = userThemes.find((item) => item.themeUid === themeMatch[1])
      if (!theme) return route.fulfill({ status: 404, json: { code: '404', message: 'theme not found', data: null } })
      const suffix = themeMatch[2] ?? ''
      if (!suffix && method === 'PUT') {
        const payload = body as { name: string, purpose: string }
        theme.name = payload.name
        theme.purpose = payload.purpose
        theme.version += 1
        return route.fulfill({ json: { code: '0', data: theme } })
      }
      if (suffix === 'default' && method === 'POST') {
        for (const item of [...systemThemes, ...userThemes]) item.defaultTheme = item.themeUid === theme.themeUid
        defaultThemeUid = theme.themeUid
        recentThemeUids = [theme.themeUid, ...recentThemeUids.filter((uid) => uid !== theme.themeUid)]
        theme.recent = true
        return route.fulfill({ json: { code: '0', data: null } })
      }
    }
    if (path.endsWith('/captures') && method === 'POST') {
      const payload = body as { terms: string[], themeUid: string }
      const selectedTheme = [...systemThemes, ...userThemes].find((theme) => theme.themeUid === payload.themeUid)!
      const items = payload.terms.map((term, index) => {
        const cardUid = `card_capture_${index + 1}`
        cards.push(makeCard({
          cardUid,
          displayTerm: term,
          normalizedTerm: term,
          status: 'generating',
          activeRevisionUid: '',
          theme: {
            themeUid: selectedTheme.themeUid,
            name: selectedTheme.name,
            purpose: selectedTheme.purpose,
          },
          themeVersion: selectedTheme.version,
          core: null,
          markdown: null,
          contentFormatVersion: null,
          generationStatus: 'pending',
        }))
        return { term, cardUid, action: 'created', status: 'generating' }
      })
      return route.fulfill({ json: { code: '0', data: { items } } })
    }
    if (path.endsWith('/cards') && method === 'GET') {
      return route.fulfill({ json: { code: '0', data: { items: cards, total: cards.length, page: 1, size: 20 } } })
    }
    const match = path.match(/\/cards\/([^/]+)(?:\/(.*))?$/)
    const cardUid = match?.[1]
    const suffix = match?.[2] ?? ''
    const card = cards.find((item) => item.cardUid === cardUid)

    if (!suffix && method === 'GET' && cardUid) {
      const attempt = detailAttempts.get(cardUid) ?? 0
      detailAttempts.set(cardUid, attempt + 1)
      const sequence = detailStatusSequences[cardUid]
      const status = sequence?.[Math.min(attempt, sequence.length - 1)] ?? 200
      if (status !== 200) {
        return route.fulfill({ status, json: { code: String(status), message: `detail failed with ${status}`, data: null } })
      }
      if (!card) return route.fulfill({ status: 404, json: { code: '404', message: 'card not found', data: null } })
      const cardSequence = detailCardSequences[cardUid]
      const cardSnapshot = cardSequence?.[Math.min(attempt, cardSequence.length - 1)]
      if (cardSnapshot) Object.assign(card, structuredClone(cardSnapshot))
      return route.fulfill({ json: { code: '0', data: card } })
    }
    if (!card) return route.fulfill({ status: 404, json: { code: '404', message: 'card not found', data: null } })
    if (suffix === 'revisions') {
      return route.fulfill({ json: { code: '0', data: {
        currentRevisionUid: card.activeRevisionUid,
        candidateRevisionUid: card.candidateRevisionUid,
        conflictStatus: card.conflictStatus,
        items: card.activeRevisionUid ? [{
          revisionUid: card.activeRevisionUid, baseRevisionUid: null, authorType: 'user',
          templateKey: 'basic', templateVersion: 1, content: card.content,
          theme: card.theme, themeVersion: card.themeVersion, core: card.core, markdown: card.markdown,
          contentFormatVersion: card.contentFormatVersion, changeSummary: '创建卡片', active: true,
          candidate: false, createdAt: card.createdAt,
        }] : [],
      } } })
    }
    if (!suffix && method === 'PUT') {
      const updateConflict = updateConflicts[card.cardUid]
      if (updateConflict) {
        return route.fulfill({
          status: 409,
          json: { code: '409030', message: 'revision conflict', data: updateConflict },
        })
      }
      const payload = body as { content?: CardContent, core?: VocabularyCore, markdown?: string }
      if (payload.content) card.content = payload.content
      if (payload.core) card.core = payload.core
      if (payload.markdown != null) card.markdown = payload.markdown
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

  const requestCount = (method: string, pathSuffix: string) => requests.filter((request) => (
    request.method === method && request.path.endsWith(pathSuffix)
  )).length

  return { cards, requests, requestCount, systemThemes, userThemes }
}

function makeUserTheme(overrides: Partial<Theme> = {}): Theme {
  return {
    themeUid: 'theme_user_product',
    ownerType: 'user',
    name: '产品英语',
    purpose: '为产品方案积累准确、自然的表达。',
    version: 1,
    status: 'active',
    system: false,
    defaultTheme: false,
    recent: true,
    promptStrategyKey: 'custom-markdown-v1',
    ...overrides,
  }
}

test('stale v1 conflict uses the 409 current format when the revision cache misses it', async ({ page }) => {
  const errors = collectRuntimeErrors(page, [409])
  const staleCard = makeCard({ cardUid: 'card_stale_v1', activeRevisionUid: 'rev_stale_base' })
  const currentCore = { schemaVersion: 1, term: 'innovative', phonetics: [], senses: [] }
  const candidateCore = {
    schemaVersion: 1,
    term: 'innovative',
    phonetics: [],
    senses: [{ partOfSpeech: 'adjective', meanings: [{ definitionEn: 'new', definitionZh: '创新的' }] }],
  }
  const { requests } = await installApiMocks(page, [staleCard], {
    card_stale_v1: {
      currentRevisionUid: 'rev_server_current',
      candidateRevisionUid: 'rev_stale_candidate',
      currentContent: { ...currentCore, markdown: '# Current' },
      candidateContent: { ...candidateCore, markdown: '# Candidate' },
      currentContentFormatVersion: 1,
      candidateContentFormatVersion: 1,
      conflictStatus: 'needs_review',
    },
  })

  await page.goto('/app/vocabulary/cards/card_stale_v1')
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByRole('button', { name: '保存修改' }).click()

  const dialog = page.getByRole('dialog', { name: '发现版本冲突' })
  await expect(dialog).toBeVisible()
  await expect(page.locator('.card-inspector__save-announcement')).toHaveText('保存失败，请重试')
  await expect(dialog.getByText('当前 Markdown')).toBeVisible()
  await dialog.getByRole('radio', { name: '组合核心数据与 Markdown' }).check()
  await dialog.getByRole('button', { name: '确认处理' }).click()

  const resolveRequest = requests.find((request) => request.path.includes('/conflicts/rev_stale_candidate/'))
  expect(resolveRequest?.body).toMatchObject({
    choice: 'merge_fields',
    mergeFields: { core: currentCore, markdown: '# Current' },
  })
  await expectCleanRuntime(page, errors)
})

async function expectCleanRuntime(page: Page, errors: string[]) {
  const errorToastCount = await page.locator('#toast-container > div').evaluateAll((toasts) => toasts.filter((toast) => (
    getComputedStyle(toast).backgroundColor === 'rgb(244, 67, 54)'
  )).length)
  expect(errorToastCount).toBe(0)
  const redToasts = page.locator('#toast-container > div').filter({ hasText: /失败|错误|异常/ })
  await expect(redToasts).toHaveCount(0)
  await expect(page.getByText(/AI output failed structured validation|stack trace|java\.lang\.|AxiosError/i)).toHaveCount(0)
  expect(errors).toEqual([])
}

async function expectNoHorizontalOverflow(page: Page) {
  await expect.poll(() => page.evaluate(() => ({
    document: document.documentElement.scrollWidth <= window.innerWidth,
    body: document.body.scrollWidth <= window.innerWidth,
  }))).toEqual({ document: true, body: true })
}

test('creates a custom default theme, selects it from the shelf, and captures two words', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  const { requests } = await installApiMocks(page, [])

  await page.goto('/app/vocabulary/themes')
  await page.getByRole('button', { name: '新建主题' }).click()
  const dialog = page.getByRole('dialog', { name: '新建主题' })
  await dialog.getByText('主题名称').locator('..').getByRole('textbox').fill('产品英语')
  await dialog.getByText('用途说明').locator('..').getByRole('textbox').fill('为产品方案积累准确、自然的表达。')
  await dialog.getByRole('button', { name: '保存主题' }).click()

  const themeCard = page.getByRole('article').filter({ hasText: '产品英语' })
  await expect(themeCard).toContainText('为产品方案积累准确、自然的表达。')
  await themeCard.getByRole('button', { name: '设为默认' }).click()
  await expect(themeCard.getByText('默认')).toBeVisible()

  await page.goto('/app/vocabulary?tab=collection')
  const shelfTheme = page.getByRole('button', { name: /产品英语.*默认主题/ })
  await expect(shelfTheme).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('textbox', { name: '批量录入单词' }).fill('resilient\npragmatic')
  await page.getByRole('button', { name: '按「产品英语」生成 2 张卡片' }).click()
  await expect(page).toHaveURL(/\/app\/vocabulary\/cards\/card_capture_1$/)
  await expect(page.locator('.card-inspector__heading h2')).toHaveText('resilient')

  const captureRequest = requests.find((request) => request.path.endsWith('/captures'))
  expect(captureRequest?.body).toMatchObject({
    terms: ['resilient', 'pragmatic'],
    themeUid: 'theme_user_1',
  })
  await expectNoHorizontalOverflow(page)
  await expectCleanRuntime(page, errors)
})

test('editing a theme freezes the old card version and regeneration uses the latest version', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  const oldTheme = makeUserTheme()
  const oldCard = makeCard({
    cardUid: 'card_theme_v1',
    displayTerm: 'resilient',
    normalizedTerm: 'resilient',
    theme: { themeUid: oldTheme.themeUid, name: oldTheme.name, purpose: oldTheme.purpose },
    themeVersion: 1,
    core: {
      schemaVersion: 1,
      term: 'resilient',
      phonetics: [],
      senses: [{ partOfSpeech: 'adjective', meanings: [{ definitionEn: 'able to recover quickly', definitionZh: '有韧性的' }] }],
    },
    markdown: '## v1 学习提示\n\nUse the original product-writing guidance.',
  })
  const { requests } = await installApiMocks(page, [oldCard], {}, [oldTheme])

  await page.goto('/app/vocabulary/themes')
  const themeCard = page.getByRole('article').filter({ hasText: '产品英语' })
  await themeCard.getByRole('button', { name: '编辑' }).click()
  const dialog = page.getByRole('dialog', { name: '编辑主题' })
  await dialog.getByText('用途说明').locator('..').getByRole('textbox').fill('聚焦产品发布、用户研究和路线图表达。')
  await dialog.getByRole('button', { name: '保存主题' }).click()
  await expect(themeCard).toContainText('聚焦产品发布、用户研究和路线图表达。')

  await page.goto('/app/vocabulary/cards/card_theme_v1')
  await expect(page.getByText('产品英语 · v1')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'v1 学习提示' })).toBeVisible()
  await expect(page.getByLabel('Markdown 内容')).toHaveCount(0)
  await page.getByRole('button', { name: '重新生成', exact: true }).click()
  const confirmation = page.getByRole('dialog', { name: '使用最新主题版本？' })
  await expect(confirmation).toContainText('当前版本会保留在历史中')
  await confirmation.getByRole('button', { name: '确认重新生成' }).click()

  expect(requests.find((request) => request.path.endsWith('/regenerate'))?.body).toEqual({
    themeUid: oldTheme.themeUid,
    useLatestThemeVersion: true,
  })
  await expect(page.getByText('产品英语 · v1')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'v1 学习提示' })).toBeVisible()
  await expectCleanRuntime(page, errors)
})

test('Markdown generation failure keeps core content and a reviewable user-facing state', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  const partialCard = makeCard({
    cardUid: 'card_partial_markdown',
    displayTerm: 'pragmatic',
    normalizedTerm: 'pragmatic',
    status: 'needs_review',
    generationStatus: 'succeeded',
    generationError: null,
    generationOutcome: 'partial',
    warning: 'markdown_unavailable',
    core: {
      schemaVersion: 1,
      term: 'pragmatic',
      phonetics: [{ region: 'uk', text: '/præɡˈmætɪk/', audioUrl: null }],
      senses: [{ partOfSpeech: 'adjective', meanings: [{ definitionEn: 'dealing with problems practically', definitionZh: '务实的' }] }],
    },
    markdown: null,
  })
  const { requests } = await installApiMocks(page, [partialCard])

  await page.goto('/app/vocabulary/cards/card_partial_markdown')
  await expect(page.locator('.card-inspector').getByText('待确认')).toBeVisible()
  await expect(page.getByText('dealing with problems practically')).toBeVisible()
  await expect(page.getByText('务实的')).toBeVisible()
  await expect(page.getByLabel('Markdown 内容')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '主题内容待完善' })).toBeVisible()
  await page.getByLabel('主题内容').getByRole('button', { name: '重新生成' }).click()
  await expect.poll(() => requests.filter((request) => request.path.endsWith('/regenerate')).length).toBe(1)
  await expectNoHorizontalOverflow(page)
  await expectCleanRuntime(page, errors)
})

test('legacy basic card remains readable and regenerates into the themed format', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  const legacyCard = makeCard({
    cardUid: 'card_legacy_basic',
    displayTerm: 'legacy',
    normalizedTerm: 'legacy',
    theme: null,
    themeVersion: null,
    core: null,
    markdown: null,
    contentFormatVersion: null,
    content: {
      term: 'legacy',
      phonetic: '/ˈleɡəsi/',
      partOfSpeech: 'noun',
      definitions: ['something handed down from the past'],
      examples: ['The system preserves its legacy cards.'],
    },
  })
  const { requests } = await installApiMocks(page, [legacyCard])

  await page.goto('/app/vocabulary/cards/card_legacy_basic')
  await expect(page.getByText('兼容卡片')).toBeVisible()
  await expect(page.locator('.card-inspector__heading h2')).toHaveText('legacy')
  await expect(page.getByText('something handed down from the past')).toBeVisible()
  await page.getByRole('button', { name: '重新生成', exact: true }).click()
  const confirmation = page.getByRole('dialog', { name: '使用最新主题版本？' })
  await confirmation.getByRole('button', { name: '确认重新生成' }).click()

  expect(requests.find((request) => request.path.endsWith('/regenerate'))?.body).toEqual({
    themeUid: 'theme_system_general',
    useLatestThemeVersion: true,
  })
  await expectCleanRuntime(page, errors)
})

test('hard refresh on a persisted card route renders detail only', async ({ page }) => {
  const { requestCount } = await installApiMocks(page, [makeCard()])

  await page.goto('/app/vocabulary/cards/card_ready')
  await page.reload()

  await expect(page.locator('.card-inspector__heading h2')).toHaveText('innovative')
  const chapters = page.getByRole('navigation', { name: '单词卡章节' })
  await expect(chapters).toBeVisible()
  await expect(chapters.getByRole('button', { name: '学习提示' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '学习提示' })).toBeVisible()
  await expect(page.getByLabel('Markdown 内容')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '单词卡中心' })).toHaveCount(0)
  await expect(page.getByRole('textbox', { name: '批量录入单词' })).toHaveCount(0)
  expect(requestCount('GET', '/cards/card_ready')).toBeGreaterThanOrEqual(1)
  expect(requestCount('GET', '/templates')).toBe(0)
})

test('persisted card navigation loads the next selected card', async ({ page }) => {
  const first = makeCard({ cardUid: 'card_first', displayTerm: 'first', normalizedTerm: 'first' })
  const second = makeCard({ cardUid: 'card_second', displayTerm: 'second', normalizedTerm: 'second' })
  const { requestCount } = await installApiMocks(page, [first, second])

  await page.goto('/app/vocabulary?tab=collection')
  await page.getByRole('listitem').filter({ hasText: 'first' }).click()
  await expect(page.locator('.card-inspector__heading h2')).toHaveText('first')
  await page.getByRole('button', { name: '返回单词库' }).click()
  await page.getByRole('listitem').filter({ hasText: 'second' }).click()
  await expect(page.locator('.card-inspector__heading h2')).toHaveText('second')

  expect(requestCount('GET', '/cards/card_first')).toBe(1)
  expect(requestCount('GET', '/cards/card_second')).toBe(1)
})

test('legacy word URL stays keyword-filtered collection and does not fetch detail', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  const { requestCount } = await installApiMocks(page, [makeCard()])

  await page.goto('/app/vocabulary/cards/supposed')
  await expect(page).toHaveURL(/\/app\/vocabulary\/cards\/supposed$/)
  await expect(page.getByRole('heading', { name: '单词卡中心' })).toBeVisible()
  await expect(page.getByRole('searchbox', { name: '搜索单词' })).toHaveValue('supposed')
  await expect(page.locator('.vocabulary-card-page')).toHaveCount(0)
  await expect.poll(() => requestCount('GET', '/cards')).toBe(1)
  expect(requestCount('GET', '/cards/supposed')).toBe(0)
  expect(requestCount('GET', '/cards/supposed/revisions')).toBe(0)
  await expectCleanRuntime(page, errors)
})

for (const detailError of [
  { status: 403, cardUid: 'card_forbidden', message: '无权查看这张单词卡' },
  { status: 404, cardUid: 'card_missing', message: '单词卡不存在或已被删除' },
]) {
  test(`${detailError.status} card detail is terminal and does not request revisions`, async ({ page }) => {
    const card = makeCard({ cardUid: detailError.cardUid })
    const { requestCount } = await installApiMocks(page, [card], {}, [], {
      [detailError.cardUid]: [detailError.status],
    })

    await page.goto(`/app/vocabulary/cards/${detailError.cardUid}`)

    const alert = page.getByRole('alert')
    await expect(alert).toContainText(detailError.message)
    await expect(alert.getByRole('button', { name: '重试' })).toHaveCount(0)
    await expect(alert.getByRole('button', { name: '返回单词库' })).toBeVisible()
    expect(requestCount('GET', `/cards/${detailError.cardUid}`)).toBe(1)
    expect(requestCount('GET', `/cards/${detailError.cardUid}/revisions`)).toBe(0)
  })
}

test('generic detail failure can retry and recover', async ({ page }) => {
  const card = makeCard({ cardUid: 'card_transient' })
  const { requestCount } = await installApiMocks(page, [card], {}, [], {
    card_transient: [500, 500, 500, 500, 200],
  })

  await page.goto('/app/vocabulary/cards/card_transient')

  const alert = page.getByRole('alert')
  await expect(alert).toContainText('单词卡详情加载失败', { timeout: 15_000 })
  const attemptsBeforeRetry = requestCount('GET', '/cards/card_transient')
  expect(attemptsBeforeRetry).toBe(4)
  await alert.getByRole('button', { name: '重试' }).click()
  await expect(page.locator('.card-inspector__heading h2')).toHaveText('innovative')
  expect(requestCount('GET', '/cards/card_transient')).toBe(attemptsBeforeRetry + 1)
})

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
      theme: null,
      themeVersion: null,
      core: null,
      markdown: null,
      contentFormatVersion: null,
    })
    const { requests } = await installApiMocks(page, [reviewCard])

    await page.goto(`/app/vocabulary/cards/${reviewCard.cardUid}`)
    await expect(page.getByRole('dialog', { name: '发现版本冲突' })).toBeVisible()
    expect(requests.filter((request) => request.path.endsWith('/templates'))).toEqual([])
    expect(errors).toEqual([])
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

test('notebook commands, source/history chapters, Markdown save/cancel, and delete confirmation are operable', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  const failedCard = makeCard({ cardUid: 'card_failed', status: 'failed', generationStatus: 'failed', generationError: 'temporary generation failure' })
  const { requests } = await installApiMocks(page, [failedCard])
  await page.goto('/app/vocabulary/cards/card_failed')

  await expect(page.getByText('本次生成失败，当前内容未受影响')).toBeVisible()
  await expect(page.getByRole('button', { name: '重试生成' })).toHaveCount(1)
  await expect(page.getByLabel('Markdown 内容')).toHaveCount(0)
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByRole('button', { name: '重新生成', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '重试生成' })).toHaveCount(0)
  await page.getByLabel('Markdown 内容').fill('Unsaved Markdown')
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByLabel('Markdown 内容')).toHaveCount(0)
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByLabel('Markdown 内容')).toHaveValue(/学习提示/)
  await page.getByLabel('Markdown 内容').fill('## 保存后的标题\n\nSaved body.')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.getByLabel('Markdown 内容')).toHaveCount(0)
  const saveAnnouncement = page.locator('.card-inspector__save-announcement')
  await expect(saveAnnouncement).toHaveAttribute('aria-live', 'polite')
  await expect(saveAnnouncement).toHaveText('单词卡已保存')
  await expect(page.getByRole('heading', { name: '保存后的标题' })).toBeVisible()

  const chapters = page.getByRole('navigation', { name: '单词卡章节' })
  const sourceSection = page.getByLabel('单词卡来源')
  const sourceTopBeforeNavigation = await sourceSection.evaluate((section) => section.getBoundingClientRect().top)
  const sourceChapter = chapters.getByRole('button', { name: '来源' })
  await sourceChapter.click()
  await expect(sourceChapter).toHaveAttribute('aria-current', 'location')
  await expect.poll(() => sourceSection.evaluate((section) => section.getBoundingClientRect().top)).toBeLessThan(sourceTopBeforeNavigation)
  await expect(page.getByText('产品写作笔记')).toBeVisible()
  const historySection = page.getByLabel('单词卡修订历史')
  const historyTopBeforeNavigation = await historySection.evaluate((section) => section.getBoundingClientRect().top)
  const scrollYBeforeHistoryNavigation = await page.evaluate(() => window.scrollY)
  const historyChapter = chapters.getByRole('button', { name: '历史' })
  await historyChapter.click()
  await expect.poll(() => historySection.evaluate((section) => section.getBoundingClientRect().top)).toBeLessThan(historyTopBeforeNavigation)
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(scrollYBeforeHistoryNavigation)
  await expect(page.getByText('创建卡片')).toBeVisible()
  await expect(page.getByRole('tab')).toHaveCount(0)

  await page.getByRole('button', { name: '重试生成' }).click()
  await page.getByRole('button', { name: '重新生成', exact: true }).click()
  await expect.poll(() => requests.filter((request) => request.path.endsWith('/retry')).length).toBe(1)
  await expect.poll(() => requests.filter((request) => request.path.endsWith('/regenerate')).length).toBe(1)
  expect(requests.find((request) => request.path.endsWith('/regenerate'))?.body).toEqual({
    themeUid: 'theme_system_general',
    useLatestThemeVersion: true,
  })

  await page.getByRole('button', { name: '删除' }).click()
  await expect(page.getByRole('dialog', { name: '删除单词卡？' })).toBeVisible()
  await expect(page.getByText('再次收藏或录入时可恢复')).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('dialog', { name: '删除单词卡？' })).toHaveCount(0)
  await page.getByRole('button', { name: '删除' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(page).toHaveURL(/\/app\/vocabulary\?tab=collection$/)
  await expect.poll(() => requests.filter((request) => request.method === 'DELETE').length).toBe(1)
  await expectCleanRuntime(page, errors)
})

test('generation states do not present missing revisions as readable documents', async ({ page }) => {
  const captured = makeCard({
    cardUid: 'card_captured_empty',
    displayTerm: 'captured',
    normalizedTerm: 'captured',
    status: 'captured',
    activeRevisionUid: null,
    generationStatus: 'captured',
    generationOutcome: null,
    core: null,
    markdown: null,
    content: {},
    sources: [],
  })
  const generating = makeCard({
    cardUid: 'card_generating_empty',
    displayTerm: 'pending',
    normalizedTerm: 'pending',
    status: 'generating',
    activeRevisionUid: null,
    generationStatus: 'pending',
    generationOutcome: null,
    core: null,
    markdown: null,
    content: {},
    sources: [],
  })
  const regenerating = makeCard({
    cardUid: 'card_regenerating',
    status: 'generating',
    generationStatus: 'running',
  })
  const failedEmpty = makeCard({
    cardUid: 'card_failed_empty',
    displayTerm: 'unavailable',
    normalizedTerm: 'unavailable',
    status: 'failed',
    activeRevisionUid: null,
    generationStatus: 'failed',
    generationOutcome: 'failed',
    core: null,
    markdown: null,
    content: {},
    sources: [],
  })
  await installApiMocks(page, [captured, generating, regenerating, failedEmpty])

  await page.goto('/app/vocabulary/cards/card_captured_empty')
  await expect(page.locator('.card-inspector__heading')).toContainText('captured')
  await expect(page.getByRole('status')).toHaveAttribute('aria-live', 'polite')
  await expect(page.getByRole('status')).toHaveText('正在生成单词卡')
  await expect(page.getByRole('navigation', { name: '单词卡章节' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toBeDisabled()

  await page.goto('/app/vocabulary/cards/card_generating_empty')
  await expect(page.getByRole('status')).toHaveText('正在生成单词卡')
  await expect(page.getByText('正在生成单词卡', { exact: true })).toHaveCount(1)
  await expect(page.getByRole('navigation', { name: '单词卡章节' })).toHaveCount(0)
  await expect(page.getByText('暂无释义')).toHaveCount(0)

  await page.goto('/app/vocabulary/cards/card_regenerating')
  await expect(page.getByRole('status')).toHaveText('正在生成新版本，当前内容可继续阅读')
  await expect(page.getByRole('heading', { name: '学习提示' })).toBeVisible()

  await page.goto('/app/vocabulary/cards/card_failed_empty')
  await expect(page.getByRole('status')).toHaveText('暂时没有可阅读的卡片内容')
  await expect(page.getByText('暂时没有可阅读的卡片内容', { exact: true })).toHaveCount(1)
  await expect(page.getByRole('navigation', { name: '单词卡章节' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '重试生成' })).toHaveCount(1)
})

test('generating card live region yields to readable content after polling ready detail', async ({ page }) => {
  const generating = makeCard({
    cardUid: 'card_transition',
    displayTerm: 'eventual',
    normalizedTerm: 'eventual',
    status: 'generating',
    activeRevisionUid: null,
    generationStatus: 'running',
    generationOutcome: null,
    core: null,
    markdown: null,
    content: {},
    sources: [],
  })
  const ready = makeCard({
    cardUid: 'card_transition',
    displayTerm: 'eventual',
    normalizedTerm: 'eventual',
    status: 'ready',
    activeRevisionUid: 'rev_transition_ready',
    generationStatus: 'ready',
    core: {
      schemaVersion: 1,
      term: 'eventual',
      phonetics: [],
      senses: [{
        partOfSpeech: 'adjective',
        meanings: [{ definitionEn: 'happening at the end', definitionZh: '最终的' }],
      }],
    },
    markdown: '## 生成完成后的主题内容\n\nThe eventual result is readable.',
    content: {},
    sources: [],
  })
  const { requestCount } = await installApiMocks(page, [generating], {}, [], {}, {
    card_transition: [generating, ready],
  })

  await page.goto('/app/vocabulary/cards/card_transition')
  const generationLiveRegion = page.getByRole('status')
  await expect(generationLiveRegion).toHaveAttribute('aria-live', 'polite')
  await expect(generationLiveRegion).toHaveText('正在生成单词卡')
  await expect(page.getByRole('navigation', { name: '单词卡章节' })).toHaveCount(0)

  await expect.poll(() => requestCount('GET', '/cards/card_transition'), { timeout: 8_000 }).toBeGreaterThanOrEqual(2)
  await expect(generationLiveRegion).toHaveCount(0)
  await expect(page.getByLabel('eventual').getByRole('heading', { name: 'eventual' })).toBeVisible()
  await expect(page.getByText('happening at the end')).toBeVisible()
  await expect(page.getByRole('heading', { name: '生成完成后的主题内容' })).toBeVisible()
  await expect(page.getByText('The eventual result is readable.')).toBeVisible()
})

test('mobile More owns theme and delete controls and closes across navigation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const first = makeCard({ cardUid: 'card_mobile_first', displayTerm: 'first', normalizedTerm: 'first' })
  const second = makeCard({ cardUid: 'card_mobile_second', displayTerm: 'second', normalizedTerm: 'second' })
  await installApiMocks(page, [first, second])
  await page.goto('/app/vocabulary/cards/card_mobile_first')

  const more = page.getByRole('button', { name: '更多单词卡操作' })
  await expect(more).toHaveAttribute('aria-expanded', 'false')
  await expect(page.getByLabel('重新生成主题')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '删除', exact: true })).toHaveCount(0)
  await more.click()
  await expect(more).toHaveAttribute('aria-expanded', 'true')
  await expect(page.getByLabel('重新生成主题')).toBeVisible()
  await expect(page.getByRole('button', { name: '删除', exact: true })).toBeVisible()
  await page.getByLabel('重新生成主题').focus()
  await page.keyboard.press('Escape')
  await expect(more).toHaveAttribute('aria-expanded', 'false')
  await expect(more).toBeFocused()

  await more.click()
  await page.getByRole('button', { name: '返回单词库' }).click()
  await page.getByRole('listitem').filter({ hasText: 'second' }).click()
  await expect(page.locator('.card-inspector__heading h2')).toHaveText('second')
  await expect(page.getByRole('button', { name: '更多单词卡操作' })).toHaveAttribute('aria-expanded', 'false')
  await expect(page.getByLabel('重新生成主题')).toHaveCount(0)
})

test('vocabulary detail remains readable across the required responsive viewports', async ({ page }) => {
  const errors = collectRuntimeErrors(page)
  await installApiMocks(page, [makeCard({
    markdown: [
      '## A deliberately long contextual explanation chapter',
      '',
      'Context body.',
      '',
      '## A deliberately long examples and counterexamples chapter',
      '',
      'Examples body.',
      '',
      '## A deliberately long memory and review guidance chapter',
      '',
      'Review body.',
    ].join('\n'),
  })])
  await page.goto('/app/vocabulary/cards/card_ready')

  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 1024, height: 768 },
    { width: 390, height: 844 },
  ]) {
    await page.setViewportSize(viewport)

    const heading = page.locator('.card-inspector__heading').getByRole('heading', { name: 'innovative' })
    const chapters = page.getByRole('navigation', { name: '单词卡章节' })
    const readButton = page.getByRole('button', { name: '阅读', exact: true })
    const editButton = page.getByRole('button', { name: '编辑', exact: true })
    const regenerateButton = page.getByRole('button', { name: '重新生成', exact: true })
    await expect(heading).toBeVisible()
    await expect(chapters).toBeVisible()
    await expect(readButton).toBeVisible()
    await expect(editButton).toBeVisible()
    await expect(regenerateButton).toBeVisible()

    const controlsDoNotOverlap = await page.locator('.card-inspector').evaluate((inspector) => {
      const elements = [
        inspector.querySelector('.card-inspector__heading h2'),
        inspector.querySelector('.card-inspector__chapters'),
        ...inspector.querySelectorAll('.card-inspector__mode button'),
        [...inspector.querySelectorAll('.card-inspector__toolbar > button')]
          .find((button) => button.textContent?.trim() === '重新生成'),
      ].filter((element): element is Element => element instanceof Element)
      const rectangles = elements.map((element) => element.getBoundingClientRect())
      return rectangles.every((rectangle, index) => rectangles.slice(index + 1).every((other) => (
        rectangle.right <= other.left
        || other.right <= rectangle.left
        || rectangle.bottom <= other.top
        || other.bottom <= rectangle.top
      )))
    })
    expect(controlsDoNotOverlap).toBe(true)
    await expectNoHorizontalOverflow(page)

    if (viewport.width === 390) {
      const mobileLayout = await page.locator('.card-inspector__notebook').evaluate((notebook) => {
        const chapters = notebook.querySelector('.card-inspector__chapters')
        const document = notebook.querySelector('.card-inspector__document')
        if (!(chapters instanceof HTMLElement) || !(document instanceof HTMLElement)) return null
        const chapterButtons = [...chapters.querySelectorAll('button')]
        const buttonTops = chapterButtons.map((button) => button.getBoundingClientRect().top)
        const notebookRect = notebook.getBoundingClientRect()
        const documentRect = document.getBoundingClientRect()
        return {
          chaptersDisplay: getComputedStyle(chapters).display,
          chaptersOverflowX: getComputedStyle(chapters).overflowX,
          chapterButtonsStayOnOneRow: new Set(buttonTops).size === 1,
          chapterButtonsDoNotShrink: chapterButtons.every((button) => getComputedStyle(button).flexShrink === '0'),
          notebookWidth: notebookRect.width,
          documentWidth: documentRect.width,
        }
      })
      expect(mobileLayout).not.toBeNull()
      expect(mobileLayout).toMatchObject({
        chaptersDisplay: 'flex',
        chaptersOverflowX: 'auto',
        chapterButtonsStayOnOneRow: true,
        chapterButtonsDoNotShrink: true,
      })
      expect(mobileLayout!.documentWidth).toBeGreaterThanOrEqual(mobileLayout!.notebookWidth - 1)

      const horizontalOverflow = await chapters.evaluate((navigation) => ({
        clientWidth: navigation.clientWidth,
        scrollWidth: navigation.scrollWidth,
      }))
      expect(horizontalOverflow.scrollWidth).toBeGreaterThan(horizontalOverflow.clientWidth)
      await chapters.evaluate((navigation) => { navigation.scrollLeft = navigation.scrollWidth })
      await expect.poll(() => chapters.evaluate((navigation) => navigation.scrollLeft)).toBeGreaterThan(0)
      const lastChapter = chapters.getByRole('button', { name: '历史' })
      const historySection = page.getByLabel('单词卡修订历史')
      const historyTopBeforeNavigation = await historySection.evaluate((section) => section.getBoundingClientRect().top)
      await lastChapter.click()
      await expect.poll(() => historySection.evaluate((section) => section.getBoundingClientRect().top)).toBeLessThan(historyTopBeforeNavigation)
    }
  }

  await expectCleanRuntime(page, errors)
})
