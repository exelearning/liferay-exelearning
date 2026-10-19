import { expect, test } from '@playwright/test';
import { login, MANAGER_PATH, SITE_PATH, user } from '../support/liferay';
import { actionItems, adminContext, content, openActions, openManager, openResource, publishedOnPage } from './helpers';

test.describe('E2E-07 authorization', () => {
    test('anonymous visitors only see published content', async ({ page }) => {
        await page.goto(`${SITE_PATH}/exelearning-demo`);
        await expect(content(page).locator('body')).not.toBeEmpty();
        await page.goto(MANAGER_PATH);
        await expect(page.locator('.management-bar')).toHaveCount(0);
    });

    test('viewers cannot manage resources, even with direct requests', async ({ page }) => {
        const { admin, groupId } = await adminContext();
        const { resource, path } = await publishedOnPage(admin, groupId, 'valid/really-simple.elpx', 'e2e-authz');
        await login(page, user('viewer'));
        await page.goto(path);
        await expect(content(page).locator('body')).toContainText('Really Simple Test Project');

        await page.goto(MANAGER_PATH);
        await expect(page.locator('.management-bar')).toHaveCount(0);

        const statuses = await page.evaluate(async id => {
            const token = (window as unknown as { Liferay: { authToken: string } }).Liferay.authToken;
            const call = (method: string, path: string) =>
                fetch(`/o/exelearning/v1${path}`, { method, headers: { 'x-csrf-token': token } }).then(r => r.status);
            return {
                publish: await call('POST', `/resources/${id}/publish`),
                unpublish: await call('POST', `/resources/${id}/unpublish`),
                remove: await call('DELETE', `/resources/${id}`),
                read: await call('GET', `/resources/${id}`),
            };
        }, resource.id);
        expect(statuses).toEqual({ publish: 403, unpublish: 403, remove: 403, read: 200 });
        await admin.dispose();
    });

    test('authors manage only their own resources; editors may edit every resource', async ({ page }) => {
        const { admin, groupId } = await adminContext();
        const { resource } = await publishedOnPage(admin, groupId, 'valid/really-simple.elpx', 'e2e-authz-roles');

        await login(page, user('author'));
        await openManager(page);
        await expect(page.locator('.management-bar').getByText('New', { exact: true })).toBeVisible();
        await openResource(page, resource.title);
        await openActions(page);
        await expect(actionItems(page)).toHaveText(['View', 'Download']);

        await page.context().clearCookies();
        await login(page, user('editor'));
        await openResource(page, resource.title);
        await openActions(page);
        await expect(actionItems(page)).toHaveText([
            'View',
            'Edit with eXeLearning',
            'Upload new version',
            'Unpublish',
            'Download',
        ]);
        await admin.dispose();
    });
});
