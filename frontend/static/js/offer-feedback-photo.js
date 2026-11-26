(() => {
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
    const image = quickPreview.querySelector('img');
    const remove = quickPreview.querySelector('[data-remove-photo]');
    const status = composer.querySelector('[data-photo-status]');
    const error = composer.querySelector('[data-photo-error]');
    const submit = form.querySelector('[type="submit"]');
    const feedback = form.closest('.offer-story-response');
    const success = feedback.querySelector('[data-feedback-success]');
    const details = form.closest('details');

    let photo = null;
    let previewUrl;
    let selection = 0;
    let hasSelection = false;
    let sending = false;
    let dragDepth = 0;

    function showError(message = '') {
        error.textContent = message;
        error.hidden = !message;
    }

    function clearPreview() {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = undefined;
        image.removeAttribute('src');
        image.hidden = true;
        photo = null;
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

    async function selectPhoto(file) {
        if (sending) return;
        const current = ++selection;
        const sourceLabel = 'Choose another photo or remove it to send feedback with text only.';
        hasSelection = true;
        clearPreview();
        showError();
        quickPreview.hidden = false;
        status.textContent = 'Preparing photo…';
        try {
            const compressed = await compress(file);
            if (current !== selection) return;
            photo = compressed;
            previewUrl = URL.createObjectURL(photo);
            image.src = previewUrl;
            image.hidden = false;
            status.textContent = 'Photo ready.';
        } catch (failure) {
            if (current !== selection) return;
            status.textContent = '';
            showError(`${failure.message || 'The photo could not be prepared.'} ${sourceLabel}`);
        }
    }

    function removePhoto() {
        selection += 1;
        input.value = '';
        clearPreview();
        quickPreview.hidden = true;
        hasSelection = false;
        status.textContent = '';
        showError();
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
        const file = input.files[0];
        input.value = '';
        if (file) selectPhoto(file);
    });

    remove.addEventListener('click', () => {
        removePhoto();
        add.focus();
    });

    composer.addEventListener('dragenter', event => {
        if (!isFileDrag(event)) return;
        event.preventDefault();
        if (sending) return;
        dragDepth += 1;
        composer.classList.toggle('is-photo-dragging', true);
    });

    composer.addEventListener('dragover', event => {
        if (!isFileDrag(event)) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = sending ? 'none' : 'copy';
    });

    composer.addEventListener('dragleave', () => {
        dragDepth = Math.max(0, dragDepth - 1);
        if (dragDepth === 0) clearDrag();
    });

    composer.addEventListener('drop', event => {
        if (!isFileDrag(event)) return;
        event.preventDefault();
        clearDrag();
        if (sending) return;
        const files = event.dataTransfer.files;
        if (files.length > 1) {
            showError('Attach one photo at a time.');
        } else if (files.length === 1) {
            selectPhoto(files[0]);
        }
    });

    form.addEventListener('submit', async event => {
        if (!hasSelection && !sending) return;
        event.preventDefault();
        if (sending || !photo) return;

        const body = new FormData(form);
        body.set('photo', photo, 'feedback.jpg');
        submit.disabled = input.disabled = add.disabled = remove.disabled = sending = true;
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
                showError(result.message || 'Feedback with the photo could not be sent. Try again or remove the photo to send text only.');
                return;
            }
            removePhoto();
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
            showError('Feedback could not be sent. Try again or remove the photo to send text only.');
        } finally {
            status.textContent = '';
            submit.disabled = input.disabled = add.disabled = remove.disabled = sending = false;
        }
    });

    picker.hidden = false;
})();
