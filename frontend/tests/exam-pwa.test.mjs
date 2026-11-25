import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const origin = 'https://pca.example';
const workerScript = await readFile(new URL('../static/exam-sw.js', import.meta.url), 'utf8');
const manifest = JSON.parse(await readFile(new URL('../static/exam.webmanifest', import.meta.url), 'utf8'));
const pwaClient = await readFile(new URL('../static/js/exam-pwa.js', import.meta.url), 'utf8');

function testWorker() {
    const offlinePage = { html: 'generic offline response' };
    const handlers = {};
    const saved = new Map([['/exam-offline.html', offlinePage]]);
    const requests = [];
    const installed = [];
    const deleted = [];
    const cache = {
        addAll: async paths => installed.push(...paths),
        match: async request => saved.get(request.url),
        put: async (request, response) => saved.set(request.url, response)
    };
    const oldCache = 'pca-exam-v0';
    const currentCache = 'pca-exam-v1';
    const unrelatedCache = 'other-app-v1';
    const caches = {
        open: async name => {
            assert.equal(name, currentCache);
            return cache;
        },
        match: async (path, { cacheName }) => {
            assert.equal(cacheName, currentCache);
            return saved.get(path);
        },
        keys: async () => [oldCache, currentCache, unrelatedCache],
        delete: async name => deleted.push(name)
    };
    const onlineResponse = { ok: true, type: 'basic', redirected: false, clone() { return this; } };
    let offline = false;
    let failure = false;
    const context = {
        URL,
        Response,
        caches,
        self: { location: { origin }, addEventListener: (type, handle) => handlers[type] = handle },
        fetch: async (request, options) => {
            requests.push({ request, options });
            if (offline) throw new TypeError('Network unavailable');
            return failure ? { ok: false, status: 404 } : onlineResponse;
        }
    };
    vm.runInNewContext(workerScript, context);
    return {
        saved, requests, installed, deleted, offlinePage, onlineResponse,
        goOffline: () => offline = true,
        networkFailure: () => failure = true,
        lifecycle: type => new Promise(resolve => handlers[type]({ waitUntil: resolve })),
        fetch: (url, options = {}) => {
            const request = { url: new URL(url, origin).href, method: 'GET', mode: 'same-origin', ...options };
            let response;
            handlers.fetch({ request, respondWith: action => response = action });
            return response;
        }
    };
}

test('the PCA Exam manifest scopes the same installed app over paper links, sessions, results and Offer', () => {
    assert.equal(manifest.name, 'PCA Exam');
    assert.equal(manifest.id, '/exam');
    assert.equal(manifest.start_url, '/exam');
    assert.equal(manifest.scope, '/exam');
    assert.equal(manifest.display, 'standalone');
    assert.deepEqual(manifest.icons.map(icon => icon.sizes), ['192x192', '512x512']);
    for (const path of ['/exam', '/exam/p/shared-paper', '/exam/session/10', '/exam/results/10', '/exam/packages']) {
        assert.ok(path.startsWith(manifest.scope), path);
    }
});

test('worker precaches a generic offline page and removes only its older caches on natural activation', async () => {
    const worker = testWorker();
    await worker.lifecycle('install');
    await worker.lifecycle('activate');
    assert.deepEqual(worker.installed, ['/exam-offline.html', '/img/pca-exam-192.png?v=1']);
    assert.deepEqual(worker.deleted, ['pca-exam-v0']);
});

test('student navigation prefers a fresh response and uses the same generic page offline', async () => {
    const worker = testWorker();
    const routes = ['/exam', '/exam/p/shared-paper', '/exam/session/10', '/exam/results/10', '/exam/packages'];
    for (const path of routes) {
        assert.equal(await worker.fetch(path, { mode: 'navigate' }), worker.onlineResponse);
        assert.equal(worker.requests.at(-1).options.cache, 'no-store');
    }
    worker.networkFailure();
    assert.equal((await worker.fetch('/exam/p/nonexistent', { mode: 'navigate' })).status, 404);
    worker.goOffline();
    for (const path of routes) {
        assert.equal(await worker.fetch(path, { mode: 'navigate' }), worker.offlinePage);
    }
    assert.deepEqual([...worker.saved.keys()], ['/exam-offline.html']);
});

test('worker bypasses answer recovery, submissions, APIs, PDFs, private images and admin requests', () => {
    const worker = testWorker();
    for (const path of ['/exam/session/3/answer', '/exam/session/3/answers', '/exam/session/3/finish', '/exam/session/3/submit',
        '/exam/packages/feedback', '/exam/packages/feedback/transcribe']) {
        assert.equal(worker.fetch(path, { method: 'POST', mode: 'navigate' }), undefined, path);
    }
    for (const path of ['/exam/admin', '/exam/results/1/report.pdf', '/exam/packages/status', '/exam/time',
        '/exam/session/3/questions/4/image?v=1', '/exam/packages/1/poster?v=2', '/exam/packages/feedback/1/photo',
        'https://r2.example/img/question.png?v=2', '/js/unversioned.js', '/js/i18n.js?v=7&export=secret']) {
        assert.equal(worker.fetch(path, { mode: 'navigate' }), undefined, path);
        assert.equal(worker.fetch(path), undefined, path);
    }
    assert.equal(worker.fetch('/exam?paperFragment=true'), undefined);
    assert.equal(worker.requests.length, 0);
});

test('worker caches only successful versioned, same-origin static asset requests', async () => {
    const worker = testWorker();
    assert.equal(await worker.fetch('/js/exam-session.js?v=15'), worker.onlineResponse);
    assert.equal(await worker.fetch('/js/exam-session.js?v=15'), worker.onlineResponse);
    worker.networkFailure();
    assert.equal((await worker.fetch('/js/nonexistent.js?v=1')).status, 404);
    assert.equal((await worker.fetch('/js/nonexistent.js?v=1')).status, 404);
    assert.equal(worker.requests.length, 3);
    assert.deepEqual([...worker.saved.keys()], ['/exam-offline.html', origin + '/js/exam-session.js?v=15']);
});

class Target {
    listeners = new Map();
    hidden = true;
    open = false;

    addEventListener(type, handle) { this.listeners.set(type, handle); }
    fire(type, event) { return this.listeners.get(type)?.(event); }
    showModal() { this.open = true; }
    close() { this.open = false; }
    removeAttribute(attribute) { if (attribute === 'open') this.close(); }
    focus() { this.focused = true; }
    select() { this.selected = true; }
}

function testPage({ userAgent = 'Chrome/140', platform, maxTouchPoints = 0, standalone = false,
    legacyStandalone = false, clipboard, hasInstall = true } = {}) {
    const registration = [];
    const button = new Target();
    const guide = new Target();
    const controls = Object.fromEntries(['safari', 'browser', 'embedded', 'open-browser', 'copy-status', 'install-done', 'copy']
        .map(name => [name, new Target()]));
    const pageUrl = new Target();
    guide.querySelector = selector => selector === '#pca-install-url' ? pageUrl : controls[selector.slice(10, -1)];
    const displayMode = Object.assign(new Target(), { matches: standalone });
    const window = Object.assign(new Target(), {
        location: { href: origin + '/exam/packages?from=whatsapp#student-feedback' },
        matchMedia: query => {
            assert.equal(query, '(display-mode: standalone)');
            return displayMode;
        }
    });
    const document = {
        currentScript: { dataset: { workerUrl: '/exam-sw.js', workerScope: '/exam' } },
        querySelector: selector => selector === '[data-pca-install-guide]' ? guide : hasInstall ? button : null
    };
    const navigator = {
        userAgent, platform, maxTouchPoints, standalone: legacyStandalone, clipboard,
        serviceWorker: { register: async (...args) => registration.push(args) }
    };
    vm.runInNewContext(pwaClient, { window, document, navigator });
    return { window, button, guide, controls, pageUrl, displayMode, registration };
}

test('PWA registers with cache revalidation even on exam pages without an install control', () => {
    const page = testPage({ hasInstall: false });
    assert.equal(page.registration.length, 1);
    const [[url, options]] = page.registration;
    assert.equal(url, '/exam-sw.js');
    assert.equal(options.scope, '/exam');
    assert.equal(options.updateViaCache, 'none');
});

test('Android Chrome native prompt runs only on click and accepted installs hide the control', async () => {
    const page = testPage({ userAgent: 'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36' });
    let prompted = 0;
    let prevented = false;
    page.window.fire('beforeinstallprompt', {
        preventDefault() { prevented = true; },
        async prompt() { prompted++; },
        userChoice: Promise.resolve({ outcome: 'accepted' })
    });
    assert.ok(prevented);
    assert.equal(prompted, 0);
    assert.equal(page.button.hidden, false);
    await page.button.fire('click');
    assert.equal(prompted, 1);
    assert.equal(page.button.hidden, true);
    assert.equal(page.guide.open, false);
});

test('declined native prompts keep the install control and offer browser instructions next', async () => {
    const page = testPage();
    page.window.fire('beforeinstallprompt', {
        preventDefault() {},
        async prompt() { return { outcome: 'dismissed' }; }
    });
    await page.button.fire('click');
    assert.equal(page.button.hidden, false);
    await page.button.fire('click');
    assert.equal(page.guide.open, true);
    assert.equal(page.controls.browser.hidden, false);
    page.window.fire('appinstalled');
    assert.equal(page.button.hidden, true);
    assert.equal(page.guide.open, false);
});

test('iPhone Safari and desktop-mode iPad show Safari instructions', async () => {
    for (const page of [testPage({ userAgent: 'iPhone AppleWebKit/605 Safari/604.1' }),
        testPage({ userAgent: 'Macintosh AppleWebKit/605 Safari/604.1', platform: 'MacIntel', maxTouchPoints: 5 })]) {
        await page.button.fire('click');
        assert.equal(page.guide.open, true);
        assert.equal(page.controls.safari.hidden, false);
        assert.equal(page.controls.browser.hidden, true);
        assert.equal(page.controls.embedded.open, false);
        page.controls['install-done'].fire('click');
        assert.equal(page.guide.open, false);
    }
});

test('in-app guide expands browser guidance and copies the exact page URL including the anchor', async () => {
    const copied = [];
    const page = testPage({ userAgent: 'iPhone AppleWebKit/605 WhatsApp',
        clipboard: { writeText: async url => copied.push(url) } });
    await page.button.fire('click');
    assert.equal(page.controls.embedded.open, true);
    assert.equal(page.controls['open-browser'].textContent, 'Safari');
    await page.controls.copy.fire('click');
    assert.deepEqual(copied, [page.window.location.href]);
    assert.equal(page.controls['copy-status'].textContent, 'Link copied.');

    const android = testPage({ userAgent: 'Android; wv) AppleWebKit/537.36' });
    await android.button.fire('click');
    assert.equal(android.controls.embedded.open, true);
    assert.equal(android.controls['open-browser'].textContent, 'Chrome');
    await android.controls.copy.fire('click');
    assert.equal(android.pageUrl.selected, true);
});

test('standalone display mode and legacy iPhone standalone hide the install control', async () => {
    for (const page of [testPage({ standalone: true }), testPage({ legacyStandalone: true })]) {
        assert.equal(page.button.hidden, true);
        await page.button.fire('click');
        assert.equal(page.guide.open, false);
    }
    const page = testPage();
    await page.button.fire('click');
    page.displayMode.matches = true;
    page.displayMode.fire('change');
    assert.equal(page.button.hidden, true);
    assert.equal(page.guide.open, false);
});
