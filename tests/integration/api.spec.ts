import { type APIRequestContext, expect, test } from '@playwright/test';
import {
    ADMIN,
    api,
    fixture,
    inContainer,
    publish,
    type Resource,
    remove,
    resources,
    siteId,
    uniqueName,
    upload,
    uploadOk,
    user,
} from '../support/liferay';

/**
 * Black-box integration tests against the deployed bundles: real Liferay
 * authentication, Documents and Media, permissions and the content servlet.
 */

let admin: APIRequestContext;
let groupId: string;
const created: string[] = [];

test.beforeAll(async () => {
    admin = await api(ADMIN);
    groupId = await siteId(admin);
});

test.afterAll(async () => {
    for (const id of created) {
        await remove(admin, id);
    }
    await admin.dispose();
});

async function track(resource: Resource) {
    created.push(resource.id);
    return resource;
}

function contentToken(url: string) {
    return url.split('/')[3] ?? '';
}

test.describe('deployment', () => {
    test('the bundles, REST API and editor are active', async () => {
        const anonymous = await api(null);
        expect((await anonymous.get('/o/exelearning-web/js/exelearning.js')).status()).toBe(200);
        expect((await anonymous.get('/o/exelearning-web/js/exelearning.css')).status()).toBe(200);

        const editor = await anonymous.get('/o/exelearning-editor/index.html?locale=es');
        expect(editor.status()).toBe(200);
        const html = await editor.text();
        expect(html).toContain('window.__EXE_EMBEDDING_CONFIG__=');
        expect(html).toContain('"trustedOrigins":["http://localhost');
        expect(html).toContain("locale={value:'es'}");
        expect(editor.headers()['content-security-policy']).toContain("frame-ancestors 'self'");
        expect(
            (await anonymous.get('/o/exelearning-editor/data/bundle.json.zst')).headers()['content-length'],
        ).not.toBe('0');

        expect((await admin.get('/o/exelearning/v1/resources/0')).status()).toBe(404);
        expect((await anonymous.get(`/o/exelearning/v1/sites/${groupId}/resources`)).status()).toBe(403);
        await anonymous.dispose();
    });
});

test.describe('import', () => {
    test('a valid package is validated, stored in Documents and Media and published atomically', async () => {
        const resource = await track(
            await uploadOk(admin, groupId, fixture('valid/really-simple.elpx'), uniqueName('integration')),
        );
        expect(resource).toMatchObject({
            title: expect.stringContaining('Really Simple Test Project'),
            author: 'eXe Team',
            language: 'en',
            license: 'creative commons: attribution - share alike 4.0',
            version: '1.0',
            publishable: true,
        });
        expect(resource.publishedVersion ?? null).toBeNull();

        const document = await admin.get(`/o/headless-delivery/v1.0/documents/${resource.id}`);
        expect(document.status()).toBe(200);
        const documentJson = await document.json();
        expect(documentJson.documentFolderId).toBeGreaterThan(0);
        expect(documentJson.fileExtension).toBe('elpx');

        const folder = await admin.get(`/o/headless-delivery/v1.0/document-folders/${documentJson.documentFolderId}`);
        expect((await folder.json()).name).toBe('eXeLearning');

        const original = await admin.get(`/o/exelearning/v1/resources/${resource.id}/package?version=1.0`, {
            headers: { Accept: 'application/zip' },
        });
        expect(Buffer.from(await original.body()).equals(fixture('valid/really-simple.elpx'))).toBe(true);
        expect(original.headers()['content-disposition']).toContain('attachment;');
    });

    test('packages without an HTML export are stored but cannot be published', async () => {
        const resource = await track(
            await uploadOk(admin, groupId, fixture('valid/source-only.elpx'), uniqueName('source-only')),
        );
        expect(resource.publishable).toBe(false);
        const response = await admin.post(`/o/exelearning/v1/resources/${resource.id}/publish`);
        expect(response.status()).toBe(400);
        expect((await response.json()).code).toBe('the-package-has-no-html-export');
    });
});

test.describe('malformed and adversarial packages', () => {
    const cases: [string, string][] = [
        ['zip-slip.elpx', 'the-package-contains-an-unsafe-path-x'],
        ['absolute-path.elpx', 'the-package-contains-an-unsafe-path-x'],
        ['windows-path.elpx', 'the-package-contains-an-unsafe-path-x'],
        ['server-script.elpx', 'the-package-contains-a-forbidden-file-x'],
        ['htaccess.elpx', 'the-package-contains-a-forbidden-file-x'],
        ['duplicate-entries.elpx', 'the-package-contains-duplicate-entries-x'],
        ['zip-bomb.elpx', 'the-package-compression-ratio-is-suspicious'],
        ['lying-sizes.elpx', 'the-package-compression-ratio-is-suspicious'],
        ['no-content-xml.elpx', 'the-file-is-not-an-exelearning-project'],
        ['legacy-elp.elpx', 'legacy-elp-packages-are-not-supported'],
        ['not-a-zip.elpx', 'the-file-is-not-a-valid-zip-archive'],
    ];

    for (const [file, code] of cases) {
        test(`${file} is rejected without leaving anything behind`, async () => {
            const before = (await resources(admin, groupId)).length;
            const result = await upload(admin, groupId, fixture(`adversarial/${file}`), uniqueName('adversarial'));
            expect(result.status).toBe(400);
            expect(result.body.code).toBe(code);
            expect(String(result.body.message).length).toBeGreaterThan(0);
            expect((await resources(admin, groupId)).length).toBe(before);
        });
    }

    test('nothing escapes the extraction area and no staging data is left', async () => {
        const escaped = inContainer(
            'ls /tmp/exelearning-escaped.html /tmp/exelearning-absolute.html /opt/liferay/data/exelearning-windows.html 2>/dev/null || true',
        );
        expect(escaped.trim()).toBe('');
        const staging = inContainer('ls -A /opt/liferay/data/exelearning/publications/.staging 2>/dev/null || true');
        expect(staging.trim()).toBe('');
    });

    test('external XML entities are never resolved', async () => {
        const result = await upload(admin, groupId, fixture('adversarial/xxe.elpx'), uniqueName('xxe'));
        if (result.status === 201) {
            created.push(String(result.body.id));
            expect(String(result.body.title)).not.toContain('root:');
        } else {
            expect(result.body.code).toBe('the-package-metadata-could-not-be-read');
        }
    });

    test('non-elpx file names and missing bodies are rejected', async () => {
        expect((await upload(admin, groupId, fixture('valid/really-simple.elpx'), 'course.zip')).body.code).toBe(
            'only-elpx-files-are-supported',
        );
        expect((await upload(admin, groupId, Buffer.alloc(0), uniqueName('empty'))).status).toBe(400);
    });
});

test.describe('publication and protected delivery', () => {
    test('published content is served in a sandbox through signed URLs only', async () => {
        const resource = await track(
            await uploadOk(admin, groupId, fixture('valid/really-simple.elpx'), uniqueName('delivery')),
        );
        const preview = await admin.post(`/o/exelearning/v1/resources/${resource.id}/preview`);
        const { url } = await preview.json();
        expect(url).toMatch(/^\/o\/exelearning-content\/\d+-1\.0-\d+-[0-9a-f]{64}\/index\.html$/);

        const anonymous = await api(null);
        const page = await anonymous.get(url);
        expect(page.status()).toBe(200);
        const headers = page.headers();
        expect(headers['content-security-policy']).toMatch(/^sandbox allow-scripts allow-popups allow-forms/);
        expect(headers['content-security-policy']).not.toContain('allow-same-origin');
        expect(headers['x-content-type-options']).toBe('nosniff');
        expect(headers['content-type']).toBe('text/html;charset=utf-8');
        expect(await page.text()).toContain('Really Simple Test Project');

        const base = url.replace(/index\.html$/, '');
        const nested = await anonymous.get(`${base}html/page-1-1.html`);
        expect(nested.status()).toBe(200);
        const css = await anonymous.get(`${base}content/css/base.css`);
        expect(css.headers()['content-type']).toBe('text/css;charset=utf-8');

        const token = contentToken(url);
        const forged = token.slice(0, -1) + (token.endsWith('0') ? '1' : '0');
        expect((await anonymous.get(`/o/exelearning-content/${forged}/index.html`)).status()).toBe(403);
        const otherVersion = token.replace('-1.0-', '-1.1-');
        expect((await anonymous.get(`/o/exelearning-content/${otherVersion}/index.html`)).status()).toBe(403);
        expect([400, 404]).toContain((await anonymous.get(`${base}..%2F..%2Fcontent.xml`)).status());
        expect((await anonymous.get(`${base}html/../../../../etc/passwd`)).status()).toBe(404);
        expect((await anonymous.get(`${base}missing.html`)).status()).toBe(404);

        const range = await anonymous.get(`${base}content.xml`, { headers: { Range: 'bytes=0-4' } });
        expect(range.status()).toBe(206);
        expect(await range.text()).toBe('<?xml');
        await anonymous.dispose();
    });

    test('updates create versions, reject stale editors and keep the published revision until republished', async () => {
        const resource = await track(
            await uploadOk(admin, groupId, fixture('valid/really-simple.elpx'), uniqueName('versions')),
        );
        await publish(admin, resource.id, '1.0');

        const missing = await admin.put(`/o/exelearning/v1/resources/${resource.id}/package?fileName=x.elpx`, {
            data: fixture('valid/true-or-false.elpx'),
            headers: { 'Content-Type': 'application/octet-stream' },
        });
        expect(missing.status()).toBe(428);

        const save = (version: string) =>
            admin.put(`/o/exelearning/v1/resources/${resource.id}/package?fileName=x.elpx`, {
                data: fixture('valid/true-or-false.elpx'),
                headers: { 'Content-Type': 'application/octet-stream', 'If-Match': `"${version}"` },
            });

        const updated = await save('1.0');
        expect(updated.status()).toBe(200);
        expect(await updated.json()).toMatchObject({ version: '1.1', publishedVersion: '1.0', title: resource.title });

        const stale = await save('1.0');
        expect(stale.status()).toBe(409);
        expect((await stale.json()).code).toBe('the-resource-was-changed-by-someone-else');

        const details = await (await admin.get(`/o/exelearning/v1/resources/${resource.id}`)).json();
        expect(details.versions.map((v: { version: string }) => v.version)).toEqual(['1.1', '1.0']);

        const invalid = await admin.put(`/o/exelearning/v1/resources/${resource.id}/package?fileName=x.elpx`, {
            data: fixture('adversarial/zip-slip.elpx'),
            headers: { 'Content-Type': 'application/octet-stream', 'If-Match': '"1.1"' },
        });
        expect(invalid.status()).toBe(400);
        expect((await (await admin.get(`/o/exelearning/v1/resources/${resource.id}`)).json()).version).toBe('1.1');

        const republished = await publish(admin, resource.id);
        expect(republished.publishedVersion).toBe('1.1');
    });
});

test.describe('authorization', () => {
    test('roles map to Liferay permissions at every endpoint', async () => {
        const viewer = await api(user('viewer'));
        const author = await api(user('author'));
        const editor = await api(user('editor'));
        const anonymous = await api(null);

        const published = await track(
            await uploadOk(admin, groupId, fixture('valid/really-simple.elpx'), uniqueName('authz')),
        );
        await publish(admin, published.id, '1.0');
        const draft = await track(
            await uploadOk(admin, groupId, fixture('valid/really-simple.elpx'), uniqueName('authz-draft')),
        );

        // Anonymous users have no REST access at all.
        expect((await anonymous.get(`/o/exelearning/v1/resources/${published.id}`)).status()).toBe(403);

        // Viewers see published resources only and cannot change anything.
        const viewerList = await (await viewer.get(`/o/exelearning/v1/sites/${groupId}/resources`)).json();
        expect(viewerList.canAdd).toBe(false);
        expect(viewerList.items.map((r: Resource) => r.id)).toContain(published.id);
        expect(viewerList.items.map((r: Resource) => r.id)).not.toContain(draft.id);
        expect((await viewer.get(`/o/exelearning/v1/resources/${draft.id}`)).status()).toBe(404);
        expect((await upload(viewer, groupId, fixture('valid/really-simple.elpx'), uniqueName('viewer'))).status).toBe(
            403,
        );
        expect((await viewer.post(`/o/exelearning/v1/resources/${published.id}/publish`)).status()).toBe(403);
        expect((await viewer.post(`/o/exelearning/v1/resources/${published.id}/preview`)).status()).toBe(403);
        expect((await viewer.delete(`/o/exelearning/v1/resources/${published.id}`)).status()).toBe(403);
        expect((await viewer.get(`/o/exelearning/v1/resources/${published.id}/package?version=1.0`)).status()).toBe(
            200,
        );

        // Authors can add packages and manage their own, but not other people's.
        const own = await uploadOk(author, groupId, fixture('valid/really-simple.elpx'), uniqueName('author'));
        created.push(own.id);
        expect(own.permissions).toEqual({ update: true, delete: true });
        expect((await author.post(`/o/exelearning/v1/resources/${own.id}/publish`)).status()).toBe(200);
        expect((await author.post(`/o/exelearning/v1/resources/${published.id}/publish`)).status()).toBe(403);
        expect((await author.get(`/o/exelearning/v1/resources/${draft.id}`)).status()).toBe(404);

        // Editors can update and publish every resource of the site but not delete.
        expect((await editor.post(`/o/exelearning/v1/resources/${draft.id}/publish`)).status()).toBe(200);
        expect((await editor.delete(`/o/exelearning/v1/resources/${published.id}`)).status()).toBe(403);

        for (const context of [viewer, author, editor, anonymous]) {
            await context.dispose();
        }
    });

    test('resources of a restricted site are invisible to non-members', async () => {
        const site = await admin.post('/o/headless-site/v1.0/sites', {
            data: {
                externalReferenceCode: `exelearning-private-${Date.now()}`,
                friendlyUrlPath: `/exelearning-private-${Date.now()}`,
                membershipType: 'private',
                name: `eXeLearning Private ${Date.now()}`,
            },
        });
        expect(site.status(), await site.text()).toBe(200);
        const privateSite = String((await site.json()).id);
        const resource = await uploadOk(admin, privateSite, fixture('valid/really-simple.elpx'), uniqueName('private'));
        await publish(admin, resource.id, '1.0');

        // The folder the integration creates only lets site members view packages.
        const folders = await (
            await admin.get(`/o/headless-delivery/v1.0/sites/${privateSite}/document-folders`)
        ).json();
        const folder = folders.items.find((item: { name: string }) => item.name === 'eXeLearning');
        const permissions = await (
            await admin.get(`/o/headless-delivery/v1.0/document-folders/${folder.id}/permissions`)
        ).json();
        const byRole = Object.fromEntries(
            permissions.items.map((item: { roleName: string; actionIds: string[] }) => [item.roleName, item.actionIds]),
        );
        expect(byRole['Site Member']).toEqual(['VIEW']);
        expect(byRole.Guest).toBeUndefined();

        const viewer = await api(user('viewer'));
        expect((await viewer.get(`/o/exelearning/v1/resources/${resource.id}`)).status()).toBe(404);
        const list = await viewer.get(`/o/exelearning/v1/sites/${privateSite}/resources`);
        expect(list.status()).toBe(403);
        expect((await viewer.get(`/o/exelearning/v1/sites/999999999/resources`)).status()).toBe(404);
        await viewer.dispose();

        await remove(admin, resource.id);
        await admin.delete(`/o/headless-site/v1.0/sites/${privateSite}`);
    });

    test('browser sessions need the CSRF token for REST calls', async ({ page }) => {
        await page.goto('/c/portal/login');
        await page.fill('#_com_liferay_login_web_portlet_LoginPortlet_login', ADMIN.email);
        await page.fill('#_com_liferay_login_web_portlet_LoginPortlet_password', ADMIN.password);
        await Promise.all([
            page.waitForURL(url => !url.searchParams.has('p_p_id')),
            page.click('#_com_liferay_login_web_portlet_LoginPortlet_loginForm button[type=submit]'),
        ]);
        const result = await page.evaluate(async path => {
            const token = (window as unknown as { Liferay: { authToken: string } }).Liferay.authToken;
            const withoutToken = await fetch(path, { credentials: 'same-origin' });
            const withToken = await fetch(path, { credentials: 'same-origin', headers: { 'x-csrf-token': token } });
            return [withoutToken.status, withToken.status];
        }, `/o/exelearning/v1/sites/${groupId}/resources`);
        expect(result).toEqual([403, 200]);
    });
});
