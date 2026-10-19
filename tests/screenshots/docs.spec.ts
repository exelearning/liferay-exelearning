import { expect, type Page, test } from '@playwright/test';
import { adminContext, content, openManager, resourceAction, status, uploadThroughUI } from '../e2e/helpers';
import { ADMIN, fixture, login, MANAGER_PATH, publish, resources, SITE_PATH, uploadOk } from '../support/liferay';

/**
 * Regenerates docs/images against a freshly seeded stack (`make screenshots`).
 * Adds two more resources so the card view is not empty, and edits one of
 * them; the seeded demo resource is left untouched.
 */
const shot = (page: Page, name: string) => page.screenshot({ path: `docs/images/${name}.png` });

const PORTLET = 'org_exelearning_liferay_portlet_ExeLearningAdminPortlet';

function resourcePage(id: string) {
    return `${MANAGER_PATH}&_${PORTLET}_mvcPath=%2Fadmin%2Fview_resource.jsp&_${PORTLET}_fileEntryId=${id}`;
}

test('documentation screenshots', async ({ page }) => {
    test.setTimeout(600_000);
    const { admin, groupId } = await adminContext();
    const existing = await resources(admin, groupId);
    const ensure = async (fileName: string, fixturePath: string) => {
        const found = existing.find(r => r.fileName === fileName);
        if (found) return found;
        const created = await uploadOk(admin, groupId, fixture(fixturePath), fileName);
        await publish(admin, created.id, created.version);
        return created;
    };
    await ensure('true-or-false.elpx', 'valid/true-or-false.elpx');
    const edited = await ensure('really-simple.elpx', 'valid/really-simple.elpx');

    await page.goto(`${SITE_PATH}/exelearning-demo`);
    await expect(content(page).locator('body')).toContainText('Un contenido de ejemplo');
    await shot(page, '01-widget-public-page');

    await login(page, ADMIN);
    await openManager(page);
    await expect(page.locator('.exe-resource-card img').first()).toBeVisible();
    await shot(page, '02-manager-cards');

    const demo = existing.find(r => r.fileName === 'exelearning-demo.elpx') ?? existing[0];
    await page.goto(resourcePage(demo?.id ?? edited.id));
    await expect(page.frameLocator('iframe.exe-preview-frame').locator('body')).not.toBeEmpty();
    await shot(page, '03-resource-details');

    await uploadThroughUI(page, 'zip-slip.elpx', fixture('adversarial/zip-slip.elpx'));
    await expect(page.locator('.alert-danger')).toBeVisible();
    await shot(page, '04-upload-rejected');

    await page.goto(resourcePage(edited.id));
    await resourceAction(page, 'Edit with eXeLearning');
    const dialog = page.locator('dialog.exe-editor-dialog');
    await expect(dialog).toHaveAttribute('data-state', 'ready', { timeout: 120_000 });
    const editor = page.frameLocator('iframe.exe-editor-frame');
    await expect(editor.locator('#load-screen-main')).toBeHidden();
    const field = editor.locator('textarea[property="pp_description"]').first();
    await expect(field).toBeVisible();
    await shot(page, '05-embedded-editor');

    await field.fill('Edited from Liferay with the embedded eXeLearning editor.');
    await field.press('Tab');
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog.locator('.exe-editor-status')).toHaveText(/Saved as version/, { timeout: 60_000 });
    await shot(page, '06-editor-saved');

    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(status(page)).toHaveText('Newer version available');
    await shot(page, '07-new-version-pending');
    await admin.dispose();
});
