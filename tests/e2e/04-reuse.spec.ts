import { expect, test } from '@playwright/test';
import { adminContext, content, pageFor, publishedOnPage } from './helpers';

test('E2E-04 one resource is reused on two pages', async ({ page }) => {
    const { admin, groupId } = await adminContext();
    const { resource, path } = await publishedOnPage(admin, groupId, 'valid/really-simple.elpx', 'e2e-reuse-a');
    const second = await pageFor(admin, groupId, 'e2e-reuse-b', resource.id);

    for (const target of [path, second]) {
        await page.goto(target);
        await expect(page.locator('.exe-viewer-title')).toHaveText(resource.title);
        await expect(content(page).locator('body')).toContainText('Really Simple Test Project');
        const src = await page.locator('iframe.exe-viewer-frame').getAttribute('src');
        expect(src).toContain(`/o/exelearning-content/${resource.id}-1.0-`);
    }
    await admin.dispose();
});
