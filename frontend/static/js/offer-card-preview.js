(() => {
    'use strict';
    const frame = document.getElementById('studentCardPreview');
    const template = document.getElementById('studentCardTemplate');
    const form = document.getElementById('packageEditor');
    const { name, category, capacity, originalPrice, offerPrice, discountPercent } = form.elements;
    const upload = form.elements.poster;
    const confirmed = Number(frame.dataset.purchased);
    const deadline = Date.parse(frame.dataset.endsAt);
    const mobile = window.matchMedia('(max-width: 760px)');
    const currency = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 });
    const wholeCurrency = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
    const format = value => Number.isFinite(value) ? (Number.isInteger(value) ? wholeCurrency : currency).format(value) : '—';
    let card;
    let uploadedPoster;
    let savedPoster;

    function render() {
        if (!card) return;
        const title = name.value.trim() || 'Package name';
        const original = originalPrice.valueAsNumber;
        const offer = offerPrice.valueAsNumber;
        const originalCents = Math.round(original * 100);
        const offerCents = Math.round(offer * 100);
        const discount = discountPercent.value === '' && !discountPercent.validity.badInput
            ? originalCents ? 100 - Math.round(offerCents * 100 / originalCents) : 0
            : discountPercent.validity.valid ? discountPercent.valueAsNumber : 0;
        const total = capacity.validity.valid ? capacity.valueAsNumber : 0;
        const remaining = Math.max(0, total - confirmed);
        const progress = card.querySelector('.purchase-progress');
        const badge = card.querySelector('.discount-label');
        const poster = card.querySelector('.poster-button img');
        const button = card.querySelector('.register-button');
        const ended = Date.now() >= deadline;
        card.querySelector('h3').textContent = title;
        card.querySelector('.category-label').textContent = category.value;
        card.querySelector('.price strong').textContent = format(offer);
        card.querySelector('.was-price del').textContent = 'Rs. ' + format(original);
        badge.hidden = !(discount > 0);
        badge.textContent = discount > 0 ? discount + '% OFF' : '';
        poster.alt = title + ' promotional poster';
        const posterUrl = uploadedPoster || savedPoster;
        if (poster.getAttribute('src') !== posterUrl) poster.src = posterUrl;
        card.querySelector('.poster-button').setAttribute('aria-label', 'Register for ' + title);
        card.querySelector('.purchased-count').textContent = confirmed;
        card.querySelector('.purchase-capacity').textContent = ' / ' + (total || '—');
        card.querySelector('.seats-left').textContent = total ? remaining + ' places left' : '— places left';
        card.dataset.low = String(total > 0 && remaining * 4 <= total);
        progress.setAttribute('aria-valuenow', Math.min(confirmed, total));
        progress.setAttribute('aria-valuemax', total);
        progress.setAttribute('aria-label', 'Confirmed purchases for ' + title);
        progress.firstElementChild.style.width = (total ? Math.min(100, Math.floor(confirmed * 100 / total)) : 0) + '%';
        button.disabled = ended || (total > 0 && remaining === 0);
        button.firstElementChild.textContent = ended ? 'Offer ended' : button.disabled ? 'Fully booked' : 'Get this package';
    }

    frame.addEventListener('load', () => {
        card = frame.contentDocument.querySelector('.package-card');
        if (!card) return;
        card.querySelectorAll('button').forEach(button => { button.inert = true; });
        savedPoster = card.querySelector('.poster-button img').getAttribute('src');
        updateLayout();
        render();
        const preview = card.parentElement;
        new ResizeObserver(() => { frame.style.height = preview.offsetHeight + 'px'; }).observe(preview);
    });
    frame.srcdoc = '<!doctype html><html lang="en"><head><meta charset="UTF-8">'
        + '<meta name="viewport" content="width=device-width, initial-scale=1"></head>'
        + '<body class="packages-page">' + template.innerHTML + '</body></html>';

    function updateLayout() {
        if (card) frame.contentDocument.documentElement.classList.toggle('desktop-preview', !mobile.matches);
    }
    mobile.addEventListener('change', updateLayout);
    form.addEventListener('input', render);
    upload.addEventListener('change', () => {
        const file = upload.files[0];
        const invalid = file && (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024);
        const error = document.getElementById('posterError');
        error.textContent = invalid ? 'Choose a JPEG or PNG poster, 5 MB or smaller.' : '';
        upload.setCustomValidity(error.textContent);
        if (uploadedPoster) URL.revokeObjectURL(uploadedPoster);
        uploadedPoster = file && !invalid ? URL.createObjectURL(file) : null;
        render();
    });
})();
