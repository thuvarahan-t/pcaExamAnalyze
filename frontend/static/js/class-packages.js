(() => {
    'use strict';
    const page = document.querySelector('.packages-page');
    if (!page) return;
    const dialog = document.getElementById('registrationDialog');
    const posterDialog = document.getElementById('posterDialog');
    const previewRegister = document.getElementById('previewRegister');
    let previewCard = null;
    const form = document.getElementById('enquiryForm');
    const error = document.getElementById('enquiryError');
    const submit = document.getElementById('prepareEnquiry');
    const cards = [...document.querySelectorAll('.package-card')];
    const mobileView = window.matchMedia('(max-width: 760px)');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const countdownPanel = document.querySelector('.countdown-panel');
    let glowFrame = 0;
    countdownPanel.addEventListener('pointermove', event => {
        if (event.pointerType !== 'mouse' || reducedMotion.matches || glowFrame) return;
        glowFrame = requestAnimationFrame(() => {
            const box = countdownPanel.getBoundingClientRect();
            countdownPanel.style.setProperty('--glow-x', ((event.clientX - box.left) / box.width * 100) + '%');
            countdownPanel.style.setProperty('--glow-y', ((event.clientY - box.top) / box.height * 100) + '%');
            glowFrame = 0;
        });
    });
    countdownPanel.addEventListener('pointerleave', () => {
        if (glowFrame) cancelAnimationFrame(glowFrame);
        glowFrame = 0;
        countdownPanel.style.removeProperty('--glow-x');
        countdownPanel.style.removeProperty('--glow-y');
    });
    countdownPanel.addEventListener('pointerdown', event => {
        if (reducedMotion.matches || !event.target.closest('.countdown>div')) return;
        event.target.closest('.countdown>div').animate([
            { transform: 'scale(1)' }, { transform: 'scale(.94)', offset: .35 }, { transform: 'scale(1)' }
        ], { duration: 260, easing: 'ease-out' });
    });
    function updatePosterInteraction() {
        cards.forEach(card => {
            card.querySelectorAll('[data-preview]').forEach(button => { button.disabled = mobileView.matches; });
        });
        if (mobileView.matches && posterDialog.open) posterDialog.close();
    }
    mobileView.addEventListener('change', updatePosterInteraction);
    updatePosterInteraction();
    let serverBase = Date.parse(page.dataset.serverNow);
    let monotonicBase = performance.now();
    let endsAt = Date.parse(page.dataset.endsAt);
    let selected = null;
    let preparing = false;
    let requestVersion = 0;
    let refreshing = false;
    let catalogRefreshPending = false;
    const now = () => serverBase + performance.now() - monotonicBase;
    const expired = () => now() >= endsAt;
    const available = card => card && card.dataset.unpublished !== 'true' && card.dataset.updated !== 'true' && !expired()
        && Number(card.dataset.purchased) < Number(card.dataset.capacity);
    const stateLabel = card => card.dataset.unpublished === 'true' ? 'No longer available' : card.dataset.updated === 'true' ? 'Offer updated' : expired() ? 'Offer ended'
        : Number(card.dataset.purchased) >= Number(card.dataset.capacity) ? 'Fully booked' : 'Register via WhatsApp';

    function tick() {
        const remaining = Math.max(0, Math.ceil((endsAt - now()) / 1000));
        const values = [Math.floor(remaining / 86400), Math.floor(remaining / 3600) % 24,
            Math.floor(remaining / 60) % 60, remaining % 60];
        ['Days', 'Hours', 'Minutes', 'Seconds'].forEach((part, i) => {
            const digit = document.getElementById('count' + part);
            const next = String(values[i]).padStart(2, '0');
            if (digit.textContent !== next) {
                const initialized = digit.textContent !== '--';
                digit.textContent = next;
                if (initialized && !reducedMotion.matches && !document.hidden) digit.animate([
                    { transform: 'translateY(4px)', opacity: .55 }, { transform: 'translateY(0)', opacity: 1 }
                ], { duration: 280, easing: 'ease-out' });
            }
        });
        document.getElementById('countdownTitle').textContent = expired() ? 'This offer has ended.' : 'Your offer ends in';
        cards.forEach(card => {
            const button = card.querySelector('.register-button');
            button.disabled = !available(card);
            button.firstElementChild.textContent = stateLabel(card);
        });
        if (previewCard && posterDialog.open) {
            previewRegister.disabled = !available(previewCard);
            previewRegister.textContent = stateLabel(previewCard);
        }
        if (selected && dialog.open) {
            submit.disabled = preparing || !available(selected);
            if (!available(selected)) {
                error.textContent = stateLabel(selected) + '. Please check the other available packs.';
            }
        }
    }
    function openRegistration(card) {
        selected = card; requestVersion++;
        preparing = false; error.textContent = '';
        submit.textContent = 'Send message in WhatsApp ↗';
        document.getElementById('selectedPackage').textContent = card.dataset.name;
        document.getElementById('selectedPrice').textContent = card.querySelector('.price').textContent.trim();
        document.getElementById('selectedPoster').src = card.querySelector('.poster-button img').src;
        dialog.showModal(); tick();
        document.getElementById(mobileView.matches ? 'registrationTitle' : 'packageName').focus({ preventScroll: true });
    }
    cards.forEach(card => {
        card.querySelectorAll('[data-register]').forEach(button => button.addEventListener('click', () => openRegistration(card)));
        card.querySelectorAll('[data-preview]').forEach(button => button.addEventListener('click', () => {
            if (mobileView.matches) return;
            previewCard = card;
            const img = document.getElementById('fullPoster'); img.src = card.querySelector('.poster-button img').src;
            img.alt = card.dataset.name + ' full poster';
            document.getElementById('previewPackageName').textContent = card.dataset.name;
            posterDialog.showModal(); tick();
        }));
    });
    previewRegister.addEventListener('click', () => {
        const card = previewCard;
        if (!available(card)) return;
        posterDialog.close();
        openRegistration(card);
    });
    [dialog, posterDialog].forEach(modal => {
        modal.querySelector('[data-close]').addEventListener('click', () => modal.close());
        modal.addEventListener('click', event => {
            const rect = modal.getBoundingClientRect();
            if (event.target === modal && (event.clientX < rect.left || event.clientX > rect.right
                || event.clientY < rect.top || event.clientY > rect.bottom)) modal.close();
        });
    });
    dialog.addEventListener('close', () => {
        selected = null; requestVersion++;
        if (catalogRefreshPending) location.reload();
    });
    posterDialog.addEventListener('close', () => { if (catalogRefreshPending) location.reload(); });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!selected || preparing || !form.reportValidity() || !available(selected)) return;
        const version = ++requestVersion;
        preparing = true; submit.disabled = true; submit.textContent = 'Preparing your message…'; error.textContent = '';
        try {
            const headers = { 'Accept': 'application/json' };
            headers[document.querySelector('meta[name="_csrf_header"]').content] = document.querySelector('meta[name="_csrf"]').content;
            const response = await fetch(selected.dataset.enquiryUrl, { method: 'POST', headers,
                body: new URLSearchParams(new FormData(form)), cache: 'no-store' });
            if (version !== requestVersion) return;
            if (!response.headers.get('content-type')?.includes('application/json'))
                throw new Error('Your session may have expired. Refresh this page and try again.');
            const result = await response.json();
            if (!response.ok) throw new Error(result.error || 'Unable to prepare your message. Please try again.');
            document.getElementById('packageName').value = result.details.name;
            document.getElementById('packageRegistration').value = result.details.registrationId;
            if (!available(selected)) throw new Error(stateLabel(selected) + '. Please check the other available packs.');
            window.location.assign(result.url);
        } catch (e) {
            if (version === requestVersion) error.textContent = e.message || 'Connection interrupted. Please try again.';
        } finally {
            if (version === requestVersion) { preparing = false; submit.textContent = 'Send message in WhatsApp ↗'; tick(); }
        }
    });
    async function refresh() {
        if (refreshing || document.hidden) return;
        refreshing = true;
        try {
            const response = await fetch(page.dataset.statusUrl, { cache: 'no-store', headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(10000) });
            if (!response.ok) throw new Error('Refresh failed');
            const status = await response.json();
            serverBase = Date.parse(status.serverNow); monotonicBase = performance.now(); endsAt = Date.parse(status.endsAt);
            const deadline = document.getElementById('offerDeadline'); deadline.dateTime = status.endsAt;
            deadline.textContent = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true, timeZone: 'Asia/Colombo' }).format(new Date(endsAt));
            const visible = new Map(status.packages.map(pack => [String(pack.id), pack]));
            cards.forEach(card => {
                const pack = visible.get(card.dataset.packageId);
                card.dataset.unpublished = String(!pack);
                if (!pack) return;
                card.dataset.updated = String(card.dataset.name !== pack.name || card.dataset.category !== pack.category
                    || Number(card.dataset.originalPrice) !== Number(pack.originalPrice)
                    || Number(card.dataset.offerPrice) !== Number(pack.offerPrice)
                    || Number(card.dataset.posterVersion) !== pack.posterVersion);
                if (card.dataset.updated === 'true') catalogRefreshPending = true;
                card.dataset.purchased = pack.purchased; card.dataset.capacity = pack.capacity;
                card.querySelector('.purchased-count').textContent = pack.purchased;
                card.querySelector('.purchase-capacity').textContent = ' / ' + pack.capacity;
                card.querySelector('.seats-left').textContent = Math.max(0, pack.capacity - pack.purchased) + ' places left';
                const progress = card.querySelector('.purchase-progress');
                progress.setAttribute('aria-valuenow', pack.purchased); progress.setAttribute('aria-valuemax', pack.capacity);
                progress.firstElementChild.style.width = Math.min(100, pack.purchased * 100 / pack.capacity) + '%';
            });
            const changed = status.packages.length !== cards.length || cards.some(card => !visible.has(card.dataset.packageId));
            catalogRefreshPending = catalogRefreshPending || changed;
            document.getElementById('refreshStatus').textContent = catalogRefreshPending
                ? 'Offers have changed. Close the popup to see the latest details.' : '';
            if (catalogRefreshPending && !dialog.open && !posterDialog.open) { location.reload(); return; }
            tick();
        } catch (_) {
            document.getElementById('refreshStatus').textContent = 'Live updates are temporarily unavailable. Showing the last confirmed counts; retrying automatically.';
        } finally { refreshing = false; }
    }
    tick(); setInterval(tick, 1000); setInterval(refresh, 15000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
    window.addEventListener('pageshow', event => { if (event.persisted) refresh(); });
})();
