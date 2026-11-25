import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const origin = 'https://pca.example';
const workerScript = await readFile(new URL('../static/exam-sw.js', import.meta.url), 'utf8');
const manifest = JSON.parse(await readFile(new URL('../static/exam.webmanifest', import.meta.url), 'utf8'));

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
