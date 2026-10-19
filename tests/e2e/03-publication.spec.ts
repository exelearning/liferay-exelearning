import { expect, test } from '@playwright/test';
import { ADMIN, fixture, login, uniqueName, uploadOk } from '../support/liferay';
import { adminContext, content, openResource, pageFor, resourceAction, status } from './helpers';

test('E2E-03 a published resource renders with navigation, assets and interactive activities', async ({ page }) => {
    const { admin, groupId } = await adminContext();
    const resource = await uploadOk(admin, groupId, fixture('valid/multipage-interactive.elpx'), uniqueName('e2e-pub'));

    await login(page, ADMIN);
    await openResource(page, resource.title);
    await resourceAction(page, 'Publish');
    await expect(page.locator('.alert-success')).toBeVisible();
    await expect(status(page)).toHaveText('Published');

    const path = await pageFor(admin, groupId, 'e2e-publication', resource.id);
    await page.context().clearCookies();
    await page.goto(path);

    const frame = content(page);
    await expect(frame.locator('h1, .page-title').first()).toBeVisible();
    const images = await frame
        .locator('img')
        .evaluateAll(
            nodes => nodes.filter(n => n instanceof HTMLImageElement && n.complete && n.naturalWidth > 0).length,
        );
    expect(images).toBeGreaterThan(0);

    const links = frame.locator('#siteNav a').filter({ visible: true });
    expect(await links.count()).toBeGreaterThan(3);
    await links.nth(2).click();
    await expect.poll(() => frame.locator('html').evaluate(() => location.pathname)).toMatch(/\/html\/.+\.html$/);

    // Interactive activity: the true/false iDevice evaluates the learner's answer.
    const quiz = await uploadOk(admin, groupId, fixture('valid/true-or-false.elpx'), uniqueName('e2e-quiz'));
    await admin.post(`/o/exelearning/v1/resources/${quiz.id}/publish`);
    await page.goto(await pageFor(admin, groupId, 'e2e-quiz', quiz.id));
    const game = content(page).locator('.exe-trueorfalse-container').first();
    await expect(game).toBeVisible();
    const answers = game.locator('input[type=radio]');
    await expect(answers.first()).toBeVisible();
    await answers.first().check();
    await game.getByRole('button', { name: /Comprobar|Check/ }).click();
    await expect(game).toContainText(/Puntuación|Score|correct|Correcto|superada/i);
    await admin.dispose();
});
