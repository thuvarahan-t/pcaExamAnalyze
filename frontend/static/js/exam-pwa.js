(() => {
    const { workerUrl, workerScope } = document.currentScript.dataset;
    if ('serviceWorker' in navigator) {
        // Let a new worker wait for existing tabs to close, including active exams.
        navigator.serviceWorker.register(workerUrl, { scope: workerScope, updateViaCache: 'none' }).catch(() => {});
    }
})();
