import { expect, type Page, test } from '@playwright/test';
import { ADMIN, login } from '../support/liferay';
import { adminContext, content, openResource, publishedOnPage, resourceAction, status } from './helpers';

async function openEditor(page: Page, title: string) {
    await openResource(page, title);
    await resourceAction(page, 'Edit with eXeLearning');
    const dialog = page.locator('dialog.exe-editor-dialog');
    await expect(dialog).toHaveAttribute('data-state', 'ready', { timeout: 120_000 });
    return dialog;
}

function descriptionField(page: Page) {
    return page.frameLocator('iframe.exe-editor-frame').locator('textarea[property="pp_description"]').first();
}

test('E2E-05/06 an editor changes a resource, saves it and republishes it', async ({ page }) => {
    const { admin, groupId } = await adminContext();
    const { resource, path } = await publishedOnPage(admin, groupId, 'valid/really-simple.elpx', 'e2e-editing');

    await login(page, ADMIN);
    const dialog = await openEditor(page, resource.title);

    const newDescription = `Edited in Liferay ${Date.now().toString(36)}`;
    const field = descriptionField(page);
    await expect(field).toHaveValue(/Test content generated with eXeLearning/);
    await field.fill(newDescription);
    await field.press('Tab');

    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog.locator('.exe-editor-status')).toHaveText('Saved as version 1.1.', { timeout: 60_000 });

    // Server-side persistence, not just an editor-side export.
    const stored = await (await admin.get(`/o/exelearning/v1/resources/${resource.id}`)).json();
    expect(stored).toMatchObject({ version: '1.1', publishedVersion: '1.0', description: newDescription });

    // Closing the editor returns to the resource page, which shows the pending version.
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.sheet-title')).toHaveText(resource.title);
    await expect(status(page)).toHaveText('Newer version available');

    const reopened = await openEditor(page, resource.title);
    await expect(descriptionField(page)).toHaveValue(newDescription);
    await reopened.getByRole('button', { name: 'Close' }).click();

    // The page still shows 1.0 until the new revision is published.
    await page.goto(path);
    await expect(page.locator('iframe.exe-viewer-frame')).toHaveAttribute('src', new RegExp(`/${resource.id}-1\\.0-`));

    await openResource(page, resource.title);
    await resourceAction(page, 'Publish');
    await expect(status(page)).toHaveText('Published');

    await page.goto(path);
    await expect(page.locator('iframe.exe-viewer-frame')).toHaveAttribute('src', new RegExp(`/${resource.id}-1\\.1-`));
    await expect(content(page).locator('meta[name="description"]').first()).toHaveAttribute('content', newDescription);
    await admin.dispose();
});
