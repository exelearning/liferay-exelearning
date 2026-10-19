import { existsSync } from 'node:fs';
import { defineConfig, devices } from '@playwright/test';

if (existsSync('.env')) {
    process.loadEnvFile('.env');
}

const baseURL = process.env.LIFERAY_URL ?? `http://localhost:${process.env.LIFERAY_PORT ?? 8080}`;

/**
 * Integration and end-to-end tests run against the real Liferay stack started
 * with `make up` and seeded with `make seed`. They share portal state, so they
 * run serially.
 */
export default defineConfig({
    timeout: 180_000,
    expect: { timeout: 20_000 },
    fullyParallel: false,
    workers: 1,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 1 : 0,
    outputDir: 'test-results/playwright',
    reporter: [
        ['list'],
        ['html', { open: 'never', outputFolder: 'playwright-report' }],
        ['junit', { outputFile: 'test-results/junit-playwright.xml' }],
    ],
    use: {
        baseURL,
        locale: 'en-US',
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure',
        video: 'retain-on-failure',
    },
    projects: [
        { name: 'integration', testDir: 'tests/integration' },
        {
            name: 'e2e-chromium',
            testDir: 'tests/e2e',
            use: { ...devices['Desktop Chrome'], viewport: { width: 1400, height: 1000 } },
        },
        {
            name: 'screenshots',
            testDir: 'tests/screenshots',
            use: { ...devices['Desktop Chrome'], viewport: { width: 1400, height: 900 } },
        },
        {
            name: 'e2e-firefox',
            testDir: 'tests/e2e',
            use: { ...devices['Desktop Firefox'], viewport: { width: 1400, height: 1000 } },
        },
    ],
});
