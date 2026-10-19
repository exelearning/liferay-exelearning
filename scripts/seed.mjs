// Idempotent demo data for the development stack. Existing objects are found
// by external reference code, e-mail address or friendly URL and never
// overwritten, so "make seed" can run repeatedly without touching user data.
import { readFile } from 'node:fs/promises';

export const SITE_ERC = 'exelearning-demo';
export const DEMO_FILE_NAME = 'exelearning-demo.elpx';
export const DEMO_FIXTURE = new URL('../tests/fixtures/valid/multipage-interactive.elpx', import.meta.url);
export const VIEWER_PORTLET = 'org_exelearning_liferay_portlet_ExeLearningViewerPortlet';
export const ADMIN_PORTLET = 'org_exelearning_liferay_portlet_ExeLearningAdminPortlet';

const SITE_ROLE_SCOPE = 3;

export const USERS = [
    { key: 'viewer', givenName: 'Demo', familyName: 'Viewer', role: null },
    { key: 'author', givenName: 'Demo', familyName: 'Author', role: 'exelearning-author' },
    { key: 'editor', givenName: 'Demo', familyName: 'Editor', role: 'exelearning-editor' },
];

export function userEmail(key) {
    return `demo.${key}@exelearning.example.com`;
}

const authorPermissions = [
    { resourceName: ADMIN_PORTLET, actionIds: ['ACCESS_IN_CONTROL_PANEL', 'VIEW'] },
    { resourceName: 'com.liferay.document.library', actionIds: ['ADD_DOCUMENT', 'VIEW'] },
    { resourceName: 'com.liferay.document.library.kernel.model.DLFolder', actionIds: ['ADD_DOCUMENT', 'VIEW'] },
];

const ROLES = {
    'exelearning-author': { name: 'eXeLearning Author', permissions: authorPermissions },
    'exelearning-editor': {
        name: 'eXeLearning Editor',
        permissions: [
            ...authorPermissions,
            { resourceName: 'com.liferay.document.library.kernel.model.DLFileEntry', actionIds: ['UPDATE', 'VIEW'] },
        ],
    },
};

export const PAGES = [
    { friendlyUrlPath: 'exelearning-demo', title: 'eXeLearning Demo' },
    { friendlyUrlPath: 'exelearning-reuse', title: 'eXeLearning Reuse' },
];

export async function json(request, path, init = {}, allow = []) {
    const headers = new Headers(init.headers);
    if (init.body && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }
    const response = await request(path, { ...init, headers });
    if (allow.includes(response.status)) {
        return null;
    }
    const text = await response.text();
    if (!response.ok) {
        throw new Error(`${init.method ?? 'GET'} ${path} failed with ${response.status}: ${text.slice(0, 500)}`);
    }
    return text ? JSON.parse(text) : null;
}

async function ensureSite(request) {
    const existing = await json(
        request,
        `/o/headless-site/v1.0/sites/by-external-reference-code/${SITE_ERC}`,
        {},
        [404],
    );
    if (existing) {
        return existing;
    }
    console.log('Creating site "eXeLearning Demo"');
    return json(request, '/o/headless-site/v1.0/sites', {
        method: 'POST',
        body: JSON.stringify({
            externalReferenceCode: SITE_ERC,
            friendlyUrlPath: '/exelearning-demo',
            membershipType: 'open',
            name: 'eXeLearning Demo',
        }),
    });
}

async function ensureRole(request, erc) {
    const existing = await json(
        request,
        `/o/headless-admin-user/v1.0/roles/by-external-reference-code/${erc}`,
        {},
        [404],
    );
    if (existing) {
        return existing;
    }
    const { name, permissions } = ROLES[erc];
    console.log(`Creating site role "${name}"`);
    return json(request, '/o/headless-admin-user/v1.0/roles', {
        method: 'POST',
        body: JSON.stringify({
            externalReferenceCode: erc,
            name,
            name_i18n: { en_US: name },
            roleType: 'site',
            rolePermissions: permissions.map(p => ({ ...p, primaryKey: '0', scope: SITE_ROLE_SCOPE })),
        }),
    });
}

async function ensureUser(request, user, password) {
    const email = userEmail(user.key);
    const existing = await json(
        request,
        `/o/headless-admin-user/v1.0/user-accounts/by-email-address/${encodeURIComponent(email)}`,
        {},
        [404],
    );
    if (existing) {
        return existing;
    }
    console.log(`Creating user ${email}`);
    return json(request, '/o/headless-admin-user/v1.0/user-accounts', {
        method: 'POST',
        body: JSON.stringify({
            alternateName: `demo-${user.key}`,
            emailAddress: email,
            familyName: user.familyName,
            givenName: user.givenName,
            password,
        }),
    });
}

async function ensureMembership(request, siteId, userIds) {
    const form = new URLSearchParams({ groupId: String(siteId), userIds: JSON.stringify(userIds) });
    await json(request, '/api/jsonws/user/add-group-users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: form.toString(),
    });
}

async function ensureDemoResource(request, siteId) {
    const list = await json(request, `/o/exelearning/v1/sites/${siteId}/resources`);
    let resource = list.items.find(item => item.fileName === DEMO_FILE_NAME);
    if (!resource) {
        console.log('Uploading the demo eXeLearning resource');
        resource = await json(request, `/o/exelearning/v1/sites/${siteId}/resources?fileName=${DEMO_FILE_NAME}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/octet-stream' },
            body: await readFile(DEMO_FIXTURE),
        });
    }
    if (!resource.publishedVersion) {
        console.log(`Publishing version ${resource.version}`);
        resource = await json(request, `/o/exelearning/v1/resources/${resource.id}/publish`, { method: 'POST' });
    }
    return resource;
}

async function ensurePage(request, siteId, page, fileEntryId) {
    const existing = await json(
        request,
        `/o/headless-delivery/v1.0/sites/${siteId}/site-pages/${page.friendlyUrlPath}`,
        {},
        [404],
    );
    if (existing) {
        return existing;
    }
    console.log(`Creating page /${page.friendlyUrlPath}`);
    return json(request, `/o/headless-delivery/v1.0/sites/${siteId}/site-pages`, {
        method: 'POST',
        body: JSON.stringify({
            friendlyUrlPath: `/${page.friendlyUrlPath}`,
            title: page.title,
            pageDefinition: {
                pageElement: {
                    type: 'Root',
                    pageElements: [
                        {
                            type: 'Widget',
                            definition: {
                                widgetInstance: {
                                    widgetName: VIEWER_PORTLET,
                                    widgetConfig: { fileEntryId: String(fileEntryId), height: '650px' },
                                },
                            },
                        },
                    ],
                },
            },
            viewableBy: 'Anyone',
        }),
    });
}

export async function seed({ request }) {
    const password = process.env.SEED_USER_PASSWORD;
    if (!password) {
        throw new Error('Set SEED_USER_PASSWORD in .env');
    }
    const site = await ensureSite(request);
    const roles = {};
    for (const erc of Object.keys(ROLES)) {
        roles[erc] = await ensureRole(request, erc);
    }
    const users = {};
    for (const user of USERS) {
        users[user.key] = await ensureUser(request, user, password);
    }
    await ensureMembership(
        request,
        site.id,
        Object.values(users).map(user => user.id),
    );
    for (const user of USERS) {
        if (user.role) {
            await json(
                request,
                `/o/headless-admin-user/v1.0/roles/${roles[user.role].id}/association/user-account/${users[user.key].id}/site/${site.id}`,
                { method: 'POST' },
            );
        }
    }
    const resource = await ensureDemoResource(request, site.id);
    for (const page of PAGES) {
        await ensurePage(request, site.id, page, resource.id);
    }
    console.log(`Seed complete: site ${site.id}, resource ${resource.id} (published ${resource.publishedVersion}).`);
    console.log(`Open ${process.env.LIFERAY_URL ?? 'http://localhost:8080'}/web/exelearning-demo/exelearning-demo`);
}
