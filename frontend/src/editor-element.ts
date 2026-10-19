import { ExeApi } from './api';
import { h } from './dom';
import { EditorDialog } from './editor-dialog';
import { createTranslator } from './i18n';

function liferayAuthToken(): string {
    const liferay = (window as unknown as { Liferay?: { authToken?: string } }).Liferay;
    return liferay?.authToken ?? '';
}

/**
 * `<exelearning-editor resource-id api-base editor-url back-url data-i18n>`:
 * opens the embedded eXeLearning editor for one resource from the native
 * Liferay "Edit with eXeLearning" page and returns to `back-url` on close.
 */
export class ExeLearningEditor extends HTMLElement {
    api?: ExeApi;
    editor: EditorDialog | null = null;
    navigate: (url: string) => void = url => window.location.assign(url);
    private started = false;

    connectedCallback(): void {
        if (this.started) {
            return;
        }
        this.started = true;
        void this.start();
    }

    disconnectedCallback(): void {
        this.editor?.close();
    }

    async start(): Promise<void> {
        const t = createTranslator(this.getAttribute('data-i18n'));
        this.api ??= new ExeApi({
            base: this.getAttribute('api-base') ?? '/o/exelearning/v1',
            csrfToken: liferayAuthToken(),
        });
        const api = this.api;
        try {
            const resource = await api.get(Number(this.getAttribute('resource-id')));
            this.editor = new EditorDialog({
                api,
                resource,
                editorUrl: this.getAttribute('editor-url') ?? '',
                t,
                onSaved: () => undefined,
                onClosed: () => this.navigate(this.getAttribute('back-url') ?? '/'),
            });
            await this.editor.open(this);
        } catch (error) {
            const detail = error instanceof Error ? error.message : String(error);
            this.replaceChildren(
                h(
                    'div',
                    { class: 'alert alert-danger', role: 'alert' },
                    `${t('editor-could-not-be-loaded')} (${detail})`,
                ),
            );
        }
    }
}
