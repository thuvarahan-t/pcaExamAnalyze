(() => {
    const maxRecordingMs = 30_000;
    const maxAudioBytes = 2_000_000;
    const form = document.querySelector('[data-feedback-form]');
    if (!form) return;

    const comment = form.elements.comment;
    const counter = form.querySelector('[data-feedback-count]');
    const maxLength = comment.maxLength;
    function updateCount() {
        counter.textContent = `${comment.value.length} / ${maxLength}`;
    }
    comment.addEventListener('input', updateCount);
    updateCount();

    const voice = form.querySelector('[data-feedback-voice]');
    if (!voice) return;
    const record = voice.querySelector('[data-voice-record]');
    const stop = voice.querySelector('[data-voice-stop]');
    const retry = voice.querySelector('[data-voice-retry]');
    const status = form.querySelector('[data-voice-status]');
    const overflow = form.querySelector('[data-voice-overflow]');
    const draft = overflow.querySelector('textarea');
    const addDraft = overflow.querySelector('[data-voice-add]');
    const discardDraft = overflow.querySelector('[data-voice-discard]');
    const voiceControls = [record, stop, retry, addDraft, discardDraft];
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    const Recorder = window.MediaRecorder;
    const mimeType = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus', 'audio/webm', 'audio/ogg']
        .find(type => Recorder?.isTypeSupported?.(type));
    if (!mimeType || !navigator.mediaDevices?.getUserMedia || !csrf || !csrfHeader) {
        show('unavailable', 'Recording unavailable. You can still type.');
        return;
    }

    let generation = 0;
    let recorder;
    let stream;
    let timer;
    let upload;
    let lastAudio;

    function show(mode, message = '') {
        voice.dataset.voiceState = mode;
        record.hidden = mode === 'recording' || mode === 'processing' || mode === 'unavailable';
        record.disabled = mode === 'permission' || !overflow.hidden;
        stop.hidden = mode !== 'recording';
        retry.hidden = mode !== 'retry';
        status.textContent = message;
    }

    function moveVoiceFocus(target) {
        const active = document.activeElement;
        if (active === document.body || voiceControls.includes(active)) target.focus();
    }

    function releaseMicrophone() {
        clearTimeout(timer);
        stream?.getTracks().forEach(track => track.stop());
        stream = undefined;
        recorder = undefined;
    }

    function append(text) {
        const separator = comment.value && !/\s$/u.test(comment.value) ? ' ' : '';
        if (comment.value.length + separator.length + text.length > maxLength) return false;
        comment.value += separator + text;
        comment.dispatchEvent(new Event('input', { bubbles: true }));
        return true;
    }

    function applyTranscript(text) {
        if (append(text)) {
            clearDraft();
            show('ready', 'Transcript added. Review before sending.');
            moveVoiceFocus(comment);
            return;
        }
        draft.value = text;
        overflow.hidden = false;
        show('ready', `Shorten the feedback or transcript to fit ${maxLength} characters.`);
        moveVoiceFocus(draft);
    }

    function clearDraft() {
        overflow.hidden = true;
        draft.value = '';
    }

    async function transcribe() {
        const current = generation;
        show('processing', 'Transcribing Tamil…');
        const controller = new AbortController();
        upload = controller;
        const deadline = setTimeout(() => controller.abort(), 24_000);
        try {
            const response = await fetch(voice.dataset.feedbackVoice, {
                method: 'POST', redirect: 'error', body: lastAudio, signal: controller.signal,
                headers: { [csrfHeader]: csrf, 'Content-Type': lastAudio.type }
            });
            const result = await response.json().catch(() => ({}));
            if (current !== generation) return;
            if (!response.ok || typeof result?.text !== 'string' || !result.text.trim()) {
                show('retry', result?.message || 'Transcription failed. Try again.');
                moveVoiceFocus(retry);
                return;
            }
            lastAudio = undefined;
            applyTranscript(result.text.trim());
        } catch {
            if (current === generation) {
                show('retry', 'Transcription unavailable. Try again.');
                moveVoiceFocus(retry);
            }
        } finally {
            clearTimeout(deadline);
            if (upload === controller) upload = undefined;
        }
    }

    function stopRecording() {
        if (recorder?.state !== 'recording') return;
        clearTimeout(timer);
        show('processing', 'Preparing recording…');
        recorder.stop();
    }

    async function startRecording() {
        const current = ++generation;
        let chunks = [];
        let size = 0;
        lastAudio = undefined;
        show('permission', 'Allow microphone access.');
        try {
            const microphone = await navigator.mediaDevices.getUserMedia({ audio: true });
            if (current !== generation) {
                microphone.getTracks().forEach(track => track.stop());
                return;
            }
            stream = microphone;
            const capture = new Recorder(stream, { mimeType });
            recorder = capture;
            capture.addEventListener('dataavailable', event => {
                if (current !== generation || !event.data.size) return;
                size += event.data.size;
                if (size > maxAudioBytes) {
                    chunks = [];
                    stopRecording();
                } else {
                    chunks.push(event.data);
                }
            });
            capture.addEventListener('error', () => {
                if (current !== generation) return;
                generation += 1;
                releaseMicrophone();
                show('ready', 'Recording interrupted. Try again.');
                moveVoiceFocus(record);
            });
            capture.addEventListener('stop', () => {
                if (current !== generation) return;
                releaseMicrophone();
                const audio = new Blob(chunks, { type: capture.mimeType || mimeType });
                chunks = [];
                if (!audio.size || audio.size > maxAudioBytes) {
                    show('ready', 'Try a shorter recording.');
                    moveVoiceFocus(record);
                    return;
                }
                lastAudio = audio;
                transcribe();
            });
            capture.start(250);
            timer = setTimeout(stopRecording, maxRecordingMs);
            show('recording', 'Recording Tamil…');
            moveVoiceFocus(stop);
        } catch (error) {
            if (current !== generation) return;
            releaseMicrophone();
            const message = error.name === 'NotAllowedError'
                ? 'Microphone access denied. Check browser settings.'
                : 'Microphone unavailable. Try again.';
            show('ready', message);
            moveVoiceFocus(record);
        }
    }

    function cancel() {
        generation += 1;
        if (recorder?.state === 'recording') recorder.stop();
        releaseMicrophone();
        upload?.abort();
        lastAudio = undefined;
        show('ready');
    }

    record.addEventListener('click', startRecording);
    stop.addEventListener('click', stopRecording);
    retry.addEventListener('click', transcribe);
    addDraft.addEventListener('click', () => {
        if (draft.value.trim()) applyTranscript(draft.value.trim());
    });
    discardDraft.addEventListener('click', () => {
        clearDraft();
        show('ready');
        record.focus();
    });
    form.addEventListener('feedback:sent', () => {
        clearDraft();
        cancel();
    });
    const details = form.closest('details');
    details.addEventListener('toggle', () => {
        if (!details.open) cancel();
    });
    window.addEventListener('pagehide', cancel);
    show('ready');
})();
