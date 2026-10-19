import { describe, expect, it } from 'vitest';
import { createTranslator } from '../src/i18n';

describe('createTranslator', () => {
    it('translates keys and fills Liferay-style placeholders', () => {
        const t = createTranslator(JSON.stringify({ hello: 'Hola {0}, tienes {1} recursos', plain: 'Texto' }));
        expect(t('hello', 'Ana', 3)).toBe('Hola Ana, tienes 3 recursos');
        expect(t('plain')).toBe('Texto');
        expect(t('hello', 'Ana')).toBe('Hola Ana, tienes {1} recursos');
    });

    it('falls back to the key for missing, broken or non-object dictionaries', () => {
        expect(createTranslator(null)('missing-key')).toBe('missing-key');
        expect(createTranslator('{broken')('key')).toBe('key');
        expect(createTranslator('"string"')('key')).toBe('key');
        expect(createTranslator(JSON.stringify({ key: 5 }))('key')).toBe('key');
    });
});
