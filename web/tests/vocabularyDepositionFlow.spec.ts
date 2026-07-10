import { expect, test } from '@playwright/test'

// This mocked flow deliberately runs with --no-deps, so it must not require the shared auth setup file.
test.use({
  storageState: {
    cookies: [],
    origins: [],
  },
})

const viewports = [
  { name: 'desktop', viewport: { width: 1440, height: 900 } },
  { name: 'mobile', viewport: { width: 390, height: 844 } },
]

for (const { name, viewport } of viewports) {
  test.describe(`vocabulary deposition ${name}`, () => {
    test.use({ viewport })

    test('manual word survives refresh and can be edited', async ({ page }) => {
      const cards: Array<Record<string, unknown>> = []
      const detail = {
        cardUid: 'card_1',
        displayTerm: 'innovative',
        normalizedTerm: 'innovative',
        templateKey: 'basic',
        status: 'ready',
        activeRevisionUid: 'rev_1',
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
          notes: 'Use this in product writing.',
        },
        sources: [],
        generationStatus: 'ready',
        generationError: null,
        createdAt: '2026-07-11T00:00:00Z',
        candidateContent: null,
      }

      await page.addInitScript(() => localStorage.setItem('auth_token', 'vocabulary-e2e-token'))
      await page.route('**/users/me/profile', (route) => route.fulfill({ json: { data: { studyStage: 'college' } } }))
      await page.route('**/v1/auth/refresh', (route) => route.fulfill({ json: { data: { token: 'vocabulary-e2e-token' } } }))
      await page.route('**/api/vocabulary/**', async (route) => {
        const url = new URL(route.request().url())
        const path = url.pathname.replace(/\/$/, '')
        const method = route.request().method()

        if (path.endsWith('/templates')) {
          return route.fulfill({ json: { code: '0', data: { items: [{ key: 'basic', version: 1, name: '基础卡片', fields: ['term', 'definitions', 'examples', 'notes'] }], defaultTemplateKey: 'basic' } } })
        }
        if (path.endsWith('/captures')) {
          cards.splice(0, cards.length, { ...detail })
          return route.fulfill({ json: { code: '0', data: { items: [{ term: 'innovative', cardUid: 'card_1', action: 'created', status: 'generating' }] } } })
        }
        if (path.endsWith('/cards') && method === 'GET') {
          return route.fulfill({ json: { code: '0', data: { items: cards, total: cards.length, page: 1, size: 20 } } })
        }
        if (path.endsWith('/revisions')) {
          return route.fulfill({ json: { code: '0', data: { currentRevisionUid: 'rev_1', candidateRevisionUid: null, conflictStatus: 'none', items: [] } } })
        }
        if (path.endsWith('/cards/card_1') && method === 'PUT') {
          const body = route.request().postDataJSON() as { content: typeof detail.content }
          detail.content = body.content
          cards.splice(0, 1, { ...detail })
          return route.fulfill({ json: { code: '0', data: detail } })
        }
        return route.fulfill({ json: { code: '0', data: detail } })
      })

      await page.goto('/')
      await page.evaluate(() => localStorage.setItem('auth_token', 'vocabulary-e2e-token'))
      await expect.poll(() => page.evaluate(() => localStorage.getItem('auth_token'))).toBe('vocabulary-e2e-token')
      await page.goto('/app/vocabulary?tab=collection')
      await page.getByRole('textbox', { name: '批量录入单词' }).fill('innovative')
      await page.getByRole('button', { name: '沉淀 1 个单词' }).click()
      await expect(page.getByText('已收下')).toBeVisible()
      await page.reload()
      const capturedCard = page.locator('.card-row').filter({ hasText: 'innovative' })
      await expect(capturedCard).toBeVisible()
      await capturedCard.click()
      await expect(page).toHaveURL(/\/app\/vocabulary\/card\/card_1$/)
      await page.getByRole('button', { name: '编辑卡片' }).click()
      await page.getByLabel('个人笔记').fill('Use this in product writing.')
      await page.getByRole('button', { name: '保存修改' }).click()
      await expect(page.getByLabel('个人笔记')).toHaveValue('Use this in product writing.')
      await page.screenshot({ path: `test-results/vocabulary-deposition-${name}.png`, fullPage: true })
      const hasHorizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
      expect(hasHorizontalOverflow).toBe(false)
    })
  })
}
