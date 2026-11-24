import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const studentScript = await readFile(new URL('../static/js/class-packages.js', import.meta.url), 'utf8');
const adminScript = await readFile(new URL('../static/js/package-admin.js', import.meta.url), 'utf8');
const promoScript = await readFile(new URL('../static/js/offer-promo-video.js', import.meta.url), 'utf8');
const feedScript = await readFile(new URL('../static/js/offer-feed.js', import.meta.url), 'utf8');

test('promo only autoplays muted once when the player is ready and visible', () => {
    const calls = [];
    const frame = { parentElement: {} };
    let reportVisibility, onReady;
    const document = {
        querySelector: selector => selector === 'iframe[data-promo-video]' ? frame : null,
        addEventListener() {},
        removeEventListener() {}
    };
    const window = {
        matchMedia: () => ({ matches: false }),
        YT: {
            Player: class {
                constructor(iframe, { events }) {
                    assert.equal(iframe, frame);
                    onReady = events.onReady;
                }
            },
            PlayerState: { UNSTARTED: -1, CUED: 5 }
        }
    };
    class IntersectionObserver {
        constructor(callback) { reportVisibility = callback; }
        observe(element) { assert.equal(element, frame.parentElement); }
        disconnect() { calls.push('disconnect'); }
    }
    vm.runInNewContext(promoScript, { document, window, IntersectionObserver });
    const enter = { isIntersecting: true, intersectionRatio: 1 };
    reportVisibility([enter]);
    reportVisibility([{ isIntersecting: false, intersectionRatio: 0 }]);
    onReady({ target: {
        getPlayerState: () => -1,
        mute: () => calls.push('mute'),
        playVideo: () => calls.push('play')
    } });
    assert.deepEqual(calls, []);
    reportVisibility([enter]);
    assert.deepEqual(calls, ['disconnect', 'mute', 'play']);
});

// Small DOM adapter for exercising actual event handlers without a browser or WhatsApp.
class Element {
    constructor(textContent = '') {
        this.textContent = textContent; this.value = ''; this.dataset = {}; this.style = {};
        this.listeners = new Map(); this.selectors = new Map(); this.lists = new Map(); this.attributes = new Map();
        this.children = []; this.open = false; this.hidden = false; this.disabled = false;
    }
    addEventListener(type, callback) {
        if (!this.listeners.has(type)) this.listeners.set(type, []);
        this.listeners.get(type).push(callback);
    }
    async fire(type, properties = {}) {
        const event = { target: this, preventDefault() {}, ...properties };
        await Promise.all((this.listeners.get(type) || []).map(callback => callback(event)));
    }
    querySelector(selector) { return this.selectors.get(selector) ?? null; }
    querySelectorAll(selector) { return this.lists.get(selector) ?? []; }
    setAttribute(key, value) { this.attributes.set(key, String(value)); }
    removeAttribute(key) { this.attributes.delete(key); }
    get firstElementChild() { return this.children[0]; }
    showModal() { this.open = true; }
    close() { this.open = false; void this.fire('close'); }
    focus() { this.focused = true; }
    reportValidity() { return true; }
    animate() {}
}

function studentPage({ mobile = false, purchased = 0 } = {}) {
    const document = new Element();
    const ids = Object.fromEntries(['registrationDialog', 'posterDialog', 'previewRegister', 'enquiryForm',
        'enquiryError', 'prepareEnquiry', 'countDays', 'countHours', 'countMinutes', 'countSeconds',
        'countdownTitle', 'selectedPackage', 'selectedPrice', 'selectedPoster', 'packageName',
        'packagePhone', 'registrationTitle', 'fullPoster', 'previewPackageName', 'offerDeadline', 'refreshStatus']
        .map(id => [id, new Element()]));
    for (const id of ['countDays', 'countHours', 'countMinutes', 'countSeconds']) ids[id].textContent = '--';
    for (const id of ['registrationDialog', 'posterDialog']) ids[id].selectors.set('[data-close]', new Element());
    ids.packageName.value = 'Thuvrahan'; ids.packagePhone.value = '4 104 6208';
    const page = new Element();
    page.dataset = { serverNow: '2026-11-12T00:00:00Z', endsAt: '2026-11-13T00:00:00Z', statusUrl: '/exam/packages/status' };
    const card = new Element();
    card.dataset = { packageId: '1', name: 'Theory Study Pack', category: 'Theory', originalPrice: '32000.00',
        offerPrice: '8000.00', posterVersion: '1', purchased: String(purchased), capacity: '111', enquiryUrl: '/exam/packages/1/enquiry' };
    const imageButton = new Element(), registerButton = new Element(), previewButton = new Element();
    registerButton.children = [new Element()];
    card.lists.set('[data-register]', [imageButton, registerButton]); card.lists.set('[data-preview]', [previewButton]);
    card.selectors.set('.register-button', registerButton);
    card.selectors.set('.poster-button img', { src: '/exam/packages/1/poster?v=1' });
    card.selectors.set('.price', new Element('Rs. 8,000'));
    for (const selector of ['.purchased-count', '.purchase-capacity', '.seats-left']) card.selectors.set(selector, new Element());
    const progress = new Element(); progress.children = [new Element()]; card.selectors.set('.purchase-progress', progress);
    document.selectors.set('.packages-page', page); document.selectors.set('.countdown-panel', new Element());
    document.selectors.set('meta[name="_csrf_header"]', { content: 'X-CSRF-TOKEN' });
    document.selectors.set('meta[name="_csrf"]', { content: 'test-csrf' });
    document.lists.set('.package-card', [card]); document.getElementById = id => ids[id];
    const window = new Element();
    window.matchMedia = query => Object.assign(new Element(), { matches: query.includes('max-width') ? mobile : true });
    const location = { reloads: 0, reload() { this.reloads++; }, assign(url) { this.assigned = url; } };
    window.location = location;
    let elapsed = 0;
    const intervals = [], requests = [];
    const catalog = { id: 1, name: 'Theory Study Pack', category: 'Theory', originalPrice: 32000,
        offerPrice: 8000, posterVersion: 1, purchased, capacity: 111 };
    const response = body => ({ ok: true, headers: { get: () => 'application/json' }, json: async () => body });
    let handleFetch = async url => response(url.endsWith('/status')
        ? { serverNow: page.dataset.serverNow, endsAt: page.dataset.endsAt, packages: [catalog] }
        : { url: 'https://wa.me/94779179035?text=prepared' });
    const context = vm.createContext({ document, window, location, URLSearchParams, Intl, Date, Number, Map, String,
        crypto: { getRandomValues: bytes => bytes.fill(1) }, performance: { now: () => elapsed }, AbortSignal: { timeout() {} },
        FormData: class { *[Symbol.iterator]() { yield ['firstName', ids.packageName.value]; yield ['lastName', 'Kumar']; yield ['phoneSuffix', ids.packagePhone.value]; } },
        fetch: async (url, options) => { requests.push({ url, options }); return handleFetch(url, options); },
        setInterval: callback => intervals.push(callback), requestAnimationFrame() {}, cancelAnimationFrame() {} });
    vm.runInContext(studentScript, context);
    return { ids, card, imageButton, registerButton, previewButton, intervals, requests, catalog, location,
        advance(ms) { elapsed += ms; intervals[0](); }, setFetch(callback) { handleFetch = callback; } };
}

test('mobile poster click opens registration directly; desktop retains separate poster preview', async () => {
    const mobile = studentPage({ mobile: true });
    assert.equal(mobile.registerButton.firstElementChild.textContent, 'Get this package');
    assert.equal(mobile.previewButton.disabled, true);
    await mobile.imageButton.fire('click');
    assert.equal(mobile.ids.registrationDialog.open, true);
    assert.equal(mobile.ids.registrationTitle.focused, true);
    assert.equal(mobile.ids.posterDialog.open, false);
    const desktop = studentPage();
    await desktop.previewButton.fire('click');
    assert.equal(desktop.ids.posterDialog.open, true);
    await desktop.ids.previewRegister.fire('click');
    assert.equal(desktop.ids.posterDialog.open, false);
    assert.equal(desktop.ids.registrationDialog.open, true);
});

test('one submit validates and prepares the WhatsApp link with CSRF and student details', async () => {
    const page = studentPage(); await page.imageButton.fire('click');
    await page.ids.enquiryForm.fire('submit');
    assert.equal(page.requests.length, 1);
    assert.equal(page.requests[0].options.headers['X-CSRF-TOKEN'], 'test-csrf');
    assert.equal(page.requests[0].options.body.get('phoneNumber'), '0741046208');
    assert.equal(page.requests[0].options.body.get('firstName'), 'Thuvrahan');
    assert.equal(page.requests[0].options.body.get('lastName'), 'Kumar');
    assert.equal(page.requests[0].options.body.get('requestKey'), '01010101-0101-0101-0101-010101010101');
    assert.equal(page.location.assigned, 'https://wa.me/94779179035?text=prepared');
});

test('full packages and an expired countdown never prepare a WhatsApp enquiry', async () => {
    const full = studentPage({ purchased: 111 }); await full.imageButton.fire('click');
    assert.equal(full.ids.prepareEnquiry.disabled, true); await full.ids.enquiryForm.fire('submit');
    assert.equal(full.requests.length, 0);
    const expired = studentPage(); expired.advance(86400001); await expired.imageButton.fire('click');
    assert.equal(expired.ids.countdownTitle.textContent, 'This offer has ended.');
    await expired.ids.enquiryForm.fire('submit'); assert.equal(expired.requests.length, 0);
});

test('live purchase counts update without reloading the page', async () => {
    const page = studentPage(); page.catalog.purchased = 7; await page.intervals[1]();
    assert.equal(page.card.querySelector('.purchased-count').textContent, 7);
    assert.equal(page.card.querySelector('.seats-left').textContent, '104 places left');
    assert.equal(page.location.reloads, 0);
});

test('admin price edits disable stale enquiries and reload only after the dialog closes', async () => {
    const page = studentPage(); await page.imageButton.fire('click');
    page.catalog.offerPrice = 8500; await page.intervals[1]();
    assert.equal(page.ids.prepareEnquiry.disabled, true);
    assert.equal(page.location.reloads, 0);
    await page.ids.enquiryForm.fire('submit');
    assert.equal(page.requests.filter(request => request.url.endsWith('/enquiry')).length, 0);
    page.ids.registrationDialog.close(); assert.equal(page.location.reloads, 1);
});

test('closing the dialog while preparing an enquiry prevents delayed WhatsApp navigation', async () => {
    const page = studentPage(); await page.imageButton.fire('click'); let complete;
    page.setFetch(() => new Promise(resolve => { complete = resolve; }));
    const submission = page.ids.enquiryForm.fire('submit'); page.ids.registrationDialog.close();
    complete({ ok: true, headers: { get: () => 'application/json' }, json: async () => ({
        url: 'https://wa.me/94779179035' }) });
    await submission; assert.equal(page.location.assigned, undefined);
});

test('confirmed students filter by either name or registration ID without changing purchases', async () => {
    const document = new Element(), filter = new Element(), status = new Element();
    const rows = [['0712345678', 'Thuvrahan Kumar', 'REG-101', 'PCA-201'],
        ['0798765432', 'Arun Student', 'REG-102', 'PCA-202']]
        .map(cells => Object.assign(new Element(), { cells: cells.map(text => new Element(text)) }));
    document.getElementById = id => ({ purchaseFilter: filter, purchaseFilterStatus: status })[id] ?? null;
    document.lists.set('[data-purchase-row]', rows);
    vm.runInNewContext(adminScript, { document });
    filter.value = 'THUV'; await filter.fire('input'); assert.deepEqual(rows.map(row => row.hidden), [false, true]);
    filter.value = 'REG-102'; await filter.fire('input'); assert.deepEqual(rows.map(row => row.hidden), [true, false]);
    filter.value = '0712345678'; await filter.fire('input'); assert.deepEqual(rows.map(row => row.hidden), [false, true]);
    filter.value = 'PCA-202'; await filter.fire('input'); assert.deepEqual(rows.map(row => row.hidden), [true, false]);
    filter.value = ''; await filter.fire('input'); assert.deepEqual(rows.map(row => row.hidden), [false, false]);
    assert.equal(rows.length, 2);
});

test('student lists page independently in groups of ten and search across all pages', async () => {
    const document = new Element();
    const ids = {};
    const lists = {};
    for (const [name, prefix, selector] of [
        ['purchases', 'purchase', '[data-purchase-row]'],
        ['interested', 'interest', '[data-interest-row]']
    ]) {
        ids[prefix + 'Filter'] = new Element();
        ids[prefix + 'FilterStatus'] = new Element();
        const rows = Array.from({ length: 23 }, (_, index) => Object.assign(new Element(), {
            cells: [new Element('REG-' + index), new Element('Student ' + index)]
        }));
        const pagination = new Element(), previous = new Element(), next = new Element(), status = new Element();
        pagination.selectors.set('[data-page-previous]', previous);
        pagination.selectors.set('[data-page-next]', next);
        pagination.selectors.set('[data-page-status]', status);
        document.selectors.set('[data-student-pagination="' + name + '"]', pagination);
        document.lists.set(selector, rows);
        lists[name] = { rows, previous, next, status, pagination };
    }
    document.getElementById = id => ids[id] ?? null;
    vm.runInNewContext(adminScript, { document });
    const visible = list => list.rows.filter(row => !row.hidden);
    assert.equal(visible(lists.purchases).length, 10);
    assert.equal(visible(lists.interested).length, 10);
    assert.equal(lists.purchases.previous.disabled, true);
    await lists.purchases.next.fire('click');
    assert.equal(visible(lists.purchases)[0].cells[0].textContent, 'REG-10');
    assert.equal(lists.interested.status.textContent, 'Page 1 of 3');
    await lists.purchases.next.fire('click');
    assert.equal(visible(lists.purchases).length, 3);
    assert.equal(lists.purchases.next.disabled, true);
    ids.purchaseFilter.value = 'REG-22'; await ids.purchaseFilter.fire('input');
    assert.equal(visible(lists.purchases).length, 1);
    assert.equal(lists.purchases.status.textContent, 'Page 1 of 1');
    ids.purchaseFilter.value = 'missing'; await ids.purchaseFilter.fire('input');
    assert.equal(visible(lists.purchases).length, 0);
    assert.equal(lists.purchases.pagination.hidden, true);
    ids.purchaseFilter.value = ''; await ids.purchaseFilter.fire('input');
    assert.equal(visible(lists.purchases).length, 10);
    await lists.interested.next.fire('click');
    assert.equal(lists.interested.status.textContent, 'Page 2 of 3');
    await lists.interested.previous.fire('click');
    assert.equal(lists.interested.status.textContent, 'Page 1 of 3');
});

const PURCHASE_TIME = '2026-11-12T10:00:00Z';
const SERVER_TIME = '2026-11-12T10:30:00Z';
const purchase = (id, name = 'Buyer ' + id) => ({ id, name, school: '', district: '',
    packageName: 'Theory Study Pack', confirmedAt: PURCHASE_TIME });

function purchasePage(seed, { hasMore = false, fetch } = {}) {
    const feed = new Element();
    const url = '/exam/packages/purchases';
    feed.dataset = { url, hasMore: String(hasMore) };
    const append = function (...children) { this.children.push(...children); };
    function scrollArea() {
        const element = Object.assign(new Element(), {
            append, clientHeight: 300, scrollTop: 0,
            replaceChildren(...children) { this.children = children; },
            scrollTo({ top }) { this.scrollTop = top; },
            querySelectorAll() { return this.children.map(row => row.querySelector('time')); }
        });
        Object.defineProperty(element, 'scrollHeight', { get: () => element.children.length * 50 });
        return element;
    }
    const viewport = scrollArea(), rows = scrollArea();
    const template = { content: { firstElementChild: { cloneNode() {
        const row = Object.assign(new Element(), { offsetHeight: 50 });
        for (const selector of ['strong', '.buy-from', 'b', 'time', '.buy-avatar']) {
            row.selectors.set(selector, new Element());
        }
        row.querySelector('.buy-avatar').style.setProperty = () => {};
        return row;
    } } } };
    const seedElements = seed.map(entry => Object.assign(new Element(), {
        dataset: { id: String(entry.id), name: entry.name, pack: entry.packageName, at: entry.confirmedAt }
    }));
    const document = new Element();
    document.createElement = () => Object.assign(new Element(), { append, style: { setProperty() {} } });
    const status = () => Object.assign(new Element(), { classList: { toggle() {} } });
    const dialog = new Element(), retry = new Element(), dialogStatus = status();
    const viewAll = new Element();
    dialog.dataset.url = url;
    dialog.selectors.set('[data-close]', new Element());
    const ids = { buyFeed: feed, buyFeedViewport: viewport, buyFeedTemplate: template, buyFeedMore: viewAll,
        buyFeedStatus: status(), buyFeedRetry: new Element(), purchasesDialog: dialog, purchasesTitle: new Element(),
        purchaseList: rows, purchaseListStatus: dialogStatus, purchaseListRetry: retry };
    document.getElementById = id => ids[id];
    document.lists.set('#buyFeedSeed li', seedElements);
    const window = new Element();
    const motion = { matches: false };
    window.matchMedia = () => motion;
    let now = Date.parse(SERVER_TIME);
    const intervals = [];
    vm.runInNewContext(feedScript, { document, window, URLSearchParams, fetch, Date: { now: () => now, parse: Date.parse },
        setInterval: (callback, delay) => {
            assert.equal(delay, 1000);
            intervals.push(callback);
        } });
    return { feed, viewport, rows, retry, dialogStatus, viewAll, motion, tick: intervals[0],
        advanceTime: ms => { now += ms; }, update: window.PcaOfferFeed.update,
        names: () => viewport.children.map(row => row.querySelector('strong').textContent),
        modalNames: () => rows.children.map(row => row.children[1].children[0].children[0].textContent) };
}

test('recent purchases scroll through six visible rows and return to the top', async () => {
    const css = await readFile(new URL('../static/css/offer-page.css', import.meta.url), 'utf8');
    assert.match(css, /\.buy-feed-viewport\s*\{[^}]*max-height: calc\(6 \* var\(--purchase-row-height\)\)/);
    const firstNames = ['Kavya', 'Nilan', 'Uma', 'Arun', 'Priya', 'Nila', 'Kumar', 'Janani'];
    const page = purchasePage(firstNames.map((name, i) => purchase(8 - i, name)));
    const { feed, viewport, motion, tick, names, update } = page;
    assert.deepEqual(names().slice(0, 6), firstNames.slice(0, 6));
    assert.equal(viewport.firstElementChild.querySelector('time').textContent, '30 mins ago');
    tick(); assert.equal(viewport.scrollTop, 50);
    tick(); assert.equal(viewport.scrollTop, 100);
    tick(); assert.equal(viewport.scrollTop, 0);

    await viewport.fire('wheel');
    viewport.scrollTop = 50;
    tick(); assert.equal(viewport.scrollTop, 50);
    page.advanceTime(4500);
    tick(); assert.equal(viewport.scrollTop, 100);
    motion.matches = true;
    tick(); assert.equal(viewport.scrollTop, 100);

    update([{ id: 9, name: 'New buyer', school: 'Example school', district: 'Jaffna',
        packageName: 'Practical Study Pack', confirmedAt: SERVER_TIME }], SERVER_TIME, false);
    assert.equal(viewport.scrollTop, 0);
    assert.deepEqual(names(), ['New buyer']);
    assert.equal(viewport.firstElementChild.querySelector('.buy-from').textContent, ' from Example school, Jaffna');
    update([], SERVER_TIME, false);
    assert.equal(feed.hidden, true);
});

test('scrolling either purchase list reaches all 300 records without duplicates', async () => {
    const all = Array.from({ length: 300 }, (_, index) => purchase(300 - index));
    const requests = [];
    let failNext = false;
    const page = purchasePage(all.slice(0, 30), { hasMore: true, fetch: async url => {
        const params = new URL(url, 'https://example.test').searchParams;
        const before = Number(params.get('beforeId') ?? 301);
        if (before !== 301) assert.equal(params.get('beforeAt'), PURCHASE_TIME);
        requests.push(before);
        if (failNext) { failNext = false; return { ok: false }; }
        const size = Number(params.get('size'));
        const items = all.filter(entry => entry.id < before).slice(0, size);
        return { ok: true, json: async () => ({ items, hasMore: items.at(-1)?.id > 1 }) };
    } });
    async function scrollToEnd(list) {
        list.scrollTop = list.scrollHeight - list.clientHeight;
        await Promise.all([list.fire('scroll'), list.fire('scroll')]);
    }

    assert.deepEqual(requests, []);
    for (let i = 0; i < 14; i++) await scrollToEnd(page.viewport);
    assert.deepEqual(page.names(), all.map(entry => entry.name));
    assert.equal(new Set(requests).size, 14);
    const anchor = page.viewport.scrollTop;
    page.update([purchase(301), ...all.slice(0, 29)], SERVER_TIME, true);
    assert.equal(page.viewport.scrollTop, anchor + 50);
    assert.deepEqual(page.names(), [purchase(301), ...all].map(entry => entry.name));
    await scrollToEnd(page.viewport);
    assert.equal(requests.length, 14);

    await page.viewAll.fire('click');
    assert.equal(page.rows.children.length, 20);
    failNext = true;
    await scrollToEnd(page.rows);
    assert.equal(page.rows.children.length, 20);
    assert.equal(page.retry.hidden, false);
    await page.retry.fire('click');
    assert.equal(page.retry.hidden, true);
    assert.equal(page.rows.children.length, 40);
    for (let i = 0; i < 13; i++) await scrollToEnd(page.rows);
    assert.deepEqual(page.modalNames(), all.map(entry => entry.name));
    const requestCount = requests.length;
    await scrollToEnd(page.rows);
    assert.equal(requests.length, requestCount);
    assert.equal(page.dialogStatus.textContent, "That's everyone so far.");
});
