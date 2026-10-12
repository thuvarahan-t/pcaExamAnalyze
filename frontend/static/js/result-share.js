(function () {
    'use strict';
    var nic = document.getElementById('resultNic');
    var toggle = document.getElementById('toggleResultNic');
    var shareButton = document.getElementById('shareResultButton');
    var capture = document.getElementById('resultShareCapture');

    if (nic && toggle) {
        toggle.addEventListener('click', function () {
            var reveal = nic.classList.contains('is-masked');
            nic.textContent = reveal ? nic.dataset.value : nic.dataset.masked;
            nic.classList.toggle('is-masked', !reveal);
            toggle.setAttribute('aria-label', reveal ? 'Hide NIC' : 'Show NIC');
            toggle.setAttribute('title', reveal ? 'Hide NIC' : 'Show NIC');
            toggle.querySelector('i').className = reveal ? 'bi bi-eye-slash-fill' : 'bi bi-eye-fill';
        });
    }

    function download(blob, fileName) {
        var url = URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    }

    function setBusy(busy) {
        if (!shareButton) return;
        shareButton.disabled = busy;
        shareButton.classList.toggle('is-busy', busy);
        shareButton.querySelector('i').className = busy ? 'bi bi-arrow-repeat' : 'bi bi-share-fill';
        shareButton.querySelector('span').textContent = busy ? 'Preparing image...' : 'Share result as image';
    }

    function isMobileDevice() {
        if (navigator.userAgentData && typeof navigator.userAgentData.mobile === 'boolean') {
            return navigator.userAgentData.mobile;
        }
        return /Android|iPhone|iPod|Mobile/i.test(navigator.userAgent || '');
    }

    if (shareButton && capture) {
        shareButton.addEventListener('click', async function () {
            if (shareButton.disabled) return;
            setBusy(true);
            var restoreVisibleNic = nic && !nic.classList.contains('is-masked');
            try {
                // A shared/downloaded receipt must never expose the NIC, even when the
                // student temporarily revealed it on screen.
                if (nic) {
                    nic.textContent = nic.dataset.masked;
                    nic.classList.add('is-masked');
                }
                if (document.fonts && document.fonts.ready) await document.fonts.ready;
                var canvas = await window.html2canvas(capture, {
                    backgroundColor: '#f4f8fc',
                    scale: Math.min(2, window.devicePixelRatio || 1),
                    useCORS: true,
                    logging: false,
                    ignoreElements: function (element) {
                        return element.tagName === 'IFRAME' || element.hasAttribute('data-html2canvas-ignore');
                    }
                });
                if (restoreVisibleNic) {
                    nic.textContent = nic.dataset.value;
                    nic.classList.remove('is-masked');
                }
                var blob = await new Promise(function (resolve) { canvas.toBlob(resolve, 'image/png', 1); });
                if (!blob) throw new Error('Image creation failed');
                var baseName = (capture.dataset.fileName || 'PCA-result').replace(/[^a-z0-9_-]+/gi, '-');
                var fileName = baseName + '.png';
                var file = new File([blob], fileName, { type: 'image/png' });
                if (isMobileDevice() && navigator.share && navigator.canShare && navigator.canShare({ files: [file] })) {
                    await navigator.share({ files: [file], title: document.title, text: 'My PCA exam submission result' });
                } else {
                    download(blob, fileName);
                }
            } catch (error) {
                if (error && error.name !== 'AbortError') window.alert('Could not create the image. Please try again.');
            } finally {
                if (restoreVisibleNic && nic) {
                    nic.textContent = nic.dataset.value;
                    nic.classList.remove('is-masked');
                }
                setBusy(false);
            }
        });
    }
})();
