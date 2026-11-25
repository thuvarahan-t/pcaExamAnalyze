(() => {
    const { workerUrl, workerScope } = document.currentScript.dataset;
    if ('serviceWorker' in navigator) {
        // Let a new worker wait for existing tabs to close, including active exams.
        navigator.serviceWorker.register(workerUrl, { scope: workerScope, updateViaCache: 'none' }).catch(() => {});
    }

    const install = document.querySelector('[data-pca-install]');
    if (!install) return;

    const guide = document.querySelector('[data-pca-install-guide]');
    const standalone = window.matchMedia('(display-mode: standalone)');
    const userAgent = navigator.userAgent;
    const appleMobile = /iPhone|iPad|iPod/i.test(userAgent)
        || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    const inApp = /WhatsApp|Instagram|FBAN|FBAV|FB_IAB|Line\/|TikTok|Snapchat|MicroMessenger|; wv\)/i.test(userAgent)
        || (appleMobile && /AppleWebKit\//i.test(userAgent) && !/Safari\//i.test(userAgent));
    const pageUrl = guide.querySelector('#pca-install-url');
    const copyStatus = guide.querySelector('[data-pca-copy-status]');
    let nativePrompt;
    let installed = false;

    function dismissGuide() {
        if (guide.open && typeof guide.close === 'function') guide.close();
        else guide.removeAttribute('open');
    }

    function syncVisibility() {
        install.hidden = standalone.matches || navigator.standalone === true || installed;
        if (install.hidden) dismissGuide();
    }

    function showGuide() {
        guide.querySelector('[data-pca-safari]').hidden = !appleMobile;
        guide.querySelector('[data-pca-browser]').hidden = appleMobile;
        guide.querySelector('[data-pca-embedded]').open = inApp;
        guide.querySelector('[data-pca-open-browser]').textContent = appleMobile ? 'Safari' : 'Chrome';
        pageUrl.value = window.location.href;
        copyStatus.textContent = '';
        if (typeof guide.showModal === 'function') guide.showModal();
        else guide.setAttribute('open', '');
    }

    window.addEventListener('beforeinstallprompt', event => {
        if (install.hidden) return;
        event.preventDefault();
        nativePrompt = event;
    });
    window.addEventListener('appinstalled', () => {
        installed = true;
        nativePrompt = undefined;
        syncVisibility();
    });
    standalone.addEventListener?.('change', syncVisibility);
    syncVisibility();

    install.addEventListener('click', async () => {
        if (install.hidden) return;
        if (!nativePrompt) {
            showGuide();
            return;
        }
        const prompt = nativePrompt;
        nativePrompt = undefined;
        install.disabled = true;
        try {
            const choice = (await prompt.prompt()) ?? (await prompt.userChoice);
            if (choice?.outcome === 'accepted') installed = true;
            syncVisibility();
        } catch {
            if (!install.hidden) showGuide();
        } finally {
            install.disabled = false;
        }
    });

    guide.querySelector('[data-pca-install-done]').addEventListener('click', dismissGuide);
    guide.querySelector('[data-pca-copy]').addEventListener('click', async () => {
        try {
            await navigator.clipboard.writeText(pageUrl.value);
            copyStatus.textContent = 'Link copied.';
        } catch {
            pageUrl.focus();
            pageUrl.select();
            copyStatus.textContent = 'Select Copy to copy this link.';
        }
    });
})();
