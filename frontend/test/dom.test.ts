import { describe, expect, it } from 'vitest';
import { h } from '../src/dom';

describe('h', () => {
    it('sets attributes and appends text safely', () => {
        const element = h(
            'div',
            { class: 'a', hidden: true, title: undefined, 'data-x': '1' },
            '<b>not html</b>',
            null,
            false,
            h('span', {}, 'child'),
        );
        expect(element.getAttribute('class')).toBe('a');
        expect(element.hasAttribute('hidden')).toBe(true);
        expect(element.hasAttribute('title')).toBe(false);
        expect(element.querySelector('b')).toBeNull();
        expect(element.textContent).toBe('<b>not html</b>child');
    });
});
