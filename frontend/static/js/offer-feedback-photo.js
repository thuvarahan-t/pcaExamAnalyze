(() => {
    const maxPhotos = 5;
    const maxPhotoBytes = 100_000;
    const maxSourceBytes = 15 * 1024 * 1024;
    const maxSourcePixels = 50_000_000;
    const form = document.querySelector('[data-feedback-form]');
    if (!form || !document.createElement('canvas').toBlob || !window.fetch || !window.FormData) return;

    const csrf = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    const composer = form.querySelector('[data-feedback-composer]');
    const picker = composer?.querySelector('[data-feedback-photo]');
    if (!csrf || !csrfHeader || !picker) return;

    const input = picker.querySelector('input[type="file"]');
    const add = picker.querySelector('[data-add-photo]');
    const quickPreview = composer.querySelector('[data-photo-preview]');
    const photoItem = quickPreview.querySelector('[data-photo-item-template]')?.content.firstElementChild;
    if (!photoItem) return;
    const status = composer.querySelector('[data-photo-status]');
    const error = composer.querySelector('[data-photo-error]');
    const submit = form.querySelector('[type="submit"]');
    const feedback = form.closest('.offer-story-response');
    const success = feedback.querySelector('[data-feedback-success]');
    const details = form.closest('details');

    const photos = [];
    let preparing = false;
    let sending = false;
    let dragDepth = 0;

    function showError(message = '') {
        error.textContent = message;
        error.hidden = !message;
    }

    function syncControls() {
        const busy = preparing || sending;
        input.disabled = add.disabled = submit.disabled = busy;
        for (const { remove } of photos) remove.disabled = busy;
    }

    function updatePreview() {
        quickPreview.hidden = photos.length === 0;
        photos.forEach(({ remove }, index) => remove.setAttribute('aria-label', `Remove photo ${index + 1}`));
        status.textContent = photos.length === 0 ? '' : photos.length === 1 ? 'Photo ready.' : `${photos.length} photos ready.`;
    }

    function discardPreview({ item, previewUrl }) {
        URL.revokeObjectURL(previewUrl);
        item.remove();
    }

    function addPreview(blob) {
        const item = photoItem.cloneNode(true);
        const remove = item.querySelector('[data-remove-photo]');
        const previewUrl = URL.createObjectURL(blob);
        const attachment = { blob, item, remove, previewUrl };
        item.querySelector('img').src = previewUrl;
        remove.addEventListener('click', () => {
            if (preparing || sending) return;
            const index = photos.indexOf(attachment);
            if (index < 0) return;
            photos.splice(index, 1);
            discardPreview(attachment);
            showError();
            updatePreview();
            (photos[index]?.remove || photos.at(-1)?.remove || add).focus();
        });
        photos.push(attachment);
        quickPreview.append(item);
    }

    async function compress(file) {
        if (file.size > maxSourceBytes) throw new Error('Choose a photo smaller than 15 MB.');
        const sourceUrl = URL.createObjectURL(file);
        try {
            const source = new Image();
            source.src = sourceUrl;
            try {
                await source.decode();
            } catch {
                throw new Error('This photo could not be opened. Try a JPEG or PNG.');
            }
            const width = source.naturalWidth;
            const height = source.naturalHeight;
            if (width < 1 || height < 1 || width * height > maxSourcePixels) {
                throw new Error('Choose a photo with smaller dimensions.');
            }
            // Mobile browsers apply EXIF orientation when decoding the source image.
            const canvas = document.createElement('canvas');
            const longestSide = Math.max(width, height);
            for (let edge = Math.min(1440, longestSide); ; edge = Math.max(360, Math.floor(edge * 0.75))) {
                canvas.width = Math.max(1, Math.round(width * edge / longestSide));
                canvas.height = Math.max(1, Math.round(height * edge / longestSide));
                const context = canvas.getContext('2d', { alpha: false });
                context.fillStyle = '#fff';
                context.fillRect(0, 0, canvas.width, canvas.height);
                context.drawImage(source, 0, 0, canvas.width, canvas.height);
                for (const quality of [0.86, 0.74, 0.62, 0.52]) {
                    const jpeg = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', quality));
                    if (jpeg?.type === 'image/jpeg' && jpeg.size > 0 && jpeg.size <= maxPhotoBytes) return jpeg;
                }
                if (edge <= 360) break;
            }
            throw new Error('This photo could not fit within 100 KB.');
        } finally {
            URL.revokeObjectURL(sourceUrl);
        }
    }

    async function selectPhotos(files) {
        if (preparing || sending || files.length === 0) return;
        if (photos.length + files.length > maxPhotos) {
            showError(`Attach up to ${maxPhotos} photos. Choose fewer photos or remove an existing one.`);
            return;
        }
        preparing = true;
        syncControls();
        showError();
        status.textContent = files.length === 1 ? 'Preparing photo…' : 'Preparing photos…';
        try {
            const compressed = [];
            // Process one source at a time to limit decoded image memory. Commit only a complete batch.
            for (const file of files) compressed.push(await compress(file));
            for (const blob of compressed) addPreview(blob);
        } catch (failure) {
            showError(`${failure.message || 'The photo could not be prepared.'} No photos from this selection were added.`);
        } finally {
            preparing = false;
            syncControls();
            updatePreview();
        }
    }

    function isFileDrag(event) {
        const transfer = event.dataTransfer;
        return transfer && (transfer.files?.length > 0 || Array.from(transfer.types || []).includes('Files'));
    }

    function clearDrag() {
        dragDepth = 0;
        composer.classList.toggle('is-photo-dragging', false);
    }

    add.addEventListener('click', () => input.click());
    input.addEventListener('change', () => {
        const files = Array.from(input.files);
        input.value = '';
        return selectPhotos(files);
    });

    composer.addEventListener('dragenter', event => {
        if (!isFileDrag(event)) return;
        event.preventDefault();
        if (preparing || sending) return;
        dragDepth += 1;
        composer.classList.toggle('is-photo-dragging', true);
    });

    composer.addEventListener('dragover', event => {
        if (!isFileDrag(event)) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = preparing || sending ? 'none' : 'copy';
    });

    composer.addEventListener('dragleave', () => {
        dragDepth = Math.max(0, dragDepth - 1);
        if (dragDepth === 0) clearDrag();
    });

    composer.addEventListener('drop', event => {
        if (!isFileDrag(event)) return;
        event.preventDefault();
        clearDrag();
        return selectPhotos(Array.from(event.dataTransfer.files));
    });

    form.addEventListener('submit', async event => {
        if (event.defaultPrevented) return;
        if (photos.length === 0 && !preparing && !sending) return;
        event.preventDefault();
        if (preparing || sending) return;

        const body = new FormData(form);
        photos.forEach(({ blob }, index) => body.append('photo', blob, `feedback-${index + 1}.jpg`));
        sending = true;
        syncControls();
        clearDrag();
        status.textContent = 'Sending feedback…';
        success.hidden = true;
        showError();
        try {
            const response = await fetch(form.action, {
                method: 'POST', body, redirect: 'error',
                headers: { [csrfHeader.content]: csrf.content, 'X-Feedback-Upload': 'true', Accept: 'application/json' }
            });
            if (!response.ok) {
                const result = await response.json().catch(() => ({}));
                showError(result.message || 'Feedback with photos could not be sent. Try again or remove the photos to send text only.');
                return;
            }
            photos.splice(0).forEach(discardPreview);
            updatePreview();
            for (const field of ['name', 'school', 'district', 'comment']) {
                form.elements[field].value = '';
            }
            form.elements.district.dispatchEvent(new Event('change', { bubbles: true }));
            form.elements.comment.dispatchEvent(new Event('input', { bubbles: true }));
            form.dispatchEvent(new Event('feedback:sent'));
            success.hidden = false;
            details.open = false;
            details.querySelector('summary').focus();
        } catch {
            showError('Feedback could not be sent. Try again or remove the photos to send text only.');
        } finally {
            status.textContent = '';
            sending = false;
            syncControls();
        }
    });

    picker.hidden = false;
})();
