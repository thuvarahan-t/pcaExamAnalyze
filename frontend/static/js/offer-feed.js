// Recent-purchase notification bar on the offer page.
// Shows one confirmed purchase at a time, newest -> oldest, then starts again.
// A purchase the admin adds is shown next, as soon as the status poll brings it in.
(() => {
    'use strict';
    const feed = document.getElementById('buyFeed');
    if (!feed) return;
    const item = document.getElementById('buyFeedItem');
    const avatar = document.getElementById('buyFeedAvatar');
    const nameEl = document.getElementById('buyFeedName');
    const packEl = document.getElementById('buyFeedPack');
    const fromEl = document.getElementById('buyFeedFrom');
    const timeEl = document.getElementById('buyFeedTime');
    const page = document.querySelector('.packages-page');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const STEP_MS = 4500;

    let clockOffset = page ? (Date.parse(page.dataset.serverNow) || Date.now()) - Date.now() : 0;
    let list = [...document.querySelectorAll('#buyFeedSeed li')].map(li => ({
        id: Number(li.dataset.id), name: li.dataset.name,
        school: li.dataset.school || '', district: li.dataset.district || '', packageName: li.dataset.pack, confirmedAt: li.dataset.at }));
    let index = -1;
    let shownId = null;
    let timer = 0;
    let paused = false;

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
    function render(entry, animate) {
        const write = () => {
            nameEl.textContent = entry.name;
            packEl.textContent = entry.packageName;
            // "from Jaffna Hindu College, Jaffna" - either part may be missing.
            const place = [entry.school, entry.district].filter(Boolean).join(', ');
            if (fromEl) fromEl.textContent = place ? ' from ' + place : '';
            timeEl.textContent = ago(entry.confirmedAt);
            timeEl.dateTime = entry.confirmedAt;
            avatar.textContent = (entry.name.trim()[0] || '?').toUpperCase();
            avatar.style.setProperty('--h', hue(entry.name));
            shownId = entry.id;
        };
        if (!animate || reducedMotion.matches || !item.animate) { write(); return; }
        item.animate([{ opacity: 1, transform: 'translateY(0)' }, { opacity: 0, transform: 'translateY(-10px)' }],
            { duration: 220, easing: 'ease-in', fill: 'forwards' }).onfinish = () => {
            write();
            item.animate([{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'translateY(0)' }],
                { duration: 320, easing: 'cubic-bezier(.2,.8,.2,1)', fill: 'forwards' });
        };
    }
    function next(animate = true) {
        if (!list.length) { feed.hidden = true; shownId = null; return; }
        feed.hidden = false;
        index = (index + 1) % list.length;
        render(list[index], animate && shownId !== null);
    }
    function schedule() {
        clearInterval(timer);
        timer = setInterval(() => { if (!paused && !document.hidden) next(); }, STEP_MS);
    }

    window.PcaOfferFeed = {
        // Called by class-packages.js with the latest list from /exam/packages/status (newest first).
        update(recent, serverNow) {
            if (serverNow) clockOffset = Date.parse(serverNow) - Date.now();
            const known = new Set(list.map(entry => entry.id));
            const fresh = recent.filter(entry => !known.has(entry.id));
            list = recent.slice();
            if (fresh.length) {
                // Jump to the newest purchase straight away and continue down the list from there.
                index = -1; next(); schedule(); return;
            }
            const at = list.findIndex(entry => entry.id === shownId);
            if (at >= 0) { index = at; timeEl.textContent = ago(list[at].confirmedAt); }
            else { index = -1; next(); }
        }
    };

    feed.addEventListener('pointerenter', event => { if (event.pointerType === 'mouse') paused = true; });
    feed.addEventListener('pointerleave', () => { paused = false; });

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
        paused = true;
        dialog.showModal();
        rows.scrollTop = 0;
        document.getElementById('purchasesTitle').focus({ preventScroll: true });
        loadPage();
    }
    if (dialog) {
        feed.addEventListener('click', openList);
        feed.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openList(); }
        });
        moreBtn.addEventListener('click', loadPage);
        dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
        dialog.addEventListener('click', event => {
            if (event.target !== dialog) return;
            const box = dialog.getBoundingClientRect();
            if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) dialog.close();
        });
        dialog.addEventListener('close', () => { loadVersion++; loading = false; paused = false; });
    }

    next(false);
    schedule();
})();
