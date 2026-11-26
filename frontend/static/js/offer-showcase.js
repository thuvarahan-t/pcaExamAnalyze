(() => {
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
    const compact = matchMedia('(max-width: 680px)');
    const medium = matchMedia('(max-width: 1000px)');
    const photoDelay = 1000;
    const photoWaitLimit = 3000;
    const eventDelay = 3500;
    const feedbackDelay = 2000;
    const slideEasing = 'cubic-bezier(.22,1,.36,1)';

    function animateEntrance(element, transform, duration, delay = 0) {
        return element.animate([{ opacity: 0, transform }, { opacity: 1, transform: 'none' }],
            { duration, delay, easing: slideEasing, fill: 'backwards' });
    }

    function feedbackPhotoPreview(section, rail) {
        const dialog = section.querySelector('[data-feedback-photo-dialog]');
        if (typeof dialog?.showModal !== 'function') return;
        const image = dialog.querySelector('img');
        let opener;
        rail.addEventListener('click', event => {
            const link = event.target.closest('[data-feedback-photo]');
            if (!link || event.defaultPrevented || event.button !== 0
                || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            opener = link;
            image.src = link.href;
            dialog.showModal();
        });
        dialog.querySelector('button').addEventListener('click', () => dialog.close());
        dialog.addEventListener('click', event => {
            if (event.target === dialog) dialog.close();
        });
        dialog.addEventListener('close', () => {
            opener?.focus({ preventScroll: true });
            opener = undefined;
        });
    }

    function eventGallery(slide, onPick) {
        const gallery = slide.querySelector('[data-event-gallery]');
        const thumbnails = gallery?.querySelector('.event-thumbnails');
        if (!thumbnails) return null;

        const cover = gallery.querySelector('.event-cover');
        const count = cover.querySelector('[data-event-photo-count]');
        const photos = [...thumbnails.querySelectorAll('[data-event-photo]')];
        let image = cover.querySelector('img');
        let preparedImage;
        let pendingImage;
        let pendingSince;
        let displayed = 0;
        let current = 0;

        function showPhoto(index, frame) {
            const previous = image;
            image = frame;
            cover.insertBefore(frame, count);
            if (!reducedMotion.matches && slide.classList.contains('is-current')) {
                previous.setAttribute('aria-hidden', 'true');
                frame.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 300, easing: 'ease-out' })
                    .onfinish = () => previous.remove();
            } else {
                previous.remove();
            }

            photos[displayed].setAttribute('aria-current', 'false');
            displayed = index;
            const photo = photos[index];
            photo.setAttribute('aria-current', 'true');
            cover.href = photo.href;
            cover.setAttribute('aria-label', `Open event photo ${index + 1} of ${photos.length}`);
            count.textContent = `${index + 1} / ${photos.length}`;

            const strip = thumbnails.getBoundingClientRect();
            const item = photo.getBoundingClientRect();
            const offset = item.left < strip.left ? item.left - strip.left
                : Math.max(0, item.right - strip.right);
            if (offset) thumbnails.scrollBy({ left: offset, behavior: reducedMotion.matches ? 'instant' : 'smooth' });
        }

        function loadPhoto(index) {
            const frame = new Image();
            frame.alt = image.alt;
            frame.decoding = 'async';
            frame.src = photos[index].href;
            return frame;
        }

        function selectPhoto(index) {
            if (index === current && (pendingImage || index === displayed)) return;
            current = index;
            const frame = preparedImage?.src === photos[index].href ? preparedImage : loadPhoto(index);
            preparedImage = null;
            pendingImage = frame;
            pendingSince = performance.now();
            function showLoadedPhoto() {
                if (pendingImage !== frame) return;
                pendingImage = null;
                showPhoto(index, frame);
            }
            if (frame.complete) {
                if (frame.naturalWidth) showLoadedPhoto();
                else pendingImage = null;
            } else {
                frame.onload = showLoadedPhoto;
                frame.onerror = () => { if (pendingImage === frame) pendingImage = null; };
            }
        }

        function nextIndex(direction) {
            return (current + direction + photos.length) % photos.length;
        }

        function preload(index) {
            if (preparedImage?.src !== photos[index].href) preparedImage = loadPhoto(index);
        }

        function advance(direction) {
            selectPhoto(nextIndex(direction));
        }

        thumbnails.addEventListener('click', event => {
            const index = photos.indexOf(event.target.closest('[data-event-photo]'));
            if (index < 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            selectPhoto(index);
            onPick();
        });
        for (const [selector, direction] of [['[data-event-photo-prev]', -1], ['[data-event-photo-next]', 1]]) {
            const button = gallery.querySelector(selector);
            button.hidden = false;
            button.addEventListener('click', () => {
                advance(direction);
                onPick();
            });
        }

        // Keep offscreen thumbnails as links until their preview comes into view.
        if ('IntersectionObserver' in window) {
            const observer = new IntersectionObserver(entries => {
                for (const { target, isIntersecting } of entries) {
                    if (!isIntersecting) continue;
                    const preview = target.querySelector('img');
                    preview.src = preview.dataset.src;
                    observer.unobserve(target);
                }
            }, { root: thumbnails, rootMargin: '0px 60px' });
            photos.forEach(photo => observer.observe(photo));
        } else {
            thumbnails.querySelectorAll('img[data-src]').forEach(preview => { preview.src = preview.dataset.src; });
        }

        return {
            advance,
            preloadNext: () => preload(nextIndex(1)),
            preloadFirst: () => {
                if (displayed === 0 && !pendingImage) image.loading = 'eager';
                else preload(0);
            },
            restart: () => selectPhoto(0),
            count: photos.length,
            get loading() { return !!pendingImage && performance.now() - pendingSince < photoWaitLimit; }
        };
    }

    for (const section of document.querySelectorAll('[data-showcase]')) {
        const slides = [...section.querySelectorAll('[data-showcase-slide]')];
        const events = section.dataset.showcase === 'events';
        const hasPhotoRotation = events && section.querySelector('.event-thumbnails');
        const rotates = slides.length > 1 || hasPhotoRotation;
        if (!slides.length || (events && !rotates)) continue;

        const rail = section.querySelector('.showcase-rail');
        const controls = section.querySelector('.showcase-controls');
        const position = controls.querySelector('[data-showcase-position]');
        const galleries = events ? slides.map(slide => eventGallery(slide, schedule)) : [];
        let first = 0;
        let photosAdvanced = 0;
        let timer;
        let touching;
        let hovering = false;
        let inView = true;
        let hasEnteredView = false;
        let allVisible = false;
        let railWidth = 0;

        function show(direction = 0) {
            const visibleCount = Math.min(slides.length,
                events || compact.matches || section.dataset.feedbackLayout === 'single' ? 1 : medium.matches ? 2 : 3);
            allVisible = slides.length <= visibleCount;
            controls.hidden = allVisible;
            const previous = slides[first];
            const animate = !!direction && !reducedMotion.matches;
            const feedbackPositions = !events && animate ? measureFeedback() : null;
            const outgoing = slides[(first + (direction < 0 ? visibleCount - 1 : 0)) % slides.length];
            if (events) {
                for (const slide of rail.querySelectorAll('.is-exiting')) {
                    slide.getAnimations().forEach(animation => animation.cancel());
                    clearExit(slide);
                }
            }
            first = (first + direction + slides.length) % slides.length;
            if (direction) photosAdvanced = 0;
            for (const [index, slide] of slides.entries()) {
                const order = (index - first + slides.length) % slides.length;
                slide.classList.toggle('is-current', order < visibleCount);
                slide.style.order = order;
            }
            position.textContent = `${first + 1} / ${slides.length}`;
            if (animate && events) slideEvent(previous, slides[first], direction);
            holdHeight();
            if (feedbackPositions) slideFeedback(feedbackPositions, outgoing, direction);
        }

        // Slides differ in height. The rail only ever grows, so rotation never moves the page below it.
        function holdHeight() {
            if (rail.offsetWidth !== railWidth) {
                railWidth = rail.offsetWidth;
                rail.style.minHeight = '';
            }
            rail.style.minHeight = `${rail.offsetHeight}px`;
        }

        function visibleSlides() {
            return slides.filter(slide => slide.classList.contains('is-current'));
        }

        function measureFeedback() {
            rail.querySelector('.feedback-exiting')?.remove();
            return new Map(visibleSlides().map(slide => {
                const frame = slide.getBoundingClientRect();
                slide.getAnimations().forEach(animation => animation.cancel());
                return [slide, frame];
            }));
        }

        function slideFeedback(positions, outgoing, direction) {
            const duration = 440;
            const timing = { duration, easing: slideEasing };
            const leaving = positions.get(outgoing);
            if (leaving) {
                const railFrame = rail.getBoundingClientRect();
                const ghost = outgoing.cloneNode(true);
                ghost.removeAttribute('data-showcase-slide');
                ghost.classList.remove('is-current');
                ghost.classList.add('feedback-exiting');
                ghost.inert = true;
                ghost.setAttribute('aria-hidden', 'true');
                Object.assign(ghost.style, {
                    left: `${leaving.left - railFrame.left}px`,
                    top: `${leaving.top - railFrame.top}px`,
                    width: `${leaving.width}px`,
                    height: `${leaving.height}px`
                });
                rail.append(ghost);
                // Leaves before its neighbour arrives, so two quotes never read on top of each other.
                ghost.animate([{ opacity: 1 }, { opacity: 0, transform: `translateX(${-direction * 28}px) scale(.96)` }],
                    { duration: 170, easing: 'ease-out', fill: 'forwards' })
                    .onfinish = () => ghost.remove();
            }
            for (const slide of visibleSlides()) {
                const start = slide === outgoing ? null : positions.get(slide);
                if (!start) {
                    animateEntrance(slide, `translateX(${direction * 48}px)`, duration, 130);
                    continue;
                }
                const finish = slide.getBoundingClientRect();
                const offset = `translate(${start.left - finish.left}px, ${start.top - finish.top}px)`;
                slide.animate([{ transform: offset }, { transform: 'none' }], timing);
            }
        }

        function clearExit(slide) {
            slide.classList.remove('is-exiting');
            slide.inert = false;
        }

        function slideEvent(previous, current, direction) {
            const timing = { duration: 480, easing: slideEasing };
            previous.getAnimations().forEach(animation => animation.cancel());
            current.getAnimations().forEach(animation => animation.cancel());
            previous.classList.add('is-exiting');
            previous.inert = true;
            previous.animate(
                [{ opacity: 1, transform: 'translateX(0)' }, { opacity: 0, transform: `translateX(${24 * direction}px)` }], timing
            ).onfinish = () => clearExit(previous);
            animateEntrance(current, `translateX(${-24 * direction}px)`, timing.duration);
        }

        function schedule() {
            clearTimeout(timer);
            const holdFeedback = !events && (reducedMotion.matches || hovering ||
                section.contains(document.activeElement));
            if (!rotates || holdFeedback || (!events && allVisible) || !inView || document.hidden || touching) return;
            const gallery = galleries[first];
            if (shouldAdvancePhoto(gallery)) {
                if (!gallery.loading) gallery.preloadNext();
            } else if (events) {
                const next = (first + 1) % slides.length;
                if (galleries[next]) galleries[next].preloadFirst();
                else slides[next].querySelector('.event-cover img')?.setAttribute('loading', 'eager');
            }
            timer = setTimeout(advanceAutomatically, gallery ? photoDelay : events ? eventDelay : feedbackDelay);
        }

        function shouldAdvancePhoto(gallery) {
            return gallery && (slides.length === 1 || photosAdvanced < gallery.count - 1);
        }

        function advanceAutomatically() {
            const gallery = galleries[first];
            if (gallery?.loading) {
                schedule();
                return;
            }
            if (shouldAdvancePhoto(gallery)) {
                gallery.advance(1);
                photosAdvanced++;
                schedule();
            } else {
                change(1, true);
            }
        }

        function change(direction, automatic = false) {
            show(direction);
            if (automatic) galleries[first]?.restart();
            schedule();
        }

        const previous = controls.querySelector('[data-showcase-prev]');
        const next = controls.querySelector('[data-showcase-next]');
        previous.hidden = next.hidden = position.hidden = slides.length < 2;
        previous.addEventListener('click', () => change(-1));
        next.addEventListener('click', () => change(1));
        if (!events) {
            feedbackPhotoPreview(section, rail);
            section.addEventListener('pointerenter', event => {
                if (event.pointerType !== 'touch') hovering = true;
                schedule();
            });
            section.addEventListener('pointerleave', () => { hovering = false; schedule(); });
            section.addEventListener('focusin', schedule);
            section.addEventListener('focusout', () => setTimeout(schedule, 0));
        }
        rail.addEventListener('touchstart', event => {
            touching = {
                start: event.touches[0],
                browsingPhotos: events && event.target.closest('.event-thumbnails'),
                gallery: events && event.target.closest('.event-cover') ? galleries[first] : null
            };
            schedule();
        }, { passive: true });
        rail.addEventListener('touchend', event => {
            if (!touching) return;
            const { start, browsingPhotos, gallery } = touching;
            const dx = event.changedTouches[0].clientX - start.clientX;
            const dy = event.changedTouches[0].clientY - start.clientY;
            touching = null;
            if (!browsingPhotos && Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy)) {
                const direction = dx < 0 ? 1 : -1;
                if (gallery) gallery.advance(direction);
                else if (slides.length > 1) {
                    change(direction);
                    return;
                }
            }
            schedule();
        }, { passive: true });
        rail.addEventListener('touchcancel', () => { touching = null; schedule(); });
        document.addEventListener('visibilitychange', schedule);
        if ('IntersectionObserver' in window) {
            inView = false;
            new IntersectionObserver(([entry]) => {
                inView = entry.isIntersecting;
                if (inView && !events && !hasEnteredView) {
                    hasEnteredView = true;
                    if (!reducedMotion.matches) {
                        visibleSlides().forEach((slide, index) =>
                            animateEntrance(slide, 'translateY(14px)', 360 + index * 60));
                    }
                }
                schedule();
            }).observe(rail);
        }
        for (const query of [compact, medium]) query.addEventListener('change', () => { show(); schedule(); });
        window.addEventListener('resize', holdHeight);
        reducedMotion.addEventListener('change', schedule);
        show();
        schedule();
    }

})();
