import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const studentScript = await readFile(new URL('../static/js/class-packages.js', import.meta.url), 'utf8');
const adminScript = await readFile(new URL('../static/js/package-admin.js', import.meta.url), 'utf8');

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

test('recent purchases scroll through three visible rows and return to the top', async () => {
    const feedScript = await readFile(new URL('../static/js/offer-feed.js', import.meta.url), 'utf8');
    const feed = new Element();
    const viewport = Object.assign(new Element(), {
        clientHeight: 150, scrollTop: 0,
        replaceChildren(...children) { this.children = children; },
        scrollTo({ top }) { this.scrollTop = top; },
        querySelectorAll() { return this.children.map(row => row.querySelector('time')); }
    });
    Object.defineProperty(viewport, 'scrollHeight', { get: () => viewport.children.length * 50 });
    const template = { content: { firstElementChild: { cloneNode() {
        const row = Object.assign(new Element(), { offsetHeight: 50 });
        for (const selector of ['strong', '.buy-from', 'b', 'time', '.buy-avatar']) {
            row.selectors.set(selector, new Element());
        }
        row.querySelector('.buy-avatar').style.setProperty = () => {};
        return row;
    } } } };
    const seed = ['Kavya', 'Nilan', 'Uma', 'Arun', 'Priya'].map((name, index) => Object.assign(new Element(), {
        dataset: { id: String(5 - index), name, pack: 'Theory Study Pack', at: '2026-11-12T10:00:00Z' }
    }));
    const document = new Element();
    document.getElementById = id => ({ buyFeed: feed, buyFeedViewport: viewport, buyFeedTemplate: template })[id];
    document.lists.set('#buyFeedSeed li', seed);
    const window = new Element();
    const motion = { matches: false };
    window.matchMedia = () => motion;
    let now = Date.parse('2026-11-12T10:30:00Z');
    const intervals = [];
    vm.runInContext(feedScript, vm.createContext({ document, window, Date: { now: () => now, parse: Date.parse },
        setInterval: (callback, delay) => {
            assert.equal(delay, 1000);
            intervals.push(callback);
        } }));
    const tick = () => intervals[0]();
    const names = () => viewport.children.map(row => row.querySelector('strong').textContent);
    assert.deepEqual(names().slice(0, 3), ['Kavya', 'Nilan', 'Uma']);
    assert.equal(viewport.firstElementChild.querySelector('time').textContent, '30 mins ago');
    tick(); assert.equal(viewport.scrollTop, 50);
    tick(); assert.equal(viewport.scrollTop, 100);
    tick(); assert.equal(viewport.scrollTop, 0);

    await viewport.fire('wheel');
    viewport.scrollTop = 50;
    tick(); assert.equal(viewport.scrollTop, 50);
    now += 4500;
    tick(); assert.equal(viewport.scrollTop, 100);
    motion.matches = true;
    tick(); assert.equal(viewport.scrollTop, 100);

    window.PcaOfferFeed.update([{ id: 6, name: 'New buyer', school: 'Example school', district: 'Jaffna',
        packageName: 'Practical Study Pack', confirmedAt: '2026-11-12T10:30:00Z' }], '2026-11-12T10:30:00Z');
    assert.equal(viewport.scrollTop, 0);
    assert.deepEqual(names(), ['New buyer']);
    assert.equal(viewport.firstElementChild.querySelector('.buy-from').textContent, ' from Example school, Jaffna');
    window.PcaOfferFeed.update([], '2026-11-12T10:30:00Z');
    assert.equal(feed.hidden, true);
});
