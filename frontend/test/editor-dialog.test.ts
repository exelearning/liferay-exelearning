import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExeApi } from '../src/api';
import { EditorDialog } from '../src/editor-dialog';
import { createTranslator } from '../src/i18n';
import { EDITOR_ORIGIN, fakeFetch, flush, resourceJson } from './harness';

const t = createTranslator(
    JSON.stringify({
        'saved-as-version-x': 'Saved as version {0}.',
        'resource-changed-conflict': 'Conflict',
        'the-resource-could-not-be-saved': 'Not saved',
        'editor-could-not-be-loaded': 'Not loaded',
        'discard-unsaved-changes': 'Discard?',
    }),
);

const open: EditorDialog[] = [];

function setup(
    routes: Parameters<typeof fakeFetch>[0] = [],
    confirm: ((message: string) => boolean) | null = vi.fn(() => true),
) {
    const fetch = fakeFetch([{ path: '/resources/101/package?version=1.0', raw: new Uint8Array([80, 75]) }, ...routes]);
    const api = new ExeApi({ base: '/o/exelearning/v1', csrfToken: 'tok', fetchImpl: fetch.impl });
    const resource = { ...resourceJson(), id: 101, groupId: 1, size: 1, versions: [] };
    const onSaved = vi.fn();
    const onClosed = vi.fn();
    const dialog = new EditorDialog({
        api,
        resource: { ...resource, publishedVersion: '1.0' },
        editorUrl: `${EDITOR_ORIGIN}/o/exelearning-editor/index.html?locale=es`,
        t,
        onSaved,
        onClosed,
        confirm: confirm ?? undefined,
        loadTimeoutMs: 1000,
        requestTimeoutMs: 1000,
    });
    open.push(dialog);
    const posted: { type: string; requestId: string; data?: Record<string, unknown> }[] = [];
    const editorWindow = { postMessage: vi.fn((message: (typeof posted)[number]) => posted.push(message)) };
    Object.defineProperty(dialog.iframe, 'contentWindow', { configurable: true, value: editorWindow });
    const send = (data: unknown) =>
        window.dispatchEvent(
            Object.assign(new Event('message'), { data, origin: EDITOR_ORIGIN, source: editorWindow }),
        );
    const last = () => {
        const message = posted.at(-1);
        if (!message) {
            throw new Error('nothing posted');
        }
        return message;
    };
    return { dialog, fetch, last, onClosed, onSaved, posted, send };
}

async function load(ctx: ReturnType<typeof setup>) {
    const opened = ctx.dialog.open();
    await flush();
    ctx.send({ type: 'EXELEARNING_READY', version: 'v4.0.5', capabilities: [] });
    ctx.send({ type: 'DOCUMENT_LOADED', projectId: null, isDirty: false, pageCount: 3 });
    await flush();
    ctx.send({ type: 'OPEN_FILE_SUCCESS', requestId: ctx.last().requestId, projectId: 'p' });
    await opened;
}

beforeEach(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
        this.setAttribute('open', '');
    };
});

afterEach(() => {
    for (const dialog of open.splice(0)) {
        Object.defineProperty(dialog, 'dirty', { value: false });
        dialog.close();
    }
    document.body.replaceChildren();
});

describe('EditorDialog', () => {
    it('loads the stored package into the editor', async () => {
        const ctx = setup();
        await load(ctx);
        expect(ctx.dialog.state).toBe('ready');
        expect(ctx.dialog.iframe.getAttribute('src')).toContain('/o/exelearning-editor/index.html');
        const open = ctx.posted.find(m => m.type === 'OPEN_FILE');
        expect(open?.data?.filename).toBe('demo.elpx');
        expect(new Uint8Array(open?.data?.bytes as ArrayBuffer)).toEqual(new Uint8Array([80, 75]));
        expect(ctx.fetch.requests[0]?.headers.get('Accept')).toBe('application/zip');
        expect(document.body.contains(ctx.dialog.dialog)).toBe(true);
        expect(ctx.dialog.dialog.querySelector('button.btn-primary')?.hasAttribute('disabled')).toBe(false);
    });

    it('reports a load failure and keeps saving disabled', async () => {
        const ctx = setup();
        Object.defineProperty(ctx.dialog.iframe, 'contentWindow', { value: null });
        const opened = ctx.dialog.open();
        ctx.send({ type: 'EXELEARNING_READY' });
        await opened;
        expect(ctx.dialog.state).toBe('error');
        expect(ctx.dialog.dialog.textContent).toContain('Not loaded');
        expect(await ctx.dialog.save()).toBe(false);
    });

    it('persists the saved project as a new revision and reports success only then', async () => {
        const ctx = setup([
            {
                method: 'PUT',
                path: '/resources/101/package?fileName=demo.elpx',
                body: resourceJson({ version: '1.1' }),
            },
        ]);
        await load(ctx);
        ctx.send({ type: 'EXELEARNING_EVENT', event: 'PROJECT_DIRTY', data: { isDirty: true } });
        expect(ctx.dialog.isDirty).toBe(true);
        expect(ctx.dialog.dialog.hasAttribute('data-dirty')).toBe(true);

        const saving = ctx.dialog.save();
        expect(ctx.dialog.state).toBe('saving');
        expect(await ctx.dialog.save()).toBe(false);
        await flush();
        const request = ctx.last();
        expect(request.type).toBe('REQUEST_SAVE');
        ctx.send({
            type: 'SAVE_FILE',
            requestId: request.requestId,
            bytes: new Uint8Array([1]).buffer,
            filename: 'x.elpx',
        });
        expect(await saving).toBe(true);

        const put = ctx.fetch.requests.find(r => r.method === 'PUT');
        expect(put?.headers.get('If-Match')).toBe('"1.0"');
        expect(ctx.onSaved).toHaveBeenCalledWith(expect.objectContaining({ version: '1.1' }));
        expect(ctx.dialog.isDirty).toBe(false);
        expect(ctx.dialog.dialog.textContent).toContain('Saved as version 1.1.');
    });

    it('keeps the changes dirty and explains a concurrent edit conflict', async () => {
        const ctx = setup([{ method: 'PUT', path: /package/, status: 409, body: { code: 'x', message: 'Changed' } }]);
        await load(ctx);
        ctx.send({ type: 'EXELEARNING_EVENT', event: 'PROJECT_DIRTY' });
        const saving = ctx.dialog.save();
        await flush();
        ctx.send({ type: 'SAVE_FILE', requestId: ctx.last().requestId, bytes: new Uint8Array([1]).buffer });
        expect(await saving).toBe(false);
        expect(ctx.dialog.state).toBe('error');
        expect(ctx.dialog.isDirty).toBe(true);
        expect(ctx.dialog.dialog.textContent).toContain('Conflict');
        expect(ctx.onSaved).not.toHaveBeenCalled();
    });

    it('reports server and editor failures without claiming success', async () => {
        const ctx = setup([{ method: 'PUT', path: /package/, status: 500, body: { code: 'e', message: 'Boom' } }]);
        await load(ctx);
        const first = ctx.dialog.save();
        await flush();
        ctx.send({ type: 'SAVE_FILE', requestId: ctx.last().requestId, bytes: new Uint8Array([1]).buffer });
        expect(await first).toBe(false);
        expect(ctx.dialog.dialog.textContent).toContain('Not saved (Boom)');

        const second = ctx.dialog.save();
        await flush();
        ctx.send({ type: 'REQUEST_SAVE_ERROR', requestId: ctx.last().requestId, error: 'nope' });
        await flush();
        ctx.send({ type: 'REQUEST_EXPORT_ERROR', requestId: ctx.last().requestId, error: 'still nope' });
        expect(await second).toBe(false);
        expect(ctx.dialog.dialog.textContent).toContain('still nope');
    });

    it('asks before discarding unsaved changes and cleans up on close', async () => {
        const confirm = vi.fn(() => false);
        const ctx = setup([], confirm);
        await load(ctx);
        ctx.send({ type: 'EXELEARNING_EVENT', event: 'PROJECT_DIRTY' });
        expect(ctx.dialog.close()).toBe(false);
        expect(confirm).toHaveBeenCalledWith('Discard?');

        const event = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);

        confirm.mockReturnValue(true);
        ctx.dialog.dialog.dispatchEvent(new Event('cancel', { cancelable: true }));
        expect(ctx.dialog.state).toBe('closed');
        expect(ctx.onClosed).toHaveBeenCalledOnce();
        expect(document.body.contains(ctx.dialog.dialog)).toBe(false);
        expect(ctx.dialog.close()).toBe(true);

        const after = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(after);
        expect(after.defaultPrevented).toBe(false);
    });

    it('closes without asking when there are no changes and via its buttons', async () => {
        const ctx = setup();
        await load(ctx);
        const unload = new Event('beforeunload', { cancelable: true });
        window.dispatchEvent(unload);
        expect(unload.defaultPrevented).toBe(false);
        (ctx.dialog.dialog.querySelector('button.btn-secondary') as HTMLButtonElement).click();
        expect(ctx.dialog.state).toBe('closed');
    });

    it('uses window.confirm by default', async () => {
        const ctx = setup([], null);
        await load(ctx);
        ctx.send({ type: 'EXELEARNING_EVENT', event: 'PROJECT_DIRTY' });
        const confirm = vi.fn(() => false);
        window.confirm = confirm;
        expect(ctx.dialog.close()).toBe(false);
        expect(confirm).toHaveBeenCalledWith('Discard?');
    });

    it('saves when the save button is clicked', async () => {
        const ctx = setup([{ method: 'PUT', path: /package/, body: resourceJson({ version: '1.1' }) }]);
        await load(ctx);
        (ctx.dialog.dialog.querySelector('button.btn-primary') as HTMLButtonElement).click();
        await flush();
        expect(ctx.last().type).toBe('REQUEST_SAVE');
    });
});
