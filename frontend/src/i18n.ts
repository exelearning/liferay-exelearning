export type Translate = (key: string, ...args: (string | number)[]) => string;

/**
 * Builds a translator from the JSON dictionary the portlet JSP renders in the
 * element's `data-i18n` attribute. The strings come from the module's Liferay
 * `Language*.properties`, so the frontend never hardcodes user-facing text.
 * Placeholders follow Liferay's `{0}`, `{1}` convention.
 */
export function createTranslator(source: string | null): Translate {
    let dictionary: Record<string, unknown> = {};
    try {
        const parsed: unknown = JSON.parse(source ?? '{}');
        if (typeof parsed === 'object' && parsed !== null) {
            dictionary = parsed as Record<string, unknown>;
        }
    } catch {
        // A broken dictionary degrades to showing keys.
    }
    return (key, ...args) => {
        const value = dictionary[key];
        const text = typeof value === 'string' ? value : key;
        return text.replace(/\{(\d+)\}/g, (match, index: string) => {
            const arg = args[Number(index)];
            return arg === undefined ? match : String(arg);
        });
    };
}
