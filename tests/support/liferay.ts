import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { type APIRequestContext, expect, type Page, request } from '@playwright/test';

export const ADMIN = { email: 'test@liferay.com', password: process.env.LIFERAY_ADMIN_PASSWORD ?? '' };

export type Role = 'viewer' | 'author' | 'editor';

export function user(role: Role) {
    return { email: `demo.${role}@exelearning.example.com`, password: process.env.SEED_USER_PASSWORD ?? '' };
}

export const SITE_PATH = '/web/exelearning-demo';

export function fixture(path: string): Buffer {
    return readFileSync(new URL(`../fixtures/${path}`, import.meta.url));
}

/** REST client that authenticates with HTTP Basic, as automation does. */
export async function api(credentials: { email: string; password: string } | null = ADMIN) {
    return request.newContext({
        baseURL: process.env.LIFERAY_URL ?? `http://localhost:${process.env.LIFERAY_PORT ?? 8080}`,
        httpCredentials: credentials
            ? { username: credentials.email, password: credentials.password, send: 'always' }
            : undefined,
    });
}

export interface Resource {
    id: string;
    groupId: string;
    title: string;
    version: string;
    publishedVersion: string | null;
    publishable: boolean;
    fileName: string;
    author: string;
    language: string;
    license: string;
    permissions: { update: boolean; delete: boolean };
    versions?: { version: string }[];
}

export async function siteId(context: APIRequestContext, erc = 'exelearning-demo'): Promise<string> {
    const response = await context.get(`/o/headless-site/v1.0/sites/by-external-reference-code/${erc}`);
    expect(response.status(), 'run "make seed" first').toBe(200);
    return String((await response.json()).id);
}

export async function upload(
    context: APIRequestContext,
    groupId: string,
    data: Buffer,
    fileName: string,
): Promise<{ status: number; body: Record<string, unknown> }> {
    const response = await context.post(
        `/o/exelearning/v1/sites/${groupId}/resources?fileName=${encodeURIComponent(fileName)}`,
        { data, headers: { 'Content-Type': 'application/octet-stream' } },
    );
    const text = await response.text();
    return { status: response.status(), body: text.startsWith('{') ? JSON.parse(text) : { raw: text } };
}

export async function uploadOk(context: APIRequestContext, groupId: string, data: Buffer, fileName: string) {
    const result = await upload(context, groupId, data, fileName);
    expect(result.status, JSON.stringify(result.body)).toBe(201);
    return result.body as unknown as Resource;
}

export async function resources(context: APIRequestContext, groupId: string): Promise<Resource[]> {
    const response = await context.get(`/o/exelearning/v1/sites/${groupId}/resources`);
    expect(response.status()).toBe(200);
    return (await response.json()).items;
}

export async function remove(context: APIRequestContext, id: string) {
    await context.delete(`/o/exelearning/v1/resources/${id}`);
}

export async function publish(context: APIRequestContext, id: string, version = '') {
    const response = await context.post(`/o/exelearning/v1/resources/${id}/publish?version=${version}`);
    expect(response.status(), await response.text()).toBe(200);
    return (await response.json()) as Resource;
}

/** Content page with a configured eXeLearning widget (uses the headless site-pages API). */
export async function createPage(context: APIRequestContext, groupId: string, slug: string, fileEntryId: string) {
    const existing = await context.get(`/o/headless-delivery/v1.0/sites/${groupId}/site-pages/${slug}`);
    if (existing.ok()) {
        return;
    }
    const response = await context.post(`/o/headless-delivery/v1.0/sites/${groupId}/site-pages`, {
        data: {
            friendlyUrlPath: `/${slug}`,
            pageSettings: { hiddenFromNavigation: true },
            title: slug,
            pageDefinition: {
                pageElement: {
                    type: 'Root',
                    pageElements: [
                        {
                            type: 'Widget',
                            definition: {
                                widgetInstance: {
                                    widgetName: 'org_exelearning_liferay_portlet_ExeLearningViewerPortlet',
                                    widgetConfig: { fileEntryId, height: '600px', showDownload: 'true' },
                                },
                            },
                        },
                    ],
                },
            },
            viewableBy: 'Anyone',
        },
    });
    expect(response.status(), await response.text()).toBe(200);
}

export async function login(page: Page, credentials: { email: string; password: string }) {
    await page.goto('/c/portal/login');
    await page.fill('#_com_liferay_login_web_portlet_LoginPortlet_login', credentials.email);
    await page.fill('#_com_liferay_login_web_portlet_LoginPortlet_password', credentials.password);
    await Promise.all([
        page.waitForURL(url => !url.searchParams.has('p_p_id')),
        page.click('#_com_liferay_login_web_portlet_LoginPortlet_loginForm button[type=submit]'),
    ]);
}

export const MANAGER_PATH =
    '/group/exelearning-demo/~/control_panel/manage?p_p_id=org_exelearning_liferay_portlet_ExeLearningAdminPortlet';

/** Runs a command in the Liferay container (used to inspect the filesystem). */
export function inContainer(command: string): string {
    return execFileSync('docker', ['compose', 'exec', '-T', 'liferay', 'sh', '-c', command], {
        encoding: 'utf8',
    });
}

export function uniqueName(prefix: string) {
    return `${prefix}-${Date.now().toString(36)}.elpx`;
}
