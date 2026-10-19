import { vi } from 'vitest';

export const EDITOR_ORIGIN = 'https://portal.example';

/** Fake editor iframe: records posted messages and can reply as the editor window. */
export function editorHarness(origin = EDITOR_ORIGIN) {
    const posted: { message: Record<string, unknown>; targetOrigin: string }[] = [];
    const editorWindow = {
        postMessage: vi.fn((message: Record<string, unknown>, targetOrigin: string) => {
            posted.push({ message, targetOrigin });
        }),
    };
    const iframe = document.createElement('iframe');
    Object.defineProperty(iframe, 'contentWindow', { configurable: true, value: editorWindow });
    const host = new EventTarget() as unknown as Window;

    const send = (data: unknown, options: { origin?: string; source?: unknown } = {}) => {
        const event = new Event('message');
        Object.assign(event, {
            data,
            origin: options.origin ?? origin,
            source: 'source' in options ? options.source : editorWindow,
        });
        host.dispatchEvent(event);
    };

    const lastRequest = () => {
        const entry = posted.at(-1);
        if (!entry) {
            throw new Error('No message posted');
        }
        return entry.message as { type: string; requestId: string; data?: Record<string, unknown> };
    };

    return { editorWindow, host, iframe, lastRequest, posted, send };
}

export interface Route {
    method?: string;
    path: string | RegExp;
    status?: number;
    body?: unknown;
    raw?: BodyInit;
}

/** Minimal fetch double that answers from a route table and records requests. */
export function fakeFetch(routes: Route[]) {
    const requests: { method: string; url: string; headers: Headers; body: unknown }[] = [];
    const impl = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
        const url = String(input);
        const method = init.method ?? 'GET';
        requests.push({ method, url, headers: new Headers(init.headers), body: init.body });
        const route = routes.find(
            r =>
                (r.method ?? 'GET') === method &&
                (typeof r.path === 'string' ? url.endsWith(r.path) : r.path.test(url)),
        );
        if (!route) {
            return new Response(JSON.stringify({ code: 'not-found', message: `No route ${method} ${url}` }), {
                status: 404,
            });
        }
        const body = route.raw ?? (route.body === undefined ? null : JSON.stringify(route.body));
        return new Response(body, { status: route.status ?? 200 });
    });
    return { impl: impl as unknown as typeof fetch, requests };
}

export function resourceJson(overrides: Record<string, unknown> = {}) {
    return {
        author: 'Ana',
        description: 'Demo',
        exelearningVersion: 'v4.0.5',
        fileName: 'demo.elpx',
        groupId: '20117',
        id: '101',
        language: 'es',
        license: 'CC BY-SA 4.0',
        modifiedDate: '2026-10-19T10:00:00Z',
        permissions: { delete: true, update: true },
        publishable: true,
        publishedVersion: '1.0',
        size: '1234',
        title: 'Demo resource',
        version: '1.0',
        versions: [
            { changeLog: 'upload', createDate: '2026-10-19T10:00:00Z', size: '1234', userName: 'Ana', version: '1.0' },
        ],
        ...overrides,
    };
}

export async function flush(times = 5) {
    for (let i = 0; i < times; i++) {
        await new Promise(resolve => setTimeout(resolve, 0));
    }
}
