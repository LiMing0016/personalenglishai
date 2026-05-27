import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { test, expect, type Page } from '@playwright/test';

const authFile = path.join(process.cwd(), 'playwright/.auth/user.json');
const testEmail = process.env.E2E_TEST_EMAIL || 'admin01@admin.com';
const testPassword = process.env.E2E_TEST_PASSWORD || 'Kiss497.*';
const mockAuthEnabled = process.env.E2E_MOCK_AUTH === '1';

function base64UrlJson(value: unknown) {
  return Buffer.from(JSON.stringify(value), 'utf8')
    .toString('base64url');
}

function createMockJwt() {
  const now = Math.floor(Date.now() / 1000);
  return [
    base64UrlJson({ alg: 'none', typ: 'JWT' }),
    base64UrlJson({
      sub: 'e2e-user',
      email: testEmail,
      iat: now,
      exp: now + 60 * 60,
    }),
    'e2e-signature',
  ].join('.');
}

async function imageDataFromDataUrl(page: Page, dataUrl: string) {
  return page.evaluate(async (src) => {
    const image = new Image();
    image.src = src;
    await image.decode();

    const canvas = document.createElement('canvas');
    canvas.width = image.width;
    canvas.height = image.height;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Canvas 2D context is unavailable');

    context.drawImage(image, 0, 0);
    const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
    return {
      width: imageData.width,
      height: imageData.height,
      pixels: Array.from(imageData.data),
    };
  }, dataUrl);
}

async function solveCaptchaX(page: Page, backgroundDataUrl: string) {
  const { width, height, pixels } = await imageDataFromDataUrl(page, backgroundDataUrl);
  const luminance = (x: number, y: number) => {
    const index = (y * width + x) * 4;
    return 0.299 * pixels[index] + 0.587 * pixels[index + 1] + 0.114 * pixels[index + 2];
  };

  let bestX = 60;
  let bestScore = Number.NEGATIVE_INFINITY;

  for (let x = 55; x <= width - 55; x += 1) {
    let score = 0;
    for (let y = 8; y < height - 8; y += 1) {
      const current = luminance(x, y);
      const left = luminance(Math.max(0, x - 5), y);
      const right = luminance(Math.min(width - 1, x + 45), y);
      const contrast = (left + right) / 2 - current;
      if (contrast > 18) score += contrast;
    }
    if (score > bestScore) {
      bestScore = score;
      bestX = x;
    }
  }

  return bestX;
}

async function createCaptchaToken(page: Page) {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const captchaResponse = await page.context().request.get('/api/v1/auth/captcha');
    expect(captchaResponse.ok()).toBeTruthy();
    const captchaBody = await captchaResponse.json() as {
      data?: { captchaId?: string; bgImage?: string };
    };
    const captchaId = captchaBody.data?.captchaId;
    const bgImage = captchaBody.data?.bgImage;
    if (!captchaId || !bgImage) throw new Error('Captcha response did not include captchaId/bgImage');

    const x = await solveCaptchaX(page, bgImage);
    const verifyResponse = await page.context().request.post('/api/v1/auth/captcha/verify', {
      data: { captchaId, x },
    });
    if (!verifyResponse.ok()) continue;

    const verifyBody = await verifyResponse.json() as {
      data?: { verified?: boolean; captchaToken?: string };
    };
    if (verifyBody.data?.verified && verifyBody.data.captchaToken) {
      return verifyBody.data.captchaToken;
    }
  }

  throw new Error('Unable to solve slider captcha for E2E auth setup');
}

test('authenticate', async ({ page }) => {
  await mkdir(path.dirname(authFile), { recursive: true });
  await page.goto('/');

  if (mockAuthEnabled) {
    await page.evaluate((token) => {
      window.localStorage.setItem('auth_token', token);
    }, createMockJwt());
    await page.context().storageState({ path: authFile });
    return;
  }

  const captchaToken = await createCaptchaToken(page);
  const loginResponse = await page.context().request.post('/api/v1/auth/login', {
    data: {
      email: testEmail,
      password: testPassword,
      captchaToken,
    },
  });

  expect(loginResponse.ok()).toBeTruthy();
  const loginBody = await loginResponse.json() as {
    data?: { token?: string };
    token?: string;
    accessToken?: string;
  };
  const token = loginBody.data?.token ?? loginBody.token ?? loginBody.accessToken;
  if (!token) throw new Error('Login response did not include an access token');

  await page.evaluate((accessToken) => {
    window.localStorage.setItem('auth_token', accessToken);
  }, token);

  await page.context().storageState({ path: authFile });
});
