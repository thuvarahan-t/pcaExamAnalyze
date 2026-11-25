import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const script = readFileSync(new URL('../static/js/offer-feedback-voice.js', import.meta.url), 'utf8');
const settle = () => new Promise(resolve => setImmediate(resolve));

function voicePage({ typed = '', denied = false, supported = true, available = true, fetch } = {}) {
    class Element extends EventTarget {
        value = '';
        hidden = true;
        textContent = '';
        click() { this.dispatchEvent(new Event('click')); }
        focus() {}
    }
    const nodes = Object.fromEntries(['count', 'record', 'stop', 'retry', 'status', 'overflow', 'draft', 'add', 'discard'].map(
        name => [name, new Element()]
    ));
    const comment = new Element();
    comment.value = typed;
    comment.maxLength = 600;
    const form = new Element();
    form.elements = { comment };
    form.closest = () => new Element();
    form.querySelector = selector => selector === '[data-feedback-count]' ? nodes.count : available ? voice : null;
    const voice = {
        dataset: { feedbackVoice: '/exam/packages/feedback/transcribe' },
        querySelector: selector => nodes[selector.match(/\[data-voice-(.*)\]/)[1]]
    };
    nodes.overflow.querySelector = selector => ({ textarea: nodes.draft,
        '[data-voice-add]': nodes.add, '[data-voice-discard]': nodes.discard })[selector];
    const document = { querySelector: selector => ({ '[data-feedback-form]': form,
        'meta[name="_csrf"]': { content: 'csrf' }, 'meta[name="_csrf_header"]': { content: 'X-CSRF-TOKEN' } })[selector] };
    let releaseCount = 0;
    class MediaRecorder extends EventTarget {
        static isTypeSupported(type) { return supported && type === 'audio/mp4'; }
        constructor(stream, { mimeType }) { super(); this.mimeType = mimeType; }
        start() { this.state = 'recording'; }
        stop() {
            this.state = 'inactive';
            setImmediate(() => {
                const event = new Event('dataavailable');
                event.data = new Blob(['sample']);
                this.dispatchEvent(event);
                this.dispatchEvent(new Event('stop'));
            });
        }
    }
    const window = new EventTarget();
    window.MediaRecorder = MediaRecorder;
    const navigator = { mediaDevices: { async getUserMedia() {
        if (denied) throw Object.assign(new Error(), { name: 'NotAllowedError' });
        return { getTracks: () => [{ stop() { releaseCount += 1; } }] };
    } } };
    const timers = new Map();
    vm.runInNewContext(script, { document, window, navigator, fetch, Blob, Event, AbortController,
        setTimeout(callback, duration) { timers.set(duration, callback); return duration; },
        clearTimeout(duration) { timers.delete(duration); } });
    return { ...nodes, comment, timers, released: () => releaseCount };
}

test('records supported audio, stops at 30 seconds and appends editable Tamil without replacing typing', async () => {
    const page = voicePage({ typed: 'I liked the lab.', fetch: async (url, request) => {
        assert.equal(url, '/exam/packages/feedback/transcribe');
        assert.equal(request.headers['Content-Type'], 'audio/mp4');
        assert.equal(request.headers['X-CSRF-TOKEN'], 'csrf');
        assert.equal(await request.body.text(), 'sample');
        return { ok: true, json: async () => ({ text: 'நன்றி.' }) };
    } });
    page.record.click();
    await settle();
    assert.equal(page.stop.hidden, false);
    page.timers.get(30_000)();
    await settle();
    assert.equal(page.released(), 1);
    assert.equal(page.comment.value, 'I liked the lab. நன்றி.');
    assert.equal(page.count.textContent, `${page.comment.value.length} / 600 characters`);
});

test('keeps typing available when microphone permission fails, recording is unsupported or keys are missing', async () => {
    const denied = voicePage({ typed: 'Original draft', denied: true, fetch: () => assert.fail('Uploaded audio') });
    denied.record.click();
    await settle();
    assert.match(denied.status.textContent, /denied/);
    assert.equal(denied.comment.value, 'Original draft');
    const unsupported = voicePage({ supported: false });
    assert.match(unsupported.status.textContent, /unavailable/);
    const unconfigured = voicePage({ available: false, typed: 'Always editable' });
    assert.equal(unconfigured.count.textContent, '15 / 600 characters');
    assert.equal(unconfigured.record.hidden, true);
});

test('retries the same recording and keeps a transcript editable when it exceeds the feedback limit', async () => {
    let attempts = 0;
    const page = voicePage({ typed: 'a'.repeat(599), fetch: async () => {
        attempts += 1;
        return attempts === 1
            ? { ok: false, json: async () => ({ message: 'Try again shortly.' }) }
            : { ok: true, json: async () => ({ text: 'நன்றி.' }) };
    } });
    page.record.click();
    await settle();
    page.stop.click();
    await settle();
    assert.equal(page.retry.hidden, false);
    assert.equal(page.status.textContent, 'Try again shortly.');
    page.retry.click();
    await settle();
    assert.equal(attempts, 2);
    assert.equal(page.comment.value.length, 599);
    assert.equal(page.overflow.hidden, false);
    assert.equal(page.draft.value, 'நன்றி.');
    page.comment.value = 'Shorter draft';
    page.add.click();
    assert.equal(page.comment.value, 'Shorter draft நன்றி.');
    assert.equal(page.overflow.hidden, true);
});
