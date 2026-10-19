import { type APIRequestContext, expect, type FrameLocator, type Page } from '@playwright/test';
import {
    ADMIN,
    api,
    createPage,
    fixture,
    MANAGER_PATH,
    publish,
    type Resource,
    siteId,
    uniqueName,
    uploadOk,
} from '../support/liferay';

export async function adminContext(): Promise<{ admin: APIRequestContext; groupId: string }> {
    const admin = await api(ADMIN);
    return { admin, groupId: await siteId(admin) };
}

/** Uploads, publishes and places a fixture on a fresh content page; returns the page path. */
export async function publishedOnPage(
    admin: APIRequestContext,
    groupId: string,
    fixturePath: string,
    slug: string,
): Promise<{ resource: Resource; path: string }> {
    const resource = await uploadOk(admin, groupId, fixture(fixturePath), uniqueName(slug));
    await publish(admin, resource.id, resource.version);
    return { resource, path: await pageFor(admin, groupId, slug, resource.id) };
}

/** Creates a content page with the widget under a slug unique to this test run. */
export async function pageFor(admin: APIRequestContext, groupId: string, slug: string, fileEntryId: string) {
    const unique = `${slug}-${Date.now().toString(36)}`;
    await createPage(admin, groupId, unique, fileEntryId);
    return `/web/exelearning-demo/${unique}`;
}

export function content(page: Page): FrameLocator {
    return page.frameLocator('iframe.exe-viewer-frame');
}

export async function openManager(page: Page) {
    await page.goto(MANAGER_PATH);
    await expect(page.locator('.management-bar')).toBeVisible();
}

/** Finds a resource with the management toolbar search and opens its page. */
export async function openResource(page: Page, title: string) {
    await openManager(page);
    const search = page.locator('.management-bar input[type="search"]').first();
    await search.fill(title);
    await search.press('Enter');
    await page.getByRole('link', { name: title, exact: true }).first().click();
    await expect(page.locator('.sheet-title')).toHaveText(title);
}

/** Items of the open actions menu; Liferay moves the open menu into a floating overlay. */
export function actionItems(page: Page) {
    return page.locator('.dropdown-menu .dropdown-item').filter({ visible: true });
}

export async function openActions(page: Page) {
    // The icon menu script is attached after the page finishes loading.
    await page.waitForLoadState('networkidle');
    await page.locator('.sheet-header .dropdown-toggle').first().click();
    await expect(actionItems(page).first()).toBeVisible();
}

/** Runs an action of the resource page's kebab menu. */
export async function resourceAction(page: Page, name: string) {
    await openActions(page);
    await actionItems(page)
        .filter({ hasText: new RegExp(`^\\s*${name}\\s*$`) })
        .click();
}

export function status(page: Page) {
    return page.locator('.sheet-header .label').first();
}

export async function uploadThroughUI(page: Page, name: string, buffer: Buffer) {
    await openManager(page);
    await page.locator('.management-bar').getByText('New', { exact: true }).click();
    await page.locator('input[type=file][name$="file"]').setInputFiles({
        name,
        mimeType: 'application/octet-stream',
        buffer,
    });
    await Promise.all([page.waitForNavigation(), page.getByRole('button', { name: 'Upload' }).click()]);
}
