import { expect, test } from '@playwright/test';
import { api, fixture, login, resources, siteId, uniqueName, user } from '../support/liferay';
import { status, uploadThroughUI } from './helpers';

test('E2E-02 an author imports a package and sees its metadata and preview', async ({ page }) => {
    const author = user('author');
    await login(page, author);

    const fileName = uniqueName('e2e-import');
    await uploadThroughUI(page, fileName, fixture('valid/multipage-interactive.elpx'));

    // Like Documents and Media, the upload lands on the stored resource with its preview.
    await expect(page.locator('.alert-success')).toBeVisible();
    await expect(page.locator('.sheet-title')).toContainText('Un contenido de ejemplo para probar estilos');
    const metadata = page.locator('.exe-metadata');
    await expect(metadata).toContainText('Ignacio Gros');
    await expect(metadata).toContainText(fileName.replace('.elpx', ''));
    await expect(status(page)).toHaveText('Not published');
    await expect(page.frameLocator('iframe.exe-preview-frame').locator('body')).toContainText(
        'Un contenido de ejemplo',
    );

    const client = await api(author);
    const stored = (await resources(client, await siteId(client))).find(r =>
        r.fileName.startsWith(fileName.replace('.elpx', '')),
    );
    expect(stored).toMatchObject({ version: '1.0', publishable: true, author: 'Ignacio Gros' });
    await client.dispose();
});
