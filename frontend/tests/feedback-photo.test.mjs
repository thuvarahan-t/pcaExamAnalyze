import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const script = readFileSync(new URL('../static/js/offer-feedback-photo.js', import.meta.url), 'utf8');

function photoPage({ decode, fetch } = {}) {
    function element() {
        return {
            events: {}, attributes: {}, hidden: true, focus() { this.focused = true; }, click() { this.clicked = true; },
            setAttribute(name, value) { this.attributes[name] = value; },
            addEventListener(name, handler) { this.events[name] = handler; },
            dispatchEvent() {}
        };
    }
    const input = element(), add = element(), error = element(), status = element();
    const template = { content: { firstElementChild: { cloneNode() {
        const image = element(), remove = element();
        return { image, removeButton: remove, querySelector: selector => ({ img: image, '[data-remove-photo]': remove })[selector] };
    } } } };
    const items = [];
    const preview = { ...element(), querySelector: () => template, append(item) {
        items.push(item);
        item.remove = () => items.splice(items.indexOf(item), 1);
    } };
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
        attachments = [];
        append(name, blob, filename) { this.attachments.push({ name, blob, filename }); }
    }
    const liveUrls = new Map();
    const revokedUrls = [];
    let nextUrl = 0;
    class Image {
        naturalWidth = 2200;
        naturalHeight = 1200;
        async decode() {
            const file = liveUrls.get(this.src);
            if (decode) await decode(file);
            if (file.invalid) throw new Error('Invalid image');
        }
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
        FormData, Image, Event, fetch, URL: {
            createObjectURL(value) {
                const url = `blob:photo-${++nextUrl}`;
                liveUrls.set(url, value);
                return url;
            },
            revokeObjectURL(url) { liveUrls.delete(url); revokedUrls.push(url); }
        } });
    return {
        form, input, items, preview, status, error, add, success, submitButton: submit, revokedUrls,
        isDragging: () => classes.has('is-photo-dragging'),
        choose(...files) {
            input.files = files;
            return input.events.change();
        },
        drag(name, files = [], types = ['Files']) {
            const event = { dataTransfer: { files, types }, preventDefault() { this.prevented = true; } };
            event.finished = composer.events[name](event);
            return event;
        },
        submit: () => form.events.submit({ preventDefault: () => { form.prevented = true; } })
    };
}

const photo = (size = 1_000_000) => ({ size });

test('accumulates multiple picks and posts each compressed photo as a repeated multipart field', async () => {
    const view = photoPage({ fetch: async (url, { body, headers }) => {
        assert.equal(url, '/exam/packages/feedback');
        assert.deepEqual(body.attachments.map(part => part.name), ['photo', 'photo', 'photo']);
        assert.deepEqual(body.attachments.map(part => part.filename), ['feedback-1.jpg', 'feedback-2.jpg', 'feedback-3.jpg']);
        for (const { blob } of body.attachments) {
            assert.equal(blob.type, 'image/jpeg');
            assert.equal(blob.size, 91_000);
        }
        assert.equal(body.comment, 'Keep this draft.');
        assert.equal(headers['X-CSRF-TOKEN'], 'csrf');
        assert.equal(headers['X-Feedback-Upload'], 'true');
        return { ok: false, json: async () => ({ message: 'Photo storage is unavailable. Remove the photos to send text only.' }) };
    } });
    await view.choose(photo(), photo());
    await view.choose(photo());
    assert.equal(view.preview.hidden, false);
    assert.equal(view.items.length, 3);
    assert.equal(view.status.textContent, '3 photos ready.');
    await view.submit();
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
    assert.equal(view.items.length, 3);
    assert.match(view.error.textContent, /storage is unavailable.*text only/i);
});

test('rejects a whole batch if one source is invalid, preserving previous photos and the draft', async () => {
    const view = photoPage({ fetch: () => assert.fail('Selecting photos must not post the draft') });
    await view.choose(photo());
    const first = view.items[0];
    await view.choose(photo(), { ...photo(), invalid: true });
    assert.deepEqual(view.items, [first]);
    assert.match(view.error.textContent, /could not be opened.*No photos from this selection were added/i);
    assert.equal(view.status.textContent, 'Photo ready.');
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
});

test('previews independent attachments, removes one at a time, then allows native text-only submission', async () => {
    const view = photoPage({ fetch: () => assert.fail('The text-only form should submit normally') });
    await view.choose(photo(), photo(), photo());
    const [first, middle, last] = view.items;
    assert.equal(middle.removeButton.attributes['aria-label'], 'Remove photo 2');
    middle.removeButton.events.click();
    assert.deepEqual(view.items, [first, last]);
    assert.equal(last.removeButton.attributes['aria-label'], 'Remove photo 2');
    assert.ok(view.revokedUrls.includes(middle.image.src));
    assert.ok(!view.revokedUrls.includes(last.image.src));
    assert.equal(last.removeButton.focused, true);
    first.removeButton.events.click();
    last.removeButton.events.click();
    assert.equal(view.preview.hidden, true);
    assert.equal(view.add.focused, true);
    await view.choose({ ...photo(), invalid: true });
    await view.submit();
    assert.equal(view.form.prevented, undefined);
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
});

test('clears the submitted draft and every preview on upload success', async () => {
    const view = photoPage({ fetch: async () => ({ ok: true }) });
    await view.choose(photo(), photo());
    const previewUrls = view.items.map(item => item.image.src);
    await view.submit();
    assert.equal(view.items.length, 0);
    assert.ok(previewUrls.every(url => view.revokedUrls.includes(url)));
    assert.equal(view.form.elements.comment.value, '');
    assert.equal(view.input.files.length, 0);
    assert.equal(view.success.hidden, false);
});

test('opens the hidden picker and accumulates dropped files up to the five-photo limit', async () => {
    const view = photoPage();
    view.add.events.click();
    assert.equal(view.input.clicked, true);
    assert.equal(view.drag('dragenter').prevented, true);
    assert.equal(view.isDragging(), true);
    view.drag('dragenter');
    view.drag('dragleave');
    assert.equal(view.isDragging(), true);
    assert.equal(view.drag('dragover').dataTransfer.dropEffect, 'copy');
    const drop = view.drag('drop', [photo(), photo(), photo()]);
    assert.equal(drop.prevented, true);
    assert.equal(view.isDragging(), false);
    await drop.finished;
    await view.choose(photo(), photo());
    assert.equal(view.items.length, 5);
    assert.equal(view.input.files.length, 0);
    const selected = [...view.items];
    await view.choose(photo());
    await view.drag('drop', [photo(), photo()]).finished;
    assert.deepEqual(view.items, selected);
    assert.match(view.error.textContent, /up to 5 photos/i);
});

test('leaves text drops alone and rejects an over-limit batch without keeping a subset', async () => {
    const view = photoPage();
    for (const event of ['dragenter', 'dragover', 'drop']) {
        assert.equal(view.drag(event, [], ['text/plain']).prevented, undefined);
    }
    assert.equal(view.isDragging(), false);

    view.drag('dragenter');
    view.drag('dragleave');
    assert.equal(view.isDragging(), false);
    const files = Array.from({ length: 6 }, () => photo());
    const drop = view.drag('drop', files);
    assert.equal(drop.prevented, true);
    await drop.finished;
    assert.match(view.error.textContent, /up to 5 photos/i);
    assert.equal(view.preview.hidden, true);
    await view.submit();
    assert.equal(view.form.prevented, undefined);
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
});

test('blocks partial submission while compressing a new batch', async () => {
    let finishDecode;
    const waiting = photo();
    const view = photoPage({
        decode: file => file === waiting ? new Promise(resolve => { finishDecode = resolve; }) : undefined,
        fetch: () => assert.fail('No partial photo upload should occur during compression')
    });
    await view.choose(photo());
    const preparing = view.choose(waiting, photo());
    await Promise.resolve();
    assert.equal(view.items.length, 1);
    assert.equal(view.add.disabled, true);
    assert.equal(view.submitButton.disabled, true);
    await view.submit();
    assert.equal(view.form.prevented, true);
    finishDecode();
    await preparing;
    assert.equal(view.items.length, 3);
    assert.equal(view.submitButton.disabled, false);
});

test('disables attachment changes while sending and leaves the batch ready to retry after failure', async () => {
    let finishUpload;
    const view = photoPage({ fetch: () => new Promise(resolve => { finishUpload = resolve; }) });
    await view.drag('drop', [photo(), photo()]).finished;
    const sending = view.submit();
    assert.equal(view.add.disabled, true);
    assert.ok(view.items.every(item => item.removeButton.disabled));
    assert.equal(view.submitButton.disabled, true);
    assert.equal(view.drag('dragover').dataTransfer.dropEffect, 'none');
    assert.equal(view.drag('drop', [{ size: 16 * 1024 * 1024 }]).prevented, true);
    finishUpload({ ok: false, json: async () => ({ message: 'Try again later.' }) });
    await sending;
    assert.equal(view.items.length, 2);
    assert.equal(view.add.disabled, false);
    assert.ok(view.items.every(item => !item.removeButton.disabled));
    assert.equal(view.submitButton.disabled, false);
    assert.equal(view.error.textContent, 'Try again later.');
    assert.equal(view.form.elements.comment.value, 'Keep this draft.');
});
