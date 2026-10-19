import { expect, test } from '@playwright/test';
import { ADMIN, inContainer, login } from '../support/liferay';
import { adminContext, content, openResource, publishedOnPage, resourceAction, status } from './helpers';

test('E2E-10 failed saves and publications keep the published revision working', async ({ page }) => {
    const { admin, groupId } = await adminContext();
    const { resource, path } = await publishedOnPage(admin, groupId, 'valid/really-simple.elpx', 'e2e-recovery');

    await login(page, ADMIN);
    await openResource(page, resource.title);
    await resourceAction(page, 'Edit with eXeLearning');
    const dialog = page.locator('dialog.exe-editor-dialog');
    await expect(dialog).toHaveAttribute('data-state', 'ready', { timeout: 120_000 });

    // The server rejects the save: the error is shown and nothing is reported as saved.
    await page.route('**/o/exelearning/v1/resources/*/package?fileName=*', route =>
        route.fulfill({
            status: 500,
            contentType: 'application/json',
            body: JSON.stringify({ code: 'unexpected-error', message: 'Storage unavailable' }),
        }),
    );
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveAttribute('data-state', 'error', { timeout: 60_000 });
    await expect(dialog.locator('.exe-editor-status')).toContainText('Storage unavailable');
    await page.unroute('**/o/exelearning/v1/resources/*/package?fileName=*');

    let detail = await (await admin.get(`/o/exelearning/v1/resources/${resource.id}`)).json();
    expect(detail.version).toBe('1.0');

    // Retrying after the failure succeeds.
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog.locator('.exe-editor-status')).toHaveText('Saved as version 1.1.', { timeout: 60_000 });
    page.once('dialog', d => d.accept());
    await dialog.getByRole('button', { name: 'Close' }).click();

    await expect(page.locator('.sheet-title')).toHaveText(resource.title);

    // A failed publication request leaves the previous revision published.
    const publishAction = (url: URL) =>
        url.searchParams.get('p_p_lifecycle') === '1' && url.href.includes('cmd=publish');
    await page.route(publishAction, route =>
        route.fulfill({ status: 500, contentType: 'text/html', body: '<h1>Publication failed</h1>' }),
    );
    await resourceAction(page, 'Publish');
    await expect(page.locator('h1')).toHaveText('Publication failed');
    await page.unroute(publishAction);
    await openResource(page, resource.title);
    await expect(status(page)).toHaveText('Newer version available');

    detail = await (await admin.get(`/o/exelearning/v1/resources/${resource.id}`)).json();
    expect(detail).toMatchObject({ version: '1.1', publishedVersion: '1.0' });
    await page.goto(path);
    await expect(page.locator('iframe.exe-viewer-frame')).toHaveAttribute('src', new RegExp(`/${resource.id}-1\\.0-`));
    await expect(content(page).locator('body')).toContainText('Really Simple Test Project');

    // A server-side failure (package without HTML export) is rejected the same way.
    const response = await admin.put(`/o/exelearning/v1/resources/${resource.id}/package?fileName=x.elpx`, {
        data: Buffer.from('not a zip'),
        headers: { 'Content-Type': 'application/octet-stream', 'If-Match': '"1.1"' },
    });
    expect(response.status()).toBe(400);
    expect(inContainer('ls -A /opt/liferay/data/exelearning/publications/.staging 2>/dev/null || true').trim()).toBe(
        '',
    );
    await admin.dispose();
});
