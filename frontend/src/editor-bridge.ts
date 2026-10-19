import {
    type DocumentLoadedMessage,
    type EditorMessage,
    type ExportFileMessage,
    type HostMessage,
    parseEditorMessage,
    type ReadyMessage,
    type SaveFileMessage,
    type StateMessage,
} from './protocol';

export class EditorTimeoutError extends Error {
    constructor(what: string) {
        super(`Timed out waiting for ${what}`);
        this.name = 'EditorTimeoutError';
    }
}

export class EditorRequestError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'EditorRequestError';
    }
}

export interface EditorBridgeOptions {
    /** The iframe hosting the static editor. */
    iframe: HTMLIFrameElement;
    /** Exact origin the editor is served from; messages from other origins are ignored. */
    origin: string;
    /** Window to listen on (the host window). */
    host?: Window;
    /** Milliseconds to wait for the editor to become ready or load a document. */
    loadTimeoutMs?: number;
    /** Milliseconds to wait for a request (save, export, state) to be answered. */
    requestTimeoutMs?: number;
}

export type EditorEvent = 'dirty' | 'saved';

interface Pending {
    expect: string;
    resolve: (message: EditorMessage) => void;
    reject: (error: Error) => void;
    timer: ReturnType<typeof setTimeout>;
}

interface Waiter<T> {
    promise: Promise<T>;
    resolve: (value: T) => void;
    reject: (error: Error) => void;
}

function waiter<T>(): Waiter<T> {
    let resolve!: (value: T) => void;
    let reject!: (error: Error) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    promise.catch(() => undefined);
    return { promise, resolve, reject };
}

let sequence = 0;

/**
 * Host side of the eXeLearning embedding protocol.
 *
 * Accepts only messages whose `source` is the editor iframe window and whose
 * `origin` is the configured editor origin, correlates requests by
 * `requestId`, applies timeouts and never posts to `'*'`.
 */
export class EditorBridge {
    private readonly iframe: HTMLIFrameElement;
    private readonly origin: string;
    private readonly host: Window;
    private readonly loadTimeoutMs: number;
    private readonly requestTimeoutMs: number;
    private readonly pending = new Map<string, Pending>();
    private readonly listeners = new Set<(event: EditorEvent) => void>();
    private readonly readyWaiter = waiter<ReadyMessage>();
    private readonly documentWaiter = waiter<DocumentLoadedMessage>();
    private readonly onMessage = (event: MessageEvent) => this.receive(event);
    private disposed = false;

    constructor(options: EditorBridgeOptions) {
        this.iframe = options.iframe;
        this.origin = options.origin;
        this.host = options.host ?? window;
        this.loadTimeoutMs = options.loadTimeoutMs ?? 120_000;
        this.requestTimeoutMs = options.requestTimeoutMs ?? 120_000;
        this.host.addEventListener('message', this.onMessage);
    }

    /** Stage 1: editor infrastructure is ready (EXELEARNING_READY). */
    ready(): Promise<ReadyMessage> {
        return this.withTimeout(this.readyWaiter.promise, this.loadTimeoutMs, 'EXELEARNING_READY');
    }

    /** Stage 2: the editor document is loaded (DOCUMENT_LOADED). */
    documentLoaded(): Promise<DocumentLoadedMessage> {
        return this.withTimeout(this.documentWaiter.promise, this.loadTimeoutMs, 'DOCUMENT_LOADED');
    }

    /** Opens `.elpx` bytes in the editor, after both readiness stages. */
    async openFile(bytes: ArrayBuffer, filename: string): Promise<void> {
        await this.ready();
        await this.documentLoaded();
        await this.request('OPEN_FILE', 'OPEN_FILE_SUCCESS', { bytes, filename }, this.loadTimeoutMs);
    }

    /** Asks the editor for the current project as `.elpx` bytes. */
    save(): Promise<SaveFileMessage> {
        return this.request('REQUEST_SAVE', 'SAVE_FILE');
    }

    /**
     * Returns the project as `.elpx` bytes. Uses REQUEST_SAVE, the documented
     * save contract, and falls back to REQUEST_EXPORT (format `elpx`) when the
     * editor rejects it: eXeLearning v4.0.5 and older static builds answer
     * REQUEST_SAVE with "Export not available".
     */
    async saveProject(): Promise<{ bytes: ArrayBuffer; filename: string }> {
        try {
            return await this.save();
        } catch (error) {
            if (!(error instanceof EditorRequestError) || this.disposed) {
                throw error;
            }
            return this.exportAs('elpx');
        }
    }

    exportAs(format: string, filename?: string): Promise<ExportFileMessage> {
        return this.request('REQUEST_EXPORT', 'EXPORT_FILE', { format, filename });
    }

    state(): Promise<StateMessage> {
        return this.request('GET_STATE', 'STATE');
    }

    async configure(hideUI: Record<string, boolean>): Promise<void> {
        await this.request('CONFIGURE', 'CONFIGURE_SUCCESS', { hideUI });
    }

    on(listener: (event: EditorEvent) => void): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /** Removes the listener and rejects every outstanding request. */
    dispose(): void {
        if (this.disposed) {
            return;
        }
        this.disposed = true;
        this.host.removeEventListener('message', this.onMessage);
        const error = new EditorRequestError('Editor closed');
        for (const entry of this.pending.values()) {
            clearTimeout(entry.timer);
            entry.reject(error);
        }
        this.pending.clear();
        this.listeners.clear();
        this.readyWaiter.reject(error);
        this.documentWaiter.reject(error);
    }

    private request<K extends EditorMessage['type']>(
        type: HostMessage['type'],
        expect: K,
        data?: Record<string, unknown>,
        timeoutMs = this.requestTimeoutMs,
    ): Promise<Extract<EditorMessage, { type: K }>> {
        if (this.disposed) {
            return Promise.reject(new EditorRequestError('Editor closed'));
        }
        const target = this.iframe.contentWindow;
        if (!target) {
            return Promise.reject(new EditorRequestError('Editor frame is not available'));
        }
        sequence += 1;
        const requestId = `exe-${sequence}-${Date.now().toString(36)}`;
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this.pending.delete(requestId);
                reject(new EditorTimeoutError(expect));
            }, timeoutMs);
            // receive() only resolves an entry with a message whose type equals `expect`.
            const accept = (message: EditorMessage) => resolve(message as Extract<EditorMessage, { type: K }>);
            this.pending.set(requestId, { expect, resolve: accept, reject, timer });
            const message: HostMessage = data ? { type, requestId, data } : { type, requestId };
            target.postMessage(message, this.origin);
        });
    }

    private receive(event: MessageEvent): void {
        if (event.origin !== this.origin || event.source !== this.iframe.contentWindow) {
            return;
        }
        const message = parseEditorMessage(event.data);
        if (!message) {
            return;
        }
        switch (message.type) {
            case 'EXELEARNING_READY':
                this.readyWaiter.resolve(message);
                return;
            case 'DOCUMENT_LOADED':
                this.documentWaiter.resolve(message);
                return;
            case 'EXELEARNING_EVENT':
                for (const listener of this.listeners) {
                    listener(message.event === 'PROJECT_DIRTY' ? 'dirty' : 'saved');
                }
                return;
        }
        const entry = this.pending.get(message.requestId);
        if (!entry) {
            return;
        }
        if (message.type === entry.expect) {
            this.settle(message.requestId, entry);
            entry.resolve(message);
        } else if ('error' in message) {
            this.settle(message.requestId, entry);
            entry.reject(new EditorRequestError(message.error));
        }
    }

    private settle(requestId: string, entry: Pending): void {
        clearTimeout(entry.timer);
        this.pending.delete(requestId);
    }

    private withTimeout<T>(promise: Promise<T>, ms: number, what: string): Promise<T> {
        let timer: ReturnType<typeof setTimeout> | undefined;
        const timeout = new Promise<never>((_, reject) => {
            timer = setTimeout(() => reject(new EditorTimeoutError(what)), ms);
        });
        return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
    }
}
