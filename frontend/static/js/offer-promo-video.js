(() => {
    const video = document.querySelector('iframe[data-promo-video]');
    if (!video) return;

    let player;
    let visible = false;
    let autoAttempted = false;

    function startWhenVisible() {
        if (!visible || document.hidden || autoAttempted) return;
        connect();
        if (!player) return;
        autoAttempted = true;
        player.unMute();
        player.playVideo();
    }

    function connect() {
        if (video.hasAttribute('src')) return;
        const url = new URL(video.dataset.promoVideo);
        url.searchParams.set('playsinline', '1');
        url.searchParams.set('enablejsapi', '1');
        url.searchParams.set('controls', '0');
        url.searchParams.set('origin', window.location.origin);
        video.src = url.href;

        function attachPlayer() {
            new window.YT.Player(video, { events: {
                onReady({ target }) {
                    player = target;
                    startWhenVisible();
                },
                onStateChange({ data }) {
                    if (data === window.YT.PlayerState.PLAYING) autoAttempted = true;
                }
            } });
        }

        if (window.YT?.Player) {
            attachPlayer();
        } else {
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
    }

    function loadForManualPlayback() {
        if (video.hasAttribute('src')) return;
        autoAttempted = true;
        connect();
    }

    video.addEventListener('focus', loadForManualPlayback, { once: true });
    if (typeof IntersectionObserver !== 'function') {
        loadForManualPlayback();
        return;
    }

    const threshold = 0.51;
    const observer = new IntersectionObserver(([entry]) => {
        visible = entry.isIntersecting && entry.intersectionRatio >= threshold;
        startWhenVisible();
    }, { threshold });
    observer.observe(video);
    document.addEventListener('visibilitychange', startWhenVisible);
})();
