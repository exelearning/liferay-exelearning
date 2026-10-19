import { describe, expect, it } from 'vitest';
import { parseEditorMessage } from '../src/protocol';

describe('parseEditorMessage', () => {
    it('rejects non-objects and messages without a type', () => {
        expect(parseEditorMessage(null)).toBeNull();
        expect(parseEditorMessage('EXELEARNING_READY')).toBeNull();
        expect(parseEditorMessage({ requestId: 'x' })).toBeNull();
        expect(parseEditorMessage({ type: 42 })).toBeNull();
    });

    it('parses the two readiness stages with safe defaults', () => {
        expect(
            parseEditorMessage({ type: 'EXELEARNING_READY', version: 'v4.0.5', capabilities: ['OPEN_FILE', 3] }),
        ).toEqual({
            type: 'EXELEARNING_READY',
            version: 'v4.0.5',
            capabilities: ['OPEN_FILE'],
        });
        expect(parseEditorMessage({ type: 'EXELEARNING_READY' })).toEqual({
            type: 'EXELEARNING_READY',
            version: 'unknown',
            capabilities: [],
        });
        expect(
            parseEditorMessage({ type: 'DOCUMENT_LOADED', projectId: 'p', isDirty: 'yes', pageCount: Number.NaN }),
        ).toEqual({
            type: 'DOCUMENT_LOADED',
            projectId: 'p',
            isDirty: false,
            pageCount: 0,
        });
    });

    it('parses state events and ignores unknown events', () => {
        expect(
            parseEditorMessage({ type: 'EXELEARNING_EVENT', event: 'PROJECT_DIRTY', data: { isDirty: true } }),
        ).toEqual({
            type: 'EXELEARNING_EVENT',
            event: 'PROJECT_DIRTY',
            isDirty: true,
        });
        expect(parseEditorMessage({ type: 'EXELEARNING_EVENT', event: 'PROJECT_SAVED' })).toEqual({
            type: 'EXELEARNING_EVENT',
            event: 'PROJECT_SAVED',
            isDirty: false,
        });
        expect(parseEditorMessage({ type: 'EXELEARNING_EVENT', event: 'OTHER' })).toBeNull();
    });

    it('requires a request id on responses', () => {
        expect(parseEditorMessage({ type: 'OPEN_FILE_SUCCESS' })).toBeNull();
        expect(parseEditorMessage({ type: 'OPEN_FILE_SUCCESS', requestId: 'r' })).toEqual({
            type: 'OPEN_FILE_SUCCESS',
            requestId: 'r',
            projectId: '',
        });
    });

    it('parses files from ArrayBuffers and typed arrays only', () => {
        const buffer = new Uint8Array([1, 2, 3]).buffer;
        const save = parseEditorMessage({ type: 'SAVE_FILE', requestId: 'r', bytes: buffer, size: 3 });
        expect(save).toMatchObject({ type: 'SAVE_FILE', filename: 'project.elpx', size: 3 });

        const view = new Uint8Array([0, 9, 8, 7, 0]).subarray(1, 4);
        const exported = parseEditorMessage({
            type: 'EXPORT_FILE',
            requestId: 'r',
            bytes: view,
            filename: 'x.elpx',
            format: 'elpx',
        });
        expect(exported).toMatchObject({ type: 'EXPORT_FILE', filename: 'x.elpx', format: 'elpx' });
        expect(new Uint8Array((exported as { bytes: ArrayBuffer }).bytes)).toEqual(new Uint8Array([9, 8, 7]));

        expect(parseEditorMessage({ type: 'SAVE_FILE', requestId: 'r', bytes: 'nope' })).toBeNull();
        expect(parseEditorMessage({ type: 'EXPORT_FILE', requestId: 'r' })).toBeNull();
        expect(parseEditorMessage({ type: 'EXPORT_FILE', requestId: 'r', bytes: buffer })).toMatchObject({
            format: '',
        });
    });

    it('parses state, acknowledgements and errors', () => {
        expect(
            parseEditorMessage({ type: 'STATE', requestId: 'r', isDirty: true, hasProject: true, pageCount: 4 }),
        ).toEqual({
            type: 'STATE',
            requestId: 'r',
            isDirty: true,
            hasProject: true,
            pageCount: 4,
        });
        expect(parseEditorMessage({ type: 'CONFIGURE_SUCCESS', requestId: 'r' })).toEqual({
            type: 'CONFIGURE_SUCCESS',
            requestId: 'r',
        });
        expect(parseEditorMessage({ type: 'REQUEST_SAVE_ERROR', requestId: 'r', error: 'boom' })).toEqual({
            type: 'REQUEST_SAVE_ERROR',
            requestId: 'r',
            error: 'boom',
        });
        expect(parseEditorMessage({ type: 'OPEN_FILE_ERROR', requestId: 'r' })).toMatchObject({
            error: 'Unknown error',
        });
        expect(parseEditorMessage({ type: 'SOMETHING_ELSE', requestId: 'r' })).toBeNull();
    });
});
