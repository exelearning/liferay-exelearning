import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExeApi } from '../src/api';
import type { ExeLearningEditor } from '../src/editor-element';
import { define } from '../src/index';
import { EDITOR_ORIGIN, fakeFetch, flush, type Route, resourceJson } from './harness';

define();

function mount(routes: Route[], options: { api?: boolean } = { api: true }) {
    const fetch = fakeFetch(routes);
    const element = document.createElement('exelearning-editor') as ExeLearningEditor;
    element.setAttribute('resource-id', '101');
    element.setAttribute('editor-url', `${EDITOR_ORIGIN}/o/exelearning-editor/index.html`);
    element.setAttribute('back-url', '/group/site/~/control_panel/manage');
    element.setAttribute(
        'data-i18n',
        JSON.stringify({ 'editor-could-not-be-loaded': 'Could not load', 'edit-x': 'Edit {0}' }),
    );
    if (options.api) {
        element.api = new ExeApi({ base: '/o/exelearning/v1', csrfToken: 'tok', fetchImpl: fetch.impl });
    }
    element.navigate = vi.fn();
    document.body.append(element);
    return { element, fetch };
}

beforeEach(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
        this.setAttribute('open', '');
    };
});

afterEach(() => {
    document.body.replaceChildren();
});

describe('exelearning-editor', () => {
    it('opens the editor for the resource and returns to the back URL on close', async () => {
        const { element } = mount([
            { path: '/resources/101', body: resourceJson() },
            { path: '/resources/101/package?version=1.0', raw: new Uint8Array([1]) },
        ]);
        await flush(10);
        expect(element.editor).not.toBeNull();
        const dialog = element.querySelector('dialog.exe-editor-dialog');
        expect(dialog?.getAttribute('aria-label')).toContain('Demo resource');

        element.editor?.close();
        expect(element.navigate).toHaveBeenCalledWith('/group/site/~/control_panel/manage');

        element.remove();
        document.body.append(element);
        await flush();
        expect(element.querySelectorAll('dialog')).toHaveLength(0);
    });

    it('shows an error when the resource cannot be loaded', async () => {
        const { element } = mount([
            { path: '/resources/101', status: 404, body: { code: 'not-found', message: 'Missing' } },
        ]);
        await flush(10);
        expect(element.querySelector('[role=alert]')?.textContent).toBe('Could not load (Missing)');
        expect(element.editor).toBeNull();
    });

    it('creates its own client with the Liferay CSRF token', async () => {
        const original = globalThis.fetch;
        const fetch = fakeFetch([{ path: '/o/exelearning/v1/resources/101', status: 500, body: 'x' }]);
        globalThis.fetch = fetch.impl;
        (window as unknown as { Liferay: { authToken: string } }).Liferay = { authToken: 'liferay-token' };
        try {
            const { element } = mount([], { api: false });
            await flush(10);
            expect(fetch.requests[0]?.headers.get('x-csrf-token')).toBe('liferay-token');
            expect(element.querySelector('[role=alert]')).not.toBeNull();
            element.removeAttribute('back-url');
        } finally {
            globalThis.fetch = original;
        }
    });

    it('reports non-Error failures and falls back to defaults', async () => {
        const element = document.createElement('exelearning-editor') as ExeLearningEditor;
        element.api = { get: () => Promise.reject('offline') } as unknown as ExeApi;
        document.body.append(element);
        await flush(10);
        expect(element.querySelector('[role=alert]')?.textContent).toContain('(offline)');
    });
});
