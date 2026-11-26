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
    const android = /Android/i.test(userAgent);
    const guideKey = 'pca-exam-install-guide-shown';
    let useWebInstall = typeof navigator.install === 'function';
    let nativePrompt;
    let installed = false;
    let prompting = false;

    guide.querySelector('[data-pca-safari]').hidden = !appleMobile;
    guide.querySelector('[data-pca-browser]').hidden = appleMobile;
    guide.querySelector('[data-pca-android]').hidden = !android;
    guide.querySelector('[data-pca-desktop]').hidden = android;

    function canPrompt() {
        return useWebInstall || Boolean(nativePrompt);
    }

    function dismissGuide() {
        if (guide.open && typeof guide.close === 'function') guide.close();
        else guide.removeAttribute('open');
    }

    function syncVisibility() {
        install.hidden = standalone.matches || navigator.standalone === true || installed;
        if (install.hidden) dismissGuide();
    }

    function showGuide() {
        if (install.hidden || guide.open) return;
        if (typeof guide.showModal === 'function') guide.showModal();
        else guide.setAttribute('open', '');
        try {
            window.sessionStorage.setItem(guideKey, '1');
        } catch {
            // The guide still works when the browser blocks storage.
        }
    }

    function guideWasShown() {
        try {
            return window.sessionStorage.getItem(guideKey) === '1';
        } catch {
            return false;
        }
    }

    window.addEventListener('beforeinstallprompt', event => {
        if (install.hidden) return;
        // Keep Chrome's own install promotion available alongside the page button.
        nativePrompt = event;
        if (guide.open) dismissGuide();
    });
    window.addEventListener('appinstalled', () => {
        installed = true;
        nativePrompt = undefined;
        syncVisibility();
    });
    standalone.addEventListener?.('change', syncVisibility);
    syncVisibility();

    async function requestInstall() {
        if (install.hidden || prompting) return;
        if (!canPrompt()) {
            showGuide();
            return;
        }
        prompting = true;
        install.disabled = true;
        try {
            if (useWebInstall) {
                await navigator.install();
                installed = true;
            } else {
                const prompt = nativePrompt;
                nativePrompt = undefined;
                const choice = (await prompt.prompt()) ?? (await prompt.userChoice);
                if (choice?.outcome === 'accepted') installed = true;
            }
        } catch (error) {
            if (error?.name !== 'AbortError') {
                useWebInstall = false;
                if (!canPrompt()) showGuide();
            }
        } finally {
            prompting = false;
            install.disabled = false;
            syncVisibility();
        }
    }

    install.addEventListener('click', requestInstall);
    guide.querySelector('[data-pca-install-done]').addEventListener('click', dismissGuide);

    // A browser that supports beforeinstallprompt may dispatch it after the page loads.
    if (!canPrompt() && !('onbeforeinstallprompt' in window) && !guideWasShown()
        && !document.querySelector('dialog[open], [role="dialog"][aria-modal="true"]:not([hidden])')) {
        showGuide();
    }
})();
