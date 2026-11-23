(() => {
    'use strict';
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
    // ---- The header countdown appears once the hero countdown is out of view. ----
    const countdown = document.querySelector('.countdown-panel');
    if (countdown && 'IntersectionObserver' in window) {
        new IntersectionObserver(([entry]) => {
            document.body.classList.toggle('past-hero', !entry.isIntersecting && entry.boundingClientRect.top < 0);
        }, { rootMargin: '-72px 0px 0px' }).observe(countdown);
    }

    // ---- Light that follows the mouse across package cards. ----
    const grid = document.querySelector('.package-grid');
    let frame = 0;
    grid?.addEventListener('pointermove', event => {
        if (event.pointerType !== 'mouse' || frame || reducedMotion.matches) return;
        const card = event.target.closest('.package-card');
        if (!card) return;
        frame = requestAnimationFrame(() => {
            frame = 0;
            const box = card.getBoundingClientRect();
            card.style.setProperty('--mx', `${event.clientX - box.left}px`);
            card.style.setProperty('--my', `${event.clientY - box.top}px`);
        });
    });
})();
