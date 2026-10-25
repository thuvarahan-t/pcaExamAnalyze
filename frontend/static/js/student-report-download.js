(function () {
  'use strict';
  var buttons = document.querySelectorAll('[data-student-pdf]');
  if (!buttons.length || !window.fetch || !window.HTMLDialogElement) return;
  var style = document.createElement('style');
  style.textContent = '.pdf-download-dialog{box-sizing:border-box;width:min(440px,calc(100% - 32px));border:1px solid #dce5f1;border-radius:22px;padding:28px;color:#102544;background:#fff;box-shadow:0 24px 80px #071c3540;font-family:Inter,Arial,sans-serif}.pdf-download-dialog::backdrop{background:#071c3599}.pdf-download-dialog h2{margin:0 32px 10px 0;font-size:22px;line-height:1.3}.pdf-download-dialog p{font-size:14px;line-height:1.6;color:#536580;margin:10px 0}.pdf-download-close{position:absolute;right:16px;top:12px;background:none;border:0;font-size:26px;color:#536580;cursor:pointer}.pdf-download-number{font-size:38px;font-weight:800;color:#2563eb;margin:20px 0 8px}.pdf-download-dialog progress{display:block;width:100%;height:12px;accent-color:#2563eb;border:0;border-radius:12px;overflow:hidden;background:#e8eef8}.pdf-download-dialog progress::-webkit-progress-bar{background:#e8eef8}.pdf-download-dialog progress::-webkit-progress-value{background:#2563eb;border-radius:12px}.pdf-download-dialog progress::-moz-progress-bar{background:#2563eb;border-radius:12px}.pdf-download-retry{margin-top:16px;border:0;border-radius:10px;background:#2563eb;color:white;padding:12px 18px;font:inherit;font-weight:700;cursor:pointer}.pdf-download-dialog .pdf-download-error{color:#b42318}.pdf-download-dialog button:focus-visible{outline:3px solid #93b4ff;outline-offset:3px}';
  document.head.appendChild(style);
  buttons.forEach(function (button) {
    var original = button.innerHTML, busy = false, dialog;
    var title = button.dataset.pdfTitle || 'Preparing your exam PDF';
    var intro = button.dataset.pdfIntro || 'Your corrected answer sheet and question images are being prepared.';
    var readyTitle = button.dataset.pdfReady || 'Your exam PDF is ready';
    // A button with data-pdf-form posts that form's fields (for example the selected exam ids).
    function payload() {
      var form = button.dataset.pdfForm && document.getElementById(button.dataset.pdfForm);
      return form ? new URLSearchParams(new FormData(form)) : null;
    }
    function createDialog() {
      dialog = document.createElement('dialog');
      dialog.className = 'pdf-download-dialog';
      dialog.setAttribute('aria-labelledby', 'studentPdfTitle');
      dialog.innerHTML = '<button class="pdf-download-close" type="button" aria-label="Close download progress">&times;</button><h2 id="studentPdfTitle"></h2><p class="pdf-download-intro"></p><div class="pdf-download-number" aria-hidden="true">0%</div><progress max="100" value="0" aria-label="PDF download progress"></progress><p class="pdf-download-stage" role="status" aria-live="polite">Starting your report...</p><p class="pdf-download-hint">You can close this window and stay on this page. Your PDF will download automatically when ready.</p><button class="pdf-download-retry" type="button" hidden>Try again</button>';
      dialog.querySelector('h2').textContent = title;
      dialog.querySelector('.pdf-download-intro').textContent = intro;
      document.body.appendChild(dialog);
      dialog.querySelector('.pdf-download-close').onclick = function () { dialog.close(); };
      dialog.querySelector('.pdf-download-retry').onclick = start;
    }
    function update(percent, stage) {
      percent = Math.max(0, Math.min(100, Math.round(percent)));
      dialog.querySelector('progress').value = percent;
      dialog.querySelector('.pdf-download-number').textContent = percent + '%';
      var label = dialog.querySelector('.pdf-download-stage');
      if (label.textContent !== stage) label.textContent = stage;
      if (busy) button.textContent = 'Preparing PDF · ' + percent + '%';
    }
    async function request(url, options) {
      var response = await fetch(url, Object.assign({ credentials: 'same-origin', cache: 'no-store' }, options));
      if (!response.ok) {
        var message = response.status === 403 ? 'Your session or result access has changed. Please reopen My Results.' :
          response.status === 404 ? 'This report has expired. Please try again.' : 'Could not prepare your PDF. Please try again.';
        try { var body = await response.json(); if (body.message) message = body.message; } catch (_) {}
        throw new Error(message);
      }
      return response;
    }
    async function download(url) {
      update(90, 'Receiving your PDF...');
      var response = await request(url);
      if (!(response.headers.get('Content-Type') || '').includes('application/pdf'))
        throw new Error('Your session has expired. Please reopen My Results.');
      var total = Number(response.headers.get('Content-Length')), blob;
      if (response.body && response.body.getReader) {
        var reader = response.body.getReader(), chunks = [], received = 0;
        while (true) {
          var part = await reader.read();
          if (part.done) break;
          chunks.push(part.value); received += part.value.length;
          if (total > 0) update(Math.min(99, 90 + received / total * 9), 'Receiving your PDF...');
        }
        if (total > 0 && received !== total) throw new Error('The PDF transfer was interrupted. Please try again.');
        blob = new Blob(chunks, { type: 'application/pdf' });
      } else { blob = await response.blob(); }
      if (!blob.size) throw new Error('The report was empty. Please try again.');
      var disposition = response.headers.get('Content-Disposition') || '';
      var match = disposition.match(/filename="?([^";]+)"?/i);
      var link = document.createElement('a'), objectUrl = URL.createObjectURL(blob);
      link.href = objectUrl; link.download = match ? match[1] : 'PCA-Student-Report.pdf';
      document.body.appendChild(link); link.click(); link.remove();
      window.setTimeout(function () { URL.revokeObjectURL(objectUrl); }, 60000);
      update(100, 'Your PDF is ready. The download has started.');
      dialog.querySelector('h2').textContent = readyTitle;
      dialog.querySelector('.pdf-download-hint').textContent = 'Check your browser downloads or Downloads folder for the PDF.';
    }
    async function start() {
      var body = payload();
      if (button.dataset.pdfForm && (!body || !body.has('ids'))) return;
      var host = button.closest('dialog');
      if (host && host.open) host.close();
      if (!dialog) createDialog();
      if (!dialog.open) dialog.showModal();
      if (busy) return;
      busy = true; button.setAttribute('aria-busy', 'true');
      dialog.querySelector('h2').textContent = title;
      dialog.querySelector('.pdf-download-stage').classList.remove('pdf-download-error');
      dialog.querySelector('.pdf-download-retry').hidden = true;
      dialog.querySelector('.pdf-download-hint').textContent = 'You can close this window and stay on this page. Your PDF will download automatically when ready.';
      update(0, 'Starting your report...');
      try {
        var headers = {};
        headers[button.dataset.csrfHeader] = button.dataset.csrfToken;
        var options = { method: 'POST', headers: headers };
        if (body) { headers['Content-Type'] = 'application/x-www-form-urlencoded'; options.body = body.toString(); }
        var response = await request(button.dataset.startUrl, options);
        var job = (await response.json()).job;
        var base = button.dataset.startUrl.replace(/\/start$/, '/') + encodeURIComponent(job);
        var deadline = Date.now() + 20 * 60 * 1000;
        while (true) {
          if (Date.now() > deadline) throw new Error('This report is taking longer than expected. Please try again shortly.');
          var status = await (await request(base + '/status')).json();
          if (status.state === 'FAILED') throw new Error(status.error || 'Could not prepare your PDF. Please try again.');
          update(Number(status.percentage) * 0.9, status.stage || 'Preparing your report...');
          if (status.state === 'DONE') { await download(base + '/file'); break; }
          await new Promise(function (resolve) { window.setTimeout(resolve, 1000); });
        }
      } catch (error) {
        dialog.querySelector('h2').textContent = 'Download needs another try';
        var label = dialog.querySelector('.pdf-download-stage');
        label.textContent = error.message || 'Check your connection and try again.';
        label.classList.add('pdf-download-error');
        dialog.querySelector('.pdf-download-retry').hidden = false;
        dialog.querySelector('.pdf-download-hint').textContent = 'Your result is saved. You can try downloading again.';
        if (!dialog.open) dialog.showModal();
      } finally {
        busy = false; button.removeAttribute('aria-busy'); button.innerHTML = original;
      }
    }
    button.addEventListener('click', function (event) { event.preventDefault(); if (!button.disabled) start(); });
  });
})();
