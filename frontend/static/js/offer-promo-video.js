(() => {
    const mux = document.querySelector('iframe[data-mux-src]');
    if (mux) {
        const source = new URL(mux.dataset.muxSrc);
        const poster = mux.parentElement.querySelector('.offer-video-poster');
        if (poster) source.searchParams.set('poster', poster.src);
        if (typeof IntersectionObserver !== 'function'
            || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            mux.src = source.href;
            return;
        }

        let inView = false;
        function startMux() {
            if (!inView || document.hidden) return;
            observer.disconnect();
            document.removeEventListener('visibilitychange', startMux);
            source.searchParams.set('autoplay', 'true');
            mux.src = source.href;
        }

        const observer = new IntersectionObserver(([entry]) => {
            inView = entry.isIntersecting;
            startMux();
        });
        observer.observe(mux.parentElement);
        document.addEventListener('visibilitychange', startMux);
        return;
    }

    const video = document.querySelector('iframe[data-promo-video]');
    if (!video || typeof IntersectionObserver !== 'function'
        || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    let player;
    let visible = false;
    let apiRequested = false;

    function startWhenVisible() {
        if (!visible || document.hidden) return;
        if (player) {
            observer.disconnect();
            document.removeEventListener('visibilitychange', startWhenVisible);
            if (player.getPlayerState() === window.YT.PlayerState.UNSTARTED
                || player.getPlayerState() === window.YT.PlayerState.CUED) {
                player.mute();
                player.playVideo();
            }
            return;
        }
        if (apiRequested) return;
        apiRequested = true;

        function attachPlayer() {
            new window.YT.Player(video, {
                events: {
                    onReady({ target }) {
                        player = target;
                        startWhenVisible();
                    }
                }
            });
        }

        if (window.YT?.Player) {
            attachPlayer();
            return;
        }
        const previousReady = window.onYouTubeIframeAPIReady;
        window.onYouTubeIframeAPIReady = () => {
            previousReady?.();
            attachPlayer();
        };
        const api = document.createElement('script');
        api.async = true;
        api.src = 'https://www.youtube.com/iframe_api';
        document.head.append(api);
    }

    const threshold = 0.51;
    const observer = new IntersectionObserver(([entry]) => {
        visible = entry.isIntersecting && entry.intersectionRatio >= threshold;
        startWhenVisible();
    }, { threshold });
    observer.observe(video.parentElement);
    document.addEventListener('visibilitychange', startWhenVisible);
})();
