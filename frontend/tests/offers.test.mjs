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
        'packageRegistration', 'registrationTitle', 'fullPoster', 'previewPackageName', 'offerDeadline', 'refreshStatus']
        .map(id => [id, new Element()]));
    for (const id of ['countDays', 'countHours', 'countMinutes', 'countSeconds']) ids[id].textContent = '--';
    for (const id of ['registrationDialog', 'posterDialog']) ids[id].selectors.set('[data-close]', new Element());
    ids.packageName.value = 'Thuvrahan'; ids.packageRegistration.value = 'PCA-101';
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
        : { url: 'https://wa.me/94779179035?text=prepared', details: { name: 'Thuvrahan', registrationId: 'PCA-101' } });
    const context = vm.createContext({ document, window, location, URLSearchParams, Intl, Date, Number, Map, String,
        performance: { now: () => elapsed }, AbortSignal: { timeout() {} },
        FormData: class { *[Symbol.iterator]() { yield ['name', ids.packageName.value]; yield ['registrationId', ids.packageRegistration.value]; } },
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
    assert.equal(page.requests[0].options.body.get('registrationId'), 'PCA-101');
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
        url: 'https://wa.me/94779179035', details: { name: 'Thuvrahan', registrationId: 'PCA-101' } }) });
    await submission; assert.equal(page.location.assigned, undefined);
});

test('confirmed students filter by either name or registration ID without changing purchases', async () => {
    const document = new Element(), filter = new Element(), status = new Element();
    const rows = [['PCA-101', 'Thuvrahan Kumar'], ['PCA-102', 'Arun Student']]
        .map(cells => Object.assign(new Element(), { cells: cells.map(text => new Element(text)) }));
    document.getElementById = id => ({ purchaseFilter: filter, purchaseFilterStatus: status })[id] ?? null;
    document.lists.set('[data-purchase-row]', rows);
    vm.runInNewContext(adminScript, { document });
    filter.value = 'THUV'; await filter.fire('input'); assert.deepEqual(rows.map(row => row.hidden), [false, true]);
    filter.value = '102'; await filter.fire('input'); assert.deepEqual(rows.map(row => row.hidden), [true, false]);
    filter.value = ''; await filter.fire('input'); assert.deepEqual(rows.map(row => row.hidden), [false, false]);
    assert.equal(rows.length, 2);
});
