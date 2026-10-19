/**
 * `<exelearning-viewer>`: progressive enhancement for the server-rendered
 * widget markup. The JSP renders the sandboxed iframe; this element only adds
 * the full-screen toggle, so the content is visible without JavaScript.
 */
export class ExeLearningViewer extends HTMLElement {
    private button: HTMLButtonElement | null = null;
    private readonly onClick = () => void this.toggleFullscreen();
    private readonly onChange = () => this.sync();

    connectedCallback(): void {
        this.button = this.querySelector('button[data-exe-fullscreen]');
        this.button?.addEventListener('click', this.onClick);
        document.addEventListener('fullscreenchange', this.onChange);
        this.sync();
    }

    disconnectedCallback(): void {
        this.button?.removeEventListener('click', this.onClick);
        document.removeEventListener('fullscreenchange', this.onChange);
        this.button = null;
    }

    async toggleFullscreen(): Promise<void> {
        if (document.fullscreenElement === this) {
            await document.exitFullscreen();
        } else {
            await this.requestFullscreen();
        }
    }

    private sync(): void {
        this.button?.setAttribute('aria-pressed', String(document.fullscreenElement === this));
    }
}
