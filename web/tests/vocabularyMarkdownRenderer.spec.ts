import { expect, test, type Page } from '@playwright/test'

declare global {
  interface Window {
    vocabularyMarkdownSections: Array<Array<{ id: string, title: string, level: 2 }>>
    setVocabularyMarkdown: (markdown: string) => void
    copiedMarkdownCode?: string
    maliciousExecuted?: boolean
  }
}

test.use({ storageState: { cookies: [], origins: [] } })

const harnessUrl = '/tests/fixtures/vocabulary-markdown-renderer.html'

async function openHarness(page: Page) {
  await page.goto(harnessUrl)
  await expect(page.getByRole('heading', { name: '初始章节' })).toBeVisible()
}

async function updateMarkdown(page: Page, markdown: string) {
  await page.evaluate((value) => window.setVocabularyMarkdown(value), markdown)
}

async function latestSections(page: Page) {
  return page.evaluate(() => window.vocabularyMarkdownSections.at(-1))
}

test('escapes raw HTML without executing it', async ({ page }) => {
  await openHarness(page)
  await updateMarkdown(page, [
    '<script>window.maliciousExecuted = true</script>',
    '<div id="unsafe-html">unsafe</div>',
  ].join('\n\n'))

  const article = page.getByRole('article')
  await expect(article).toContainText('<script>window.maliciousExecuted = true</script>')
  await expect(article).toContainText('<div id="unsafe-html">unsafe</div>')
  await expect(article.locator('script')).toHaveCount(0)
  await expect(article.locator('#unsafe-html')).toHaveCount(0)
  expect(await page.evaluate(() => window.maliciousExecuted)).toBeUndefined()
})

test('does not render Markdown image syntax as an image', async ({ page }) => {
  await openHarness(page)
  await updateMarkdown(page, '![例句截图](https://example.com/example.png)')

  const article = page.getByRole('article')
  await expect(article).toContainText('例句截图')
  await expect(article.locator('img')).toHaveCount(0)
})

test('emits initial H2 sections immediately', async ({ page }) => {
  await openHarness(page)

  await expect.poll(() => latestSections(page)).toEqual([
    { id: 'markdown-section-1', title: '初始章节', level: 2 },
  ])
})

test('emits updated sections when Markdown changes', async ({ page }) => {
  await openHarness(page)
  await updateMarkdown(page, '## 例句\n\nExample.\n\n## 用法\n\nUsage.')

  await expect.poll(() => latestSections(page)).toEqual([
    { id: 'markdown-section-1', title: '例句', level: 2 },
    { id: 'markdown-section-2', title: '用法', level: 2 },
  ])
})

test('renders the empty state and emits no sections for empty Markdown', async ({ page }) => {
  await openHarness(page)
  await updateMarkdown(page, '')

  await expect(page.getByText('暂无主题内容', { exact: true })).toBeVisible()
  await expect(page.getByRole('article')).toHaveCount(0)
  await expect.poll(() => latestSections(page)).toEqual([])
})

test('delegates fenced-code pointer and keyboard copy clicks to the shared handler', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(Navigator.prototype, 'clipboard', {
      configurable: true,
      value: {
        writeText: async (text: string) => {
          window.copiedMarkdownCode = text
        },
      },
    })
  })
  await openHarness(page)
  await updateMarkdown(page, '```ts\nconst answer = 42\n```')

  const copyButton = page.getByRole('button', { name: '复制文本' })
  await copyButton.click()

  await expect.poll(() => page.evaluate(() => window.copiedMarkdownCode)).toBe('const answer = 42')
  await expect(copyButton).toHaveText('已复制')

  await page.evaluate(() => {
    window.copiedMarkdownCode = undefined
  })
  await copyButton.focus()
  await page.keyboard.press('Enter')
  await expect.poll(() => page.evaluate(() => window.copiedMarkdownCode)).toBe('const answer = 42')
})
