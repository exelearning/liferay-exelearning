type Child = Node | string | null | undefined | false;

/**
 * Minimal element factory. Text is always assigned as text nodes, never as
 * HTML, because titles and metadata come from untrusted packages.
 */
export function h<K extends keyof HTMLElementTagNameMap>(
    tag: K,
    attributes: Record<string, string | boolean | undefined> = {},
    ...children: Child[]
): HTMLElementTagNameMap[K] {
    const element = document.createElement(tag);
    for (const [name, value] of Object.entries(attributes)) {
        if (value === true) {
            element.setAttribute(name, '');
        } else if (typeof value === 'string') {
            element.setAttribute(name, value);
        }
    }
    for (const child of children) {
        if (child) {
            element.append(child);
        }
    }
    return element;
}

/** Sandbox for untrusted published content; mirrors the server CSP `sandbox` directive. */
export const CONTENT_SANDBOX = 'allow-scripts allow-popups allow-forms allow-downloads allow-popups-to-escape-sandbox';
