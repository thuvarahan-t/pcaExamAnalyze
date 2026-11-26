import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const script = readFileSync(new URL('../static/js/offer-feedback-photo.js', import.meta.url), 'utf8');

function photoPage({ decodes = true, fetch } = {}) {
    function element() {
        return {
            events: {}, hidden: true, focus() { this.focused = true; }, click() { this.clicked = true; }, removeAttribute() {},
            addEventListener(name, handler) { this.events[name] = handler; },
            dispatchEvent() {}
        };
    }
    const input = element(), image = element(), add = element(), remove = element(), error = element(), status = element();
    const preview = { ...element(), querySelector: selector => ({ img: image, '[data-remove-photo]': remove })[selector] };
    const picker = { ...element(), querySelector: selector => ({ 'input[type="file"]': input, '[data-add-photo]': add })[selector] };
    const composer = element();
    const classes = new Set();
    composer.classList = { toggle: (name, active) => active ? classes.add(name) : classes.delete(name) };
    composer.querySelector = selector => ({ '[data-feedback-photo]': picker, '[data-photo-preview]': preview,
        '[data-photo-status]': status, '[data-photo-error]': error })[selector];
    input.files = [];
    Object.defineProperty(input, 'value', { set() { this.files = []; } });
    const success = element(), submit = element();
    const details = { open: true, querySelector: () => element() };
    const form = element();
    const fields = { name: 'Test Student', school: 'Test School', district: 'Jaffna', comment: 'Keep this draft.' };
    form.elements = Object.fromEntries(Object.entries(fields).map(([name, value]) => [name, { ...element(), value }]));
    form.action = '/exam/packages/feedback';
    form.querySelector = selector => selector === '[data-feedback-composer]' ? composer : submit;
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
        form, input, image, status, error, add, remove, success, composer, submitButton: submit,
        isDragging: () => classes.has('is-photo-dragging'),
        async choose() {
            input.files = [{ size: 1_000_000 }];
            input.events.change();
            await new Promise(resolve => setImmediate(resolve));
        },
        drag(name, files = [], types = ['Files']) {
            const event = { dataTransfer: { files, types }, preventDefault() { this.prevented = true; } };
            composer.events[name](event);
            return event;
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
    assert.equal(view.add.focused, true);
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

test('opens the hidden picker and accepts a file drop through the same compressed upload path', async () => {
    let uploads = 0;
    const view = photoPage({ fetch: async (url, { body }) => {
        uploads += 1;
        assert.equal(body.photo.type, 'image/jpeg');
        assert.equal(body.photo.size, 91_000);
        assert.equal(body.comment, 'Keep this draft.');
        return { ok: true };
    } });
    view.add.events.click();
    assert.equal(view.input.clicked, true);
    assert.equal(view.drag('dragenter').prevented, true);
    assert.equal(view.isDragging(), true);
    view.drag('dragenter');
    view.drag('dragleave');
    assert.equal(view.isDragging(), true);
    assert.equal(view.drag('dragover').dataTransfer.dropEffect, 'copy');
    assert.equal(view.drag('drop', [{ size: 1_000_000 }]).prevented, true);
    assert.equal(view.isDragging(), false);
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(view.image.hidden, false);
    assert.equal(view.input.files.length, 0);
    await view.submit();
    assert.equal(uploads, 1);
});

test('leaves text drops alone and rejects drops with more than one file', async () => {
    const view = photoPage({ fetch: () => assert.fail('A photo was posted without an eligible drop') });
    for (const event of ['dragenter', 'dragover', 'drop']) {
        assert.equal(view.drag(event, [], ['text/plain']).prevented, undefined);
    }
    assert.equal(view.isDragging(), false);

    view.drag('dragenter');
    view.drag('dragleave');
    assert.equal(view.isDragging(), false);
    const files = [{ size: 1_000_000 }, { size: 500_000 }];
    assert.equal(view.drag('drop', files).prevented, true);
    assert.match(view.error.textContent, /one photo at a time/i);
    assert.equal(view.image.hidden, true);
    await view.submit();
    assert.equal(view.form.prevented, undefined);
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
});

test('disables attachment changes while sending and leaves the dropped photo ready to retry after failure', async () => {
    let finishUpload;
    const view = photoPage({ fetch: () => new Promise(resolve => { finishUpload = resolve; }) });
    view.drag('drop', [{ size: 500_000 }]);
    await new Promise(resolve => setImmediate(resolve));
    const sending = view.submit();
    assert.equal(view.add.disabled, true);
    assert.equal(view.remove.disabled, true);
    assert.equal(view.submitButton.disabled, true);
    assert.equal(view.drag('dragover').dataTransfer.dropEffect, 'none');
    assert.equal(view.drag('drop', [{ size: 16 * 1024 * 1024 }]).prevented, true);
    finishUpload({ ok: false, json: async () => ({ message: 'Try again later.' }) });
    await sending;
    assert.equal(view.image.hidden, false);
    assert.equal(view.add.disabled, false);
    assert.equal(view.remove.disabled, false);
    assert.equal(view.submitButton.disabled, false);
    assert.equal(view.error.textContent, 'Try again later.');
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
});
