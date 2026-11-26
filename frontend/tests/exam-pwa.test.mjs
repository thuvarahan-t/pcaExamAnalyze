import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const origin = 'https://pca.example';
const workerScript = await readFile(new URL('../static/exam-sw.js', import.meta.url), 'utf8');
const manifest = JSON.parse(await readFile(new URL('../static/exam.webmanifest', import.meta.url), 'utf8'));
const pwaClient = await readFile(new URL('../static/js/exam-pwa.js', import.meta.url), 'utf8');
const androidChrome = 'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36';

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
    disabled = false;
    open = false;

    addEventListener(type, handle) { this.listeners.set(type, handle); }
    fire(type, event = { target: this, currentTarget: this }) { return this.listeners.get(type)?.(event); }
    showModal() { this.open = true; }
    close() { this.open = false; }
    setAttribute(attribute) { if (attribute === 'open') this.open = true; }
    removeAttribute(attribute) { if (attribute === 'open') this.close(); }
}

function testPage({ userAgent = 'Firefox/145', platform, maxTouchPoints = 0, standalone = false, supportsPrompt = false,
    legacyStandalone = false, hasInstall = true, install, session = new Map(),
    path = '/exam/packages?from=whatsapp#student-feedback', otherDialogOpen = false, customDialogOpen = false } = {}) {
    const registration = [];
    const button = new Target();
    const guide = new Target();
    const otherDialog = Object.assign(new Target(), { open: otherDialogOpen });
    const customDialog = Object.assign(new Target(), {
        hidden: !customDialogOpen,
        getClientRects: () => customDialogOpen ? [{}] : []
    });
    const controls = Object.fromEntries([
        'safari', 'browser', 'android', 'desktop', 'install-done'
    ].map(name => [name, new Target()]));
    guide.querySelector = selector => controls[selector.slice(10, -1)];
    const displayMode = Object.assign(new Target(), { matches: standalone });
    const sessionStorage = {
        getItem: key => session.get(key) ?? null,
        setItem: (key, value) => session.set(key, String(value))
    };
    const location = new URL(path, origin);
    const window = Object.assign(new Target(), {
        location, sessionStorage, isSecureContext: true,
        matchMedia: query => {
            assert.equal(query, '(display-mode: standalone)');
            return displayMode;
        }
    });
    if (supportsPrompt) window.onbeforeinstallprompt = null;
    const document = {
        currentScript: { dataset: { workerUrl: '/exam-sw.js', workerScope: '/exam' } },
        querySelector(selector) {
            if (selector === '[data-pca-install]') return hasInstall ? button : null;
            if (selector === '[data-pca-install-guide]') return guide;
            return this.querySelectorAll(selector)[0] ?? null;
        },
        querySelectorAll(selector) {
            const matches = [];
            if (selector.includes('dialog[open]') && otherDialog.open) matches.push(otherDialog);
            if (selector === 'dialog[open]' && guide.open) matches.push(guide);
            if (selector.includes('[role="dialog"]') && !customDialog.hidden) matches.push(customDialog);
            return matches;
        }
    };
    const navigator = {
        userAgent, platform, maxTouchPoints, standalone: legacyStandalone, install,
        serviceWorker: { register: async (...args) => registration.push(args) }
    };
    vm.runInNewContext(pwaClient, { window, document, navigator, sessionStorage, isSecureContext: true });
    return { window, button, guide, controls, displayMode, registration };
}

test('PWA registers with cache revalidation even on exam pages without an install control', () => {
    const page = testPage({ hasInstall: false });
    assert.equal(page.registration.length, 1);
    const [[url, options]] = page.registration;
    assert.equal(url, '/exam-sw.js');
    assert.equal(options.scope, '/exam');
    assert.equal(options.updateViaCache, 'none');
});

test('Android Chrome retains its own promotion and opens the native prompt from the install button', async () => {
    const page = testPage({ userAgent: androidChrome, supportsPrompt: true });
    assert.equal(page.guide.open, false);
    let prompted = 0;
    let prevented = false;
    page.window.fire('beforeinstallprompt', {
        preventDefault() { prevented = true; },
        async prompt() { prompted++; },
        userChoice: Promise.resolve({ outcome: 'accepted' })
    });
    assert.equal(prevented, false);
    assert.equal(prompted, 0);
    assert.equal(page.button.hidden, false);
    assert.equal(page.guide.open, false);
    const installation = page.button.fire('click');
    assert.equal(prompted, 1);
    assert.equal(page.guide.open, false);
    await installation;
    assert.equal(page.button.hidden, true);
    assert.equal(page.guide.open, false);
});

test('the install button prefers navigator.install over an available captured prompt', async () => {
    let directCalls = 0;
    let legacyCalls = 0;
    const page = testPage({ supportsPrompt: true, install: async () => { directCalls++; return { manifestId: origin + '/exam' }; } });
    assert.equal(page.guide.open, false);
    page.window.fire('beforeinstallprompt', {
        preventDefault() { assert.fail('the page must leave browser promotion available'); },
        async prompt() { legacyCalls++; return { outcome: 'accepted' }; }
    });
    assert.equal(directCalls, 0);
    assert.equal(legacyCalls, 0);
    const installation = page.button.fire('click');
    assert.equal(directCalls, 1);
    assert.equal(legacyCalls, 0);
    assert.equal(page.guide.open, false);
    await installation;
    assert.equal(page.button.hidden, true);
    assert.equal(page.guide.open, false);
});

test('canceling navigator.install leaves the guide closed and allows another direct install click', async () => {
    let calls = 0;
    const page = testPage({ install: async () => {
        calls++;
        if (calls === 1) throw new DOMException('Canceled', 'AbortError');
        return { manifestId: origin + '/exam' };
    } });
    assert.equal(page.guide.open, false);
    await page.button.fire('click');
    assert.equal(calls, 1);
    assert.equal(page.guide.open, false);
    assert.equal(page.button.hidden, false);
    assert.equal(page.button.disabled, false);
    await page.button.fire('click');
    assert.equal(calls, 2);
    assert.equal(page.button.hidden, true);
    assert.equal(page.guide.open, false);
});

test('an early Android install click opens its manual guide and a late prompt dismisses it', async () => {
    const session = new Map();
    const page = testPage({ userAgent: androidChrome, supportsPrompt: true, session });
    assert.equal(page.guide.open, false);
    assert.equal(session.size, 0);
    await page.button.fire('click');
    assert.equal(page.guide.open, true);
    assert.equal(page.controls.browser.hidden, false);
    assert.equal(page.controls.android.hidden, false);
    assert.equal(page.controls.desktop.hidden, true);
    assert.equal(page.controls.safari.hidden, true);

    let prompts = 0;
    let prevented = false;
    page.window.fire('beforeinstallprompt', {
        preventDefault() { prevented = true; },
        async prompt() { prompts++; return { outcome: 'accepted' }; }
    });
    assert.equal(prevented, false);
    assert.equal(prompts, 0);
    assert.equal(page.guide.open, false);
    await page.button.fire('click');
    assert.equal(prompts, 1);
    assert.equal(page.guide.open, false);
});

test('declining a single-use browser prompt leaves the manual guide closed until another install click', async () => {
    const page = testPage({ userAgent: 'Chrome/140', supportsPrompt: true });
    let prompts = 0;
    page.window.fire('beforeinstallprompt', {
        preventDefault() {},
        async prompt() { prompts++; return { outcome: 'dismissed' }; }
    });
    await page.button.fire('click');
    assert.equal(prompts, 1);
    assert.equal(page.button.hidden, false);
    assert.equal(page.guide.open, false);
    await page.button.fire('click');
    assert.equal(prompts, 1);
    assert.equal(page.guide.open, true);
    assert.equal(page.controls.browser.hidden, false);
    assert.equal(page.controls.desktop.hidden, false);
    assert.equal(page.controls.android.hidden, true);
    page.window.fire('appinstalled');
    assert.equal(page.button.hidden, true);
    assert.equal(page.guide.open, false);
});

test('a modern installation error uses a captured prompt only on another click, or shows manual steps without one', async () => {
    for (const hasLegacyPrompt of [true, false]) {
        let modernCalls = 0;
        let legacyCalls = 0;
        const page = testPage({ supportsPrompt: hasLegacyPrompt, install: async () => {
            modernCalls++;
            throw new DOMException('Install unavailable', 'NotAllowedError');
        } });
        if (hasLegacyPrompt) {
            page.window.fire('beforeinstallprompt', {
                preventDefault() {},
                async prompt() { legacyCalls++; return { outcome: 'accepted' }; }
            });
        }
        await page.button.fire('click');
        assert.equal(modernCalls, 1);
        assert.equal(legacyCalls, 0);
        assert.equal(page.guide.open, !hasLegacyPrompt);
        if (!hasLegacyPrompt) page.controls['install-done'].fire('click');

        await page.button.fire('click');
        assert.equal(modernCalls, 1);
        assert.equal(legacyCalls, Number(hasLegacyPrompt));
        assert.equal(page.guide.open, !hasLegacyPrompt);
        assert.equal(page.button.hidden, hasLegacyPrompt);
    }
});

test('without native install support the guide opens once per tab across Exam and Offer', async () => {
    const session = new Map();
    const exam = testPage({ path: '/exam', session });
    assert.equal(exam.guide.open, true);
    exam.controls['install-done'].fire('click');

    const offer = testPage({ path: '/exam/packages', session });
    assert.equal(offer.guide.open, false);
    assert.equal(offer.button.hidden, false);
    await offer.button.fire('click');
    assert.equal(offer.guide.open, true);
    assert.equal(testPage({ path: '/exam/packages', session }).guide.open, false);
    assert.equal(testPage({ path: '/exam/packages' }).guide.open, true);
});

test('another open modal prevents the automatic guide without using up the tab reminder', () => {
    for (const dialog of [{ otherDialogOpen: true }, { customDialogOpen: true }]) {
        const session = new Map();
        const busy = testPage({ ...dialog, session });
        assert.equal(busy.guide.open, false);
        assert.equal(busy.button.hidden, false);
        assert.equal(testPage({ session }).guide.open, true);
    }
});

test('iPhone Safari and desktop-mode iPad show Safari instructions', async () => {
    for (const page of [testPage({ userAgent: 'iPhone AppleWebKit/605 Safari/604.1' }),
        testPage({ userAgent: 'Macintosh AppleWebKit/605 Safari/604.1', platform: 'MacIntel', maxTouchPoints: 5 })]) {
        await page.button.fire('click');
        assert.equal(page.guide.open, true);
        assert.equal(page.controls.safari.hidden, false);
        assert.equal(page.controls.browser.hidden, true);
        page.controls['install-done'].fire('click');
        assert.equal(page.guide.open, false);
    }
});

test('Android and Apple in-app browsers show their platform installation steps directly', () => {
    const android = testPage({ userAgent: 'Mozilla/5.0 (Linux; Android 15; wv) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36' });
    assert.equal(android.guide.open, true);
    assert.equal(android.controls.browser.hidden, false);
    assert.equal(android.controls.android.hidden, false);
    assert.equal(android.controls.desktop.hidden, true);
    assert.equal(android.controls.safari.hidden, true);

    const apple = testPage({ userAgent: 'iPhone AppleWebKit/605 WhatsApp' });
    assert.equal(apple.guide.open, true);
    assert.equal(apple.controls.browser.hidden, true);
    assert.equal(apple.controls.safari.hidden, false);
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
