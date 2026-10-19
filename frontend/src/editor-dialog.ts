import { ApiError, type ExeApi, type Resource } from './api';
import { h } from './dom';
import { EditorBridge } from './editor-bridge';
import type { Translate } from './i18n';

export type EditorState = 'loading' | 'ready' | 'saving' | 'error' | 'closed';

export interface EditorDialogOptions {
    api: ExeApi;
    resource: Resource;
    editorUrl: string;
    t: Translate;
    onSaved: (resource: Resource) => void;
    onClosed?: () => void;
    confirm?: (message: string) => boolean;
    loadTimeoutMs?: number;
    requestTimeoutMs?: number;
}

/**
 * Full-screen modal that hosts the embedded static editor for one resource.
 *
 * Lifecycle: load bytes from Liferay, wait for EXELEARNING_READY and
 * DOCUMENT_LOADED, send OPEN_FILE, then save with REQUEST_SAVE and persist the
 * returned bytes through the REST API with optimistic concurrency. Success is
 * reported only after the server stored the new revision.
 */
export class EditorDialog {
    readonly dialog: HTMLDialogElement;
    readonly iframe: HTMLIFrameElement;
    private readonly options: EditorDialogOptions;
    private readonly bridge: EditorBridge;
    private readonly status: HTMLElement;
    private readonly saveButton: HTMLButtonElement;
    private readonly closeButton: HTMLButtonElement;
    private readonly onBeforeUnload = (event: BeforeUnloadEvent) => {
        if (this.dirty) {
            event.preventDefault();
        }
    };
    private resource: Resource;
    private stateValue: EditorState = 'loading';
    private dirty = false;
    private loaded = false;

    constructor(options: EditorDialogOptions) {
        this.options = options;
        this.resource = options.resource;
        const { t } = options;
        const origin = new URL(options.editorUrl, window.location.href).origin;

        this.status = h('span', { class: 'exe-editor-status', role: 'status', 'aria-live': 'polite' });
        this.saveButton = h('button', { type: 'button', class: 'btn btn-primary btn-sm', disabled: true }, t('save'));
        this.closeButton = h('button', { type: 'button', class: 'btn btn-secondary btn-sm' }, t('close'));
        this.iframe = h('iframe', {
            class: 'exe-editor-frame',
            title: t('exelearning-editor'),
            allow: 'fullscreen; clipboard-read; clipboard-write',
        });
        this.dialog = h(
            'dialog',
            { class: 'exe-editor-dialog', 'aria-label': t('edit-x', this.resource.title) },
            h(
                'div',
                { class: 'exe-editor-toolbar' },
                h('strong', { class: 'exe-editor-title' }, this.resource.title),
                this.status,
                this.saveButton,
                this.closeButton,
            ),
            this.iframe,
        );
        this.bridge = new EditorBridge({
            iframe: this.iframe,
            origin,
            loadTimeoutMs: options.loadTimeoutMs,
            requestTimeoutMs: options.requestTimeoutMs,
        });
        this.bridge.on(event => {
            if (event === 'dirty') {
                this.dirty = true;
                this.render();
            }
        });
        this.saveButton.addEventListener('click', () => void this.save());
        this.closeButton.addEventListener('click', () => this.close());
        this.dialog.addEventListener('cancel', event => {
            event.preventDefault();
            this.close();
        });
    }

    get state(): EditorState {
        return this.stateValue;
    }

    get isDirty(): boolean {
        return this.dirty;
    }

    /** Mounts the dialog and loads the current revision into the editor. */
    async open(container: HTMLElement = document.body): Promise<void> {
        container.append(this.dialog);
        this.dialog.showModal();
        window.addEventListener('beforeunload', this.onBeforeUnload);
        this.setState('loading', this.options.t('loading-editor'));
        try {
            const bytesPromise = this.options.api.download(this.resource.id, this.resource.version);
            this.iframe.src = this.options.editorUrl;
            const bytes = await bytesPromise;
            await this.bridge.openFile(bytes, this.resource.fileName);
            this.loaded = true;
            this.dirty = false;
            this.setState('ready', '');
        } catch (error) {
            this.fail('editor-could-not-be-loaded', error);
        }
    }

    /** Requests the project from the editor and stores it as a new revision. */
    async save(): Promise<boolean> {
        if (!this.loaded || (this.stateValue !== 'ready' && this.stateValue !== 'error')) {
            return false;
        }
        const { t } = this.options;
        this.setState('saving', t('saving'));
        try {
            const file = await this.bridge.saveProject();
            const updated = await this.options.api.saveRevision(
                this.resource.id,
                file.bytes,
                this.resource.fileName,
                this.resource.version,
            );
            this.resource = updated;
            this.dirty = false;
            this.setState('ready', t('saved-as-version-x', updated.version));
            this.options.onSaved(updated);
            return true;
        } catch (error) {
            if (error instanceof ApiError && error.status === 409) {
                this.fail('resource-changed-conflict', error);
            } else {
                this.fail('the-resource-could-not-be-saved', error);
            }
            return false;
        }
    }

    /** Closes the dialog, asking first when there are unsaved changes. */
    close(): boolean {
        if (this.stateValue === 'closed') {
            return true;
        }
        const confirm = this.options.confirm ?? (message => window.confirm(message));
        if (this.dirty && !confirm(this.options.t('discard-unsaved-changes'))) {
            return false;
        }
        this.stateValue = 'closed';
        window.removeEventListener('beforeunload', this.onBeforeUnload);
        this.bridge.dispose();
        this.dialog.close();
        this.dialog.remove();
        this.options.onClosed?.();
        return true;
    }

    private fail(key: string, error: unknown): void {
        const detail = error instanceof Error ? error.message : String(error);
        this.setState('error', `${this.options.t(key)} (${detail})`);
    }

    private setState(state: EditorState, message: string): void {
        if (this.stateValue === 'closed') {
            return;
        }
        this.stateValue = state;
        this.status.textContent = message;
        this.status.classList.toggle('text-danger', state === 'error');
        this.render();
    }

    private render(): void {
        this.saveButton.disabled = !this.loaded || (this.stateValue !== 'ready' && this.stateValue !== 'error');
        this.dialog.toggleAttribute('data-dirty', this.dirty);
        this.dialog.dataset.state = this.stateValue;
    }
}
