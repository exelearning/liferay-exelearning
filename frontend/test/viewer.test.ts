import { afterEach, describe, expect, it, vi } from 'vitest';
import { ExeLearningEditor } from '../src/editor-element';
import { define } from '../src/index';
import { ExeLearningViewer } from '../src/viewer';

define();

function mountViewer() {
    const viewer = document.createElement('exelearning-viewer') as ExeLearningViewer;
    viewer.innerHTML = '<button data-exe-fullscreen aria-pressed="false">Full screen</button><iframe></iframe>';
    document.body.append(viewer);
    return { viewer, button: viewer.querySelector('button') as HTMLButtonElement };
}

function setFullscreenElement(element: Element | null) {
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, get: () => element });
    document.dispatchEvent(new Event('fullscreenchange'));
}

afterEach(() => {
    setFullscreenElement(null);
    document.body.replaceChildren();
});

describe('exelearning-viewer', () => {
    it('toggles full screen on its own element', async () => {
        const { viewer, button } = mountViewer();
        const request = vi.fn(async () => setFullscreenElement(viewer));
        const exit = vi.fn(async () => setFullscreenElement(null));
        viewer.requestFullscreen = request;
        document.exitFullscreen = exit;

        button.click();
        await Promise.resolve();
        expect(request).toHaveBeenCalledOnce();
        expect(button.getAttribute('aria-pressed')).toBe('true');

        button.click();
        await Promise.resolve();
        expect(exit).toHaveBeenCalledOnce();
        expect(button.getAttribute('aria-pressed')).toBe('false');
    });

    it('keeps several widgets independent and cleans up listeners', async () => {
        const one = mountViewer();
        const two = mountViewer();
        setFullscreenElement(one.viewer);
        expect(one.button.getAttribute('aria-pressed')).toBe('true');
        expect(two.button.getAttribute('aria-pressed')).toBe('false');

        const request = vi.fn(async () => undefined);
        two.viewer.requestFullscreen = request;
        two.viewer.remove();
        two.button.click();
        setFullscreenElement(null);
        expect(request).not.toHaveBeenCalled();
        expect(two.button.getAttribute('aria-pressed')).toBe('false');
    });

    it('works without a full-screen button', () => {
        const viewer = document.createElement('exelearning-viewer');
        document.body.append(viewer);
        viewer.remove();
        expect(viewer.isConnected).toBe(false);
    });
});

describe('define', () => {
    it('registers the elements once', () => {
        const registry = { get: vi.fn(() => undefined), define: vi.fn() };
        define(registry as unknown as CustomElementRegistry);
        expect(registry.define).toHaveBeenCalledWith('exelearning-editor', ExeLearningEditor);
        expect(registry.define).toHaveBeenCalledWith('exelearning-viewer', ExeLearningViewer);
        define();
        expect(customElements.get('exelearning-viewer')).toBe(ExeLearningViewer);
    });
});
