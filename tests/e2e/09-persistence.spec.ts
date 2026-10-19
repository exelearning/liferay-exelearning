import { execFileSync } from 'node:child_process';
import { expect, test } from '@playwright/test';
import { resources } from '../support/liferay';
import { adminContext, content, publishedOnPage } from './helpers';

test('E2E-09 resources, pages and references survive a portal restart', async ({ page }) => {
    test.setTimeout(900_000);
    const { admin, groupId } = await adminContext();
    const { resource, path } = await publishedOnPage(admin, groupId, 'valid/really-simple.elpx', 'e2e-persistence');
    await admin.dispose();

    execFileSync('docker', ['compose', 'restart', 'liferay'], { stdio: 'inherit' });
    execFileSync('node', ['--env-file=.env', 'scripts/liferay.mjs', 'wait', '--integration'], { stdio: 'inherit' });

    const { admin: after } = await adminContext();
    const stored = (await resources(after, groupId)).find(r => r.id === resource.id);
    expect(stored).toMatchObject({ version: '1.0', publishedVersion: '1.0', title: resource.title });

    await page.goto(path);
    await expect(content(page).locator('body')).toContainText('Really Simple Test Project');
    await after.dispose();
});
