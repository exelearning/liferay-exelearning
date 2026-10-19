import { ExeLearningEditor } from './editor-element';
import { ExeLearningViewer } from './viewer';

/** Registers the custom elements once, even if the script is loaded again by SPA navigation. */
export function define(registry: CustomElementRegistry = customElements): void {
    if (!registry.get('exelearning-editor')) {
        registry.define('exelearning-editor', ExeLearningEditor);
    }
    if (!registry.get('exelearning-viewer')) {
        registry.define('exelearning-viewer', ExeLearningViewer);
    }
}

define();
