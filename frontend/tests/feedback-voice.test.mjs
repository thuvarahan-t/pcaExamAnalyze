import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const script = readFileSync(new URL('../static/js/offer-feedback-voice.js', import.meta.url), 'utf8');
const settle = () => new Promise(resolve => setImmediate(resolve));

function voicePage({ typed = '', denied = false, supported = true, available = true, fetch } = {}) {
    class Element extends EventTarget {
        value = '';
        hidden = false;
        disabled = false;
        textContent = '';
        click() {
            if (this.hidden || this.disabled) return;
            this.focus();
            this.dispatchEvent(new Event('click'));
        }
        focus() { document.activeElement = this; }
    }
    const nodes = Object.fromEntries(['count', 'record', 'stop', 'retry', 'status', 'progress', 'progressLabel',
        'cancel', 'overflow', 'draft', 'add', 'discard'].map(
        name => [name, new Element()]
    ));
    for (const name of ['record', 'stop', 'retry', 'overflow']) nodes[name].hidden = true;
    const comment = new Element();
    comment.value = typed;
    comment.maxLength = 600;
    const school = new Element();
    const details = new Element();
    details.open = true;
    const form = new Element();
    form.elements = { comment, school };
    form.closest = () => details;
    const voice = {
        dataset: { feedbackVoice: '/exam/packages/feedback/transcribe' },
        querySelector: selector => ({ '[data-voice-record]': nodes.record,
            '[data-voice-stop]': nodes.stop, '[data-voice-retry]': nodes.retry })[selector] || null
    };
    form.querySelector = selector => ({ '[data-feedback-count]': nodes.count,
        '[data-feedback-voice]': available ? voice : null, '[data-voice-status]': available ? nodes.status : null,
        '[data-voice-progress]': available ? nodes.progress : null,
        '[data-voice-overflow]': available ? nodes.overflow : null })[selector] || null;
    nodes.progress.querySelector = selector => ({
        '[data-voice-progress-label]': nodes.progressLabel, '[data-voice-cancel]': nodes.cancel
    })[selector];
    nodes.overflow.querySelector = selector => ({ textarea: nodes.draft,
        '[data-voice-add]': nodes.add, '[data-voice-discard]': nodes.discard })[selector];
    const body = new Element();
    const document = { body, activeElement: body, querySelector: selector => ({ '[data-feedback-form]': form,
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
    return { ...nodes, comment, school, form, details, timers, released: () => releaseCount,
        voiceState: () => voice.dataset.voiceState, focused: () => document.activeElement };
}

test('records supported audio, stops at 30 seconds and appends editable Tamil without replacing typing', async () => {
    const page = voicePage({ typed: 'I liked the lab.', fetch: async (url, request) => {
        assert.equal(url, '/exam/packages/feedback/transcribe');
        assert.equal(request.headers['Content-Type'], 'audio/mp4');
        assert.equal(request.headers['X-CSRF-TOKEN'], 'csrf');
        assert.equal(await request.body.text(), 'sample');
        return { ok: true, json: async () => ({ text: 'நன்றி.' }) };
    } });
    assert.equal(page.voiceState(), 'ready');
    page.record.click();
    assert.equal(page.voiceState(), 'permission');
    assert.equal(page.record.disabled, true);
    await settle();
    assert.equal(page.voiceState(), 'recording');
    assert.equal(page.stop.hidden, false);
    assert.equal(page.focused(), page.stop);
    page.timers.get(30_000)();
    assert.equal(page.voiceState(), 'processing');
    assert.equal(page.stop.hidden, true);
    assert.equal(page.progress.hidden, false);
    await settle();
    assert.equal(page.voiceState(), 'ready');
    assert.equal(page.progress.hidden, true);
    assert.equal(page.focused(), page.comment);
    assert.equal(page.released(), 1);
    assert.equal(page.comment.value, 'I liked the lab. நன்றி.');
    assert.equal(page.count.textContent, `${page.comment.value.length} / 600`);
});

test('keeps typing available when microphone permission fails, recording is unsupported or keys are missing', async () => {
    const denied = voicePage({ typed: 'Original draft', denied: true, fetch: () => assert.fail('Uploaded audio') });
    denied.record.click();
    await settle();
    assert.equal(denied.voiceState(), 'ready');
    assert.equal(denied.record.disabled, false);
    assert.match(denied.status.textContent, /denied/);
    assert.equal(denied.comment.value, 'Original draft');
    const unsupported = voicePage({ supported: false });
    assert.equal(unsupported.voiceState(), 'unavailable');
    assert.equal(unsupported.record.hidden, true);
    assert.match(unsupported.status.textContent, /unavailable/);
    const unconfigured = voicePage({ available: false, typed: 'Always editable' });
    assert.equal(unconfigured.count.textContent, '15 / 600');
    assert.equal(unconfigured.record.hidden, true);
});

test('retries the same recording and keeps a transcript editable when it exceeds the feedback limit', async () => {
    const recordings = [];
    const page = voicePage({ typed: 'a'.repeat(599), fetch: async (url, request) => {
        recordings.push(request.body);
        return recordings.length === 1
            ? { ok: false, json: async () => ({ message: 'Try again shortly.' }) }
            : { ok: true, json: async () => ({ text: 'நன்றி.' }) };
    } });
    page.record.click();
    await settle();
    page.stop.click();
    await settle();
    assert.equal(page.voiceState(), 'retry');
    assert.equal(page.retry.hidden, false);
    assert.equal(page.status.textContent, 'Try again shortly.');
    assert.equal(page.focused(), page.retry);
    page.retry.click();
    assert.equal(page.voiceState(), 'processing');
    await settle();
    assert.equal(recordings.length, 2);
    assert.equal(recordings[1], recordings[0]);
    assert.equal(page.voiceState(), 'ready');
    assert.equal(page.comment.value.length, 599);
    assert.equal(page.overflow.hidden, false);
    assert.equal(page.record.disabled, true);
    assert.equal(page.draft.value, 'நன்றி.');
    assert.equal(page.focused(), page.draft);
    page.comment.value = 'Shorter draft';
    page.add.click();
    assert.equal(page.comment.value, 'Shorter draft நன்றி.');
    assert.equal(page.overflow.hidden, true);
    assert.equal(page.record.disabled, false);
});

test('keeps another field focused while a recording starts and returns a transcript', async () => {
    const page = voicePage({ fetch: async () => ({ ok: true, json: async () => ({ text: 'நன்றி.' }) }) });
    page.record.click();
    page.school.focus();
    await settle();
    assert.equal(page.voiceState(), 'recording');
    assert.equal(page.focused(), page.school);
    page.timers.get(30_000)();
    await settle();
    assert.equal(page.voiceState(), 'ready');
    assert.equal(page.comment.value, 'நன்றி.');
    assert.equal(page.focused(), page.school);
});

test('closing the form clears processing state and ignores a late transcript', async () => {
    let finish;
    let signal;
    const page = voicePage({ typed: 'Original draft', fetch: (url, request) => {
        signal = request.signal;
        return new Promise(resolve => { finish = resolve; });
    } });
    page.record.click();
    await settle();
    page.stop.click();
    await settle();
    assert.equal(page.voiceState(), 'processing');
    assert.equal(signal.aborted, false);
    assert.equal(page.progressLabel.textContent, 'Transcribing…');
    page.timers.get(8_000)();
    assert.equal(page.progressLabel.textContent, 'Still transcribing…');
    page.details.open = false;
    page.details.dispatchEvent(new Event('toggle'));
    assert.equal(signal.aborted, true);
    assert.equal(page.voiceState(), 'ready');
    assert.equal(page.status.textContent, '');
    finish({ ok: true, json: async () => ({ text: 'நன்றி.' }) });
    await settle();
    assert.equal(page.comment.value, 'Original draft');
    assert.equal(page.voiceState(), 'ready');
});

test('canceling an active transcription keeps typed feedback and returns focus to the mic', async () => {
    let complete;
    let signal;
    const page = voicePage({ typed: 'Original feedback', fetch: (url, request) => {
        signal = request.signal;
        return new Promise(resolve => { complete = resolve; });
    } });
    page.record.click();
    await settle();
    page.stop.click();
    await settle();
    assert.equal(page.focused(), page.cancel);
    page.comment.value += ' with more details';
    const submittedWhileTranscribing = new Event('submit', { cancelable: true });
    assert.equal(page.form.dispatchEvent(submittedWhileTranscribing), false);
    assert.match(page.progressLabel.textContent, /cancel to send/);
    page.cancel.click();
    assert.equal(signal.aborted, true);
    assert.equal(page.progress.hidden, true);
    assert.equal(page.focused(), page.record);
    assert.equal(page.form.dispatchEvent(new Event('submit', { cancelable: true })), true);
    complete({ ok: true, json: async () => ({ text: 'நன்றி.' }) });
    await settle();
    assert.equal(page.comment.value, 'Original feedback with more details');
    assert.equal(page.status.textContent, 'Transcription canceled.');
});
