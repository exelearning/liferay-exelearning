import { expect, test } from '@playwright/test';
import { ADMIN, fixture, inContainer, login, resources } from '../support/liferay';
import { adminContext, content, openManager, publishedOnPage, uploadThroughUI } from './helpers';

test.describe('E2E-08 security', () => {
    test('a path traversal package is rejected in the UI and nothing escapes', async ({ page }) => {
        await login(page, ADMIN);
        const { admin, groupId } = await adminContext();
        const before = (await resources(admin, groupId)).length;
        await uploadThroughUI(page, 'zip-slip.elpx', fixture('adversarial/zip-slip.elpx'));
        await expect(page.locator('.alert-danger')).toContainText('unsafe path');
        await expect(page.locator('input[type=file][name$="file"]')).toBeVisible();
        expect((await resources(admin, groupId)).length).toBe(before);
        await admin.dispose();
        await openManager(page);
        expect(inContainer('ls /tmp/exelearning-escaped.html 2>/dev/null || echo absent').trim()).toBe('absent');
    });

    test('published content runs in an opaque origin without access to the portal', async ({ page }) => {
        const { admin, groupId } = await adminContext();
        const { path } = await publishedOnPage(admin, groupId, 'valid/really-simple.elpx', 'e2e-isolation');
        await login(page, ADMIN);
        await page.goto(path);
        const iframe = page.locator('iframe.exe-viewer-frame');
        await expect(iframe).toHaveAttribute(
            'sandbox',
            'allow-scripts allow-popups allow-forms allow-downloads allow-popups-to-escape-sandbox',
        );
        await expect(content(page).locator('body')).toContainText('Really Simple Test Project');

        const frame = page.frames().find(f => f.url().includes('/o/exelearning-content/'));
        expect(frame).toBeDefined();
        const probe = await frame?.evaluate(async () => {
            const attempt = (fn: () => unknown) => {
                try {
                    fn();
                    return 'allowed';
                } catch {
                    return 'blocked';
                }
            };
            const api = await fetch('/o/exelearning/v1/resources/0', { credentials: 'include' })
                .then(r => r.status)
                .catch(() => 'blocked');
            return {
                origin: window.origin,
                parentDocument: attempt(() => window.parent.document.title),
                cookies: attempt(() => document.cookie),
                storage: attempt(() => window.localStorage.length),
                api,
            };
        });
        expect(probe).toEqual({
            origin: 'null',
            parentDocument: 'blocked',
            cookies: 'blocked',
            storage: 'blocked',
            api: 'blocked',
        });

        // Opening the content URL directly keeps the sandbox (CSP), so it never runs as the portal.
        const src = (await iframe.getAttribute('src')) ?? '';
        await page.goto(src);
        expect(await page.evaluate(() => window.origin)).toBe('null');

        const token = src.split('/')[3] ?? '';
        const forged = token.slice(0, -1) + (token.endsWith('0') ? '1' : '0');
        expect((await page.request.get(`/o/exelearning-content/${forged}/index.html`)).status()).toBe(403);
        await admin.dispose();
    });
});
