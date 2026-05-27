import { expect, test } from '@playwright/test';
import { mockWritingWorkspaceApis } from './support/writingE2eApi';

test.beforeEach(async ({ page }) => {
  await mockWritingWorkspaceApis(page);
});

test('enters writing and verifies Writing Coach Agent menu interaction', async ({ page }) => {
  await page.goto('/app/writing');

  await expect(page).not.toHaveURL(/\/login/);
  await expect(page.getByRole('heading', { name: '写作练习' })).toBeVisible();

  await page.getByRole('button', { name: /\+ 新建作文/ }).click();
  await expect(page.getByRole('heading', { name: '新建写作任务' })).toBeVisible();
  await page.getByRole('button', { name: '继续' }).click();

  await expect(page).toHaveURL(/\/app\/writing\/editor/);
  await page.getByRole('button', { name: '教练' }).click();

  const agentChip = page.locator('.agent-chip');
  await expect(agentChip).toContainText('写作教练');
  await agentChip.click();

  const toolMenu = page.getByRole('menu', { name: '选择写作教练能力' });
  await expect(toolMenu).toBeVisible();

  await toolMenu.getByRole('menuitem', { name: /审题/ }).click();
  await expect(toolMenu).toBeHidden();
  await expect(agentChip).toContainText('审题');
  await expect(page.locator('.composer-input')).toHaveAttribute(
    'placeholder',
    /请先帮我审题/,
  );
});
