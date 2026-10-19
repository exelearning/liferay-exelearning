import { describe, expect, it } from 'vitest';
import { ApiError, ExeApi } from '../src/api';
import { fakeFetch, resourceJson } from './harness';

function api(routes: Parameters<typeof fakeFetch>[0]) {
    const fetch = fakeFetch(routes);
    return { client: new ExeApi({ base: '/o/exelearning/v1/', csrfToken: 'tok', fetchImpl: fetch.impl }), fetch };
}

describe('ExeApi', () => {
    it('lists resources and restores numeric fields', async () => {
        const { client, fetch } = api([
            {
                path: '/o/exelearning/v1/sites/7/resources?keywords=a%20b',
                body: { canAdd: true, items: [resourceJson()] },
            },
        ]);
        const result = await client.list(7, 'a b');
        expect(result.canAdd).toBe(true);
        expect(result.items[0]).toMatchObject({ id: 101, groupId: 20117, size: 1234 });
        expect(result.items[0]?.versions?.[0]?.size).toBe(1234);
        const request = fetch.requests[0];
        expect(request?.headers.get('x-csrf-token')).toBe('tok');
        expect(request?.headers.get('Accept')).toBe('application/json');
    });

    it('lists without keywords and treats a missing canAdd as false', async () => {
        const { client } = api([{ path: '/o/exelearning/v1/sites/7/resources', body: { items: [] } }]);
        await expect(client.list(7)).resolves.toEqual({ canAdd: false, items: [] });
    });

    it('uploads, saves with If-Match, publishes, unpublishes and deletes', async () => {
        const { client, fetch } = api([
            { method: 'POST', path: '/sites/7/resources?fileName=a%20b.elpx', status: 201, body: resourceJson() },
            {
                method: 'PUT',
                path: '/resources/101/package?fileName=demo.elpx',
                body: resourceJson({ version: '1.1' }),
            },
            {
                method: 'POST',
                path: '/resources/101/publish?version=1.1',
                body: resourceJson({ publishedVersion: '1.1' }),
            },
            { method: 'POST', path: '/resources/101/unpublish', body: resourceJson({ publishedVersion: null }) },
            { method: 'DELETE', path: '/resources/101', status: 204 },
            { path: '/resources/101', body: resourceJson() },
        ]);
        const file = new Blob(['zip']);
        expect((await client.upload(7, file, 'a b.elpx')).id).toBe(101);
        expect(fetch.requests[0]?.headers.get('Content-Type')).toBe('application/octet-stream');
        expect(fetch.requests[0]?.body).toBe(file);

        expect((await client.saveRevision(101, new ArrayBuffer(1), 'demo.elpx', '1.0')).version).toBe('1.1');
        expect(fetch.requests[1]?.headers.get('If-Match')).toBe('"1.0"');

        expect((await client.publish(101, '1.1')).publishedVersion).toBe('1.1');
        expect((await client.unpublish(101)).publishedVersion).toBeNull();
        await expect(client.remove(101)).resolves.toBeUndefined();
        expect((await client.get(101)).title).toBe('Demo resource');
    });

    it('downloads package bytes with a zip Accept header', async () => {
        const { client, fetch } = api([{ path: '/resources/101/package?version=1.0', raw: new Uint8Array([1, 2]) }]);
        const bytes = await client.download(101, '1.0');
        expect(new Uint8Array(bytes)).toEqual(new Uint8Array([1, 2]));
        expect(fetch.requests[0]?.headers.get('Accept')).toBe('application/zip');
    });

    it('builds download links with p_auth', () => {
        const { client } = api([]);
        expect(client.packageUrl(5)).toBe('/o/exelearning/v1/resources/5/package?p_auth=tok');
        expect(client.packageUrl(5, '1.2')).toBe('/o/exelearning/v1/resources/5/package?version=1.2&p_auth=tok');
        const anonymous = new ExeApi({ base: '/api', csrfToken: '' });
        expect(anonymous.packageUrl(5)).toBe('/api/resources/5/package');
    });

    it('requests preview URLs', async () => {
        const { client } = api([
            {
                method: 'POST',
                path: '/resources/101/preview?version=1.0',
                body: { url: '/o/exelearning-content/t/index.html' },
            },
        ]);
        await expect(client.previewUrl(101, '1.0')).resolves.toBe('/o/exelearning-content/t/index.html');
    });

    it('maps error responses to ApiError with server messages', async () => {
        const { client } = api([
            { method: 'PUT', path: /package/, status: 409, body: { code: 'conflict', message: 'Changed' } },
            { path: '/resources/1', status: 500, raw: 'oops' },
            { path: '/resources/2', status: 400, body: ['unexpected'] },
            { path: '/resources/3', status: 403, body: { code: 1, message: 2 } },
        ]);
        const conflict = await client.saveRevision(9, new ArrayBuffer(0), 'x.elpx', '1.0').catch(e => e);
        expect(conflict).toBeInstanceOf(ApiError);
        expect(conflict).toMatchObject({ status: 409, code: 'conflict', message: 'Changed' });
        await expect(client.get(1)).rejects.toMatchObject({ status: 500, code: 'http.500' });
        await expect(client.get(2)).rejects.toMatchObject({ status: 400, code: 'http.400' });
        await expect(client.get(3)).rejects.toMatchObject({ status: 403, code: 'http.403' });
    });

    it('uses the global fetch by default', async () => {
        const original = globalThis.fetch;
        globalThis.fetch = fakeFetch([{ path: '/x/resources/4', body: resourceJson({ id: '4' }) }]).impl;
        try {
            expect((await new ExeApi({ base: '/x', csrfToken: '' }).get(4)).id).toBe(4);
        } finally {
            globalThis.fetch = original;
        }
    });
});
