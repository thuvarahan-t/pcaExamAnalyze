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
    let live = [...document.querySelectorAll('#buyFeedSeed li')].map(li => ({
        id: Number(li.dataset.id), name: li.dataset.name,
        school: li.dataset.school || '', district: li.dataset.district || '', packageName: li.dataset.pack, confirmedAt: li.dataset.at }));
    let list = [...live];
    let hasMoreRecent = feed.dataset.hasMore === 'true';
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
    function feedRow(entry) {
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
    }
    function render() {
        feed.hidden = !list.length;
        viewport.replaceChildren(...list.map(feedRow));
    }
    function recentBottom(rowHeight) {
        return Math.max(0, live.length * rowHeight - viewport.clientHeight);
    }
    function readingHistory() {
        const rowHeight = viewport.firstElementChild?.offsetHeight || 0;
        return touching || Date.now() < resumeAt || viewport.scrollTop > recentBottom(rowHeight) + 1;
    }

    const feedHistory = purchasePager({
        rows: viewport,
        status: document.getElementById('buyFeedStatus'),
        retry: document.getElementById('buyFeedRetry'),
        url: feed.dataset.url,
        canAutoLoad: () => !feed.hidden,
        append(items) {
            list.push(...items);
            viewport.append(...items.map(feedRow));
        }
    });
    feedHistory.reset(list.at(-1), hasMoreRecent);

    window.PcaOfferFeed = {
        // The status endpoint returns the recent purchases in newest-first order.
        update(recent, serverNow, hasMore) {
            if (serverNow) clockOffset = Date.parse(serverNow) - Date.now();
            const changed = recent.length !== live.length || recent.some((entry, index) => {
                const previous = live[index];
                return Object.keys(entry).some(key => entry[key] !== previous[key]);
            });
            const complete = hasMore === false;
            const previousTail = list.at(-1)?.id;
            if (changed || (complete && list.length > recent.length)) {
                const known = new Set(live.map(entry => entry.id));
                const hasNewPurchase = recent.some(entry => !known.has(entry.id));
                const top = viewport.scrollTop;
                const rowHeight = viewport.firstElementChild?.offsetHeight || 1;
                const anchor = list[Math.floor(top / rowHeight)]?.id;
                const reviewing = top > 0 && readingHistory();
                const boundary = list.findIndex(entry => entry.id === recent.at(-1)?.id);
                // Keep loaded history only when the response is partial and overlaps it.
                const older = complete || boundary < 0 ? [] : list.slice(boundary + 1);
                list = [...recent, ...older];
                live = recent;
                render();
                const anchorIndex = list.findIndex(entry => entry.id === anchor);
                if (hasNewPurchase && !reviewing) {
                    viewport.scrollTop = 0;
                    pauseAfterInput();
                } else {
                    viewport.scrollTop = anchorIndex < 0 ? top : anchorIndex * rowHeight + top % rowHeight;
                }
            } else {
                viewport.querySelectorAll('time').forEach(time => { time.textContent = ago(time.dateTime); });
            }
            if (previousTail !== list.at(-1)?.id || (list.length === live.length && hasMore !== hasMoreRecent)) {
                feedHistory.reset(list.at(-1), hasMore);
            }
            hasMoreRecent = hasMore;
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

    function purchasePager({ rows, status, retry, url, canAutoLoad, append, doneMessage = '' }) {
        const PAGE_SIZE = 20;
        const LOAD_AHEAD_PX = 120;
        let cursor, hasMore, loading, version = 0;

        function setStatus(message, isError = false) {
            status.textContent = message;
            status.classList.toggle('error', isError);
        }
        function loadNearEnd() {
            const remaining = rows.scrollHeight - rows.clientHeight - rows.scrollTop;
            if (hasMore && !loading && retry.hidden && canAutoLoad() && remaining <= LOAD_AHEAD_PX) {
                return loadPage();
            }
        }
        async function loadPage() {
            if (!hasMore || loading) return;
            loading = true;
            const requestVersion = version;
            retry.hidden = true;
            rows.setAttribute('aria-busy', 'true');
            setStatus('Loading purchases…');
            try {
                const params = new URLSearchParams({ size: String(PAGE_SIZE) });
                if (cursor) {
                    params.set('beforeAt', cursor.confirmedAt);
                    params.set('beforeId', String(cursor.id));
                }
                const response = await fetch(url + '?' + params, { cache: 'no-store', headers: { Accept: 'application/json' } });
                if (!response.ok) throw new Error('bad status');
                const data = await response.json();
                if (requestVersion !== version) return;
                append(data.items);
                if (data.items.length) cursor = data.items.at(-1);
                hasMore = data.items.length > 0 && data.hasMore;
                if (!rows.children.length) setStatus('No purchases yet.');
                else setStatus(hasMore ? '' : doneMessage);
            } catch (_) {
                if (requestVersion !== version) return;
                setStatus('Could not load purchases. Check your connection and try again.', true);
                retry.hidden = false;
            } finally {
                if (requestVersion === version) {
                    loading = false;
                    rows.removeAttribute('aria-busy');
                }
            }
            // A tall viewport may still be near the end after appending a page.
            return loadNearEnd();
        }
        function reset(after = null, more = false) {
            version++;
            cursor = after;
            hasMore = more;
            loading = false;
            retry.hidden = true;
            rows.removeAttribute('aria-busy');
            setStatus('');
        }

        rows.addEventListener('scroll', loadNearEnd, { passive: true });
        window.addEventListener('resize', loadNearEnd);
        retry.addEventListener('click', loadPage);
        return { reset, loadPage };
    }

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
    const dialog = document.getElementById('purchasesDialog');
    if (dialog) {
        const rows = document.getElementById('purchaseList');
        const history = purchasePager({
            rows,
            status: document.getElementById('purchaseListStatus'),
            retry: document.getElementById('purchaseListRetry'),
            url: dialog.dataset.url,
            canAutoLoad: () => dialog.open,
            append: items => rows.append(...items.map(row)),
            doneMessage: "That's everyone so far."
        });
        viewAll.addEventListener('click', () => {
            if (dialog.open) return;
            rows.replaceChildren();
            history.reset(null, true);
            dialog.showModal();
            rows.scrollTop = 0;
            document.getElementById('purchasesTitle').focus({ preventScroll: true });
            return history.loadPage();
        });
        dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
        dialog.addEventListener('click', event => {
            if (event.target !== dialog) return;
            const box = dialog.getBoundingClientRect();
            if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) dialog.close();
        });
        dialog.addEventListener('close', () => {
            if (dialog.open) return;
            history.reset();
            pauseAfterInput();
        });
    }

    render();
    setInterval(() => {
        if (reducedMotion.matches || document.hidden || dialog?.open || touching || Date.now() < resumeAt) return;
        const rowHeight = viewport.firstElementChild?.offsetHeight || 0;
        const bottom = recentBottom(rowHeight);
        if (bottom <= 0 || viewport.scrollTop > bottom + 1) return;
        const nextTop = (Math.floor((viewport.scrollTop + 1) / rowHeight) + 1) * rowHeight;
        viewport.scrollTo({ top: viewport.scrollTop >= bottom - 1 ? 0 : Math.min(nextTop, bottom), behavior: 'smooth' });
    }, STEP_MS);
})();
