import { expect, test } from '@playwright/test';
import { ADMIN, login, SITE_PATH } from '../support/liferay';
import { openManager } from './helpers';

test('E2E-01 the integration is installed and its screens are available', async ({ page, request }) => {
    for (const path of ['/o/exelearning-web/js/exelearning.js', '/o/exelearning-editor/index.html']) {
        expect((await request.get(path)).status(), path).toBe(200);
    }

    await page.goto(`${SITE_PATH}/exelearning-demo`);
    await expect(page.locator('exelearning-viewer iframe.exe-viewer-frame')).toBeVisible();

    await login(page, ADMIN);
    await openManager(page);
    await expect(page.locator('.management-bar').getByText('New', { exact: true })).toBeVisible();
    await expect(page.locator('.exe-resource-card').first()).toBeVisible();

    const registered = await page.evaluate(() => ({
        editor: !!customElements.get('exelearning-editor'),
        viewer: !!customElements.get('exelearning-viewer'),
    }));
    expect(registered).toEqual({ editor: true, viewer: true });
});
