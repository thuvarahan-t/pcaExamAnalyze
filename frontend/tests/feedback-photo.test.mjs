import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const script = readFileSync(new URL('../static/js/offer-feedback-photo.js', import.meta.url), 'utf8');

function photoPage({ decodes = true, fetch } = {}) {
    function element() {
        return {
            events: {}, hidden: true, focus() {}, removeAttribute() {},
            addEventListener(name, handler) { this.events[name] = handler; },
            dispatchEvent() {}
        };
    }
    const input = element(), image = element(), remove = element(), error = element(), status = element();
    const preview = { querySelector: () => image };
    const picker = { querySelector: selector => ({ input, '[data-photo-preview]': preview,
        '[data-photo-status]': status, '[data-photo-error]': error, '[data-remove-photo]': remove })[selector] };
    input.files = [];
    Object.defineProperty(input, 'value', { set() { this.files = []; } });
    const success = element(), submit = element();
    const details = { open: true, querySelector: () => element() };
    const form = element();
    const fields = { name: 'Test Student', school: 'Test School', district: 'Jaffna', comment: 'Keep this draft.' };
    form.elements = Object.fromEntries(Object.entries(fields).map(([name, value]) => [name, { ...element(), value }]));
    form.action = '/exam/packages/feedback';
    form.querySelector = selector => selector === '[data-feedback-photo]' ? picker : submit;
    form.closest = selector => selector === 'details' ? details : { querySelector: () => success };
    class FormData {
        constructor(source) { this.comment = source.elements.comment.value; }
        set(name, value) { this[name] = value; }
    }
    class Image {
        naturalWidth = 2200;
        naturalHeight = 1200;
        async decode() { if (!decodes) throw new Error('Invalid image'); }
    }
    const document = {
        querySelector: selector => ({ '[data-feedback-form]': form,
            'meta[name="_csrf"]': { content: 'csrf' }, 'meta[name="_csrf_header"]': { content: 'X-CSRF-TOKEN' } })[selector],
        createElement: () => ({
            getContext: () => ({ fillRect() {}, drawImage() {} }),
            toBlob: (callback, type, quality) => callback({ type, size: quality > 0.65 ? 150_000 : 91_000 })
        })
    };
    vm.runInNewContext(script, { document, window: { FormData, fetch: fetch || (() => {}) },
        FormData, Image, Event, fetch, URL: { createObjectURL: () => 'blob:photo', revokeObjectURL() {} } });
    return {
        form, input, image, status, error, remove, success,
        async choose() {
            input.files = [{ size: 1_000_000 }];
            input.events.change();
            await new Promise(resolve => setImmediate(resolve));
        },
        submit: () => form.events.submit({ preventDefault: () => { form.prevented = true; } })
    };
}

test('compresses the selected photo before posting and preserves text when storage fails', async () => {
    const view = photoPage({ fetch: async (url, { body, headers }) => {
        assert.equal(url, '/exam/packages/feedback');
        assert.equal(body.photo.type, 'image/jpeg');
        assert.ok(body.photo.size <= 100_000);
        assert.equal(body.comment, 'Keep this draft.');
        assert.equal(headers['X-CSRF-TOKEN'], 'csrf');
        assert.equal(headers['X-Feedback-Upload'], 'true');
        return { ok: false, json: async () => ({ message: 'Photo storage is unavailable. Remove the photo to send text only.' }) };
    } });
    await view.choose();
    assert.equal(view.image.hidden, false);
    await view.submit();
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
    assert.match(view.error.textContent, /storage is unavailable.*text only/i);
    view.remove.events.click();
    view.form.prevented = false;
    await view.submit();
    assert.equal(view.form.prevented, false);
});

test('shows invalid-photo recovery and keeps the typed draft', async () => {
    const view = photoPage({ decodes: false, fetch: () => assert.fail('Invalid photo was posted') });
    await view.choose();
    assert.equal(view.image.hidden, true);
    assert.match(view.error.textContent, /could not be opened.*text only/i);
    await view.submit();
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
});

test('clears the submitted draft and displays the review confirmation on photo success', async () => {
    const view = photoPage({ fetch: async () => ({ ok: true }) });
    await view.choose();
    await view.submit();
    assert.equal(view.form.elements.comment.value, '');
    assert.equal(view.input.files.length, 0);
    assert.equal(view.success.hidden, false);
});
