import { afterEach, describe, expect, it, vi } from 'vitest';
import { EditorBridge, EditorRequestError, EditorTimeoutError } from '../src/editor-bridge';
import { EDITOR_ORIGIN, editorHarness, flush } from './harness';

function bridgeWith(harness = editorHarness(), options: { loadTimeoutMs?: number; requestTimeoutMs?: number } = {}) {
    const bridge = new EditorBridge({ iframe: harness.iframe, origin: EDITOR_ORIGIN, host: harness.host, ...options });
    return { bridge, harness };
}

afterEach(() => {
    vi.useRealTimers();
});

describe('EditorBridge', () => {
    it('waits for both readiness stages before opening a file', async () => {
        const { bridge, harness } = bridgeWith();
        const bytes = new Uint8Array([1, 2]).buffer;
        const opened = bridge.openFile(bytes, 'demo.elpx');
        await flush();
        expect(harness.posted).toHaveLength(0);

        harness.send({ type: 'EXELEARNING_READY', version: 'v4.0.5', capabilities: [] });
        await flush();
        expect(harness.posted).toHaveLength(0);

        harness.send({ type: 'DOCUMENT_LOADED', projectId: null, isDirty: false, pageCount: 1 });
        await flush();
        const request = harness.lastRequest();
        expect(request.type).toBe('OPEN_FILE');
        expect(request.data).toEqual({ bytes, filename: 'demo.elpx' });
        expect(harness.posted[0]?.targetOrigin).toBe(EDITOR_ORIGIN);

        harness.send({ type: 'OPEN_FILE_SUCCESS', requestId: request.requestId, projectId: 'p1' });
        await expect(opened).resolves.toBeUndefined();
    });

    it('accepts DOCUMENT_LOADED before EXELEARNING_READY', async () => {
        const { bridge, harness } = bridgeWith();
        harness.send({ type: 'DOCUMENT_LOADED', projectId: 'p', isDirty: false, pageCount: 1 });
        harness.send({ type: 'EXELEARNING_READY', version: 'x', capabilities: [] });
        await expect(bridge.ready()).resolves.toMatchObject({ version: 'x' });
        await expect(bridge.documentLoaded()).resolves.toMatchObject({ projectId: 'p' });
    });

    it('ignores messages from other origins, other windows and malformed payloads', async () => {
        const { bridge, harness } = bridgeWith(editorHarness(), { loadTimeoutMs: 50 });
        harness.send({ type: 'EXELEARNING_READY' }, { origin: 'https://evil.example' });
        harness.send({ type: 'EXELEARNING_READY' }, { source: window });
        harness.send({ type: 'EXELEARNING_READY' }, { source: null });
        harness.send('EXELEARNING_READY');
        await expect(bridge.ready()).rejects.toBeInstanceOf(EditorTimeoutError);
    });

    it('correlates responses by request id', async () => {
        const { bridge, harness } = bridgeWith();
        const first = bridge.state();
        const firstId = harness.lastRequest().requestId;
        const second = bridge.state();
        const secondId = harness.lastRequest().requestId;
        expect(firstId).not.toBe(secondId);

        harness.send({ type: 'STATE', requestId: 'unrelated', isDirty: true, hasProject: true, pageCount: 9 });
        harness.send({ type: 'STATE', requestId: secondId, isDirty: true, hasProject: true, pageCount: 2 });
        harness.send({ type: 'STATE', requestId: firstId, isDirty: false, hasProject: true, pageCount: 1 });

        await expect(first).resolves.toMatchObject({ pageCount: 1, isDirty: false });
        await expect(second).resolves.toMatchObject({ pageCount: 2, isDirty: true });
    });

    it('rejects a request with the editor error and ignores unrelated message types', async () => {
        const { bridge, harness } = bridgeWith();
        const saved = bridge.save();
        const { requestId } = harness.lastRequest();
        harness.send({ type: 'CONFIGURE_SUCCESS', requestId });
        harness.send({ type: 'REQUEST_SAVE_ERROR', requestId, error: 'Export not available' });
        await expect(saved).rejects.toThrow(new EditorRequestError('Export not available'));
    });

    it('falls back to REQUEST_EXPORT when REQUEST_SAVE is rejected', async () => {
        const { bridge, harness } = bridgeWith();
        const result = bridge.saveProject();
        const save = harness.lastRequest();
        expect(save.type).toBe('REQUEST_SAVE');
        harness.send({ type: 'REQUEST_SAVE_ERROR', requestId: save.requestId, error: 'Export not available' });
        await flush();

        const exported = harness.lastRequest();
        expect(exported.type).toBe('REQUEST_EXPORT');
        expect(exported.data).toMatchObject({ format: 'elpx' });
        const bytes = new Uint8Array([5]).buffer;
        harness.send({ type: 'EXPORT_FILE', requestId: exported.requestId, bytes, filename: 'p.elpx', format: 'elpx' });
        await expect(result).resolves.toMatchObject({ filename: 'p.elpx', bytes });
    });

    it('returns SAVE_FILE bytes directly when REQUEST_SAVE works', async () => {
        const { bridge, harness } = bridgeWith();
        const result = bridge.saveProject();
        const bytes = new Uint8Array([7]).buffer;
        harness.send({
            type: 'SAVE_FILE',
            requestId: harness.lastRequest().requestId,
            bytes,
            filename: 'a.elpx',
            size: 1,
        });
        await expect(result).resolves.toMatchObject({ filename: 'a.elpx' });
        expect(harness.posted).toHaveLength(1);
    });

    it('does not fall back on timeouts', async () => {
        vi.useFakeTimers();
        const { bridge, harness } = bridgeWith(editorHarness(), { requestTimeoutMs: 1000 });
        const result = bridge.saveProject();
        const assertion = expect(result).rejects.toBeInstanceOf(EditorTimeoutError);
        await vi.advanceTimersByTimeAsync(1001);
        await assertion;
        expect(harness.posted).toHaveLength(1);
    });

    it('times out requests and forgets them', async () => {
        vi.useFakeTimers();
        const { bridge, harness } = bridgeWith(editorHarness(), { requestTimeoutMs: 1000 });
        const state = bridge.state();
        const assertion = expect(state).rejects.toThrow('Timed out waiting for STATE');
        await vi.advanceTimersByTimeAsync(1001);
        await assertion;
        harness.send({ type: 'STATE', requestId: harness.lastRequest().requestId, isDirty: false, hasProject: true });
    });

    it('forwards dirty and saved events to listeners until unsubscribed', () => {
        const { bridge, harness } = bridgeWith();
        const events: string[] = [];
        const unsubscribe = bridge.on(event => events.push(event));
        harness.send({ type: 'EXELEARNING_EVENT', event: 'PROJECT_DIRTY', data: { isDirty: true } });
        harness.send({ type: 'EXELEARNING_EVENT', event: 'PROJECT_SAVED', data: { isDirty: false } });
        unsubscribe();
        harness.send({ type: 'EXELEARNING_EVENT', event: 'PROJECT_DIRTY', data: { isDirty: true } });
        expect(events).toEqual(['dirty', 'saved']);
    });

    it('sends configure and export requests', async () => {
        const { bridge, harness } = bridgeWith();
        const configured = bridge.configure({ saveButton: true });
        expect(harness.lastRequest()).toMatchObject({ type: 'CONFIGURE', data: { hideUI: { saveButton: true } } });
        harness.send({ type: 'CONFIGURE_SUCCESS', requestId: harness.lastRequest().requestId });
        await expect(configured).resolves.toBeUndefined();
    });

    it('rejects pending work and stops listening when disposed', async () => {
        const { bridge, harness } = bridgeWith();
        const state = bridge.state();
        const ready = bridge.ready();
        bridge.dispose();
        bridge.dispose();
        await expect(state).rejects.toThrow('Editor closed');
        await expect(ready).rejects.toThrow('Editor closed');
        await expect(bridge.state()).rejects.toThrow('Editor closed');
        await expect(bridge.saveProject()).rejects.toThrow('Editor closed');

        const listener = vi.fn();
        bridge.on(listener);
        harness.send({ type: 'EXELEARNING_EVENT', event: 'PROJECT_DIRTY' });
        expect(listener).not.toHaveBeenCalled();
    });

    it('fails requests when the frame has no window', async () => {
        const harness = editorHarness();
        Object.defineProperty(harness.iframe, 'contentWindow', { value: null });
        const { bridge } = bridgeWith(harness);
        await expect(bridge.state()).rejects.toThrow('Editor frame is not available');
    });

    it('keeps two editors on one page apart', async () => {
        const one = editorHarness();
        const two = editorHarness();
        const shared = new EventTarget() as unknown as Window;
        const first = new EditorBridge({ iframe: one.iframe, origin: EDITOR_ORIGIN, host: shared });
        const second = new EditorBridge({ iframe: two.iframe, origin: EDITOR_ORIGIN, host: shared, loadTimeoutMs: 50 });
        const event = new Event('message');
        Object.assign(event, { data: { type: 'EXELEARNING_READY' }, origin: EDITOR_ORIGIN, source: one.editorWindow });
        shared.dispatchEvent(event);
        await expect(first.ready()).resolves.toMatchObject({ type: 'EXELEARNING_READY' });
        await expect(second.ready()).rejects.toBeInstanceOf(EditorTimeoutError);
    });
});
