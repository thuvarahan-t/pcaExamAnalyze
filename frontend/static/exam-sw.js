const CACHE_PREFIX = 'pca-exam-v';
const CACHE_NAME = `${CACHE_PREFIX}1`;
const OFFLINE_PAGE = '/exam-offline.html';
const OFFLINE_ASSETS = [OFFLINE_PAGE, '/img/pca-exam-192.png?v=1'];

// The /exam scope also contains teacher pages and API URLs. Only these student
// documents get an offline response, and their real responses are never cached.
const STUDENT_PAGE = /^\/exam(?:\/?|\/(?:details|result|results(?:\/\d+)?|packages\/?|p\/[^/]+(?:\/result(?:-lookup)?)?|session\/\d+))$/;
const STATIC_ASSET = /^\/(?:css\/[\w-]+\.css|js\/[\w.-]+\.js|img\/[\w-]+\.(?:png|jpe?g|svg|webp)|fonts\/[\w.-]+\.woff2)$/;
const VERSION_QUERY = /^\?v=\d+$/;

self.addEventListener('install', event => {
    event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(OFFLINE_ASSETS)));
});

self.addEventListener('activate', event => {
    event.waitUntil(caches.keys().then(keys => Promise.all(keys
        .filter(key => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME)
        .map(key => caches.delete(key)))));
});

self.addEventListener('fetch', event => {
    const { request } = event;
    const url = new URL(request.url);
    if (request.method !== 'GET' || url.origin !== self.location.origin) return;

    if (request.mode === 'navigate' && STUDENT_PAGE.test(url.pathname)) {
        event.respondWith(fetch(request, { cache: 'no-store' }).catch(async () =>
            (await caches.match(OFFLINE_PAGE, { cacheName: CACHE_NAME })) ?? Response.error()));
    } else if (request.mode !== 'navigate' && STATIC_ASSET.test(url.pathname) && VERSION_QUERY.test(url.search)) {
        event.respondWith(staticResponse(request));
    }
});

async function staticResponse(request) {
    const cache = await caches.open(CACHE_NAME).catch(() => null);
    const saved = await cache?.match(request).catch(() => null);
    if (saved) return saved;

    const response = await fetch(request);
    if (response.ok && response.type === 'basic' && !response.redirected) {
        await cache?.put(request, response.clone()).catch(() => {});
    }
    return response;
}
