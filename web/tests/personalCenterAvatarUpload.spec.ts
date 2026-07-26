import { expect, test } from '@playwright/test'

const ONE_PIXEL_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64',
)

test.use({ storageState: { cookies: [], origins: [] } })

test('uploads an avatar separately from nickname editing and falls back on image error', async ({ page }) => {
  const pageErrors: Error[] = []
  page.on('pageerror', (error) => pageErrors.push(error))
  await page.goto('/dev/personal-center-preview')

  const avatarButton = page.getByRole('button', { name: '上传头像' })
  await expect(avatarButton).toBeVisible()
  await expect(page.locator('.nickname-input')).toHaveCount(0)

  await avatarButton.focus()
  await expect(avatarButton).toBeFocused()
  const chooserPromise = page.waitForEvent('filechooser')
  await page.keyboard.press('Enter')
  const chooser = await chooserPromise
  await chooser.setFiles({
    name: 'avatar.png',
    mimeType: 'image/png',
    buffer: ONE_PIXEL_PNG,
  })

  const avatarImage = page.locator('.avatar-image')
  await expect(avatarImage).toBeVisible()
  await expect(page.getByText('头像已更新')).toBeVisible()
  await expect(page.locator('.nickname-input')).toHaveCount(0)
  const firstPreviewUrl = await avatarImage.getAttribute('src')

  const secondChooserPromise = page.waitForEvent('filechooser')
  await avatarButton.click()
  const secondChooser = await secondChooserPromise
  await secondChooser.setFiles({
    name: 'avatar.png',
    mimeType: 'image/png',
    buffer: ONE_PIXEL_PNG,
  })
  await expect(avatarImage).toBeVisible()
  await expect.poll(() => avatarImage.getAttribute('src')).not.toBe(firstPreviewUrl)

  await avatarImage.evaluate((image: HTMLImageElement) => {
    image.src = '/missing-avatar-for-fallback.png'
  })
  await expect(page.locator('.avatar-initial')).toHaveText('C')

  await page.getByRole('button', { name: '编辑昵称' }).click()
  await expect(page.locator('.nickname-input')).toBeVisible()
  expect(pageErrors).toEqual([])
})
