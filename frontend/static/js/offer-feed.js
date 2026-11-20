(() => {
    'use strict';
    const feed = document.getElementById('buyFeed');
    if (!feed) return;
    const viewport = document.getElementById('buyFeedViewport');
    const template = document.getElementById('buyFeedTemplate');
    const viewAll = document.getElementById('buyFeedMore');
    const page = document.querySelector('.packages-page');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const STEP_MS = 1000;
    const INPUT_PAUSE_MS = 4500;

    let clockOffset = page ? (Date.parse(page.dataset.serverNow) || Date.now()) - Date.now() : 0;
    let list = [...document.querySelectorAll('#buyFeedSeed li')].map(li => ({
        id: Number(li.dataset.id), name: li.dataset.name,
        school: li.dataset.school || '', district: li.dataset.district || '', packageName: li.dataset.pack, confirmedAt: li.dataset.at }));
    let resumeAt = 0;
    let touching = false;

    function ago(iso) {
        const seconds = Math.max(0, Math.round((Date.now() + clockOffset - Date.parse(iso)) / 1000));
        if (seconds < 60) return 'just now';
        const minutes = Math.round(seconds / 60);
        if (minutes < 60) return minutes + (minutes === 1 ? ' min ago' : ' mins ago');
        const hours = Math.round(minutes / 60);
        if (hours < 24) return hours + (hours === 1 ? ' hour ago' : ' hours ago');
        const days = Math.round(hours / 24);
        return days + (days === 1 ? ' day ago' : ' days ago');
    }
    function hue(text) {
        let h = 0;
        for (const ch of text) h = (h * 31 + ch.codePointAt(0)) % 360;
        return h;
    }
    function render() {
        feed.hidden = !list.length;
        viewport.replaceChildren(...list.map(entry => {
            const item = template.content.firstElementChild.cloneNode(true);
            const avatar = item.querySelector('.buy-avatar');
            const place = [entry.school, entry.district].filter(Boolean).join(', ');
            item.querySelector('strong').textContent = entry.name;
            item.querySelector('.buy-from').textContent = place ? ' from ' + place : '';
            item.querySelector('b').textContent = entry.packageName;
            const time = item.querySelector('time');
            time.dateTime = entry.confirmedAt;
            time.textContent = ago(entry.confirmedAt);
            avatar.textContent = (entry.name.trim()[0] || '?').toUpperCase();
            avatar.style.setProperty('--h', hue(entry.name));
            return item;
        }));
    }

    window.PcaOfferFeed = {
        // The status endpoint returns the recent purchases in newest-first order.
        update(recent, serverNow) {
            if (serverNow) clockOffset = Date.parse(serverNow) - Date.now();
            const known = new Set(list.map(entry => entry.id));
            const hasNewPurchase = recent.some(entry => !known.has(entry.id));
            const changed = recent.length !== list.length || recent.some((entry, index) => {
                const previous = list[index];
                return Object.keys(entry).some(key => entry[key] !== previous[key]);
            });
            list = recent;
            if (changed) {
                const top = viewport.scrollTop;
                render();
                viewport.scrollTop = hasNewPurchase ? 0 : top;
                if (hasNewPurchase) pauseAfterInput();
            } else {
                viewport.querySelectorAll('time').forEach(time => { time.textContent = ago(time.dateTime); });
            }
        }
    };

    function pauseAfterInput() {
        resumeAt = Date.now() + INPUT_PAUSE_MS;
    }

    viewport.addEventListener('wheel', pauseAfterInput, { passive: true });
    viewport.addEventListener('keydown', pauseAfterInput);
    viewport.addEventListener('pointerdown', () => { touching = true; pauseAfterInput(); });
    for (const event of ['pointerup', 'pointercancel']) {
        window.addEventListener(event, () => {
            if (touching) pauseAfterInput();
            touching = false;
        });
    }
    viewport.addEventListener('touchmove', pauseAfterInput, { passive: true });

    // ---- Popup: full scrollable list of purchases, 20 at a time with "Load more". ----
    const dialog = document.getElementById('purchasesDialog');
    const rows = document.getElementById('purchaseList');
    const statusEl = document.getElementById('purchaseListStatus');
    const moreBtn = document.getElementById('purchaseLoadMore');
    const PAGE_SIZE = 20;
    let cursor = null;          // last loaded item: { confirmedAt, id }
    let loading = false;
    let loadVersion = 0;        // ignores responses that arrive after the popup was closed/reopened

    function row(entry) {
        const li = document.createElement('li');
        li.className = 'purchase-row is-new';
        const badge = document.createElement('span');
        badge.className = 'buy-avatar'; badge.setAttribute('aria-hidden', 'true');
        badge.textContent = (entry.name.trim()[0] || '?').toUpperCase();
        badge.style.setProperty('--h', hue(entry.name));
        const body = document.createElement('div'); body.className = 'purchase-row-body';
        const text = document.createElement('p');
        const who = document.createElement('strong'); who.textContent = entry.name;
        text.append(who);
        const place = [entry.school, entry.district].filter(Boolean).join(', ');
        if (place) {
            const from = document.createElement('span'); from.className = 'buy-from'; from.textContent = ' from ' + place;
            text.append(from);
        }
        const pack = document.createElement('b'); pack.textContent = entry.packageName;
        text.append(' purchased ', pack);
        const when = document.createElement('time'); when.dateTime = entry.confirmedAt; when.textContent = ago(entry.confirmedAt);
        body.append(text, when);
        li.append(badge, body);
        return li;
    }
    function setStatus(message, isError) {
        statusEl.textContent = message;
        statusEl.classList.toggle('error', Boolean(isError));
    }
    async function loadPage() {
        if (loading) return;
        loading = true;
        const version = ++loadVersion;
        moreBtn.disabled = true; moreBtn.textContent = 'Loading…';
        if (!cursor) { rows.replaceChildren(); for (let i = 0; i < 4; i++) { const s = document.createElement('li'); s.className = 'purchase-skeleton'; rows.append(s); } }
        setStatus('');
        try {
            const params = new URLSearchParams({ size: String(PAGE_SIZE) });
            if (cursor) { params.set('beforeAt', cursor.confirmedAt); params.set('beforeId', String(cursor.id)); }
            const response = await fetch(dialog.dataset.url + '?' + params, { cache: 'no-store', headers: { Accept: 'application/json' } });
            if (!response.ok) throw new Error('bad status');
            const data = await response.json();
            if (version !== loadVersion) return;
            if (!cursor) rows.replaceChildren();
            data.items.forEach(entry => rows.append(row(entry)));
            if (data.items.length) cursor = data.items[data.items.length - 1];
            moreBtn.hidden = !data.hasMore;
            moreBtn.textContent = 'Load more';
            if (!rows.children.length) setStatus('No purchases yet.');
            else if (!data.hasMore) setStatus("That's everyone so far.");
        } catch (_) {
            if (version !== loadVersion) return;
            if (!cursor) rows.replaceChildren();
            setStatus('Could not load purchases. Check your connection and try again.', true);
            moreBtn.hidden = false; moreBtn.textContent = 'Try again';
        } finally {
            if (version === loadVersion) { loading = false; moreBtn.disabled = false; }
        }
    }
    function openList() {
        if (!dialog || dialog.open) return;
        cursor = null; loading = false; moreBtn.hidden = true;
        dialog.showModal();
        rows.scrollTop = 0;
        document.getElementById('purchasesTitle').focus({ preventScroll: true });
        loadPage();
    }
    if (dialog) {
        viewAll.addEventListener('click', openList);
        moreBtn.addEventListener('click', loadPage);
        dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
        dialog.addEventListener('click', event => {
            if (event.target !== dialog) return;
            const box = dialog.getBoundingClientRect();
            if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) dialog.close();
        });
        dialog.addEventListener('close', () => { loadVersion++; loading = false; pauseAfterInput(); });
    }

    render();
    setInterval(() => {
        if (reducedMotion.matches || document.hidden || dialog?.open || touching || Date.now() < resumeAt) return;
        const bottom = viewport.scrollHeight - viewport.clientHeight;
        if (bottom <= 0) return;
        const rowHeight = viewport.firstElementChild.offsetHeight;
        const nextTop = (Math.floor((viewport.scrollTop + 1) / rowHeight) + 1) * rowHeight;
        viewport.scrollTo({ top: viewport.scrollTop >= bottom - 1 ? 0 : Math.min(nextTop, bottom), behavior: 'smooth' });
    }, STEP_MS);
})();
