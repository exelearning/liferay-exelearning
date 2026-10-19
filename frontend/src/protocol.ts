/**
 * Typed contract for the eXeLearning embedding protocol (postMessage).
 *
 * Verified against exelearning v4.0.5 `public/app/core/EmbeddingBridge.js`
 * and `doc/development/embedding.md`. Inbound data is untrusted: every
 * message is parsed from `unknown` and rejected unless its shape matches.
 */

export type ExportFormat = 'elpx' | 'html5' | 'scorm12' | 'scorm2004' | 'epub3' | 'ims';

export interface ReadyMessage {
    type: 'EXELEARNING_READY';
    version: string;
    capabilities: string[];
}

export interface DocumentLoadedMessage {
    type: 'DOCUMENT_LOADED';
    projectId: string | null;
    isDirty: boolean;
    pageCount: number;
}

export interface OpenFileSuccessMessage {
    type: 'OPEN_FILE_SUCCESS';
    requestId: string;
    projectId: string;
}

export interface SaveFileMessage {
    type: 'SAVE_FILE';
    requestId: string;
    bytes: ArrayBuffer;
    filename: string;
    size: number;
}

export interface ExportFileMessage {
    type: 'EXPORT_FILE';
    requestId: string;
    bytes: ArrayBuffer;
    filename: string;
    format: string;
}

export interface StateMessage {
    type: 'STATE';
    requestId: string;
    isDirty: boolean;
    hasProject: boolean;
    pageCount: number;
}

export interface AckMessage {
    type: 'CONFIGURE_SUCCESS' | 'SET_TRUSTED_ORIGINS_SUCCESS';
    requestId: string;
}

/** `${REQUEST}_ERROR` responses carry only `requestId` and `error`. */
export interface ErrorMessage {
    type: `${string}_ERROR`;
    requestId: string;
    error: string;
}

export interface EventMessage {
    type: 'EXELEARNING_EVENT';
    event: 'PROJECT_DIRTY' | 'PROJECT_SAVED';
    isDirty: boolean;
}

export type EditorMessage =
    | ReadyMessage
    | DocumentLoadedMessage
    | OpenFileSuccessMessage
    | SaveFileMessage
    | ExportFileMessage
    | StateMessage
    | AckMessage
    | ErrorMessage
    | EventMessage;

/** Message sent from the host to the editor iframe. */
export interface HostMessage {
    type: 'OPEN_FILE' | 'REQUEST_SAVE' | 'REQUEST_EXPORT' | 'GET_STATE' | 'CONFIGURE';
    requestId: string;
    data?: Record<string, unknown>;
}

type Fields = Record<string, unknown>;

function isRecord(value: unknown): value is Fields {
    return typeof value === 'object' && value !== null;
}

function str(value: unknown): string | null {
    return typeof value === 'string' ? value : null;
}

function num(value: unknown): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function bytes(value: unknown): ArrayBuffer | null {
    if (value instanceof ArrayBuffer) {
        return value;
    }
    if (ArrayBuffer.isView(value) && value.buffer instanceof ArrayBuffer) {
        return value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength);
    }
    return null;
}

function parseFile(m: Fields): { bytes: ArrayBuffer; filename: string } | null {
    const data = bytes(m.bytes);
    return data ? { bytes: data, filename: str(m.filename) ?? 'project.elpx' } : null;
}

/**
 * Parses an inbound message. Returns null for anything that is not a
 * well-formed editor message so callers can ignore it.
 */
export function parseEditorMessage(raw: unknown): EditorMessage | null {
    if (!isRecord(raw)) {
        return null;
    }
    const type = str(raw.type);
    if (!type) {
        return null;
    }
    const requestId = str(raw.requestId);

    switch (type) {
        case 'EXELEARNING_READY':
            return {
                type,
                version: str(raw.version) ?? 'unknown',
                capabilities: Array.isArray(raw.capabilities)
                    ? raw.capabilities.filter((c): c is string => typeof c === 'string')
                    : [],
            };
        case 'DOCUMENT_LOADED':
            return {
                type,
                projectId: str(raw.projectId),
                isDirty: raw.isDirty === true,
                pageCount: num(raw.pageCount),
            };
        case 'EXELEARNING_EVENT': {
            const event = str(raw.event);
            if (event !== 'PROJECT_DIRTY' && event !== 'PROJECT_SAVED') {
                return null;
            }
            const data = isRecord(raw.data) ? raw.data : {};
            return { type, event, isDirty: data.isDirty === true };
        }
    }

    if (!requestId) {
        return null;
    }

    switch (type) {
        case 'OPEN_FILE_SUCCESS':
            return { type, requestId, projectId: str(raw.projectId) ?? '' };
        case 'SAVE_FILE': {
            const file = parseFile(raw);
            return file ? { type, requestId, bytes: file.bytes, filename: file.filename, size: num(raw.size) } : null;
        }
        case 'EXPORT_FILE': {
            const file = parseFile(raw);
            return file
                ? { type, requestId, bytes: file.bytes, filename: file.filename, format: str(raw.format) ?? '' }
                : null;
        }
        case 'STATE':
            return {
                type,
                requestId,
                isDirty: raw.isDirty === true,
                hasProject: raw.hasProject === true,
                pageCount: num(raw.pageCount),
            };
        case 'CONFIGURE_SUCCESS':
        case 'SET_TRUSTED_ORIGINS_SUCCESS':
            return { type, requestId };
    }

    if (type.endsWith('_ERROR')) {
        return { type: type as ErrorMessage['type'], requestId, error: str(raw.error) ?? 'Unknown error' };
    }
    return null;
}
